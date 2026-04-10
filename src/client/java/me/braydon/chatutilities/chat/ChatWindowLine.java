package me.braydon.chatutilities.chat;

import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

public record ChatWindowLine(Component baseContent, int addedGuiTick, int stackCount, long addedWallTimeMs) {
    public ChatWindowLine {
        stackCount = Math.max(1, stackCount);
    }

    public static ChatWindowLine single(Component message, int tick, long wallTimeMs) {
        return new ChatWindowLine(message, tick, 1, wallTimeMs);
    }

    /** Full line for rendering and hit-testing (includes gray {@code (xN)} when stacked). */
    public Component styled() {
        Component body =
                stackCount <= 1
                        ? baseContent
                        : Component.empty()
                                .append(baseContent)
                                .append(stackedSuffixComponent(stackCount));
        if (!ChatUtilitiesClientOptions.isChatTimestampsEnabled()) {
            return body;
        }
        MutableComponent ts = ChatTimestampFormatter.componentAtMillis(addedWallTimeMs);
        if (ts.getString().isEmpty()) {
            return body;
        }
        return Component.empty().append(ts).append(body);
    }

    private static Component stackedSuffixComponent(int amount) {
        String raw = ChatUtilitiesClientOptions.getStackedMessageFormat();
        String suffix = raw.replace("%amount%", Integer.toString(Math.max(1, amount)));
        if (!suffix.isEmpty() && !Character.isWhitespace(suffix.charAt(0))) {
            suffix = " " + suffix;
        }
        int rgb = ChatUtilitiesClientOptions.getStackedMessageColorRgb() & 0xFFFFFF;
        return Component.literal(suffix)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
    }

    boolean sameStackAs(ChatWindowLine incoming) {
        String a = ChatUtilitiesManager.plainTextForMatching(baseContent);
        String b = ChatUtilitiesManager.plainTextForMatching(incoming.baseContent);
        if (a.isBlank() || b.isBlank()) {
            return false;
        }
        return a.equals(b);
    }

    ChatWindowLine mergedWithRepeat() {
        return new ChatWindowLine(baseContent, addedGuiTick, stackCount + 1, addedWallTimeMs);
    }
}
