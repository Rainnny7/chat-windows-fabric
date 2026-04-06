package me.braydon.chatutilities.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class ChatUtilitiesManager {
    public enum ChatIntercept {
        NONE,
        /** Hide from vanilla and custom windows. */
        DROP,
        /** Hide from vanilla; show in matching windows only. */
        WINDOWS
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("chat-utilities");
    private static final Logger MINECRAFT_GAME_LOG = LoggerFactory.getLogger(Minecraft.class);
    private static final int PATTERN_FLAGS = Pattern.UNICODE_CASE;
    private static final String REGEX_PREFIX = "regex:";
    private static final int PATTERN_FORMAT_V2 = 2;
    private static final int FORMAT_VERSION_V3 = 3;
    private static final long DEDUP_MS = 5L;

    private static final ChatUtilitiesManager INSTANCE = new ChatUtilitiesManager();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final List<ServerProfile> profiles = new ArrayList<>();
    private final Map<String, ServerProfile> profilesById = new HashMap<>();

    private Path configPath;
    private Path legacyConfigPath;
    private String lastDedupText = "";
    private long lastDedupAt;
    private int clientCommandFeedbackDepth;

    /** Compiled ignore patterns per profile id (rebuilt when ignores change). */
    private final Map<String, List<Pattern>> compiledIgnoresByProfile = new HashMap<>();

    /** Compiled message-sound rules per profile id. */
    private final Map<String, List<CompiledMessageSound>> compiledMessageSoundsByProfile = new HashMap<>();

    /** After closing the GUI for window positioning, reopen this screen (consumed when applied). */
    private Supplier<Screen> restoreScreenAfterPosition;

    private ChatUtilitiesManager() {}

    public static ChatUtilitiesManager get() {
        return INSTANCE;
    }

    public void init() {
        Path dir = FabricLoader.getInstance().getConfigDir();
        this.configPath = dir.resolve("chat-utilities.json");
        this.legacyConfigPath = dir.resolve("chat-windows.json");
        this.restoreScreenAfterPosition = null;
        load();
    }

    public void setRestoreScreenAfterPosition(Supplier<Screen> screenSupplier) {
        this.restoreScreenAfterPosition = screenSupplier;
    }

    public void clearRestoreScreenAfterPosition() {
        this.restoreScreenAfterPosition = null;
    }

    public void runRestoreScreenAfterPositionIfAny(Minecraft mc) {
        Supplier<Screen> s = restoreScreenAfterPosition;
        if (s == null) {
            return;
        }
        restoreScreenAfterPosition = null;
        mc.execute(() -> mc.setScreen(s.get()));
    }

    public void pushClientCommandFeedback() {
        clientCommandFeedbackDepth++;
    }

    public void popClientCommandFeedback() {
        if (clientCommandFeedbackDepth > 0) {
            clientCommandFeedbackDepth--;
        }
    }

    public List<ServerProfile> getProfiles() {
        return Collections.unmodifiableList(profiles);
    }

    public ServerProfile getProfile(String id) {
        return profilesById.get(id);
    }

    public ServerProfile createProfileForCurrentServer(String displayName) {
        String id = UUID.randomUUID().toString();
        ServerProfile p = new ServerProfile(id, displayName == null || displayName.isBlank() ? "New Profile" : displayName);
        String host = currentConnectionHostNormalized();
        if (host != null && !host.isEmpty()) {
            p.getServers().add(host);
        }
        profiles.add(p);
        profilesById.put(id, p);
        recompileIgnores(p);
        recompileMessageSounds(p);
        save();
        return p;
    }

    public void removeProfile(String id) {
        ServerProfile removed = profilesById.remove(id);
        if (removed != null) {
            profiles.remove(removed);
            compiledIgnoresByProfile.remove(id);
            compiledMessageSoundsByProfile.remove(id);
            save();
        }
    }

    public void markProfileServersDirty() {
        save();
    }

    public void addIgnorePattern(ServerProfile profile, String raw) throws PatternSyntaxException {
        String s = raw.strip();
        compileUserMatchPattern(s);
        profile.getIgnorePatternSources().add(s);
        recompileIgnores(profile);
        save();
    }

    public void removeIgnorePattern(ServerProfile profile, int index) {
        List<String> list = profile.getIgnorePatternSources();
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            recompileIgnores(profile);
            save();
        }
    }

    public void setIgnorePatternAt(ServerProfile profile, int index, String raw) throws PatternSyntaxException {
        List<String> list = profile.getIgnorePatternSources();
        if (index < 0 || index >= list.size()) {
            return;
        }
        String s = raw.strip();
        compileUserMatchPattern(s);
        list.set(index, s);
        recompileIgnores(profile);
        save();
    }

    public void addMessageSound(ServerProfile profile, String patternRaw, String soundRaw)
            throws PatternSyntaxException {
        String pat = patternRaw == null ? "" : patternRaw.strip();
        compileUserMatchPattern(pat);
        Identifier soundId =
                parseSoundId(soundRaw)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid sound id"));
        if (BuiltInRegistries.SOUND_EVENT.get(soundId).isEmpty()) {
            throw new IllegalArgumentException("Unknown sound: " + soundId);
        }
        profile.getMessageSounds().add(new MessageSoundRule(pat, soundId.toString()));
        recompileMessageSounds(profile);
        save();
    }

    public void removeMessageSound(ServerProfile profile, int index) {
        List<MessageSoundRule> list = profile.getMessageSounds();
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            recompileMessageSounds(profile);
            save();
        }
    }

    public void setMessageSoundAt(ServerProfile profile, int index, String patternRaw, String soundRaw)
            throws PatternSyntaxException {
        List<MessageSoundRule> list = profile.getMessageSounds();
        if (index < 0 || index >= list.size()) {
            return;
        }
        String pat = patternRaw == null ? "" : patternRaw.strip();
        compileUserMatchPattern(pat);
        Identifier soundId =
                parseSoundId(soundRaw)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid sound id"));
        if (BuiltInRegistries.SOUND_EVENT.get(soundId).isEmpty()) {
            throw new IllegalArgumentException("Unknown sound: " + soundId);
        }
        list.set(index, new MessageSoundRule(pat, soundId.toString()));
        recompileMessageSounds(profile);
        save();
    }

    /**
     * Parses a sound registry id; if there is no {@code :}, uses the {@code minecraft} namespace.
     */
    public static Optional<Identifier> parseSoundId(String raw) {
        String s = raw == null ? "" : raw.strip();
        if (s.isEmpty()) {
            return Optional.empty();
        }
        Identifier parsed = Identifier.tryParse(s);
        if (parsed != null) {
            return Optional.of(parsed);
        }
        if (s.indexOf(':') < 0) {
            return Optional.of(Identifier.withDefaultNamespace(s));
        }
        return Optional.empty();
    }

    public static boolean isRegisteredSound(Identifier id) {
        return id != null && BuiltInRegistries.SOUND_EVENT.get(id).isPresent();
    }

    /** Plays UI preview of a sound id, or returns false if invalid. */
    public static boolean playSoundPreview(Identifier soundId) {
        if (soundId == null) {
            return false;
        }
        return BuiltInRegistries.SOUND_EVENT.get(soundId)
                .map(Holder::value)
                .map(
                        evt -> {
                            Minecraft.getInstance()
                                    .getSoundManager()
                                    .play(SimpleSoundInstance.forUI(evt, 1.0F));
                            return true;
                        })
                .orElse(false);
    }

    /**
     * When chat is not ignored, plays every message sound rule on the active profile that matches
     * {@code text}.
     */
    public void playMessageSoundsIfApplicable(Component message) {
        if (clientCommandFeedbackDepth > 0) {
            return;
        }
        ServerProfile profile = getActiveProfile();
        if (profile == null) {
            return;
        }
        String text = plainTextForMatching(message);
        if (text.isEmpty()) {
            return;
        }
        if (matchesAnyIgnore(profile, text)) {
            return;
        }
        List<CompiledMessageSound> rules = compiledMessageSoundsByProfile.get(profile.getId());
        if (rules == null || rules.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        for (CompiledMessageSound r : rules) {
            if (r.pattern.matcher(text).find()) {
                BuiltInRegistries.SOUND_EVENT
                        .get(r.soundId)
                        .map(Holder::value)
                        .ifPresent(evt -> mc.getSoundManager().play(SimpleSoundInstance.forUI(evt, 1.0F)));
            }
        }
    }

    public boolean createWindow(ServerProfile profile, String id, String patternInput) throws PatternSyntaxException {
        if (profile.getWindows().containsKey(id)) {
            return false;
        }
        Pattern p = compileUserMatchPattern(patternInput);
        profile.getWindows().put(id, new ChatWindow(id, p, patternInput.strip()));
        save();
        return true;
    }

    public boolean addPattern(ServerProfile profile, String windowId, String patternInput) throws PatternSyntaxException {
        ChatWindow w = profile.getWindows().get(windowId);
        if (w == null) {
            return false;
        }
        w.addPattern(compileUserMatchPattern(patternInput), patternInput.strip());
        save();
        return true;
    }

    public boolean removePattern(ServerProfile profile, String windowId, int userPosition) {
        ChatWindow w = profile.getWindows().get(windowId);
        if (w == null) {
            return false;
        }
        if (!w.removePatternAtUserIndex(userPosition)) {
            return false;
        }
        save();
        return true;
    }

    public boolean setPatternAt(ServerProfile profile, String windowId, int userPosition, String patternInput)
            throws PatternSyntaxException {
        ChatWindow w = profile.getWindows().get(windowId);
        if (w == null) {
            return false;
        }
        Pattern p = compileUserMatchPattern(patternInput);
        if (!w.setPatternAtUserIndex(userPosition, p, patternInput.strip())) {
            return false;
        }
        save();
        return true;
    }

    public boolean removeWindow(ServerProfile profile, String windowId) {
        if (profile.getWindows().remove(windowId) != null) {
            save();
            return true;
        }
        return false;
    }

    /**
     * Renames a chat window id, preserving map order and all window state. Fails if {@code newId}
     * is blank, equals {@code oldId}, or is already in use.
     */
    public boolean renameWindow(ServerProfile profile, String oldId, String newId) {
        String n = newId == null ? "" : newId.strip();
        if (n.isEmpty() || n.equals(oldId)) {
            return false;
        }
        if (profile.getWindows().containsKey(n)) {
            return false;
        }
        List<String> ids = profile.getWindowIds();
        int idx = ids.indexOf(oldId);
        if (idx < 0) {
            return false;
        }
        ChatWindow w = profile.getWindows().remove(oldId);
        if (w == null) {
            return false;
        }
        ChatWindow renamed = w.withId(n);
        LinkedHashMap<String, ChatWindow> fresh = new LinkedHashMap<>();
        for (String id : ids) {
            if (id.equals(oldId)) {
                fresh.put(n, renamed);
            } else {
                fresh.put(id, profile.getWindows().get(id));
            }
        }
        profile.getWindows().clear();
        profile.getWindows().putAll(fresh);
        save();
        return true;
    }

    public boolean toggleVisibility(ServerProfile profile, String windowId) {
        ChatWindow w = profile.getWindows().get(windowId);
        if (w == null) {
            return false;
        }
        w.toggleVisible();
        save();
        return true;
    }

    /**
     * Puts every visible window in the given profile into positioning mode simultaneously,
     * so all windows can be dragged at once from the Chat Windows panel.
     */
    public void enableAllWindowsPositioning(String profileId) {
        ServerProfile profile = profilesById.get(profileId);
        if (profile == null) {
            return;
        }
        for (ChatWindow w : profile.getWindows().values()) {
            w.setPositioningMode(w.isVisible());
        }
        save();
    }

    public void togglePosition(String profileId, String windowId) {
        ServerProfile profile = profilesById.get(profileId);
        if (profile == null) {
            return;
        }
        ChatWindow w = profile.getWindows().get(windowId);
        if (w == null) {
            return;
        }
        boolean next = !w.isPositioningMode();
        if (next) {
            for (ChatWindow o : profile.getWindows().values()) {
                o.setPositioningMode(false);
            }
        }
        w.setPositioningMode(next);
        save();
    }

    public boolean isPositioning() {
        ServerProfile p = getActiveProfile();
        if (p == null) {
            return false;
        }
        for (ChatWindow w : p.getWindows().values()) {
            if (w.isPositioningMode()) {
                return true;
            }
        }
        return false;
    }

    public void clearAllPositioningModes() {
        ServerProfile p = getActiveProfile();
        if (p == null) {
            return;
        }
        for (ChatWindow w : p.getWindows().values()) {
            w.setPositioningMode(false);
        }
    }

    /**
     * Windows for HUD / positioning / scroll: only the active profile for the current connection.
     */
    public Collection<ChatWindow> getActiveProfileWindows() {
        ServerProfile p = getActiveProfile();
        if (p == null) {
            return List.of();
        }
        return Collections.unmodifiableCollection(p.getWindows().values());
    }

    /** Clears stored lines in every chat window (all profiles), e.g. when vanilla chat clears (F3+D). */
    public void clearAllWindowChatHistory() {
        for (ServerProfile p : profiles) {
            for (ChatWindow w : p.getWindows().values()) {
                w.clearStoredChat();
            }
        }
    }

    public ChatIntercept interceptChat(Component message) {
        if (clientCommandFeedbackDepth > 0) {
            return ChatIntercept.NONE;
        }
        ServerProfile profile = getActiveProfile();
        if (profile == null) {
            return ChatIntercept.NONE;
        }
        String text = plainTextForMatching(message);
        if (text.isEmpty()) {
            return ChatIntercept.NONE;
        }
        if (matchesAnyIgnore(profile, text)) {
            return ChatIntercept.DROP;
        }
        for (ChatWindow w : profile.getWindows().values()) {
            if (w.matches(text)) {
                return ChatIntercept.WINDOWS;
            }
        }
        return ChatIntercept.NONE;
    }

    public void dispatchToWindows(Component message) {
        ServerProfile profile = getActiveProfile();
        if (profile == null) {
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
        for (ChatWindow w : profile.getWindows().values()) {
            if (w.matches(text)) {
                w.addLine(entry);
            }
        }
    }

    public ServerProfile getActiveProfile() {
        String host = currentConnectionHostNormalized();
        for (ServerProfile p : profiles) {
            if (profileMatchesConnection(p, host)) {
                return p;
            }
        }
        return null;
    }

    private void recompileIgnores(ServerProfile p) {
        List<Pattern> list = new ArrayList<>();
        for (String src : p.getIgnorePatternSources()) {
            if (src == null || src.strip().isEmpty()) {
                continue;
            }
            try {
                list.add(compileUserMatchPattern(src));
            } catch (PatternSyntaxException e) {
                LOGGER.warn("Bad ignore pattern in profile {}: {}", p.getId(), src, e);
            }
        }
        compiledIgnoresByProfile.put(p.getId(), list);
    }

    private boolean matchesAnyIgnore(ServerProfile p, String text) {
        List<Pattern> list = compiledIgnoresByProfile.get(p.getId());
        if (list == null || list.isEmpty()) {
            return false;
        }
        for (Pattern pat : list) {
            if (pat.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private void recompileMessageSounds(ServerProfile p) {
        List<CompiledMessageSound> list = new ArrayList<>();
        for (MessageSoundRule rule : p.getMessageSounds()) {
            if (rule.getPatternSource() == null || rule.getPatternSource().strip().isEmpty()) {
                continue;
            }
            Optional<Identifier> sid = parseSoundId(rule.getSoundId());
            if (sid.isEmpty() || BuiltInRegistries.SOUND_EVENT.get(sid.get()).isEmpty()) {
                LOGGER.warn("Skipping message sound in profile {}: bad sound id {}", p.getId(), rule.getSoundId());
                continue;
            }
            try {
                Pattern pat = compileUserMatchPattern(rule.getPatternSource());
                list.add(new CompiledMessageSound(pat, sid.get()));
            } catch (PatternSyntaxException e) {
                LOGGER.warn("Bad message sound pattern in profile {}: {}", p.getId(), rule.getPatternSource(), e);
            }
        }
        compiledMessageSoundsByProfile.put(p.getId(), list);
    }

    public static String currentConnectionHostNormalized() {
        Minecraft mc = Minecraft.getInstance();
        ServerData sd = mc.getCurrentServer();
        if (sd == null) {
            return null;
        }
        return stripPortFromAddress(sd.ip);
    }

    static boolean profileMatchesConnection(ServerProfile profile, String connectionHostNorm) {
        List<String> servers = profile.getServers();
        if (servers.isEmpty()) {
            return true;
        }
        if (connectionHostNorm == null || connectionHostNorm.isEmpty()) {
            return false;
        }
        String h = connectionHostNorm.toLowerCase(Locale.ROOT);
        for (String entry : servers) {
            if (entry == null || entry.strip().isEmpty()) {
                continue;
            }
            if (hostMatchesRule(h, entry.strip())) {
                return true;
            }
        }
        return false;
    }

    public static boolean hostMatchesRule(String connectionHostLower, String ruleRaw) {
        String rule = ruleRaw.strip().toLowerCase(Locale.ROOT);
        if (rule.isEmpty()) {
            return false;
        }
        if (isProbablyIpLiteral(connectionHostLower)) {
            return connectionHostLower.equals(rule);
        }
        return connectionHostLower.equals(rule) || connectionHostLower.endsWith("." + rule);
    }

    static boolean isProbablyIpLiteral(String h) {
        if (h.indexOf(':') >= 0) {
            return true;
        }
        for (int i = 0; i < h.length(); i++) {
            char c = h.charAt(i);
            if (c != '.' && !Character.isDigit(c)) {
                return false;
            }
        }
        return true;
    }

    public static String stripPortFromAddress(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        String s = raw.strip();
        if (s.startsWith("[")) {
            int end = s.indexOf(']');
            if (end > 1) {
                return s.substring(1, end).toLowerCase(Locale.ROOT);
            }
        }
        int lastColon = s.lastIndexOf(':');
        if (lastColon > 0) {
            String tail = s.substring(lastColon + 1);
            if (!tail.isEmpty() && tail.chars().allMatch(Character::isDigit)) {
                return s.substring(0, lastColon).toLowerCase(Locale.ROOT);
            }
        }
        return s.toLowerCase(Locale.ROOT);
    }

    private static String componentToLegacyLogString(Component message) {
        return ChatCopyTextHelper.toLegacySectionString(message);
    }

    static String plainTextForMatching(Component message) {
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
     * Plain text = literal substring; {@code regex:} prefix for Java regex (case-insensitive prefix).
     */
    public static Pattern compileUserMatchPattern(String input) throws PatternSyntaxException {
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

    public void save() {
        if (configPath == null) {
            return;
        }
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, gson.toJson(buildRootV3()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("Failed to save chat-utilities config", e);
        }
    }

    /** Same JSON shape as {@code chat-utilities.json} — for export to a file. */
    public String serializeProfilesToJson() {
        return gson.toJson(buildRootV3());
    }

    /**
     * Merges profiles from exported JSON. If an imported profile id already exists, a new id is assigned.
     *
     * @return number of profiles added
     */
    public int importProfilesFromJson(String json) throws JsonParseException {
        RootV3 root = gson.fromJson(json, RootV3.class);
        if (root == null || root.profiles == null) {
            throw new JsonParseException("Missing profiles array");
        }
        int added = 0;
        for (PersistedProfile pp : root.profiles) {
            if (pp == null || pp.id == null || pp.id.isEmpty()) {
                continue;
            }
            if (profilesById.containsKey(pp.id)) {
                pp.id = UUID.randomUUID().toString();
            }
            ServerProfile sp = buildProfileFromPersisted(pp);
            if (sp != null) {
                profiles.add(sp);
                profilesById.put(sp.getId(), sp);
                recompileIgnores(sp);
                recompileMessageSounds(sp);
                added++;
            }
        }
        if (added > 0) {
            save();
        }
        return added;
    }

    private RootV3 buildRootV3() {
        RootV3 root = new RootV3();
        root.formatVersion = FORMAT_VERSION_V3;
        for (ServerProfile sp : profiles) {
            PersistedProfile pp = new PersistedProfile();
            pp.id = sp.getId();
            pp.displayName = sp.getDisplayName();
            pp.servers = new ArrayList<>(sp.getServers());
            pp.ignorePatterns = new ArrayList<>(sp.getIgnorePatternSources());
            for (MessageSoundRule ms : sp.getMessageSounds()) {
                PersistedMessageSound pms = new PersistedMessageSound();
                pms.pattern = ms.getPatternSource();
                pms.soundId = ms.getSoundId();
                pp.messageSounds.add(pms);
            }
            for (ChatWindow w : sp.getWindows().values()) {
                PersistedWindow pw = new PersistedWindow();
                pw.id = w.getId();
                pw.patterns = new ArrayList<>(w.getPatternSources());
                pw.anchorX = w.getAnchorX();
                pw.anchorY = w.getAnchorY();
                pw.visible = w.isVisible();
                pw.widthFrac = w.getWidthFrac();
                pw.maxVisibleLines = w.getMaxVisibleLines();
                pp.windows.add(pw);
            }
            root.profiles.add(pp);
        }
        return root;
    }

    public void load() {
        profiles.clear();
        profilesById.clear();
        compiledIgnoresByProfile.clear();
        compiledMessageSoundsByProfile.clear();
        if (configPath == null) {
            return;
        }
        try {
            if (Files.isRegularFile(configPath)) {
                loadV3(Files.readString(configPath, StandardCharsets.UTF_8));
            } else if (Files.isRegularFile(legacyConfigPath)) {
                migrateFromLegacy(Files.readString(legacyConfigPath, StandardCharsets.UTF_8));
                save();
            }
        } catch (IOException | JsonParseException e) {
            LOGGER.error("Failed to load chat-utilities config", e);
        }
        for (ServerProfile p : profiles) {
            recompileIgnores(p);
            recompileMessageSounds(p);
        }
    }

    private void loadV3(String json) throws JsonParseException {
        RootV3 root = gson.fromJson(json, RootV3.class);
        if (root == null || root.profiles == null) {
            return;
        }
        for (PersistedProfile pp : root.profiles) {
            ServerProfile sp = buildProfileFromPersisted(pp);
            if (sp != null) {
                profiles.add(sp);
                profilesById.put(sp.getId(), sp);
            }
        }
    }

    private ServerProfile buildProfileFromPersisted(PersistedProfile pp) {
        if (pp == null || pp.id == null || pp.id.isEmpty()) {
            return null;
        }
        ServerProfile sp = new ServerProfile(pp.id, pp.displayName != null ? pp.displayName : pp.id);
        if (pp.servers != null) {
            sp.getServers().addAll(pp.servers);
        }
        if (pp.ignorePatterns != null) {
            sp.getIgnorePatternSources().addAll(pp.ignorePatterns);
        }
        if (pp.messageSounds != null) {
            for (PersistedMessageSound pms : pp.messageSounds) {
                if (pms == null || pms.pattern == null || pms.pattern.strip().isEmpty()) {
                    continue;
                }
                sp.getMessageSounds()
                        .add(new MessageSoundRule(pms.pattern, pms.soundId != null ? pms.soundId : ""));
            }
        }
        if (pp.windows != null) {
            for (PersistedWindow pw : pp.windows) {
                if (pw == null || pw.id == null) {
                    continue;
                }
                List<String> sources = new ArrayList<>();
                if (pw.patterns != null && !pw.patterns.isEmpty()) {
                    sources.addAll(pw.patterns);
                } else if (pw.regex != null && !pw.regex.isEmpty()) {
                    sources.add(pw.regex);
                }
                try {
                    List<Pattern> compiled = new ArrayList<>();
                    for (String src : sources) {
                        if (src == null || src.strip().isEmpty()) {
                            continue;
                        }
                        compiled.add(compileUserMatchPattern(src));
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
                    sp.getWindows().put(pw.id, cw);
                } catch (PatternSyntaxException e) {
                    LOGGER.warn("Skipping window {}: bad pattern", pw.id, e);
                }
            }
        }
        return sp;
    }

    private void migrateFromLegacy(String json) throws JsonParseException {
        LegacyRoot legacy = gson.fromJson(json, LegacyRoot.class);
        if (legacy == null || legacy.windows == null) {
            return;
        }
        ServerProfile sp = new ServerProfile(UUID.randomUUID().toString(), "Migrated");
        boolean legacyFormat = legacy.patternFormat == null || legacy.patternFormat < PATTERN_FORMAT_V2;
        for (PersistedWindow pw : legacy.windows) {
            if (pw == null || pw.id == null) {
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
                sp.getWindows().put(pw.id, cw);
            } catch (PatternSyntaxException e) {
                LOGGER.warn("Skipping migrated window {}: bad pattern", pw.id, e);
            }
        }
        profiles.add(sp);
        profilesById.put(sp.getId(), sp);
    }

    private static final class RootV3 {
        int formatVersion;
        List<PersistedProfile> profiles = new ArrayList<>();
    }

    private static final class PersistedProfile {
        String id;
        String displayName;
        List<String> servers = new ArrayList<>();
        List<String> ignorePatterns = new ArrayList<>();
        List<PersistedMessageSound> messageSounds = new ArrayList<>();
        List<PersistedWindow> windows = new ArrayList<>();
    }

    private static final class PersistedMessageSound {
        String pattern;
        String soundId;
    }

    private static final class CompiledMessageSound {
        final Pattern pattern;
        final Identifier soundId;

        CompiledMessageSound(Pattern pattern, Identifier soundId) {
            this.pattern = pattern;
            this.soundId = soundId;
        }
    }

    private static final class LegacyRoot {
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
