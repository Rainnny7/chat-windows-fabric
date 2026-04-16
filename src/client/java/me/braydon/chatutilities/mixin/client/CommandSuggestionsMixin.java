package me.braydon.chatutilities.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.concurrent.CompletableFuture;
import me.braydon.chatutilities.chat.CommandAliasChatSuggestions;
import me.braydon.chatutilities.chat.CommandOutgoingAliases;
import me.braydon.chatutilities.chat.ChatUtilitiesManager;
import me.braydon.chatutilities.chat.ServerProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CommandSuggestions.class)
public class CommandSuggestionsMixin {

    @Shadow @Final EditBox input;

    @Unique
    private static final ThreadLocal<Boolean> chatUtilities$insideFormatTextRecursion = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<String> chatUtilities$resolvedSuggestionsInput = new ThreadLocal<>();

    @Unique
    private static final ThreadLocal<Integer> chatUtilities$adjustedCursor = new ThreadLocal<>();

    @Redirect(
            method = "updateCommandInfo()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/EditBox;getValue()Ljava/lang/String;"))
    private String chatUtilities$aliasExpandedSuggestionsInput(EditBox box) {
        String before = box.getValue();
        if (before == null || before.isBlank() || !before.stripLeading().startsWith("/")) {
            chatUtilities$resolvedSuggestionsInput.set(before);
            chatUtilities$adjustedCursor.set(box.getCursorPosition());
            return before;
        }
        ServerProfile p = ChatUtilitiesManager.get().getEffectiveProfileForOutgoingCommands();
        if (p == null || p.getCommandAliases().isEmpty()) {
            chatUtilities$resolvedSuggestionsInput.set(before);
            chatUtilities$adjustedCursor.set(box.getCursorPosition());
            return before;
        }
        String after = CommandOutgoingAliases.modifySlashChatMessage(before, p);
        String resolved = after != null ? after : before;
        int cursor = box.getCursorPosition();
        int adjusted = adjustCursorForAliasExpansion(before, resolved, cursor);
        chatUtilities$resolvedSuggestionsInput.set(resolved);
        chatUtilities$adjustedCursor.set(adjusted);
        return resolved;
    }

    @Redirect(
            method = "updateCommandInfo()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/EditBox;getCursorPosition()I"))
    private int chatUtilities$clampCursorForAliasExpandedSuggestions(EditBox box) {
        String value = chatUtilities$resolvedSuggestionsInput.get();
        if (value == null) {
            value = box.getValue();
        }
        Integer c = chatUtilities$adjustedCursor.get();
        int cursor = c != null ? c : box.getCursorPosition();
        int len = value != null ? value.length() : 0;
        return Mth.clamp(cursor, 0, len);
    }

    @Unique
    private static int adjustCursorForAliasExpansion(String before, String after, int cursor) {
        if (before == null || after == null) {
            return cursor;
        }
        String b = before.stripLeading();
        String a = after.stripLeading();
        if (!b.startsWith("/") || !a.startsWith("/")) {
            return cursor;
        }
        int bSpace = b.indexOf(' ');
        int aSpace = a.indexOf(' ');
        int bCmdEnd = bSpace < 0 ? b.length() : bSpace;
        int aCmdEnd = aSpace < 0 ? a.length() : aSpace;
        // If cursor is inside the first token, clamp to end of the new first token.
        if (cursor <= bCmdEnd) {
            return Math.min(cursor, aCmdEnd);
        }
        // If cursor is after the first token, shift by the command-length delta.
        int delta = aCmdEnd - bCmdEnd;
        return cursor + delta;
    }

    @Inject(method = "updateCommandInfo()V", at = @At("RETURN"))
    private void chatUtilities$clearSuggestionsThreadLocal(CallbackInfo ci) {
        chatUtilities$resolvedSuggestionsInput.remove();
        chatUtilities$adjustedCursor.remove();
    }

    @WrapOperation(
            method = "showSuggestions(Z)V",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Ljava/util/concurrent/CompletableFuture;join()Ljava/lang/Object;"))
    private Object chatUtilities$mergeAliasJoinShow(
            CompletableFuture<?> pending, Operation<Object> original, boolean showSuggestions) {
        return chatUtilities$mergeJoinedSuggestions(original, pending);
    }

    @WrapOperation(
            method = {"updateUsageInfo()V", "updateCommandInfo()V"},
            at =
                    @At(
                            value = "INVOKE",
                            target = "Ljava/util/concurrent/CompletableFuture;join()Ljava/lang/Object;"))
    private Object chatUtilities$mergeAliasJoinUsage(
            CompletableFuture<?> pending, Operation<Object> original) {
        return chatUtilities$mergeJoinedSuggestions(original, pending);
    }

    @Unique
    private Object chatUtilities$mergeJoinedSuggestions(Operation<Object> original, CompletableFuture<?> pending) {
        Object joined = original.call(pending);
        if (!(joined instanceof Suggestions s)) {
            return joined;
        }
        return CommandAliasChatSuggestions.mergeVanilla(this.input, s);
    }

    /**
     * {@link #updateCommandInfo} parses an alias-expanded command, but the render path still passed the raw box text
     * into {@code formatText}, so Brigadier indices did not match and the whole line looked invalid (red).
     * Intercept {@code formatText} (refmapped reliably) to run it on the expanded string, then restyle the displayed text.
     */
    @Inject(method = "formatText", at = @At("HEAD"), cancellable = true)
    private static void chatUtilities$aliasAwareFormatText(
            ParseResults<ClientSuggestionProvider> parse,
            String original,
            int firstCharacterIndex,
            CallbackInfoReturnable<FormattedCharSequence> cir) {
        if (Boolean.TRUE.equals(chatUtilities$insideFormatTextRecursion.get())) {
            return;
        }
        if (original == null || original.isBlank() || !original.stripLeading().startsWith("/")) {
            return;
        }
        ServerProfile p = ChatUtilitiesManager.get().getEffectiveProfileForOutgoingCommands();
        if (p == null || p.getCommandAliases().isEmpty()) {
            return;
        }
        String expanded = CommandOutgoingAliases.modifySlashChatMessage(original, p);
        if (expanded == null || expanded.equals(original)) {
            return;
        }
        int adj = adjustCursorForAliasExpansion(original, expanded, firstCharacterIndex);
        chatUtilities$insideFormatTextRecursion.set(true);
        try {
            FormattedCharSequence expandedFmt =
                    CommandSuggestionsFormatTextInvoker.chatutilities$invokeFormatText(
                            parse, expanded, adj);
            boolean parseComplains = parse != null && !parse.getExceptions().isEmpty();
            Style style =
                    parseComplains
                            ? Style.EMPTY.withColor(ChatFormatting.RED)
                            : chatUtilities$dominantStyle(expandedFmt);
            cir.setReturnValue(Component.literal(original).withStyle(style).getVisualOrderText());
            cir.cancel();
        } finally {
            chatUtilities$insideFormatTextRecursion.remove();
        }
    }

    @Unique
    private static Style chatUtilities$dominantStyle(FormattedCharSequence seq) {
        final Style[] first = {Style.EMPTY};
        seq.accept(
                (int index, Style style, int codePoint) -> {
                    first[0] = style;
                    return false;
                });
        return first[0];
    }
}

