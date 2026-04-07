package me.braydon.chatutilities.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Draws the chat symbol chip and palette after {@link net.minecraft.client.gui.screens.ChatScreen#render}
 * returns (Fabric {@link net.fabricmc.fabric.api.client.screen.v1.ScreenEvents#afterRender}), so they appear
 * above other mods that inject into the same screen render. Pause/options/F3 use different screens or layers
 * and are unaffected.
 */
public final class ChatSymbolPaletteLayer {
    private static @Nullable ChatSymbolPalette palette;
    private static boolean chipVisible;
    private static int chipX;
    private static int chipY;
    private static int chipW;
    private static int chipH;
    private static boolean menuChipVisible;
    private static int menuChipX;
    private static int menuChipY;
    private static int menuChipW;
    private static int menuChipH;

    private ChatSymbolPaletteLayer() {}

    /** Called from {@code ChatScreen} render tail each frame before Fabric {@code afterRender}. */
    public static void prepare(
            ChatSymbolPalette p,
            boolean showChip,
            int cx,
            int cy,
            int cw,
            int ch,
            boolean showMenuChip,
            int mx,
            int my,
            int mw,
            int mh) {
        palette = p;
        chipVisible = showChip;
        chipX = cx;
        chipY = cy;
        chipW = cw;
        chipH = ch;
        menuChipVisible = showMenuChip;
        menuChipX = mx;
        menuChipY = my;
        menuChipW = mw;
        menuChipH = mh;
    }

    private static void drawHudChip(
            GuiGraphics graphics, Font font, int x, int y, int w, int h, int mouseX, int mouseY, Component glyph) {
        boolean hovered =
                mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int bg = hovered ? ChatSymbolPalette.HUD_CHIP_FILL_HOVER : ChatSymbolPalette.HUD_CHIP_FILL;
        graphics.fill(x, y, x + w, y + h, bg);
        graphics.renderOutline(x, y, w, h, ChatSymbolPalette.HUD_CHIP_EDGE);
        int tw = font.width(glyph);
        int th = font.lineHeight;
        graphics.drawString(
                font, glyph, x + (w - tw) / 2, y + (h - th) / 2, ChatSymbolPalette.HUD_CHIP_TEXT, false);
    }

    public static void render(GuiGraphics graphics, Font font, int screenW, int screenH, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (chipVisible && chipW > 0) {
            drawHudChip(
                    graphics,
                    font,
                    chipX,
                    chipY,
                    chipW,
                    chipH,
                    mouseX,
                    mouseY,
                    Component.literal("\u263A"));
        }
        if (menuChipVisible && menuChipW > 0) {
            drawHudChip(
                    graphics,
                    font,
                    menuChipX,
                    menuChipY,
                    menuChipW,
                    menuChipH,
                    mouseX,
                    mouseY,
                    Component.literal("\u2699"));
        }
        if (palette != null) {
            palette.render(graphics, font, screenW, screenH, mouseX, mouseY);
        }
        ChatUtilitiesHud.renderPositioningOverChatScreen(graphics, mc.getDeltaTracker());
    }
}
