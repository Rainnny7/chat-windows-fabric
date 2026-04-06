package me.braydon.chatutilities.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Title / content / footer layout similar to the multiplayer screen. */
public final class ChatUtilitiesScreenLayout {
    /** ARGB with full opacity — use for GUI text ({@code 0xFFFFFF} alone is alpha 0 in Java). */
    public static final int TEXT_WHITE = 0xFFFFFFFF;
    public static final int TEXT_GRAY = 0xFFA0A0A0;
    public static final int TEXT_GRAY_DARK = 0xFF808080;
    public static final int TEXT_LABEL = 0xFFC8C8C8;

    public static final int TITLE_Y = 32;
    public static final int FOOTER_PRIMARY_Y_OFFSET = 28;
    public static final int FOOTER_SECONDARY_Y_OFFSET = 52;
    public static final int CONTENT_TOP = 56;
    public static final int LIST_MAX_WIDTH = 350;
    public static final int LIST_SIDE_MARGIN = 25;

    private ChatUtilitiesScreenLayout() {}

    public static int listWidth(Screen screen) {
        return Math.min(LIST_MAX_WIDTH, screen.width - LIST_SIDE_MARGIN * 2);
    }

    public static int listLeft(Screen screen) {
        return (screen.width - listWidth(screen)) / 2;
    }

    public static int footerPrimaryY(Screen screen) {
        return screen.height - FOOTER_PRIMARY_Y_OFFSET;
    }

    public static int footerSecondaryY(Screen screen) {
        return screen.height - FOOTER_SECONDARY_Y_OFFSET;
    }

    /** Space above the footer row(s); use for scrollable / growing content. */
    public static int contentBottomReserved(Screen screen) {
        return screen.height - FOOTER_SECONDARY_Y_OFFSET - 8;
    }

    /** One horizontal footer row (e.g. root profile list). */
    public static int footerRowY(Screen screen) {
        return screen.height - FOOTER_PRIMARY_Y_OFFSET;
    }

    /** Reserve space when the screen uses only {@link #footerRowY}. */
    public static int contentBottomReservedSingleFooter(Screen screen) {
        return screen.height - FOOTER_PRIMARY_Y_OFFSET - 8;
    }

    /**
     * Draws wrapped text centered on {@code centerX} (each line is centered; {@code maxWidth} is the wrap
     * width). Returns the Y coordinate just below the last line.
     */
    public static int drawCenteredWrapped(
            Font font,
            GuiGraphics graphics,
            Component text,
            int centerX,
            int startY,
            int maxWidth,
            int color,
            int lineSpacing) {
        List<FormattedCharSequence> lines = font.split(text, maxWidth);
        int y = startY;
        for (FormattedCharSequence line : lines) {
            int lineW = font.width(line);
            graphics.drawString(font, line, centerX - lineW / 2, y, color, false);
            y += lineSpacing;
        }
        return y;
    }

    /** Close the whole Chat Utilities flow (back to whatever opened Server Profiles). */
    public static void closeEntireChatUtilitiesMenu(ChatUtilitiesRootScreen chatRoot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.setScreen(chatRoot.getParentScreen());
        }
    }
}
