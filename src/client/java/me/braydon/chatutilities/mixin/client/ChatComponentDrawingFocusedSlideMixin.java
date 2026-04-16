package me.braydon.chatutilities.mixin.client;

import me.braydon.chatutilities.chat.ChatSmoothAppearance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Shifts per-row chat text, tags, and icons with smooth-chat slide-in (vanilla HUD second pass). */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess")
public class ChatComponentDrawingFocusedSlideMixin {

    private static int chatUtilities$slide() {
        return ChatSmoothAppearance.vanillaChatLineSlideYPixels();
    }

    @ModifyArg(
            method = "fill",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"),
            index = 1,
            require = 0)
    private int chatUtilities$slideFocusedFillY1(int y) {
        return y + chatUtilities$slide();
    }

    @ModifyArg(
            method = "fill",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;fill(Lnet/minecraft/client/renderer/RenderType;IIIII)V"),
            index = 2,
            require = 0)
    private int chatUtilities$slideFocusedFillY1$renderType(int y) {
        return y + chatUtilities$slide();
    }

    @ModifyArg(
            method = "fill",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"),
            index = 3,
            require = 0)
    private int chatUtilities$slideFocusedFillY2(int y) {
        return y + chatUtilities$slide();
    }

    @ModifyArg(
            method = "fill",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;fill(Lnet/minecraft/client/renderer/RenderType;IIIII)V"),
            index = 4,
            require = 0)
    private int chatUtilities$slideFocusedFillY2$renderType(int y) {
        return y + chatUtilities$slide();
    }

    @ModifyArg(
            method = "handleMessage",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/ActiveTextCollector;accept(Lnet/minecraft/client/gui/TextAlignment;IILnet/minecraft/client/gui/ActiveTextCollector$Parameters;Lnet/minecraft/util/FormattedCharSequence;)V"),
            index = 2)
    private int chatUtilities$slideHandleMessageY(int y) {
        return y + chatUtilities$slide();
    }

    @ModifyArg(
            method = "handleTag",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"),
            index = 1,
            require = 0)
    private int chatUtilities$slideTagFillY1(int y) {
        return y + chatUtilities$slide();
    }

    @ModifyArg(
            method = "handleTag",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;fill(Lnet/minecraft/client/renderer/RenderType;IIIII)V"),
            index = 2,
            require = 0)
    private int chatUtilities$slideTagFillY1$renderType(int y) {
        return y + chatUtilities$slide();
    }

    @ModifyArg(
            method = "handleTag",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V"),
            index = 3,
            require = 0)
    private int chatUtilities$slideTagFillY2(int y) {
        return y + chatUtilities$slide();
    }

    @ModifyArg(
            method = "handleTag",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;fill(Lnet/minecraft/client/renderer/RenderType;IIIII)V"),
            index = 4,
            require = 0)
    private int chatUtilities$slideTagFillY2$renderType(int y) {
        return y + chatUtilities$slide();
    }

    @ModifyArg(
            method = "handleTag",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/components/ChatComponent$DrawingFocusedGraphicsAccess;isMouseOver(IIII)Z"),
            index = 1)
    private int chatUtilities$slideTagHitTop(int y) {
        return y + chatUtilities$slide();
    }

    @ModifyArg(
            method = "handleTag",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/components/ChatComponent$DrawingFocusedGraphicsAccess;isMouseOver(IIII)Z"),
            index = 3)
    private int chatUtilities$slideTagHitBottom(int y) {
        return y + chatUtilities$slide();
    }

    @ModifyArg(
            method = "handleTagIcon",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/multiplayer/chat/GuiMessageTag$Icon;draw(Lnet/minecraft/client/gui/GuiGraphics;II)V"),
            index = 2)
    private int chatUtilities$slideTagIconY(int y) {
        return y + chatUtilities$slide();
    }

    @ModifyArg(
            method = "handleTagIcon",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/components/ChatComponent$DrawingFocusedGraphicsAccess;isMouseOver(IIII)Z"),
            index = 1)
    private int chatUtilities$slideTagIconHitTop(int y) {
        return y + chatUtilities$slide();
    }

    @ModifyArg(
            method = "handleTagIcon",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/components/ChatComponent$DrawingFocusedGraphicsAccess;isMouseOver(IIII)Z"),
            index = 3)
    private int chatUtilities$slideTagIconHitBottom(int y) {
        return y + chatUtilities$slide();
    }
}
