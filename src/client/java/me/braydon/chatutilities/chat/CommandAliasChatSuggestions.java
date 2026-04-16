package me.braydon.chatutilities.chat;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.Mth;

/** Prefix completions for profile {@link CommandAlias} roots while typing {@code /…} in chat. */
public final class CommandAliasChatSuggestions {
    private CommandAliasChatSuggestions() {}

    /**
     * Builds alias-name suggestions for the first command token when the cursor is inside or at the end of
     * that token (no arguments yet). Returns {@link Suggestions#empty()} when none apply.
     */
    public static Suggestions build(EditBox input, ServerProfile profile) {
        if (input == null || profile == null || profile.getCommandAliases().isEmpty()) {
            return Suggestions.empty().join();
        }
        String raw = input.getValue();
        if (raw == null || raw.isBlank()) {
            return Suggestions.empty().join();
        }
        int slash = raw.indexOf('/');
        if (slash < 0) {
            return Suggestions.empty().join();
        }
        int tokStart = slash + 1;
        int tokEnd = tokStart;
        while (tokEnd < raw.length() && !Character.isWhitespace(raw.charAt(tokEnd))) {
            tokEnd++;
        }
        int cursor = Mth.clamp(input.getCursorPosition(), 0, raw.length());
        if (cursor <= slash || cursor > tokEnd) {
            return Suggestions.empty().join();
        }
        String typed = raw.substring(tokStart, Math.min(cursor, tokEnd));
        List<Suggestion> out = new ArrayList<>();
        for (CommandAlias a : profile.getCommandAliases()) {
            String from = a.from();
            if (from.isEmpty()) {
                continue;
            }
            if (typed.length() >= from.length() && from.regionMatches(true, 0, typed, 0, from.length())) {
                continue;
            }
            if (typed.isEmpty() || from.regionMatches(true, 0, typed, 0, typed.length())) {
                int repEnd = Math.min(cursor, tokEnd);
                out.add(new Suggestion(StringRange.between(tokStart, repEnd), from));
            }
        }
        if (out.isEmpty()) {
            return Suggestions.empty().join();
        }
        return Suggestions.create(raw, out);
    }

    public static Suggestions mergeVanilla(EditBox input, Suggestions vanilla) {
        if (vanilla == null) {
            return Suggestions.empty().join();
        }
        ServerProfile p = ChatUtilitiesManager.get().getEffectiveProfileForOutgoingCommands();
        Suggestions aliases = build(input, p);
        if (aliases.isEmpty()) {
            return vanilla;
        }
        if (vanilla.isEmpty()) {
            return aliases;
        }
        String cmd = input != null && input.getValue() != null ? input.getValue() : "";
        return Suggestions.merge(cmd, List.of(vanilla, aliases));
    }

}
