package me.braydon.chatutilities.mixin.client;

import me.braydon.chatutilities.chat.ChatTextShadowRenderContext;
import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Overrides the {@code dropShadow} flag for {@link GuiGraphicsExtractor#text} during vanilla chat rendering.
 *
 * <p>Gated by {@link ChatTextShadowRenderContext} so this does not affect other UI text.
 */
@Mixin(GuiGraphicsExtractor.class)
public class GuiGraphicsMixin {

    @ModifyVariable(
            method = "text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private boolean chatUtilities$shadowString(boolean dropShadow) {
        return apply(dropShadow);
    }

    @ModifyVariable(
            method =
                    "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private boolean chatUtilities$shadowOrdered(boolean dropShadow) {
        return apply(dropShadow);
    }

    @ModifyVariable(
            method = "text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0)
    private boolean chatUtilities$shadowComponent(boolean dropShadow) {
        return apply(dropShadow);
    }

    private static boolean apply(boolean dropShadow) {
        if (!ChatTextShadowRenderContext.isActive()) {
            return dropShadow;
        }
        return ChatUtilitiesClientOptions.isChatTextShadow();
    }
}
