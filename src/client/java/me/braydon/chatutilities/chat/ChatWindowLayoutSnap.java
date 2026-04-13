package me.braydon.chatutilities.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Grid, screen-center, and sibling-window snapping while arranging chat windows in layout mode.
 */
public final class ChatWindowLayoutSnap {
    public static final int GRID_PX = 8;
    public static final int SNAP_PX = 8;

    private ChatWindowLayoutSnap() {}

    public static int snapPixelsToGrid(int v, int dim) {
        int s = Math.round(v / (float) GRID_PX) * GRID_PX;
        return Mth.clamp(s, 0, dim);
    }

    public static int snapPixelsToGridBottom(int v, int boxH, int gh) {
        int s = Math.round(v / (float) GRID_PX) * GRID_PX;
        return Mth.clamp(s, boxH, gh);
    }

    public static void snapWidthFracToGrid(ChatWindow w, int gw) {
        int wpx = Math.round(w.getWidthFrac() * gw);
        wpx = Math.max(Math.round(ChatWindow.MIN_WIDTH_FRAC * gw), snapPixelsToGrid(wpx, gw));
        w.setWidthFrac(wpx / (float) gw);
    }

    /** Snaps left edge and width together after dragging the left or bottom-left / top-left handles. */
    public static void snapLeftAndWidthToGrid(ChatWindow w, int gw) {
        int left = Math.round(w.getAnchorX() * gw);
        int wpx = Math.round(w.getWidthFrac() * gw);
        left = snapPixelsToGrid(left, gw);
        wpx = Math.max(Math.round(ChatWindow.MIN_WIDTH_FRAC * gw), snapPixelsToGrid(wpx, gw));
        left = Mth.clamp(left, 0, gw - wpx);
        w.setAnchorX(left / (float) gw);
        w.setWidthFrac(wpx / (float) gw);
    }

    public static float snapMoveAnchorX(
            ChatWindow w,
            float raw,
            Minecraft mc,
            int gw,
            int gh,
            List<ChatWindow> positioned,
            String excludeId) {
        Component ph = w.getLines().isEmpty() ? Component.literal("[empty]") : null;
        ChatWindowGeometry geo = ChatWindowGeometry.compute(w, mc, gw, gh, ph, false);
        int left = Math.round(raw * gw);
        int boxW = geo.boxW;
        return pickClosestLeft(left, boxW, gw, gh, positioned, excludeId, mc) / (float) gw;
    }

    public static float snapMoveAnchorY(
            ChatWindow w,
            float raw,
            Minecraft mc,
            int gw,
            int gh,
            List<ChatWindow> positioned,
            String excludeId) {
        Component ph = w.getLines().isEmpty() ? Component.literal("[empty]") : null;
        ChatWindowGeometry geo = ChatWindowGeometry.compute(w, mc, gw, gh, ph, false);
        int bottom = Math.round(raw * gh);
        int boxH = geo.boxH;
        return pickClosestBottom(bottom, boxH, gw, gh, positioned, excludeId, mc) / (float) gh;
    }

    private static int pickClosestLeft(
            int left,
            int boxW,
            int gw,
            int gh,
            List<ChatWindow> positioned,
            String excludeId,
            Minecraft mc) {
        List<Integer> cands = new ArrayList<>();
        cands.add(snapPixelsToGrid(left, gw));
        cands.add(gw / 2 - boxW / 2);
        for (ChatWindow o : positioned) {
            if (o.getId().equals(excludeId)) {
                continue;
            }
            Component ph = o.getLines().isEmpty() ? Component.literal("[empty]") : null;
            ChatWindowGeometry g = ChatWindowGeometry.compute(o, mc, gw, gh, ph, false);
            cands.add(g.x);
            cands.add(g.x + g.boxW);
        }
        return pickClosest(left, cands, v -> Mth.clamp(v, 0, gw - boxW));
    }

    private static int pickClosestBottom(
            int bottom,
            int boxH,
            int gw,
            int gh,
            List<ChatWindow> positioned,
            String excludeId,
            Minecraft mc) {
        List<Integer> cands = new ArrayList<>();
        cands.add(snapPixelsToGrid(bottom, gh));
        cands.add(boxH);
        int midY = gh / 2;
        cands.add(midY + boxH / 2);
        for (ChatWindow o : positioned) {
            if (o.getId().equals(excludeId)) {
                continue;
            }
            Component ph = o.getLines().isEmpty() ? Component.literal("[empty]") : null;
            ChatWindowGeometry g = ChatWindowGeometry.compute(o, mc, gw, gh, ph, false);
            cands.add(g.y + g.boxH);
            cands.add(g.y);
        }
        return pickClosest(bottom, cands, v -> Mth.clamp(v, boxH, gh));
    }

    private static int pickClosest(int raw, List<Integer> candidates, IntUnaryOperator clamp) {
        int best = raw;
        int bestDist = Integer.MAX_VALUE;
        for (int cand : candidates) {
            int c = clamp.applyAsInt(cand);
            int d = Math.abs(c - raw);
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return bestDist <= SNAP_PX ? best : raw;
    }

    private static int pickClosestInt(int raw, List<Integer> candidates, IntUnaryOperator clamp) {
        return pickClosest(raw, candidates, clamp);
    }

    /**
     * Snaps east-side resize width (px) to the 8px grid, the vanilla chat text column width, or another layout window’s
     * width. Records a vertical guide at the snapped right edge when a non-trivial snap applies.
     */
    public static int snapEastResizeWidthPx(
            int left,
            int rawWidthPx,
            int gw,
            List<ChatWindow> positioned,
            String excludeId,
            Minecraft mc,
            ChatUtilitiesManager mgr) {
        int minW = Math.round(ChatWindow.MIN_WIDTH_FRAC * gw);
        int w0 = Math.max(minW, rawWidthPx);
        List<Integer> cand = new ArrayList<>();
        cand.add(snapPixelsToGrid(w0, gw));
        cand.add(w0);
        int vanillaW =
                VanillaChatOverlayBounds.textColumnRightExcl(mc) - VanillaChatOverlayBounds.textColumnLeftIncl(mc);
        cand.add(Mth.clamp(vanillaW, minW, gw - left));
        for (ChatWindow o : positioned) {
            if (o.getId().equals(excludeId)) {
                continue;
            }
            Component ph = o.getLines().isEmpty() ? Component.literal("[empty]") : null;
            ChatWindowGeometry g = ChatWindowGeometry.compute(o, mc, gw, gh(mc), ph, false);
            cand.add(Mth.clamp(g.boxW, minW, gw - left));
        }
        int picked = pickClosestInt(w0, cand, w -> Mth.clamp(w, minW, gw - left));
        if (picked != w0) {
            mgr.noteLayoutSnapGuideVertical(left + picked);
        }
        return picked;
    }

    private static int gh(Minecraft mc) {
        return mc.getWindow().getGuiScaledHeight();
    }

    /**
     * Snaps west-side resize: adjusts {@code rawLeft} and {@code rawWidth} so the left edge aligns to grid / vanilla /
     * other windows when close, keeping the right edge fixed at {@code fixedRight}.
     */
    public static int snapWestResizeLeftPx(
            int rawLeft,
            int rawWidthPx,
            int fixedRight,
            int gw,
            List<ChatWindow> positioned,
            String excludeId,
            Minecraft mc,
            ChatUtilitiesManager mgr) {
        int minW = Math.round(ChatWindow.MIN_WIDTH_FRAC * gw);
        int left = Mth.clamp(rawLeft, 0, fixedRight - minW);
        int w0 = fixedRight - left;
        List<Integer> candLeft = new ArrayList<>();
        candLeft.add(snapPixelsToGrid(left, gw));
        candLeft.add(left);
        candLeft.add(VanillaChatOverlayBounds.textColumnLeftIncl(mc));
        candLeft.add(VanillaChatOverlayBounds.textColumnRightExcl(mc) - w0);
        for (ChatWindow o : positioned) {
            if (o.getId().equals(excludeId)) {
                continue;
            }
            Component ph = o.getLines().isEmpty() ? Component.literal("[empty]") : null;
            ChatWindowGeometry g = ChatWindowGeometry.compute(o, mc, gw, gh(mc), ph, false);
            candLeft.add(g.x);
            candLeft.add(g.x + g.boxW - w0);
        }
        int pickedLeft =
                pickClosestInt(
                        left,
                        candLeft,
                        l -> Mth.clamp(l, 0, fixedRight - minW));
        if (pickedLeft != left) {
            mgr.noteLayoutSnapGuideVertical(pickedLeft);
        }
        return pickedLeft;
    }

}
