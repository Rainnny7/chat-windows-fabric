package me.braydon.chatutilities.chat;

import com.mojang.blaze3d.platform.Window;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public final class ChatUtilitiesHud {
    private ChatUtilitiesHud() {}

    public static void register() {
        HudRenderCallback.EVENT.register(ChatUtilitiesHud::render);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) {
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

        for (ChatWindow window : ChatUtilitiesManager.get().getActiveProfileWindows()) {
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
            }

            if (hasStored) {
                graphics.fill(x, y, x + boxW, y + boxH, 0x80000000);
                renderStyledRows(graphics, geo, x, y, boxW, boxH, chatOpacity);
                ChatWindowScrollbar.render(graphics, mc, window, geo, chatOpacity, chatOpen);
            } else if (window.isPositioningMode()) {
                graphics.fill(x, y, x + boxW, y + boxH, 0x80000000);
                renderPlaceholderRows(graphics, mc, geo, x, y, boxW, boxH, 0xAAAAAA, chatOpacity);
            } else {
                graphics.fill(x, y, x + boxW, y + boxH, 0x80000000);
                renderStyledRows(graphics, geo, x, y, boxW, boxH, chatOpacity);
            }
        }
    }

    private static void renderStyledRows(
            GuiGraphics graphics,
            ChatWindowGeometry geo,
            int x,
            int y,
            int boxW,
            int boxH,
            float chatOpacity) {
        graphics.enableScissor(x, y, x + boxW, y + boxH);
        try {
            ActiveTextCollector textRenderer =
                    graphics.textRenderer(GuiGraphics.HoveredTextEffects.TOOLTIP_AND_CURSOR);
            int tx = x + ChatWindowGeometry.padding();
            int ty = y + ChatWindowGeometry.padding() + geo.contentStartYOffset;
            for (ChatWindowGeometry.RenderedRow row : geo.rows) {
                float a = Mth.clamp(row.alpha * chatOpacity, 0f, 1f);
                ActiveTextCollector.Parameters params = textRenderer.defaultParameters().withOpacity(a);
                textRenderer.accept(TextAlignment.LEFT, tx, ty, params, row.text);
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
                graphics.drawString(mc.font, row.text, x + ChatWindowGeometry.padding(), ty, color, true);
                ty += ChatWindowGeometry.lineHeight();
            }
        } finally {
            graphics.disableScissor();
        }
    }
}
