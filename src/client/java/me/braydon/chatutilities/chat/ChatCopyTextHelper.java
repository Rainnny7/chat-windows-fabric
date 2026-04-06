package me.braydon.chatutilities.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/** Clipboard-oriented serialization of chat {@link Component}s. */
public final class ChatCopyTextHelper {
    private ChatCopyTextHelper() {}

    /**
     * Visible plain text only: literal segments from the component tree, with no legacy color
     * letters left behind when {@link Component#getString()} mishandles formatting.
     */
    public static String plainForClipboard(Component message) {
        StringBuilder out = new StringBuilder();
        message.visit(
                (style, segment) -> {
                    if (!segment.isEmpty()) {
                        out.append(segment);
                    }
                    return Optional.empty();
                },
                Style.EMPTY);
        String s = out.length() > 0 ? out.toString() : message.getString();
        if (s == null) {
            return "";
        }
        return ChatFormatting.stripFormatting(s);
    }

    /**
     * Legacy § formatting: emits codes only when style changes (not before every sibling), uses
     * exact palette colors when possible, otherwise {@code §x§r§r§g§g§b§b} RGB (1.16+).
     */
    public static String toLegacySectionString(Component message) {
        StringBuilder out = new StringBuilder();
        Style[] lastHolder = new Style[] {null};
        message.visit(
                (style, segment) -> {
                    if (segment.isEmpty()) {
                        return Optional.empty();
                    }
                    Style last = lastHolder[0];
                    if (last == null) {
                        appendLegacyStyleCodes(out, style);
                        lastHolder[0] = style;
                    } else if (!stylesEqual(last, style)) {
                        char p = ChatFormatting.PREFIX_CODE;
                        out.append(p).append(ChatFormatting.RESET.getChar());
                        appendLegacyStyleCodes(out, style);
                        lastHolder[0] = style;
                    }
                    out.append(segment);
                    return Optional.empty();
                },
                Style.EMPTY);
        if (out.length() > 0) {
            return out.toString();
        }
        String plain = message.getString();
        return plain == null ? "" : plain;
    }

    /**
     * Adventure MiniMessage–style tags, derived from {@link #toLegacySectionString(Component)} so
     * formatting matches the working § path. Parses {@code §} codes (including {@code §x§R§R§G§G§B§B}
     * RGB) into tags; literal {@code <} and {@code \} in message text are escaped.
     */
    public static String toMiniMessageString(Component message) {
        return legacySectionStringToMiniMessage(toLegacySectionString(message));
    }

    /**
     * Converts a legacy § string (as produced by {@link #toLegacySectionString(Component)}) into
     * MiniMessage.
     */
    private static String legacySectionStringToMiniMessage(String legacy) {
        if (legacy == null || legacy.isEmpty()) {
            return "";
        }
        char p = ChatFormatting.PREFIX_CODE;
        StringBuilder out = new StringBuilder(legacy.length() + 32);
        for (int i = 0; i < legacy.length(); i++) {
            char c = legacy.charAt(i);
            if (c != p) {
                appendMiniMessageEscapedChar(out, c);
                continue;
            }
            if (i + 1 >= legacy.length()) {
                appendMiniMessageEscapedChar(out, p);
                continue;
            }
            char code = legacy.charAt(i + 1);
            if (code == 'x' || code == 'X') {
                if (isLegacyRgbHeader(legacy, i)) {
                    out.append("<#");
                    for (int k = 0; k < 6; k++) {
                        out.append(Character.toLowerCase(legacy.charAt(i + 3 + 2 * k)));
                    }
                    out.append('>');
                    i += 13; // loop will ++ → 14 chars total (§x + 6×§digit)
                    continue;
                }
            }
            ChatFormatting fmt = formattingForLegacyCodeChar(code);
            if (fmt != null) {
                appendMiniMessageTagForFormatting(out, fmt);
                i++; // consume code char; loop consumes §
                continue;
            }
            appendMiniMessageEscapedChar(out, p);
            appendMiniMessageEscapedChar(out, code);
            i++;
        }
        return out.toString();
    }

    private static boolean isLegacyRgbHeader(String s, int sectionIndex) {
        if (sectionIndex + 14 > s.length()) {
            return false;
        }
        char p = ChatFormatting.PREFIX_CODE;
        char x = s.charAt(sectionIndex + 1);
        if (x != 'x' && x != 'X') {
            return false;
        }
        for (int k = 0; k < 6; k++) {
            int pos = sectionIndex + 2 + 2 * k;
            if (s.charAt(pos) != p) {
                return false;
            }
            if (Character.digit(s.charAt(pos + 1), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static @Nullable ChatFormatting formattingForLegacyCodeChar(char code) {
        for (ChatFormatting f : ChatFormatting.values()) {
            if (f.getChar() == code) {
                return f;
            }
        }
        return null;
    }

    private static void appendMiniMessageTagForFormatting(StringBuilder out, ChatFormatting fmt) {
        if (fmt == ChatFormatting.RESET) {
            out.append("<reset>");
            return;
        }
        if (fmt.isColor()) {
            out.append('<').append(minimessageColorName(fmt)).append('>');
            return;
        }
        switch (fmt) {
            case BOLD -> out.append("<bold>");
            case ITALIC -> out.append("<italic>");
            case UNDERLINE -> out.append("<underlined>");
            case STRIKETHROUGH -> out.append("<strikethrough>");
            case OBFUSCATED -> out.append("<obfuscated>");
            default -> {}
        }
    }

    private static void appendMiniMessageEscapedChar(StringBuilder out, char c) {
        if (c == '\\') {
            out.append("\\\\");
        } else if (c == '<') {
            out.append("\\<");
        } else {
            out.append(c);
        }
    }

    private static void appendLegacyStyleCodes(StringBuilder out, Style style) {
        char p = ChatFormatting.PREFIX_CODE;
        TextColor tc = style.getColor();
        if (tc != null) {
            ChatFormatting named = exactFormattingColor(tc);
            if (named != null) {
                out.append(p).append(named.getChar());
            } else {
                appendRgbLegacy(out, tc.getValue() & 0xFFFFFF);
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

    /** {@code §x§r§r§g§g§b§b} */
    private static void appendRgbLegacy(StringBuilder out, int rgb) {
        char p = ChatFormatting.PREFIX_CODE;
        out.append(p).append('x');
        String hex = String.format("%06x", rgb);
        for (int i = 0; i < hex.length(); i++) {
            out.append(p).append(hex.charAt(i));
        }
    }

    private static boolean stylesEqual(Style a, Style b) {
        return a.isBold() == b.isBold()
                && a.isItalic() == b.isItalic()
                && a.isUnderlined() == b.isUnderlined()
                && a.isStrikethrough() == b.isStrikethrough()
                && a.isObfuscated() == b.isObfuscated()
                && java.util.Objects.equals(a.getColor(), b.getColor());
    }

    /**
     * Adventure MiniMessage named colors (not single-letter legacy codes — {@code <b>} is bold, not
     * aqua).
     */
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

    private static @Nullable ChatFormatting exactFormattingColor(TextColor tc) {
        int rgb = tc.getValue() & 0xFFFFFF;
        for (ChatFormatting cf : ChatFormatting.values()) {
            if (!cf.isColor()) {
                continue;
            }
            Integer c = cf.getColor();
            if (c != null && (c & 0xFFFFFF) == rgb) {
                return cf;
            }
        }
        return null;
    }
}
