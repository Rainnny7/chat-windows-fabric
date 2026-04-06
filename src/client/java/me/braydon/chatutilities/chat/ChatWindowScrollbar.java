package me.braydon.chatutilities.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public final class ChatWindowScrollbar {
    private static final int BAR_W = 1;

    private ChatWindowScrollbar() {}

    public static void render(
            GuiGraphics graphics,
            Minecraft mc,
            ChatWindow window,
            ChatWindowGeometry geo,
            float chatOpacity,
            boolean chatScreenOpen) {
        if (!chatScreenOpen || geo.maxHistoryScrollRows <= 0) {
            return;
        }
        int x = geo.x;
        int y = geo.y;
        int boxW = geo.boxW;
        int boxH = geo.boxH;
        int pad = ChatWindowGeometry.padding();
        int barX = x + boxW - pad - BAR_W;
        int trackY = y + pad;
        int trackH = boxH - 2 * pad;
        if (trackH < 4 || barX < x + pad) {
            return;
        }
        int[] m = ChatWindowGeometry.historyScrollMetrics(window, mc, mc.getWindow().getGuiScaledWidth());
        int total = m[0];
        int viewport = m[1];
        if (total <= viewport) {
            return;
        }
        float a = Mth.clamp(chatOpacity, 0f, 1f);
        int trackAlpha = Mth.clamp(Math.round(28 * a), 0, 255);
        int trackColor = (trackAlpha << 24) | 0xFFFFFF;
        graphics.fill(barX, trackY, barX + BAR_W, trackY + trackH, trackColor);
        int thumbAlpha = Mth.clamp(Math.round(85 * a), 0, 255);
        int thumbRgb = 0xB4B4B4;
        int thumbColor = (thumbAlpha << 24) | thumbRgb;
        int minThumbH = 4;
        int thumbH = Math.max(minThumbH, Mth.ceil(trackH * (viewport / (double) total)));
        thumbH = Math.min(thumbH, trackH);
        int maxTravel = trackH - thumbH;
        int maxScroll = geo.maxHistoryScrollRows;
        int scroll = Mth.clamp(window.getHistoryScrollRows(), 0, maxScroll);
        double frac = maxScroll > 0 ? scroll / (double) maxScroll : 0.0;
        int thumbY = trackY + (int) Math.round(maxTravel * (1.0 - frac));
        graphics.fill(barX, thumbY, barX + BAR_W, thumbY + thumbH, thumbColor);
    }
}
