package me.braydon.window.mixin.client;

import me.braydon.window.chat.ChatWindowManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void chatWindows$cancelAttackWhilePositioning(CallbackInfoReturnable<Boolean> cir) {
        if (ChatWindowManager.get().isPositioning()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void chatWindows$cancelContinueAttackWhilePositioning(boolean leftClick, CallbackInfo ci) {
        if (ChatWindowManager.get().isPositioning()) {
            ci.cancel();
        }
    }
}
