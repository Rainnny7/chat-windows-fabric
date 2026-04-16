package me.braydon.chatutilities.mixin.client;

import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Applies unfocused-chat panel opacity to vanilla chat row fills while chat is closed. */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess")
public class ChatPanelBackgroundAlphaUnfocusedMixin {

    @ModifyArg(
            method = "fill",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"),
            index = 4,
            require = 0)
    private int chatUtilities$multiplyChatRowFillAlphaUnfocused(int color) {
        return ChatUtilitiesClientOptions.multiplyChatPanelBackgroundArgb(color, false);
    }

    @ModifyArg(
            method = "fill",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;fill(Lnet/minecraft/client/renderer/RenderType;IIIII)V"),
            index = 5,
            require = 0)
    private int chatUtilities$multiplyChatRowFillAlphaUnfocused$renderType(int color) {
        return ChatUtilitiesClientOptions.multiplyChatPanelBackgroundArgb(color, false);
    }

    @ModifyArg(
            method = "handleTag",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"),
            index = 4,
            require = 0)
    private int chatUtilities$multiplyTagFillAlphaUnfocused(int color) {
        return ChatUtilitiesClientOptions.multiplyChatPanelBackgroundArgb(color, false);
    }

    @ModifyArg(
            method = "handleTag",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;fill(Lnet/minecraft/client/renderer/RenderType;IIIII)V"),
            index = 5,
            require = 0)
    private int chatUtilities$multiplyTagFillAlphaUnfocused$renderType(int color) {
        return ChatUtilitiesClientOptions.multiplyChatPanelBackgroundArgb(color, false);
    }
}
