package me.braydon.chatutilities.chat;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public final class ChatWindowFade {
    private ChatWindowFade() {}

    public static float lineAlpha(int addedGuiTick, int currentGuiTick) {
        ChatComponent.AlphaCalculator opacity = ChatComponent.AlphaCalculator.timeBased(currentGuiTick);
        GuiMessage.Line line =
                new GuiMessage.Line(addedGuiTick, FormattedCharSequence.EMPTY, null, true);
        return Mth.clamp(opacity.calculate(line), 0f, 1f);
    }
}
