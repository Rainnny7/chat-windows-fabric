package me.braydon.chatutilities.chat;

import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.system.MemoryStack;

import java.nio.DoubleBuffer;

public final class ChatUtilitiesTick {
    private static final int SAVE_THROTTLE_TICKS = 40;

    private enum DragKind {
        NONE,
        MOVE,
        RESIZE_E,
        RESIZE_N,
        RESIZE_NE
    }

    private static boolean wasChatScreenOpen;
    private static boolean escapeWasDown;
    private static int throttle;
    private static boolean wasMouseDown;
    private static DragKind dragKind = DragKind.NONE;
    private static float pressAnchorX;
    private static float pressAnchorY;
    private static int pressMx;
    private static int pressMy;
    private static int pressMaxLines;
    private static int pressBoxW;

    private ChatUtilitiesTick() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ChatUtilitiesTick::onEndTick);
    }

    private static void onEndTick(Minecraft mc) {
        ProfileFaviconCache.tick(mc);

        boolean chatNow = mc.screen instanceof ChatScreen;
        if (wasChatScreenOpen && !chatNow) {
            for (ChatWindow w : ChatUtilitiesManager.get().getActiveProfileWindows()) {
                w.resetHistoryScroll();
            }
        }
        wasChatScreenOpen = chatNow;

        ChatUtilitiesManager mgr = ChatUtilitiesManager.get();
        boolean allowHudInput =
                mc.screen == null || (mc.screen instanceof ChatScreen && mgr.isPositioning());
        if (!allowHudInput) {
            escapeWasDown = false;
            wasMouseDown = false;
            dragKind = DragKind.NONE;
            return;
        }

        long handle = mc.getWindow().handle();
        boolean escapeDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;
        if (escapeDown && !escapeWasDown && mgr.isPositioning()) {
            mgr.clearAllPositioningModes();
            mgr.save();
            mgr.runRestoreScreenAfterPositionIfAny(mc);
            dragKind = DragKind.NONE;
            wasMouseDown = false;
        }
        escapeWasDown = escapeDown;

        ChatWindow positioned = null;
        for (ChatWindow w : mgr.getActiveProfileWindows()) {
            if (w.isPositioningMode()) {
                positioned = w;
                break;
            }
        }
        if (positioned == null) {
            resetGuiCursor(mc);
            wasMouseDown = false;
            dragKind = DragKind.NONE;
            return;
        }

        int gw = mc.getWindow().getGuiScaledWidth();
        int gh = mc.getWindow().getGuiScaledHeight();
        int sw = mc.getWindow().getScreenWidth();
        int sh = mc.getWindow().getScreenHeight();

        double mxFb;
        double myFb;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            DoubleBuffer xb = stack.mallocDouble(1);
            DoubleBuffer yb = stack.mallocDouble(1);
            GLFW.glfwGetCursorPos(handle, xb, yb);
            mxFb = xb.get(0);
            myFb = yb.get(0);
        }

        int mxGui = sw > 0 ? (int) Math.round(mxFb * gw / (double) sw) : 0;
        int myGui = sh > 0 ? (int) Math.round(myFb * gh / (double) sh) : 0;

        boolean down = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        Component ph = positioned.getLines().isEmpty() ? Component.literal("[empty]") : null;
        ChatWindowGeometry geo = ChatWindowGeometry.compute(positioned, mc, gw, gh, ph);

        if (down) {
            if (!wasMouseDown) {
                dragKind =
                        switch (ChatWindowGeometry.positioningPointerAt(mxGui, myGui, geo)) {
                            case RESIZE_NE -> DragKind.RESIZE_NE;
                            case RESIZE_E -> DragKind.RESIZE_E;
                            case RESIZE_N -> DragKind.RESIZE_N;
                            case MOVE -> DragKind.MOVE;
                            default -> DragKind.NONE;
                        };

                pressAnchorX = positioned.getAnchorX();
                pressAnchorY = positioned.getAnchorY();
                pressMx = mxGui;
                pressMy = myGui;
                pressMaxLines = positioned.getMaxVisibleLines();
                pressBoxW = geo.boxW;
            } else if (dragKind != DragKind.NONE) {
                switch (dragKind) {
                    case MOVE -> {
                        positioned.setAnchorX(pressAnchorX + (mxGui - pressMx) / (float) gw);
                        positioned.setAnchorY(pressAnchorY + (myGui - pressMy) / (float) gh);
                    }
                    case RESIZE_E -> {
                        int newW = pressBoxW + (mxGui - pressMx);
                        positioned.setWidthFrac(newW / (float) gw);
                    }
                    case RESIZE_N -> {
                        int dLines = Math.round((pressMy - myGui) / (float) ChatWindowGeometry.lineHeight());
                        positioned.setMaxVisibleLines(pressMaxLines + dLines);
                    }
                    case RESIZE_NE -> {
                        int newW = pressBoxW + (mxGui - pressMx);
                        positioned.setWidthFrac(newW / (float) gw);
                        int dLines = Math.round((pressMy - myGui) / (float) ChatWindowGeometry.lineHeight());
                        positioned.setMaxVisibleLines(pressMaxLines + dLines);
                    }
                    default -> {}
                }
                if (++throttle >= SAVE_THROTTLE_TICKS) {
                    throttle = 0;
                    mgr.save();
                }
            }
        } else {
            if (wasMouseDown && dragKind != DragKind.NONE) {
                mgr.save();
            }
            dragKind = DragKind.NONE;
            throttle = 0;
        }

        wasMouseDown = down;

        applyPositioningCursor(mc, geo, mxGui, myGui, down);
    }

    private static void resetGuiCursor(Minecraft mc) {
        mc.getWindow().selectCursor(CursorType.DEFAULT);
    }

    private static void applyPositioningCursor(
            Minecraft mc, ChatWindowGeometry geo, int mxGui, int myGui, boolean mouseDown) {
        CursorType type;
        if (mouseDown && dragKind != DragKind.NONE) {
            type =
                    switch (dragKind) {
                        case RESIZE_E -> CursorTypes.RESIZE_EW;
                        case RESIZE_N -> CursorTypes.RESIZE_NS;
                        case RESIZE_NE -> CursorTypes.RESIZE_ALL;
                        case MOVE -> CursorTypes.ARROW;
                        default -> CursorType.DEFAULT;
                    };
        } else {
            type =
                    switch (ChatWindowGeometry.positioningPointerAt(mxGui, myGui, geo)) {
                        case RESIZE_E -> CursorTypes.RESIZE_EW;
                        case RESIZE_N -> CursorTypes.RESIZE_NS;
                        case RESIZE_NE -> CursorTypes.RESIZE_ALL;
                        case MOVE -> CursorTypes.ARROW;
                        default -> CursorType.DEFAULT;
                    };
        }
        mc.getWindow().selectCursor(type);
    }
}
