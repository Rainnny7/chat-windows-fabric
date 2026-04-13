package me.braydon.chatutilities.chat;

import me.braydon.chatutilities.ChatUtilitiesModClient;
import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ChatWindowGeometry {
    private static final int PADDING = 4;
    /**
     * Gap between the inner top padding and the first chat row — vanilla HUD leaves noticeable space above the first
     * line (in addition to side padding); 2px was too tight on common GUI scales.
     */
    public static final int CONTENT_TOP_INSET = 0;
    /**
     * In adjust-layout overlay only: small inset between the window outline and chat rows (not the HUD’s larger
     * {@link #padding()} + {@link #CONTENT_TOP_INSET}).
     */
    public static final int LAYOUT_MODE_CONTENT_INSET = 2;
    /** Pushes chat text slightly down within each row like vanilla (line strip vs baseline). */
    public static final int ROW_TEXT_TOP_NUDGE = 1;
    public static final int EDGE_PX = 6;
    /** Larger hit area for bottom-right corner (resize both width and height from bottom). */
    public static final int SE_CORNER_PX = 14;

    public enum PositioningPointer {
        NONE,
        MOVE,
        RESIZE_E,
        RESIZE_W,
        RESIZE_N,
        RESIZE_S,
        RESIZE_NE,
        RESIZE_NW,
        RESIZE_SE,
        RESIZE_SW
    }

    private static float stackPulseBoostedAlpha(float base, ChatWindowLine line) {
        long until = line.stackPulseUntilMs();
        if (until <= System.currentTimeMillis()) {
            return base;
        }
        float u = (until - System.currentTimeMillis()) / 450f;
        float bump = 0.42f * Mth.clamp(u, 0f, 1f);
        return Mth.clamp(base + bump, 0f, 1f);
    }

    /**
     * Hit-test for layout mode: corners first (bottom-right uses a larger zone), then edges, then move.
     * Regions extend slightly outside the box so handles are easier to grab.
     */
    public static PositioningPointer positioningPointerAt(int mxGui, int myGui, ChatWindowGeometry geo) {
        int x = geo.x;
        int y = geo.y;
        int w = geo.boxW;
        int h = geo.boxH;
        int e = EDGE_PX;
        int se = SE_CORNER_PX;
        int oc = EDGE_PX;

        // Corners (outside padding allowed)
        if (mxGui >= x + w - se && mxGui <= x + w + e && myGui >= y + h - se && myGui <= y + h + e) {
            return PositioningPointer.RESIZE_SE;
        }
        if (mxGui >= x + w - oc && mxGui <= x + w + e && myGui >= y - e && myGui <= y + oc) {
            return PositioningPointer.RESIZE_NE;
        }
        if (mxGui >= x - e && mxGui <= x + oc && myGui >= y + h - oc && myGui <= y + h + e) {
            return PositioningPointer.RESIZE_SW;
        }
        if (mxGui >= x - e && mxGui <= x + oc && myGui >= y - e && myGui <= y + oc) {
            return PositioningPointer.RESIZE_NW;
        }
        // Edges (full length so narrow windows still get a bottom/top strip)
        if (myGui >= y + h - e && myGui <= y + h + e && mxGui >= x && mxGui <= x + w) {
            return PositioningPointer.RESIZE_S;
        }
        if (myGui >= y - e && myGui <= y + e && mxGui >= x && mxGui <= x + w) {
            return PositioningPointer.RESIZE_N;
        }
        if (mxGui >= x + w - e && mxGui <= x + w + e && myGui >= y && myGui <= y + h) {
            return PositioningPointer.RESIZE_E;
        }
        if (mxGui >= x - e && mxGui <= x + e && myGui >= y && myGui <= y + h) {
            return PositioningPointer.RESIZE_W;
        }
        if (mxGui >= x && mxGui <= x + w && myGui >= y && myGui <= y + h) {
            return PositioningPointer.MOVE;
        }
        return PositioningPointer.NONE;
    }

    public static final class RenderedRow {
        public final FormattedCharSequence text;
        public final float alpha;
        /** Extra Y (gui px) while smooth-chat slide-in runs; 0 when inactive. */
        public final int slideYOffset;

        public RenderedRow(FormattedCharSequence text, float alpha, int slideYOffset) {
            this.text = text;
            this.alpha = alpha;
            this.slideYOffset = slideYOffset;
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
    /**
     * Top padding above the first line inside the window (smaller when chat is open and the window has multiple
     * tabs, so the gap under the tab strip is not oversized).
     */
    public final int contentTopInsetPx;
    /**
     * Offset in gui-px from the box's top edge ({@link #y}) to the top of the first rendered row.
     * Replaces the old {@code PADDING + contentTopInsetPx + contentStartYOffset} formula so that
     * compact layout-chrome mode (which uses a smaller inset than PADDING) renders rows flush with
     * its outline instead of leaving dead space above and below.
     */
    public final int contentRowOffsetY;
    /**
     * Bottom inset in gui-px: the scissor/clip bottom is {@code y + boxH - rowBottomInsetPx}.
     * Matches {@link #contentRowOffsetY} so the top and bottom margins are symmetric in every mode.
     */
    public final int rowBottomInsetPx;
    /** Same row pitch as vanilla HUD chat ({@link net.minecraft.client.gui.components.ChatComponent#getLineHeight()}). */
    public final int linePx;

    /** Text scale used for this geometry (affects row height and wrap width). */
    public final float textScale;

    private ChatWindowGeometry(
            int x,
            int y,
            int boxW,
            int boxH,
            int anchorXGui,
            int anchorYGui,
            List<RenderedRow> rows,
            int maxHistoryScrollRows,
            int contentStartYOffset,
            int contentTopInsetPx,
            int contentRowOffsetY,
            int rowBottomInsetPx,
            int linePx,
            float textScale) {
        this.x = x;
        this.y = y;
        this.boxW = boxW;
        this.boxH = boxH;
        this.anchorXGui = anchorXGui;
        this.anchorYGui = anchorYGui;
        this.rows = rows;
        this.maxHistoryScrollRows = maxHistoryScrollRows;
        this.contentStartYOffset = contentStartYOffset;
        this.contentTopInsetPx = contentTopInsetPx;
        this.contentRowOffsetY = contentRowOffsetY;
        this.rowBottomInsetPx = rowBottomInsetPx;
        this.linePx = linePx;
        this.textScale = textScale;
    }

    /** Row height in gui pixels, matching vanilla chat for this client. */
    public int lineHeight() {
        return linePx;
    }

    /**
     * While chat is open with an active search filter, scroll so {@code target} is in the expanded-history viewport
     * (same row/wrap model as {@link #compute}).
     */
    public static void scrollOpenChatToLine(ChatWindow window, Minecraft mc, int gw, int gh, ChatWindowLine target) {
        if (target == null) {
            return;
        }
        int maxLineWidth = splitWidthForWindow(window, gw);
        int viewportRows = window.getMaxVisibleLines();
        List<ChatWindowLine> sources = new ArrayList<>();
        for (ChatWindowLine line : window.getLines()) {
            for (FormattedCharSequence ignored : mc.font.split(line.styled(), maxLineWidth)) {
                sources.add(line);
            }
        }
        int total = sources.size();
        if (total == 0) {
            return;
        }
        int first = -1;
        for (int i = 0; i < total; i++) {
            if (sources.get(i) == target) {
                first = i;
                break;
            }
        }
        if (first < 0) {
            return;
        }
        int maxScroll = Math.max(0, total - viewportRows);
        int startPreferred = Mth.clamp(first - viewportRows / 2, 0, Math.max(0, total - viewportRows));
        int scrollPreferred = total - viewportRows - startPreferred;
        int s = Mth.clamp(scrollPreferred, 0, maxScroll);
        window.setHistoryScrollRows(s);
    }

    private static boolean passesOpenChatSearch(Minecraft mc, ChatWindowLine line) {
        if (!(mc.screen instanceof ChatScreen)) {
            return true;
        }
        if (!ChatUtilitiesClientOptions.isChatSearchBarEnabled()) {
            return true;
        }
        if (!ChatSearchState.isFiltering()) {
            return true;
        }
        return ChatSearchState.matchesComponent(line.styled());
    }

    private static int vanillaChatLineHeight(Minecraft mc) {
        return Math.max(1, mc.gui.getChat().getLineHeight());
    }

    /** Wrap width passed to {@link net.minecraft.client.gui.Font#split} for a window at this GUI width. */
    public static int splitWidthForWindow(ChatWindow window, int gw) {
        float ts =
                Mth.clamp(window.getTextScale(), ChatWindow.MIN_TEXT_SCALE, ChatWindow.MAX_TEXT_SCALE);
        int innerPx = Math.max(24, Math.round(window.getWidthFrac() * gw) - PADDING * 2);
        return Math.max(24, (int) Math.floor(innerPx / Math.max(0.2f, ts)));
    }

    /**
     * Layout hit-test / snap geometry: configured frame ({@code anchor}, {@code widthFrac}, {@code maxVisibleLines})
     * matching the layout overlay and HUD positioning mode.
     */
    public static ChatWindowGeometry compute(ChatWindow window, Minecraft mc, int gw, int gh, Component placeholderWhenNoLine) {
        return compute(window, mc, gw, gh, placeholderWhenNoLine, false);
    }

    /**
     * @param compactLayoutChrome when true (adjust-layout overlay), {@link #boxH} matches {@link #LAYOUT_MODE_CONTENT_INSET}
     *         margins only — not {@link #CONTENT_TOP_INSET} + {@link #padding()} — so the outline matches drawn chat rows.
     */
    public static ChatWindowGeometry compute(
            ChatWindow window, Minecraft mc, int gw, int gh, Component placeholderWhenNoLine, boolean compactLayoutChrome) {
        return compute(
                window,
                mc,
                gw,
                gh,
                placeholderWhenNoLine,
                mc.gui.getGuiTicks(),
                true,
                mc.screen instanceof ChatScreen,
                0,
                0,
                false,
                compactLayoutChrome);
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
            int mouseGuiY,
            boolean tightLayoutChrome,
            boolean compactLayoutChrome) {
        float textScale =
                Mth.clamp(window.getTextScale(), ChatWindow.MIN_TEXT_SCALE, ChatWindow.MAX_TEXT_SCALE);
        int baseLh = vanillaChatLineHeight(mc);
        int lh = Math.max(1, Math.round(baseLh * textScale));
        int maxLineWidth = splitWidthForWindow(window, gw);

        boolean chatUiPresent =
                mc.screen instanceof ChatScreen
                        || (ChatUtilitiesModClient.CHAT_PEEK_KEY != null
                                && ChatUtilitiesModClient.CHAT_PEEK_KEY.isDown());
        boolean useChatHistory = !forceOpaque && chatScreenOpen && chatUiPresent && !window.getLines().isEmpty();
        if (tightLayoutChrome) {
            useChatHistory = false;
        }

        List<RenderedRow> rows = new ArrayList<>();
        int maxHistoryScroll = 0;
        int contentStartYOffset = 0;
        int viewportRows = window.getMaxVisibleLines();

        int topInset = compactLayoutChrome ? LAYOUT_MODE_CONTENT_INSET : CONTENT_TOP_INSET;
        // Keep insets stable across chat open/closed. If the window needs extra space for tabs, it should be handled
        // by the tab strip placement, not by changing content inset (which changes boxH and causes window shifting).

        if (useChatHistory) {
            // Opening chat can be very expensive if we split the entire history every frame.
            // Fast path for the common case (not scrolled): only build the last visible rows.
            int scroll = Math.max(0, window.getHistoryScrollRows());
            int need = Math.max(1, viewportRows + scroll);
            List<RenderedRow> rev = new ArrayList<>(need + 8);
            int wrappedCount = 0;
            for (var it = window.getLines().descendingIterator(); it.hasNext(); ) {
                ChatWindowLine line = it.next();
                if (!passesOpenChatSearch(mc, line)) {
                    continue;
                }
                float a = ChatWindowFade.chatWindowSmoothFadeMultiplier(line, guiTick);
                a = stackPulseBoostedAlpha(a, line);
                int slide = ChatWindowFade.chatWindowLineSlideY(line, guiTick);
                List<FormattedCharSequence> split = mc.font.split(line.styled(), maxLineWidth);
                wrappedCount += split.size();
                for (int i = split.size() - 1; i >= 0 && rev.size() < need; i--) {
                    rev.add(new RenderedRow(split.get(i), a, slide));
                }
                // If we are not scrolled, we can stop once we have enough rows.
                if (scroll == 0 && rev.size() >= viewportRows) {
                    break;
                }
            }
            // If scrolled, we need accurate max scroll so clamping doesn't break; count full history in that case.
            if (scroll > 0) {
                boolean searching = ChatSearchState.isFiltering();
                ChatWindowTab selTab = window.getSelectedTab();
                int cached = !searching ? selTab.getCachedTotalWrappedRows(maxLineWidth) : -1;
                if (cached >= 0) {
                    wrappedCount = cached;
                } else {
                    wrappedCount = 0;
                    for (ChatWindowLine line : window.getLines()) {
                        if (!passesOpenChatSearch(mc, line)) {
                            continue;
                        }
                        wrappedCount += mc.font.split(line.styled(), maxLineWidth).size();
                    }
                    if (!searching) {
                        selTab.setCachedTotalWrappedRows(wrappedCount, maxLineWidth);
                    }
                }
            }
            int total = Math.max(0, wrappedCount);
            maxHistoryScroll = Math.max(0, total - viewportRows);
            if (scroll > 0) {
                window.clampHistoryScroll(maxHistoryScroll);
            }
            // Reverse to restore top-to-bottom visual order.
            rows = new ArrayList<>(rev.size());
            for (int i = rev.size() - 1; i >= 0; i--) {
                rows.add(rev.get(i));
            }
            // If scrolled, the rev list includes rows above the viewport; slice to viewport.
            if (scroll > 0 && rows.size() > viewportRows) {
                int end = Math.max(0, rows.size() - scroll);
                int start = Math.max(0, end - viewportRows);
                if (start < end) {
                    rows = new ArrayList<>(rows.subList(start, end));
                }
            } else if (scroll == 0 && rows.size() > viewportRows) {
                rows = new ArrayList<>(rows.subList(rows.size() - viewportRows, rows.size()));
            }
        } else {
            // Fast path: only build the last visible rows (avoid splitting the entire history every frame).
            int need = Math.max(1, viewportRows);
            List<RenderedRow> rev = new ArrayList<>(need + 8);
            for (var it = window.getLines().descendingIterator(); it.hasNext() && rev.size() < need; ) {
                ChatWindowLine line = it.next();
                if (!forceOpaque && !passesOpenChatSearch(mc, line)) {
                    continue;
                }
                float a = forceOpaque ? 1f : ChatWindowFade.chatWindowLineAlpha(line, guiTick);
                if (!forceOpaque) {
                    a = stackPulseBoostedAlpha(a, line);
                }
                if (!forceOpaque && a <= 0f) {
                    continue;
                }
                int slide =
                        forceOpaque ? 0 : ChatWindowFade.chatWindowLineSlideY(line, guiTick);
                List<FormattedCharSequence> split = mc.font.split(line.styled(), maxLineWidth);
                for (int i = split.size() - 1; i >= 0 && rev.size() < need; i--) {
                    rev.add(new RenderedRow(split.get(i), a, slide));
                }
            }
            // Reverse to restore top-to-bottom visual order.
            rows = new ArrayList<>(rev.size());
            for (int i = rev.size() - 1; i >= 0; i--) {
                rows.add(rev.get(i));
            }
        }

        if (rows.isEmpty() && placeholderWhenNoLine != null) {
            for (FormattedCharSequence row : mc.font.split(placeholderWhenNoLine, maxLineWidth)) {
                rows.add(new RenderedRow(row, 1f, 0));
            }
        }
        if (rows.isEmpty() && forceOpaque) {
            int splitW = Math.max(maxLineWidth, 200);
            for (FormattedCharSequence row : mc.font.split(Component.literal("[empty]"), splitW)) {
                rows.add(new RenderedRow(row, 1f, 0));
            }
        }

        int maxRowW = 0;
        for (RenderedRow rr : rows) {
            maxRowW = Math.max(maxRowW, mc.font.width(rr.text));
        }
        // Avoid recomputing width from full history on every frame; rows already represent visible content.

        int configuredW = Math.round(window.getWidthFrac() * gw);
        int boxW;
        if (tightLayoutChrome) {
            int capW = Math.max(24, configuredW);
            int naturalW = rows.isEmpty() ? 56 : maxRowW + PADDING * 2;
            boxW = Math.min(gw, Math.max(48, Math.min(capW, naturalW)));
        } else {
            int innerMinW = rows.isEmpty() ? 40 : maxRowW + PADDING * 2;
            boxW = Math.min(gw, Math.max(innerMinW, configuredW));
        }

        // Keep window size stable regardless of current content. The window is configured in "rows", so always size
        // to that row count (or 1 minimum), then pad/offset the visible content within.
        int lineCount = Math.max(1, viewportRows);

        if (!compactLayoutChrome && !tightLayoutChrome && chatScreenOpen && chatUiPresent && window.getTabCount() > 1) {
            int maxAcross = lineCount;
            for (ChatWindowTab tab : window.getTabs()) {
                if (maxAcross >= viewportRows) {
                    break; // Already at the cap; no tab can push it higher.
                }
                int total = 0;
                // Iterate newest-first and stop as soon as this tab fills the viewport —
                // avoids scanning thousands of lines when the window has a large history.
                for (var it2 = tab.getLines().descendingIterator(); it2.hasNext(); ) {
                    ChatWindowLine line = it2.next();
                    if (!passesOpenChatSearch(mc, line)) {
                        continue;
                    }
                    total += mc.font.split(line.styled(), maxLineWidth).size();
                    if (total >= viewportRows) {
                        break;
                    }
                }
                int shown = total == 0 ? 1 : Math.min(viewportRows, total);
                maxAcross = Math.max(maxAcross, shown);
            }
            lineCount = Math.max(lineCount, maxAcross);
        }

        int boxH;
        if (useChatHistory) {
            boxH = topInset + lineCount * lh + PADDING * 2;
        } else if (compactLayoutChrome) {
            boxH = LAYOUT_MODE_CONTENT_INSET * 2 + lineCount * lh;
        } else {
            boxH = topInset + lineCount * lh + PADDING * 2;
        }

        int anchorXGui = Math.round(window.getAnchorX() * gw);
        int anchorYGui = Math.round(window.getAnchorY() * gh);
        int x = Math.min(Math.max(0, anchorXGui), Math.max(0, gw - boxW));
        int y = Math.min(Math.max(0, anchorYGui - boxH), Math.max(0, gh - boxH));

        if (!tightLayoutChrome && lineCount > rows.size()) {
            // Keep newest messages anchored to the bottom (like vanilla chat). This also prevents "reflow" when opening
            // the chat screen (T) where the same window would otherwise jump from top-aligned to bottom-aligned.
            contentStartYOffset = Math.max(0, (lineCount - rows.size()) * lh);
        }

        // In compact layout-chrome mode the outline uses only LAYOUT_MODE_CONTENT_INSET (2px) as
        // top/bottom margin — not PADDING (4px) — so the first row must start 2px from the box
        // top, not 6px (pad + topInset).  rowBottomInsetPx keeps the scissor bottom symmetric.
        int contentRowOffsetY;
        int rowBottomInsetPx;
        if (compactLayoutChrome) {
            contentRowOffsetY = LAYOUT_MODE_CONTENT_INSET + contentStartYOffset;
            rowBottomInsetPx  = LAYOUT_MODE_CONTENT_INSET;
        } else {
            contentRowOffsetY = PADDING + topInset + contentStartYOffset;
            rowBottomInsetPx  = PADDING;
        }

        return new ChatWindowGeometry(
                x,
                y,
                boxW,
                boxH,
                anchorXGui,
                anchorYGui,
                rows,
                maxHistoryScroll,
                contentStartYOffset,
                topInset,
                contentRowOffsetY,
                rowBottomInsetPx,
                lh,
                textScale);
    }

    /**
     * Which stored chat line produced {@code geo.rows.get(rowIndex)}; empty for placeholder-only rows or bad index.
     */
    public static Optional<ChatWindowLine> sourceLineForRow(
            ChatWindow window,
            Minecraft mc,
            int gw,
            ChatWindowGeometry geo,
            int guiTick,
            boolean forceOpaque,
            boolean chatScreenOpen,
            int rowIndex) {
        if (window.getLines().isEmpty() || rowIndex < 0 || rowIndex >= geo.rows.size()) {
            return Optional.empty();
        }
        int maxLineWidth = splitWidthForWindow(window, gw);
        boolean useChatHistory =
                !forceOpaque && chatScreenOpen && mc.screen instanceof ChatScreen && !window.getLines().isEmpty();

        List<ChatWindowLine> sources = new ArrayList<>();
        int viewportRows = window.getMaxVisibleLines();

        if (useChatHistory) {
            for (ChatWindowLine line : window.getLines()) {
                if (!passesOpenChatSearch(mc, line)) {
                    continue;
                }
                for (FormattedCharSequence ignored : mc.font.split(line.styled(), maxLineWidth)) {
                    sources.add(line);
                }
            }
            int total = sources.size();
            int maxHistoryScroll = Math.max(0, total - viewportRows);
            window.clampHistoryScroll(maxHistoryScroll);
            int scroll = window.getHistoryScrollRows();
            int start = Math.max(0, total - viewportRows - scroll);
            int end = Math.max(start, total - scroll);
            sources = new ArrayList<>(sources.subList(start, end));
        } else {
            List<ChatWindowLine> alive = new ArrayList<>();
            for (ChatWindowLine e : window.getLines()) {
                if (forceOpaque || ChatWindowFade.chatWindowLineAlpha(e, guiTick) > 0f) {
                    alive.add(e);
                }
            }
            List<ChatWindowLine> flat = new ArrayList<>();
            for (ChatWindowLine line : alive) {
                if (!forceOpaque && !passesOpenChatSearch(mc, line)) {
                    continue;
                }
                for (FormattedCharSequence ignored : mc.font.split(line.styled(), maxLineWidth)) {
                    flat.add(line);
                }
            }
            if (flat.size() > viewportRows) {
                sources = new ArrayList<>(flat.subList(flat.size() - viewportRows, flat.size()));
            } else {
                sources = flat;
            }
        }

        if (rowIndex >= sources.size()) {
            return Optional.empty();
        }
        return Optional.of(sources.get(rowIndex));
    }

    public static boolean historyHitTest(ChatWindow window, Minecraft mc, int gw, int mouseGuiX, int mouseGuiY) {
        if (!(mc.screen instanceof ChatScreen) || window.getLines().isEmpty()) {
            return false;
        }
        int gh = mc.getWindow().getGuiScaledHeight();
        ChatWindowGeometry geo =
                compute(
                        window,
                        mc,
                        gw,
                        gh,
                        null,
                        mc.gui.getGuiTicks(),
                        false,
                        true,
                        mouseGuiX,
                        mouseGuiY,
                        false,
                        false);
        return mouseGuiX >= geo.x
                && mouseGuiX < geo.x + geo.boxW
                && mouseGuiY >= geo.y
                && mouseGuiY < geo.y + geo.boxH;
    }

    public static int[] historyScrollMetrics(ChatWindow window, Minecraft mc, int gw) {
        int maxLineWidth = splitWidthForWindow(window, gw);
        int total = countWrappedRows(window, mc, maxLineWidth);
        int viewportRows = window.getMaxVisibleLines();
        return new int[] {total, viewportRows};
    }

    private static int countWrappedRows(ChatWindow window, Minecraft mc, int maxLineWidth) {
        int n = 0;
        for (ChatWindowLine line : window.getLines()) {
            if (!passesOpenChatSearch(mc, line)) {
                continue;
            }
            for (FormattedCharSequence ignored : mc.font.split(line.styled(), maxLineWidth)) {
                n++;
            }
        }
        return n;
    }

    /**
     * Which visible row contains {@code relY} (pixels down from the top of the first text row), matching
     * {@link ChatUtilitiesHud} placement including {@link RenderedRow#slideYOffset}.
     */
    /**
     * Row index in {@code geo.rows} whose source line is {@code target}, or {@code -1}. Used when re-anchoring HUD
     * Jump without the cursor staying inside the text column.
     */
    public static int rowIndexForSourceLine(
            ChatWindow window,
            Minecraft mc,
            int gw,
            ChatWindowGeometry geo,
            int guiTick,
            ChatWindowLine target) {
        for (int r = 0; r < geo.rows.size(); r++) {
            Optional<ChatWindowLine> src =
                    sourceLineForRow(window, mc, gw, geo, guiTick, false, true, r);
            if (src.isPresent() && src.get() == target) {
                return r;
            }
        }
        return -1;
    }

    public static int rowIndexForContentRelY(ChatWindowGeometry geo, int relY) {
        if (relY < 0 || geo.rows.isEmpty()) {
            return -1;
        }
        int acc = 0;
        int rowH = geo.linePx;
        for (int i = 0; i < geo.rows.size(); i++) {
            RenderedRow r = geo.rows.get(i);
            int top = acc + r.slideYOffset;
            int bottom = top + rowH;
            if (relY >= top && relY < bottom) {
                return i;
            }
            acc += rowH;
        }
        return -1;
    }

    /** Vanilla chat row height when no {@link ChatWindowGeometry} is available yet. */
    public static int lineHeight(Minecraft mc) {
        return vanillaChatLineHeight(mc);
    }

    public static int padding() {
        return PADDING;
    }

    public static int argbText(float alpha, int rgb) {
        int a = Mth.clamp(Math.round(alpha * 255f), 0, 255);
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
