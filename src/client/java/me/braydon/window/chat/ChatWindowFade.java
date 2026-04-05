package me.braydon.window.chat;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * Delegates to vanilla chat HUD opacity ({@link ChatComponent.AlphaCalculator#timeBased} +
 * {@link GuiMessage.Line}), so timing matches main chat including accessibility chat delay.
 */
public final class ChatWindowFade {
    private ChatWindowFade() {}

    /**
     * Opacity for an unfocused HUD line, matching vanilla chat for the same {@code addedTime} and
     * current tick.
     */
    public static float lineAlpha(int addedGuiTick, int currentGuiTick) {
        ChatComponent.AlphaCalculator opacity = ChatComponent.AlphaCalculator.timeBased(currentGuiTick);
        GuiMessage.Line line =
                new GuiMessage.Line(addedGuiTick, FormattedCharSequence.EMPTY, null, true);
        return Mth.clamp(opacity.calculate(line), 0f, 1f);
    }
}
