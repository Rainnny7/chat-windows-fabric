package me.braydon.chatutilities.mixin.client;

import com.mojang.brigadier.ParseResults;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CommandSuggestions.class)
public interface CommandSuggestionsFormatTextInvoker {
    /** {@code formatText} is static in 1.21.11; invoker must be static too. */
    @Invoker("formatText")
    static FormattedCharSequence chatutilities$invokeFormatText(
            ParseResults<ClientSuggestionProvider> parse, String original, int firstCharacterIndex) {
        throw new UnsupportedOperationException();
    }
}
