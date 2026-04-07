package me.braydon.chatutilities.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * 1px track + thumb matching {@link me.braydon.chatutilities.chat.ChatWindowScrollbar} and the symbol palette grid.
 */
public final class ThinScrollbar {
    public static final int W = 1;

    private ThinScrollbar() {}

    /**
     * @param scrollPixels offset into content (0 = top)
     * @param contentHeight total scrollable content height in pixels
     * @param viewportHeight visible track height in pixels
     */
    public static void render(
            GuiGraphics g,
            int barX,
            int trackTop,
            int viewportHeight,
            int contentHeight,
            double scrollPixels,
            float opacity) {
        float op = Mth.clamp(opacity, 0f, 1f);
        int trackAlpha = Mth.clamp(Math.round(28 * op), 0, 255);
        int trackColor = (trackAlpha << 24) | 0xFFFFFF;
        g.fill(barX, trackTop, barX + W, trackTop + viewportHeight, trackColor);
        if (contentHeight <= viewportHeight) {
            return;
        }
        int thumbAlpha = Mth.clamp(Math.round(85 * op), 0, 255);
        int thumbColor = (thumbAlpha << 24) | 0xB4B4B4;
        int minThumbH = 4;
        int thumbH =
                Math.max(minThumbH, Mth.ceil(viewportHeight * (viewportHeight / (double) contentHeight)));
        thumbH = Math.min(thumbH, viewportHeight);
        int maxTravel = viewportHeight - thumbH;
        double maxScroll = contentHeight - viewportHeight;
        double t = maxScroll > 0 ? Mth.clamp(scrollPixels / maxScroll, 0, 1) : 0;
        int thumbY = trackTop + (int) Math.round(maxTravel * t);
        g.fill(barX, thumbY, barX + W, thumbY + thumbH, thumbColor);
    }
}
