package me.braydon.chatutilities.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/**
 * 1px track + thumb matching {@link me.braydon.chatutilities.chat.ChatWindowScrollbar} and the symbol palette grid.
 */
public final class ThinScrollbar {
    public static final int W = 1;
    /** Wider hit strip extending left from the visual track. */
    public static final int HIT_W = 6;

    private ThinScrollbar() {}

    public static final class Metrics {
        public final int thumbTop;
        public final int thumbH;
        public final int maxTravel;
        public final double maxScroll;

        private Metrics(int thumbTop, int thumbH, int maxTravel, double maxScroll) {
            this.thumbTop = thumbTop;
            this.thumbH = thumbH;
            this.maxTravel = maxTravel;
            this.maxScroll = maxScroll;
        }

        public static Metrics compute(
                int trackTop, int viewportHeight, int contentHeight, double scrollPixels) {
            if (contentHeight <= viewportHeight) {
                return null;
            }
            int minThumbH = 4;
            int thumbH =
                    Math.max(
                            minThumbH,
                            Mth.ceil(viewportHeight * (viewportHeight / (double) contentHeight)));
            thumbH = Math.min(thumbH, viewportHeight);
            int maxTravel = viewportHeight - thumbH;
            double maxScroll = contentHeight - viewportHeight;
            double t = maxScroll > 0 ? Mth.clamp(scrollPixels / maxScroll, 0, 1) : 0;
            int thumbTop = trackTop + (int) Math.round(maxTravel * t);
            return new Metrics(thumbTop, thumbH, maxTravel, maxScroll);
        }

        public boolean thumbContainsY(int my) {
            return my >= thumbTop && my < thumbTop + thumbH;
        }

        public int scrollPixelsForTrackClickY(int trackTop, int my) {
            int center = my - thumbH / 2;
            double t = maxTravel > 0 ? Mth.clamp((double) (center - trackTop) / maxTravel, 0, 1) : 0;
            return (int) Math.round(t * maxScroll);
        }
    }

    /**
     * @param scrollPixels offset into content (0 = top)
     * @param contentHeight total scrollable content height in pixels
     * @param viewportHeight visible track height in pixels
     */
    public static void render(
            GuiGraphicsExtractor g,
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
        Metrics m = Metrics.compute(trackTop, viewportHeight, contentHeight, scrollPixels);
        if (m == null) {
            return;
        }
        int thumbAlpha = Mth.clamp(Math.round(85 * op), 0, 255);
        int thumbColor = (thumbAlpha << 24) | 0xB4B4B4;
        g.fill(barX, m.thumbTop, barX + W, m.thumbTop + m.thumbH, thumbColor);
    }
}
