package me.braydon.chatutilities.chat;

import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import me.braydon.chatutilities.client.ModAccentAnimator;
import me.braydon.chatutilities.gui.RoundedPanelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * External tab strip for multi-tab HUD chat windows when chat is open. Picks top, bottom, left, or right by free
 * space; horizontal strips are left-aligned and only as wide as labels need.
 */
public final class ChatWindowHudTabStrip {
    private static final int MARGIN = 2;
    private static final int H_STRIP = 10;
    private static final int V_STRIP_W = 50;
    private static final int TAB_GAP = 1;
    private static final int TAB_PAD_H = 2;
    private static final int MIN_TAB_W = 22;
    private static final int BADGE_GAP = 4;
    private static final int BADGE_RADIUS = 4;
    private static final int BADGE_H = 8;

    /** Height of the horizontal tab strip (matches {@link #H_STRIP}). */
    public static int stripHeight() {
        return H_STRIP;
    }

    public enum Edge {
        TOP,
        BOTTOM,
        RIGHT,
        LEFT
    }

    public record Placement(Edge edge, int sx, int sy, int sw, int sh) {}

    private ChatWindowHudTabStrip() {}

    // ── Drag-to-reorder state (chat open only) ────────────────────────────────

    private static @org.jspecify.annotations.Nullable String dragWindowId;
    private static @org.jspecify.annotations.Nullable String dragTabId;
    private static int dragFromIndex = -1;
    private static int dragHoverIndex = -1;
    private static boolean dragDidMove;

    public static void clearDrag() {
        dragWindowId = null;
        dragTabId = null;
        dragFromIndex = -1;
        dragHoverIndex = -1;
        dragDidMove = false;
    }

    public static Placement resolve(ChatWindowGeometry geo, int gw, int gh, Minecraft mc, ChatWindow window) {
        return resolve(geo, gw, gh, mc, window, List.of());
    }

    /** Like {@link #resolve(ChatWindowGeometry, int, int, Minecraft, ChatWindow)} but avoids overlapping other windows. */
    public static Placement resolve(
            ChatWindowGeometry geo,
            int gw,
            int gh,
            Minecraft mc,
            ChatWindow window,
            List<int[]> occupiedRects) {
        int x = geo.x;
        int y = geo.y;
        int w = geo.boxW;
        int h = geo.boxH;
        Font font = mc.font;
        int n = window.getTabCount();
        if (n <= 1) {
            return new Placement(Edge.TOP, x, y - H_STRIP, w, H_STRIP);
        }

        // User expectation: tabs should stay on TOP unless that exact top strip region is unavailable.
        // Only fall back to BOTTOM when TOP truly cannot be used (off-screen or overlapping another window).
        int sw = Math.min(w, gw - x - MARGIN);
        sw = Mth.clamp(sw, MIN_TAB_W, Math.min(w, gw - x - MARGIN));
        int sh = horizontalStripHeight(font, window, sw);

        if (fitsTop(y, sh)) {
            Placement pTop = new Placement(Edge.TOP, x, y - sh, sw, sh);
            if (!intersectsAny(pTop, occupiedRects)) {
                return pTop;
            }
        }
        if (fitsBottom(y, h, gh, sh)) {
            Placement pBottom = new Placement(Edge.BOTTOM, x, y + h, sw, sh);
            if (!intersectsAny(pBottom, occupiedRects)) {
                return pBottom;
            }
        }
        int sy = Math.max(MARGIN, y - sh);
        return new Placement(Edge.TOP, x, sy, sw, sh);
    }

    private static boolean intersectsAny(Placement p, List<int[]> occupiedRects) {
        if (occupiedRects == null || occupiedRects.isEmpty()) {
            return false;
        }
        int l = p.sx();
        int t = p.sy();
        int r = p.sx() + p.sw();
        int b = p.sy() + p.sh();
        for (int[] rc : occupiedRects) {
            if (rc == null || rc.length < 4) {
                continue;
            }
            int ol = rc[0], ot = rc[1], or = rc[2], ob = rc[3];
            boolean hit = l < or && r > ol && t < ob && b > ot;
            if (hit) {
                return true;
            }
        }
        return false;
    }

    private static boolean fitsTop(int y, int sh) {
        return y >= sh + MARGIN;
    }

    private static boolean fitsBottom(int y, int h, int gh, int sh) {
        return y + h + sh + MARGIN <= gh;
    }

    private static boolean fitsRight(int x, int w, int gw) {
        return x + w + V_STRIP_W + MARGIN <= gw;
    }

    private static boolean fitsLeft(int x) {
        return x >= V_STRIP_W + MARGIN;
    }

    private static int horizontalStripWidth(Font font, ChatWindow window, int maxOuterW) {
        List<ChatWindowTab> tabs = window.getTabs();
        int n = tabs.size();
        if (n <= 0) {
            return TAB_PAD_H * 2;
        }
        int innerBudget = Math.max(MIN_TAB_W, maxOuterW - TAB_PAD_H * 2 - (n - 1) * TAB_GAP);
        int[] seg = segmentWidthsHorizontal(font, tabs, innerBudget);
        int sum = TAB_PAD_H * 2;
        for (int s : seg) {
            sum += s + TAB_GAP;
        }
        return sum - TAB_GAP;
    }

    private static int horizontalStripHeight(Font font, ChatWindow window, int outerW) {
        List<ChatWindowTab> tabs = window.getTabs();
        if (tabs.size() <= 1) {
            return H_STRIP;
        }
        int innerW = Math.max(MIN_TAB_W, outerW - TAB_PAD_H * 2);
        int x = 0;
        int rows = 1;
        for (ChatWindowTab t : tabs) {
            int segW = desiredHorizontalSegmentWidth(font, t, innerW);
            if (x > 0 && x + segW > innerW) {
                rows++;
                x = 0;
            }
            x += segW + TAB_GAP;
        }
        return rows * H_STRIP + (rows - 1) * TAB_GAP;
    }

    private static int horizontalStripHeight(Font font, List<ChatWindowTab> tabs, int outerW) {
        if (tabs == null || tabs.size() <= 1) {
            return H_STRIP;
        }
        int innerW = Math.max(MIN_TAB_W, outerW - TAB_PAD_H * 2);
        int x = 0;
        int rows = 1;
        for (ChatWindowTab t : tabs) {
            int segW = desiredHorizontalSegmentWidth(font, t, innerW);
            if (x > 0 && x + segW > innerW) {
                rows++;
                x = 0;
            }
            x += segW + TAB_GAP;
        }
        return rows * H_STRIP + (rows - 1) * TAB_GAP;
    }

    private static int desiredHorizontalSegmentWidth(Font font, ChatWindowTab tab, int innerBudget) {
        String label = tab.getDisplayName() == null ? "" : tab.getDisplayName();
        UnreadBadgeSpec badge = unreadBadgeSpec(font, tab.getUnreadCount());
        int reserve = badge == null ? 0 : badge.w + BADGE_GAP;
        int want = font.width(label) + 6 + reserve;
        return Mth.clamp(want, MIN_TAB_W, innerBudget);
    }

    private record HRect(int idx, int x, int y, int w, int h) {}

    private static List<HRect> horizontalWrappedRects(Font font, List<ChatWindowTab> tabs, Placement p) {
        int innerW = Math.max(MIN_TAB_W, p.sw() - TAB_PAD_H * 2);
        int x0 = p.sx() + TAB_PAD_H;
        int y0 = p.sy();
        int x = 0;
        int y = 0;
        List<HRect> out = new ArrayList<>();
        for (int i = 0; i < tabs.size(); i++) {
            ChatWindowTab tab = tabs.get(i);
            int segW = desiredHorizontalSegmentWidth(font, tab, innerW);
            if (x > 0 && x + segW > innerW) {
                x = 0;
                y += H_STRIP + TAB_GAP;
            }
            out.add(new HRect(i, x0 + x, y0 + y, segW, H_STRIP));
            x += segW + TAB_GAP;
        }
        return out;
    }

    private static int verticalStripWidth(Font font, ChatWindow window, int maxInnerH) {
        List<ChatWindowTab> tabs = window.getTabs();
        int m = 0;
        for (ChatWindowTab t : tabs) {
            m = Math.max(m, font.width(truncate(font, t.getDisplayName(), V_STRIP_W - 6)));
        }
        return Mth.clamp(m + 6, MIN_TAB_W, V_STRIP_W);
    }

    private static int[] segmentWidthsHorizontal(Font font, List<ChatWindowTab> tabs, int innerBudget) {
        int n = tabs.size();
        int[] want = new int[n];
        int sumWant = 0;
        for (int i = 0; i < n; i++) {
            ChatWindowTab t = tabs.get(i);
            String label = t.getDisplayName() == null ? "" : t.getDisplayName();
            UnreadBadgeSpec badge = unreadBadgeSpec(font, t.getUnreadCount());
            int reserve = badge == null ? 0 : badge.w + BADGE_GAP;
            want[i] = Mth.clamp(font.width(label) + 6 + reserve, MIN_TAB_W, innerBudget);
            sumWant += want[i];
        }
        int gaps = (n - 1) * TAB_GAP;
        if (sumWant + gaps <= innerBudget) {
            return want;
        }
        float scale = (innerBudget - gaps) / (float) sumWant;
        int[] out = new int[n];
        int rem = innerBudget - gaps;
        for (int i = 0; i < n; i++) {
            out[i] = Math.max(MIN_TAB_W, (int) (want[i] * scale));
            rem -= out[i];
        }
        for (int i = 0; i < n && rem != 0; i++) {
            int d = Mth.sign(rem);
            if (out[i] + d >= MIN_TAB_W && out[i] + d <= innerBudget) {
                out[i] += d;
                rem -= d;
            }
        }
        return out;
    }

    public static void render(
            GuiGraphicsExtractor g,
            Minecraft mc,
            ChatWindow window,
            Placement p,
            float chatOpacity,
            int mouseX,
            int mouseY) {
        if (window.getTabCount() <= 1) {
            return;
        }
        List<ChatWindowTab> tabs = window.getTabs();
        Font font = mc.font;
        float textBg = mc.options.textBackgroundOpacity().get().floatValue();
        float panelM =
                ChatUtilitiesClientOptions.getChatPanelBackgroundOpacityMultiplier(
                        mc.screen instanceof ChatScreen);
        float stripA = Mth.clamp(textBg * chatOpacity * panelM, 0f, 1f);

        int sel = window.getSelectedTabIndex();
        ChatWindowTab selectedTab =
                (sel >= 0 && sel < tabs.size()) ? tabs.get(sel) : null;

        // While dragging, render a live preview of the new tab order.
        List<ChatWindowTab> renderTabs = tabs;
        if (dragWindowId != null
                && dragWindowId.equals(window.getId())
                && dragTabId != null
                && dragFromIndex >= 0
                && dragHoverIndex >= 0
                && dragFromIndex < tabs.size()
                && dragHoverIndex < tabs.size()
                && dragFromIndex != dragHoverIndex) {
            ArrayList<ChatWindowTab> rt = new ArrayList<>(tabs);
            int from = Mth.clamp(dragFromIndex, 0, rt.size() - 1);
            int to = Mth.clamp(dragHoverIndex, 0, rt.size() - 1);
            ChatWindowTab moved = rt.remove(from);
            int insertAt = to;
            if (from < to) {
                insertAt = Math.max(0, to - 1);
            }
            insertAt = Mth.clamp(insertAt, 0, rt.size());
            rt.add(insertAt, moved);
            renderTabs = rt;
        }

        int n = renderTabs.size();
        int selRender = selectedTab != null ? renderTabs.indexOf(selectedTab) : sel;
        int accent = ModAccentAnimator.currentArgb();
        int accentA = Mth.clamp(Math.round(((accent >>> 24) & 0xFF) * stripA), 0, 255);
        int accentRgb = accent & 0xFFFFFF;
        int accentStrip = (accentA << 24) | accentRgb;
        switch (p.edge()) {
            case TOP, BOTTOM -> {
                List<HRect> rects = horizontalWrappedRects(font, renderTabs, p);
                for (HRect rc : rects) {
                    int i = rc.idx();
                    int lx = rc.x();
                    int ly = rc.y();
                    int rw = rc.w();
                    int rh = rc.h();
                    boolean hover = mouseX >= lx && mouseX < lx + rw && mouseY >= ly && mouseY < ly + rh;
                    int face =
                            i == selRender
                                    ? packRgba(0x42, 0x42, 0x48, stripA)
                                    : hover
                                            ? packRgba(0x32, 0x32, 0x38, stripA)
                                            : packRgba(0x22, 0x22, 0x28, stripA);
                    int edge =
                            i == selRender
                                    ? packRgba(0xA0, 0xA0, 0xA8, stripA)
                                    : packRgba(0x55, 0x55, 0x5A, stripA);
                    g.fill(lx, ly, lx + rw, ly + rh, face);
                    g.outline(lx, ly, rw, rh, edge);

                    int unreadCount = i != selRender ? renderTabs.get(i).getUnreadCount() : 0;
                    UnreadBadgeSpec badge = unreadBadgeSpec(font, unreadCount);
                    int reserve = badge == null ? 0 : badge.w + BADGE_GAP;
                    String label = truncate(font, renderTabs.get(i).getDisplayName(), Math.max(0, rw - 4 - reserve));
                    int tw = font.width(label);
                    int tc = i == selRender ? 0xFFFFFFFF : 0xFFE0E0E8;
                    int labelX = lx + 3;
                    int labelY = ly + (rh - 8) / 2;
                    g.text(font, label, labelX, labelY, tc, false);
                    if (badge != null) {
                        int bx = labelX + tw + BADGE_GAP;
                        int rightLimit = lx + rw - 2;
                        bx = Math.min(bx, rightLimit - badge.w);
                        bx = Math.max(bx, lx + 2);
                        int by = ly + (rh - badge.h) / 2;
                        // Keep the rounded badge fully inside the tab rect (avoid bleed above/below at some GUI scales).
                        by = Mth.clamp(by, ly + 1, (ly + rh) - badge.h - 1);
                        renderUnreadBadgeAt(g, font, bx, by, badge);
                    }

                    if (i == selRender && accentA > 0) {
                        int ax0 = lx + 1;
                        int ax1 = lx + rw - 1;
                        int ay = p.edge() == Edge.TOP ? (ly + rh - 2) : (ly + 1);
                        g.fill(ax0, ay, ax1, ay + 1, accentStrip);
                    }
                }
            }
            case RIGHT, LEFT -> {
                int segH = Math.max(1, (p.sh() - 4) / n);
                for (int i = 0; i < n; i++) {
                    int ly = p.sy() + 2 + i * segH;
                    int rh = (i == n - 1) ? p.sy() + p.sh() - 2 - ly : segH;
                    boolean hover =
                            mouseX >= p.sx()
                                    && mouseX < p.sx() + p.sw()
                                    && mouseY >= ly
                                    && mouseY < ly + rh;
                    int face =
                            i == selRender
                                    ? packRgba(0x42, 0x42, 0x48, stripA)
                                    : hover
                                            ? packRgba(0x32, 0x32, 0x38, stripA)
                                            : packRgba(0x22, 0x22, 0x28, stripA);
                    int edge =
                            i == selRender
                                    ? packRgba(0xA0, 0xA0, 0xA8, stripA)
                                    : packRgba(0x55, 0x55, 0x5A, stripA);
                    g.fill(p.sx(), ly, p.sx() + p.sw(), ly + rh, face);
                    g.outline(p.sx(), ly, p.sw(), rh, edge);

                    int unreadCount = i != selRender ? renderTabs.get(i).getUnreadCount() : 0;
                    UnreadBadgeSpec badge = unreadBadgeSpec(font, unreadCount);
                    int reserve = badge == null ? 0 : badge.w + BADGE_GAP;
                    String label = truncate(font, renderTabs.get(i).getDisplayName(), Math.max(0, p.sw() - 6 - reserve));
                    int tw = font.width(label);
                    int tc = i == selRender ? 0xFFFFFFFF : 0xFFE0E0E8;
                    int labelX = p.sx() + 3;
                    int labelY = ly + (rh - 8) / 2;
                    g.text(font, label, labelX, labelY, tc, false);
                    if (badge != null) {
                        int bx = labelX + tw + BADGE_GAP;
                        int rightLimit = p.sx() + p.sw() - 2;
                        bx = Math.min(bx, rightLimit - badge.w);
                        bx = Math.max(bx, p.sx() + 2);
                        int by = ly + (rh - badge.h) / 2;
                        by = Mth.clamp(by, ly + 1, (ly + rh) - badge.h - 1);
                        renderUnreadBadgeAt(g, font, bx, by, badge);
                    }

                    if (i == selRender && accentA > 0) {
                        int ay0 = ly + 1;
                        int ay1 = ly + rh - 1;
                        int ax = p.edge() == Edge.LEFT ? (p.sx() + p.sw() - 2) : (p.sx() + 1);
                        g.fill(ax, ay0, ax + 1, ay1, accentStrip);
                    }
                }
            }
        }
    }

    public static Placement resolveUnreadOnly(
            ChatWindowGeometry geo, int gw, int gh, Minecraft mc, ChatWindow window, List<ChatWindowTab> unreadTabs) {
        int x = geo.x;
        int y = geo.y;
        int w = geo.boxW;
        int h = geo.boxH;
        int sw = Math.min(w, gw - x - MARGIN);
        sw = Mth.clamp(sw, MIN_TAB_W, Math.min(w, gw - x - MARGIN));

        // Keep the unread-only strip anchored to the same edge the full tab strip would choose.
        Placement base = resolve(geo, gw, gh, mc, window);
        Edge edge = (base.edge() == Edge.TOP || base.edge() == Edge.BOTTOM) ? base.edge() : Edge.TOP;

        int sh = horizontalStripHeight(mc.font, unreadTabs, sw);
        if (edge == Edge.TOP && fitsTop(y, sh)) {
            return new Placement(Edge.TOP, x, y - sh, sw, sh);
        }
        if (edge == Edge.BOTTOM && fitsBottom(y, h, gh, sh)) {
            return new Placement(Edge.BOTTOM, x, y + h, sw, sh);
        }
        // Fallback to the other edge if the preferred one doesn't fit.
        if (fitsTop(y, sh)) {
            return new Placement(Edge.TOP, x, y - sh, sw, sh);
        }
        if (fitsBottom(y, h, gh, sh)) {
            return new Placement(Edge.BOTTOM, x, y + h, sw, sh);
        }
        int sy = Math.max(MARGIN, y - sh);
        return new Placement(Edge.TOP, x, sy, sw, sh);
    }

    public static void renderUnreadOnly(GuiGraphicsExtractor g, Minecraft mc, List<ChatWindowTab> unreadTabs, Placement p, float chatOpacity) {
        if (unreadTabs == null || unreadTabs.isEmpty()) {
            return;
        }
        if (p.edge() != Edge.TOP && p.edge() != Edge.BOTTOM) {
            return;
        }
        Font font = mc.font;
        float textBg = mc.options.textBackgroundOpacity().get().floatValue();
        float panelM = ChatUtilitiesClientOptions.getChatPanelBackgroundOpacityMultiplier(false);
        float stripA = Mth.clamp(textBg * chatOpacity * panelM, 0f, 1f);

        List<HRect> rects = horizontalWrappedRects(font, unreadTabs, p);
        for (HRect rc : rects) {
            ChatWindowTab tab = unreadTabs.get(rc.idx());
            int lx = rc.x();
            int ly = rc.y();
            int rw = rc.w();
            int rh = rc.h();
            g.fill(lx, ly, lx + rw, ly + rh, packRgba(0x22, 0x22, 0x28, stripA));
            g.outline(lx, ly, rw, rh, packRgba(0x55, 0x55, 0x5A, stripA));

            int unreadCount = tab.getUnreadCount();
            UnreadBadgeSpec badge =
                    unreadCount > 0 && ChatUtilitiesClientOptions.isChatWindowTabUnreadBadgesEnabled()
                            ? unreadBadgeSpecForce(font, unreadCount)
                            : null;
            int reserve = badge == null ? 0 : badge.w + BADGE_GAP;
            String label = truncate(font, tab.getDisplayName(), Math.max(0, rw - 4 - reserve));
            int tw = font.width(label);
            int labelX = lx + 3;
            int labelY = ly + (rh - 8) / 2;
            g.text(font, label, labelX, labelY, 0xFFE0E0E8, false);
            if (badge != null) {
                int bx = labelX + tw + BADGE_GAP;
                int rightLimit = lx + rw - 2;
                bx = Math.min(bx, rightLimit - badge.w);
                bx = Math.max(bx, lx + 2);
                int by = ly + (rh - badge.h) / 2;
                by = Mth.clamp(by, ly + 1, (ly + rh) - badge.h - 1);
                renderUnreadBadgeAt(g, font, bx, by, badge);
            }
        }
    }

    private record UnreadBadgeSpec(String text, int w, int h) {}

    private static UnreadBadgeSpec unreadBadgeSpec(Font font, int unreadCount) {
        if (!ChatUtilitiesClientOptions.isChatWindowTabUnreadBadgesEnabled() || unreadCount <= 0) {
            return null;
        }
        return unreadBadgeSpecInner(font, unreadCount);
    }

    private static UnreadBadgeSpec unreadBadgeSpecForce(Font font, int unreadCount) {
        if (unreadCount <= 0) {
            return null;
        }
        return unreadBadgeSpecInner(font, unreadCount);
    }

    private static final float BADGE_TEXT_SCALE = 0.72f;

    private static UnreadBadgeSpec unreadBadgeSpecInner(Font font, int unreadCount) {
        String text = unreadCount > 999 ? "1k+" : Integer.toString(unreadCount);
        int tw = Math.round(font.width(text) * BADGE_TEXT_SCALE);
        int innerPad = text.length() >= 3 ? 3 : text.length() == 2 ? 4 : 5;
        int w = Math.max(11, tw + innerPad);
        int h = BADGE_H;
        return new UnreadBadgeSpec(text, w, h);
    }

    private static void renderUnreadBadgeAt(GuiGraphicsExtractor g, Font font, int bx, int by, UnreadBadgeSpec badge) {
        if (badge == null) {
            return;
        }
        int rgb = ChatUtilitiesClientOptions.getTabUnreadBadgeColorRgb() & 0xFFFFFF;
        int fill = 0xFF000000 | rgb;
        if (ChatUtilitiesClientOptions.getTabUnreadBadgeStyle() == ChatUtilitiesClientOptions.TabUnreadBadgeStyle.HEART) {
            fillHeartBadgeBackground(g, bx, by, badge.w, badge.h, fill);
        } else {
            RoundedPanelRenderer.fillRoundedRect(g, bx, by, badge.w, badge.h, BADGE_RADIUS, fill);
        }
        float cx = bx + badge.w * 0.5f;
        float cy = by + badge.h * 0.5f;
        var pose = g.pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.scale(BADGE_TEXT_SCALE, BADGE_TEXT_SCALE);
        int tw = font.width(badge.text);
        int th = font.lineHeight;
        g.text(font, badge.text, -tw / 2, -th / 2, 0xFFFFFFFF, false);
        pose.popMatrix();
    }

    /** Filled heart shape inside {@code [x,y,w,h]} (scanline silhouette). */
    private static void fillHeartBadgeBackground(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int cx = x + w / 2;
        for (int row = 0; row < h; row++) {
            float ny = (row + 0.5f) / h;
            float halfNorm =
                    ny < 0.32f
                            ? 0.42f + 0.22f * Mth.sin(ny / 0.32f * Mth.PI)
                            : 0.64f * (1f - (ny - 0.32f) / 0.68f);
            int half = Math.max(1, Math.round(0.5f * w * halfNorm));
            int yy = y + row;
            g.fill(cx - half, yy, cx + half, yy + 1, color);
        }
    }

    private static int packRgba(int r, int g, int b, float a) {
        int ai = Mth.clamp(Math.round(a * 255f), 0, 255);
        return (ai << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF);
    }

    private static String truncate(Font font, String s, int maxPx) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        if (font.width(s) <= maxPx) {
            return s;
        }
        String ell = "...";
        int budget = maxPx - font.width(ell);
        if (budget <= 0) {
            return ell;
        }
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            String next = b.toString() + s.charAt(i);
            if (font.width(next) > budget) {
                break;
            }
            b.append(s.charAt(i));
        }
        return b + ell;
    }

    public static boolean tryHandleClick(Minecraft mc, double mouseX, double mouseY, int button) {
        if (button != 0 || mc.mouseHandler.isMouseGrabbed()) {
            return false;
        }
        if (!(mc.screen instanceof ChatScreen)) {
            return false;
        }
        if (ChatUtilitiesManager.get().isPositioning()) {
            return false;
        }
        int mx = (int) mouseX;
        int my = (int) mouseY;
        int gw = mc.getWindow().getGuiScaledWidth();
        int gh = mc.getWindow().getGuiScaledHeight();
        int guiTick = mc.gui.getGuiTicks();

        ChatUtilitiesManager mgr = ChatUtilitiesManager.get();
        List<ChatWindow> ordered = new ArrayList<>(mgr.getHudChatWindows());
        for (int i = ordered.size() - 1; i >= 0; i--) {
            ChatWindow window = ordered.get(i);
            if (!window.isVisible() || window.getTabCount() <= 1) {
                continue;
            }
            boolean layoutChrome = mgr.showsLayoutChrome(window);
            ChatWindowGeometry geo =
                    ChatWindowGeometry.compute(
                            window,
                            mc,
                            gw,
                            gh,
                            layoutChrome ? Component.literal("[empty]") : null,
                            guiTick,
                            layoutChrome,
                            true,
                            mx,
                            my,
                            false,
                            layoutChrome);
            List<int[]> occupied = new ArrayList<>();
            for (ChatWindow o : ordered) {
                if (o == window || !o.isVisible()) {
                    continue;
                }
                boolean oChrome = mgr.showsLayoutChrome(o);
                ChatWindowGeometry og =
                        ChatWindowGeometry.compute(
                                o,
                                mc,
                                gw,
                                gh,
                                oChrome ? Component.literal("[empty]") : null,
                                guiTick,
                                oChrome,
                                true,
                                mx,
                                my,
                                false,
                                oChrome);
                occupied.add(new int[] {og.x, og.y, og.x + og.boxW, og.y + og.boxH});
            }
            Placement p = resolve(geo, gw, gh, mc, window, occupied);
            if (mx < p.sx() || mx >= p.sx() + p.sw() || my < p.sy() || my >= p.sy() + p.sh()) {
                continue;
            }
            int idx = tabIndexAt(mc, p, mx, my, window);
            if (idx >= 0) {
                dragWindowId = window.getId();
                dragFromIndex = idx;
                ChatWindowTab tab = idx >= 0 && idx < window.getTabCount() ? window.getTabs().get(idx) : null;
                dragTabId = tab != null ? tab.getId() : null;
                dragHoverIndex = idx;
                dragDidMove = false;
                int prev = window.getSelectedTabIndex();
                if (idx != prev) {
                    window.setSelectedTabIndex(idx);
                    mc.getSoundManager()
                            .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
                }
                return true;
            }
        }
        return false;
    }

    public static boolean tryHandleDrag(Minecraft mc, double mouseX, double mouseY, int button) {
        if (button != 0 || mc.mouseHandler.isMouseGrabbed()) {
            return false;
        }
        if (!(mc.screen instanceof ChatScreen)) {
            return false;
        }
        if (ChatUtilitiesManager.get().isPositioning()) {
            return false;
        }
        if (dragWindowId == null || dragTabId == null || dragFromIndex < 0) {
            return false;
        }
        int mx = (int) mouseX;
        int my = (int) mouseY;
        int gw = mc.getWindow().getGuiScaledWidth();
        int gh = mc.getWindow().getGuiScaledHeight();
        int guiTick = mc.gui.getGuiTicks();

        ChatUtilitiesManager mgr = ChatUtilitiesManager.get();
        List<ChatWindow> ordered = new ArrayList<>(mgr.getHudChatWindows());
        for (int i = ordered.size() - 1; i >= 0; i--) {
            ChatWindow window = ordered.get(i);
            if (!window.isVisible() || window.getTabCount() <= 1) {
                continue;
            }
            if (!window.getId().equals(dragWindowId)) {
                continue;
            }
            boolean layoutChrome = mgr.showsLayoutChrome(window);
            ChatWindowGeometry geo =
                    ChatWindowGeometry.compute(
                            window,
                            mc,
                            gw,
                            gh,
                            layoutChrome ? Component.literal("[empty]") : null,
                            guiTick,
                            layoutChrome,
                            true,
                            mx,
                            my,
                            false,
                            layoutChrome);
            List<int[]> occupied = new ArrayList<>();
            for (ChatWindow o : ordered) {
                if (o == window || !o.isVisible()) {
                    continue;
                }
                boolean oChrome = mgr.showsLayoutChrome(o);
                ChatWindowGeometry og =
                        ChatWindowGeometry.compute(
                                o,
                                mc,
                                gw,
                                gh,
                                oChrome ? Component.literal("[empty]") : null,
                                guiTick,
                                oChrome,
                                true,
                                mx,
                                my,
                                false,
                                oChrome);
                occupied.add(new int[] {og.x, og.y, og.x + og.boxW, og.y + og.boxH});
            }
            Placement p = resolve(geo, gw, gh, mc, window, occupied);
            if (mx < p.sx() || mx >= p.sx() + p.sw() || my < p.sy() || my >= p.sy() + p.sh()) {
                dragHoverIndex = -1;
                return true;
            }
            int idx = tabIndexAt(mc, p, mx, my, window);
            dragHoverIndex = idx;
            if (idx >= 0 && dragFromIndex >= 0 && idx != dragFromIndex) {
                boolean moved = window.moveTab(dragFromIndex, idx);
                if (moved) {
                    dragDidMove = true;
                    // Keep dragging the same tab after the list mutates.
                    int now = -1;
                    if (dragTabId != null) {
                        for (int ti = 0; ti < window.getTabs().size(); ti++) {
                            ChatWindowTab t = window.getTabs().get(ti);
                            if (t != null && dragTabId.equals(t.getId())) {
                                now = ti;
                                break;
                            }
                        }
                    }
                    dragFromIndex = now >= 0 ? now : idx;
                    dragHoverIndex = dragFromIndex;
                }
            }
            return true;
        }
        return false;
    }

    public static boolean tryHandleRelease(Minecraft mc, double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        if (dragWindowId == null || dragTabId == null || dragFromIndex < 0) {
            return false;
        }
        try {
            ChatUtilitiesManager mgr = ChatUtilitiesManager.get();
            for (ChatWindow w : mgr.getHudChatWindows()) {
                if (w == null || !w.getId().equals(dragWindowId) || w.getTabCount() <= 1) {
                    continue;
                }
                if (dragDidMove) {
                    mgr.save();
                    return true;
                }
                int from = dragFromIndex;
                int to = dragHoverIndex;
                if (to >= 0 && from >= 0 && to != from) {
                    boolean moved = w.moveTab(from, to);
                    if (moved) {
                        mgr.save();
                    }
                }
                return true;
            }
            return false;
        } finally {
            clearDrag();
        }
    }

    private static int tabIndexAt(Minecraft mc, Placement p, int mx, int my, ChatWindow window) {
        int tabCount = window.getTabCount();
        if (tabCount <= 0) {
            return -1;
        }
        Font font = mc.font;
        List<ChatWindowTab> tabs = window.getTabs();
        return switch (p.edge()) {
            case TOP, BOTTOM -> {
                for (HRect rc : horizontalWrappedRects(font, tabs, p)) {
                    if (mx >= rc.x() && mx < rc.x() + rc.w() && my >= rc.y() && my < rc.y() + rc.h()) {
                        yield rc.idx();
                    }
                }
                yield -1;
            }
            case RIGHT, LEFT -> {
                int segH = Math.max(1, (p.sh() - 4) / tabCount);
                int rel = my - (p.sy() + 2);
                yield Mth.clamp(rel / segH, 0, tabCount - 1);
            }
        };
    }
}
