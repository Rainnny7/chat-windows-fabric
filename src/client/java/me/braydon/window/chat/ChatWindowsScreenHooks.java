package me.braydon.window.chat;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

import java.util.ArrayList;
import java.util.List;

/** Chat screen: scroll wheel on a hovered custom window scrolls its history (blocks vanilla chat scroll). */
public final class ChatWindowsScreenHooks {
    private static final int SCROLL_LINES_PER_NOTCH = 3;

    private ChatWindowsScreenHooks() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register(
                (client, screen, scaledWidth, scaledHeight) -> {
                    if (!(screen instanceof ChatScreen cs)) {
                        return;
                    }
                    ScreenMouseEvents.allowMouseScroll(cs)
                            .register(
                                    (s, mouseX, mouseY, horizontalAmount, verticalAmount) ->
                                            !consumeScrollForHoveredWindow(mouseX, mouseY, verticalAmount));
                });
    }

    private static boolean consumeScrollForHoveredWindow(
            double mouseX, double mouseY, double verticalAmount) {
        if (verticalAmount == 0.0) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        int gw = mc.getWindow().getGuiScaledWidth();
        int gh = mc.getWindow().getGuiScaledHeight();
        int mx = (int) mouseX;
        int my = (int) mouseY;

        ChatWindowManager mgr = ChatWindowManager.get();
        List<ChatWindow> ordered = new ArrayList<>(mgr.getWindows());
        for (int i = ordered.size() - 1; i >= 0; i--) {
            ChatWindow w = ordered.get(i);
            if (!w.isVisible() || w.getLines().isEmpty()) {
                continue;
            }
            if (!ChatWindowGeometry.historyHitTest(w, gw, gh, mx, my)) {
                continue;
            }
            int[] m = ChatWindowGeometry.historyScrollMetrics(w, mc, gw);
            int total = m[0];
            int viewport = m[1];
            int maxScroll = Math.max(0, total - viewport);
            if (maxScroll <= 0) {
                return false;
            }
            int step = verticalAmount > 0 ? SCROLL_LINES_PER_NOTCH : -SCROLL_LINES_PER_NOTCH;
            w.addHistoryScrollRows(step, total, viewport);
            return true;
        }
        return false;
    }
}
