package me.braydon.chatutilities.mixin.client;

import me.braydon.chatutilities.ChatUtilitiesModClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chat Peek should expand the HUD chat like pressing T, without opening the chat screen.
 *
 * <p>Peek forces {@link ChatComponent#isChatFocused()} to {@code true} so height/lines match the
 * focused chat settings. {@link GuiRenderChatPeekMixin} redirects the HUD guard in {@code extractChat}
 * and forces the expanded {@code extractRenderState} boolean so the main chat actually draws expanded.
 */
@Mixin(ChatComponent.class)
public class ChatComponentFocusPeekMixin {

    @Inject(method = "isChatFocused()Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void chatUtilities$peekForcesChatFocused(CallbackInfoReturnable<Boolean> cir) {
        if (ChatUtilitiesModClient.CHAT_PEEK_KEY != null && ChatUtilitiesModClient.CHAT_PEEK_KEY.isDown()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && !(mc.screen instanceof ChatScreen)) {
                cir.setReturnValue(true);
            }
        }
    }
}

