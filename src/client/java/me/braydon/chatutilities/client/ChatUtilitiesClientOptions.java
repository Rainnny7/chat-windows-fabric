package me.braydon.chatutilities.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.util.Mth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Client-only preferences (not part of server profile JSON). */
public final class ChatUtilitiesClientOptions {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final String FILE_NAME = "chat-utilities-client.json";

    private static Path path;

    private static boolean showChatSymbolSelector = true;

    /** Gear chip to the right of the symbol selector on {@link net.minecraft.client.gui.screens.ChatScreen}. */
    private static boolean showChatBarMenuButton = true;

    private static boolean chatTextShadow = true;

    private static boolean clickToCopyEnabled = true;

    private static CopyFormattedStyle copyFormattedStyle = CopyFormattedStyle.VANILLA;

    /** Legacy codes inserted from the chat symbol palette (color/style strip). */
    private static CopyFormattedStyle symbolPaletteInsertStyle = CopyFormattedStyle.VANILLA;

    private static ClickMouseBinding copyPlainBinding = ClickMouseBinding.defaultPlain();

    private static ClickMouseBinding copyFormattedBinding = ClickMouseBinding.defaultFormatted();

    /**
     * Session-only: last Chat Utilities sidebar profile + panel. Survives closing/reopening the GUI in this JVM run;
     * not written to {@link #FILE_NAME} (does not survive game restart).
     */
    private static String lastMenuProfileId;

    /** {@link me.braydon.chatutilities.gui.ChatUtilitiesRootScreen.Panel#name()} */
    private static String lastMenuPanel;

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

    public static void setCopyPlainBinding(ClickMouseBinding binding) {
        copyPlainBinding = binding != null ? binding : ClickMouseBinding.defaultPlain();
        save();
    }

    public static void setCopyFormattedBinding(ClickMouseBinding binding) {
        copyFormattedBinding = binding != null ? binding : ClickMouseBinding.defaultFormatted();
        save();
    }

    public static String getLastMenuProfileId() {
        return lastMenuProfileId;
    }

    public static String getLastMenuPanel() {
        return lastMenuPanel;
    }

    public static void setLastMenuState(String profileId, String panelName) {
        lastMenuProfileId = profileId;
        lastMenuPanel = panelName;
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

    /**
     * Restores all client preferences to built-in defaults (symbol selector, shadow, copy, smooth chat, etc.),
     * clears session-only menu memory, and writes {@link #FILE_NAME} once.
     */
    public static void resetAllToDefaults() {
        showChatSymbolSelector = true;
        showChatBarMenuButton = true;
        chatTextShadow = true;
        clickToCopyEnabled = true;
        copyFormattedStyle = CopyFormattedStyle.VANILLA;
        symbolPaletteInsertStyle = CopyFormattedStyle.VANILLA;
        copyPlainBinding = ClickMouseBinding.defaultPlain();
        copyFormattedBinding = ClickMouseBinding.defaultFormatted();
        lastMenuProfileId = null;
        lastMenuPanel = null;
        smoothChat = false;
        smoothChatFadeMs = 200;
        smoothChatBarOpenMs = 200;
        longerChatHistory = false;
        chatHistoryLimitLines = CHAT_HISTORY_LIMIT_DEFAULT;
        stackRepeatedMessages = false;
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
            d.chatTextShadow = chatTextShadow;
            d.clickToCopyEnabled = clickToCopyEnabled;
            d.copyFormattedStyle = copyFormattedStyle.name();
            d.symbolPaletteInsertStyle = symbolPaletteInsertStyle.name();
            d.copyPlainBinding = copyPlainBinding;
            d.copyFormattedBinding = copyFormattedBinding;
            d.smoothChat = smoothChat;
            d.smoothChatFadeMs = smoothChatFadeMs;
            d.smoothChatBarOpenMs = smoothChatBarOpenMs;
            d.longerChatHistory = longerChatHistory;
            d.chatHistoryLimitLines = getChatHistoryLimitLines();
            d.stackRepeatedMessages = stackRepeatedMessages;
            Files.writeString(path, GSON.toJson(d), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
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
        boolean chatTextShadow = true;
        boolean clickToCopyEnabled = true;
        String copyFormattedStyle = CopyFormattedStyle.VANILLA.name();
        String symbolPaletteInsertStyle = CopyFormattedStyle.VANILLA.name();
        ClickMouseBinding copyPlainBinding;
        ClickMouseBinding copyFormattedBinding;
        boolean smoothChat;
        int smoothChatFadeMs = 200;
        int smoothChatBarOpenMs = 200;
        boolean longerChatHistory;
        int chatHistoryLimitLines = CHAT_HISTORY_LIMIT_DEFAULT;
        boolean stackRepeatedMessages;
    }
}
