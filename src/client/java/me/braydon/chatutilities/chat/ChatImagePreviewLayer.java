package me.braydon.chatutilities.chat;

import com.mojang.blaze3d.platform.Window;
import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.Optional;

/** Drawn from {@link net.fabricmc.fabric.api.client.screen.v1.ScreenEvents#afterRender} on {@link net.minecraft.client.gui.screens.ChatScreen}. */
public final class ChatImagePreviewLayer {

    private static final int MAX_PREVIEW_W = 220;
    private static final int MAX_PREVIEW_H = 165;

    private ChatImagePreviewLayer() {}

    public static void render(GuiGraphicsExtractor graphics, Font font, int screenW, int screenH, int mouseX, int mouseY) {
        if (!ChatUtilitiesClientOptions.isImageChatPreviewEnabled()) {
            ChatImagePreviewState.setLastHoveredPreviewUrl(null);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Optional<String> urlOpt = ChatImagePreviewUrlResolver.findPreviewUrl(mc, mouseX, mouseY);
        if (urlOpt.isEmpty()) {
            ChatImagePreviewState.setLastHoveredPreviewUrl(null);
            return;
        }
        String url = urlOpt.get();
        ChatImagePreviewState.setLastHoveredPreviewUrl(url);

        ChatImagePreviewResources.CachedTex entry = ChatImagePreviewResources.getOrStartLoading(url);
        int tipLeft = mouseX + 12;
        int tipTop = mouseY - 8;
        if (tipLeft + MAX_PREVIEW_W > screenW - 4) {
            tipLeft = mouseX - MAX_PREVIEW_W - 12;
        }
        if (tipTop + MAX_PREVIEW_H > screenH - 4) {
            tipTop = screenH - MAX_PREVIEW_H - 4;
        }
        tipLeft = Mth.clamp(tipLeft, 4, screenW - 8);
        tipTop = Mth.clamp(tipTop, 4, screenH - 8);

        if (entry.state() == ChatImagePreviewResources.State.READY && entry.textureId() != null) {
            int iw = entry.width();
            int ih = entry.height();
            float scale = Math.min((float) MAX_PREVIEW_W / iw, (float) MAX_PREVIEW_H / ih);
            int dw = Mth.ceil(iw * scale);
            int dh = Mth.ceil(ih * scale);
            graphics.fill(tipLeft - 2, tipTop - 2, tipLeft + dw + 2, tipTop + dh + 2, 0xE0101010);
            graphics.outline(tipLeft - 2, tipTop - 2, dw + 4, dh + 4, 0xFFFFFFFF);
            var pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(tipLeft, tipTop);
            pose.scale(scale, scale);
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    entry.textureId(),
                    0,
                    0,
                    0f,
                    0f,
                    iw,
                    ih,
                    iw,
                    ih);
            pose.popMatrix();
            int hintY = tipTop + dh + 6;
            if (hintY + font.lineHeight <= screenH - 2) {
                graphics.text(
                        font,
                        Component.literal("Click to view in fullscreen")
                                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
                        tipLeft,
                        hintY,
                        0xFFAAAAAA,
                        false);
            }
        } else if (entry.state() == ChatImagePreviewResources.State.LOADING) {
            graphics.text(
                    font,
                    Component.translatable("chat-utilities.image_preview.loading"),
                    tipLeft,
                    tipTop,
                    0xFFE0E0E0,
                    false);
        }
    }

    public static void updateHoverStateFromMouse() {
        if (!ChatUtilitiesClientOptions.isImageChatPreviewEnabled()) {
            ChatImagePreviewState.setLastHoveredPreviewUrl(null);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen)) {
            return;
        }
        Window win = mc.getWindow();
        int mx = (int) mc.mouseHandler.getScaledXPos(win);
        int my = (int) mc.mouseHandler.getScaledYPos(win);
        ChatImagePreviewUrlResolver.findPreviewUrl(mc, mx, my)
                .ifPresentOrElse(ChatImagePreviewState::setLastHoveredPreviewUrl, () -> ChatImagePreviewState.setLastHoveredPreviewUrl(null));
    }
}
