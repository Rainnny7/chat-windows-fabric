package me.braydon.window.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ChatWindowManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("chat-windows");
    /** Same SLF4J name as the client {@link Minecraft} class logger (latest.log / launcher output). */
    private static final Logger MINECRAFT_GAME_LOG = LoggerFactory.getLogger(Minecraft.class);
    private static final int PATTERN_FLAGS = Pattern.UNICODE_CASE;
    private static final String REGEX_PREFIX = "regex:";
    private static final int PATTERN_FORMAT_V2 = 2;
    private static final long DEDUP_MS = 5L;

    private static final ChatWindowManager INSTANCE = new ChatWindowManager();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, ChatWindow> windows = new LinkedHashMap<>();
    private Path configPath;
    private String lastDedupText = "";
    private long lastDedupAt;
    /** While non-zero, {@link #shouldHideFromMainChat} is false so Fabric client command feedback is not swallowed by patterns. */
    private int clientCommandFeedbackDepth;

    private ChatWindowManager() {}

    public static ChatWindowManager get() {
        return INSTANCE;
    }

    public void init() {
        this.configPath = FabricLoader.getInstance().getConfigDir().resolve("chat-windows.json");
        load();
    }

    public boolean createWindow(String id, String patternInput) throws PatternSyntaxException {
        Pattern p = compileUserMatchPattern(patternInput);
        ChatWindow existing = windows.put(id, new ChatWindow(id, p, patternInput.strip()));
        save();
        return existing != null;
    }

    public boolean addPattern(String id, String patternInput) throws PatternSyntaxException {
        ChatWindow w = windows.get(id);
        if (w == null) {
            return false;
        }
        w.addPattern(compileUserMatchPattern(patternInput), patternInput.strip());
        save();
        return true;
    }

    public boolean removePattern(String id, int userPosition) {
        ChatWindow w = windows.get(id);
        if (w == null) {
            return false;
        }
        if (!w.removePatternAtUserIndex(userPosition)) {
            return false;
        }
        save();
        return true;
    }

    public boolean removeWindow(String id) {
        ChatWindow removed = windows.remove(id);
        if (removed != null) {
            save();
            return true;
        }
        return false;
    }

    public ChatWindow getWindow(String id) {
        return windows.get(id);
    }

    public Collection<ChatWindow> getWindows() {
        return Collections.unmodifiableCollection(windows.values());
    }

    public List<String> getWindowIds() {
        return new ArrayList<>(windows.keySet());
    }

    public boolean hasWindow(String id) {
        return windows.containsKey(id);
    }

    public void pushClientCommandFeedback() {
        clientCommandFeedbackDepth++;
    }

    public void popClientCommandFeedback() {
        if (clientCommandFeedbackDepth > 0) {
            clientCommandFeedbackDepth--;
        }
    }

    /** Plain-text match against patterns; used to hide from vanilla and route to windows. */
    public boolean shouldHideFromMainChat(Component message) {
        if (clientCommandFeedbackDepth > 0) {
            return false;
        }
        if (windows.isEmpty()) {
            return false;
        }
        String text = plainTextForMatching(message);
        if (text.isEmpty()) {
            return false;
        }
        for (ChatWindow w : windows.values()) {
            if (w.matches(text)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds the styled message to every window whose patterns match. Call only when the message is
     * not going to vanilla chat (or dedup will skip duplicates from any extra hooks).
     */
    public void dispatchToWindows(Component message) {
        if (windows.isEmpty()) {
            return;
        }
        String text = plainTextForMatching(message);
        if (text.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (text.equals(lastDedupText) && now - lastDedupAt < DEDUP_MS) {
            return;
        }
        lastDedupText = text;
        lastDedupAt = now;
        String legacyLog = componentToLegacyLogString(message);
        MINECRAFT_GAME_LOG.info("[CHAT] {}", legacyLog.isEmpty() ? text : legacyLog);
        int tick = Minecraft.getInstance().gui.getGuiTicks();
        ChatWindowLine entry = new ChatWindowLine(message, tick);
        for (ChatWindow w : windows.values()) {
            if (w.matches(text)) {
                w.addLine(entry);
            }
        }
    }

    /**
     * Builds a legacy {@code §} string by walking styled segments ({@code getString()} drops styles for
     * many server-built component trees, e.g. Hypixel ranks).
     */
    private static String componentToLegacyLogString(Component message) {
        StringBuilder out = new StringBuilder();
        message.visit(
                (style, segment) -> {
                    if (!segment.isEmpty()) {
                        char p = ChatFormatting.PREFIX_CODE;
                        out.append(p).append(ChatFormatting.RESET.getChar());
                        appendStyleAsLegacy(out, style);
                        out.append(segment);
                    }
                    return Optional.empty();
                },
                Style.EMPTY);
        if (out.length() > 0) {
            return out.toString();
        }
        String plain = message.getString();
        return plain == null ? "" : plain;
    }

    private static void appendStyleAsLegacy(StringBuilder out, Style style) {
        char p = ChatFormatting.PREFIX_CODE;
        TextColor tc = style.getColor();
        if (tc != null) {
            ChatFormatting color = legacyChatColorFor(tc);
            if (color != null) {
                out.append(p).append(color.getChar());
            }
        }
        if (style.isBold()) {
            out.append(p).append(ChatFormatting.BOLD.getChar());
        }
        if (style.isItalic()) {
            out.append(p).append(ChatFormatting.ITALIC.getChar());
        }
        if (style.isUnderlined()) {
            out.append(p).append(ChatFormatting.UNDERLINE.getChar());
        }
        if (style.isStrikethrough()) {
            out.append(p).append(ChatFormatting.STRIKETHROUGH.getChar());
        }
        if (style.isObfuscated()) {
            out.append(p).append(ChatFormatting.OBFUSCATED.getChar());
        }
    }

    private static ChatFormatting legacyChatColorFor(TextColor tc) {
        int rgb = tc.getValue() & 0xFFFFFF;
        int bestDist = Integer.MAX_VALUE;
        ChatFormatting nearest = ChatFormatting.WHITE;
        for (ChatFormatting cf : ChatFormatting.values()) {
            if (!cf.isColor()) {
                continue;
            }
            Integer cRgbObj = cf.getColor();
            if (cRgbObj == null) {
                continue;
            }
            int cRgb = cRgbObj & 0xFFFFFF;
            if (cRgb == rgb) {
                return cf;
            }
            int dr = (rgb >> 16) - (cRgb >> 16);
            int dg = ((rgb >> 8) & 0xFF) - ((cRgb >> 8) & 0xFF);
            int db = (rgb & 0xFF) - (cRgb & 0xFF);
            int dist = dr * dr + dg * dg + db * db;
            if (dist < bestDist) {
                bestDist = dist;
                nearest = cf;
            }
        }
        return nearest;
    }

    private static String plainTextForMatching(Component message) {
        String raw = message.getString();
        if (raw == null) {
            return "";
        }
        String s = ChatFormatting.stripFormatting(raw);
        s = s.strip();
        s = stripEdgeInvisible(s);
        return s.strip();
    }

    private static String stripEdgeInvisible(String s) {
        int start = 0;
        int end = s.length();
        while (start < end && isEdgeInvisible(s.charAt(start))) {
            start++;
        }
        while (end > start && isEdgeInvisible(s.charAt(end - 1))) {
            end--;
        }
        return s.substring(start, end);
    }

    private static boolean isEdgeInvisible(char c) {
        return c == '\uFEFF'
                || c == '\u200B'
                || c == '\u200C'
                || c == '\u200D'
                || c == '\u200E'
                || c == '\u200F'
                || c == '\u2060'
                || (c >= '\u2066' && c <= '\u2069');
    }

    /**
     * Command input and v2 config: plain text is matched as a literal substring (regex metacharacters are not
     * special). Prefix with {@code regex:} for a real Java regex (prefix itself is case-insensitive).
     */
    private static Pattern compileUserMatchPattern(String input) throws PatternSyntaxException {
        String s = input.strip();
        if (s.isEmpty()) {
            throw new PatternSyntaxException("Empty pattern", input, 0);
        }
        if (regionMatchesIgnoreCase(s, 0, REGEX_PREFIX, 0, REGEX_PREFIX.length())) {
            String expr = s.substring(REGEX_PREFIX.length()).strip();
            if (expr.isEmpty()) {
                throw new PatternSyntaxException("Empty regex after " + REGEX_PREFIX, input, 0);
            }
            return Pattern.compile(expr, PATTERN_FLAGS);
        }
        return Pattern.compile(Pattern.quote(s), PATTERN_FLAGS);
    }

    private static boolean regionMatchesIgnoreCase(String s, int sStart, String other, int oStart, int len) {
        return s.length() - sStart >= len && s.regionMatches(true, sStart, other, oStart, len);
    }

    private static List<String> migrateLegacySourcesToV2(List<String> sources) {
        List<String> out = new ArrayList<>(sources.size());
        for (String src : sources) {
            if (src == null) {
                continue;
            }
            String t = src.strip();
            if (t.isEmpty()) {
                continue;
            }
            if (regionMatchesIgnoreCase(t, 0, REGEX_PREFIX, 0, REGEX_PREFIX.length())) {
                out.add(src);
            } else {
                out.add(REGEX_PREFIX + src);
            }
        }
        return out;
    }

    public boolean isPositioning() {
        for (ChatWindow w : windows.values()) {
            if (w.isPositioningMode()) {
                return true;
            }
        }
        return false;
    }

    public void clearAllPositioningModes() {
        for (ChatWindow w : windows.values()) {
            w.setPositioningMode(false);
        }
    }

    public void togglePosition(String id) {
        ChatWindow w = windows.get(id);
        if (w == null) {
            return;
        }
        boolean next = !w.isPositioningMode();
        if (next) {
            for (ChatWindow o : windows.values()) {
                o.setPositioningMode(false);
            }
        }
        w.setPositioningMode(next);
        save();
    }

    public boolean toggleVisibility(String id) {
        ChatWindow w = windows.get(id);
        if (w == null) {
            return false;
        }
        w.toggleVisible();
        save();
        return true;
    }

    public void save() {
        if (configPath == null) {
            return;
        }
        PersistedRoot root = new PersistedRoot();
        root.patternFormat = PATTERN_FORMAT_V2;
        for (ChatWindow w : windows.values()) {
            PersistedWindow pw = new PersistedWindow();
            pw.id = w.getId();
            pw.patterns = new ArrayList<>(w.getPatternSources());
            pw.anchorX = w.getAnchorX();
            pw.anchorY = w.getAnchorY();
            pw.visible = w.isVisible();
            pw.widthFrac = w.getWidthFrac();
            pw.maxVisibleLines = w.getMaxVisibleLines();
            root.windows.add(pw);
        }
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, gson.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to save chat-windows config", e);
        }
    }

    public void load() {
        windows.clear();
        if (configPath == null || !Files.isRegularFile(configPath)) {
            return;
        }
        try {
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            PersistedRoot root = gson.fromJson(json, PersistedRoot.class);
            if (root == null || root.windows == null) {
                return;
            }
            boolean legacyFormat = root.patternFormat == null || root.patternFormat < PATTERN_FORMAT_V2;
            for (PersistedWindow pw : root.windows) {
                if (pw.id == null) {
                    continue;
                }
                List<String> sources = new ArrayList<>();
                if (pw.patterns != null && !pw.patterns.isEmpty()) {
                    sources.addAll(pw.patterns);
                } else if (pw.regex != null && !pw.regex.isEmpty()) {
                    sources.add(pw.regex);
                } else {
                    continue;
                }
                if (legacyFormat) {
                    sources = migrateLegacySourcesToV2(sources);
                }
                try {
                    List<Pattern> compiled = new ArrayList<>();
                    for (String src : sources) {
                        if (src == null || src.strip().isEmpty()) {
                            continue;
                        }
                        compiled.add(compileUserMatchPattern(src));
                    }
                    if (compiled.isEmpty()) {
                        continue;
                    }
                    ChatWindow cw = new ChatWindow(pw.id, compiled, sources);
                    cw.setAnchorX(pw.anchorX);
                    cw.setAnchorY(pw.anchorY);
                    cw.setVisible(pw.visible);
                    if (pw.widthFrac > 0) {
                        cw.setWidthFrac(pw.widthFrac);
                    }
                    if (pw.maxVisibleLines > 0) {
                        cw.setMaxVisibleLines(pw.maxVisibleLines);
                    }
                    windows.put(pw.id, cw);
                } catch (PatternSyntaxException e) {
                    LOGGER.warn("Skipping window {}: bad regex", pw.id, e);
                }
            }
        } catch (IOException | JsonParseException e) {
            LOGGER.error("Failed to load chat-windows config", e);
        }
    }

    private static final class PersistedRoot {
        /** Absent or {@code < 2}: legacy file (full-string regex); rewritten on save as v2 with {@code regex:} prefixes. */
        Integer patternFormat;
        List<PersistedWindow> windows = new ArrayList<>();
    }

    private static final class PersistedWindow {
        String id;
        List<String> patterns;
        String regex;
        float anchorX = 0.02f;
        float anchorY = 0.85f;
        boolean visible = true;
        float widthFrac = ChatWindow.DEFAULT_WIDTH_FRAC;
        int maxVisibleLines = ChatWindow.DEFAULT_MAX_VISIBLE_LINES;
    }
}
