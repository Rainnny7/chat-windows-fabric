package me.braydon.chatutilities.chat;

import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import me.braydon.chatutilities.gui.ChatUtilitiesScreenLayout;
import me.braydon.chatutilities.gui.ThinScrollbar;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.jspecify.annotations.Nullable;

/**
 * LabyMod-style palette: legacy codes in a left strip and a scrollable Unicode grid in a panel anchored
 * bottom-right above the chat bar.
 */
public final class ChatSymbolPalette {
    /** Match {@link me.braydon.chatutilities.gui.ChatUtilitiesRootScreen} panel styling. */
    public static final int PALETTE_PANEL_BG = 0xF0101012;

    public static final int PALETTE_PANEL_EDGE = 0xFF2C2C3A;
    public static final int PALETTE_SIDEBAR_BG = 0xFF080810;
    public static final int PALETTE_SIDEBAR_SEP = 0xFF1E1E28;
    public static final int PALETTE_SYMBOL_WELL = 0x28080810;
    public static final int PALETTE_HOVER_CELL = 0x304878C8;
    public static final int PALETTE_HOVER_FORMAT = 0x40FFFFFF;

    /** Chat bar chip — same family as root GUI list rows. */
    public static final int HUD_CHIP_FILL = 0xE510121A;

    public static final int HUD_CHIP_FILL_HOVER = 0xE81C2840;
    public static final int HUD_CHIP_EDGE = 0xFF2C2C3A;
    public static final int HUD_CHIP_TEXT = 0xFFEAEAF0;

    private static final String FORMAT_STYLES = "kmnlor";
    private static final String FORMAT_HEX = "abcdef";
    private static final String FORMAT_DIGITS = "0123456789";

    /**
     * Unicode shortcuts (from LabyMod {@code GuiChatSymbols#createSymbols}); BMP characters only in the
     * literal string.
     */
    private static final String SYMBOL_SOURCE =
            "\u2764\u2765\u2714\u2716\u2717\u2718\u2742\u22c6\u2722\u2723\u2724\u2725\u2726\u2729\u272a\u272b\u272c\u272d\u272e\u272f\u2730\u2605\u2731\u2732\u2733\u2734\u2735\u2736\u2737\u2738\u2739\u273a\u273b\u273c\u2744\u2745\u2746\u2747\u2748\u2749\u274a\u274b\u2606\u2721\u273d\u273e\u273f\u2740\u2741\u2743\u270c\u267c\u267d\u2702\u2704\u2708\u27a1\u2b05\u2b06\u2b07\u279f\u27a2\u27a3\u27a4\u27a5\u27a6\u27a7\u27a8\u279a\u2798\u2799\u279b\u279c\u279d\u279e\u27b8\u27b2\u27b3\u27b4\u27b5\u27b6\u27b7\u27b9\u27ba\u27bb\u27bc\u27bd\u24c2\u2b1b\u2b1c\u2588\u259b\u2580\u259c\u2586\u2584\u258c\u2615\u2139\u2122\u2691\u2690\u2603\u26a0\u2694\u2696\u2692\u2699\u269c\u2680\u2681\u2682\u2683\u2684\u2685\u268a\u268b\u268c\u268d\u268e\u268f\u2630\u2631\u2632\u2633\u2634\u2635\u2636\u2637\u2686\u2687\u2688\u2689\u267f\u2669\u266a\u266b\u266c\u266d\u266e\u266f\u2660\u2661\u2662\u2663\u2664\u2665\u2666\u2667\u2654\u2655\u2656\u2657\u2658\u2659\u265a\u265b\u265c\u265d\u265e\u265f\u26aa\u26ab\u262f\u262e\u2623\u260f\u2780\u2781\u2782\u2783\u2784\u2785\u2786\u2787\u2788\u2789\u278a\u278b\u278c\u278d\u278e\u278f\u2790\u2791\u2792\u2793\u24d0\u24d1\u24d2\u24d3\u24d4\u24d5\u24d6\u24d7\u24d8\u24d9\u24da\u24db\u24dc\u24dd\u24de\u24df\u24e0\u24e1\u24e2\u24e3\u24e4\u24e5\u24e6\u24e7\u24e8\u24e9\uc6c3\uc720\u264b\u2622\u2620\u2611\u25b2\u231a\u00bf\u2763\u2642\u2640\u263f\u24b6\u270d\u2709\u2624\u2612\u25bc\u2318\u231b\u00a1\u10e6\u30c4\u263c\u2601\u2652\u270e\u00a9\u00ae\u03a3\u262d\u271e\u2103\u2109\u03df\u2602\u00a2\u00a3\u221e\u00bd\u262a\u263a\u263b\u2639\u2307\u269b\u2328\u2706\u260e\u2325\u21e7\u21a9\u2190\u2192\u2191\u2193\u27ab\u261c\u261e\u261d\u261f\u267a\u2332\u26a2\u26a3\u2751\u2752\u25c8\u25d0\u25d1\u00ab\u00bb\u2039\u203a\u2013\u2014\u2044\u00b6\u203d\u2042\u203b\u00b1\u00d7\u2248\u00f7\u2260\u03c0\u2020\u2021\u00a5\u20ac\u2030\u2026\u00b7\u2022\u25cf";

    private static final String[] SYMBOLS = buildSymbols();

    private static final int CELL = 11;
    private static final int SYMBOL_CELL = 10;
    private static final int PANEL_PAD = 4;
    private static final int EDGE_MARGIN = 4;
    /** Total width of the symbols panel (right block), matching the previous single-panel body width. */
    private static final int MAIN_PANEL_WIDTH = 148;
    /** Space between the legacy-code strip and the symbols panel (kept small so the two read as one block). */
    private static final int STRIP_GAP = 1;
    /** Left inset of the symbol grid inside the main panel (title/divider keep {@link #PANEL_PAD}). */
    private static final int SYMBOL_WELL_PAD_LEFT = 2;
    private static final int STACK_HEIGHT = 136;
    private static final int CLOSE_SIZE = 10;

    private boolean open;
    private double scrollPixels;

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        this.open = open;
        if (!open) {
            scrollPixels = 0;
        }
    }

    public void toggle() {
        setOpen(!open);
    }

    private static String[] buildSymbols() {
        String[] out = new String[SYMBOL_SOURCE.length()];
        for (int i = 0; i < SYMBOL_SOURCE.length(); i++) {
            out[i] = String.valueOf(SYMBOL_SOURCE.charAt(i));
        }
        return out;
    }

    private static int stripWidth() {
        return PANEL_PAD * 2 + 3 * CELL;
    }

    private static int stackBottom(int screenHeight) {
        return screenHeight - 18;
    }

    private static int stackTop(int screenHeight) {
        return stackBottom(screenHeight) - STACK_HEIGHT;
    }

    private static int mainPanelRight(int screenWidth) {
        return screenWidth - EDGE_MARGIN;
    }

    private static int mainPanelLeft(int screenWidth) {
        return mainPanelRight(screenWidth) - MAIN_PANEL_WIDTH;
    }

    private static int stripRight(int screenWidth) {
        return mainPanelLeft(screenWidth) - STRIP_GAP;
    }

    private static int stripLeft(int screenWidth) {
        return stripRight(screenWidth) - stripWidth();
    }

    private static int symbolsTop(int screenHeight, Font font) {
        int st = stackTop(screenHeight);
        return st + PANEL_PAD + font.lineHeight + 4;
    }

    private static int symbolsAreaHeight(int screenHeight, Font font) {
        return stackBottom(screenHeight) - PANEL_PAD - symbolsTop(screenHeight, font);
    }

    private static int symbolsAreaLeft(int screenWidth) {
        return mainPanelLeft(screenWidth) + SYMBOL_WELL_PAD_LEFT;
    }

    private static int symbolsAreaRight(int screenWidth) {
        return mainPanelRight(screenWidth) - PANEL_PAD - ThinScrollbar.W;
    }

    /** As many columns as fit in the symbol well so the grid uses the full width (scrollbar stays on the right). */
    private static int symbolColumnCount(int symbolsAreaWidth) {
        return Math.max(1, symbolsAreaWidth / SYMBOL_CELL);
    }

    private static int symbolRowCount(int cols) {
        return (SYMBOLS.length + cols - 1) / cols;
    }

    private static int closeButtonX(int screenWidth) {
        return mainPanelRight(screenWidth) - PANEL_PAD - CLOSE_SIZE;
    }

    private static int closeButtonY(int screenHeight) {
        return stackTop(screenHeight) + 3;
    }

    public boolean containsPoint(double mx, double my, int screenWidth, int screenHeight) {
        if (!open) {
            return false;
        }
        int sl = stripLeft(screenWidth);
        int mr = mainPanelRight(screenWidth);
        int st = stackTop(screenHeight);
        int sb = stackBottom(screenHeight);
        return mx >= sl && mx < mr && my >= st && my < sb;
    }

    /** @return true if the click was used by the palette (caller should swallow the event). */
    public boolean mouseClicked(double mx, double my, int button, EditBox input, int screenWidth, int screenHeight) {
        if (!open || button != 0) {
            return false;
        }
        int cx = closeButtonX(screenWidth);
        int cy = closeButtonY(screenHeight);
        if (mx >= cx && mx < cx + CLOSE_SIZE && my >= cy && my < cy + CLOSE_SIZE) {
            setOpen(false);
            playClick();
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        if (tryClickFormatStrip(mx, my, screenWidth, screenHeight, input)) {
            return true;
        }
        Font font = mc.font;
        int symTop = symbolsTop(screenHeight, font);
        int symH = symbolsAreaHeight(screenHeight, font);
        int symLeft = symbolsAreaLeft(screenWidth);
        int symRight = symbolsAreaRight(screenWidth);
        int areaW = symRight - symLeft;
        int cols = symbolColumnCount(areaW);
        if (my >= symTop && my < symTop + symH && mx >= symLeft && mx < symRight) {
            if (mx >= symLeft + cols * SYMBOL_CELL) {
                return false;
            }
            int col = (int) ((mx - symLeft) / SYMBOL_CELL);
            int row = (int) ((my - symTop + scrollPixels) / SYMBOL_CELL);
            if (col < 0 || col >= cols || row < 0) {
                return false;
            }
            int index = row * cols + col;
            if (index < SYMBOLS.length) {
                insertRaw(input, SYMBOLS[index]);
                playClick();
                return true;
            }
        }
        return false;
    }

    private boolean tryClickFormatStrip(
            double mx, double my, int screenWidth, int screenHeight, EditBox input) {
        int st = stackTop(screenHeight);
        int sb = stackBottom(screenHeight);
        int sr = stripRight(screenWidth);
        int sl = stripLeft(screenWidth);
        if (my < st || my >= sb || mx < sl || mx >= sr) {
            return false;
        }
        int innerLeft = sl + PANEL_PAD;
        int innerTop = st + PANEL_PAD;
        String[] cols = {FORMAT_STYLES, FORMAT_HEX, FORMAT_DIGITS};
        for (int c = 0; c < cols.length; c++) {
            String s = cols[c];
            for (int r = 0; r < s.length(); r++) {
                int cellX = innerLeft + c * CELL;
                int cellY = innerTop + r * CELL;
                if (mx >= cellX && mx < cellX + CELL && my >= cellY && my < cellY + CELL) {
                    insertFormatting(input, s.charAt(r));
                    playClick();
                    return true;
                }
            }
        }
        return false;
    }

    public boolean mouseScrolled(
            double mx, double my, double verticalAmount, int screenWidth, int screenHeight) {
        if (!open || verticalAmount == 0.0) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int symTop = symbolsTop(screenHeight, font);
        int symH = symbolsAreaHeight(screenHeight, font);
        int symLeft = symbolsAreaLeft(screenWidth);
        int symRight = mainPanelRight(screenWidth) - PANEL_PAD;
        int scrollRight = mainPanelRight(screenWidth) - PANEL_PAD;
        if (mx >= symLeft && mx < scrollRight && my >= symTop && my < symTop + symH) {
            nudgeScroll(-verticalAmount * 12.0, screenWidth, screenHeight, font);
            return true;
        }
        return false;
    }

    private void nudgeScroll(double delta, int screenWidth, int screenHeight, Font font) {
        int areaW = symbolsAreaRight(screenWidth) - symbolsAreaLeft(screenWidth);
        int cols = symbolColumnCount(areaW);
        int rows = symbolRowCount(cols);
        int symH = symbolsAreaHeight(screenHeight, font);
        int contentH = rows * SYMBOL_CELL;
        double maxScroll = Math.max(0, contentH - symH);
        scrollPixels = Mth.clamp(scrollPixels + delta, 0, maxScroll);
    }

    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!open) {
            return;
        }
        int sl = stripLeft(screenWidth);
        int sr = stripRight(screenWidth);
        int ml = mainPanelLeft(screenWidth);
        int mr = mainPanelRight(screenWidth);
        int st = stackTop(screenHeight);
        int sb = stackBottom(screenHeight);

        graphics.fill(sl, st, sr, sb, PALETTE_SIDEBAR_BG);
        graphics.fill(ml, st, mr, sb, PALETTE_PANEL_BG);

        graphics.fill(sr - 1, st, sr, sb, PALETTE_SIDEBAR_SEP);

        graphics.renderOutline(sl, st, mr - sl, sb - st, PALETTE_PANEL_EDGE);

        renderFormatStrip(graphics, font, mouseX, mouseY, screenWidth, screenHeight);

        String title = I18n.get("chat-utilities.chat.symbol_palette.title");
        graphics.drawString(font, title, ml + PANEL_PAD, st + PANEL_PAD, ChatUtilitiesScreenLayout.TEXT_LABEL, false);

        int cbx = closeButtonX(screenWidth);
        int cby = closeButtonY(screenHeight);
        boolean closeHover =
                mouseX >= cbx && mouseX < cbx + CLOSE_SIZE && mouseY >= cby && mouseY < cby + CLOSE_SIZE;
        if (closeHover) {
            graphics.fill(cbx, cby, cbx + CLOSE_SIZE, cby + CLOSE_SIZE, PALETTE_HOVER_FORMAT);
        }
        String closeLabel = "\u00D7";
        int cw = font.width(closeLabel);
        int ch = font.lineHeight;
        graphics.drawString(
                font,
                closeLabel,
                cbx + (CLOSE_SIZE - cw) / 2,
                cby + (CLOSE_SIZE - ch) / 2,
                closeHover ? 0xFFFFFFFF : ChatUtilitiesScreenLayout.TEXT_GRAY,
                false);

        int divY = st + PANEL_PAD + font.lineHeight + 2;
        graphics.fill(ml + PANEL_PAD, divY, mr - PANEL_PAD, divY + 1, PALETTE_SIDEBAR_SEP);

        int symTop = symbolsTop(screenHeight, font);
        int symH = symbolsAreaHeight(screenHeight, font);
        int symLeft = symbolsAreaLeft(screenWidth);
        int symRight = symbolsAreaRight(screenWidth);
        int areaW = symRight - symLeft;
        int cols = symbolColumnCount(areaW);
        int rowsTotal = symbolRowCount(cols);
        int contentHScroll = rowsTotal * SYMBOL_CELL;
        scrollPixels = Mth.clamp(scrollPixels, 0, Math.max(0, contentHScroll - symH));

        graphics.fill(symLeft, symTop, symRight, symTop + symH, PALETTE_SYMBOL_WELL);

        int firstRow = (int) (scrollPixels / SYMBOL_CELL);
        double yOff = scrollPixels % SYMBOL_CELL;
        graphics.enableScissor(symLeft, symTop, symRight, symTop + symH);
        try {
            for (int row = 0; row <= symH / SYMBOL_CELL + 1; row++) {
                int r = firstRow + row;
                for (int c = 0; c < cols; c++) {
                    int idx = r * cols + c;
                    if (idx >= SYMBOLS.length) {
                        break;
                    }
                    int sx = symLeft + c * SYMBOL_CELL;
                    int sy = (int) (symTop + row * SYMBOL_CELL - yOff);
                    if (sy + SYMBOL_CELL < symTop || sy > symTop + symH) {
                        continue;
                    }
                    boolean hovered =
                            mouseX >= sx
                                    && mouseX < sx + SYMBOL_CELL
                                    && mouseY >= Math.max(symTop, sy)
                                    && mouseY < Math.min(symTop + symH, sy + SYMBOL_CELL);
                    if (hovered) {
                        graphics.fill(
                                sx,
                                Math.max(symTop, sy),
                                sx + SYMBOL_CELL,
                                Math.min(symTop + symH, sy + SYMBOL_CELL),
                                PALETTE_HOVER_CELL);
                    }
                    String sym = SYMBOLS[idx];
                    int sw = font.width(sym);
                    int sh = font.lineHeight;
                    int drawY = sy + (SYMBOL_CELL - sh) / 2;
                    if (drawY + sh >= symTop && drawY < symTop + symH) {
                        graphics.drawString(font, sym, sx + (SYMBOL_CELL - sw) / 2, drawY, 0xFFFFFFFF, false);
                    }
                }
            }
        } finally {
            graphics.disableScissor();
        }

        int rows = symbolRowCount(cols);
        int contentH = rows * SYMBOL_CELL;
        ThinScrollbar.render(graphics, symRight, symTop, symH, contentH, scrollPixels, 1f);
    }

    private void renderFormatStrip(
            GuiGraphics graphics, Font font, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        int st = stackTop(screenHeight);
        int sl = stripLeft(screenWidth);
        int innerLeft = sl + PANEL_PAD;
        int innerTop = st + PANEL_PAD;
        String[] cols = {FORMAT_STYLES, FORMAT_HEX, FORMAT_DIGITS};
        for (int c = 0; c < cols.length; c++) {
            String s = cols[c];
            for (int r = 0; r < s.length(); r++) {
                int cx = innerLeft + c * CELL;
                int cy = innerTop + r * CELL;
                boolean hovered =
                        mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL;
                int bg = hovered ? PALETTE_HOVER_FORMAT : 0x20000000;
                graphics.fill(cx, cy, cx + CELL, cy + CELL, bg);
                char code = s.charAt(r);
                String label = String.valueOf(code);
                int tw = font.width(label);
                int fg = labelColorForCode(code);
                graphics.drawString(font, label, cx + (CELL - tw) / 2, cy + 2, fg, false);
            }
        }
    }

    private static int labelColorForCode(char code) {
        ChatFormatting f = formattingForLegacyCodeChar(code);
        if (f != null) {
            Integer c = f.getColor();
            if (c != null) {
                return 0xFF000000 | c;
            }
        }
        return ChatUtilitiesScreenLayout.TEXT_LABEL;
    }

    private static @Nullable ChatFormatting formattingForLegacyCodeChar(char code) {
        for (ChatFormatting f : ChatFormatting.values()) {
            if (f.getChar() == code) {
                return f;
            }
        }
        return null;
    }

    private static void insertFormatting(EditBox box, char code) {
        String text =
                ChatPaletteFormatInsert.textForLegacyCode(
                        code, ChatUtilitiesClientOptions.getSymbolPaletteInsertStyle());
        insertRaw(box, text);
    }

    private static void insertRaw(EditBox box, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int pos = Mth.clamp(box.getCursorPosition(), 0, box.getValue().length());
        String v = box.getValue();
        String next = v.substring(0, pos) + text + v.substring(pos);
        box.setValue(next);
        int np = pos + text.length();
        box.setCursorPosition(np);
    }

    private static void playClick() {
        playUiClickSound();
    }

    public static void playUiClickSound() {
        Minecraft.getInstance()
                .getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
    }
}
