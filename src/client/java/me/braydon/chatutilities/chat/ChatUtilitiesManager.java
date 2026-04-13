package me.braydon.chatutilities.chat;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import me.braydon.chatutilities.mixin.client.ChatComponentAccess;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.Connection;
 
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.resources.Identifier;
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
import java.util.regex.Matcher;

public final class ChatUtilitiesManager {
    /** Used when a play-sound rule has no sound id yet (new row or toggled from ignore). */
    public static final Identifier DEFAULT_CHAT_ACTION_SOUND = Identifier.withDefaultNamespace("ui.button.click");

    public enum ChatIntercept {
        NONE,
        /** Hide from vanilla and custom windows. */
        DROP,
        /** Hide from vanilla; show in matching windows only. */
        WINDOWS
    }

    private static final Logger LOGGER = LoggerFactory.getLogger("chat-utilities");
    private static final Logger MINECRAFT_GAME_LOG = LoggerFactory.getLogger(Minecraft.class);
    private static final int PATTERN_FLAGS = Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE;
    private static final String REGEX_PREFIX = "regex:";
    private static final int PATTERN_FORMAT_V2 = 2;
    private static final int FORMAT_VERSION_V3 = 3;
    private static final int FORMAT_VERSION_V4 = 4;
    private static final long DEDUP_MS = 5L;
    private static final long SOUND_DEBOUNCE_MS = 1500L;

    private static final ChatUtilitiesManager INSTANCE = new ChatUtilitiesManager();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final List<ServerProfile> profiles = new ArrayList<>();
    private final Map<String, ServerProfile> profilesById = new HashMap<>();

    private Path configPath;
    private Path legacyConfigPath;
    private String lastDedupText = "";
    private long lastDedupAt;
    private int clientCommandFeedbackDepth;
    /**
     * Last profile that matched a known connection host. Used as a best-effort fallback during early connect where
     * {@link Minecraft#getCurrentServer()} may still be null, but chat can already start arriving.
     */
    private String lastKnownActiveProfileId;

    /**
     * Server hostname captured from the JOIN event handler ({@link net.minecraft.client.multiplayer.ClientPacketListener#getServerData()}).
     * {@code getServerData()} is always populated at JOIN time, unlike {@link Minecraft#getCurrentServer()} which
     * can lag behind on first login.  Stored here so that {@link #currentConnectionHostNormalized()} can return
     * the correct <em>hostname</em> (not just a raw IP) when messages arrive early in the connection lifecycle.
     * Cleared on disconnect.
     */
    private String cachedJoinServerHost;

    /**
     * Set to {@code true} by {@link #onLoginStart()} when the login phase begins (before any play-state
     * packets can arrive).  Cleared once {@link #onPlayJoin} fires.  Used by
     * {@link #shouldBufferAsEarlyMessage()} to decide whether to buffer incoming chat messages —
     * avoids relying on {@code Minecraft.getConnection()} which is null during very early connect.
     */
    private boolean loginPending;

    /**
     * Messages that arrived before {@link #onPlayJoin} fired (before {@code cachedJoinServerHost} was set).
     * These are buffered and replayed once the correct server profile is known.
     */
    private final List<Component> earlyMessageBuffer = new ArrayList<>();

    /**
     * When chat is routed only to custom windows, vanilla may still append a row in some environments; matching HUD
     * rows skip smooth-chat on that tick/plain so the effect only appears on the window.
     */
    private int vanillaSmoothSuppressGuiTick = -1;
    private String vanillaSmoothSuppressPlain = "";

    /** Leading chat timestamp (same idea as {@link VanillaChatRepeatStacker}) for smooth-suppress plain matching. */
    private static final Pattern VANILLA_SMOOTH_SUPPRESS_TS_PREFIX =
            Pattern.compile("^\\s*\\[\\d{1,2}:\\d{2}(?::\\d{2})?\\]\\s*");

    /**
     * After a vanilla stack merge, briefly boost opacity for that logical line when chat is unfocused so {@code (xN)}
     * updates stay readable without changing {@code addedTime} (no smooth-chat re-entry).
     */
    private int vanillaStackPulseGuiTick = Integer.MIN_VALUE;
    private long vanillaStackPulseUntilMs = 0L;

    /** {@link GuiMessage.Line#addedTime()} of the last stack merge; smooth-chat is suppressed until this tick. */
    private int vanillaStackSmoothSuppressMergeTick = Integer.MIN_VALUE;

    private int vanillaStackSmoothSuppressEndTick = Integer.MIN_VALUE;

    /** Snapshot of vanilla chat lines when leaving a world (oldest-first replay order). */
    private final List<Component> preservedVanillaChatSnapshot = new ArrayList<>();

    /** Compiled ignore patterns per profile id (rebuilt when ignores change). */
    private final Map<String, List<Pattern>> compiledIgnoresByProfile = new HashMap<>();

    /** Compiled message-sound rules per profile id. */
    private final Map<String, List<CompiledMessageSound>> compiledMessageSoundsByProfile = new HashMap<>();

    /**
     * Debounce for message sound triggers to avoid repeated playback when the client replays queued history lines
     * (e.g. during search/filter redraws on some versions/servers).
     */
    private final LinkedHashMap<String, Long> recentMessageSoundKeyToAt =
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > 256;
                }
            };

    /** Color-highlight rules per profile id (plain-text paint; see {@link #applyChatColorHighlights}). */
    private final Map<String, List<ChatActionColorHighlighter.Rule>> compiledColorHighlightsByProfile =
            new HashMap<>();

    /** Plain-text replacement rules per profile id. */
    private final Map<String, List<CompiledTextReplacement>> compiledTextReplacementsByProfile = new HashMap<>();

    /** Auto-response rules per profile id. */
    private final Map<String, List<CompiledAutoResponse>> compiledAutoResponsesByProfile = new HashMap<>();

    /** Re-entrancy guard for auto responses. */
    private int autoResponseDepth;

    /**
     * Debounce for auto-response sends to avoid repeated sends when vanilla rebuild paths replay existing messages
     * (e.g. during chat search/filter refreshes).
     */
    private static final long AUTO_RESPONSE_DEDUP_MS = 1500L;
    private final LinkedHashMap<String, Long> recentAutoResponseKeyToAt =
            new LinkedHashMap<>(256, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                    return size() > 256;
                }
            };

    /** After closing the GUI for window positioning, reopen this screen (consumed when applied). */
    private Supplier<Screen> restoreScreenAfterPosition;

    /**
     * Profile id for an in-progress “Adjust Layout” session from the menu. Not persisted — keeps HUD/tick in layout mode
     * even if per-window flags were lost on the same frame as opening chat.
     */
    private String layoutAdjustProfileId;

    private boolean layoutAdjustPointerDown;

    private ChatUtilitiesManager() {}

    public static ChatUtilitiesManager get() {
        return INSTANCE;
    }

    public void init() {
        Path dir = FabricLoader.getInstance().getConfigDir();
        this.configPath = dir.resolve("chat-utilities.json");
        this.legacyConfigPath = dir.resolve("chat-windows.json");
        this.restoreScreenAfterPosition = null;
        this.layoutAdjustProfileId = null;
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
        recompileChatActions(p);
        save();
        return p;
    }

    public void removeProfile(String id) {
        ServerProfile removed = profilesById.remove(id);
        if (removed != null) {
            profiles.remove(removed);
            compiledIgnoresByProfile.remove(id);
            compiledMessageSoundsByProfile.remove(id);
            compiledColorHighlightsByProfile.remove(id);
            save();
        }
    }

    public void markProfileServersDirty() {
        save();
    }

    /**
     * Adds an effect for {@code patternRaw}, merging into an existing {@link ChatActionGroup} when the pattern
     * string matches (after {@link String#strip()}).
     */
    public void addChatAction(
            ServerProfile profile, ChatActionEffect.Type type, String patternRaw, String soundRaw)
            throws PatternSyntaxException, IllegalArgumentException {
        addChatAction(profile, type, patternRaw, soundRaw, ChatActionEffect.DEFAULT_HIGHLIGHT_RGB);
    }

    public void addChatAction(
            ServerProfile profile,
            ChatActionEffect.Type type,
            String patternRaw,
            String soundRaw,
            int highlightRgb)
            throws PatternSyntaxException, IllegalArgumentException {
        addChatAction(profile, type, patternRaw, soundRaw, highlightRgb, false, false, false, false, false);
    }

    public void addChatAction(
            ServerProfile profile,
            ChatActionEffect.Type type,
            String patternRaw,
            String soundRaw,
            int highlightRgb,
            boolean highlightBold,
            boolean highlightItalic,
            boolean highlightUnderlined,
            boolean highlightStrikethrough,
            boolean highlightObfuscated)
            throws PatternSyntaxException, IllegalArgumentException {
        String pat = patternRaw == null ? "" : patternRaw.strip();
        compileUserMatchPattern(pat);
        ChatActionEffect effect =
                newChatActionEffect(
                        type,
                        soundRaw,
                        highlightRgb,
                        highlightBold,
                        highlightItalic,
                        highlightUnderlined,
                        highlightStrikethrough,
                        highlightObfuscated);
        for (ChatActionGroup g : profile.getChatActionGroups()) {
            if (pat.equals(g.getPatternSource().strip())) {
                g.getEffects().add(effect);
                recompileChatActions(profile);
                save();
                return;
            }
        }
        ChatActionGroup g = new ChatActionGroup(pat);
        g.addEffect(effect);
        profile.getChatActionGroups().add(g);
        recompileChatActions(profile);
        save();
    }

    public void addEffectToGroup(
            ServerProfile profile, int groupIndex, ChatActionEffect.Type type, String soundRaw)
            throws IllegalArgumentException {
        addEffectToGroup(profile, groupIndex, type, soundRaw, ChatActionEffect.DEFAULT_HIGHLIGHT_RGB);
    }

    public void addEffectToGroup(
            ServerProfile profile,
            int groupIndex,
            ChatActionEffect.Type type,
            String soundRaw,
            int highlightRgb)
            throws IllegalArgumentException {
        List<ChatActionGroup> groups = profile.getChatActionGroups();
        if (groupIndex < 0 || groupIndex >= groups.size()) {
            return;
        }
        ChatActionEffect effect = newChatActionEffect(type, soundRaw, highlightRgb);
        groups.get(groupIndex).getEffects().add(effect);
        recompileChatActions(profile);
        save();
    }

    public void removeChatActionGroupAt(ServerProfile profile, int groupIndex) {
        List<ChatActionGroup> groups = profile.getChatActionGroups();
        if (groupIndex >= 0 && groupIndex < groups.size()) {
            groups.remove(groupIndex);
            recompileChatActions(profile);
            save();
        }
    }

    private static final int MAX_COMMAND_ALIASES = 64;

    /**
     * Profile used when rewriting outgoing slash commands (aliases). Same fallback as incoming chat routing: active
     * server match, else the sole profile if only one exists.
     */
    public ServerProfile getEffectiveProfileForOutgoingCommands() {
        ServerProfile p = getActiveProfile();
        if (p != null) {
            return p;
        }
        String uiPreferred = ChatUtilitiesClientOptions.getLastMenuProfileId();
        if (uiPreferred != null) {
            ServerProfile byUi = profilesById.get(uiPreferred);
            if (byUi != null) {
                return byUi;
            }
        }
        if (lastKnownActiveProfileId != null) {
            ServerProfile stale = profilesById.get(lastKnownActiveProfileId);
            if (stale != null) {
                return stale;
            }
        }
        if (profiles.size() == 1) {
            return profiles.get(0);
        }
        if (!profiles.isEmpty()) {
            return profiles.get(0);
        }
        return null;
    }

    public boolean addCommandAlias(ServerProfile profile, String rawFrom, String rawTo) {
        if (profile == null) {
            return false;
        }
        try {
            CommandAlias c = new CommandAlias(rawFrom, rawTo);
            if (c.from().isEmpty() || c.to().isEmpty()) {
                return false;
            }
            for (CommandAlias x : profile.getCommandAliases()) {
                if (x.from().equals(c.from())) {
                    return false;
                }
            }
            if (profile.commandAliasesMutable().size() >= MAX_COMMAND_ALIASES) {
                return false;
            }
            profile.commandAliasesMutable().add(c);
            save();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean removeCommandAliasAt(ServerProfile profile, int index) {
        if (profile == null) {
            return false;
        }
        List<CommandAlias> list = profile.commandAliasesMutable();
        if (index < 0 || index >= list.size()) {
            return false;
        }
        list.remove(index);
        save();
        return true;
    }

    public boolean updateCommandAliasAt(ServerProfile profile, int index, String rawFrom, String rawTo) {
        if (profile == null) {
            return false;
        }
        List<CommandAlias> list = profile.commandAliasesMutable();
        if (index < 0 || index >= list.size()) {
            return false;
        }
        CommandAlias c = new CommandAlias(rawFrom, rawTo);
        if (c.from().isEmpty() || c.to().isEmpty()) {
            return false;
        }
        for (int i = 0; i < list.size(); i++) {
            if (i != index && list.get(i).from().equals(c.from())) {
                return false;
            }
        }
        list.set(index, c);
        save();
        return true;
    }

    public void removeChatActionEffectAt(ServerProfile profile, int groupIndex, int effectIndex) {
        List<ChatActionGroup> groups = profile.getChatActionGroups();
        if (groupIndex < 0 || groupIndex >= groups.size()) {
            return;
        }
        List<ChatActionEffect> effects = groups.get(groupIndex).getEffects();
        if (effectIndex < 0 || effectIndex >= effects.size()) {
            return;
        }
        effects.remove(effectIndex);
        if (effects.isEmpty()) {
            groups.remove(groupIndex);
        }
        recompileChatActions(profile);
        save();
    }

    public void setChatActionGroupPattern(ServerProfile profile, int groupIndex, String patternRaw)
            throws PatternSyntaxException {
        List<ChatActionGroup> groups = profile.getChatActionGroups();
        if (groupIndex < 0 || groupIndex >= groups.size()) {
            return;
        }
        String pat = patternRaw == null ? "" : patternRaw.strip();
        compileUserMatchPattern(pat);
        groups.get(groupIndex).setPatternSource(pat);
        recompileChatActions(profile);
        save();
    }

    public void setChatActionEffectAt(
            ServerProfile profile,
            int groupIndex,
            int effectIndex,
            ChatActionEffect.Type type,
            String soundRaw)
            throws IllegalArgumentException {
        List<ChatActionGroup> groups = profile.getChatActionGroups();
        if (groupIndex < 0 || groupIndex >= groups.size()) {
            return;
        }
        List<ChatActionEffect> effects = groups.get(groupIndex).getEffects();
        if (effectIndex < 0 || effectIndex >= effects.size()) {
            return;
        }
        ChatActionEffect e = effects.get(effectIndex);
        ChatActionEffect.Type oldType = e.getType();
        e.setType(type == null ? ChatActionEffect.Type.IGNORE : type);
        if (e.getType() == ChatActionEffect.Type.PLAY_SOUND) {
            Identifier soundId = resolvePlaySoundId(soundRaw);
            e.setSoundId(soundId.toString());
            e.setTargetText("");
        } else if (e.getType() == ChatActionEffect.Type.TEXT_REPLACEMENT || e.getType() == ChatActionEffect.Type.AUTO_RESPONSE) {
            e.setSoundId("");
            e.setTargetText(soundRaw == null ? "" : soundRaw);
        } else {
            e.setSoundId("");
            e.setTargetText("");
        }
        if (e.getType() == ChatActionEffect.Type.COLOR_HIGHLIGHT) {
            if (oldType != ChatActionEffect.Type.COLOR_HIGHLIGHT) {
                e.setHighlightColorRgb(ChatActionEffect.DEFAULT_HIGHLIGHT_RGB);
                e.setHighlightBold(false);
                e.setHighlightItalic(false);
                e.setHighlightUnderlined(false);
                e.setHighlightStrikethrough(false);
                e.setHighlightObfuscated(false);
            }
        } else {
            e.setHighlightColorRgb(ChatActionEffect.DEFAULT_HIGHLIGHT_RGB);
            e.setHighlightBold(false);
            e.setHighlightItalic(false);
            e.setHighlightUnderlined(false);
            e.setHighlightStrikethrough(false);
            e.setHighlightObfuscated(false);
        }
        recompileChatActions(profile);
        save();
    }

    public void setChatActionHighlightRgb(ServerProfile profile, int groupIndex, int effectIndex, int rgb) {
        List<ChatActionGroup> groups = profile.getChatActionGroups();
        if (groupIndex < 0 || groupIndex >= groups.size()) {
            return;
        }
        List<ChatActionEffect> effects = groups.get(groupIndex).getEffects();
        if (effectIndex < 0 || effectIndex >= effects.size()) {
            return;
        }
        ChatActionEffect e = effects.get(effectIndex);
        if (e.getType() != ChatActionEffect.Type.COLOR_HIGHLIGHT) {
            return;
        }
        e.setHighlightColorRgb(rgb);
        recompileChatActions(profile);
        save();
    }

    public void setChatActionHighlightStyle(
            ServerProfile profile,
            int groupIndex,
            int effectIndex,
            int rgb,
            boolean bold,
            boolean italic,
            boolean underlined,
            boolean strikethrough,
            boolean obfuscated) {
        List<ChatActionGroup> groups = profile.getChatActionGroups();
        if (groupIndex < 0 || groupIndex >= groups.size()) {
            return;
        }
        List<ChatActionEffect> effects = groups.get(groupIndex).getEffects();
        if (effectIndex < 0 || effectIndex >= effects.size()) {
            return;
        }
        ChatActionEffect e = effects.get(effectIndex);
        if (e.getType() != ChatActionEffect.Type.COLOR_HIGHLIGHT) {
            return;
        }
        e.setHighlightColorRgb(rgb);
        e.setHighlightBold(bold);
        e.setHighlightItalic(italic);
        e.setHighlightUnderlined(underlined);
        e.setHighlightStrikethrough(strikethrough);
        e.setHighlightObfuscated(obfuscated);
        recompileChatActions(profile);
        save();
    }

    private static ChatActionEffect newChatActionEffect(
            ChatActionEffect.Type type,
            String soundRaw,
            int highlightRgb,
            boolean hb,
            boolean hi,
            boolean hu,
            boolean hs,
            boolean ho)
            throws IllegalArgumentException {
        ChatActionEffect.Type t = type == null ? ChatActionEffect.Type.IGNORE : type;
        if (t == ChatActionEffect.Type.PLAY_SOUND) {
            Identifier soundId = resolvePlaySoundId(soundRaw);
            return new ChatActionEffect(t, soundId.toString(), "", ChatActionEffect.DEFAULT_HIGHLIGHT_RGB, false, false, false, false, false);
        }
        if (t == ChatActionEffect.Type.COLOR_HIGHLIGHT) {
            return new ChatActionEffect(t, "", "", highlightRgb, hb, hi, hu, hs, ho);
        }
        if (t == ChatActionEffect.Type.TEXT_REPLACEMENT || t == ChatActionEffect.Type.AUTO_RESPONSE) {
            String target = soundRaw == null ? "" : soundRaw;
            return new ChatActionEffect(t, "", target, ChatActionEffect.DEFAULT_HIGHLIGHT_RGB, false, false, false, false, false);
        }
        return new ChatActionEffect(ChatActionEffect.Type.IGNORE, "", "", ChatActionEffect.DEFAULT_HIGHLIGHT_RGB, false, false, false, false, false);
    }

    private static ChatActionEffect newChatActionEffect(ChatActionEffect.Type type, String soundRaw, int highlightRgb)
            throws IllegalArgumentException {
        return newChatActionEffect(type, soundRaw, highlightRgb, false, false, false, false, false);
    }

    private static Identifier resolvePlaySoundId(String soundRaw) {
        String s = soundRaw == null ? "" : soundRaw.strip();
        if (s.isEmpty()) {
            if (BuiltInRegistries.SOUND_EVENT.get(DEFAULT_CHAT_ACTION_SOUND).isEmpty()) {
                throw new IllegalArgumentException("Missing default sound: " + DEFAULT_CHAT_ACTION_SOUND);
            }
            return DEFAULT_CHAT_ACTION_SOUND;
        }
        Identifier soundId =
                parseSoundId(s).orElseThrow(() -> new IllegalArgumentException("Invalid sound id"));
        if (BuiltInRegistries.SOUND_EVENT.get(soundId).isEmpty()) {
            throw new IllegalArgumentException("Unknown sound: " + soundId);
        }
        return soundId;
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
        ServerProfile profile = effectiveProfileForIncomingChat();
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
        String selfName = localPlayerName();
        boolean ignoreSelf =
                ChatUtilitiesClientOptions.isIgnoreSelfInChatActions()
                        && isLikelySelfChatLine(text, selfName)
                        && selfName != null
                        && !selfName.isBlank();
        String quotedSelf = ignoreSelf ? Pattern.quote(selfName.strip()) : null;
        List<CompiledMessageSound> rules = compiledMessageSoundsByProfile.get(profile.getId());
        if (rules == null || rules.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        for (CompiledMessageSound r : rules) {
            if (quotedSelf != null && quotedSelf.equals(r.pattern.pattern())) {
                continue;
            }
            if (r.pattern.matcher(text).find()) {
                long now = System.currentTimeMillis();
                String k = profile.getId() + "|" + r.soundId + "|" + text;
                Long last;
                synchronized (recentMessageSoundKeyToAt) {
                    last = recentMessageSoundKeyToAt.get(k);
                    if (last == null || now - last >= SOUND_DEBOUNCE_MS) {
                        recentMessageSoundKeyToAt.put(k, now);
                        last = null;
                    }
                }
                if (last != null) {
                    continue;
                }
                BuiltInRegistries.SOUND_EVENT
                        .get(r.soundId)
                        .map(Holder::value)
                        .ifPresent(evt -> mc.getSoundManager().play(SimpleSoundInstance.forUI(evt, 1.0F)));
            }
        }
    }

    /**
     * Applies plain-text replacements (TEXT_REPLACEMENT) for the active profile.
     *
     * <p>Implementation is intentionally simple: replacements operate on the plain-text match string and the result is
     * re-emitted as a literal component.
     */
    public Component applyChatTextReplacementsIfApplicable(Component message) {
        if (clientCommandFeedbackDepth > 0) {
            return message;
        }
        ServerProfile profile = effectiveProfileForIncomingChat();
        if (profile == null) {
            return message;
        }
        String text = plainTextForMatching(message);
        if (text.isEmpty()) {
            return message;
        }
        if (matchesAnyIgnore(profile, text)) {
            return message;
        }
        List<CompiledTextReplacement> rules = compiledTextReplacementsByProfile.get(profile.getId());
        if (rules == null || rules.isEmpty()) {
            return message;
        }
        String selfName = localPlayerName();
        boolean ignoreSelf =
                ChatUtilitiesClientOptions.isIgnoreSelfInChatActions()
                        && isLikelySelfChatLine(text, selfName)
                        && selfName != null
                        && !selfName.isBlank();
        String quotedSelf = ignoreSelf ? Pattern.quote(selfName.strip()) : null;

        String out = text;
        boolean changed = false;
        for (CompiledTextReplacement r : rules) {
            if (quotedSelf != null && quotedSelf.equals(r.pattern.pattern())) {
                continue;
            }
            if (r.pattern.matcher(out).find()) {
                out = r.pattern.matcher(out).replaceAll(Matcher.quoteReplacement(r.targetText));
                changed = true;
            }
        }
        return changed ? Component.literal(out) : message;
    }

    /** Triggers AUTO_RESPONSE effects for the active profile. */
    public void triggerAutoResponsesIfApplicable(Component message) {
        if (clientCommandFeedbackDepth > 0) {
            return;
        }
        if (autoResponseDepth > 0) {
            return;
        }
        ServerProfile profile = effectiveProfileForIncomingChat();
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
        List<CompiledAutoResponse> rules = compiledAutoResponsesByProfile.get(profile.getId());
        if (rules == null || rules.isEmpty()) {
            return;
        }
        String selfName = localPlayerName();
        boolean ignoreSelf =
                ChatUtilitiesClientOptions.isIgnoreSelfInChatActions()
                        && isLikelySelfChatLine(text, selfName)
                        && selfName != null
                        && !selfName.isBlank();
        String quotedSelf = ignoreSelf ? Pattern.quote(selfName.strip()) : null;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.player.connection == null) {
            return;
        }
        autoResponseDepth++;
        try {
            for (CompiledAutoResponse r : rules) {
                if (quotedSelf != null && quotedSelf.equals(r.pattern.pattern())) {
                    continue;
                }
                if (r.responseText == null || r.responseText.isBlank()) {
                    continue;
                }
                if (r.pattern.matcher(text).find()) {
                    long now = System.currentTimeMillis();
                    String key =
                            profile.getId()
                                    + "\n"
                                    + r.pattern.pattern()
                                    + "\n"
                                    + r.responseText.strip()
                                    + "\n"
                                    + text;
                    Long last;
                    synchronized (recentAutoResponseKeyToAt) {
                        last = recentAutoResponseKeyToAt.get(key);
                        if (last == null || (now - last) >= AUTO_RESPONSE_DEDUP_MS) {
                            recentAutoResponseKeyToAt.put(key, now);
                            last = null;
                        }
                    }
                    if (last != null) {
                        continue;
                    }
                    // Mirror vanilla click command dispatch style.
                    mc.player.connection.sendChat(r.responseText.strip());
                }
            }
        } finally {
            autoResponseDepth--;
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
        return addPattern(profile, windowId, w.getDefaultTabId(), patternInput);
    }

    public boolean addPattern(ServerProfile profile, String windowId, String tabId, String patternInput)
            throws PatternSyntaxException {
        ChatWindow w = profile.getWindows().get(windowId);
        if (w == null) {
            return false;
        }
        ChatWindowTab tab = w.getTabById(tabId);
        if (tab == null) {
            return false;
        }
        tab.addPattern(compileUserMatchPattern(patternInput), patternInput.strip());
        save();
        return true;
    }

    public boolean removePattern(ServerProfile profile, String windowId, int userPosition) {
        ChatWindow w = profile.getWindows().get(windowId);
        if (w == null) {
            return false;
        }
        return removePattern(profile, windowId, w.getDefaultTabId(), userPosition);
    }

    public boolean removePattern(ServerProfile profile, String windowId, String tabId, int userPosition) {
        ChatWindow w = profile.getWindows().get(windowId);
        if (w == null) {
            return false;
        }
        ChatWindowTab tab = w.getTabById(tabId);
        if (tab == null || !tab.removePatternAtUserIndex(userPosition)) {
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
        return setPatternAt(profile, windowId, w.getDefaultTabId(), userPosition, patternInput);
    }

    public boolean setPatternAt(
            ServerProfile profile, String windowId, String tabId, int userPosition, String patternInput)
            throws PatternSyntaxException {
        ChatWindow w = profile.getWindows().get(windowId);
        if (w == null) {
            return false;
        }
        ChatWindowTab tab = w.getTabById(tabId);
        if (tab == null) {
            return false;
        }
        Pattern p = compileUserMatchPattern(patternInput);
        if (!tab.setPatternAtUserIndex(userPosition, p, patternInput.strip())) {
            return false;
        }
        save();
        return true;
    }

    public boolean addChatWindowTab(ServerProfile profile, String windowId, String displayName) {
        ChatWindow w = profile.getWindows().get(windowId);
        if (w == null) {
            return false;
        }
        String name = displayName == null ? "" : displayName.strip();
        if (name.isEmpty()) {
            return false;
        }
        w.addTab(new ChatWindowTab(UUID.randomUUID().toString(), name));
        save();
        return true;
    }

    public boolean removeChatWindowTab(ServerProfile profile, String windowId, String tabId) {
        ChatWindow w = profile.getWindows().get(windowId);
        if (w == null) {
            return false;
        }
        if (!w.removeTabById(tabId)) {
            return false;
        }
        save();
        return true;
    }

    public boolean renameChatWindowTab(ServerProfile profile, String windowId, String tabId, String newName) {
        ChatWindow w = profile.getWindows().get(windowId);
        if (w == null) {
            return false;
        }
        ChatWindowTab tab = w.getTabById(tabId);
        if (tab == null) {
            return false;
        }
        String n = newName == null ? "" : newName.strip();
        if (n.isEmpty()) {
            return false;
        }
        tab.setDisplayName(n);
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
     * Puts every window in the given profile into positioning mode so they can be dragged from chat (including hidden
     * windows — otherwise {@code setPositioningMode(visible)} alone left nothing to grab when all were hidden).
     */
    public void enableAllWindowsPositioning(String profileId) {
        ServerProfile profile = profilesById.get(profileId);
        if (profile == null) {
            return;
        }
        this.layoutAdjustProfileId = profileId;
        for (ChatWindow w : profile.getWindows().values()) {
            w.setPositioningMode(true);
        }
        save();
    }

    public void togglePosition(String profileId, String windowId) {
        this.layoutAdjustProfileId = null;
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

    /**
     * True if any window on any profile is in layout mode. Must not be limited to {@link #getActiveProfile()} —
     * "Adjust Layout" enables positioning on the profile being edited in the menu, which may differ from the
     * connection-matched profile (or there may be no active profile in singleplayer / before join).
     */
    public boolean isPositioning() {
        if (layoutAdjustProfileId != null) {
            return true;
        }
        for (ServerProfile p : profiles) {
            for (ChatWindow w : p.getWindows().values()) {
                if (w.isPositioningMode()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * True while the user is actively dragging or resizing a window in adjust-layout mode (hides on-screen help that
     * would sit under the pointer).
     */
    public boolean isLayoutAdjustPointerDown() {
        return layoutAdjustPointerDown;
    }

    void setLayoutAdjustPointerDown(boolean layoutAdjustPointerDown) {
        this.layoutAdjustPointerDown = layoutAdjustPointerDown;
    }

    /** Vertical lines (gui X) drawn while resizing chat windows in adjust-layout when a snap target is hit. */
    private final List<Integer> layoutSnapGuideVerticalXs = new ArrayList<>();

    public void clearLayoutSnapGuides() {
        layoutSnapGuideVerticalXs.clear();
    }

    public void noteLayoutSnapGuideVertical(int x) {
        if (!layoutSnapGuideVerticalXs.contains(x)) {
            layoutSnapGuideVerticalXs.add(x);
        }
    }

    public List<Integer> layoutSnapGuideVerticalXs() {
        return layoutSnapGuideVerticalXs;
    }

    public void clearAllPositioningModes() {
        layoutAdjustProfileId = null;
        for (ServerProfile p : profiles) {
            for (ChatWindow w : p.getWindows().values()) {
                w.setPositioningMode(false);
            }
        }
    }

    /**
     * Gray handles / opaque layout geometry: true when the window flag is set or it belongs to the active Adjust Layout
     * profile (session id survives one frame where flags might not).
     */
    public boolean showsLayoutChrome(ChatWindow w) {
        if (w.isPositioningMode()) {
            return true;
        }
        if (layoutAdjustProfileId == null) {
            return false;
        }
        ServerProfile p = profilesById.get(layoutAdjustProfileId);
        return p != null && p.getWindow(w.getId()) == w;
    }

    /**
     * Windows in layout mode for dragging/resizing, in stable profile/window order (top-most hit-test last).
     */
    public List<ChatWindow> getPositioningLayoutWindows() {
        if (layoutAdjustProfileId != null) {
            ServerProfile p = profilesById.get(layoutAdjustProfileId);
            if (p != null) {
                return new ArrayList<>(p.getWindows().values());
            }
        }
        List<ChatWindow> out = new ArrayList<>();
        for (ServerProfile p : profiles) {
            for (ChatWindow w : p.getWindows().values()) {
                if (w.isPositioningMode()) {
                    out.add(w);
                }
            }
        }
        return out;
    }

    /**
     * Windows drawn on the HUD: active connection profile normally; while arranging layout, windows from the adjust
     * session profile or any profile with per-window layout mode.
     */
    public Collection<ChatWindow> getHudChatWindows() {
        if (isPositioning()) {
            return getPositioningLayoutWindows();
        }
        return getActiveProfileWindows();
    }

    /**
     * Windows for chat routing / scroll reset / normal HUD when not in layout mode: only the active profile for the
     * current connection.
     */
    public Collection<ChatWindow> getActiveProfileWindows() {
        ServerProfile p = effectiveProfileForIncomingChat();
        if (p == null) {
            return List.of();
        }
        return Collections.unmodifiableCollection(p.getWindows().values());
    }

    /**
     * Clears stored lines for the active profile's chat windows.
     *
     * <p>Vanilla "clear chat" is inherently connection-scoped; clearing every profile at once feels wrong and makes
     * chat windows appear "randomly" wiped when swapping servers.
     */
    public void clearActiveProfileWindowChatHistory() {
        ServerProfile p = effectiveProfileForIncomingChat();
        if (p == null) {
            return;
        }
        for (ChatWindow w : p.getWindows().values()) {
            w.clearStoredChat();
        }
    }

    public void markVanillaSmoothSuppressForWindowOnlyChat(String plain, int guiTick) {
        vanillaSmoothSuppressPlain = plainNormalizedForVanillaSmoothSuppress(plain == null ? "" : plain);
        vanillaSmoothSuppressGuiTick = guiTick;
    }

    private static String plainNormalizedForVanillaSmoothSuppress(String plain) {
        Matcher m = VANILLA_SMOOTH_SUPPRESS_TS_PREFIX.matcher(plain);
        if (m.find()) {
            plain = plain.substring(m.end());
        }
        return plain.strip();
    }

    public boolean shouldSuppressVanillaSmoothForLine(GuiMessage.Line line) {
        if (!ChatUtilitiesClientOptions.isSmoothChat()) {
            return false;
        }
        // Stack merge: no smooth slide/fade replay for the merged row until the normal fade window has passed.
        if (line.addedTime() == vanillaStackSmoothSuppressMergeTick) {
            Minecraft mc = Minecraft.getInstance();
            int now = mc != null ? mc.gui.getGuiTicks() : Integer.MAX_VALUE;
            if (now <= vanillaStackSmoothSuppressEndTick) {
                return true;
            }
        }
        // Optional short opacity bump (same tick id as merge).
        if (line.addedTime() == vanillaStackPulseGuiTick
                && System.currentTimeMillis() <= vanillaStackPulseUntilMs) {
            return true;
        }
        if (vanillaSmoothSuppressGuiTick < 0) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        int nowTick = mc != null ? mc.gui.getGuiTicks() : vanillaSmoothSuppressGuiTick;
        if (nowTick > vanillaSmoothSuppressGuiTick + 3) {
            vanillaSmoothSuppressGuiTick = -1;
            return false;
        }
        if (line.addedTime() != vanillaSmoothSuppressGuiTick) {
            return false;
        }
        String linePlain =
                plainNormalizedForVanillaSmoothSuppress(plainTextForMatching(line.content()));
        return vanillaSmoothSuppressPlain.equals(linePlain);
    }

    /**
     * Called after the repeat stacker replaces two lines with one. {@code mergeGuiTick} is the new line's
     * {@link GuiMessage#addedTime()} (current GUI tick).
     */
    public void noteVanillaStackMergeForHud(int mergeGuiTick) {
        vanillaStackPulseGuiTick = mergeGuiTick;
        vanillaStackPulseUntilMs = System.currentTimeMillis() + 220L;
        vanillaStackSmoothSuppressMergeTick = mergeGuiTick;
        int fadeMs = ChatUtilitiesClientOptions.getSmoothChatFadeMs();
        int durTicks = Math.max(1, Mth.ceil(fadeMs / (1000f / 20f)));
        vanillaStackSmoothSuppressEndTick = mergeGuiTick + durTicks + 2;
    }

    /** Extra opacity (0–1) for the merged vanilla line while the pulse is active. */
    public float vanillaStackPulseOpacityBoost(int lineAddedTime) {
        if (System.currentTimeMillis() > vanillaStackPulseUntilMs) {
            return 0f;
        }
        if (lineAddedTime != vanillaStackPulseGuiTick) {
            return 0f;
        }
        float u =
                (vanillaStackPulseUntilMs - System.currentTimeMillis()) / 220f;
        float w = Mth.clamp(u, 0f, 1f);
        return 0.35f * w * w;
    }

    /**
     * Copies vanilla chat into {@link #preservedVanillaChatSnapshot} when the option is on and the log is non-empty.
     * Does not replace an existing snapshot with an empty log (so a later hook does not wipe a good capture).
     */
    public void snapshotVanillaChatIfPreserving(Minecraft mc) {
        if (!ChatUtilitiesClientOptions.isPreserveVanillaChatOnDisconnect()) {
            preservedVanillaChatSnapshot.clear();
            return;
        }
        if (mc == null || mc.gui == null) {
            return;
        }
        ChatComponentAccess access = (ChatComponentAccess) mc.gui.getChat();
        List<GuiMessage> all = access.chatUtilities$getAllMessages();
        if (all.isEmpty()) {
            return;
        }
        preservedVanillaChatSnapshot.clear();
        for (int i = all.size() - 1; i >= 0; i--) {
            GuiMessage gm = all.get(i);
            if (gm != null && gm.content() != null) {
                preservedVanillaChatSnapshot.add(gm.content());
            }
        }
    }

    /** Replays {@link #preservedVanillaChatSnapshot} into vanilla chat if the log is still empty (main thread). */
    public void restoreVanillaChatSnapshotIfPreserving() {
        if (!ChatUtilitiesClientOptions.isPreserveVanillaChatOnDisconnect()) {
            preservedVanillaChatSnapshot.clear();
            return;
        }
        if (preservedVanillaChatSnapshot.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gui == null) {
            return;
        }
        ChatComponentAccess access = (ChatComponentAccess) mc.gui.getChat();
        if (!access.chatUtilities$getAllMessages().isEmpty()) {
            return;
        }
        List<Component> lines = new ArrayList<>(preservedVanillaChatSnapshot);
        preservedVanillaChatSnapshot.clear();
        for (Component c : lines) {
            mc.gui.getChat().addMessage(c);
        }
    }

    /**
     * Returns {@code true} when incoming chat should be buffered rather than processed immediately.
     * This happens when a multiplayer connection is active but {@link #onPlayJoin} has not yet fired
     * (i.e. the server profile is not yet known).  Messages are held in {@link #earlyMessageBuffer}
     * and replayed with the correct profile once {@link #onPlayJoin} sets {@code cachedJoinServerHost}.
     */
    public boolean shouldBufferAsEarlyMessage() {
        if (!loginPending) {
            return false;
        }
        if (cachedJoinServerHost != null) {
            return false;
        }
        if (clientCommandFeedbackDepth > 0) {
            return false;
        }
        return true;
    }

    /** Adds {@code message} to the early-message buffer (called when {@link #shouldBufferAsEarlyMessage()} is true). */
    public void bufferEarlyMessage(Component message) {
        if (message != null) {
            earlyMessageBuffer.add(message);
        }
    }

    public ChatIntercept interceptChat(Component message) {
        if (clientCommandFeedbackDepth > 0) {
            return ChatIntercept.NONE;
        }
        ServerProfile profile = effectiveProfileForIncomingChat();
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
        ServerProfile profile = effectiveProfileForIncomingChat();
        if (profile == null) {
            return;
        }
        Component toStore = applyChatColorHighlights(message);
        String text = plainTextForMatching(toStore);
        if (text.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (text.equals(lastDedupText) && now - lastDedupAt < DEDUP_MS) {
            return;
        }
        lastDedupText = text;
        lastDedupAt = now;
        String legacyLog = componentToLegacyLogString(toStore);
        MINECRAFT_GAME_LOG.info("[CHAT] {}", legacyLog.isEmpty() ? text : legacyLog);
        int tick = Minecraft.getInstance().gui.getGuiTicks();
        ChatWindowLine entry = ChatWindowLine.single(toStore, tick, now);
        for (ChatWindow w : profile.getWindows().values()) {
            if (w.matches(text)) {
                w.addLineToMatchingTabs(entry, text);
            }
        }
    }

    public ServerProfile getActiveProfile() {
        String host = currentConnectionHostNormalized();
        for (ServerProfile p : profiles) {
            if (profileMatchesConnection(p, host)) {
                lastKnownActiveProfileId = p.getId();
                return p;
            }
        }
        return null;
    }

    /**
     * Called when the client fully enters the play state (via {@link net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents#JOIN}).
     *
     * <p>Sets {@code cachedJoinServerHost} so that {@link #effectiveProfileForIncomingChat()} can
     * resolve the correct profile immediately.  Any messages that arrived before this point (early
     * proxy/plugin lines) are replayed through the full routing pipeline.
     *
     * @param serverHost hostname from {@code handler.getServerData().ip} at JOIN time, already port-stripped.
     *                   May be {@code null} if the server data was unavailable (rare).
     */
    /**
     * Called when the client starts the login phase for a new connection.  Sets {@link #loginPending}
     * so that any chat arriving before {@link #onPlayJoin} is buffered rather than processed under
     * the wrong profile.
     */
    public void onLoginStart() {
        loginPending = true;
    }

    public void onPlayJoin(String serverHost) {
        loginPending = false;
        if (serverHost != null && !serverHost.isBlank()) {
            cachedJoinServerHost = serverHost;
        }
        // Eagerly resolve; sets lastKnownActiveProfileId as a side-effect.
        getActiveProfile();
        // Replay messages that arrived before the JOIN event fired (e.g. proxy welcome lines,
        // MOTD-style chat packets sent during the login sequence).  Now that cachedJoinServerHost
        // is set the correct profile will be used for routing.
        replayEarlyMessages();
        restoreVanillaChatSnapshotIfPreserving();
    }

    /**
     * Called on disconnect to clear per-connection state so stale profile selections from a
     * previous server do not leak into the next connection.
     */
    public void onPlayDisconnect() {
        snapshotVanillaChatIfPreserving(Minecraft.getInstance());
        loginPending = false;
        lastKnownActiveProfileId = null;
        cachedJoinServerHost = null;
        earlyMessageBuffer.clear();
        vanillaSmoothSuppressGuiTick = -1;
        vanillaStackPulseGuiTick = Integer.MIN_VALUE;
        vanillaStackPulseUntilMs = 0L;
        vanillaStackSmoothSuppressMergeTick = Integer.MIN_VALUE;
        vanillaStackSmoothSuppressEndTick = Integer.MIN_VALUE;
        if (!ChatUtilitiesClientOptions.isPreserveVanillaChatOnDisconnect()) {
            preservedVanillaChatSnapshot.clear();
        }
    }

    /**
     * Replays messages that arrived before {@link #onPlayJoin} fired, now that the correct server
     * profile is known.  Each message is fed back through {@link ChatComponent#addMessage(Component)}
     * so the full mixin pipeline (auto-responses, text replacements, routing) runs with the correct
     * profile.  Must be called from the main client thread.
     */
    private void replayEarlyMessages() {
        if (earlyMessageBuffer.isEmpty()) {
            return;
        }
        List<Component> pending = new ArrayList<>(earlyMessageBuffer);
        earlyMessageBuffer.clear();
        Minecraft mc = Minecraft.getInstance();
        for (Component msg : pending) {
            mc.gui.getChat().addMessage(msg);
        }
    }

    /**
     * Profile used when applying chat actions (sounds, highlights, routing) to incoming messages. Falls back to the
     * only configured profile when the connection does not match any server list, so a single profile still works if
     * the hostname was not added under Edit Profile.
     */
    private ServerProfile effectiveProfileForIncomingChat() {
        String host = currentConnectionHostNormalized();
        if (host != null && !host.isEmpty()) {
            ServerProfile p = getActiveProfile();
            if (p != null) {
                return p;
            }
            // Connected to a server whose address does not match any profile — do not keep routing under a stale
            // menu-selected profile from a previous session.
            return null;
        }
        // Offline / no resolved host: keep fallbacks so a single profile still works without server entries.
        String uiPreferred = ChatUtilitiesClientOptions.getLastMenuProfileId();
        if (uiPreferred != null) {
            ServerProfile p = profilesById.get(uiPreferred);
            if (p != null) {
                return p;
            }
        }
        if (lastKnownActiveProfileId != null) {
            ServerProfile p = profilesById.get(lastKnownActiveProfileId);
            if (p != null) {
                return p;
            }
        }
        if (!profiles.isEmpty()) {
            return profiles.get(0);
        }
        return null;
    }

    /**
     * Profile used for incoming events not tied to a specific message (e.g. server-triggered chat clear).
     *
     * <p>Matches the routing behavior of {@link #effectiveProfileForIncomingChat()}.
     */
    public ServerProfile getEffectiveProfileForIncomingEvents() {
        return effectiveProfileForIncomingChat();
    }

    private void recompileChatActions(ServerProfile p) {
        List<Pattern> ignores = new ArrayList<>();
        List<CompiledMessageSound> sounds = new ArrayList<>();
        List<ChatActionColorHighlighter.Rule> highlights = new ArrayList<>();
        List<CompiledTextReplacement> replacements = new ArrayList<>();
        List<CompiledAutoResponse> autoResponses = new ArrayList<>();
        for (ChatActionGroup group : p.getChatActionGroups()) {
            if (group.getPatternSource() == null || group.getPatternSource().strip().isEmpty()) {
                continue;
            }
            Pattern pat;
            try {
                pat = compileUserMatchPattern(group.getPatternSource().strip());
            } catch (PatternSyntaxException e) {
                LOGGER.warn("Bad chat action pattern in profile {}: {}", p.getId(), group.getPatternSource(), e);
                continue;
            }
            for (ChatActionEffect effect : group.getEffects()) {
                if (effect.getType() == ChatActionEffect.Type.IGNORE) {
                    ignores.add(pat);
                } else if (effect.getType() == ChatActionEffect.Type.PLAY_SOUND) {
                    Optional<Identifier> sid = parseSoundId(effect.getSoundId());
                    if (sid.isEmpty() || BuiltInRegistries.SOUND_EVENT.get(sid.get()).isEmpty()) {
                        LOGGER.warn(
                                "Skipping message sound in profile {}: bad sound id {}",
                                p.getId(),
                                effect.getSoundId());
                        continue;
                    }
                    sounds.add(new CompiledMessageSound(pat, sid.get()));
                } else if (effect.getType() == ChatActionEffect.Type.COLOR_HIGHLIGHT) {
                    highlights.add(
                            new ChatActionColorHighlighter.Rule(
                                    pat,
                                    ChatActionColorHighlighter.highlightOverlayStyle(
                                            effect.getHighlightColorRgb(),
                                            effect.isHighlightBold(),
                                            effect.isHighlightItalic(),
                                            effect.isHighlightUnderlined(),
                                            effect.isHighlightStrikethrough(),
                                            effect.isHighlightObfuscated())));
                } else if (effect.getType() == ChatActionEffect.Type.TEXT_REPLACEMENT) {
                    replacements.add(new CompiledTextReplacement(pat, effect.getTargetText()));
                } else if (effect.getType() == ChatActionEffect.Type.AUTO_RESPONSE) {
                    autoResponses.add(new CompiledAutoResponse(pat, effect.getTargetText()));
                }
            }
        }
        compiledIgnoresByProfile.put(p.getId(), ignores);
        compiledMessageSoundsByProfile.put(p.getId(), sounds);
        compiledColorHighlightsByProfile.put(p.getId(), highlights);
        compiledTextReplacementsByProfile.put(p.getId(), replacements);
        compiledAutoResponsesByProfile.put(p.getId(), autoResponses);
    }

    /**
     * Colors matched plain-text spans for the active profile. Preserves component structure and styles outside
     * matches; only merged color/format overlays apply on matched UTF-16 ranges.
     */
    public Component applyChatColorHighlights(Component message) {
        ServerProfile profile = effectiveProfileForIncomingChat();
        if (profile == null) {
            return message;
        }
        String text = plainTextForMatching(message);
        if (text.isEmpty()) {
            return message;
        }
        if (matchesAnyIgnore(profile, text)) {
            return message;
        }
        List<ChatActionColorHighlighter.Rule> rules = compiledColorHighlightsByProfile.get(profile.getId());
        if (rules == null || rules.isEmpty()) {
            return message;
        }
        String selfName = localPlayerName();
        boolean ignoreSelf =
                ChatUtilitiesClientOptions.isIgnoreSelfInChatActions()
                        && isLikelySelfChatLine(text, selfName)
                        && selfName != null
                        && !selfName.isBlank();
        if (ignoreSelf) {
            String quotedSelf = Pattern.quote(selfName.strip());
            List<ChatActionColorHighlighter.Rule> filtered = new ArrayList<>();
            for (ChatActionColorHighlighter.Rule r : rules) {
                if (!quotedSelf.equals(r.pattern().pattern())) {
                    filtered.add(r);
                }
            }
            return filtered.isEmpty() ? message : ChatActionColorHighlighter.apply(message, filtered);
        }
        return ChatActionColorHighlighter.apply(message, rules);
    }

    private static String localPlayerName() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return null;
        }
        return mc.player.getName().getString();
    }

    private static boolean isLikelySelfChatLine(String plainText, String selfName) {
        if (plainText == null || plainText.isEmpty() || selfName == null || selfName.isBlank()) {
            return false;
        }
        String s = plainText.stripLeading();
        String name = selfName.strip();
        if (s.startsWith("<" + name + ">")) {
            return true;
        }
        if (s.startsWith(name + ":") || s.startsWith(name + " :")) {
            return true;
        }
        int close = s.indexOf(']');
        if (close >= 0 && close <= 32) {
            String after = s.substring(close + 1).stripLeading();
            return after.startsWith("<" + name + ">")
                    || after.startsWith(name + ":")
                    || after.startsWith(name + " :");
        }
        return false;
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

    public static String currentConnectionHostNormalized() {
        Minecraft mc = Minecraft.getInstance();
        ServerData sd = mc.getCurrentServer();
        if (sd != null && sd.ip != null && !sd.ip.isBlank()) {
            return stripPortFromAddress(sd.ip);
        }
        // During early connect, getCurrentServer() may still be null. Fall back to the active connection.
        try {
            ClientPacketListener listener = mc.getConnection();
            if (listener != null) {
                ServerData sd2 = listener.getServerData();
                if (sd2 != null && sd2.ip != null && !sd2.ip.isBlank()) {
                    return stripPortFromAddress(sd2.ip);
                }
                Connection conn = listener.getConnection();
                if (conn != null && conn.getRemoteAddress() != null) {
                    // Prefer the cached hostname from the JOIN event over the raw socket address.
                    // The raw address is an IP literal which will never match a hostname-configured
                    // profile (isProbablyIpLiteral -> strict equality only).
                    String cached = get().cachedJoinServerHost;
                    if (cached != null && !cached.isBlank()) {
                        return cached;
                    }
                    String s = conn.getRemoteAddress().toString();
                    if (s != null && !s.isBlank()) {
                        // Common forms: "/host:port" or "host/addr:port"
                        int slash = s.lastIndexOf('/');
                        if (slash >= 0 && slash + 1 < s.length()) {
                            s = s.substring(slash + 1);
                        }
                        return stripPortFromAddress(s);
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        // Final fallback: use the cached hostname from the JOIN event even without an active connection.
        String cached = get().cachedJoinServerHost;
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        return null;
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

    /**
     * Plain visible text for regex / window matching: literal visit (same basis as clipboard plain), then strip edge
     * invisibles. Prefer over {@link Component#getString()} so patterns match rendered text reliably.
     */
    /** Public for mixins; same as internal matching for windows, search, and actions. */
    public static String plainTextForMatching(Component message) {
        if (message == null) {
            return "";
        }
        String s = ChatCopyTextHelper.plainForClipboard(message);
        s = s.strip();
        s = stripEdgeInvisible(s);
        return s.strip();
    }

    /** Same normalization as {@link #plainTextForMatching(Component)} for a rendered chat line. */
    public static String plainTextForMatching(FormattedCharSequence seq) {
        if (seq == null || FormattedCharSequence.EMPTY.equals(seq)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        seq.accept(
                (index, style, codePoint) -> {
                    sb.appendCodePoint(codePoint);
                    return true;
                });
        String s = ChatFormatting.stripFormatting(sb.toString());
        if (s == null) {
            return "";
        }
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
                recompileChatActions(sp);
                added++;
            }
        }
        if (added > 0) {
            save();
        }
        return added;
    }

    /**
     * Imports a single profile from a LabyMod chat windows JSON export.
     *
     * <p>Creates a new {@link ServerProfile} named {@code LabyMod Import}, {@code LabyMod Import 2}, etc.
     *
     * @return number of profiles added (0 or 1)
     */
    public int importProfileFromLabyModJson(String json) throws JsonParseException {
        LabyRoot root = gson.fromJson(json, LabyRoot.class);
        if (root == null || root.windows == null || root.windows.isEmpty()) {
            throw new JsonParseException("Missing windows array");
        }
        String baseName = "LabyMod Import";
        String displayName = uniqueProfileDisplayName(baseName);

        String id = UUID.randomUUID().toString();
        ServerProfile sp = new ServerProfile(id, displayName);

        int windowIndex = 1;
        for (LabyWindow lw : root.windows) {
            if (lw == null || lw.tabs == null || lw.tabs.isEmpty()) {
                continue;
            }
            String widBase = "Laby Window";
            String wid = windowIndex == 1 ? widBase : (widBase + " " + windowIndex);
            windowIndex++;

            List<ChatWindowTab> restoredTabs = new ArrayList<>();
            for (LabyTab lt : lw.tabs) {
                if (lt == null) {
                    continue;
                }
                String tabName =
                        lt.config != null && lt.config.name != null && !lt.config.name.isBlank()
                                ? lt.config.name.strip()
                                : "Tab";
                List<String> sources = new ArrayList<>();
                List<Pattern> compiled = new ArrayList<>();
                if (lt.config != null && lt.config.filters != null) {
                    for (LabyFilter f : lt.config.filters) {
                        if (f == null || f.includeTags == null) {
                            continue;
                        }
                        for (String tag : f.includeTags) {
                            if (tag == null || tag.isBlank()) {
                                continue;
                            }
                            String src = tag.strip();
                            try {
                                compiled.add(compileUserMatchPattern(src));
                                sources.add(src);
                            } catch (PatternSyntaxException ignored) {
                            }
                        }
                    }
                }
                restoredTabs.add(new ChatWindowTab(UUID.randomUUID().toString(), tabName, compiled, sources));
            }
            if (restoredTabs.isEmpty()) {
                restoredTabs.add(new ChatWindowTab(UUID.randomUUID().toString(), ChatWindow.DEFAULT_TAB_NAME));
            }
            ChatWindow cw = new ChatWindow(wid, restoredTabs);
            sp.getWindows().put(wid, cw);
        }

        profiles.add(sp);
        profilesById.put(sp.getId(), sp);
        recompileChatActions(sp);
        save();
        return 1;
    }

    private String uniqueProfileDisplayName(String baseName) {
        String base = baseName == null || baseName.isBlank() ? "Profile" : baseName.strip();
        String candidate = base;
        int i = 2;
        while (true) {
            boolean exists = false;
            for (ServerProfile p : profiles) {
                if (p != null && candidate.equals(p.getDisplayName())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                return candidate;
            }
            candidate = base + " " + i;
            i++;
        }
    }

    private RootV3 buildRootV3() {
        RootV3 root = new RootV3();
        root.formatVersion = FORMAT_VERSION_V4;
        for (ServerProfile sp : profiles) {
            PersistedProfile pp = new PersistedProfile();
            pp.id = sp.getId();
            pp.displayName = sp.getDisplayName();
            pp.servers = new ArrayList<>(sp.getServers());
            for (ChatActionGroup g : sp.getChatActionGroups()) {
                if (g.getPatternSource() == null || g.getPatternSource().strip().isEmpty()) {
                    continue;
                }
                PersistedChatActionGroup pg = new PersistedChatActionGroup();
                pg.pattern = g.getPatternSource().strip();
                for (ChatActionEffect e : g.getEffects()) {
                    PersistedChatEffect pe = new PersistedChatEffect();
                    pe.action = e.getType().persistKey();
                    pe.soundId = e.getSoundId();
                    pe.targetText = e.getTargetText();
                    if (e.getType() == ChatActionEffect.Type.COLOR_HIGHLIGHT) {
                        pe.highlightRgb = e.getHighlightColorRgb();
                        pe.highlightBold = e.isHighlightBold();
                        pe.highlightItalic = e.isHighlightItalic();
                        pe.highlightUnderlined = e.isHighlightUnderlined();
                        pe.highlightStrikethrough = e.isHighlightStrikethrough();
                        pe.highlightObfuscated = e.isHighlightObfuscated();
                    }
                    pg.effects.add(pe);
                }
                if (!pg.effects.isEmpty()) {
                    pp.chatActionGroups.add(pg);
                }
            }
            for (ChatWindow w : sp.getWindows().values()) {
                PersistedWindow pw = new PersistedWindow();
                pw.id = w.getId();
                pw.tabs = new ArrayList<>();
                for (ChatWindowTab tab : w.getTabs()) {
                    PersistedWindowTab pt = new PersistedWindowTab();
                    pt.id = tab.getId();
                    pt.name = tab.getDisplayName();
                    pt.patterns = new ArrayList<>(tab.getPatternSources());
                    pw.tabs.add(pt);
                }
                pw.anchorX = w.getAnchorX();
                pw.anchorY = w.getAnchorY();
                pw.visible = w.isVisible();
                pw.widthFrac = w.getWidthFrac();
                pw.maxVisibleLines = w.getMaxVisibleLines();
                pw.textScale = w.getTextScale();
                pp.windows.add(pw);
            }
            for (CommandAlias ca : sp.getCommandAliases()) {
                PersistedCommandAlias pca = new PersistedCommandAlias();
                pca.from = ca.from();
                pca.to = ca.to();
                pp.commandAliases.add(pca);
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
        compiledColorHighlightsByProfile.clear();
        compiledTextReplacementsByProfile.clear();
        compiledAutoResponsesByProfile.clear();
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
            recompileChatActions(p);
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

    private static ChatWindow buildChatWindowFromPersisted(PersistedWindow pw) throws PatternSyntaxException {
        if (pw.tabs != null && !pw.tabs.isEmpty()) {
            List<ChatWindowTab> tabs = new ArrayList<>();
            for (PersistedWindowTab pt : pw.tabs) {
                if (pt == null || pt.id == null || pt.id.isEmpty()) {
                    continue;
                }
                List<String> sources = new ArrayList<>();
                if (pt.patterns != null) {
                    for (String s : pt.patterns) {
                        if (s != null && !s.strip().isEmpty()) {
                            sources.add(s);
                        }
                    }
                }
                List<Pattern> compiled = new ArrayList<>();
                for (String src : sources) {
                    compiled.add(compileUserMatchPattern(src));
                }
                String tabName =
                        pt.name != null && !pt.name.isBlank()
                                ? pt.name.strip()
                                : ChatWindow.DEFAULT_TAB_NAME;
                tabs.add(new ChatWindowTab(pt.id, tabName, compiled, sources));
            }
            if (tabs.isEmpty()) {
                return null;
            }
            ChatWindow cw = new ChatWindow(pw.id, tabs);
            applyPersistedWindowLayout(pw, cw);
            return cw;
        }
        List<String> sources = new ArrayList<>();
        if (pw.patterns != null && !pw.patterns.isEmpty()) {
            sources.addAll(pw.patterns);
        } else if (pw.regex != null && !pw.regex.isEmpty()) {
            sources.add(pw.regex);
        }
        List<Pattern> compiled = new ArrayList<>();
        for (String src : sources) {
            if (src == null || src.strip().isEmpty()) {
                continue;
            }
            compiled.add(compileUserMatchPattern(src));
        }
        if (compiled.isEmpty()) {
            return null;
        }
        ChatWindow cw = new ChatWindow(pw.id, compiled, sources);
        applyPersistedWindowLayout(pw, cw);
        return cw;
    }

    private static void applyPersistedWindowLayout(PersistedWindow pw, ChatWindow cw) {
        cw.setAnchorX(pw.anchorX);
        cw.setAnchorY(pw.anchorY);
        cw.setVisible(pw.visible);
        if (pw.widthFrac > 0) {
            cw.setWidthFrac(pw.widthFrac);
        }
        if (pw.maxVisibleLines > 0) {
            cw.setMaxVisibleLines(pw.maxVisibleLines);
        }
        if (pw.textScale > 0f) {
            cw.setTextScale(pw.textScale);
        }
    }

    private static ChatActionEffect effectFromPersisted(PersistedChatEffect pe) {
        ChatActionEffect.Type t = ChatActionEffect.Type.fromPersistKey(pe.action);
        String sound = pe.soundId != null ? pe.soundId : "";
        String target = pe.targetText != null ? pe.targetText : "";
        int rgb = ChatActionEffect.DEFAULT_HIGHLIGHT_RGB;
        if (t == ChatActionEffect.Type.COLOR_HIGHLIGHT && pe.highlightRgb != null) {
            rgb = pe.highlightRgb & 0xFFFFFF;
        }
        if (t == ChatActionEffect.Type.COLOR_HIGHLIGHT) {
            return new ChatActionEffect(
                    t,
                    sound,
                    target,
                    rgb,
                    pe.highlightBold,
                    pe.highlightItalic,
                    pe.highlightUnderlined,
                    pe.highlightStrikethrough,
                    pe.highlightObfuscated);
        }
        return new ChatActionEffect(t, sound, target, rgb, false, false, false, false, false);
    }

    private ServerProfile buildProfileFromPersisted(PersistedProfile pp) {
        if (pp == null || pp.id == null || pp.id.isEmpty()) {
            return null;
        }
        ServerProfile sp = new ServerProfile(pp.id, pp.displayName != null ? pp.displayName : pp.id);
        if (pp.servers != null) {
            sp.getServers().addAll(pp.servers);
        }
        boolean haveGroups = pp.chatActionGroups != null && !pp.chatActionGroups.isEmpty();
        if (haveGroups) {
            for (PersistedChatActionGroup pg : pp.chatActionGroups) {
                if (pg == null || pg.pattern == null || pg.pattern.strip().isEmpty() || pg.effects == null) {
                    continue;
                }
                ChatActionGroup g = new ChatActionGroup(pg.pattern.strip());
                for (PersistedChatEffect pe : pg.effects) {
                    if (pe == null) {
                        continue;
                    }
                    g.addEffect(effectFromPersisted(pe));
                }
                if (!g.getEffects().isEmpty()) {
                    sp.getChatActionGroups().add(g);
                }
            }
        } else if (pp.chatActions != null && !pp.chatActions.isEmpty()) {
            LinkedHashMap<String, ChatActionGroup> byPattern = new LinkedHashMap<>();
            for (PersistedChatAction pa : pp.chatActions) {
                if (pa == null || pa.pattern == null || pa.pattern.strip().isEmpty()) {
                    continue;
                }
                String pat = pa.pattern.strip();
                ChatActionGroup g = byPattern.computeIfAbsent(pat, ChatActionGroup::new);
                ChatActionEffect.Type t = ChatActionEffect.Type.fromPersistKey(pa.action);
                String sound = pa.soundId != null ? pa.soundId : "";
                int rgb = ChatActionEffect.DEFAULT_HIGHLIGHT_RGB;
                if (t == ChatActionEffect.Type.COLOR_HIGHLIGHT && pa.highlightRgb != null) {
                    rgb = pa.highlightRgb & 0xFFFFFF;
                }
                g.addEffect(new ChatActionEffect(t, sound, rgb));
            }
            sp.getChatActionGroups().addAll(byPattern.values());
        } else {
            LinkedHashMap<String, ChatActionGroup> byPattern = new LinkedHashMap<>();
            if (pp.ignorePatterns != null) {
                for (String ign : pp.ignorePatterns) {
                    if (ign != null && !ign.strip().isEmpty()) {
                        String pat = ign.strip();
                        ChatActionGroup g = byPattern.computeIfAbsent(pat, ChatActionGroup::new);
                        g.addEffect(
                                new ChatActionEffect(
                                        ChatActionEffect.Type.IGNORE,
                                        "",
                                        ChatActionEffect.DEFAULT_HIGHLIGHT_RGB));
                    }
                }
            }
            if (pp.messageSounds != null) {
                for (PersistedMessageSound pms : pp.messageSounds) {
                    if (pms == null || pms.pattern == null || pms.pattern.strip().isEmpty()) {
                        continue;
                    }
                    String pat = pms.pattern.strip();
                    ChatActionGroup g = byPattern.computeIfAbsent(pat, ChatActionGroup::new);
                    g.addEffect(
                            new ChatActionEffect(
                                    ChatActionEffect.Type.PLAY_SOUND,
                                    pms.soundId != null ? pms.soundId : "",
                                    ChatActionEffect.DEFAULT_HIGHLIGHT_RGB));
                }
            }
            sp.getChatActionGroups().addAll(byPattern.values());
        }
        if (pp.windows != null) {
            for (PersistedWindow pw : pp.windows) {
                if (pw == null || pw.id == null) {
                    continue;
                }
                try {
                    ChatWindow cw = buildChatWindowFromPersisted(pw);
                    if (cw != null) {
                        sp.getWindows().put(pw.id, cw);
                    }
                } catch (PatternSyntaxException e) {
                    LOGGER.warn("Skipping window {}: bad pattern", pw.id, e);
                }
            }
        }
        if (pp.commandAliases != null) {
            for (PersistedCommandAlias pca : pp.commandAliases) {
                if (pca == null || pca.from == null || pca.to == null) {
                    continue;
                }
                try {
                    CommandAlias c = new CommandAlias(pca.from, pca.to);
                    if (!c.from().isEmpty() && !c.to().isEmpty()) {
                        sp.commandAliasesMutable().add(c);
                    }
                } catch (Exception ignored) {
                }
            }
            while (sp.commandAliasesMutable().size() > MAX_COMMAND_ALIASES) {
                sp.commandAliasesMutable().remove(sp.commandAliasesMutable().size() - 1);
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
                if (pw.textScale > 0f) {
                    cw.setTextScale(pw.textScale);
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

    // ── LabyMod import model ──────────────────────────────────────────────────

    private static final class LabyRoot {
        List<LabyWindow> windows = new ArrayList<>();
    }

    private static final class LabyWindow {
        List<LabyTab> tabs = new ArrayList<>();
    }

    private static final class LabyTab {
        LabyTabConfig config;
    }

    private static final class LabyTabConfig {
        String name;
        List<LabyFilter> filters = new ArrayList<>();
    }

    private static final class LabyFilter {
        List<String> includeTags = new ArrayList<>();
    }

    private static final class PersistedChatAction {
        String pattern;
        String action;
        String soundId;
        /** v4+ optional; used when {@code action} is color highlight. */
        Integer highlightRgb;
    }

    private static final class PersistedChatActionGroup {
        String pattern;
        List<PersistedChatEffect> effects = new ArrayList<>();
    }

    private static final class PersistedChatEffect {
        String action;
        String soundId;
        String targetText;
        /** Set for {@link ChatActionEffect.Type#COLOR_HIGHLIGHT}. */
        Integer highlightRgb;
        boolean highlightBold;
        boolean highlightItalic;
        boolean highlightUnderlined;
        boolean highlightStrikethrough;
        boolean highlightObfuscated;
    }

    private static final class PersistedCommandAlias {
        /** First token without leading slash. */
        String from;
        /** Replacement command name without leading slash. */
        String to;
    }

    private static final class PersistedProfile {
        String id;
        String displayName;
        List<String> servers = new ArrayList<>();
        /** v4+ preferred; one row per pattern with multiple effects. */
        List<PersistedChatActionGroup> chatActionGroups = new ArrayList<>();
        /** Legacy rows; migrated into {@link #chatActionGroups} when that list is absent. */
        List<PersistedChatAction> chatActions = new ArrayList<>();
        /** Legacy v3; migrated when both {@code chatActionGroups} and {@code chatActions} are empty. */
        List<String> ignorePatterns = new ArrayList<>();
        List<PersistedMessageSound> messageSounds = new ArrayList<>();
        List<PersistedWindow> windows = new ArrayList<>();
        /** Outgoing command aliases (first token); stored without leading slash. */
        List<PersistedCommandAlias> commandAliases = new ArrayList<>();
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

    private static final class CompiledTextReplacement {
        final Pattern pattern;
        final String targetText;

        CompiledTextReplacement(Pattern pattern, String targetText) {
            this.pattern = pattern;
            this.targetText = targetText == null ? "" : targetText;
        }
    }

    private static final class CompiledAutoResponse {
        final Pattern pattern;
        final String responseText;

        CompiledAutoResponse(Pattern pattern, String responseText) {
            this.pattern = pattern;
            this.responseText = responseText == null ? "" : responseText;
        }
    }

    private static final class LegacyRoot {
        Integer patternFormat;
        List<PersistedWindow> windows = new ArrayList<>();
    }

    private static final class PersistedWindowTab {
        String id;
        String name;
        List<String> patterns = new ArrayList<>();
    }

    private static final class PersistedWindow {
        String id;
        /** v4+: explicit tabs; when absent, {@link #patterns}/{@link #regex} define a single default tab. */
        List<PersistedWindowTab> tabs;
        List<String> patterns;
        String regex;
        float anchorX = 0.02f;
        float anchorY = 0.85f;
        boolean visible = true;
        float widthFrac = ChatWindow.DEFAULT_WIDTH_FRAC;
        int maxVisibleLines = ChatWindow.DEFAULT_MAX_VISIBLE_LINES;
        float textScale = ChatWindow.DEFAULT_TEXT_SCALE;
    }
}
