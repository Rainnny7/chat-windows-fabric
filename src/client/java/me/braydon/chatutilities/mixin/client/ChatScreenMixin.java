package me.braydon.chatutilities.mixin.client;

import me.braydon.chatutilities.chat.ChatClickCopyHandler;
import me.braydon.chatutilities.chat.ChatSymbolPalette;
import me.braydon.chatutilities.chat.ChatSymbolPaletteLayer;
import me.braydon.chatutilities.chat.ChatUtilitiesHud;
import me.braydon.chatutilities.chat.ChatUtilitiesManager;
import me.braydon.chatutilities.chat.ChatWindowClickHandler;
import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import me.braydon.chatutilities.command.ChatUtilitiesCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Mth;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chat screen draws {@link net.minecraft.client.gui.components.CommandSuggestions} after {@code super.render()},
 * so a normal {@code Button} widget was covered. The chip and symbol palette are drawn from Fabric
 * {@link ScreenEvents#afterRender} so they layer above other mods that inject into {@code ChatScreen#render}; clicks
 * are still handled in this mixin.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    private static final int chatUtilities$BTN_GAP = 3;
    /** Space between symbol chip and settings chip (smaller than {@link #chatUtilities$BTN_GAP}). */
    private static final int chatUtilities$BTN_GAP_INNER = 1;
    private static final int chatUtilities$BTN_W = 20;
    /** Vanilla font baseline sits slightly low in the chat bar; nudge the chip up for optical centering. */
    private static final int chatUtilities$CHIP_Y_NUDGE = -2;

    @Shadow
    protected EditBox input;

    @Shadow
    @Final
    private CommandSuggestions commandSuggestions;

    @Unique
    private final ChatSymbolPalette chatUtilities$palette = new ChatSymbolPalette();

    @Unique
    private boolean chatUtilities$barAnimActive;

    @Unique
    private long chatUtilities$barAnimStartMs;

    @Unique
    private int chatUtilities$inputAnchorY;

    @Unique
    private int chatUtilities$symAnchorY;

    @Unique
    private int chatUtilities$layoutW = Integer.MIN_VALUE;

    @Unique
    private int chatUtilities$layoutH = Integer.MIN_VALUE;

    /** Same vertical offset applied to the chat bar {@code fill} and the {@link EditBox}. */
    @Unique
    private int chatUtilities$barVisualDy;

    /** Symbol button bounds in screen space; {@code width == 0} means disabled (narrow chat). */
    @Unique
    private int chatUtilities$symX;

    @Unique
    private int chatUtilities$symY;

    @Unique
    private int chatUtilities$symW;

    @Unique
    private int chatUtilities$symH;

    @Unique
    private int chatUtilities$menuX;

    @Unique
    private int chatUtilities$menuY;

    @Unique
    private int chatUtilities$menuW;

    @Unique
    private int chatUtilities$menuH;

    @Unique
    private int chatUtilities$menuAnchorY;

    @Unique
    private boolean chatUtilities$fabricAfterRenderHooked;

    @Inject(method = "init", at = @At("TAIL"))
    private void chatUtilities$afterInit(CallbackInfo ci) {
        chatUtilities$symW = 0;
        chatUtilities$menuW = 0;
        ChatScreen self = (ChatScreen) (Object) this;
        if (input != null && ChatUtilitiesClientOptions.isShowChatSymbolSelector()) {
            int fullW = input.getWidth();
            boolean menuChip = ChatUtilitiesClientOptions.isShowChatBarMenuButton();
            int shrink =
                    menuChip
                            ? chatUtilities$BTN_GAP
                                    + chatUtilities$BTN_W
                                    + chatUtilities$BTN_GAP_INNER
                                    + chatUtilities$BTN_W
                                    + chatUtilities$BTN_GAP
                            : chatUtilities$BTN_W + chatUtilities$BTN_GAP;
            if (fullW > shrink + 40) {
                input.setWidth(fullW - shrink);
                chatUtilities$symX = input.getX() + input.getWidth() + chatUtilities$BTN_GAP;
                chatUtilities$symW = chatUtilities$BTN_W;
                chatUtilities$symH = Math.max(input.getHeight(), 12);
                chatUtilities$symY =
                        input.getY()
                                + (input.getHeight() - chatUtilities$symH) / 2
                                + chatUtilities$CHIP_Y_NUDGE;
                if (menuChip) {
                    chatUtilities$menuX =
                            chatUtilities$symX
                                    + chatUtilities$symW
                                    + chatUtilities$BTN_GAP_INNER;
                    chatUtilities$menuW = chatUtilities$BTN_W;
                    chatUtilities$menuH = chatUtilities$symH;
                    chatUtilities$menuY = chatUtilities$symY;
                }
            }
        }
        if (!chatUtilities$fabricAfterRenderHooked) {
            chatUtilities$fabricAfterRenderHooked = true;
            ScreenEvents.afterRender(self)
                    .register(
                            (screen, graphics, mouseX, mouseY, partialTick) ->
                                    ChatSymbolPaletteLayer.render(
                                            graphics,
                                            Minecraft.getInstance().font,
                                            screen.width,
                                            screen.height,
                                            mouseX,
                                            mouseY));
        }
        chatUtilities$initBarSlideAfterLayout(self);
    }

    @Unique
    private void chatUtilities$initBarSlideAfterLayout(ChatScreen self) {
        chatUtilities$layoutW = self.width;
        chatUtilities$layoutH = self.height;
        chatUtilities$barAnimActive = false;
        chatUtilities$barVisualDy = 0;
        if (input == null) {
            return;
        }
        if (!ChatUtilitiesClientOptions.isSmoothChat()) {
            return;
        }
        int dur = ChatUtilitiesClientOptions.getSmoothChatBarOpenMs();
        if (dur <= 0) {
            return;
        }
        chatUtilities$barAnimActive = true;
        chatUtilities$barAnimStartMs = System.currentTimeMillis();
        chatUtilities$inputAnchorY = input.getY();
        chatUtilities$symAnchorY =
                chatUtilities$symW > 0
                        ? input.getY()
                                + (input.getHeight() - chatUtilities$symH) / 2
                                + chatUtilities$CHIP_Y_NUDGE
                        : 0;
        chatUtilities$menuAnchorY =
                chatUtilities$menuW > 0
                        ? input.getY()
                                + (input.getHeight() - chatUtilities$menuH) / 2
                                + chatUtilities$CHIP_Y_NUDGE
                        : 0;
        chatUtilities$applyBarSlideOffset(chatUtilities$slideDistance());
    }

    @Unique
    private int chatUtilities$slideDistance() {
        return input != null ? Math.max(12, input.getHeight() + 4) : 12;
    }

    @Unique
    private void chatUtilities$applyBarSlideOffset(int dy) {
        chatUtilities$barVisualDy = dy;
        if (input == null) {
            return;
        }
        input.setY(chatUtilities$inputAnchorY + dy);
        if (chatUtilities$symW > 0) {
            chatUtilities$symY = chatUtilities$symAnchorY + dy;
        }
        if (chatUtilities$menuW > 0) {
            chatUtilities$menuY = chatUtilities$menuAnchorY + dy;
        }
        commandSuggestions.updateCommandInfo();
    }

    /**
     * Vanilla draws the chat bar backdrop at a fixed Y; the {@link EditBox} is moved separately, so without this the
     * bar appears stuck while the text and emoji chip slide.
     */
    @Redirect(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(IIIII)V",
                            ordinal = 0))
    private void chatUtilities$offsetChatBarFill(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dy = chatUtilities$barVisualDy;
        graphics.fill(x0, y0 + dy, x1, y1 + dy, color);
    }

    @Unique
    private void chatUtilities$refreshBarSlide(ChatScreen self) {
        if (input == null) {
            return;
        }
        if (chatUtilities$layoutW != self.width || chatUtilities$layoutH != self.height) {
            chatUtilities$layoutW = self.width;
            chatUtilities$layoutH = self.height;
            chatUtilities$barAnimActive = false;
            chatUtilities$barVisualDy = 0;
            chatUtilities$inputAnchorY = input.getY();
            if (chatUtilities$symW > 0) {
                chatUtilities$symY =
                        input.getY()
                                + (input.getHeight() - chatUtilities$symH) / 2
                                + chatUtilities$CHIP_Y_NUDGE;
                chatUtilities$symAnchorY = chatUtilities$symY;
            }
            if (chatUtilities$menuW > 0) {
                chatUtilities$menuY =
                        input.getY()
                                + (input.getHeight() - chatUtilities$menuH) / 2
                                + chatUtilities$CHIP_Y_NUDGE;
                chatUtilities$menuAnchorY = chatUtilities$menuY;
            }
            commandSuggestions.updateCommandInfo();
        }
        if (!chatUtilities$barAnimActive) {
            return;
        }
        if (!ChatUtilitiesClientOptions.isSmoothChat()) {
            chatUtilities$barAnimActive = false;
            chatUtilities$applyBarSlideOffset(0);
            return;
        }
        int dur = ChatUtilitiesClientOptions.getSmoothChatBarOpenMs();
        if (dur <= 0) {
            chatUtilities$barAnimActive = false;
            chatUtilities$applyBarSlideOffset(0);
            return;
        }
        long elapsed = System.currentTimeMillis() - chatUtilities$barAnimStartMs;
        float t = Mth.clamp(elapsed / (float) dur, 0f, 1f);
        float eased = 1f - (float) Math.pow(1f - t, 3);
        int slide = chatUtilities$slideDistance();
        int dy = Math.round((1f - eased) * slide);
        chatUtilities$applyBarSlideOffset(dy);
        if (t >= 1f) {
            chatUtilities$barAnimActive = false;
        }
    }

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V", at = @At("HEAD"))
    private void chatUtilities$renderHeadBarSlide(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        chatUtilities$refreshBarSlide((ChatScreen) (Object) this);
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void chatUtilities$onRemoved(CallbackInfo ci) {
        chatUtilities$palette.setOpen(false);
        chatUtilities$symW = 0;
        chatUtilities$menuW = 0;
        chatUtilities$barAnimActive = false;
        chatUtilities$barVisualDy = 0;
        chatUtilities$layoutW = Integer.MIN_VALUE;
        chatUtilities$layoutH = Integer.MIN_VALUE;
    }

    @Unique
    private boolean chatUtilities$symbolHit(double mx, double my) {
        return chatUtilities$symW > 0
                && mx >= chatUtilities$symX
                && mx < chatUtilities$symX + chatUtilities$symW
                && my >= chatUtilities$symY
                && my < chatUtilities$symY + chatUtilities$symH;
    }

    @Unique
    private boolean chatUtilities$menuHit(double mx, double my) {
        return chatUtilities$menuW > 0
                && mx >= chatUtilities$menuX
                && mx < chatUtilities$menuX + chatUtilities$menuW
                && my >= chatUtilities$menuY
                && my < chatUtilities$menuY + chatUtilities$menuH;
    }

    @Inject(
            method = "mouseClicked(Lnet/minecraft/client/input/MouseButtonEvent;Z)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void chatUtilities$mouseClickedHead(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        ChatScreen self = (ChatScreen) (Object) this;
        int sw = self.width;
        int sh = self.height;
        double mx = event.x();
        double my = event.y();
        if (!ChatUtilitiesManager.get().isPositioning()
                && ChatClickCopyHandler.tryHandleCopyClick(
                        Minecraft.getInstance(), self, mx, my, event.button())) {
            cir.setReturnValue(true);
            return;
        }
        if (event.button() == 0
                && !ChatUtilitiesManager.get().isPositioning()
                && ChatWindowClickHandler.tryHandleClick(Minecraft.getInstance(), mx, my, event.button())) {
            cir.setReturnValue(true);
            return;
        }
        if (event.button() == 0 && chatUtilities$menuHit(mx, my)) {
            chatUtilities$palette.setOpen(false);
            ChatSymbolPalette.playUiClickSound();
            ChatUtilitiesCommands.openMenuNextTick(Minecraft.getInstance());
            cir.setReturnValue(true);
            return;
        }
        if (event.button() == 0 && chatUtilities$symbolHit(mx, my)) {
            chatUtilities$palette.toggle();
            ChatSymbolPalette.playUiClickSound();
            cir.setReturnValue(true);
            return;
        }
        if (!chatUtilities$palette.isOpen()) {
            return;
        }
        if (chatUtilities$symbolHit(mx, my)) {
            return;
        }
        if (chatUtilities$palette.containsPoint(mx, my, sw, sh)) {
            chatUtilities$palette.mouseClicked(mx, my, event.button(), input, sw, sh);
            cir.setReturnValue(true);
            return;
        }
        chatUtilities$palette.setOpen(false);
    }

    @Inject(
            method = "mouseScrolled(DDDD)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void chatUtilities$mouseScrolledHead(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount,
            CallbackInfoReturnable<Boolean> cir) {
        ChatScreen self = (ChatScreen) (Object) this;
        if (chatUtilities$palette.mouseScrolled(
                mouseX, mouseY, verticalAmount, self.width, self.height)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(
            method = "render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            at = @At("TAIL"))
    private void chatUtilities$renderOverChatUi(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ChatSymbolPaletteLayer.prepare(
                chatUtilities$palette,
                chatUtilities$symW > 0,
                chatUtilities$symX,
                chatUtilities$symY,
                chatUtilities$symW,
                chatUtilities$symH,
                chatUtilities$menuW > 0,
                chatUtilities$menuX,
                chatUtilities$menuY,
                chatUtilities$menuW,
                chatUtilities$menuH);
    }
}
