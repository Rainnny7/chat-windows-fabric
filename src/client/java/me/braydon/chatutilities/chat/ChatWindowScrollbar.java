package me.braydon.chatutilities.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

public final class ChatWindowScrollbar {
    public static final int BAR_W = 1;
    private static final int HIT_W = 6;

    private ChatWindowScrollbar() {}

    /**
     * Scrollbar layout when visible; {@code null} when there is nothing to scroll.
     */
    public static final class ScrollbarMetrics {
        public final int barX;
        public final int hitLeft;
        public final int hitRight;
        public final int trackTop;
        public final int trackH;
        public final int thumbTop;
        public final int thumbH;
        public final int maxTravel;
        public final int maxScrollRows;

        private ScrollbarMetrics(
                int barX,
                int hitLeft,
                int hitRight,
                int trackTop,
                int trackH,
                int thumbTop,
                int thumbH,
                int maxTravel,
                int maxScrollRows) {
            this.barX = barX;
            this.hitLeft = hitLeft;
            this.hitRight = hitRight;
            this.trackTop = trackTop;
            this.trackH = trackH;
            this.thumbTop = thumbTop;
            this.thumbH = thumbH;
            this.maxTravel = maxTravel;
            this.maxScrollRows = maxScrollRows;
        }

        public boolean contains(int mx, int my) {
            return mx >= hitLeft && mx < hitRight && my >= trackTop && my < trackTop + trackH;
        }

        public boolean thumbContains(int my) {
            return my >= thumbTop && my < thumbTop + thumbH;
        }

        public int scrollRowsForTrackClick(int my) {
            int center = my - thumbH / 2;
            double frac = maxTravel > 0 ? (double) (center - trackTop) / maxTravel : 0;
            frac = Mth.clamp(frac, 0, 1);
            return Mth.clamp(
                    (int) Math.round(maxScrollRows * (1.0 - frac)), 0, maxScrollRows);
        }
    }

    public static ScrollbarMetrics metrics(
            Minecraft mc, ChatWindow window, ChatWindowGeometry geo, float chatOpacityIgnored) {
        if (geo.maxHistoryScrollRows <= 0) {
            return null;
        }
        int x = geo.x;
        int y = geo.y;
        int boxW = geo.boxW;
        int boxH = geo.boxH;
        int pad = ChatWindowGeometry.padding();
        int topInset = geo.contentTopInsetPx;
        int contentOff = geo.contentStartYOffset;
        int barX = x + boxW - pad - BAR_W;
        int trackY = y + pad + topInset + contentOff;
        int trackH = boxH - 2 * pad - topInset - contentOff;
        if (trackH < 4 || barX < x + pad) {
            return null;
        }
        int[] m = ChatWindowGeometry.historyScrollMetrics(window, mc, mc.getWindow().getGuiScaledWidth());
        int total = m[0];
        int viewport = m[1];
        if (total <= viewport) {
            return null;
        }
        int minThumbH = 4;
        int thumbH = Math.max(minThumbH, Mth.ceil(trackH * (viewport / (double) total)));
        thumbH = Math.min(thumbH, trackH);
        int maxTravel = trackH - thumbH;
        int maxScroll = geo.maxHistoryScrollRows;
        int scroll = Mth.clamp(window.getHistoryScrollRows(), 0, maxScroll);
        double frac = maxScroll > 0 ? scroll / (double) maxScroll : 0.0;
        int thumbY = trackY + (int) Math.round(maxTravel * (1.0 - frac));
        int hitLeft = barX + BAR_W - HIT_W;
        int hitRight = barX + BAR_W;
        return new ScrollbarMetrics(barX, hitLeft, hitRight, trackY, trackH, thumbY, thumbH, maxTravel, maxScroll);
    }

    public static void render(
            GuiGraphicsExtractor graphics,
            Minecraft mc,
            ChatWindow window,
            ChatWindowGeometry geo,
            float chatOpacity,
            boolean chatScreenOpen) {
        if (!chatScreenOpen) {
            return;
        }
        ScrollbarMetrics sm = metrics(mc, window, geo, chatOpacity);
        if (sm == null) {
            return;
        }
        float a = Mth.clamp(chatOpacity, 0f, 1f);
        int trackAlpha = Mth.clamp(Math.round(28 * a), 0, 255);
        int trackColor = (trackAlpha << 24) | 0xFFFFFF;
        graphics.fill(sm.barX, sm.trackTop, sm.barX + BAR_W, sm.trackTop + sm.trackH, trackColor);
        int thumbAlpha = Mth.clamp(Math.round(85 * a), 0, 255);
        int thumbRgb = 0xB4B4B4;
        int thumbColor = (thumbAlpha << 24) | thumbRgb;
        graphics.fill(sm.barX, sm.thumbTop, sm.barX + BAR_W, sm.thumbTop + sm.thumbH, thumbColor);
    }
}
