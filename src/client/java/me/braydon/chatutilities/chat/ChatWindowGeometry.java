package me.braydon.chatutilities.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public final class ChatWindowGeometry {
    private static final int LINE_HEIGHT = 9;
    private static final int PADDING = 4;
    public static final int EDGE_PX = 6;

    public enum PositioningPointer {
        NONE,
        MOVE,
        RESIZE_E,
        RESIZE_N,
        RESIZE_NE
    }

    public static PositioningPointer positioningPointerAt(int mxGui, int myGui, ChatWindowGeometry geo) {
        int edge = EDGE_PX;
        boolean nearRight =
                mxGui >= geo.x + geo.boxW - edge
                        && mxGui <= geo.x + geo.boxW + edge
                        && myGui >= geo.y
                        && myGui <= geo.y + geo.boxH;
        boolean nearTop =
                myGui >= geo.y - edge
                        && myGui <= geo.y + edge
                        && mxGui >= geo.x
                        && mxGui <= geo.x + geo.boxW;
        boolean inside =
                mxGui >= geo.x
                        && mxGui <= geo.x + geo.boxW
                        && myGui >= geo.y
                        && myGui <= geo.y + geo.boxH;
        if (nearRight && nearTop) {
            return PositioningPointer.RESIZE_NE;
        }
        if (nearRight) {
            return PositioningPointer.RESIZE_E;
        }
        if (nearTop) {
            return PositioningPointer.RESIZE_N;
        }
        if (inside) {
            return PositioningPointer.MOVE;
        }
        return PositioningPointer.NONE;
    }

    public static final class RenderedRow {
        public final FormattedCharSequence text;
        public final float alpha;

        public RenderedRow(FormattedCharSequence text, float alpha) {
            this.text = text;
            this.alpha = alpha;
        }
    }

    public final int x;
    public final int y;
    public final int boxW;
    public final int boxH;
    public final int anchorXGui;
    public final int anchorYGui;
    public final List<RenderedRow> rows;
    public final int maxHistoryScrollRows;
    public final int contentStartYOffset;

    private ChatWindowGeometry(
            int x,
            int y,
            int boxW,
            int boxH,
            int anchorXGui,
            int anchorYGui,
            List<RenderedRow> rows,
            int maxHistoryScrollRows,
            int contentStartYOffset) {
        this.x = x;
        this.y = y;
        this.boxW = boxW;
        this.boxH = boxH;
        this.anchorXGui = anchorXGui;
        this.anchorYGui = anchorYGui;
        this.rows = rows;
        this.maxHistoryScrollRows = maxHistoryScrollRows;
        this.contentStartYOffset = contentStartYOffset;
    }

    public static ChatWindowGeometry compute(ChatWindow window, Minecraft mc, int gw, int gh, Component placeholderWhenNoLine) {
        return compute(
                window,
                mc,
                gw,
                gh,
                placeholderWhenNoLine,
                mc.gui.getGuiTicks(),
                true,
                false,
                0,
                0);
    }

    public static ChatWindowGeometry compute(
            ChatWindow window,
            Minecraft mc,
            int gw,
            int gh,
            Component placeholderWhenNoLine,
            int guiTick,
            boolean forceOpaque,
            boolean chatScreenOpen,
            int mouseGuiX,
            int mouseGuiY) {
        int maxLineWidth = Math.max(24, Math.round(window.getWidthFrac() * gw) - PADDING * 2);
        int msgCap = window.getMaxVisibleLines();

        boolean useChatHistory =
                !forceOpaque && chatScreenOpen && mc.screen instanceof ChatScreen && !window.getLines().isEmpty();

        List<RenderedRow> rows = new ArrayList<>();
        int maxHistoryScroll = 0;
        int contentStartYOffset = 0;
        int viewportRows = window.getMaxVisibleLines();

        if (useChatHistory) {
            List<RenderedRow> allHistory = new ArrayList<>();
            for (ChatWindowLine line : window.getLines()) {
                for (FormattedCharSequence row : mc.font.split(line.styled(), maxLineWidth)) {
                    allHistory.add(new RenderedRow(row, 1f));
                }
            }
            int total = allHistory.size();
            maxHistoryScroll = Math.max(0, total - viewportRows);
            window.clampHistoryScroll(maxHistoryScroll);
            int scroll = window.getHistoryScrollRows();
            int start = Math.max(0, total - viewportRows - scroll);
            int end = Math.max(start, total - scroll);
            rows = new ArrayList<>(allHistory.subList(start, end));
            contentStartYOffset = Math.max(0, viewportRows - rows.size()) * LINE_HEIGHT;
        } else {
            List<ChatWindowLine> alive = new ArrayList<>();
            for (ChatWindowLine e : window.getLines()) {
                if (forceOpaque || ChatWindowFade.lineAlpha(e.addedGuiTick(), guiTick) > 0f) {
                    alive.add(e);
                }
            }
            List<ChatWindowLine> visMsg = takeLast(alive, msgCap);
            for (ChatWindowLine line : visMsg) {
                float a = forceOpaque ? 1f : ChatWindowFade.lineAlpha(line.addedGuiTick(), guiTick);
                for (FormattedCharSequence row : mc.font.split(line.styled(), maxLineWidth)) {
                    rows.add(new RenderedRow(row, a));
                }
            }
        }

        if (rows.isEmpty() && placeholderWhenNoLine != null) {
            for (FormattedCharSequence row : mc.font.split(placeholderWhenNoLine, maxLineWidth)) {
                rows.add(new RenderedRow(row, 1f));
            }
        }

        int maxRowW = 0;
        for (RenderedRow rr : rows) {
            maxRowW = Math.max(maxRowW, mc.font.width(rr.text));
        }
        if (useChatHistory) {
            for (ChatWindowLine line : window.getLines()) {
                for (FormattedCharSequence row : mc.font.split(line.styled(), maxLineWidth)) {
                    maxRowW = Math.max(maxRowW, mc.font.width(row));
                }
            }
        }

        int innerMinW = rows.isEmpty() ? 40 : maxRowW + PADDING * 2;
        int boxW = Math.min(gw, Math.max(innerMinW, Math.round(window.getWidthFrac() * gw)));

        int lineCount;
        int boxH;
        if (useChatHistory) {
            lineCount = viewportRows;
            boxH = lineCount * LINE_HEIGHT + PADDING * 2;
        } else {
            int contentRows = Math.max(rows.isEmpty() ? 1 : rows.size(), 1);
            if (forceOpaque) {
                lineCount = Math.max(contentRows, viewportRows);
            } else {
                lineCount = contentRows;
            }
            boxH = lineCount * LINE_HEIGHT + PADDING * 2;
        }

        int anchorXGui = Math.round(window.getAnchorX() * gw);
        int anchorYGui = Math.round(window.getAnchorY() * gh);
        int x = Math.min(Math.max(0, anchorXGui), Math.max(0, gw - boxW));
        int y = Math.min(Math.max(0, anchorYGui - boxH), Math.max(0, gh - boxH));

        if (!useChatHistory && forceOpaque && lineCount > rows.size()) {
            contentStartYOffset = (lineCount - rows.size()) * LINE_HEIGHT;
        }

        return new ChatWindowGeometry(
                x, y, boxW, boxH, anchorXGui, anchorYGui, rows, maxHistoryScroll, contentStartYOffset);
    }

    public static boolean historyHitTest(ChatWindow window, Minecraft mc, int gw, int mouseGuiX, int mouseGuiY) {
        if (!(mc.screen instanceof ChatScreen) || window.getLines().isEmpty()) {
            return false;
        }
        int viewportRows = window.getMaxVisibleLines();
        int boxH = viewportRows * LINE_HEIGHT + PADDING * 2;
        int boxW = Math.min(gw, Math.round(window.getWidthFrac() * gw));
        int anchorXGui = Math.round(window.getAnchorX() * gw);
        int gh = mc.getWindow().getGuiScaledHeight();
        int anchorYGui = Math.round(window.getAnchorY() * gh);
        int x = Math.min(Math.max(0, anchorXGui), Math.max(0, gw - boxW));
        int y = Math.min(Math.max(0, anchorYGui - boxH), Math.max(0, gh - boxH));
        return mouseGuiX >= x
                && mouseGuiX < x + boxW
                && mouseGuiY >= y
                && mouseGuiY < y + boxH;
    }

    public static int[] historyScrollMetrics(ChatWindow window, Minecraft mc, int gw) {
        int maxLineWidth = Math.max(24, Math.round(window.getWidthFrac() * gw) - PADDING * 2);
        int total = countWrappedRows(window, mc, maxLineWidth);
        int viewportRows = window.getMaxVisibleLines();
        return new int[] {total, viewportRows};
    }

    private static int countWrappedRows(ChatWindow window, Minecraft mc, int maxLineWidth) {
        int n = 0;
        for (ChatWindowLine line : window.getLines()) {
            for (FormattedCharSequence ignored : mc.font.split(line.styled(), maxLineWidth)) {
                n++;
            }
        }
        return n;
    }

    private static List<ChatWindowLine> takeLast(Iterable<ChatWindowLine> lines, int n) {
        ArrayList<ChatWindowLine> buf = new ArrayList<>();
        for (ChatWindowLine s : lines) {
            buf.add(s);
            if (buf.size() > n) {
                buf.removeFirst();
            }
        }
        return buf;
    }

    public static int lineHeight() {
        return LINE_HEIGHT;
    }

    public static int padding() {
        return PADDING;
    }

    public static int argbText(float alpha, int rgb) {
        int a = Mth.clamp(Math.round(alpha * 255f), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
