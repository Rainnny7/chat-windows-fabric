package me.braydon.chatutilities.mixin.client;

import me.braydon.chatutilities.chat.ChatImagePreviewUrlResolver;
import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jspecify.annotations.Nullable;

/**
 * Suppress vanilla component hover tooltips while the image preview tooltip is active, so only one tooltip is shown.
 *
 * <p>Mixin target is {@link Screen} (stable) rather than {@link ChatScreen} call sites (brittle across versions).
 */
@Mixin(Screen.class)
public class ScreenMixin {

    @Inject(
            method = "renderComponentHoverEffect(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/network/chat/Style;II)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void chatUtilities$suppressHoverTooltipWhenImagePreviewActive(
            GuiGraphicsExtractor graphics, Style style, int mouseX, int mouseY, CallbackInfo ci) {
        suppressIfPreviewHover(mouseX, mouseY, ci);
    }

    @Inject(
            method =
                    "renderComponentHoverEffect(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/network/chat/Style;IILnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void chatUtilities$suppressHoverTooltipWhenImagePreviewActive_component(
            GuiGraphicsExtractor graphics, Style style, int mouseX, int mouseY, @Nullable Component component, CallbackInfo ci) {
        suppressIfPreviewHover(mouseX, mouseY, ci);
    }

    private static void suppressIfPreviewHover(int mouseX, int mouseY, CallbackInfo ci) {
        if (!ChatUtilitiesClientOptions.isImageChatPreviewEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof ChatScreen)) {
            return;
        }
        if (ChatImagePreviewUrlResolver.findPreviewUrl(mc, mouseX, mouseY).isPresent()) {
            ci.cancel();
        }
    }
}

