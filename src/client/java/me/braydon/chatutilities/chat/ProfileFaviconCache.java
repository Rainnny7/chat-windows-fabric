package me.braydon.chatutilities.chat;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.EventLoopGroupHolder;
import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Resolves multiplayer-style favicons for profile rows: prefers icons from the saved server list and the
 * current connection, then falls back to a one-off status ping. Textures are cached by profile host rule.
 */
public final class ProfileFaviconCache {
    private static final String NAMESPACE = "chat-utilities";
    private static final int LIST_REBUILD_INTERVAL = 20;
    private static final long PING_FAIL_COOLDOWN_MS = 45_000L;

    private static final Map<String, Identifier> uploaded = new ConcurrentHashMap<>();
    /** RGB {@code 0xRRGGBB} from favicon for sidebar accent; missing entry → default blue. */
    private static final Map<String, Integer> accentRgbByKey = new ConcurrentHashMap<>();
    private static final Map<String, PingState> pingByKey = new ConcurrentHashMap<>();
    private static volatile Map<String, byte[]> listIconSnapshot = Map.of();

    private static ServerStatusPinger pinger;
    private static int tickCounter;

    /** Same blue as sidebar accent when no favicon / no usable color ({@code C_ACCENT} RGB). */
    public static final int DEFAULT_PROFILE_ACCENT_RGB = 0x3A9FE0;

    private ProfileFaviconCache() {}

    public static void tick(Minecraft mc) {
        if (pinger != null) {
            pinger.tick();
        }
        if (++tickCounter % LIST_REBUILD_INTERVAL != 0) {
            return;
        }
        rebuildListIconSnapshot(mc);
    }

    private static void rebuildListIconSnapshot(Minecraft mc) {
        Map<String, byte[]> next = new HashMap<>();
        try {
            ServerList list = new ServerList(mc);
            list.load();
            for (int i = 0; i < list.size(); i++) {
                ServerData d = list.get(i);
                addIfPresent(next, d);
            }
            ServerData cur = mc.getCurrentServer();
            if (cur != null) {
                addIfPresent(next, cur);
            }
        } catch (RuntimeException ignored) {
            return;
        }
        listIconSnapshot = Map.copyOf(next);
    }

    private static void addIfPresent(Map<String, byte[]> out, ServerData d) {
        byte[] icon = d.getIconBytes();
        if (icon == null || icon.length == 0) {
            return;
        }
        String host = ChatUtilitiesManager.stripPortFromAddress(d.ip);
        if (host.isEmpty()) {
            return;
        }
        out.put(host, icon.clone());
    }

    /**
     * Dominant color from the server icon for sidebar tinting, or {@link #DEFAULT_PROFILE_ACCENT_RGB}
     * if there is no icon or the average is too gray. Call after {@link #getIcon} has run for the same host.
     */
    public static int getProfileAccentRgb(Minecraft mc, String profileHostRule) {
        if (profileHostRule == null || profileHostRule.isBlank()) {
            return DEFAULT_PROFILE_ACCENT_RGB;
        }
        getIcon(mc, profileHostRule);
        String key = profileHostRule.strip().toLowerCase(Locale.ROOT);
        Integer c = accentRgbByKey.get(key);
        return c != null ? c : DEFAULT_PROFILE_ACCENT_RGB;
    }

    /**
     * @param profileHostRule first server entry from the profile (already lowercased in config)
     * @return a registered texture location, or {@code null} to use the default compass
     */
    public static Identifier getIcon(Minecraft mc, String profileHostRule) {
        if (profileHostRule == null || profileHostRule.isBlank()) {
            return null;
        }
        String key = profileHostRule.strip().toLowerCase(Locale.ROOT);

        if (listIconSnapshot.isEmpty()) {
            rebuildListIconSnapshot(mc);
        }

        Identifier existing = uploaded.get(key);
        if (existing != null) {
            return existing;
        }

        for (Map.Entry<String, byte[]> e : listIconSnapshot.entrySet()) {
            if (ChatUtilitiesManager.hostMatchesRule(e.getKey(), key)) {
                if (tryUpload(key, e.getValue(), mc)) {
                    return uploaded.get(key);
                }
                break;
            }
        }

        requestPingIfNeeded(mc, key);
        return uploaded.get(key);
    }

    private static void requestPingIfNeeded(Minecraft mc, String key) {
        if (uploaded.containsKey(key)) {
            return;
        }
        PingState st = pingByKey.computeIfAbsent(key, k -> new PingState());
        synchronized (st) {
            if (st.probe != null) {
                tryFinishFromProbe(key, st.probe, mc);
                return;
            }
            if (st.inFlight) {
                return;
            }
            if (System.currentTimeMillis() < st.cooldownUntilMs) {
                return;
            }
            st.inFlight = true;
        }

        ServerData probe = new ServerData("", key, ServerData.Type.OTHER);
        st.probe = probe;

        if (pinger == null) {
            pinger = new ServerStatusPinger();
        }

        AtomicBoolean endOnce = new AtomicBoolean();
        Runnable onEnd =
                () -> {
                    if (!endOnce.compareAndSet(false, true)) {
                        return;
                    }
                    mc.execute(
                            () -> {
                                tryFinishFromProbe(key, probe, mc);
                                synchronized (st) {
                                    st.inFlight = false;
                                    st.probe = null;
                                    if (!uploaded.containsKey(key)) {
                                        st.cooldownUntilMs =
                                                System.currentTimeMillis() + PING_FAIL_COOLDOWN_MS;
                                    }
                                }
                            });
                };

        try {
            pinger.pingServer(probe, onEnd, onEnd, EventLoopGroupHolder.remote(false));
        } catch (UnknownHostException e) {
            synchronized (st) {
                st.inFlight = false;
                st.probe = null;
                st.cooldownUntilMs = System.currentTimeMillis() + PING_FAIL_COOLDOWN_MS;
            }
        }
    }

    private static void tryFinishFromProbe(String key, ServerData probe, Minecraft mc) {
        byte[] icon = probe.getIconBytes();
        if (icon != null && icon.length > 0) {
            tryUpload(key, icon, mc);
        }
    }

    private static boolean tryUpload(String key, byte[] pngBytes, Minecraft mc) {
        if (uploaded.containsKey(key)) {
            return true;
        }
        synchronized (ProfileFaviconCache.class) {
            if (uploaded.containsKey(key)) {
                return true;
            }
            NativeImage img;
            try {
                img = NativeImage.read(new ByteArrayInputStream(pngBytes));
            } catch (IOException e) {
                return false;
            }
            if (img == null) {
                return false;
            }
            try {
                int w = img.getWidth();
                int h = img.getHeight();
                if (w < 1 || h < 1 || w > 1024 || h > 1024) {
                    return false;
                }
                String path = "profile_icon/" + Integer.toHexString(key.hashCode());
                Identifier id = Identifier.fromNamespaceAndPath(NAMESPACE, path);
                DynamicTexture tex = new DynamicTexture(() -> path, img);
                mc.getTextureManager().register(id, tex);
                uploaded.put(key, id);
                accentRgbByKey.put(key, averageOpaqueRgb(img));
                PingState st = pingByKey.get(key);
                if (st != null) {
                    synchronized (st) {
                        st.inFlight = false;
                        st.probe = null;
                    }
                }
                return true;
            } catch (RuntimeException ex) {
                return false;
            }
        }
    }

    /**
     * Picks a saturated accent from the icon: saturation-weighted blend of opaque pixels, skips
     * near-black/near-white, then boosts saturation slightly. Falls back to plain average, then
     * {@link #DEFAULT_PROFILE_ACCENT_RGB} if still too gray.
     */
    private static int averageOpaqueRgb(NativeImage img) {
        long rSum = 0;
        long gSum = 0;
        long bSum = 0;
        long rPlain = 0;
        long gPlain = 0;
        long bPlain = 0;
        double wSum = 0;
        int nPlain = 0;
        int w = img.getWidth();
        int h = img.getHeight();
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int argb = img.getPixel(px, py);
                int a = (argb >> 24) & 0xFF;
                if (a < 40) {
                    continue;
                }
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                rPlain += r;
                gPlain += g;
                bPlain += b;
                nPlain++;
                float[] hsb = Color.RGBtoHSB(r, g, b, null);
                float bri = hsb[2];
                if (bri < 0.07f || bri > 0.96f) {
                    continue;
                }
                double weight = 0.12 + Math.pow(hsb[1], 1.15) * 2.2;
                rSum += r * weight;
                gSum += g * weight;
                bSum += b * weight;
                wSum += weight;
            }
        }
        int r;
        int g;
        int b;
        if (wSum >= 1e-3) {
            r = (int) (rSum / wSum);
            g = (int) (gSum / wSum);
            b = (int) (bSum / wSum);
        } else if (nPlain > 0) {
            r = (int) (rPlain / nPlain);
            g = (int) (gPlain / nPlain);
            b = (int) (bPlain / nPlain);
        } else {
            return DEFAULT_PROFILE_ACCENT_RGB;
        }
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        if (hsb[1] < 0.10f) {
            return DEFAULT_PROFILE_ACCENT_RGB;
        }
        hsb[1] = Math.min(1f, hsb[1] * 1.22f);
        hsb[2] = Math.min(1f, hsb[2] * 1.06f);
        int packed = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
        return ((packed >> 16) & 0xFF) << 16 | ((packed >> 8) & 0xFF) << 8 | (packed & 0xFF);
    }

    private static final class PingState {
        volatile boolean inFlight;
        volatile long cooldownUntilMs;
        volatile ServerData probe;
    }
}
