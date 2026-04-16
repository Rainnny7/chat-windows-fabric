package me.braydon.chatutilities.chat;

import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Vanilla chat search row affordances: Jump button hit targets and draw helpers. */
public final class ChatSearchOverlay {
    private static final int MARGIN = 6;
    private static final int JUMP_FILL = 0xF0282828;
    private static final int JUMP_EDGE = 0xFFC8C8C8;
    /** Full ARGB; {@code 0xFFFFFF} alone can render as fully transparent for GUI text. */
    private static final int JUMP_TEXT = 0xFFFFFFFF;
    private static final Component JUMP_LABEL = Component.translatable("chat-utilities.chat_search.jump");

    private static int vanillaJumpX;
    private static int vanillaJumpY;
    private static int vanillaJumpW;
    private static int vanillaJumpH;
    private static GuiMessage.Line vanillaJumpLine;

    /** Previous frame’s Jump rect so the cursor can move onto the label without losing the target line. */
    private static int vanillaJumpPersistX;

    private static int vanillaJumpPersistY;
    private static int vanillaJumpPersistW;
    private static int vanillaJumpPersistH;
    private static GuiMessage.Line vanillaStickyLine;

    private static int hudJumpX;
    private static int hudJumpY;
    private static int hudJumpW;
    private static int hudJumpH;
    private static ChatWindow hudJumpWindow;
    private static ChatWindowLine hudJumpLine;

    private static int hudJumpPersistX;
    private static int hudJumpPersistY;
    private static int hudJumpPersistW;
    private static int hudJumpPersistH;
    private static ChatWindow hudStickyWindow;
    private static ChatWindowLine hudStickyLine;

    private ChatSearchOverlay() {}

    /** Clears filter-driven Jump UI (call when the search query is cleared). */
    public static void resetJumpInteraction() {
        vanillaJumpW = 0;
        vanillaJumpLine = null;
        vanillaStickyLine = null;
        vanillaJumpPersistW = 0;
        hudJumpW = 0;
        hudJumpWindow = null;
        hudJumpLine = null;
        hudStickyWindow = null;
        hudStickyLine = null;
        hudJumpPersistW = 0;
    }

    public static void clearVanillaJump() {
        vanillaJumpW = 0;
        vanillaJumpLine = null;
    }

    public static void clearHudJump() {
        hudJumpW = 0;
        hudJumpWindow = null;
        hudJumpLine = null;
    }

    public static void renderVanillaJump(
            Minecraft mc, GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY, int screenW, int screenH) {
        if (!ChatUtilitiesClientOptions.isChatSearchBarEnabled() || !ChatSearchState.isFiltering()) {
            resetJumpInteraction();
            return;
        }
        if (!(mc.screen instanceof ChatScreen)) {
            return;
        }
        int w = font.width(JUMP_LABEL) + 8;
        int h = font.lineHeight + 4;
        int gap = 4;
        int textR = VanillaChatOverlayBounds.textColumnRightExcl(mc);
        int textL = VanillaChatOverlayBounds.textColumnLeftIncl(mc);

        boolean onPersistJump =
                vanillaJumpPersistW > 0
                        && mouseX >= vanillaJumpPersistX
                        && mouseX < vanillaJumpPersistX + vanillaJumpPersistW
                        && mouseY >= vanillaJumpPersistY
                        && mouseY < vanillaJumpPersistY + vanillaJumpPersistH;

        boolean onStickyRowBand =
                vanillaStickyLine != null
                        && VanillaChatLinePicker.guiScreenMouseInVerticalBandForGuiLine(
                                mc, vanillaStickyLine, mouseX, mouseY, screenW, screenH);

        Optional<GuiMessage.Line> pick = VanillaChatLinePicker.pickGuiLineAt(mc, mouseX, mouseY);
        if (pick.isPresent()
                && VanillaChatLinePicker.vanillaTrimmedLineMatchesOpenChatSearch(mc.gui.getChat(), pick.get())) {
            vanillaStickyLine = pick.get();
        } else if (!onPersistJump && !onStickyRowBand) {
            vanillaStickyLine = null;
        }

        if (vanillaStickyLine == null) {
            vanillaJumpW = 0;
            vanillaJumpLine = null;
            vanillaJumpPersistW = 0;
            return;
        }

        Optional<Float> centerY = VanillaChatLinePicker.guiScreenCenterYForGuiLine(mc, vanillaStickyLine);
        if (centerY.isEmpty()) {
            vanillaJumpW = 0;
            vanillaJumpLine = null;
            vanillaJumpPersistW = 0;
            return;
        }

        int y =
                Mth.clamp(
                        Math.round(centerY.get()) - h / 2,
                        MARGIN,
                        Math.max(MARGIN, screenH - h - MARGIN));

        int x = textR + gap;
        boolean placed = false;
        if (x + w + MARGIN <= screenW && !vanillaJumpRectBlocked(mc, x, y, w, h, screenW, screenH)) {
            placed = true;
        } else {
            x = textL - gap - w;
            if (x >= MARGIN && !vanillaJumpRectBlocked(mc, x, y, w, h, screenW, screenH)) {
                placed = true;
            }
        }
        if (!placed) {
            vanillaJumpW = 0;
            vanillaJumpLine = null;
            vanillaJumpPersistW = 0;
            return;
        }
        graphics.fill(x, y, x + w, y + h, JUMP_FILL);
        graphics.outline(x, y, w, h, JUMP_EDGE);
        graphics.text(
                font,
                JUMP_LABEL,
                x + 4,
                y + 2,
                JUMP_TEXT,
                ChatUtilitiesClientOptions.isChatTextShadow());
        vanillaJumpX = x;
        vanillaJumpY = y;
        vanillaJumpW = w;
        vanillaJumpH = h;
        vanillaJumpLine = vanillaStickyLine;
        vanillaJumpPersistX = x;
        vanillaJumpPersistY = y;
        vanillaJumpPersistW = w;
        vanillaJumpPersistH = h;
    }

    private static boolean vanillaJumpRectBlocked(
            Minecraft mc, int x, int y, int w, int h, int screenW, int screenH) {
        int x1 = x + w;
        int y1 = y + h;
        int gw = screenW;
        int gh = screenH;
        int tick = mc.gui.getGuiTicks();
        for (ChatWindow win : ChatUtilitiesManager.get().getHudChatWindows()) {
            if (!win.isVisible()) {
                continue;
            }
            ChatWindowGeometry geo =
                    ChatWindowGeometry.compute(win, mc, gw, gh, null, tick, false, true, 0, 0, false, false);
            if (rectsOverlap(x, y, x1, y1, geo.x, geo.y, geo.x + geo.boxW, geo.y + geo.boxH)) {
                return true;
            }
        }
        return false;
    }

    private static boolean rectsOverlap(int ax0, int ay0, int ax1, int ay1, int bx0, int by0, int bx1, int by1) {
        return ax0 < bx1 && ax1 > bx0 && ay0 < by1 && ay1 > by0;
    }

    /**
     * Picks the topmost HUD window under the cursor and draws Jump when the hovered row matches the active search
     * filter. Persists the Jump chip while the cursor moves from the row onto the label.
     */
    public static void renderHudJumpTopmost(
            Minecraft mc,
            GuiGraphicsExtractor graphics,
            int gw,
            int gh,
            int mx,
            int my,
            int guiTick,
            float chatOpacity) {
        if (!ChatUtilitiesClientOptions.isChatSearchBarEnabled() || !ChatSearchState.isFiltering()) {
            resetJumpInteraction();
            return;
        }
        if (!(mc.screen instanceof ChatScreen) || mc.mouseHandler.isMouseGrabbed()) {
            return;
        }
        ChatUtilitiesManager mgr = ChatUtilitiesManager.get();
        if (mgr.isPositioning()) {
            return;
        }

        Font font = mc.font;
        int labelW = font.width(JUMP_LABEL) + 8;
        int labelH = font.lineHeight + 4;
        int gap = 4;

        boolean onPersistHud =
                hudJumpPersistW > 0
                        && mx >= hudJumpPersistX
                        && mx < hudJumpPersistX + hudJumpPersistW
                        && my >= hudJumpPersistY
                        && my < hudJumpPersistY + hudJumpPersistH;

        boolean onHudStickyRowBand = mouseInHudStickyContentRowBand(mc, gw, gh, mx, my, guiTick);

        if ((onPersistHud || onHudStickyRowBand)
                && hudStickyWindow != null
                && hudStickyLine != null) {
            if (tryDrawHudJumpForSticky(mc, graphics, gw, gh, guiTick, chatOpacity, font, labelW, labelH, gap)) {
                return;
            }
        }

        List<ChatWindow> ordered = new ArrayList<>(mgr.getHudChatWindows());
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
                            my,
                            false,
                            false);
            if (geo.rows.isEmpty()) {
                continue;
            }
            int tx = geo.x + ChatWindowGeometry.padding();
            int ty =
                    geo.y
                            + ChatWindowGeometry.padding()
                            + geo.contentTopInsetPx
                            + geo.contentStartYOffset;
            int textRight = geo.x + geo.boxW - ChatWindowGeometry.padding();
            int textBottom = geo.y + geo.boxH - ChatWindowGeometry.padding();
            if (mx < tx || mx >= textRight || my < ty || my >= textBottom) {
                continue;
            }
            int relY = my - ty;
            int rowIndex = ChatWindowGeometry.rowIndexForContentRelY(geo, relY);
            if (rowIndex < 0 || rowIndex >= geo.rows.size()) {
                continue;
            }
            Optional<ChatWindowLine> src =
                    ChatWindowGeometry.sourceLineForRow(
                            window, mc, gw, geo, guiTick, false, true, rowIndex);
            if (src.isEmpty()) {
                continue;
            }
            if (!ChatSearchState.matchesComponent(src.get().styled())) {
                continue;
            }
            int lh = geo.lineHeight();
            int tyRow = ty;
            for (int r = 0; r < rowIndex; r++) {
                tyRow += lh;
            }
            ChatWindowGeometry.RenderedRow rr = geo.rows.get(rowIndex);
            int drawRowTop = tyRow + rr.slideYOffset;

            int boxR = geo.x + geo.boxW;
            int jumpY = Mth.clamp(drawRowTop + (lh - labelH) / 2, MARGIN, gh - labelH - MARGIN);
            int jumpX = boxR + gap;
            boolean placed = false;
            if (jumpX + labelW + MARGIN <= gw && !vanillaJumpRectBlocked(mc, jumpX, jumpY, labelW, labelH, gw, gh)) {
                placed = true;
            } else {
                jumpX = geo.x - gap - labelW;
                if (jumpX >= MARGIN && !vanillaJumpRectBlocked(mc, jumpX, jumpY, labelW, labelH, gw, gh)) {
                    placed = true;
                }
            }
            if (!placed) {
                continue;
            }
            float a = Mth.clamp(rr.alpha * chatOpacity, 0f, 1f);
            int fill = (Math.round(a * 240f) << 24) | (JUMP_FILL & 0xFFFFFF);
            int edge = (Math.round(a * 255f) << 24) | (JUMP_EDGE & 0xFFFFFF);
            int textRgb = ChatWindowGeometry.argbText(a, 0xFFFFFF);
            graphics.fill(jumpX, jumpY, jumpX + labelW, jumpY + labelH, fill);
            graphics.outline(jumpX, jumpY, labelW, labelH, edge);
            graphics.text(
                    font, JUMP_LABEL, jumpX + 4, jumpY + 2, textRgb, ChatUtilitiesClientOptions.isChatTextShadow());
            hudJumpX = jumpX;
            hudJumpY = jumpY;
            hudJumpW = labelW;
            hudJumpH = labelH;
            hudJumpWindow = window;
            hudJumpLine = src.get();
            hudStickyWindow = window;
            hudStickyLine = src.get();
            hudJumpPersistX = jumpX;
            hudJumpPersistY = jumpY;
            hudJumpPersistW = labelW;
            hudJumpPersistH = labelH;
            return;
        }
        hudJumpW = 0;
        hudJumpWindow = null;
        hudJumpLine = null;
        if (!onPersistHud && !onHudStickyRowBand) {
            hudStickyWindow = null;
            hudStickyLine = null;
            hudJumpPersistW = 0;
        }
    }

    /**
     * Full-width (gui) horizontal strip for the HUD row of {@link #hudStickyLine} so the cursor can move from the
     * message to Jump without leaving “the line”.
     */
    private static boolean mouseInHudStickyContentRowBand(
            Minecraft mc, int gw, int gh, int mx, int my, int guiTick) {
        ChatWindow window = hudStickyWindow;
        ChatWindowLine line = hudStickyLine;
        if (window == null || line == null) {
            return false;
        }
        ChatWindowGeometry geo =
                ChatWindowGeometry.compute(
                        window, mc, gw, gh, null, guiTick, false, true, gw / 2, gh / 2, false, false);
        if (geo.rows.isEmpty()) {
            return false;
        }
        int rowIndex = ChatWindowGeometry.rowIndexForSourceLine(window, mc, gw, geo, guiTick, line);
        if (rowIndex < 0) {
            return false;
        }
        int ty =
                geo.y
                        + ChatWindowGeometry.padding()
                        + geo.contentTopInsetPx
                        + geo.contentStartYOffset;
        int lh = geo.lineHeight();
        int tyRow = ty;
        for (int r = 0; r < rowIndex; r++) {
            tyRow += lh;
        }
        ChatWindowGeometry.RenderedRow rr = geo.rows.get(rowIndex);
        int drawRowTop = tyRow + rr.slideYOffset;
        int rowBottom = drawRowTop + lh;
        int padX = 56;
        int padY = 4;
        return my >= drawRowTop - padY
                && my < rowBottom + padY
                && mx >= geo.x - padX
                && mx < geo.x + geo.boxW + padX;
    }

    private static boolean tryDrawHudJumpForSticky(
            Minecraft mc,
            GuiGraphicsExtractor graphics,
            int gw,
            int gh,
            int guiTick,
            float chatOpacity,
            Font font,
            int labelW,
            int labelH,
            int gap) {
        ChatWindow window = hudStickyWindow;
        ChatWindowLine line = hudStickyLine;
        if (window == null || line == null) {
            return false;
        }
        ChatWindowGeometry geo =
                ChatWindowGeometry.compute(
                        window, mc, gw, gh, null, guiTick, false, true, gw / 2, gh / 2, false, false);
        if (geo.rows.isEmpty()) {
            return false;
        }
        int rowIndex = ChatWindowGeometry.rowIndexForSourceLine(window, mc, gw, geo, guiTick, line);
        if (rowIndex < 0) {
            return false;
        }
        int tx = geo.x + ChatWindowGeometry.padding();
        int ty =
                geo.y
                        + ChatWindowGeometry.padding()
                        + geo.contentTopInsetPx
                        + geo.contentStartYOffset;
        int lh = geo.lineHeight();
        int tyRow = ty;
        for (int r = 0; r < rowIndex; r++) {
            tyRow += lh;
        }
        ChatWindowGeometry.RenderedRow rr = geo.rows.get(rowIndex);
        int drawRowTop = tyRow + rr.slideYOffset;
        int boxR = geo.x + geo.boxW;
        int jumpY = Mth.clamp(drawRowTop + (lh - labelH) / 2, MARGIN, gh - labelH - MARGIN);
        int jumpX = boxR + gap;
        boolean placed = false;
        if (jumpX + labelW + MARGIN <= gw && !vanillaJumpRectBlocked(mc, jumpX, jumpY, labelW, labelH, gw, gh)) {
            placed = true;
        } else {
            jumpX = geo.x - gap - labelW;
            if (jumpX >= MARGIN && !vanillaJumpRectBlocked(mc, jumpX, jumpY, labelW, labelH, gw, gh)) {
                placed = true;
            }
        }
        if (!placed) {
            return false;
        }
        float a = Mth.clamp(rr.alpha * chatOpacity, 0f, 1f);
        int fill = (Math.round(a * 240f) << 24) | (JUMP_FILL & 0xFFFFFF);
        int edge = (Math.round(a * 255f) << 24) | (JUMP_EDGE & 0xFFFFFF);
        int textRgb = ChatWindowGeometry.argbText(a, 0xFFFFFF);
        graphics.fill(jumpX, jumpY, jumpX + labelW, jumpY + labelH, fill);
        graphics.outline(jumpX, jumpY, labelW, labelH, edge);
        graphics.text(
                font, JUMP_LABEL, jumpX + 4, jumpY + 2, textRgb, ChatUtilitiesClientOptions.isChatTextShadow());
        hudJumpX = jumpX;
        hudJumpY = jumpY;
        hudJumpW = labelW;
        hudJumpH = labelH;
        hudJumpWindow = window;
        hudJumpLine = line;
        hudJumpPersistX = jumpX;
        hudJumpPersistY = jumpY;
        hudJumpPersistW = labelW;
        hudJumpPersistH = labelH;
        return true;
    }

    public static boolean tryConsumeJumpClick(Minecraft mc, EditBox searchField, double mx, double my) {
        if (!ChatUtilitiesClientOptions.isChatSearchBarEnabled()) {
            return false;
        }
        int ix = (int) mx;
        int iy = (int) my;
        if (hudJumpW > 0
                && ix >= hudJumpX
                && ix < hudJumpX + hudJumpW
                && iy >= hudJumpY
                && iy < hudJumpY + hudJumpH) {
            clearSearch(searchField);
            if (hudJumpWindow != null && hudJumpLine != null) {
                int gw = mc.getWindow().getGuiScaledWidth();
                int gh = mc.getWindow().getGuiScaledHeight();
                ChatWindowGeometry.scrollOpenChatToLine(hudJumpWindow, mc, gw, gh, hudJumpLine);
            }
            return true;
        }
        if (vanillaJumpW > 0
                && ix >= vanillaJumpX
                && ix < vanillaJumpX + vanillaJumpW
                && iy >= vanillaJumpY
                && iy < vanillaJumpY + vanillaJumpH) {
            clearSearch(searchField);
            ChatSearchJump.scrollVanillaToLine(mc, vanillaJumpLine);
            return true;
        }
        return false;
    }

    private static void clearSearch(EditBox searchField) {
        ChatSearchState.clear();
        if (searchField != null) {
            searchField.setValue("");
        }
    }
}
