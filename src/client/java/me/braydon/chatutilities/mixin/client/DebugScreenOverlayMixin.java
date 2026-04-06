package me.braydon.chatutilities.mixin.client;

import me.braydon.chatutilities.gui.ChatUtilitiesRootScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Suppresses the F3 debug overlay while Chat Utilities is open so it renders on top. */
@Mixin(targets = "net.minecraft.client.gui.components.DebugScreenOverlay")
public class DebugScreenOverlayMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true, require = 0)
    private void chatUtilities$suppressDebugWhenMenuOpen(GuiGraphics graphics, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof ChatUtilitiesRootScreen) {
            ci.cancel();
        }
    }
}
