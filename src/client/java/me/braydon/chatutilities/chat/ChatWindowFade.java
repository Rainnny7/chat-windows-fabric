package me.braydon.chatutilities.chat;

import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public final class ChatWindowFade {
    private ChatWindowFade() {}

    private static GuiMessage fakeParentMessage(int addedGuiTick) {
        return new GuiMessage(addedGuiTick, Component.empty(), null, (GuiMessageSource) null, null);
    }

    /**
     * Opacity for a chat-window line when the chat UI is closed: vanilla time-based HUD fade (unfocused chat) times
     * smooth-chat fade-in, with stack-merge suppress so repeat counters stay readable like vanilla stacked lines.
     */
    public static float chatWindowLineAlpha(ChatWindowLine line, int currentGuiTick) {
        ChatComponent.AlphaCalculator opacity = ChatComponent.AlphaCalculator.timeBased(currentGuiTick);
        GuiMessage.Line fake =
                new GuiMessage.Line(fakeParentMessage(line.addedGuiTick()), FormattedCharSequence.EMPTY, true);
        float base = Mth.clamp(opacity.calculate(fake), 0f, 1f);
        return base * chatWindowSmoothFadeMultiplier(line, currentGuiTick);
    }

    /** Smooth-chat fade multiplier for a window line; {@code 1} while stack-merge suppress is active. */
    public static float chatWindowSmoothFadeMultiplier(ChatWindowLine line, int guiTick) {
        if (!ChatUtilitiesClientOptions.isSmoothChat()) {
            return 1f;
        }
        if (line.stackFadeSuppressUntilTick() >= 0 && guiTick <= line.stackFadeSuppressUntilTick()) {
            return 1f;
        }
        return ChatSmoothAppearance.fadeInMultiplier(line.addedGuiTick());
    }

    /** Smooth-chat slide offset; {@code 0} while stack-merge suppress is active. */
    public static int chatWindowLineSlideY(ChatWindowLine line, int guiTick) {
        if (!ChatUtilitiesClientOptions.isSmoothChat()) {
            return 0;
        }
        if (line.stackFadeSuppressUntilTick() >= 0 && guiTick <= line.stackFadeSuppressUntilTick()) {
            return 0;
        }
        return ChatSmoothAppearance.fadeSlideOffsetYPixels(line.addedGuiTick());
    }

    /**
     * @deprecated Prefer {@link #chatWindowLineAlpha(ChatWindowLine, int)} so stack merges use the correct tick and
     *     suppress window.
     */
    @Deprecated
    public static float lineAlpha(int addedGuiTick, int currentGuiTick) {
        ChatComponent.AlphaCalculator opacity = ChatComponent.AlphaCalculator.timeBased(currentGuiTick);
        GuiMessage.Line line =
                new GuiMessage.Line(fakeParentMessage(addedGuiTick), FormattedCharSequence.EMPTY, true);
        float base = Mth.clamp(opacity.calculate(line), 0f, 1f);
        return base * ChatSmoothAppearance.fadeInMultiplier(addedGuiTick);
    }
}
