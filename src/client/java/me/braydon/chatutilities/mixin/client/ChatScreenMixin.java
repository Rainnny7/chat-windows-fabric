package me.braydon.chatutilities.mixin.client;

import me.braydon.chatutilities.chat.ChatSymbolPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Chat screen draws {@link net.minecraft.client.gui.components.CommandSuggestions} after {@code super.render()},
 * so a normal {@code Button} widget was covered. We paint the symbol chip in {@code render} TAIL (same pass as the
 * palette) and handle clicks manually.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    private static final int chatUtilities$BTN_GAP = 3;
    private static final int chatUtilities$BTN_W = 20;
    /** Vanilla chat line is 12px tall; the bar chrome reads slightly taller—center the chip in this strip. */
    private static final int chatUtilities$CHAT_BAR_H = 14;

    @Shadow
    protected EditBox input;

    @Unique
    private final ChatSymbolPalette chatUtilities$palette = new ChatSymbolPalette();

    /** Symbol button bounds in screen space; {@code width == 0} means disabled (narrow chat). */
    @Unique
    private int chatUtilities$symX;

    @Unique
    private int chatUtilities$symY;

    @Unique
    private int chatUtilities$symW;

    @Unique
    private int chatUtilities$symH;

    @Inject(method = "init", at = @At("TAIL"))
    private void chatUtilities$afterInit(CallbackInfo ci) {
        chatUtilities$symW = 0;
        if (input == null) {
            return;
        }
        int fullW = input.getWidth();
        int shrink = chatUtilities$BTN_W + chatUtilities$BTN_GAP;
        if (fullW <= shrink + 40) {
            return;
        }
        input.setWidth(fullW - shrink);
        ChatScreen self = (ChatScreen) (Object) this;
        chatUtilities$symX = input.getX() + input.getWidth() + chatUtilities$BTN_GAP;
        chatUtilities$symW = chatUtilities$BTN_W;
        chatUtilities$symH = Math.max(input.getHeight(), 12);
        chatUtilities$symY =
                self.height - chatUtilities$CHAT_BAR_H + (chatUtilities$CHAT_BAR_H - chatUtilities$symH) / 2;
    }

    @Inject(method = "removed", at = @At("TAIL"))
    private void chatUtilities$onRemoved(CallbackInfo ci) {
        chatUtilities$palette.setOpen(false);
        chatUtilities$symW = 0;
    }

    @Unique
    private boolean chatUtilities$symbolHit(double mx, double my) {
        return chatUtilities$symW > 0
                && mx >= chatUtilities$symX
                && mx < chatUtilities$symX + chatUtilities$symW
                && my >= chatUtilities$symY
                && my < chatUtilities$symY + chatUtilities$symH;
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
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        if (chatUtilities$symW > 0) {
            boolean hovered = chatUtilities$symbolHit(mouseX, mouseY);
            int x0 = chatUtilities$symX;
            int y0 = chatUtilities$symY;
            int x1 = x0 + chatUtilities$symW;
            int y1 = y0 + chatUtilities$symH;
            int bg = hovered ? 0xFF5A5A78 : 0xFF3C3C3C;
            graphics.fill(x0, y0, x1, y1, bg);
            graphics.renderOutline(x0, y0, chatUtilities$symW, chatUtilities$symH, 0xFF8E8E8E);
            Component face = Component.literal("\u263A");
            int tw = font.width(face);
            int th = font.lineHeight;
            graphics.drawString(
                    font,
                    face,
                    x0 + (chatUtilities$symW - tw) / 2,
                    y0 + (chatUtilities$symH - th) / 2,
                    0xFFFFFFFF,
                    false);
        }
        chatUtilities$palette.render(graphics, font, ((ChatScreen) (Object) this).width, ((ChatScreen) (Object) this).height, mouseX, mouseY);
    }
}
