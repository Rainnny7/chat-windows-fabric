package me.braydon.chatutilities.mixin.client;

import me.braydon.chatutilities.ChatUtilitiesModClient;
import me.braydon.chatutilities.chat.ChatMessageRebuildGuard;
import me.braydon.chatutilities.chat.ChatSearchState;
import me.braydon.chatutilities.chat.ChatSmoothAppearance;
import me.braydon.chatutilities.chat.ChatTextShadowRenderContext;
import me.braydon.chatutilities.chat.ChatTimestampFormatter;
import me.braydon.chatutilities.chat.ChatUtilitiesHud;
import me.braydon.chatutilities.chat.ChatUtilitiesManager;
import me.braydon.chatutilities.chat.VanillaChatLinePicker;
import me.braydon.chatutilities.chat.VanillaChatRepeatStacker;
import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    /** True only when this {@code addMessage} call will run vanilla’s append (not window-only / dropped). */
    @Unique
    private boolean chatUtilities$stackVanillaAfterAdd;

    /** Avoid re-running routing when {@link ChatTimestampFormatter} re-enters {@code addMessage} with a prefixed line. */
    @Unique
    private static final ThreadLocal<Boolean> chatUtilities$vanillaTimestampRecursing =
            ThreadLocal.withInitial(() -> false);

    /**
     * 1.21+ moves the history cap into {@code addMessageToQueue} / {@code addMessageToDisplayQueue} (literal
     * {@value ChatUtilitiesClientOptions#VANILLA_CHAT_HISTORY_LINES} on each list), not in {@code addMessage} itself.
     */
    @ModifyConstant(
            method = {
                "addMessageToQueue(Lnet/minecraft/client/GuiMessage;)V",
                "addMessageToDisplayQueue(Lnet/minecraft/client/GuiMessage;)V"
            },
            constant = @Constant(intValue = ChatUtilitiesClientOptions.VANILLA_CHAT_HISTORY_LINES))
    private int chatUtilities$modifyMaxChatHistory(int original) {
        return ChatUtilitiesClientOptions.getEffectiveChatHistoryLimit();
    }

    @Inject(
            method = {
                "addMessageToQueue(Lnet/minecraft/client/GuiMessage;)V",
                "addMessageToDisplayQueue(Lnet/minecraft/client/GuiMessage;)V"
            },
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void chatUtilities$interceptQueueAdds(GuiMessage msg, CallbackInfo ci) {
        if (msg == null) {
            return;
        }
        if (Boolean.TRUE.equals(chatUtilities$vanillaTimestampRecursing.get())) {
            return;
        }
        if (ChatMessageRebuildGuard.isActive()) {
            return;
        }
        ChatUtilitiesManager mgr = ChatUtilitiesManager.get();
        Component c = msg.content();
        if (c == null) {
            return;
        }
        mgr.triggerAutoResponsesIfApplicable(c);
        c = mgr.applyChatTextReplacementsIfApplicable(c);
        ChatUtilitiesManager.ChatIntercept intercept = mgr.interceptChat(c);
        if (intercept != ChatUtilitiesManager.ChatIntercept.DROP) {
            mgr.playMessageSoundsIfApplicable(c);
        }
        if (intercept == ChatUtilitiesManager.ChatIntercept.DROP) {
            ci.cancel();
            return;
        }
        if (intercept == ChatUtilitiesManager.ChatIntercept.WINDOWS) {
            int tick = Minecraft.getInstance().gui.getGuiTicks();
            String plain = ChatUtilitiesManager.plainTextForMatching(c);
            mgr.dispatchToWindows(c);
            mgr.markVanillaSmoothSuppressForWindowOnlyChat(plain, tick);
            ci.cancel();
        }
    }

    @Inject(
            method =
                    "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at = @At("HEAD"),
            cancellable = true)
    private void chatUtilities$suppressVanillaChatWhenUnreadTabsShown(CallbackInfo ci) {
        if (ChatUtilitiesHud.shouldSuppressVanillaChatHud(Minecraft.getInstance())) {
            ci.cancel();
        }
    }

    @Inject(
            method =
                    "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at = @At("HEAD"))
    private void chatUtilities$shadowEnterMain(CallbackInfo ci) {
        ChatTextShadowRenderContext.enter();
    }

    @Inject(
            method =
                    "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at = @At("TAIL"))
    private void chatUtilities$shadowExitMain(CallbackInfo ci) {
        ChatTextShadowRenderContext.exit();
    }

    @ModifyVariable(
            method =
                    "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true)
    private boolean chatUtilities$peekForcesVanillaRenderFlag0(boolean original) {
        if (original) {
            return true;
        }
        if (ChatUtilitiesModClient.CHAT_PEEK_KEY != null && ChatUtilitiesModClient.CHAT_PEEK_KEY.isDown()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && !(mc.screen instanceof ChatScreen)) {
                return true;
            }
        }
        return false;
    }

    @ModifyVariable(
            method =
                    "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at = @At("HEAD"),
            ordinal = 1,
            argsOnly = true)
    private boolean chatUtilities$peekForcesVanillaRenderFlag1(boolean original) {
        if (original) {
            return true;
        }
        if (ChatUtilitiesModClient.CHAT_PEEK_KEY != null && ChatUtilitiesModClient.CHAT_PEEK_KEY.isDown()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && !(mc.screen instanceof ChatScreen)) {
                return true;
            }
        }
        return false;
    }

    @Inject(
            method = "captureClickableText(Lnet/minecraft/client/gui/ActiveTextCollector;IIZ)V",
            at = @At("HEAD"))
    private void chatUtilities$shadowEnterCapture(
            ActiveTextCollector collector, int windowHeight, int currentTick, boolean expanded, CallbackInfo ci) {
        ChatTextShadowRenderContext.enter();
    }

    @Inject(
            method = "captureClickableText(Lnet/minecraft/client/gui/ActiveTextCollector;IIZ)V",
            at = @At("TAIL"))
    private void chatUtilities$shadowExitCapture(
            ActiveTextCollector collector, int windowHeight, int currentTick, boolean expanded, CallbackInfo ci) {
        ChatTextShadowRenderContext.exit();
    }

    @Inject(
            method =
                    "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V",
            at = @At("HEAD"))
    private void chatUtilities$shadowEnterAccess(CallbackInfo ci) {
        ChatTextShadowRenderContext.enter();
    }

    @Inject(
            method =
                    "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V",
            at = @At("TAIL"))
    private void chatUtilities$shadowExitAccess(CallbackInfo ci) {
        ChatTextShadowRenderContext.exit();
    }

    @ModifyVariable(
            method =
                    "render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true)
    private boolean chatUtilities$peekForcesVanillaExpandedArg(boolean expanded) {
        if (expanded) {
            return true;
        }
        if (ChatUtilitiesModClient.CHAT_PEEK_KEY != null && ChatUtilitiesModClient.CHAT_PEEK_KEY.isDown()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && !(mc.screen instanceof ChatScreen)) {
                return true;
            }
        }
        return false;
    }

    @ModifyVariable(
            method =
                    "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"),
            ordinal = 0,
            argsOnly = true)
    private Component chatUtilities$applyColorHighlights(Component message) {
        return ChatUtilitiesManager.get().applyChatColorHighlights(message);
    }

    @Inject(
            method =
                    "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"),
            cancellable = true)
    private void chatUtilities$interceptAddMessage(
            Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        if (Boolean.TRUE.equals(chatUtilities$vanillaTimestampRecursing.get())) {
            chatUtilities$stackVanillaAfterAdd = true;
            return;
        }
        if (ChatMessageRebuildGuard.isActive()) {
            chatUtilities$stackVanillaAfterAdd = true;
            return;
        }
        ChatUtilitiesManager mgr = ChatUtilitiesManager.get();
        if (mgr.shouldBufferAsEarlyMessage()) {
            mgr.bufferEarlyMessage(message);
            ci.cancel();
            return;
        }
        mgr.triggerAutoResponsesIfApplicable(message);
        message = mgr.applyChatTextReplacementsIfApplicable(message);
        ChatUtilitiesManager.ChatIntercept intercept = mgr.interceptChat(message);
        chatUtilities$stackVanillaAfterAdd = intercept == ChatUtilitiesManager.ChatIntercept.NONE;
        if (intercept != ChatUtilitiesManager.ChatIntercept.DROP) {
            mgr.playMessageSoundsIfApplicable(message);
        }
        switch (intercept) {
            case DROP -> ci.cancel();
            case WINDOWS -> {
                int tick = Minecraft.getInstance().gui.getGuiTicks();
                String plain = ChatUtilitiesManager.plainTextForMatching(message);
                mgr.dispatchToWindows(message);
                mgr.markVanillaSmoothSuppressForWindowOnlyChat(plain, tick);
                ci.cancel();
            }
            case NONE -> {
                if (ChatUtilitiesClientOptions.isChatTimestampsEnabled()
                        && !ChatTimestampFormatter.formatNowPlain().isBlank()) {
                    ci.cancel();
                    long receivedMs = System.currentTimeMillis();
                    chatUtilities$vanillaTimestampRecursing.set(true);
                    try {
                        ((ChatComponent) (Object) this)
                                .addMessage(
                                        Component.empty()
                                                .append(
                                                        ChatTimestampFormatter.componentAtMillis(receivedMs))
                                                .append(message),
                                        signature,
                                        tag);
                    } finally {
                        chatUtilities$vanillaTimestampRecursing.remove();
                    }
                }
            }
        }
    }

    /**
     * Some chat-like lines (notably certain system/info messages) can enter through overloads that don't include
     * signature/tag. We still want window routing to apply so filtered lines don't leak into vanilla chat.
     *
     * <p>These injections are optional (require=0) to avoid hard crashes across minor MC mapping changes.
     */
    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void chatUtilities$interceptAddMessage_componentOnly(Component message, CallbackInfo ci) {
        chatUtilities$interceptWindowRoutingOnly(message, ci);
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0)
    private void chatUtilities$interceptAddMessage_componentTag(Component message, GuiMessageTag tag, CallbackInfo ci) {
        chatUtilities$interceptWindowRoutingOnly(message, ci);
    }

    @Unique
    private void chatUtilities$interceptWindowRoutingOnly(Component message, CallbackInfo ci) {
        if (message == null) {
            return;
        }
        if (Boolean.TRUE.equals(chatUtilities$vanillaTimestampRecursing.get())) {
            return;
        }
        ChatUtilitiesManager mgr = ChatUtilitiesManager.get();
        if (mgr.shouldBufferAsEarlyMessage()) {
            mgr.bufferEarlyMessage(message);
            ci.cancel();
            return;
        }
        Component m = message;
        // Keep behavior consistent with the main addMessage hook.
        mgr.triggerAutoResponsesIfApplicable(m);
        m = mgr.applyChatTextReplacementsIfApplicable(m);

        ChatUtilitiesManager.ChatIntercept intercept = mgr.interceptChat(m);
        if (intercept != ChatUtilitiesManager.ChatIntercept.DROP) {
            mgr.playMessageSoundsIfApplicable(m);
        }
        if (intercept == ChatUtilitiesManager.ChatIntercept.DROP) {
            ci.cancel();
            return;
        }
        if (intercept == ChatUtilitiesManager.ChatIntercept.WINDOWS) {
            int tick = Minecraft.getInstance().gui.getGuiTicks();
            String plain = ChatUtilitiesManager.plainTextForMatching(m);
            mgr.dispatchToWindows(m);
            mgr.markVanillaSmoothSuppressForWindowOnlyChat(plain, tick);
            ci.cancel();
        }
    }

    @Inject(
            method =
                    "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("TAIL"))
    private void chatUtilities$stackRepeatedMessages(
            Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        if (!chatUtilities$stackVanillaAfterAdd) {
            return;
        }
        chatUtilities$stackVanillaAfterAdd = false;
        VanillaChatRepeatStacker.afterAddMessage((ChatComponent) (Object) this, message);
    }

    @Inject(method = "clearMessages(Z)V", at = @At("HEAD"), cancellable = true)
    private void chatUtilities$preserveVanillaChatOnSoftClear(boolean clearSentChatHistory, CallbackInfo ci) {
        if (!ChatUtilitiesClientOptions.isPreserveVanillaChatOnDisconnect()) {
            return;
        }
        // Leaving a world/server typically clears the HUD log with {@code false}; full clears (including sent
        // history) use {@code true}. Only skip the soft clear so manual “clear chat” flows can still wipe history.
        if (!clearSentChatHistory) {
            ci.cancel();
        }
    }

    @Inject(method = "clearMessages(Z)V", at = @At("TAIL"))
    private void chatUtilities$clearCustomWindowsOnChatClear(boolean clearSentMsgHistory, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null || mc.player == null) {
            return;
        }
        // Keep per-server/profile window history in memory across disconnect / world changes.
        // Also keep it across server-triggered clear-chat events (always-on; no user toggle).
    }

    @Redirect(
            method =
                    "forEachLine(Lnet/minecraft/client/gui/components/ChatComponent$AlphaCalculator;Lnet/minecraft/client/gui/components/ChatComponent$LineConsumer;)I",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/components/ChatComponent$LineConsumer;accept(Lnet/minecraft/client/GuiMessage$Line;IF)V"))
    private void chatUtilities$vanillaPickOnLineAccept(
            ChatComponent.LineConsumer consumer, GuiMessage.Line line, int lineIndex, float opacity) {
        Minecraft mc = Minecraft.getInstance();
        ChatComponent self = (ChatComponent) (Object) this;
        boolean filter =
                mc != null
                        && mc.screen instanceof ChatScreen
                        && ChatUtilitiesClientOptions.isChatSearchBarEnabled()
                        && ChatSearchState.isFiltering();
        if (filter && !VanillaChatLinePicker.vanillaTrimmedLineMatchesOpenChatSearch(self, line)) {
            return;
        }
        boolean suppressSmooth =
                ChatUtilitiesManager.get().shouldSuppressVanillaSmoothForLine(line);
        float smooth =
                VanillaChatLinePicker.isPickCaptureActive()
                        ? 1f
                        : suppressSmooth
                                ? 1f
                                : ChatSmoothAppearance.fadeInMultiplier(line.addedTime());
        float pulse = ChatUtilitiesManager.get().vanillaStackPulseOpacityBoost(line.addedTime());
        float outOpacity = Mth.clamp(opacity * smooth + pulse, 0f, 1f);
        VanillaChatLinePicker.notifyLineDuringPick(line, lineIndex, outOpacity);
        int slideY =
                suppressSmooth ? 0 : ChatSmoothAppearance.fadeSlideOffsetYPixels(line.addedTime());
        ChatSmoothAppearance.setVanillaChatLineSlideYPixels(slideY);
        try {
            consumer.accept(line, lineIndex, outOpacity);
        } finally {
            ChatSmoothAppearance.clearVanillaChatLineSlideYPixels();
        }
    }
}
