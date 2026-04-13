package me.braydon.chatutilities.chat;

import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.mojang.blaze3d.platform.Window;
import me.braydon.chatutilities.ChatUtilitiesModClient;
import me.braydon.chatutilities.chat.ChatImagePreviewLayer;
import me.braydon.chatutilities.chat.ChatImagePreviewState;
import me.braydon.chatutilities.chat.ChatImagePreviewUrls;
import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import me.braydon.chatutilities.client.ModChromaClock;
import me.braydon.chatutilities.client.ModUpdateChecker;
import me.braydon.chatutilities.mixin.client.ChatScreenInputAccess;
import net.minecraft.client.gui.components.EditBox;
import me.braydon.chatutilities.gui.ChatUtilitiesRootScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;

public final class ChatUtilitiesTick {
    private static final int SAVE_THROTTLE_TICKS = 40;

    private enum DragKind {
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

    private static boolean wasChatScreenOpen;
    private static boolean escapeWasDown;
    private static int throttle;
    private static boolean wasMouseDown;
    private static DragKind dragKind = DragKind.NONE;
    /** ID of the specific window currently being dragged. */
    private static String dragTargetId = null;
    private static float pressAnchorX;
    private static float pressAnchorY;
    private static int pressMx;
    private static int pressMy;
    private static int pressMaxLines;
    private static int pressBoxW;
    private static int pressBoxLeft;
    private static int pressBoxRight;
    /** Bottom-right: Shift at press time adds text-scale delta on top of normal SE resize (window still moves/resizes). */
    private static boolean dragSeUsesTextScale;
    private static float pressTextScale;
    /** Avoids calling GLFW set cursor every tick when the type is unchanged (reduces flicker). */
    private static CursorType lastAppliedCursor = CursorType.DEFAULT;

    private ChatUtilitiesTick() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ChatUtilitiesTick::onEndTick);
    }

    private static void onEndTick(Minecraft mc) {
        ChatUtilitiesManager mgrAll = ChatUtilitiesManager.get();
        mgrAll.clearLayoutSnapGuides();
        mgrAll.setLayoutAdjustPointerDown(false);
        ModChromaClock.onClientTick();
        ModUpdateChecker.tick(mc);
        ProfileFaviconCache.tick(mc);
        if (mc.screen instanceof ChatScreen && !ChatUtilitiesManager.get().isPositioning()) {
            ChatWindowScrollbarInteraction.clientTick(mc);
        }

        // Keybind to open Chat Utilities menu (checked before everything else)
        if (mc.screen == null && !ChatUtilitiesManager.get().isPositioning()) {
            while (ChatUtilitiesModClient.OPEN_MENU_KEY.consumeClick()) {
                mc.execute(() -> mc.setScreen(new ChatUtilitiesRootScreen(null)));
            }
        }

        boolean chatNow = mc.screen instanceof ChatScreen;
        if (wasChatScreenOpen && !chatNow) {
            for (ChatWindow w : ChatUtilitiesManager.get().getActiveProfileWindows()) {
                w.resetHistoryScroll();
            }
        }
        wasChatScreenOpen = chatNow;

        if (chatNow) {
            ChatImagePreviewLayer.updateHoverStateFromMouse();
        }
        ChatUtilitiesManager mgr = mgrAll;
        boolean allowHudInput =
                !mc.debugEntries.isOverlayVisible()
                        && (mc.screen == null
                                || (mc.screen instanceof ChatScreen && mgr.isPositioning()));
        if (!allowHudInput) {
            escapeWasDown = false;
            wasMouseDown = false;
            dragKind = DragKind.NONE;
            dragTargetId = null;
            dragSeUsesTextScale = false;
            // Do not reset GLFW cursor here: vanilla screens (e.g. Chat Utilities with EditBoxes)
            // set the text caret during render; forcing DEFAULT every tick causes cursor flicker.
            return;
        }

        long handle = mc.getWindow().handle();
        boolean escapeDown = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_ESCAPE) == GLFW.GLFW_PRESS;
        if (escapeDown && !escapeWasDown && mgr.isPositioning()) {
            mgr.clearAllPositioningModes();
            mgr.save();
            mgr.runRestoreScreenAfterPositionIfAny(mc);
            dragKind = DragKind.NONE;
            dragTargetId = null;
            dragSeUsesTextScale = false;
            wasMouseDown = false;
        }
        escapeWasDown = escapeDown;

        // Collect all windows currently in positioning mode
        List<ChatWindow> positionedWindows = new ArrayList<>();
        positionedWindows.addAll(mgr.getPositioningLayoutWindows());
        if (positionedWindows.isEmpty()) {
            if (mc.screen == null || !(mc.screen instanceof ChatScreen)) {
                resetGuiCursor(mc);
            }
            wasMouseDown = false;
            dragKind = DragKind.NONE;
            dragTargetId = null;
            dragSeUsesTextScale = false;
            return;
        }

        Window win = mc.getWindow();
        int gw = win.getGuiScaledWidth();
        int gh = win.getGuiScaledHeight();
        // Match {@link ChatUtilitiesHud}: scaled GUI coordinates (differs from raw GLFW × scale on some setups).
        int mxGui = (int) mc.mouseHandler.getScaledXPos(win);
        int myGui = (int) mc.mouseHandler.getScaledYPos(win);

        boolean down = GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

        if (down) {
            if (!wasMouseDown) {
                // First frame of press: top-most positioned window under the cursor wins
                dragTargetId = null;
                dragKind = DragKind.NONE;
                for (int wi = positionedWindows.size() - 1; wi >= 0; wi--) {
                    ChatWindow w = positionedWindows.get(wi);
                    Component ph = w.getLines().isEmpty() ? Component.literal("[empty]") : null;
                    ChatWindowGeometry geo = ChatWindowGeometry.compute(w, mc, gw, gh, ph, true);
                    DragKind kind = pointerToDrag(ChatWindowGeometry.positioningPointerAt(mxGui, myGui, geo));
                    if (kind != DragKind.NONE) {
                        dragTargetId = w.getId();
                        dragKind = kind;
                        dragSeUsesTextScale =
                                kind == DragKind.RESIZE_SE && shiftHeldForFineResize(mc);
                        if (dragSeUsesTextScale) {
                            pressTextScale = w.getTextScale();
                        }
                        pressAnchorX = w.getAnchorX();
                        pressAnchorY = w.getAnchorY();
                        pressMx = mxGui;
                        pressMy = myGui;
                        pressMaxLines = w.getMaxVisibleLines();
                        pressBoxW = geo.boxW;
                        pressBoxLeft = geo.x;
                        pressBoxRight = geo.x + geo.boxW;
                        break;
                    }
                }
            } else if (dragKind != DragKind.NONE && dragTargetId != null) {
                ChatWindow target = findById(positionedWindows, dragTargetId);
                if (target != null) {
                    applyDrag(
                            target,
                            dragKind,
                            gw,
                            gh,
                            mxGui,
                            myGui,
                            mc,
                            positionedWindows,
                            mgr);
                    if (++throttle >= SAVE_THROTTLE_TICKS) {
                        throttle = 0;
                        mgr.save();
                    }
                }
            }
        } else {
            if (wasMouseDown && dragKind != DragKind.NONE) {
                mgr.save();
            }
            dragKind = DragKind.NONE;
            dragTargetId = null;
            dragSeUsesTextScale = false;
            throttle = 0;
        }

        wasMouseDown = down;
        mgr.setLayoutAdjustPointerDown(down && dragKind != DragKind.NONE && dragTargetId != null);

        // Cursor: reflect current drag or hover state (only when the type changes)
        if (down && dragKind != DragKind.NONE) {
            applyDragCursor(mc, dragKind);
        } else {
            ChatWindowGeometry.PositioningPointer hoverPtr = ChatWindowGeometry.PositioningPointer.NONE;
            for (int wi = positionedWindows.size() - 1; wi >= 0; wi--) {
                ChatWindow w = positionedWindows.get(wi);
                Component ph = w.getLines().isEmpty() ? Component.literal("[empty]") : null;
                ChatWindowGeometry geo = ChatWindowGeometry.compute(w, mc, gw, gh, ph, true);
                ChatWindowGeometry.PositioningPointer ptr = ChatWindowGeometry.positioningPointerAt(mxGui, myGui, geo);
                if (ptr != ChatWindowGeometry.PositioningPointer.NONE) {
                    hoverPtr = ptr;
                    break;
                }
            }
            applyHoverCursor(mc, hoverPtr);
        }
    }

    private static ChatWindow findById(List<ChatWindow> list, String id) {
        for (ChatWindow w : list) {
            if (w.getId().equals(id)) {
                return w;
            }
        }
        return null;
    }

    private static DragKind pointerToDrag(ChatWindowGeometry.PositioningPointer ptr) {
        return switch (ptr) {
            case RESIZE_SE -> DragKind.RESIZE_SE;
            case RESIZE_SW -> DragKind.RESIZE_SW;
            case RESIZE_NE -> DragKind.RESIZE_NE;
            case RESIZE_NW -> DragKind.RESIZE_NW;
            case RESIZE_E -> DragKind.RESIZE_E;
            case RESIZE_W -> DragKind.RESIZE_W;
            case RESIZE_N -> DragKind.RESIZE_N;
            case RESIZE_S -> DragKind.RESIZE_S;
            case MOVE -> DragKind.MOVE;
            default -> DragKind.NONE;
        };
    }

    /**
     * {@link ChatWindowGeometry} places the window top at {@code anchorYGui - boxH} with bottom at {@code anchorYGui}
     * when unclamped; the box must satisfy {@code boxH <= anchorYGui} so the top is not above the screen. This caps
     * {@link ChatWindow#getMaxVisibleLines()} during vertical resize (viewport rows before max(content, viewport)).
     */
    private static boolean shiftHeldForFineResize(Minecraft mc) {
        long wh = mc.getWindow().handle();
        return GLFW.glfwGetKey(wh, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(wh, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private static int maxViewportLinesForAnchor(Minecraft mc, int gh, float anchorYFrac, float textScale) {
        int anchorBottom = Math.round(Mth.clamp(anchorYFrac, 0f, 1f) * gh);
        float ts = Mth.clamp(textScale, ChatWindow.MIN_TEXT_SCALE, ChatWindow.MAX_TEXT_SCALE);
        int lh = Math.max(1, Math.round(ChatWindowGeometry.lineHeight(mc) * ts));
        int chromeV = ChatWindowGeometry.CONTENT_TOP_INSET + 2 * ChatWindowGeometry.padding();
        int usable = anchorBottom - chromeV;
        if (usable < lh * ChatWindow.MIN_VISIBLE_LINES) {
            return ChatWindow.MIN_VISIBLE_LINES;
        }
        int slots = usable / lh;
        return Mth.clamp(slots, ChatWindow.MIN_VISIBLE_LINES, ChatWindow.MAX_VISIBLE_LINES_CAP);
    }

    private static void applyDrag(
            ChatWindow w,
            DragKind kind,
            int gw,
            int gh,
            int mx,
            int my,
            Minecraft mc,
            List<ChatWindow> positionedWindows,
            ChatUtilitiesManager mgr) {
        float ts = Mth.clamp(w.getTextScale(), ChatWindow.MIN_TEXT_SCALE, ChatWindow.MAX_TEXT_SCALE);
        int lh = Math.max(1, Math.round(ChatWindowGeometry.lineHeight(mc) * ts));
        int minWp = Math.round(ChatWindow.MIN_WIDTH_FRAC * gw);
        boolean shiftHeld = shiftHeldForFineResize(mc);
        boolean fineResize = shiftHeld && !(kind == DragKind.RESIZE_SE && dragSeUsesTextScale);
        switch (kind) {
            case MOVE -> {
                float nx = pressAnchorX + (mx - pressMx) / (float) gw;
                float ny = pressAnchorY + (my - pressMy) / (float) gh;
                if (!fineResize) {
                    nx = ChatWindowLayoutSnap.snapMoveAnchorX(w, nx, mc, gw, gh, positionedWindows, w.getId());
                    ny = ChatWindowLayoutSnap.snapMoveAnchorY(w, ny, mc, gw, gh, positionedWindows, w.getId());
                }
                w.setAnchorX(nx);
                w.setAnchorY(ny);
            }
            case RESIZE_E -> {
                int newW = pressBoxW + (mx - pressMx);
                newW = Math.max(minWp, newW);
                if (!fineResize) {
                    newW = ChatWindowLayoutSnap.snapEastResizeWidthPx(
                            pressBoxLeft, newW, gw, positionedWindows, w.getId(), mc, mgr);
                }
                w.setWidthFrac(newW / (float) gw);
                if (!fineResize) {
                    ChatWindowLayoutSnap.snapWidthFracToGrid(w, gw);
                }
            }
            case RESIZE_W -> {
                int newLeft = pressBoxLeft + (mx - pressMx);
                newLeft = Mth.clamp(newLeft, 0, pressBoxRight - minWp);
                int newW = pressBoxRight - newLeft;
                if (!fineResize) {
                    newLeft =
                            ChatWindowLayoutSnap.snapWestResizeLeftPx(
                                    newLeft, newW, pressBoxRight, gw, positionedWindows, w.getId(), mc, mgr);
                    newW = pressBoxRight - newLeft;
                }
                w.setAnchorX(newLeft / (float) gw);
                w.setWidthFrac(newW / (float) gw);
                if (!fineResize) {
                    ChatWindowLayoutSnap.snapLeftAndWidthToGrid(w, gw);
                }
            }
            case RESIZE_N -> {
                int dLines =
                        fineResize
                                ? (pressMy - my) / lh
                                : Math.round((pressMy - my) / (float) lh);
                int maxV = maxViewportLinesForAnchor(mc, gh, pressAnchorY, w.getTextScale());
                int newLines = Mth.clamp(pressMaxLines + dLines, ChatWindow.MIN_VISIBLE_LINES, maxV);
                w.setMaxVisibleLines(newLines);
            }
            case RESIZE_S -> {
                int dLines =
                        fineResize
                                ? (my - pressMy) / lh
                                : Math.round((my - pressMy) / (float) lh);
                float rawAy = pressAnchorY + dLines * lh / (float) gh;
                rawAy = Mth.clamp(rawAy, 0f, 1f);
                int maxV = maxViewportLinesForAnchor(mc, gh, rawAy, w.getTextScale());
                int newLines = Mth.clamp(pressMaxLines + dLines, ChatWindow.MIN_VISIBLE_LINES, maxV);
                float syncedAy = pressAnchorY + (newLines - pressMaxLines) * lh / (float) gh;
                w.setAnchorY(Mth.clamp(syncedAy, 0f, 1f));
                w.setMaxVisibleLines(newLines);
            }
            case RESIZE_NE -> {
                int newW = pressBoxW + (mx - pressMx);
                newW = Math.max(minWp, newW);
                if (!fineResize) {
                    newW = ChatWindowLayoutSnap.snapEastResizeWidthPx(
                            pressBoxLeft, newW, gw, positionedWindows, w.getId(), mc, mgr);
                }
                w.setWidthFrac(newW / (float) gw);
                int dLines =
                        fineResize
                                ? (pressMy - my) / lh
                                : Math.round((pressMy - my) / (float) lh);
                int maxV = maxViewportLinesForAnchor(mc, gh, pressAnchorY, w.getTextScale());
                int newLines = Mth.clamp(pressMaxLines + dLines, ChatWindow.MIN_VISIBLE_LINES, maxV);
                w.setMaxVisibleLines(newLines);
                if (!fineResize) {
                    ChatWindowLayoutSnap.snapWidthFracToGrid(w, gw);
                }
            }
            case RESIZE_NW -> {
                int newLeft = pressBoxLeft + (mx - pressMx);
                newLeft = Mth.clamp(newLeft, 0, pressBoxRight - minWp);
                int newW = pressBoxRight - newLeft;
                if (!fineResize) {
                    newLeft =
                            ChatWindowLayoutSnap.snapWestResizeLeftPx(
                                    newLeft, newW, pressBoxRight, gw, positionedWindows, w.getId(), mc, mgr);
                    newW = pressBoxRight - newLeft;
                }
                w.setAnchorX(newLeft / (float) gw);
                w.setWidthFrac(newW / (float) gw);
                int dLines =
                        fineResize
                                ? (pressMy - my) / lh
                                : Math.round((pressMy - my) / (float) lh);
                int maxV = maxViewportLinesForAnchor(mc, gh, pressAnchorY, w.getTextScale());
                int newLines = Mth.clamp(pressMaxLines + dLines, ChatWindow.MIN_VISIBLE_LINES, maxV);
                w.setMaxVisibleLines(newLines);
                if (!fineResize) {
                    ChatWindowLayoutSnap.snapLeftAndWidthToGrid(w, gw);
                }
            }
            case RESIZE_SE -> {
                int newW = pressBoxW + (mx - pressMx);
                newW = Math.max(minWp, newW);
                if (!fineResize) {
                    newW = ChatWindowLayoutSnap.snapEastResizeWidthPx(
                            pressBoxLeft, newW, gw, positionedWindows, w.getId(), mc, mgr);
                }
                w.setWidthFrac(newW / (float) gw);
                int dLines =
                        fineResize
                                ? (my - pressMy) / lh
                                : Math.round((my - pressMy) / (float) lh);
                float rawAy = pressAnchorY + dLines * lh / (float) gh;
                rawAy = Mth.clamp(rawAy, 0f, 1f);
                int maxV = maxViewportLinesForAnchor(mc, gh, rawAy, w.getTextScale());
                int newLines = Mth.clamp(pressMaxLines + dLines, ChatWindow.MIN_VISIBLE_LINES, maxV);
                float syncedAy = pressAnchorY + (newLines - pressMaxLines) * lh / (float) gh;
                w.setAnchorY(Mth.clamp(syncedAy, 0f, 1f));
                w.setMaxVisibleLines(newLines);
                if (!fineResize) {
                    ChatWindowLayoutSnap.snapWidthFracToGrid(w, gw);
                }
                if (dragSeUsesTextScale) {
                    float delta = (mx - pressMx + my - pressMy) * 0.00235f;
                    float raw = pressTextScale + delta;
                    raw = Mth.clamp(raw, ChatWindow.MIN_TEXT_SCALE, ChatWindow.MAX_TEXT_SCALE);
                    float snapEps = 0.09f;
                    if (Math.abs(raw - ChatWindow.DEFAULT_TEXT_SCALE) < snapEps) {
                        raw = ChatWindow.DEFAULT_TEXT_SCALE;
                    }
                    w.setTextScale(raw);
                }
            }
            case RESIZE_SW -> {
                int newLeft = pressBoxLeft + (mx - pressMx);
                newLeft = Mth.clamp(newLeft, 0, pressBoxRight - minWp);
                int newW = pressBoxRight - newLeft;
                if (!fineResize) {
                    newLeft =
                            ChatWindowLayoutSnap.snapWestResizeLeftPx(
                                    newLeft, newW, pressBoxRight, gw, positionedWindows, w.getId(), mc, mgr);
                    newW = pressBoxRight - newLeft;
                }
                w.setAnchorX(newLeft / (float) gw);
                w.setWidthFrac(newW / (float) gw);
                int dLines =
                        fineResize
                                ? (my - pressMy) / lh
                                : Math.round((my - pressMy) / (float) lh);
                float rawAy = pressAnchorY + dLines * lh / (float) gh;
                rawAy = Mth.clamp(rawAy, 0f, 1f);
                int maxV = maxViewportLinesForAnchor(mc, gh, rawAy, w.getTextScale());
                int newLines = Mth.clamp(pressMaxLines + dLines, ChatWindow.MIN_VISIBLE_LINES, maxV);
                float syncedAy = pressAnchorY + (newLines - pressMaxLines) * lh / (float) gh;
                w.setAnchorY(Mth.clamp(syncedAy, 0f, 1f));
                w.setMaxVisibleLines(newLines);
                if (!fineResize) {
                    ChatWindowLayoutSnap.snapLeftAndWidthToGrid(w, gw);
                }
            }
            default -> {}
        }
    }

    private static void resetGuiCursor(Minecraft mc) {
        lastAppliedCursor = CursorType.DEFAULT;
        mc.getWindow().selectCursor(CursorType.DEFAULT);
    }

    private static void setCursorIfChanged(Minecraft mc, CursorType type) {
        if (type != lastAppliedCursor) {
            lastAppliedCursor = type;
            mc.getWindow().selectCursor(type);
        }
    }

    private static void applyDragCursor(Minecraft mc, DragKind kind) {
        CursorType type =
                switch (kind) {
                    case RESIZE_E, RESIZE_W -> CursorTypes.RESIZE_EW;
                    case RESIZE_N, RESIZE_S -> CursorTypes.RESIZE_NS;
                    case RESIZE_NE, RESIZE_NW, RESIZE_SE, RESIZE_SW -> CursorTypes.RESIZE_ALL;
                    case MOVE -> CursorTypes.ARROW;
                    default -> CursorType.DEFAULT;
                };
        setCursorIfChanged(mc, type);
    }

    private static void applyHoverCursor(Minecraft mc, ChatWindowGeometry.PositioningPointer ptr) {
        CursorType type =
                switch (ptr) {
                    case RESIZE_E, RESIZE_W -> CursorTypes.RESIZE_EW;
                    case RESIZE_N, RESIZE_S -> CursorTypes.RESIZE_NS;
                    case RESIZE_NE, RESIZE_NW, RESIZE_SE, RESIZE_SW -> CursorTypes.RESIZE_ALL;
                    case MOVE -> CursorTypes.ARROW;
                    default -> CursorType.DEFAULT;
                };
        setCursorIfChanged(mc, type);
    }
}
