package me.braydon.chatutilities.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

/** Filled rounded rectangles for the main Chat Utilities shell (span-based corners, no per-pixel effects). */
public final class RoundedPanelRenderer {

    private RoundedPanelRenderer() {}

    /** {@code floor(sqrt(n))} for small non-negative {@code n} (corner math only). */
    private static int isqrt(int n) {
        if (n <= 0) {
            return 0;
        }
        return (int) Math.sqrt(n);
    }

    public static void fillRoundedRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int radius, int color) {
        int r = Math.min(radius, Math.min(w, h) / 2);
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }
        g.fill(x + r, y, x + w - r, y + h, color);
        g.fill(x, y + r, x + w, y + h - r, color);
        int r2 = r * r;
        fillCornerTL(g, x, y, r, r2, color);
        fillCornerTR(g, x, y, w, r, r2, color);
        fillCornerBL(g, x, y, h, r, r2, color);
        fillCornerBR(g, x, y, w, h, r, r2, color);
    }

    /**
     * Draws {@code borderPx} of {@code borderArgb} <strong>outside</strong> {@code (x,y,w,h)}, then fills the interior
     * with {@code fillArgb} using the full {@code w×h} rounded rect (no inset — content/hit area matches {@code x,y,w,h}).
     */
    public static void fillRoundedRectOutsideBorder(
            GuiGraphicsExtractor g,
            int x,
            int y,
            int w,
            int h,
            int radius,
            int fillArgb,
            int borderArgb,
            int borderPx) {
        int bp = Mth.clamp(borderPx, 1, 8);
        if (w <= 0 || h <= 0) {
            return;
        }
        int r = Math.min(radius, Math.min(w, h) / 2);
        fillRoundedRect(g, x - bp, y - bp, w + 2 * bp, h + 2 * bp, r + bp, borderArgb);
        fillRoundedRect(g, x, y, w, h, r, fillArgb);
    }

    /**
     * Fills {@code color} in the intersection of an axis-aligned rectangle with a filled rounded rectangle
     * (same geometry as {@link #fillRoundedRect}).
     */
    public static void fillRectIntersectRounded(
            GuiGraphicsExtractor g, int rx, int ry, int rw, int rh, int rr, int ax, int ay, int aw, int ah, int color) {
        if (aw <= 0 || ah <= 0 || rw <= 0 || rh <= 0) {
            return;
        }
        int r = Math.min(rr, Math.min(rw, rh) / 2);
        int ax1 = ax + aw;
        int ay1 = ay + ah;
        int ry1 = ry + rh;
        int y0 = Math.max(ry, ay);
        int y1 = Math.min(ry1, ay1);
        if (y0 >= y1) {
            return;
        }
        int xClip0 = ax;
        int xClip1 = ax1;
        int r2 = r * r;
        int midTop = ry + r;
        int midBot = ry + rh - r;

        for (int py = y0; py < y1; py++) {
            if (py >= midTop && py < midBot) {
                int f0 = Math.max(rx, xClip0);
                int f1 = Math.min(rx + rw, xClip1);
                if (f0 < f1) {
                    g.fill(f0, py, f1, py + 1, color);
                }
            } else if (py < midTop) {
                fillRowTopBandClip(g, py, rx, ry, rw, r, r2, xClip0, xClip1, color);
            } else {
                fillRowBottomBandClip(g, py, rx, ry, rw, rh, r, r2, xClip0, xClip1, color);
            }
        }
    }

    private static void fillSpanClip(
            GuiGraphicsExtractor g, int py, int spanX0, int spanX1, int clipX0, int clipX1, int color) {
        int f0 = Math.max(spanX0, clipX0);
        int f1 = Math.min(spanX1, clipX1);
        if (f0 < f1) {
            g.fill(f0, py, f1, py + 1, color);
        }
    }

    private static void fillRowTopBandClip(
            GuiGraphicsExtractor g, int py, int x, int y, int w, int r, int r2, int c0, int c1, int color) {
        int cy = y + r;
        int dy = py - cy;
        int dy2 = dy * dy;
        if (dy2 > r2) {
            return;
        }
        int wHalf = isqrt(r2 - dy2);
        int cxL = x + r;
        int cxR = x + w - r;
        int tl0 = Math.max(x, cxL - wHalf);
        int tl1 = Math.min(x + r - 1, cxL) + 1;
        fillSpanClip(g, py, tl0, tl1, c0, c1, color);
        int cMid0 = x + r;
        int cMid1 = x + w - r;
        fillSpanClip(g, py, cMid0, cMid1, c0, c1, color);
        int tr0 = Math.max(x + w - r, cxR);
        int tr1 = Math.min(x + w - 1, cxR + wHalf) + 1;
        fillSpanClip(g, py, tr0, tr1, c0, c1, color);
    }

    private static void fillRowBottomBandClip(
            GuiGraphicsExtractor g, int py, int x, int y, int w, int h, int r, int r2, int c0, int c1, int color) {
        int cy = y + h - r;
        int dy = py - cy;
        int dy2 = dy * dy;
        if (dy2 > r2) {
            return;
        }
        int wHalf = isqrt(r2 - dy2);
        int cxL = x + r;
        int cxR = x + w - r;
        int bl0 = Math.max(x, cxL - wHalf);
        int bl1 = Math.min(x + r - 1, cxL) + 1;
        fillSpanClip(g, py, bl0, bl1, c0, c1, color);
        int cMid0 = x + r;
        int cMid1 = x + w - r;
        fillSpanClip(g, py, cMid0, cMid1, c0, c1, color);
        int br0 = Math.max(x + w - r, cxR);
        int br1 = Math.min(x + w - 1, cxR + wHalf) + 1;
        fillSpanClip(g, py, br0, br1, c0, c1, color);
    }

    private static void fillCornerTL(GuiGraphicsExtractor g, int x, int y, int r, int r2, int color) {
        int cx = x + r;
        int cy = y + r;
        for (int py = y; py < y + r; py++) {
            int dy = py - cy;
            int dy2 = dy * dy;
            if (dy2 > r2) {
                continue;
            }
            int wHalf = isqrt(r2 - dy2);
            int px0 = Math.max(x, cx - wHalf);
            int px1 = Math.min(x + r - 1, cx);
            if (px0 <= px1) {
                g.fill(px0, py, px1 + 1, py + 1, color);
            }
        }
    }

    private static void fillCornerTR(GuiGraphicsExtractor g, int x, int y, int w, int r, int r2, int color) {
        int x0 = x + w - r;
        int cx = x + w - r;
        int cy = y + r;
        for (int py = y; py < y + r; py++) {
            int dy = py - cy;
            int dy2 = dy * dy;
            if (dy2 > r2) {
                continue;
            }
            int wHalf = isqrt(r2 - dy2);
            int px0 = Math.max(x0, cx);
            int px1 = Math.min(x + w - 1, cx + wHalf);
            if (px0 <= px1) {
                g.fill(px0, py, px1 + 1, py + 1, color);
            }
        }
    }

    private static void fillCornerBL(GuiGraphicsExtractor g, int x, int y, int h, int r, int r2, int color) {
        int y0 = y + h - r;
        int cx = x + r;
        int cy = y + h - r;
        for (int py = y0; py < y + h; py++) {
            int dy = py - cy;
            int dy2 = dy * dy;
            if (dy2 > r2) {
                continue;
            }
            int wHalf = isqrt(r2 - dy2);
            int px0 = Math.max(x, cx - wHalf);
            int px1 = Math.min(x + r - 1, cx);
            if (px0 <= px1) {
                g.fill(px0, py, px1 + 1, py + 1, color);
            }
        }
    }

    private static void fillCornerBR(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int r2, int color) {
        int x0 = x + w - r;
        int y0 = y + h - r;
        int cx = x + w - r;
        int cy = y + h - r;
        for (int py = y0; py < y + h; py++) {
            int dy = py - cy;
            int dy2 = dy * dy;
            if (dy2 > r2) {
                continue;
            }
            int wHalf = isqrt(r2 - dy2);
            int px0 = Math.max(x0, cx);
            int px1 = Math.min(x + w - 1, cx + wHalf);
            if (px0 <= px1) {
                g.fill(px0, py, px1 + 1, py + 1, color);
            }
        }
    }
}
