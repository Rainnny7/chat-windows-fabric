package me.braydon.chatutilities.chat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Rewrites outgoing chat/commands using per-profile {@link CommandAlias} entries (longest {@code from} first). */
public final class CommandOutgoingAliases {
    private CommandOutgoingAliases() {}

    public static String modifySlashChatMessage(String message, ServerProfile profile) {
        if (message == null || profile == null) {
            return message;
        }
        String t = message.stripLeading();
        if (!t.startsWith("/")) {
            return message;
        }
        int sp = t.indexOf(' ');
        String first = sp < 0 ? t.substring(1) : t.substring(1, sp);
        String rest = sp < 0 ? "" : t.substring(sp);
        String replaced = replaceFirstToken(first, rest, profile);
        if (replaced == null) {
            return message;
        }
        return "/" + replaced;
    }

    /**
     * @param command command string as in Fabric {@code MODIFY_COMMAND} (no leading {@code /})
     */
    public static String modifyCommandMessage(String command, ServerProfile profile) {
        if (command == null || profile == null) {
            return command;
        }
        String t = command.stripLeading();
        int sp = t.indexOf(' ');
        String first = sp < 0 ? t : t.substring(0, sp);
        String rest = sp < 0 ? "" : t.substring(sp);
        String replaced = replaceFirstToken(first, rest, profile);
        return replaced != null ? replaced : command;
    }

    /** @return {@code null} if no alias matched */
    private static String replaceFirstToken(String firstToken, String rest, ServerProfile profile) {
        if (firstToken.isEmpty()) {
            return null;
        }
        List<CommandAlias> sorted = new ArrayList<>(profile.getCommandAliases());
        sorted.sort(Comparator.comparingInt((CommandAlias a) -> a.from().length()).reversed());
        for (CommandAlias a : sorted) {
            if (firstToken.equalsIgnoreCase(a.from())) {
                return a.to() + rest;
            }
        }
        return null;
    }
}
