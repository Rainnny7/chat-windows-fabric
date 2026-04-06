package me.braydon.chatutilities.chat;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.network.EventLoopGroupHolder;
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
    private static final Map<String, PingState> pingByKey = new ConcurrentHashMap<>();
    private static volatile Map<String, byte[]> listIconSnapshot = Map.of();

    private static ServerStatusPinger pinger;
    private static int tickCounter;

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

    private static final class PingState {
        volatile boolean inFlight;
        volatile long cooldownUntilMs;
        volatile ServerData probe;
    }
}
