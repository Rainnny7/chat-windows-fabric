package me.braydon.chatutilities.mixin.client;

import me.braydon.chatutilities.chat.ChatUtilitiesManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void chatUtilities$cancelAttackWhilePositioning(CallbackInfoReturnable<Boolean> cir) {
        if (ChatUtilitiesManager.get().isPositioning()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void chatUtilities$cancelContinueAttackWhilePositioning(boolean leftClick, CallbackInfo ci) {
        if (ChatUtilitiesManager.get().isPositioning()) {
            ci.cancel();
        }
    }
}
