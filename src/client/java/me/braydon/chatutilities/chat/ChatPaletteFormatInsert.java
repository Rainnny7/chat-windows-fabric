package me.braydon.chatutilities.chat;

import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import net.minecraft.ChatFormatting;
import org.jspecify.annotations.Nullable;

/**
 * Text inserted into the chat field when picking legacy color/style codes in {@link ChatSymbolPalette}, following
 * {@link ChatUtilitiesClientOptions#getSymbolPaletteInsertStyle()}.
 */
public final class ChatPaletteFormatInsert {
    private ChatPaletteFormatInsert() {}

    public static String textForLegacyCode(
            char code, ChatUtilitiesClientOptions.CopyFormattedStyle style) {
        return switch (style) {
            case VANILLA -> "&" + code;
            case SECTION_SYMBOL -> String.valueOf(ChatFormatting.PREFIX_CODE) + code;
            case MINIMESSAGE -> {
                String mm = miniMessageTagForLegacyCode(code);
                yield mm.isEmpty()
                        ? String.valueOf(ChatFormatting.PREFIX_CODE) + code
                        : mm;
            }
        };
    }

    private static String miniMessageTagForLegacyCode(char code) {
        ChatFormatting fmt = formattingForLegacyCodeChar(code);
        if (fmt == null) {
            return "";
        }
        if (fmt == ChatFormatting.RESET) {
            return "<reset>";
        }
        if (fmt.isColor()) {
            return "<" + minimessageColorName(fmt) + ">";
        }
        return switch (fmt) {
            case BOLD -> "<b>";
            case ITALIC -> "<i>";
            case UNDERLINE -> "<u>";
            case STRIKETHROUGH -> "<st>";
            case OBFUSCATED -> "<obf>";
            default -> "";
        };
    }

    private static @Nullable ChatFormatting formattingForLegacyCodeChar(char code) {
        for (ChatFormatting f : ChatFormatting.values()) {
            if (f.getChar() == code) {
                return f;
            }
        }
        return null;
    }

    /** Adventure MiniMessage named colors (aligned with {@code ChatCopyTextHelper} output). */
    private static String minimessageColorName(ChatFormatting cf) {
        return switch (cf) {
            case BLACK -> "black";
            case DARK_BLUE -> "dark_blue";
            case DARK_GREEN -> "dark_green";
            case DARK_AQUA -> "dark_aqua";
            case DARK_RED -> "dark_red";
            case DARK_PURPLE -> "dark_purple";
            case GOLD -> "gold";
            case GRAY -> "gray";
            case DARK_GRAY -> "dark_gray";
            case BLUE -> "blue";
            case GREEN -> "green";
            case AQUA -> "aqua";
            case RED -> "red";
            case LIGHT_PURPLE -> "light_purple";
            case YELLOW -> "yellow";
            case WHITE -> "white";
            default -> cf.getSerializedName();
        };
    }
}
