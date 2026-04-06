package me.braydon.chatutilities.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.awt.Desktop;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves {@link ClickEvent}s on text drawn in custom chat HUD windows (same layout as
 * {@link ChatWindowGeometry#compute} + {@link ChatUtilitiesHud}).
 */
public final class ChatWindowClickHandler {
    private ChatWindowClickHandler() {}

    public static boolean tryHandleClick(Minecraft mc, double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }
        // Match {@link ChatWindowHover}: only with chat open (T) and GUI cursor, not grabbed look.
        if (!(mc.screen instanceof ChatScreen) || mc.mouseHandler.isMouseGrabbed()) {
            return false;
        }
        if (ChatUtilitiesManager.get().isPositioning()) {
            return false;
        }
        int mx = (int) mouseX;
        int my = (int) mouseY;
        int gw = mc.getWindow().getGuiScaledWidth();
        int gh = mc.getWindow().getGuiScaledHeight();
        boolean chatOpen = mc.screen instanceof ChatScreen;
        int guiTick = mc.gui.getGuiTicks();

        ChatUtilitiesManager mgr = ChatUtilitiesManager.get();
        List<ChatWindow> ordered = new ArrayList<>(mgr.getActiveProfileWindows());
        for (int i = ordered.size() - 1; i >= 0; i--) {
            ChatWindow window = ordered.get(i);
            if (!window.isVisible() || window.getLines().isEmpty()) {
                continue;
            }
            ChatWindowGeometry geo =
                    ChatWindowGeometry.compute(
                            window,
                            mc,
                            gw,
                            gh,
                            null,
                            guiTick,
                            false,
                            chatOpen,
                            mx,
                            my);
            if (geo.rows.isEmpty()) {
                continue;
            }
            int tx = geo.x + ChatWindowGeometry.padding();
            int ty = geo.y + ChatWindowGeometry.padding() + geo.contentStartYOffset;
            int textRight = geo.x + geo.boxW - ChatWindowGeometry.padding();
            int textBottom = geo.y + geo.boxH - ChatWindowGeometry.padding();
            if (mx < tx || mx >= textRight || my < ty || my >= textBottom) {
                continue;
            }
            int relY = my - ty;
            if (relY < 0) {
                continue;
            }
            int rowIndex = relY / ChatWindowGeometry.lineHeight();
            if (rowIndex < 0 || rowIndex >= geo.rows.size()) {
                continue;
            }
            int relX = mx - tx;
            FormattedCharSequence rowText = geo.rows.get(rowIndex).text;
            Style style = styleAtRelativeX(mc, rowText, relX);
            ClickEvent click = style.getClickEvent();
            if (click == null) {
                continue;
            }
            dispatchClickEvent(mc, click);
            return true;
        }
        return false;
    }

    /** Mirrors vanilla chat click handling (URLs, commands, clipboard) for HUD text. */
    private static void dispatchClickEvent(Minecraft mc, ClickEvent click) {
        switch (click) {
            case ClickEvent.OpenUrl openUrl -> {
                try {
                    URI uri = openUrl.uri();
                    if (uri != null && Desktop.isDesktopSupported()) {
                        Desktop d = Desktop.getDesktop();
                        if (d.isSupported(Desktop.Action.BROWSE)) {
                            d.browse(uri);
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            case ClickEvent.RunCommand run -> {
                String cmd = run.command();
                if (mc.player != null && cmd != null && !cmd.isEmpty()) {
                    String c = cmd.startsWith("/") ? cmd.substring(1) : cmd;
                    mc.player.connection.sendCommand(c);
                }
            }
            case ClickEvent.SuggestCommand suggest -> {
                String cmd = suggest.command();
                if (mc.screen instanceof ChatScreen chat && cmd != null) {
                    chat.insertText(cmd, false);
                }
            }
            case ClickEvent.CopyToClipboard copy -> {
                String t = copy.value();
                if (t != null) {
                    mc.keyboardHandler.setClipboard(t);
                }
            }
            case ClickEvent.ChangePage ignored -> {}
            case ClickEvent.ShowDialog ignored2 -> {}
            default -> {}
        }
    }

    /** Hit-test which {@link Style} applies at {@code relX} pixels along a wrapped row. */
    public static Style styleAtRelativeX(Minecraft mc, FormattedCharSequence line, int relX) {
        Font font = mc.font;
        final int[] cur = {0};
        final Style[] picked = {Style.EMPTY};
        line.accept((index, style, codePoint) -> {
            String ch = new String(Character.toChars(codePoint));
            int w = font.width(Component.literal(ch).withStyle(style));
            if (relX >= cur[0] && relX < cur[0] + w) {
                picked[0] = style;
                return false;
            }
            cur[0] += w;
            return true;
        });
        return picked[0];
    }
}
