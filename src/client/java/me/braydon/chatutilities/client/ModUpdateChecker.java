package me.braydon.chatutilities.client;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Compares installed mod version to latest GitHub release (semver-ish numeric segments). */
public final class ModUpdateChecker {
    private static final String GITHUB_LATEST_API =
            "https://api.github.com/repos/Rainnny7/chat-utilities-fabric/releases/latest";
    public static final String RELEASES_URL = "https://github.com/Rainnny7/chat-utilities-fabric/releases";

    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();

    private static final long MIN_INTERVAL_MS = 6L * 60L * 60L * 1000L;

    private static final AtomicBoolean REQUEST_IN_FLIGHT = new AtomicBoolean();

    private static volatile boolean updateAvailable;
    private static volatile String latestTagCached = "";
    private static long lastRequestStartMs;

    private ModUpdateChecker() {}

    public static boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public static String getLatestVersionLabel() {
        return latestTagCached;
    }

    public static void clearResult() {
        updateAvailable = false;
        latestTagCached = "";
    }

    /** Call from client tick; respects {@link ChatUtilitiesClientOptions#isCheckForUpdatesEnabled()} and cooldown. */
    public static void tick(Minecraft mc) {
        if (mc == null || !ChatUtilitiesClientOptions.isCheckForUpdatesEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastRequestStartMs < MIN_INTERVAL_MS) {
            return;
        }
        if (!REQUEST_IN_FLIGHT.compareAndSet(false, true)) {
            return;
        }
        lastRequestStartMs = now;
        Optional<String> currentOpt = currentModVersionString();
        if (currentOpt.isEmpty()) {
            REQUEST_IN_FLIGHT.set(false);
            return;
        }
        String current = currentOpt.get();
        CompletableFuture.runAsync(
                () -> {
                    boolean done = false;
                    try {
                        HttpRequest req =
                                HttpRequest.newBuilder(URI.create(GITHUB_LATEST_API))
                                        .timeout(Duration.ofSeconds(14))
                                        .header(
                                                "User-Agent",
                                                "chat-utilities-fabric/1.0 (Fabric; +https://github.com/Rainnny7/chat-utilities-fabric)")
                                        .GET()
                                        .build();
                        HttpResponse<String> resp =
                                HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                        if (resp.statusCode() / 100 != 2) {
                            return;
                        }
                        JsonObject root = GSON.fromJson(resp.body(), JsonObject.class);
                        if (root == null || !root.has("tag_name")) {
                            return;
                        }
                        String tag = root.get("tag_name").getAsString();
                        if (tag == null) {
                            return;
                        }
                        String latest = normalizeVersionTag(tag);
                        latestTagCached = latest;
                        boolean newer = compareSemver(latest, current) > 0;
                        done = true;
                        mc.execute(
                                () -> {
                                    updateAvailable = newer;
                                    REQUEST_IN_FLIGHT.set(false);
                                });
                    } catch (Exception ignored) {
                    } finally {
                        if (!done) {
                            mc.execute(() -> REQUEST_IN_FLIGHT.set(false));
                        }
                    }
                });
    }

    public static void forceRecheckSoon() {
        lastRequestStartMs = 0L;
    }

    private static Optional<String> currentModVersionString() {
        Optional<ModContainer> c = FabricLoader.getInstance().getModContainer("chat-utilities");
        return c.map(
                cont ->
                        normalizeVersionTag(
                                cont.getMetadata().getVersion().getFriendlyString()));
    }

    static String normalizeVersionTag(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.strip();
        if (s.startsWith("v") || s.startsWith("V")) {
            s = s.substring(1).strip();
        }
        int plus = s.indexOf('+');
        if (plus > 0) {
            s = s.substring(0, plus);
        }
        return s;
    }

    /**
     * @return positive if {@code a} is newer than {@code b}
     */
    static int compareSemver(String a, String b) {
        List<Integer> pa = semverNumericPrefix(a);
        List<Integer> pb = semverNumericPrefix(b);
        int n = Math.max(pa.size(), pb.size());
        for (int i = 0; i < n; i++) {
            int x = i < pa.size() ? pa.get(i) : 0;
            int y = i < pb.size() ? pb.get(i) : 0;
            if (x != y) {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }

    private static List<Integer> semverNumericPrefix(String v) {
        List<Integer> out = new ArrayList<>();
        if (v == null || v.isEmpty()) {
            return out;
        }
        String s = v;
        int dash = s.indexOf('-');
        if (dash > 0) {
            s = s.substring(0, dash);
        }
        for (String part : s.split("\\.")) {
            if (part.isEmpty()) {
                continue;
            }
            int end = 0;
            while (end < part.length() && Character.isDigit(part.charAt(end))) {
                end++;
            }
            if (end == 0) {
                break;
            }
            try {
                out.add(Integer.parseInt(part.substring(0, end)));
            } catch (NumberFormatException e) {
                out.add(0);
            }
        }
        return out;
    }
}
