package me.braydon.chatutilities.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

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

    private static boolean chatTextShadow = true;

    private static boolean clickToCopyEnabled = true;

    private static CopyFormattedStyle copyFormattedStyle = CopyFormattedStyle.SECTION_SYMBOL;

    private static ClickMouseBinding copyPlainBinding = ClickMouseBinding.defaultPlain();

    private static ClickMouseBinding copyFormattedBinding = ClickMouseBinding.defaultFormatted();

    /** Last expanded profile id in the Chat Utilities menu; restored when reopening. */
    private static String lastMenuProfileId;

    /** {@link me.braydon.chatutilities.gui.ChatUtilitiesRootScreen.Panel#name()} */
    private static String lastMenuPanel;

    private ChatUtilitiesClientOptions() {}

    public enum CopyFormattedStyle {
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
        copyFormattedStyle = value != null ? value : CopyFormattedStyle.SECTION_SYMBOL;
        save();
    }

    public static void cycleCopyFormattedStyle() {
        setCopyFormattedStyle(
                copyFormattedStyle == CopyFormattedStyle.SECTION_SYMBOL
                        ? CopyFormattedStyle.MINIMESSAGE
                        : CopyFormattedStyle.SECTION_SYMBOL);
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
                chatTextShadow = d.chatTextShadow;
                clickToCopyEnabled = d.clickToCopyEnabled;
                if (d.copyFormattedStyle != null) {
                    copyFormattedStyle = parseCopyFormattedStyle(d.copyFormattedStyle);
                }
                if (d.copyPlainBinding != null) {
                    copyPlainBinding = d.copyPlainBinding.normalized();
                }
                if (d.copyFormattedBinding != null) {
                    copyFormattedBinding = d.copyFormattedBinding.normalized();
                }
                lastMenuProfileId = d.lastMenuProfileId;
                lastMenuPanel = d.lastMenuPanel;
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
            d.chatTextShadow = chatTextShadow;
            d.clickToCopyEnabled = clickToCopyEnabled;
            d.copyFormattedStyle = copyFormattedStyle.name();
            d.copyPlainBinding = copyPlainBinding;
            d.copyFormattedBinding = copyFormattedBinding;
            d.lastMenuProfileId = lastMenuProfileId;
            d.lastMenuPanel = lastMenuPanel;
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
            return CopyFormattedStyle.SECTION_SYMBOL;
        }
        String s = raw.trim();
        return switch (s) {
            case "MINIMESSAGE" -> CopyFormattedStyle.MINIMESSAGE;
            case "SECTION_SYMBOL", "COMPONENT" -> CopyFormattedStyle.SECTION_SYMBOL;
            default -> {
                try {
                    yield CopyFormattedStyle.valueOf(s);
                } catch (IllegalArgumentException e) {
                    yield CopyFormattedStyle.SECTION_SYMBOL;
                }
            }
        };
    }

    private static final class Data {
        boolean showChatSymbolSelector = true;
        boolean chatTextShadow = true;
        boolean clickToCopyEnabled = true;
        String copyFormattedStyle = CopyFormattedStyle.SECTION_SYMBOL.name();
        ClickMouseBinding copyPlainBinding;
        ClickMouseBinding copyFormattedBinding;
        String lastMenuProfileId;
        String lastMenuPanel;
    }
}
