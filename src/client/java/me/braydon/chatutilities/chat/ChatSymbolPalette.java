package me.braydon.chatutilities.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

/**
 * LabyMod-style palette: style codes (§) and a scrollable Unicode grid, anchored to the bottom-right
 * above the chat bar.
 */
public final class ChatSymbolPalette {
    /** Same ordering as common chat-color mods (digits, hex colors, then magic/reset). */
    private static final String STYLE_CODES = "0123456789abcdefkmnlopr";

    /**
     * Unicode shortcuts (from LabyMod {@code GuiChatSymbols#createSymbols}); BMP characters only in the
     * literal string.
     */
    private static final String SYMBOL_SOURCE =
            "\u2764\u2765\u2714\u2716\u2717\u2718\u2742\u22c6\u2722\u2723\u2724\u2725\u2726\u2729\u272a\u272b\u272c\u272d\u272e\u272f\u2730\u2605\u2731\u2732\u2733\u2734\u2735\u2736\u2737\u2738\u2739\u273a\u273b\u273c\u2744\u2745\u2746\u2747\u2748\u2749\u274a\u274b\u2606\u2721\u273d\u273e\u273f\u2740\u2741\u2743\u270c\u267c\u267d\u2702\u2704\u2708\u27a1\u2b05\u2b06\u2b07\u279f\u27a2\u27a3\u27a4\u27a5\u27a6\u27a7\u27a8\u279a\u2798\u2799\u279b\u279c\u279d\u279e\u27b8\u27b2\u27b3\u27b4\u27b5\u27b6\u27b7\u27b9\u27ba\u27bb\u27bc\u27bd\u24c2\u2b1b\u2b1c\u2588\u259b\u2580\u259c\u2586\u2584\u258c\u2615\u2139\u2122\u2691\u2690\u2603\u26a0\u2694\u2696\u2692\u2699\u269c\u2680\u2681\u2682\u2683\u2684\u2685\u268a\u268b\u268c\u268d\u268e\u268f\u2630\u2631\u2632\u2633\u2634\u2635\u2636\u2637\u2686\u2687\u2688\u2689\u267f\u2669\u266a\u266b\u266c\u266d\u266e\u266f\u2660\u2661\u2662\u2663\u2664\u2665\u2666\u2667\u2654\u2655\u2656\u2657\u2658\u2659\u265a\u265b\u265c\u265d\u265e\u265f\u26aa\u26ab\u262f\u262e\u2623\u260f\u2780\u2781\u2782\u2783\u2784\u2785\u2786\u2787\u2788\u2789\u278a\u278b\u278c\u278d\u278e\u278f\u2790\u2791\u2792\u2793\u24d0\u24d1\u24d2\u24d3\u24d4\u24d5\u24d6\u24d7\u24d8\u24d9\u24da\u24db\u24dc\u24dd\u24de\u24df\u24e0\u24e1\u24e2\u24e3\u24e4\u24e5\u24e6\u24e7\u24e8\u24e9\uc6c3\uc720\u264b\u2622\u2620\u2611\u25b2\u231a\u00bf\u2763\u2642\u2640\u263f\u24b6\u270d\u2709\u2624\u2612\u25bc\u2318\u231b\u00a1\u10e6\u30c4\u263c\u2601\u2652\u270e\u00a9\u00ae\u03a3\u262d\u271e\u2103\u2109\u03df\u2602\u00a2\u00a3\u221e\u00bd\u262a\u263a\u263b\u2639\u2307\u269b\u2328\u2706\u260e\u2325\u21e7\u21a9\u2190\u2192\u2191\u2193\u27ab\u261c\u261e\u261d\u261f\u267a\u2332\u26a2\u26a3\u2751\u2752\u25c8\u25d0\u25d1\u00ab\u00bb\u2039\u203a\u2013\u2014\u2044\u00b6\u203d\u2042\u203b\u00b1\u00d7\u2248\u00f7\u2260\u03c0\u2020\u2021\u00a5\u20ac\u2030\u2026\u00b7\u2022\u25cf";

    private static final String[] SYMBOLS = buildSymbols();

    private static final int COLOR_COLS = 8;
    private static final int CELL = 11;
    private static final int SYMBOL_COLS = 9;
    private static final int SYMBOL_CELL = 10;
    private static final int PANEL_PAD = 4;
    private static final int SCROLLBAR_W = 4;

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

    /** Total outer width from right edge (wider than original Laby strip). */
    private static final int PANEL_WIDTH = 152;

    private static int panelLeft(int screenWidth) {
        return screenWidth - PANEL_WIDTH;
    }

    private static int panelRight(int screenWidth) {
        return screenWidth - 4;
    }

    private static int panelBottom(int screenHeight) {
        return screenHeight - 18;
    }

    private static int panelTop(int screenHeight) {
        return panelBottom(screenHeight) - 136;
    }

    private static int colorSectionBottom(int screenHeight) {
        return panelTop(screenHeight) + PANEL_PAD + ((STYLE_CODES.length() + COLOR_COLS - 1) / COLOR_COLS) * CELL + 2;
    }

    private static int symbolsTop(int screenHeight) {
        return colorSectionBottom(screenHeight) + 2;
    }

    private static int symbolsAreaHeight(int screenHeight) {
        return panelBottom(screenHeight) - symbolsTop(screenHeight) - PANEL_PAD;
    }

    private static int symbolsAreaLeft(int screenWidth) {
        return panelLeft(screenWidth) + PANEL_PAD;
    }

    private static int symbolsAreaRight(int screenWidth) {
        return panelRight(screenWidth) - PANEL_PAD - SCROLLBAR_W - 2;
    }

    public boolean containsPoint(double mx, double my, int screenWidth, int screenHeight) {
        if (!open) {
            return false;
        }
        return mx >= panelLeft(screenWidth)
                && mx < panelRight(screenWidth)
                && my >= panelTop(screenHeight)
                && my < panelBottom(screenHeight);
    }

    /** @return true if the click was used by the palette (caller should swallow the event). */
    public boolean mouseClicked(double mx, double my, int button, EditBox input, int screenWidth, int screenHeight) {
        if (!open || button != 0) {
            return false;
        }
        int pl = panelLeft(screenWidth);
        int pt = panelTop(screenHeight);
        int colorBottom = colorSectionBottom(screenHeight);

        for (int i = 0; i < STYLE_CODES.length(); i++) {
            char code = STYLE_CODES.charAt(i);
            int col = i % COLOR_COLS;
            int row = i / COLOR_COLS;
            int cx = pl + PANEL_PAD + col * CELL;
            int cy = pt + PANEL_PAD + row * CELL;
            if (mx >= cx && mx < cx + CELL && my >= cy && my < cy + CELL) {
                insertFormatting(input, code);
                playClick();
                return true;
            }
        }

        int symTop = symbolsTop(screenHeight);
        int symH = symbolsAreaHeight(screenHeight);
        int symLeft = symbolsAreaLeft(screenWidth);
        int symRight = symbolsAreaRight(screenWidth);
        if (my >= symTop && my < symTop + symH && mx >= symLeft && mx < symRight) {
            int col = (int) ((mx - symLeft) / SYMBOL_CELL);
            int row = (int) ((my - symTop + scrollPixels) / SYMBOL_CELL);
            col = Mth.clamp(col, 0, SYMBOL_COLS - 1);
            if (col < SYMBOL_COLS && row >= 0) {
                int index = row * SYMBOL_COLS + col;
                if (index < SYMBOLS.length) {
                    insertRaw(input, SYMBOLS[index]);
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
        int symTop = symbolsTop(screenHeight);
        int symH = symbolsAreaHeight(screenHeight);
        int symLeft = symbolsAreaLeft(screenWidth);
        int symRight = panelRight(screenWidth) - PANEL_PAD;
        if (mx >= symLeft && mx < symRight && my >= symTop && my < symTop + symH) {
            nudgeScroll(-verticalAmount * 12.0, screenHeight);
            return true;
        }
        return false;
    }

    private void nudgeScroll(double delta, int screenHeight) {
        int rows = (SYMBOLS.length + SYMBOL_COLS - 1) / SYMBOL_COLS;
        int symH = symbolsAreaHeight(screenHeight);
        int contentH = rows * SYMBOL_CELL;
        double maxScroll = Math.max(0, contentH - symH);
        scrollPixels = Mth.clamp(scrollPixels + delta, 0, maxScroll);
    }

    public void render(GuiGraphics graphics, Font font, int screenWidth, int screenHeight, int mouseX, int mouseY) {
        if (!open) {
            return;
        }
        int pl = panelLeft(screenWidth);
        int pr = panelRight(screenWidth);
        int pt = panelTop(screenHeight);
        int pb = panelBottom(screenHeight);
        graphics.fill(pl, pt, pr, pb, 0xC8000000);
        graphics.renderOutline(pl, pt, pr - pl, pb - pt, 0xFF555555);

        int colorBottom = colorSectionBottom(screenHeight);
        for (int i = 0; i < STYLE_CODES.length(); i++) {
            char code = STYLE_CODES.charAt(i);
            int col = i % COLOR_COLS;
            int row = i / COLOR_COLS;
            int cx = pl + PANEL_PAD + col * CELL;
            int cy = pt + PANEL_PAD + row * CELL;
            boolean hovered =
                    mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL;
            int bg = hovered ? 0xFF5A5A78 : 0xFF2A2A2A;
            graphics.fill(cx, cy, cx + CELL, cy + CELL, bg);
            String label = String.valueOf(code);
            int tw = font.width(label);
            graphics.drawString(font, label, cx + (CELL - tw) / 2, cy + 2, 0xFFFFFFFF, false);
        }

        int symTop = symbolsTop(screenHeight);
        int symH = symbolsAreaHeight(screenHeight);
        int symLeft = symbolsAreaLeft(screenWidth);
        int symRight = symbolsAreaRight(screenWidth);
        graphics.fill(symLeft, symTop, symRight, symTop + symH, 0x66000000);

        int firstRow = (int) (scrollPixels / SYMBOL_CELL);
        double yOff = scrollPixels % SYMBOL_CELL;
        // GuiGraphics.enableScissor(x1, y1, x2, y2) — opposite corners, not (x, y, w, h).
        graphics.enableScissor(symLeft, symTop, symRight, symTop + symH);
        try {
            for (int row = 0; row <= symH / SYMBOL_CELL + 1; row++) {
                int r = firstRow + row;
                for (int c = 0; c < SYMBOL_COLS; c++) {
                    int idx = r * SYMBOL_COLS + c;
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
                                0x336666FF);
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

        int sbLeft = symRight + 2;
        int sbRight = sbLeft + SCROLLBAR_W;
        graphics.fill(sbLeft, symTop, sbRight, symTop + symH, 0xFF1A1A1A);
        int rows = (SYMBOLS.length + SYMBOL_COLS - 1) / SYMBOL_COLS;
        int contentH = rows * SYMBOL_CELL;
        if (contentH > symH) {
            double frac = symH / (double) contentH;
            int thumbH = Math.max(8, (int) (symH * frac));
            double maxScroll = contentH - symH;
            double t = maxScroll > 0 ? scrollPixels / maxScroll : 0;
            int thumbY = symTop + (int) ((symH - thumbH) * t);
            graphics.fill(sbLeft, thumbY, sbRight, thumbY + thumbH, 0xFFAAAAAA);
        }

    }

    private static void insertFormatting(EditBox box, char code) {
        insertRaw(box, String.valueOf(ChatFormatting.PREFIX_CODE) + code);
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
