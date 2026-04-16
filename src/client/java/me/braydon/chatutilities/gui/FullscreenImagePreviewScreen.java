package me.braydon.chatutilities.gui;

import me.braydon.chatutilities.chat.ChatImagePreviewResources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Full-window preview of a remote image (same texture cache as hover). Esc closes.
 */
public final class FullscreenImagePreviewScreen extends Screen {

    private final String imageUrl;
    private final Screen parent;

    public FullscreenImagePreviewScreen(Screen parent, String imageUrl) {
        super(Component.translatable("chat-utilities.image_preview.fullscreen.title"));
        this.parent = parent;
        this.imageUrl = imageUrl;
    }

    @Override
    protected void init() {
        ChatImagePreviewResources.getOrStartLoading(imageUrl);
    }

    /**
     * Skip vanilla menu blur here so another mod (e.g. Blur+) cannot hit “only blur once per frame” when
     * {@code super.render} runs. Dimming is drawn in {@link #render} instead.
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0xD0101010);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        ChatImagePreviewResources.CachedTex e = ChatImagePreviewResources.getOrStartLoading(imageUrl);
        int sw = this.width;
        int sh = this.height;
        if (e.state() == ChatImagePreviewResources.State.READY && e.textureId() != null) {
            int iw = e.width();
            int ih = e.height();
            float scale = Math.min((float) (sw - 40) / iw, (float) (sh - 40) / ih);
            int dw = Mth.ceil(iw * scale);
            int dh = Mth.ceil(ih * scale);
            int lx = (sw - dw) / 2;
            int ly = (sh - dh) / 2;
            var pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(lx, ly);
            pose.scale(scale, scale);
            graphics.blit(
                    RenderPipelines.GUI_TEXTURED,
                    e.textureId(),
                    0,
                    0,
                    0f,
                    0f,
                    iw,
                    ih,
                    iw,
                    ih);
            pose.popMatrix();
        } else if (e.state() == ChatImagePreviewResources.State.LOADING) {
            graphics.centeredText(
                    this.font,
                    Component.translatable("chat-utilities.image_preview.loading"),
                    sw / 2,
                    sh / 2,
                    0xFFFFFFFF);
        } else {
            graphics.centeredText(
                    this.font,
                    Component.translatable("chat-utilities.image_preview.failed"),
                    sw / 2,
                    sh / 2,
                    0xFFFF6666);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }
}
