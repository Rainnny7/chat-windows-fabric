package me.braydon.chatutilities.mixin.client;

import me.braydon.chatutilities.chat.ChatUtilitiesManager;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {
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
}
