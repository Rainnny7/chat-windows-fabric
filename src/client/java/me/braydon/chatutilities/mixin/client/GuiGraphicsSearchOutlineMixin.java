package me.braydon.chatutilities.mixin.client;

import me.braydon.chatutilities.chat.ChatSearchUi;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link net.minecraft.client.gui.components.EditBox} inherits {@code renderWidget} from
 * {@link net.minecraft.client.gui.components.AbstractWidget}; the outline call is not in {@code EditBox}'s class
 * file, so a redirect on {@code EditBox} finds no targets. Suppress outline only for the bound chat-search field by
 * matching its widget bounds.
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class GuiGraphicsSearchOutlineMixin {

    @Inject(method = "outline(IIIII)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void chatUtilities$suppressVanillaSearchOutline(
            int x, int y, int w, int h, int color, CallbackInfo ci) {
        if (ChatSearchUi.shouldSuppressBoundSearchOutline(x, y, w, h)) {
            ci.cancel();
        }
    }
}
