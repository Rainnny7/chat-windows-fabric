package me.braydon.window.mixin.client;

import me.braydon.window.chat.ChatWindowManager;
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
    /**
     * Only hook the 3-arg overload: the 1-arg {@code addMessage(Component)} delegates here, and intercepting
     * at HEAD on both would cancel before delegation so system/command lines could never reach the tagged path.
     */
    @Inject(
            method =
                    "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void chatWindows$interceptAddMessage(
            Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        ChatWindowManager mgr = ChatWindowManager.get();
        if (mgr.shouldHideFromMainChat(message)) {
            mgr.dispatchToWindows(message);
            ci.cancel();
        }
    }
}
