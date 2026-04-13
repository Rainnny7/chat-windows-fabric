package me.braydon.chatutilities.chat;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Live chat search query while {@link net.minecraft.client.gui.screens.ChatScreen} is open. */
public final class ChatSearchState {
    private static String rawQuery = "";
    private static Pattern compiled;
    private static boolean invalidPattern;

    private ChatSearchState() {}

    public static void setQuery(String raw) {
        rawQuery = raw == null ? "" : raw;
        String q = rawQuery.strip();
        if (q.isEmpty()) {
            compiled = null;
            invalidPattern = false;
            return;
        }
        try {
            compiled = ChatUtilitiesManager.compileUserMatchPattern(q);
            invalidPattern = false;
        } catch (PatternSyntaxException e) {
            compiled = null;
            invalidPattern = true;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof ChatScreen) {
            ChatSearchVanillaScrollClamp.apply(mc);
        }
    }

    public static String getRawQuery() {
        return rawQuery;
    }

    public static void clear() {
        rawQuery = "";
        compiled = null;
        invalidPattern = false;
        ChatSearchOverlay.resetJumpInteraction();
    }

    public static boolean isFiltering() {
        return compiled != null;
    }

    public static boolean isInvalidPattern() {
        return invalidPattern;
    }

    public static boolean matchesPlain(String plain) {
        if (compiled == null || plain == null) {
            return true;
        }
        return compiled.matcher(plain).find();
    }

    public static boolean matchesComponent(Component message) {
        return matchesPlain(ChatUtilitiesManager.plainTextForMatching(message));
    }

    /**
     * Single wrapped row only; for vanilla chat search use {@link
     * VanillaChatLinePicker#vanillaTrimmedLineMatchesOpenChatSearch} so matches span full logical messages.
     */
    public static boolean matchesLine(GuiMessage.Line line) {
        if (compiled == null) {
            return true;
        }
        FormattedCharSequence seq = line.content();
        String plain = ChatUtilitiesManager.plainTextForMatching(seq);
        return !plain.isEmpty() && compiled.matcher(plain).find();
    }
}
