package me.braydon.chatutilities.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Client-only preferences (not part of server profile JSON). */
public final class ChatUtilitiesClientOptions {

    public enum ChatSearchBarPosition {
        BELOW_CHAT,
        ABOVE_CHAT;

        public static ChatSearchBarPosition fromPersisted(String raw) {
            if (raw == null || raw.isBlank()) {
                return ABOVE_CHAT;
            }
            try {
                return valueOf(raw.strip());
            } catch (IllegalArgumentException e) {
                return ABOVE_CHAT;
            }
        }
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String FILE_NAME = "chat-utilities-client.json";

    private static Path path;

    private static boolean showChatSymbolSelector = true;

    /** Gear chip to the right of the symbol selector on {@link net.minecraft.client.gui.screens.ChatScreen}. */
    private static boolean showChatBarMenuButton = true;

    /** Search row when chat is open. */
    private static boolean chatSearchBarEnabled = false;

    private static ChatSearchBarPosition chatSearchBarPosition = ChatSearchBarPosition.ABOVE_CHAT;

    private static boolean chatTextShadow = true;

    private static boolean clickToCopyEnabled = true;

    private static CopyFormattedStyle copyFormattedStyle = CopyFormattedStyle.MINIMESSAGE;

    /** Legacy codes inserted from the chat symbol palette (color/style strip). */
    private static CopyFormattedStyle symbolPaletteInsertStyle = CopyFormattedStyle.MINIMESSAGE;

    private static ClickMouseBinding copyPlainBinding = ClickMouseBinding.defaultPlain();

    private static ClickMouseBinding copyFormattedBinding = ClickMouseBinding.defaultFormatted();

    /** Shift+LMB (default) on an image preview opens fullscreen. */
    private static ClickMouseBinding fullscreenImagePreviewClickBinding =
            ClickMouseBinding.defaultFullscreenImagePreview();

    /** Skip chat action sounds/highlights on your own chat lines. */
    private static boolean ignoreSelfInChatActions = true;

    /** When true, the image preview domain whitelist is bypassed (extension check still applies). */
    private static boolean allowUntrustedImagePreviewDomains;

    /**
     * Session-only: last Chat Utilities sidebar profile + panel. Survives closing/reopening the GUI in this JVM run;
     * not written to {@link #FILE_NAME} (does not survive game restart).
     */
    private static String lastMenuProfileId;

    /** {@link me.braydon.chatutilities.gui.ChatUtilitiesRootScreen.Panel#name()} */
    private static String lastMenuPanel;

    private static int lastMenuServerScroll;
    private static int lastMenuWinScroll;
    private static int lastMenuActionScroll;
    private static int lastMenuAliasScroll;
    private static int lastMenuSettingsContentScroll;
    private static int lastMenuChatWindowsListScrollPixels;

    private static boolean smoothChat;

    /** Fade-in duration for smooth chat (milliseconds), clamped {@link #SMOOTH_CHAT_FADE_MS_MIN}–{@link #SMOOTH_CHAT_FADE_MS_MAX}. */
    private static int smoothChatFadeMs = 200;

    /** Duration of the chat input bar slide-in when opening chat (milliseconds). */
    private static int smoothChatBarOpenMs = 200;

    public static final int SMOOTH_CHAT_FADE_MS_MIN = 50;

    public static final int SMOOTH_CHAT_FADE_MS_MAX = 2000;

    /** Millisecond steps for smooth-chat sliders (fade + chat bar open). */
    public static final int SMOOTH_CHAT_SLIDER_STEP_MS = 50;

    /** Vanilla client keeps this many recent chat messages; used when longer history is off. */
    public static final int VANILLA_CHAT_HISTORY_LINES = 100;

    public static final int CHAT_HISTORY_LIMIT_MIN = 100;

    public static final int CHAT_HISTORY_LIMIT_MAX = 5000;

    public static final int CHAT_HISTORY_LIMIT_STEP = 50;

    public static final int CHAT_HISTORY_LIMIT_DEFAULT = 500;

    private static boolean longerChatHistory;

    /** Stored limit when {@link #longerChatHistory} is on; clamped when read or written. */
    private static int chatHistoryLimitLines = CHAT_HISTORY_LIMIT_DEFAULT;

    private static boolean stackRepeatedMessages;

    /** Default suffix shown for stacked repeats. */
    public static final String STACKED_MESSAGE_FORMAT_DEFAULT = "(x%amount%)";

    /** RGB only (no alpha); default matches {@link net.minecraft.ChatFormatting#GRAY} (§7). */
    public static final int STACKED_MESSAGE_COLOR_RGB_DEFAULT = 0xAAAAAA;

    private static String stackedMessageFormat = STACKED_MESSAGE_FORMAT_DEFAULT;
    private static int stackedMessageColorRgb = STACKED_MESSAGE_COLOR_RGB_DEFAULT;

    private static boolean chatTimestampsEnabled;

    /** Default {@link java.time.format.DateTimeFormatter} pattern (US locale, 12-hour with AM/PM). */
    public static final String CHAT_TIMESTAMP_FORMAT_DEFAULT = "'['hh:mm:ss a']'";

    private static String chatTimestampFormatPattern = CHAT_TIMESTAMP_FORMAT_DEFAULT;

    /** RGB only (no alpha); default matches {@link net.minecraft.ChatFormatting#GRAY} (§7). */
    public static final int CHAT_TIMESTAMP_COLOR_RGB_DEFAULT = 0xAAAAAA;

    private static int chatTimestampColorRgb = CHAT_TIMESTAMP_COLOR_RGB_DEFAULT;

    /**
     * Strength of chat panel backgrounds (vanilla HUD rows + chat windows): 100 = vanilla alpha, lower = more
     * transparent.
     */
    public static final int CHAT_PANEL_BG_OPACITY_MIN = 0;

    public static final int CHAT_PANEL_BG_OPACITY_MAX = 100;

    public static final int CHAT_PANEL_BG_OPACITY_DEFAULT = 100;

    private static int chatPanelBackgroundOpacityUnfocusedPercent = CHAT_PANEL_BG_OPACITY_DEFAULT;
    private static int chatPanelBackgroundOpacityFocusedPercent = CHAT_PANEL_BG_OPACITY_DEFAULT;
    private static int chatBarBackgroundOpacityPercent = CHAT_PANEL_BG_OPACITY_DEFAULT;

    /** Default mod UI accent (same blue as legacy {@code C_ACCENT}). */
    public static final int MOD_PRIMARY_DEFAULT_ARGB = 0xFF3A9FE0;

    public static final float MOD_PRIMARY_CHROMA_SPEED_MIN = 1f;

    public static final float MOD_PRIMARY_CHROMA_SPEED_MAX = 120f;

    public static final int MOD_PRIMARY_RECENT_MAX = 12;

    private static int modPrimaryArgb = MOD_PRIMARY_DEFAULT_ARGB;

    private static boolean modPrimaryChroma;

    private static float modPrimaryChromaSpeed = 10f;

    private static final List<Integer> modPrimaryRecent = new ArrayList<>();

    /** Recent timestamp colors (ARGB with full alpha); same cap as {@link #MOD_PRIMARY_RECENT_MAX}. */
    private static final List<Integer> chatTimestampRecent = new ArrayList<>();

    /** Hover thumbnails for whitelisted image links in chat (Open URL). */
    private static boolean imageChatPreviewEnabled = true;

    /** Show unread badges on non-selected chat-window tabs. */
    private static boolean chatWindowTabUnreadBadgesEnabled = true;

    /** When chat is closed, show unread-only tabs on the HUD. */
    private static boolean alwaysShowUnreadTabs = true;

    /** Host suffixes allowed for image preview (e.g. {@code imgur.com} matches {@code i.imgur.com}). */
    private static final List<String> imagePreviewWhitelistHosts =
            new ArrayList<>(
                    List.of(
                            "cdn.rainnny.club",
                            "cdn.fascinated.cc",
                            "imgur.com",
                            "i.imgur.com",
                            "gyazo.com",
                            "i.gyazo.com",
                            "prnt.sc",
                            "image.prntscr.com",
                            "cdn.discordapp.com",
                            "media.discordapp.net",
                            "i.redd.it",
                            "preview.redd.it",
                            "i.ibb.co",
                            "i.postimg.cc"));

    private ChatUtilitiesClientOptions() {}

    public enum CopyFormattedStyle {
        /** {@code &} legacy codes (server.properties / common plugin style). */
        VANILLA,
        /** {@code §} legacy codes (Java Edition section sign). */
        SECTION_SYMBOL,
        MINIMESSAGE
    }

    public static void init() {
        path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        load();
    }

    public static boolean isShowChatSymbolSelector() {
        return showChatSymbolSelector;
    }

    public static void setShowChatSymbolSelector(boolean value) {
        showChatSymbolSelector = value;
        save();
    }

    public static void toggleShowChatSymbolSelector() {
        setShowChatSymbolSelector(!showChatSymbolSelector);
    }

    public static boolean isShowChatBarMenuButton() {
        return showChatBarMenuButton;
    }

    public static void setShowChatBarMenuButton(boolean value) {
        showChatBarMenuButton = value;
        save();
    }

    public static void toggleShowChatBarMenuButton() {
        setShowChatBarMenuButton(!showChatBarMenuButton);
    }

    public static boolean isChatSearchBarEnabled() {
        return chatSearchBarEnabled;
    }

    public static void setChatSearchBarEnabled(boolean value) {
        chatSearchBarEnabled = value;
        save();
    }

    public static void toggleChatSearchBarEnabled() {
        setChatSearchBarEnabled(!chatSearchBarEnabled);
    }

    public static ChatSearchBarPosition getChatSearchBarPosition() {
        return chatSearchBarPosition;
    }

    public static void setChatSearchBarPosition(ChatSearchBarPosition value) {
        chatSearchBarPosition = value != null ? value : ChatSearchBarPosition.BELOW_CHAT;
        save();
    }

    public static void cycleChatSearchBarPosition() {
        setChatSearchBarPosition(
                chatSearchBarPosition == ChatSearchBarPosition.BELOW_CHAT
                        ? ChatSearchBarPosition.ABOVE_CHAT
                        : ChatSearchBarPosition.BELOW_CHAT);
    }

    public static boolean isIgnoreSelfInChatActions() {
        return ignoreSelfInChatActions;
    }

    public static void setIgnoreSelfInChatActions(boolean value) {
        ignoreSelfInChatActions = value;
        save();
    }

    public static void toggleIgnoreSelfInChatActions() {
        setIgnoreSelfInChatActions(!ignoreSelfInChatActions);
    }

    public static boolean isAllowUntrustedImagePreviewDomains() {
        return allowUntrustedImagePreviewDomains;
    }

    public static void setAllowUntrustedImagePreviewDomains(boolean value) {
        allowUntrustedImagePreviewDomains = value;
        save();
    }

    public static void toggleAllowUntrustedImagePreviewDomains() {
        setAllowUntrustedImagePreviewDomains(!allowUntrustedImagePreviewDomains);
    }

    public static boolean isChatTextShadow() {
        return chatTextShadow;
    }

    public static void setChatTextShadow(boolean value) {
        chatTextShadow = value;
        save();
    }

    public static void toggleChatTextShadow() {
        setChatTextShadow(!chatTextShadow);
    }

    public static boolean isClickToCopyEnabled() {
        return clickToCopyEnabled;
    }

    public static void setClickToCopyEnabled(boolean value) {
        clickToCopyEnabled = value;
        save();
    }

    public static void toggleClickToCopyEnabled() {
        setClickToCopyEnabled(!clickToCopyEnabled);
    }

    public static CopyFormattedStyle getCopyFormattedStyle() {
        return copyFormattedStyle;
    }

    public static void setCopyFormattedStyle(CopyFormattedStyle value) {
        copyFormattedStyle = value != null ? value : CopyFormattedStyle.VANILLA;
        save();
    }

    public static void cycleCopyFormattedStyle() {
        setCopyFormattedStyle(
                switch (copyFormattedStyle) {
                    case VANILLA -> CopyFormattedStyle.SECTION_SYMBOL;
                    case SECTION_SYMBOL -> CopyFormattedStyle.MINIMESSAGE;
                    case MINIMESSAGE -> CopyFormattedStyle.VANILLA;
                });
    }

    public static CopyFormattedStyle getSymbolPaletteInsertStyle() {
        return symbolPaletteInsertStyle;
    }

    public static void setSymbolPaletteInsertStyle(CopyFormattedStyle value) {
        symbolPaletteInsertStyle = value != null ? value : CopyFormattedStyle.VANILLA;
        save();
    }

    public static void cycleSymbolPaletteInsertStyle() {
        setSymbolPaletteInsertStyle(
                switch (symbolPaletteInsertStyle) {
                    case VANILLA -> CopyFormattedStyle.SECTION_SYMBOL;
                    case SECTION_SYMBOL -> CopyFormattedStyle.MINIMESSAGE;
                    case MINIMESSAGE -> CopyFormattedStyle.VANILLA;
                });
    }

    public static ClickMouseBinding getCopyPlainBinding() {
        return copyPlainBinding;
    }

    public static ClickMouseBinding getCopyFormattedBinding() {
        return copyFormattedBinding;
    }

    public static ClickMouseBinding getFullscreenImagePreviewClickBinding() {
        return fullscreenImagePreviewClickBinding;
    }

    public static void setCopyPlainBinding(ClickMouseBinding binding) {
        copyPlainBinding = binding != null ? binding : ClickMouseBinding.defaultPlain();
        save();
    }

    public static void setCopyFormattedBinding(ClickMouseBinding binding) {
        copyFormattedBinding = binding != null ? binding : ClickMouseBinding.defaultFormatted();
        save();
    }

    public static void setFullscreenImagePreviewClickBinding(ClickMouseBinding binding) {
        fullscreenImagePreviewClickBinding =
                binding != null ? binding.normalized() : ClickMouseBinding.defaultFullscreenImagePreview();
        save();
    }

    public static String getLastMenuProfileId() {
        return lastMenuProfileId;
    }

    public static String getLastMenuPanel() {
        return lastMenuPanel;
    }

    public static int getLastMenuServerScroll() {
        return lastMenuServerScroll;
    }

    public static int getLastMenuWinScroll() {
        return lastMenuWinScroll;
    }

    public static int getLastMenuActionScroll() {
        return lastMenuActionScroll;
    }

    public static int getLastMenuAliasScroll() {
        return lastMenuAliasScroll;
    }

    public static int getLastMenuSettingsContentScroll() {
        return lastMenuSettingsContentScroll;
    }

    public static int getLastMenuChatWindowsListScrollPixels() {
        return lastMenuChatWindowsListScrollPixels;
    }

    public static void setLastMenuState(
            String profileId,
            String panelName,
            int serverScroll,
            int winScroll,
            int actionScroll,
            int aliasScroll,
            int settingsContentScroll,
            int chatWindowsListScrollPixels) {
        lastMenuProfileId = profileId;
        lastMenuPanel = panelName;
        lastMenuServerScroll = Math.max(0, serverScroll);
        lastMenuWinScroll = Math.max(0, winScroll);
        lastMenuActionScroll = Math.max(0, actionScroll);
        lastMenuAliasScroll = Math.max(0, aliasScroll);
        lastMenuSettingsContentScroll = Math.max(0, settingsContentScroll);
        lastMenuChatWindowsListScrollPixels = Math.max(0, chatWindowsListScrollPixels);
    }

    public static boolean isSmoothChat() {
        return smoothChat;
    }

    public static void setSmoothChat(boolean value) {
        smoothChat = value;
        save();
    }

    public static void toggleSmoothChat() {
        setSmoothChat(!smoothChat);
    }

    public static int getSmoothChatFadeMs() {
        return smoothChatFadeMs;
    }

    public static void setSmoothChatFadeMs(int ms) {
        smoothChatFadeMs = Mth.clamp(ms, SMOOTH_CHAT_FADE_MS_MIN, SMOOTH_CHAT_FADE_MS_MAX);
        save();
    }

    public static int getSmoothChatBarOpenMs() {
        return smoothChatBarOpenMs;
    }

    public static void setSmoothChatBarOpenMs(int ms) {
        smoothChatBarOpenMs = Mth.clamp(ms, SMOOTH_CHAT_FADE_MS_MIN, SMOOTH_CHAT_FADE_MS_MAX);
        save();
    }

    public static boolean isLongerChatHistory() {
        return longerChatHistory;
    }

    public static void setLongerChatHistory(boolean value) {
        longerChatHistory = value;
        save();
    }

    public static void toggleLongerChatHistory() {
        setLongerChatHistory(!longerChatHistory);
    }

    /**
     * Max stored lines for vanilla HUD chat and per-window HUD history. When longer history is disabled, matches
     * {@link #VANILLA_CHAT_HISTORY_LINES}.
     */
    public static int getEffectiveChatHistoryLimit() {
        if (!longerChatHistory) {
            return VANILLA_CHAT_HISTORY_LINES;
        }
        return Mth.clamp(chatHistoryLimitLines, CHAT_HISTORY_LIMIT_MIN, CHAT_HISTORY_LIMIT_MAX);
    }

    public static int getChatHistoryLimitLines() {
        return Mth.clamp(chatHistoryLimitLines, CHAT_HISTORY_LIMIT_MIN, CHAT_HISTORY_LIMIT_MAX);
    }

    public static void setChatHistoryLimitLines(int lines) {
        chatHistoryLimitLines = Mth.clamp(lines, CHAT_HISTORY_LIMIT_MIN, CHAT_HISTORY_LIMIT_MAX);
        save();
    }

    public static boolean isStackRepeatedMessages() {
        return stackRepeatedMessages;
    }

    public static void setStackRepeatedMessages(boolean value) {
        stackRepeatedMessages = value;
        save();
    }

    public static void toggleStackRepeatedMessages() {
        setStackRepeatedMessages(!stackRepeatedMessages);
    }

    public static String getStackedMessageFormat() {
        return stackedMessageFormat == null || stackedMessageFormat.isBlank()
                ? STACKED_MESSAGE_FORMAT_DEFAULT
                : stackedMessageFormat;
    }

    public static void setStackedMessageFormat(String format) {
        String f = format == null ? "" : format.strip();
        if (f.length() > 96) {
            f = f.substring(0, 96);
        }
        stackedMessageFormat = f.isEmpty() ? STACKED_MESSAGE_FORMAT_DEFAULT : f;
        save();
    }

    public static int getStackedMessageColorRgb() {
        return stackedMessageColorRgb & 0xFFFFFF;
    }

    public static void setStackedMessageColorRgb(int rgb) {
        stackedMessageColorRgb = rgb & 0xFFFFFF;
        save();
    }

    public static boolean isChatTimestampsEnabled() {
        return chatTimestampsEnabled;
    }

    public static void setChatTimestampsEnabled(boolean value) {
        chatTimestampsEnabled = value;
        save();
    }

    public static void toggleChatTimestampsEnabled() {
        setChatTimestampsEnabled(!chatTimestampsEnabled);
    }

    public static String getChatTimestampFormatPattern() {
        return chatTimestampFormatPattern == null || chatTimestampFormatPattern.isBlank()
                ? CHAT_TIMESTAMP_FORMAT_DEFAULT
                : chatTimestampFormatPattern;
    }

    public static void setChatTimestampFormatPattern(String pattern) {
        String p = pattern == null ? "" : pattern.strip();
        if (p.length() > 96) {
            p = p.substring(0, 96);
        }
        chatTimestampFormatPattern = p.isEmpty() ? CHAT_TIMESTAMP_FORMAT_DEFAULT : p;
        save();
    }

    public static int getChatTimestampColorRgb() {
        return chatTimestampColorRgb & 0xFFFFFF;
    }

    public static void setChatTimestampColorRgb(int rgb) {
        chatTimestampColorRgb = rgb & 0xFFFFFF;
        save();
    }

    public static int getChatPanelBackgroundOpacityUnfocusedPercent() {
        return Mth.clamp(
                chatPanelBackgroundOpacityUnfocusedPercent,
                CHAT_PANEL_BG_OPACITY_MIN,
                CHAT_PANEL_BG_OPACITY_MAX);
    }

    public static void setChatPanelBackgroundOpacityUnfocusedPercent(int percent) {
        chatPanelBackgroundOpacityUnfocusedPercent =
                Mth.clamp(percent, CHAT_PANEL_BG_OPACITY_MIN, CHAT_PANEL_BG_OPACITY_MAX);
        save();
    }

    public static int getChatPanelBackgroundOpacityFocusedPercent() {
        return Mth.clamp(
                chatPanelBackgroundOpacityFocusedPercent,
                CHAT_PANEL_BG_OPACITY_MIN,
                CHAT_PANEL_BG_OPACITY_MAX);
    }

    public static void setChatPanelBackgroundOpacityFocusedPercent(int percent) {
        chatPanelBackgroundOpacityFocusedPercent =
                Mth.clamp(percent, CHAT_PANEL_BG_OPACITY_MIN, CHAT_PANEL_BG_OPACITY_MAX);
        save();
    }

    public static int getChatBarBackgroundOpacityPercent() {
        return Mth.clamp(chatBarBackgroundOpacityPercent, CHAT_PANEL_BG_OPACITY_MIN, CHAT_PANEL_BG_OPACITY_MAX);
    }

    public static void setChatBarBackgroundOpacityPercent(int percent) {
        chatBarBackgroundOpacityPercent =
                Mth.clamp(percent, CHAT_PANEL_BG_OPACITY_MIN, CHAT_PANEL_BG_OPACITY_MAX);
        save();
    }

    /** Backward-compat helper used by old call sites: uses focused panel opacity. */
    public static int getChatPanelBackgroundOpacityPercent() {
        return getChatPanelBackgroundOpacityFocusedPercent();
    }

    /** Backward-compat helper used by old call sites: writes both focused and unfocused. */
    public static void setChatPanelBackgroundOpacityPercent(int percent) {
        int v = Mth.clamp(percent, CHAT_PANEL_BG_OPACITY_MIN, CHAT_PANEL_BG_OPACITY_MAX);
        chatPanelBackgroundOpacityFocusedPercent = v;
        chatPanelBackgroundOpacityUnfocusedPercent = v;
        save();
    }

    /** Multiplier applied to panel fill alpha (100% = 1.0). */
    public static float getChatPanelBackgroundOpacityMultiplier(boolean focused) {
        return (focused
                        ? getChatPanelBackgroundOpacityFocusedPercent()
                        : getChatPanelBackgroundOpacityUnfocusedPercent())
                / 100f;
    }

    /** Backward-compat helper: focused multiplier. */
    public static float getChatPanelBackgroundOpacityMultiplier() {
        return getChatPanelBackgroundOpacityMultiplier(true);
    }

    /** Multiplies the alpha channel of an ARGB color (e.g. vanilla chat row fills). */
    public static int multiplyChatPanelBackgroundArgb(int argb, boolean focused) {
        float m = getChatPanelBackgroundOpacityMultiplier(focused);
        int a = (argb >>> 24) & 0xFF;
        int rgb = argb & 0xFFFFFF;
        int na = Mth.clamp(Math.round(a * m), 0, 255);
        return (na << 24) | rgb;
    }

    /** Backward-compat helper: focused panel opacity. */
    public static int multiplyChatPanelBackgroundArgb(int argb) {
        return multiplyChatPanelBackgroundArgb(argb, true);
    }

    public static int getModPrimaryArgb() {
        return modPrimaryArgb;
    }

    public static void setModPrimaryArgb(int argb) {
        modPrimaryArgb = argb;
        save();
    }

    public static boolean isModPrimaryChroma() {
        return modPrimaryChroma;
    }

    public static void setModPrimaryChroma(boolean value) {
        modPrimaryChroma = value;
        save();
    }

    public static float getModPrimaryChromaSpeed() {
        return Mth.clamp(modPrimaryChromaSpeed, MOD_PRIMARY_CHROMA_SPEED_MIN, MOD_PRIMARY_CHROMA_SPEED_MAX);
    }

    public static void setModPrimaryChromaSpeed(float speed) {
        modPrimaryChromaSpeed = Mth.clamp(speed, MOD_PRIMARY_CHROMA_SPEED_MIN, MOD_PRIMARY_CHROMA_SPEED_MAX);
        save();
    }

    public static List<Integer> getModPrimaryRecent() {
        return List.copyOf(modPrimaryRecent);
    }

    /** Adds {@code argb} to the front of the recent list (deduped), capped at {@link #MOD_PRIMARY_RECENT_MAX}. */
    public static void pushModPrimaryRecent(int argb) {
        modPrimaryRecent.removeIf(i -> i == argb);
        modPrimaryRecent.add(0, argb);
        while (modPrimaryRecent.size() > MOD_PRIMARY_RECENT_MAX) {
            modPrimaryRecent.remove(modPrimaryRecent.size() - 1);
        }
        save();
    }

    public static List<Integer> getChatTimestampRecent() {
        return List.copyOf(chatTimestampRecent);
    }

    public static boolean isImageChatPreviewEnabled() {
        return imageChatPreviewEnabled;
    }

    public static void setImageChatPreviewEnabled(boolean value) {
        imageChatPreviewEnabled = value;
        save();
    }

    public static void toggleImageChatPreviewEnabled() {
        setImageChatPreviewEnabled(!imageChatPreviewEnabled);
    }

    public static boolean isChatWindowTabUnreadBadgesEnabled() {
        return chatWindowTabUnreadBadgesEnabled;
    }

    public static void setChatWindowTabUnreadBadgesEnabled(boolean value) {
        chatWindowTabUnreadBadgesEnabled = value;
        save();
    }

    public static void toggleChatWindowTabUnreadBadgesEnabled() {
        setChatWindowTabUnreadBadgesEnabled(!chatWindowTabUnreadBadgesEnabled);
    }

    public static boolean isAlwaysShowUnreadTabs() {
        return alwaysShowUnreadTabs;
    }

    public static void setAlwaysShowUnreadTabs(boolean value) {
        alwaysShowUnreadTabs = value;
        save();
    }

    public static void toggleAlwaysShowUnreadTabs() {
        setAlwaysShowUnreadTabs(!alwaysShowUnreadTabs);
    }

    public static List<String> getImagePreviewWhitelistHosts() {
        synchronized (imagePreviewWhitelistHosts) {
            return List.copyOf(imagePreviewWhitelistHosts);
        }
    }

    public static void setImagePreviewWhitelistHosts(List<String> hosts) {
        synchronized (imagePreviewWhitelistHosts) {
            imagePreviewWhitelistHosts.clear();
            if (hosts != null) {
                for (String h : hosts) {
                    if (h != null && !h.strip().isEmpty()) {
                        imagePreviewWhitelistHosts.add(h.strip());
                    }
                }
            }
            if (imagePreviewWhitelistHosts.isEmpty()) {
                imagePreviewWhitelistHosts.addAll(
                        List.of(
                                "cdn.rainnny.club",
                                "cdn.fascinated.cc",
                                "imgur.com",
                                "i.imgur.com",
                                "gyazo.com",
                                "i.gyazo.com",
                                "prnt.sc",
                                "image.prntscr.com",
                                "cdn.discordapp.com",
                                "media.discordapp.net",
                                "i.redd.it",
                                "preview.redd.it",
                                "i.ibb.co",
                                "i.postimg.cc"));
            }
        }
        save();
    }

    /** Adds opaque ARGB (RGB-only colors stored as {@code 0xFF000000 | rgb}) to the timestamp recent list. */
    public static void pushChatTimestampRecent(int rgbOpaque) {
        int argb = rgbOpaque | 0xFF000000;
        chatTimestampRecent.removeIf(i -> i == argb);
        chatTimestampRecent.add(0, argb);
        while (chatTimestampRecent.size() > MOD_PRIMARY_RECENT_MAX) {
            chatTimestampRecent.remove(chatTimestampRecent.size() - 1);
        }
        save();
    }

    /**
     * Restores all client preferences to built-in defaults (symbol selector, shadow, copy, smooth chat, etc.),
     * clears session-only menu memory, and writes {@link #FILE_NAME} once.
     */
    public static void resetAllToDefaults() {
        showChatSymbolSelector = true;
        showChatBarMenuButton = true;
        chatSearchBarEnabled = false;
        chatSearchBarPosition = ChatSearchBarPosition.ABOVE_CHAT;
        chatTextShadow = true;
        clickToCopyEnabled = true;
        copyFormattedStyle = CopyFormattedStyle.MINIMESSAGE;
        symbolPaletteInsertStyle = CopyFormattedStyle.MINIMESSAGE;
        copyPlainBinding = ClickMouseBinding.defaultPlain();
        copyFormattedBinding = ClickMouseBinding.defaultFormatted();
        fullscreenImagePreviewClickBinding = ClickMouseBinding.defaultFullscreenImagePreview();
        ignoreSelfInChatActions = true;
        lastMenuProfileId = null;
        lastMenuPanel = null;
        smoothChat = false;
        smoothChatFadeMs = 200;
        smoothChatBarOpenMs = 200;
        longerChatHistory = false;
        chatHistoryLimitLines = CHAT_HISTORY_LIMIT_DEFAULT;
        stackRepeatedMessages = false;
        stackedMessageFormat = STACKED_MESSAGE_FORMAT_DEFAULT;
        stackedMessageColorRgb = STACKED_MESSAGE_COLOR_RGB_DEFAULT;
        chatTimestampsEnabled = false;
        chatTimestampFormatPattern = CHAT_TIMESTAMP_FORMAT_DEFAULT;
        chatTimestampColorRgb = CHAT_TIMESTAMP_COLOR_RGB_DEFAULT;
        chatPanelBackgroundOpacityUnfocusedPercent = CHAT_PANEL_BG_OPACITY_DEFAULT;
        chatPanelBackgroundOpacityFocusedPercent = CHAT_PANEL_BG_OPACITY_DEFAULT;
        chatBarBackgroundOpacityPercent = CHAT_PANEL_BG_OPACITY_DEFAULT;
        modPrimaryArgb = MOD_PRIMARY_DEFAULT_ARGB;
        modPrimaryChroma = false;
        modPrimaryChromaSpeed = 10f;
        modPrimaryRecent.clear();
        chatTimestampRecent.clear();
        imageChatPreviewEnabled = true;
        chatWindowTabUnreadBadgesEnabled = true;
        alwaysShowUnreadTabs = true;
        synchronized (imagePreviewWhitelistHosts) {
            imagePreviewWhitelistHosts.clear();
            imagePreviewWhitelistHosts.addAll(
                    List.of(
                            "cdn.rainnny.club",
                            "cdn.fascinated.cc",
                            "imgur.com",
                            "i.imgur.com",
                            "gyazo.com",
                            "i.gyazo.com",
                            "prnt.sc",
                            "image.prntscr.com",
                            "cdn.discordapp.com",
                            "media.discordapp.net",
                            "i.redd.it",
                            "preview.redd.it",
                            "i.ibb.co",
                            "i.postimg.cc"));
        }
        save();
    }

    private static void load() {
        if (path == null || !Files.isRegularFile(path)) {
            return;
        }
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            Data d = GSON.fromJson(json, Data.class);
            if (d != null) {
                showChatSymbolSelector = d.showChatSymbolSelector;
                if (d.showChatBarMenuButton != null) {
                    showChatBarMenuButton = d.showChatBarMenuButton;
                }
                if (d.chatSearchBarEnabled != null) {
                    chatSearchBarEnabled = d.chatSearchBarEnabled;
                }
                if (d.chatSearchBarPosition != null) {
                    chatSearchBarPosition = ChatSearchBarPosition.fromPersisted(d.chatSearchBarPosition);
                }
                if (d.ignoreSelfInChatActions != null) {
                    ignoreSelfInChatActions = d.ignoreSelfInChatActions;
                }
                chatTextShadow = d.chatTextShadow;
                clickToCopyEnabled = d.clickToCopyEnabled;
                if (d.copyFormattedStyle != null) {
                    copyFormattedStyle = parseCopyFormattedStyle(d.copyFormattedStyle);
                }
                if (d.symbolPaletteInsertStyle != null) {
                    symbolPaletteInsertStyle = parseCopyFormattedStyle(d.symbolPaletteInsertStyle);
                }
                if (d.copyPlainBinding != null) {
                    copyPlainBinding = d.copyPlainBinding.normalized();
                }
                if (d.copyFormattedBinding != null) {
                    copyFormattedBinding = d.copyFormattedBinding.normalized();
                }
                if (d.fullscreenImagePreviewClickBinding != null) {
                    fullscreenImagePreviewClickBinding = d.fullscreenImagePreviewClickBinding.normalized();
                }
                smoothChat = d.smoothChat;
                if (d.smoothChatFadeMs > 0) {
                    smoothChatFadeMs = Mth.clamp(
                            d.smoothChatFadeMs, SMOOTH_CHAT_FADE_MS_MIN, SMOOTH_CHAT_FADE_MS_MAX);
                }
                if (d.smoothChatBarOpenMs > 0) {
                    smoothChatBarOpenMs = Mth.clamp(
                            d.smoothChatBarOpenMs, SMOOTH_CHAT_FADE_MS_MIN, SMOOTH_CHAT_FADE_MS_MAX);
                }
                longerChatHistory = d.longerChatHistory;
                if (d.chatHistoryLimitLines > 0) {
                    chatHistoryLimitLines =
                            Mth.clamp(d.chatHistoryLimitLines, CHAT_HISTORY_LIMIT_MIN, CHAT_HISTORY_LIMIT_MAX);
                }
                stackRepeatedMessages = d.stackRepeatedMessages;
                if (d.stackedMessageFormat != null && !d.stackedMessageFormat.isBlank()) {
                    stackedMessageFormat = d.stackedMessageFormat.strip();
                }
                if (d.stackedMessageColorRgb != null) {
                    stackedMessageColorRgb = d.stackedMessageColorRgb & 0xFFFFFF;
                }
                if (d.chatTimestampsEnabled != null) {
                    chatTimestampsEnabled = d.chatTimestampsEnabled;
                }
                if (d.chatTimestampFormatPattern != null && !d.chatTimestampFormatPattern.isBlank()) {
                    chatTimestampFormatPattern = d.chatTimestampFormatPattern.strip();
                }
                if (d.chatTimestampColorRgb != null) {
                    chatTimestampColorRgb = d.chatTimestampColorRgb & 0xFFFFFF;
                }
                if (d.chatPanelBackgroundOpacityFocusedPercent != null) {
                    chatPanelBackgroundOpacityFocusedPercent =
                            Mth.clamp(
                                    d.chatPanelBackgroundOpacityFocusedPercent,
                                    CHAT_PANEL_BG_OPACITY_MIN,
                                    CHAT_PANEL_BG_OPACITY_MAX);
                }
                if (d.chatPanelBackgroundOpacityUnfocusedPercent != null) {
                    chatPanelBackgroundOpacityUnfocusedPercent =
                            Mth.clamp(
                                    d.chatPanelBackgroundOpacityUnfocusedPercent,
                                    CHAT_PANEL_BG_OPACITY_MIN,
                                    CHAT_PANEL_BG_OPACITY_MAX);
                }
                if (d.chatBarBackgroundOpacityPercent != null) {
                    chatBarBackgroundOpacityPercent =
                            Mth.clamp(
                                    d.chatBarBackgroundOpacityPercent,
                                    CHAT_PANEL_BG_OPACITY_MIN,
                                    CHAT_PANEL_BG_OPACITY_MAX);
                }
                if (d.chatPanelBackgroundOpacityPercent != null) {
                    int legacy =
                            Mth.clamp(
                                    d.chatPanelBackgroundOpacityPercent,
                                    CHAT_PANEL_BG_OPACITY_MIN,
                                    CHAT_PANEL_BG_OPACITY_MAX);
                    chatPanelBackgroundOpacityFocusedPercent = legacy;
                    chatPanelBackgroundOpacityUnfocusedPercent = legacy;
                }
                if (d.modPrimaryArgb != null) {
                    modPrimaryArgb = d.modPrimaryArgb;
                }
                if (d.modPrimaryChroma != null) {
                    modPrimaryChroma = d.modPrimaryChroma;
                }
                if (d.modPrimaryChromaSpeed != null) {
                    modPrimaryChromaSpeed =
                            Mth.clamp(
                                    d.modPrimaryChromaSpeed,
                                    MOD_PRIMARY_CHROMA_SPEED_MIN,
                                    MOD_PRIMARY_CHROMA_SPEED_MAX);
                }
                modPrimaryRecent.clear();
                if (d.modPrimaryRecent != null) {
                    for (Integer c : d.modPrimaryRecent) {
                        if (c != null && modPrimaryRecent.size() < MOD_PRIMARY_RECENT_MAX) {
                            modPrimaryRecent.add(c);
                        }
                    }
                }
                chatTimestampRecent.clear();
                if (d.chatTimestampRecent != null) {
                    for (Integer c : d.chatTimestampRecent) {
                        if (c != null && chatTimestampRecent.size() < MOD_PRIMARY_RECENT_MAX) {
                            chatTimestampRecent.add(c);
                        }
                    }
                }
                if (d.imageChatPreviewEnabled != null) {
                    imageChatPreviewEnabled = d.imageChatPreviewEnabled;
                }
                if (d.chatWindowTabUnreadBadgesEnabled != null) {
                    chatWindowTabUnreadBadgesEnabled = d.chatWindowTabUnreadBadgesEnabled;
                }
                if (d.alwaysShowUnreadTabs != null) {
                    alwaysShowUnreadTabs = d.alwaysShowUnreadTabs;
                }
                if (d.imagePreviewWhitelistHosts != null && !d.imagePreviewWhitelistHosts.isEmpty()) {
                    synchronized (imagePreviewWhitelistHosts) {
                        imagePreviewWhitelistHosts.clear();
                        for (String h : d.imagePreviewWhitelistHosts) {
                            if (h != null && !h.strip().isEmpty()) {
                                imagePreviewWhitelistHosts.add(h.strip());
                            }
                        }
                    }
                }
                if (d.allowUntrustedImagePreviewDomains != null) {
                    allowUntrustedImagePreviewDomains = d.allowUntrustedImagePreviewDomains;
                }
            }
        } catch (IOException ignored) {
        }
    }

    private static void save() {
        if (path == null) {
            return;
        }
        try {
            Files.createDirectories(path.getParent());
            Data d = new Data();
            d.showChatSymbolSelector = showChatSymbolSelector;
            d.showChatBarMenuButton = showChatBarMenuButton;
            d.chatSearchBarEnabled = chatSearchBarEnabled;
            d.chatSearchBarPosition = chatSearchBarPosition.name();
            d.chatTextShadow = chatTextShadow;
            d.clickToCopyEnabled = clickToCopyEnabled;
            d.copyFormattedStyle = copyFormattedStyle.name();
            d.symbolPaletteInsertStyle = symbolPaletteInsertStyle.name();
            d.copyPlainBinding = copyPlainBinding;
            d.copyFormattedBinding = copyFormattedBinding;
            d.fullscreenImagePreviewClickBinding = fullscreenImagePreviewClickBinding;
            d.ignoreSelfInChatActions = ignoreSelfInChatActions;
            d.smoothChat = smoothChat;
            d.smoothChatFadeMs = smoothChatFadeMs;
            d.smoothChatBarOpenMs = smoothChatBarOpenMs;
            d.longerChatHistory = longerChatHistory;
            d.chatHistoryLimitLines = getChatHistoryLimitLines();
            d.stackRepeatedMessages = stackRepeatedMessages;
            d.chatTimestampsEnabled = chatTimestampsEnabled;
            d.chatTimestampFormatPattern = getChatTimestampFormatPattern();
            d.chatTimestampColorRgb = getChatTimestampColorRgb();
            d.chatPanelBackgroundOpacityFocusedPercent = getChatPanelBackgroundOpacityFocusedPercent();
            d.chatPanelBackgroundOpacityUnfocusedPercent = getChatPanelBackgroundOpacityUnfocusedPercent();
            d.chatBarBackgroundOpacityPercent = getChatBarBackgroundOpacityPercent();
            d.modPrimaryArgb = modPrimaryArgb;
            d.modPrimaryChroma = modPrimaryChroma;
            d.modPrimaryChromaSpeed = getModPrimaryChromaSpeed();
            d.modPrimaryRecent = new ArrayList<>(modPrimaryRecent);
            d.chatTimestampRecent = new ArrayList<>(chatTimestampRecent);
            d.imageChatPreviewEnabled = imageChatPreviewEnabled;
            d.chatWindowTabUnreadBadgesEnabled = chatWindowTabUnreadBadgesEnabled;
            d.alwaysShowUnreadTabs = alwaysShowUnreadTabs;
            d.allowUntrustedImagePreviewDomains = allowUntrustedImagePreviewDomains;
            synchronized (imagePreviewWhitelistHosts) {
                d.imagePreviewWhitelistHosts = new ArrayList<>(imagePreviewWhitelistHosts);
            }
            Files.writeString(path, GSON.toJson(d), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    /** Exports persistent client options (not session-only menu state). */
    public static String serializePersistentOptionsToJson() {
        Data d = new Data();
        d.showChatSymbolSelector = showChatSymbolSelector;
        d.showChatBarMenuButton = showChatBarMenuButton;
        d.chatSearchBarEnabled = chatSearchBarEnabled;
        d.chatSearchBarPosition = chatSearchBarPosition.name();
        d.chatTextShadow = chatTextShadow;
        d.clickToCopyEnabled = clickToCopyEnabled;
        d.copyFormattedStyle = copyFormattedStyle.name();
        d.symbolPaletteInsertStyle = symbolPaletteInsertStyle.name();
        d.copyPlainBinding = copyPlainBinding;
        d.copyFormattedBinding = copyFormattedBinding;
        d.fullscreenImagePreviewClickBinding = fullscreenImagePreviewClickBinding;
        d.ignoreSelfInChatActions = ignoreSelfInChatActions;
        d.smoothChat = smoothChat;
        d.smoothChatFadeMs = smoothChatFadeMs;
        d.smoothChatBarOpenMs = smoothChatBarOpenMs;
        d.longerChatHistory = longerChatHistory;
        d.chatHistoryLimitLines = getChatHistoryLimitLines();
        d.stackRepeatedMessages = stackRepeatedMessages;
        d.stackedMessageFormat = getStackedMessageFormat();
        d.stackedMessageColorRgb = getStackedMessageColorRgb();
        d.chatTimestampsEnabled = chatTimestampsEnabled;
        d.chatTimestampFormatPattern = getChatTimestampFormatPattern();
        d.chatTimestampColorRgb = getChatTimestampColorRgb();
        d.chatPanelBackgroundOpacityFocusedPercent = getChatPanelBackgroundOpacityFocusedPercent();
        d.chatPanelBackgroundOpacityUnfocusedPercent = getChatPanelBackgroundOpacityUnfocusedPercent();
        d.chatBarBackgroundOpacityPercent = getChatBarBackgroundOpacityPercent();
        d.modPrimaryArgb = modPrimaryArgb;
        d.modPrimaryChroma = modPrimaryChroma;
        d.modPrimaryChromaSpeed = getModPrimaryChromaSpeed();
        d.modPrimaryRecent = new ArrayList<>(modPrimaryRecent);
        d.chatTimestampRecent = new ArrayList<>(chatTimestampRecent);
        d.imageChatPreviewEnabled = imageChatPreviewEnabled;
        d.chatWindowTabUnreadBadgesEnabled = chatWindowTabUnreadBadgesEnabled;
        d.alwaysShowUnreadTabs = alwaysShowUnreadTabs;
        d.allowUntrustedImagePreviewDomains = allowUntrustedImagePreviewDomains;
        synchronized (imagePreviewWhitelistHosts) {
            d.imagePreviewWhitelistHosts = new ArrayList<>(imagePreviewWhitelistHosts);
        }
        return GSON.toJson(d);
    }

    /** Imports persistent client options from JSON and immediately applies/saves them. */
    public static void importPersistentOptionsFromJson(String json) throws IOException {
        if (json == null || json.isBlank()) {
            return;
        }
        if (path == null) {
            path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        }
        Files.createDirectories(path.getParent());
        Files.writeString(path, json, StandardCharsets.UTF_8);
        load();
    }

    /**
     * Mouse button plus modifier keys that must match exactly (each modifier must match
     * {@link net.minecraft.client.gui.screens.Screen#hasControlDown()} etc.).
     */
    public static final class ClickMouseBinding {
        public int mouseButton;
        public boolean requireControl;
        public boolean requireShift;
        public boolean requireAlt;

        public ClickMouseBinding() {
            this(0, true, false, false);
        }

        public ClickMouseBinding(
                int mouseButton, boolean requireControl, boolean requireShift, boolean requireAlt) {
            this.mouseButton = mouseButton;
            this.requireControl = requireControl;
            this.requireShift = requireShift;
            this.requireAlt = requireAlt;
        }

        public static ClickMouseBinding defaultPlain() {
            return new ClickMouseBinding(0, true, false, false);
        }

        public static ClickMouseBinding defaultFormatted() {
            return new ClickMouseBinding(0, true, true, false);
        }

        public static ClickMouseBinding defaultFullscreenImagePreview() {
            return new ClickMouseBinding(0, false, true, false);
        }

        public ClickMouseBinding normalized() {
            return new ClickMouseBinding(mouseButton, requireControl, requireShift, requireAlt);
        }

        public boolean matches(int button, boolean control, boolean shift, boolean alt) {
            return button == mouseButton
                    && control == requireControl
                    && shift == requireShift
                    && alt == requireAlt;
        }
    }

    private static CopyFormattedStyle parseCopyFormattedStyle(String raw) {
        if (raw == null || raw.isEmpty()) {
            return CopyFormattedStyle.VANILLA;
        }
        String s = raw.trim();
        return switch (s) {
            case "MINIMESSAGE" -> CopyFormattedStyle.MINIMESSAGE;
            case "SECTION_SYMBOL", "COMPONENT" -> CopyFormattedStyle.SECTION_SYMBOL;
            case "VANILLA" -> CopyFormattedStyle.VANILLA;
            default -> {
                try {
                    yield CopyFormattedStyle.valueOf(s);
                } catch (IllegalArgumentException e) {
                    yield CopyFormattedStyle.VANILLA;
                }
            }
        };
    }

    private static final class Data {
        boolean showChatSymbolSelector = true;
        /** {@code null} when absent from older config files (defaults to {@code true} in static state). */
        Boolean showChatBarMenuButton;
        /** {@code null} when absent from older config files. */
        Boolean chatSearchBarEnabled;
        /** {@link ChatSearchBarPosition#name()}; {@code null} defaults to {@code BELOW_CHAT}. */
        String chatSearchBarPosition;
        boolean chatTextShadow = true;
        boolean clickToCopyEnabled = true;
        String copyFormattedStyle = CopyFormattedStyle.MINIMESSAGE.name();
        String symbolPaletteInsertStyle = CopyFormattedStyle.MINIMESSAGE.name();
        ClickMouseBinding copyPlainBinding;
        ClickMouseBinding copyFormattedBinding;
        ClickMouseBinding fullscreenImagePreviewClickBinding;
        Boolean ignoreSelfInChatActions;
        boolean smoothChat;
        int smoothChatFadeMs = 200;
        int smoothChatBarOpenMs = 200;
        boolean longerChatHistory;
        int chatHistoryLimitLines = CHAT_HISTORY_LIMIT_DEFAULT;
        boolean stackRepeatedMessages;
        String stackedMessageFormat;
        Integer stackedMessageColorRgb;
        Boolean chatTimestampsEnabled;
        String chatTimestampFormatPattern;
        Integer chatTimestampColorRgb;
        /** {@code null} in older configs — defaults to {@link #CHAT_PANEL_BG_OPACITY_DEFAULT}. */
        Integer chatPanelBackgroundOpacityPercent;
        Integer chatPanelBackgroundOpacityFocusedPercent;
        Integer chatPanelBackgroundOpacityUnfocusedPercent;
        Integer chatBarBackgroundOpacityPercent;
        Integer modPrimaryArgb;
        Boolean modPrimaryChroma;
        Float modPrimaryChromaSpeed;
        List<Integer> modPrimaryRecent;
        List<Integer> chatTimestampRecent;
        Boolean imageChatPreviewEnabled;
        Boolean chatWindowTabUnreadBadgesEnabled;
        Boolean alwaysShowUnreadTabs;
        Boolean allowUntrustedImagePreviewDomains;
        List<String> imagePreviewWhitelistHosts;
    }
}
