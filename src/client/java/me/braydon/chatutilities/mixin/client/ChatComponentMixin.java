package me.braydon.chatutilities.mixin.client;

import me.braydon.chatutilities.chat.ChatTextShadowRenderContext;
import me.braydon.chatutilities.chat.ChatUtilitiesManager;
import me.braydon.chatutilities.chat.VanillaChatLinePicker;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @Inject(
            method =
                    "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at = @At("HEAD"))
    private void chatUtilities$shadowEnterMain(CallbackInfo ci) {
        ChatTextShadowRenderContext.enter();
    }

    @Inject(
            method =
                    "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at = @At("TAIL"))
    private void chatUtilities$shadowExitMain(CallbackInfo ci) {
        ChatTextShadowRenderContext.exit();
    }

    @Inject(
            method = "captureClickableText(Lnet/minecraft/client/gui/ActiveTextCollector;IIZ)V",
            at = @At("HEAD"))
    private void chatUtilities$shadowEnterCapture(
            ActiveTextCollector collector, int windowHeight, int currentTick, boolean expanded, CallbackInfo ci) {
        ChatTextShadowRenderContext.enter();
    }

    @Inject(
            method = "captureClickableText(Lnet/minecraft/client/gui/ActiveTextCollector;IIZ)V",
            at = @At("TAIL"))
    private void chatUtilities$shadowExitCapture(
            ActiveTextCollector collector, int windowHeight, int currentTick, boolean expanded, CallbackInfo ci) {
        ChatTextShadowRenderContext.exit();
    }

    @Inject(
            method =
                    "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V",
            at = @At("HEAD"))
    private void chatUtilities$shadowEnterAccess(CallbackInfo ci) {
        ChatTextShadowRenderContext.enter();
    }

    @Inject(
            method =
                    "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V",
            at = @At("TAIL"))
    private void chatUtilities$shadowExitAccess(CallbackInfo ci) {
        ChatTextShadowRenderContext.exit();
    }

    @Inject(
            method =
                    "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void chatUtilities$interceptAddMessage(
            Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        ChatUtilitiesManager mgr = ChatUtilitiesManager.get();
        ChatUtilitiesManager.ChatIntercept intercept = mgr.interceptChat(message);
        if (intercept != ChatUtilitiesManager.ChatIntercept.DROP) {
            mgr.playMessageSoundsIfApplicable(message);
        }
        switch (intercept) {
            case DROP -> ci.cancel();
            case WINDOWS -> {
                mgr.dispatchToWindows(message);
                ci.cancel();
            }
            case NONE -> {}
        }
    }

    @Inject(method = "clearMessages", at = @At("TAIL"))
    private void chatUtilities$clearCustomWindowsOnChatClear(boolean clearSentMsgHistory, CallbackInfo ci) {
        ChatUtilitiesManager.get().clearAllWindowChatHistory();
    }

    @Redirect(
            method =
                    "forEachLine(Lnet/minecraft/client/gui/components/ChatComponent$AlphaCalculator;Lnet/minecraft/client/gui/components/ChatComponent$LineConsumer;)I",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/components/ChatComponent$LineConsumer;accept(Lnet/minecraft/client/GuiMessage$Line;IF)V"))
    private void chatUtilities$vanillaPickOnLineAccept(
            ChatComponent.LineConsumer consumer, GuiMessage.Line line, int lineIndex, float opacity) {
        VanillaChatLinePicker.notifyLineDuringPick(line, lineIndex, opacity);
        consumer.accept(line, lineIndex, opacity);
    }
}
