package me.braydon.chatutilities.chat;

import com.mojang.blaze3d.platform.Window;

import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

public final class ChatUtilitiesHud {
    /** Same alpha as vanilla chat line backing ({@code 0x80000000}). */
    private static final int CHAT_BACKDROP_BASE_ALPHA = 128;

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

        int gw = mc.getWindow().getGuiScaledWidth();
        int gh = mc.getWindow().getGuiScaledHeight();
        Window win = mc.getWindow();
        int mx = (int) mc.mouseHandler.getScaledXPos(win);
        int my = (int) mc.mouseHandler.getScaledYPos(win);
        boolean chatOpen = mc.screen instanceof ChatScreen;
        int guiTick = mc.gui.getGuiTicks();
        float chatOpacity = mc.options.chatOpacity().get().floatValue();

        ChatUtilitiesManager mgr = ChatUtilitiesManager.get();
        for (ChatWindow window : mgr.getActiveProfileWindows()) {
            if (!window.isVisible()) {
                continue;
            }

            boolean hasStored = !window.getLines().isEmpty();
            if (!hasStored && !window.isPositioningMode() && !chatOpen) {
                continue;
            }

            Component placeholder = null;
            if (!hasStored) {
                placeholder =
                        window.isPositioningMode()
                                ? Component.literal("[empty]")
                                : Component.literal("No matching chat yet")
                                        .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            }

            ChatWindowGeometry geo =
                    ChatWindowGeometry.compute(
                            window,
                            mc,
                            gw,
                            gh,
                            placeholder,
                            guiTick,
                            window.isPositioningMode(),
                            chatOpen,
                            mx,
                            my);

            if (!hasStored && geo.rows.isEmpty()) {
                continue;
            }

            if (hasStored && geo.rows.isEmpty() && !chatOpen && !window.isPositioningMode()) {
                continue;
            }

            int x = geo.x;
            int y = geo.y;
            int boxW = geo.boxW;
            int boxH = geo.boxH;

            if (window.isPositioningMode()) {
                graphics.fill(x - 1, y - 1, x + boxW + 1, y + boxH + 1, 0x66C8C8C8);
                int hintLen = Math.min(12, Math.min(boxW, boxH) / 2);
                if (hintLen >= 3) {
                    int hi = 0xFFFFFFFF;
                    graphics.fill(x + boxW - hintLen, y + boxH - 2, x + boxW, y + boxH - 1, hi);
                    graphics.fill(x + boxW - 2, y + boxH - hintLen, x + boxW - 1, y + boxH, hi);
                }
            }

            if (hasStored) {
                fillChatWindowBackdrop(graphics, x, y, boxW, boxH, chatOpacity);
                renderStyledRows(graphics, mc, geo, x, y, boxW, boxH, chatOpacity);
                ChatWindowScrollbar.render(graphics, mc, window, geo, chatOpacity, chatOpen);
            } else if (window.isPositioningMode()) {
                fillChatWindowBackdrop(graphics, x, y, boxW, boxH, chatOpacity);
                renderPlaceholderRows(graphics, mc, geo, x, y, boxW, boxH, 0xAAAAAA, chatOpacity);
            } else {
                fillChatWindowBackdrop(graphics, x, y, boxW, boxH, chatOpacity);
                renderStyledRows(graphics, mc, geo, x, y, boxW, boxH, chatOpacity);
            }
        }

        if (mgr.isPositioning()) {
            int cx = gw / 2;
            int cy = gh / 2;
            graphics.fill(cx, 0, cx + 1, gh, 0x55FFFFFF);
            graphics.fill(0, cy, gw, cy + 1, 0x55FFFFFF);
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
     * Single semi-transparent rectangle behind the window, matching vanilla chat HUD: one uniform
     * alpha for the whole panel. Line fade is applied to text only in {@link #renderStyledRows}.
     */
    private static void fillChatWindowBackdrop(
            GuiGraphics graphics,
            int x,
            int y,
            int boxW,
            int boxH,
            float chatOpacity) {
        int a = Mth.clamp(Math.round(CHAT_BACKDROP_BASE_ALPHA * chatOpacity), 0, 255);
        graphics.fill(x, y, x + boxW, y + boxH, (a << 24));
    }

    private static void renderStyledRows(
            GuiGraphics graphics,
            Minecraft mc,
            ChatWindowGeometry geo,
            int x,
            int y,
            int boxW,
            int boxH,
            float chatOpacity) {
        graphics.enableScissor(x, y, x + boxW, y + boxH);
        try {
            int tx = x + ChatWindowGeometry.padding();
            int ty = y + ChatWindowGeometry.padding() + geo.contentStartYOffset;
            for (ChatWindowGeometry.RenderedRow row : geo.rows) {
                float a = Mth.clamp(row.alpha * chatOpacity, 0f, 1f);
                int color = ChatWindowGeometry.argbText(a, 0xFFFFFF);
                graphics.drawString(mc.font, row.text, tx, ty, color, ChatUtilitiesClientOptions.isChatTextShadow());
                ty += ChatWindowGeometry.lineHeight();
            }
        } finally {
            graphics.disableScissor();
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
            float chatOpacity) {
        graphics.enableScissor(x, y, x + boxW, y + boxH);
        try {
            int ty = y + ChatWindowGeometry.padding() + geo.contentStartYOffset;
            for (ChatWindowGeometry.RenderedRow row : geo.rows) {
                float a = Mth.clamp(row.alpha * chatOpacity, 0f, 1f);
                int color = ChatWindowGeometry.argbText(a, rgb);
                graphics.drawString(
                        mc.font,
                        row.text,
                        x + ChatWindowGeometry.padding(),
                        ty,
                        color,
                        ChatUtilitiesClientOptions.isChatTextShadow());
                ty += ChatWindowGeometry.lineHeight();
            }
        } finally {
            graphics.disableScissor();
        }
    }
}
