package me.braydon.chatutilities.chat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Resolves hover tooltips for text in custom chat HUD windows (same layout as
 * {@link ChatWindowClickHandler}).
 */
public final class ChatWindowHover {
    private ChatWindowHover() {}

    public static Optional<Component> hoverTooltipAt(Minecraft mc, int mx, int my) {
        // Same as vanilla hover text: only with chat open (T) and a visible GUI cursor — not while
        // playing with the mouse grabbed for look/camera.
        if (!(mc.screen instanceof ChatScreen) || mc.mouseHandler.isMouseGrabbed()) {
            return Optional.empty();
        }
        if (ChatUtilitiesManager.get().isPositioning()) {
            return Optional.empty();
        }
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
            Style style = ChatWindowClickHandler.styleAtRelativeX(mc, rowText, relX);
            HoverEvent hover = style.getHoverEvent();
            if (hover == null) {
                continue;
            }
            if (hover instanceof HoverEvent.ShowText showText) {
                return Optional.of(showText.value());
            }
        }
        return Optional.empty();
    }
}
