package me.braydon.chatutilities.mixin.client;

import me.braydon.chatutilities.chat.ChatTextShadowRenderContext;
import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Targets intermediary {@code class_332} ({@link net.minecraft.client.gui.GuiGraphics}). With
 * {@code argsOnly = true}, {@code ModifyVariable.ordinal} counts parameters of the handler type
 * (here {@code boolean}), not overall JVM argument index — use {@code 0} for the sole shadow flag.
 */
@Mixin(targets = "net.minecraft.class_332", remap = false)
public class GuiGraphicsMixin {

    @ModifyVariable(
            method = "method_51433(Lnet/minecraft/class_327;Ljava/lang/String;IIIZ)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false)
    private boolean chatUtilities$shadowString(boolean shadow) {
        return apply(shadow);
    }

    @ModifyVariable(
            method = "method_51430(Lnet/minecraft/class_327;Lnet/minecraft/class_5481;IIIZ)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false)
    private boolean chatUtilities$shadowOrdered(boolean shadow) {
        return apply(shadow);
    }

    @ModifyVariable(
            method = "method_51439(Lnet/minecraft/class_327;Lnet/minecraft/class_2561;IIIZ)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false)
    private boolean chatUtilities$shadowComponent(boolean shadow) {
        return apply(shadow);
    }

    @ModifyVariable(
            method = "method_51440(Lnet/minecraft/class_327;Lnet/minecraft/class_5348;IIIIZ)V",
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false)
    private boolean chatUtilities$shadowFormattedText(boolean shadow) {
        return apply(shadow);
    }

    private static boolean apply(boolean shadow) {
        if (!ChatTextShadowRenderContext.isActive()) {
            return shadow;
        }
        return ChatUtilitiesClientOptions.isChatTextShadow();
    }
}
