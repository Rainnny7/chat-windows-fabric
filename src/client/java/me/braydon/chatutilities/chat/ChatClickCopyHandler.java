package me.braydon.chatutilities.chat;

import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Ctrl+click (configurable) copy for custom chat windows and vanilla chat while the chat screen is open.
 */
public final class ChatClickCopyHandler {
    private ChatClickCopyHandler() {}

    public static boolean tryHandleCopyClick(
            Minecraft mc, ChatScreen ignoredScreen, double mouseX, double mouseY, int button) {
        if (!ChatUtilitiesClientOptions.isClickToCopyEnabled()) {
            return false;
        }
        if (mc.mouseHandler.isMouseGrabbed()) {
            return false;
        }
        if (ChatUtilitiesManager.get().isPositioning()) {
            return false;
        }

        long win = mc.getWindow().handle();
        boolean control =
                GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        boolean shift =
                GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        boolean alt =
                GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                        || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;

        ChatUtilitiesClientOptions.ClickMouseBinding formatted =
                ChatUtilitiesClientOptions.getCopyFormattedBinding();
        ChatUtilitiesClientOptions.ClickMouseBinding plain =
                ChatUtilitiesClientOptions.getCopyPlainBinding();

        boolean wantFormatted = formatted.matches(button, control, shift, alt);
        boolean wantPlain = plain.matches(button, control, shift, alt);
        if (wantFormatted && wantPlain) {
            wantPlain = false;
        }
        if (!wantFormatted && !wantPlain) {
            return false;
        }

        int mx = (int) mouseX;
        int my = (int) mouseY;
        int gw = mc.getWindow().getGuiScaledWidth();
        int gh = mc.getWindow().getGuiScaledHeight();
        int guiTick = mc.gui.getGuiTicks();

        Optional<Component> fromWindow = tryCopyFromTopWindow(mc, gw, gh, guiTick, mx, my);
        Optional<Component> message = fromWindow.or(() -> tryCopyFromVanillaChat(mc, mx, my));

        if (message.isEmpty()) {
            return false;
        }

        Component comp = message.get();
        String clip;
        if (wantFormatted) {
            clip =
                    switch (ChatUtilitiesClientOptions.getCopyFormattedStyle()) {
                        case SECTION_SYMBOL -> ChatCopyTextHelper.toLegacySectionString(comp);
                        case MINIMESSAGE -> ChatCopyTextHelper.toMiniMessageString(comp);
                    };
        } else {
            clip = ChatCopyTextHelper.plainForClipboard(comp);
        }
        mc.keyboardHandler.setClipboard(clip);
        showCopyToast(mc);
        return true;
    }

    private static Optional<Component> tryCopyFromTopWindow(
            Minecraft mc, int gw, int gh, int guiTick, int mx, int my) {
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
                            true,
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
            Optional<ChatWindowLine> src =
                    ChatWindowGeometry.sourceLineForRow(
                            window, mc, gw, geo, guiTick, false, true, rowIndex);
            if (src.isPresent()) {
                return Optional.of(src.get().styled());
            }
        }
        return Optional.empty();
    }

    private static Optional<Component> tryCopyFromVanillaChat(Minecraft mc, int mx, int my) {
        return VanillaChatLinePicker.pickLineAt(mc, mx, my);
    }

    private static void showCopyToast(Minecraft mc) {
        SystemToast.add(
                mc.getToastManager(),
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.translatable("chat-utilities.toast.copy_chat.title"),
                Component.translatable("chat-utilities.toast.copy_chat.detail"));
    }
}
