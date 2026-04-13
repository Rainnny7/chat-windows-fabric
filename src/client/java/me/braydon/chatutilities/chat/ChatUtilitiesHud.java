package me.braydon.chatutilities.chat;

import com.mojang.blaze3d.platform.Window;
import me.braydon.chatutilities.ChatUtilitiesModClient;
import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public final class ChatUtilitiesHud {

    private ChatUtilitiesHud() {}

    public static void register() {
        HudRenderCallback.EVENT.register(ChatUtilitiesHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        // Only suppress when the full F3 overlay is open — not for lines/charts set to "always" in debug settings.
        if (mc.options.hideGui || mc.debugEntries.isOverlayVisible()) {
            return;
        }
        // ChatScreen draws after the HUD pass; layout windows sit near the chat area and would be fully covered.
        // {@link me.braydon.chatutilities.mixin.client.ChatScreenMixin} paints them at render TAIL instead.
        if (mc.screen instanceof ChatScreen && ChatUtilitiesManager.get().isPositioning()) {
            return;
        }

        renderChatWindowsLayer(graphics, deltaTracker);
    }

    /**
     * Called from {@link net.minecraft.client.gui.screens.ChatScreen} render TAIL while adjusting layout so windows
     * and guidelines appear above the chat UI.
     */
    public static void renderPositioningOverChatScreen(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.debugEntries.isOverlayVisible()) {
            return;
        }
        if (!ChatUtilitiesManager.get().isPositioning()) {
            return;
        }
        // Do not call disableScissor() in a loop: the stack is often shallow (or empty), and extra pops crash with
        // IllegalStateException: Scissor stack underflow (see ChatScreen render TAIL).
        renderChatWindowsLayer(graphics, deltaTracker);
    }

    private static void renderChatWindowsLayer(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();

        int gw = mc.getWindow().getGuiScaledWidth();
        int gh = mc.getWindow().getGuiScaledHeight();
        Window win = mc.getWindow();
        int mx = (int) mc.mouseHandler.getScaledXPos(win);
        int my = (int) mc.mouseHandler.getScaledYPos(win);
        boolean chatOpen = mc.screen instanceof ChatScreen;
        boolean chatPeek =
                ChatUtilitiesModClient.CHAT_PEEK_KEY != null && ChatUtilitiesModClient.CHAT_PEEK_KEY.isDown();
        boolean chatExpandedForWindows = chatOpen || chatPeek;
        int guiTick = mc.gui.getGuiTicks();
        float chatOpacity = mc.options.chatOpacity().get().floatValue();

        ChatUtilitiesManager mgr = ChatUtilitiesManager.get();
        for (ChatWindow window : mgr.getHudChatWindows()) {
            boolean layoutChrome = mgr.showsLayoutChrome(window);
            boolean showUnreadOnly =
                    window.isVisible()
                            && !chatExpandedForWindows
                            && ChatUtilitiesClientOptions.isAlwaysShowUnreadTabs()
                            && !mgr.isPositioning()
                            && !layoutChrome
                            && window.getTabCount() > 1
                            && window.getTabs().stream().anyMatch(t -> t != null && t.getUnreadCount() > 0);
            if (!window.isVisible() && !showUnreadOnly && !(mgr.isPositioning() && layoutChrome)) {
                continue;
            }

            boolean hasStored = !window.getLines().isEmpty();
            if (!hasStored && !layoutChrome && !chatExpandedForWindows && !showUnreadOnly) {
                continue;
            }

            Component placeholder = null;
            if (!hasStored) {
                placeholder =
                        layoutChrome
                                ? Component.literal("[empty]")
                                : Component.literal("No matching chat yet")
                                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            }

            // Layout frame always uses configured bounds (not content-tight); matches tick/snap hit-tests.
            ChatWindowGeometry geo =
                    ChatWindowGeometry.compute(
                            window,
                            mc,
                            gw,
                            gh,
                            placeholder,
                            guiTick,
                            layoutChrome,
                            chatExpandedForWindows,
                            mx,
                            my,
                            false,
                            layoutChrome);

            if (!hasStored && geo.rows.isEmpty() && !layoutChrome) {
                continue;
            }

            if (hasStored && geo.rows.isEmpty() && !chatOpen && !layoutChrome && !showUnreadOnly) {
                continue;
            }

            int x = geo.x;
            int y = geo.y;
            int boxW = geo.boxW;
            int boxH = geo.boxH;

            if (showUnreadOnly) {
                List<ChatWindowTab> unreadTabs = new ArrayList<>();
                for (ChatWindowTab t : window.getTabs()) {
                    if (t != null && t.getUnreadCount() > 0) {
                        unreadTabs.add(t);
                    }
                }
                if (!unreadTabs.isEmpty()) {
                    // When the selected tab is empty, the normal geometry can become content-tight and jitter.
                    // Use "chrome" geometry for stable placement of the unread-only strip.
                    ChatWindowGeometry geoUnread =
                            ((!hasStored || geo.rows.isEmpty()) && !layoutChrome)
                                    ? ChatWindowGeometry.compute(
                                            window,
                                            mc,
                                            gw,
                                            gh,
                                            Component.literal("[empty]"),
                                            guiTick,
                                            true,
                                            true,
                                            mx,
                                            my,
                                            false,
                                            false)
                                    : geo;
                    ChatWindowHudTabStrip.Placement pUnread =
                            ChatWindowHudTabStrip.resolveUnreadOnly(geoUnread, gw, gh, mc, window, unreadTabs);
                    float unreadOpacity = Mth.clamp(chatOpacity * 0.85f, 0f, 1f);
                    ChatWindowHudTabStrip.renderUnreadOnly(graphics, mc, unreadTabs, pUnread, unreadOpacity);
                }
                continue;
            }

            if (layoutChrome) {
                int pad = ChatWindowGeometry.padding();
                // Full outline rect filled (padding strip matches row backdrop base color).
                int frameL = x;
                int frameT = y;
                int frameR = x + boxW;
                int frameB = y + boxH;
                if (frameR > frameL && frameB > frameT) {
                    float textBg = mc.options.textBackgroundOpacity().get().floatValue();
                    float panelM = ChatUtilitiesClientOptions.getChatPanelBackgroundOpacityMultiplier(chatOpen);
                    // One layer only (no per-row strips): matches HUD chat panel opacity, avoids darker “row bands”.
                    int filler = ARGB.black(Mth.clamp(textBg * chatOpacity * panelM, 0f, 1f));
                    graphics.fill(frameL, frameT, frameR, frameB, filler);
                    int innerL = x + pad;
                    int innerT = y + geo.contentRowOffsetY;
                    int innerR = x + boxW - pad;
                    int innerB = y + boxH - geo.rowBottomInsetPx;
                    if (window.getTabCount() > 1) {
                        List<int[]> occupied =
                                tabOccupiedRectsForPlacement(mgr, chatOpen, window, mc, gw, gh, guiTick, mx, my);
                        ChatWindowHudTabStrip.Placement tabPl =
                                ChatWindowHudTabStrip.resolve(geo, gw, gh, mc, window, occupied);
                        ChatWindowHudTabStrip.render(graphics, mc, window, tabPl, chatOpacity, mx, my);
                    }
                    if (innerR > innerL && innerB > innerT) {
                        graphics.enableScissor(innerL, innerT, innerR, innerB);
                    } else {
                        graphics.enableScissor(frameL, frameT, frameR, frameB);
                    }
                    try {
                        if (hasStored) {
                            renderStyledRows(
                                    graphics, mc, geo, x, y, boxW, boxH, chatOpacity, true, 0);
                        } else {
                            renderPlaceholderRows(
                                    graphics, mc, geo, x, y, boxW, boxH, 0xAAAAAA, chatOpacity, true, 0);
                        }
                    } finally {
                        graphics.disableScissor();
                    }
                }
                // Geometry hit-tests use (x,y,boxW,boxH); outline flush with that rect.
                int edge = 0xFF8A8A98;
                graphics.renderOutline(x, y, boxW, boxH, edge);
                int hintLen = Math.min(12, Math.min(boxW, boxH) / 2);
                if (hintLen >= 3) {
                    int hi = 0xFFFFFFFF;
                    graphics.fill(x + boxW - hintLen, y + boxH - 2, x + boxW, y + boxH - 1, hi);
                    graphics.fill(x + boxW - 2, y + boxH - hintLen, x + boxW - 1, y + boxH, hi);
                }
            } else if (hasStored) {
                ChatWindowHudTabStrip.Placement tabPl = null;
                if (chatExpandedForWindows && window.getTabCount() > 1) {
                    List<int[]> occupied =
                            tabOccupiedRectsForPlacement(mgr, chatOpen, window, mc, gw, gh, guiTick, mx, my);
                    tabPl = ChatWindowHudTabStrip.resolve(geo, gw, gh, mc, window, occupied);
                    if (tabPl.edge() == ChatWindowHudTabStrip.Edge.TOP) {
                        int ty0 = y + geo.contentRowOffsetY;
                        float textBg = mc.options.textBackgroundOpacity().get().floatValue();
                        float panelM = ChatUtilitiesClientOptions.getChatPanelBackgroundOpacityMultiplier(chatOpen);
                        int fillerTop =
                                ARGB.black(Mth.clamp(textBg * chatOpacity * panelM, 0f, 1f));
                        graphics.fill(x, y, x + boxW, ty0, fillerTop);
                    }
                }
                renderStyledRows(graphics, mc, geo, x, y, boxW, boxH, chatOpacity, false, 0);
                ChatWindowScrollbar.render(graphics, mc, window, geo, chatOpacity, chatOpen);
                if (tabPl != null) {
                    ChatWindowHudTabStrip.render(graphics, mc, window, tabPl, chatOpacity, mx, my);
                }
            } else {
                ChatWindowHudTabStrip.Placement tabPl = null;
                if (chatExpandedForWindows && window.getTabCount() > 1) {
                    List<int[]> occupied =
                            tabOccupiedRectsForPlacement(mgr, chatOpen, window, mc, gw, gh, guiTick, mx, my);
                    tabPl = ChatWindowHudTabStrip.resolve(geo, gw, gh, mc, window, occupied);
                    if (tabPl.edge() == ChatWindowHudTabStrip.Edge.TOP) {
                        int ty0 = y + geo.contentRowOffsetY;
                        float textBg = mc.options.textBackgroundOpacity().get().floatValue();
                        float panelM = ChatUtilitiesClientOptions.getChatPanelBackgroundOpacityMultiplier(chatOpen);
                        int fillerTop =
                                ARGB.black(Mth.clamp(textBg * chatOpacity * panelM, 0f, 1f));
                        graphics.fill(x, y, x + boxW, ty0, fillerTop);
                    }
                }
                renderStyledRows(graphics, mc, geo, x, y, boxW, boxH, chatOpacity, false, 0);
                if (tabPl != null) {
                    ChatWindowHudTabStrip.render(graphics, mc, window, tabPl, chatOpacity, mx, my);
                }
            }

        }

        if (chatOpen
                && !mgr.isPositioning()
                && ChatUtilitiesClientOptions.isChatSearchBarEnabled()
                && ChatSearchState.isFiltering()) {
            ChatSearchOverlay.renderHudJumpTopmost(mc, graphics, gw, gh, mx, my, guiTick, chatOpacity);
        }

        if (mgr.isPositioning()) {
            int cx = gw / 2;
            int cy = gh / 2;
            graphics.fill(cx, 0, cx + 1, gh, 0x55FFFFFF);
            graphics.fill(0, cy, gw, cy + 1, 0x55FFFFFF);
            for (int vx : mgr.layoutSnapGuideVerticalXs()) {
                graphics.fill(vx, 0, vx + 1, gh, 0x88FFE08A);
            }
            if (!mgr.isLayoutAdjustPointerDown()) {
                renderLayoutModeHelp(graphics, mc, gw, gh);
            }
        }

        ChatWindowHover.hoverTooltipAt(mc, mx, my)
                .ifPresent(
                        tip -> {
                            List<FormattedCharSequence> lines = Tooltip.splitTooltip(mc, tip);
                            List<ClientTooltipComponent> components =
                                    lines.stream().map(ClientTooltipComponent::create).toList();
                            graphics.renderTooltip(
                                    mc.font,
                                    components,
                                    mx,
                                    my,
                                    DefaultTooltipPositioner.INSTANCE,
                                    null);
                        });
    }

    /**
     * When chat is closed and the HUD is showing unread-only tabs, suppress vanilla HUD chat so only the unread strip
     * remains on-screen.
     */
    public static boolean shouldSuppressVanillaChatHud(Minecraft mc) {
        return false;
    }

    /**
     * When chat is open for typing (not adjust-layout), tab strips ignore other windows so strips can overlap the
     * expanded chat. In adjust-layout, other windows are considered so tabs tuck above/below correctly.
     */
    private static List<int[]> tabOccupiedRectsForPlacement(
            ChatUtilitiesManager mgr,
            boolean chatOpen,
            ChatWindow self,
            Minecraft mc,
            int gw,
            int gh,
            int guiTick,
            int mx,
            int my) {
        if (chatOpen && !mgr.isPositioning()) {
            return List.of();
        }
        return occupiedChatWindowBoxesForTabs(mgr, self, mc, gw, gh, guiTick, chatOpen, mx, my);
    }

    private static List<int[]> occupiedChatWindowBoxesForTabs(
            ChatUtilitiesManager mgr,
            ChatWindow self,
            Minecraft mc,
            int gw,
            int gh,
            int guiTick,
            boolean chatOpen,
            int mx,
            int my) {
        List<int[]> out = new ArrayList<>();
        for (ChatWindow w : mgr.getHudChatWindows()) {
            if (w == self) {
                continue;
            }
            boolean layoutChrome = mgr.showsLayoutChrome(w);
            if (!w.isVisible() && !(mgr.isPositioning() && layoutChrome)) {
                continue;
            }
            boolean hasStored = !w.getLines().isEmpty();
            if (!hasStored && !layoutChrome && !chatOpen) {
                continue;
            }
            Component placeholder = null;
            if (!hasStored) {
                placeholder =
                        layoutChrome
                                ? Component.literal("[empty]")
                                : Component.literal("No matching chat yet")
                                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            }
            ChatWindowGeometry geo =
                    ChatWindowGeometry.compute(
                            w,
                            mc,
                            gw,
                            gh,
                            placeholder,
                            guiTick,
                            layoutChrome,
                            chatOpen,
                            mx,
                            my,
                            false,
                            false);
            out.add(new int[] {geo.x, geo.y, geo.x + geo.boxW, geo.y + geo.boxH});
        }
        return out;
    }

    private static void renderLayoutModeHelp(GuiGraphics graphics, Minecraft mc, int gw, int gh) {
        String block = I18n.get("chat-utilities.layout_mode.help.compact");
        String[] lines = block.split("\n");
        var font = mc.font;
        int lineH = font.lineHeight;
        int maxW = 0;
        for (String line : lines) {
            maxW = Math.max(maxW, font.width(line));
        }
        int padX = 8;
        int padY = 6;
        int boxW = maxW + 2 * padX;
        int gap = 1;
        int boxH = lines.length * lineH + (lines.length - 1) * gap + 2 * padY;
        int bx = (gw - boxW) / 2;
        int by = (gh - boxH) / 2;
        graphics.fill(bx, by, bx + boxW, by + boxH, 0xC0101010);
        graphics.renderOutline(bx, by, boxW, boxH, 0xFF707088);
        int y = by + padY;
        for (String line : lines) {
            graphics.drawString(font, line, bx + padX, y, 0xFFE8EEF8, false);
            y += lineH + gap;
        }
    }

    /**
     * Per-line backdrop like vanilla HUD chat pass 1: {@code fill(-4, rowTop, rowInnerWidth + 8, rowBottom,
     * ARGB.black(lineOpacity * textBackgroundOpacity))} in chat-local space; here {@code tx} is the text origin.
     */
    private static void fillVanillaStyleChatRowBackdrop(
            GuiGraphics graphics,
            Minecraft mc,
            int tx,
            int rowTop,
            int innerWidth,
            int rowBottom,
            float lineOpacity) {
        float textBg = mc.options.textBackgroundOpacity().get().floatValue();
        float panelM = ChatUtilitiesClientOptions.getChatPanelBackgroundOpacityMultiplier(mc.screen instanceof ChatScreen);
        float a = Mth.clamp(lineOpacity * textBg * panelM, 0f, 1f);
        int argb = ARGB.black(a);
        graphics.fill(tx - 4, rowTop, tx + innerWidth + 8, rowBottom, argb);
    }

    /**
     * @param layoutPreview when true, caller has already scissored to the inner content rect (adjust-layout overlay).
     */
    private static void renderStyledRows(
            GuiGraphics graphics,
            Minecraft mc,
            ChatWindowGeometry geo,
            int x,
            int y,
            int boxW,
            int boxH,
            float chatOpacity,
            boolean layoutPreview,
            int layoutExtraTop) {
        boolean ownScissor = !layoutPreview;
        if (ownScissor) {
            graphics.enableScissor(x, y, x + boxW, y + boxH);
        }
        try {
            int pad = ChatWindowGeometry.padding();
            int tx = x + pad;
            int innerW = boxW - 2 * pad;
            int lh = geo.lineHeight();
            float rowScale = geo.textScale;
            int ty = y + geo.contentRowOffsetY + layoutExtraTop;
            for (ChatWindowGeometry.RenderedRow row : geo.rows) {
                int rowBottom = ty + lh;
                if (!layoutPreview) {
                    fillVanillaStyleChatRowBackdrop(graphics, mc, tx, ty, innerW, rowBottom, row.alpha);
                }
                float a = Mth.clamp(row.alpha * chatOpacity, 0f, 1f);
                int color = ChatWindowGeometry.argbText(a, 0xFFFFFF);
                int drawY = ty + row.slideYOffset + ChatWindowGeometry.ROW_TEXT_TOP_NUDGE;
                if (rowScale > 0.999f && rowScale < 1.001f) {
                    graphics.drawString(
                            mc.font, row.text, tx, drawY, color, ChatUtilitiesClientOptions.isChatTextShadow());
                } else {
                    var pose = graphics.pose();
                    pose.pushMatrix();
                    pose.translate(tx, drawY);
                    pose.scale(rowScale, rowScale);
                    graphics.drawString(
                            mc.font, row.text, 0, 0, color, ChatUtilitiesClientOptions.isChatTextShadow());
                    pose.popMatrix();
                }
                ty = rowBottom;
            }
        } finally {
            if (ownScissor) {
                graphics.disableScissor();
            }
        }
    }

    private static void renderPlaceholderRows(
            GuiGraphics graphics,
            Minecraft mc,
            ChatWindowGeometry geo,
            int x,
            int y,
            int boxW,
            int boxH,
            int rgb,
            float chatOpacity,
            boolean layoutPreview,
            int layoutExtraTop) {
        boolean ownScissor = !layoutPreview;
        if (ownScissor) {
            graphics.enableScissor(x, y, x + boxW, y + boxH);
        }
        try {
            int pad = ChatWindowGeometry.padding();
            int tx = x + pad;
            int innerW = boxW - 2 * pad;
            int lh = geo.lineHeight();
            float rowScale = geo.textScale;
            int ty = y + geo.contentRowOffsetY + layoutExtraTop;
            for (ChatWindowGeometry.RenderedRow row : geo.rows) {
                int rowBottom = ty + lh;
                if (!layoutPreview) {
                    fillVanillaStyleChatRowBackdrop(graphics, mc, tx, ty, innerW, rowBottom, row.alpha);
                }
                float a = Mth.clamp(row.alpha * chatOpacity, 0f, 1f);
                int color = ChatWindowGeometry.argbText(a, rgb);
                int drawY = ty + row.slideYOffset + ChatWindowGeometry.ROW_TEXT_TOP_NUDGE;
                if (rowScale > 0.999f && rowScale < 1.001f) {
                    graphics.drawString(
                            mc.font,
                            row.text,
                            tx,
                            drawY,
                            color,
                            ChatUtilitiesClientOptions.isChatTextShadow());
                } else {
                    var pose = graphics.pose();
                    pose.pushMatrix();
                    pose.translate(tx, drawY);
                    pose.scale(rowScale, rowScale);
                    graphics.drawString(
                            mc.font,
                            row.text,
                            0,
                            0,
                            color,
                            ChatUtilitiesClientOptions.isChatTextShadow());
                    pose.popMatrix();
                }
                ty = rowBottom;
            }
        } finally {
            if (ownScissor) {
                graphics.disableScissor();
            }
        }
    }
}
