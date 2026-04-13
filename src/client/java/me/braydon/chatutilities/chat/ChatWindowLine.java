package me.braydon.chatutilities.chat;

import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;

public record ChatWindowLine(
        Component baseContent,
        int addedGuiTick,
        int stackCount,
        long addedWallTimeMs,
        long stackPulseUntilMs,
        /**
         * While {@code guiTick <=} this value, smooth-chat fade/slide is skipped for this line (same idea as vanilla
         * stack merge suppress) so repeat-stack updates do not replay the intro animation every merge.
         */
        int stackFadeSuppressUntilTick) {
    public ChatWindowLine {
        stackCount = Math.max(1, stackCount);
    }

    public static ChatWindowLine single(Component message, int tick, long wallTimeMs) {
        return new ChatWindowLine(message, tick, 1, wallTimeMs, 0L, -1);
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
        return VanillaChatRepeatStacker.chatWindowPlainStackEqual(a, b);
    }

    ChatWindowLine mergedWithRepeat(int mergeGuiTick) {
        long pulseEnd = System.currentTimeMillis() + 450;
        long pulse = Math.max(stackPulseUntilMs, pulseEnd);
        int fadeMs = ChatUtilitiesClientOptions.getSmoothChatFadeMs();
        int durTicks = Math.max(1, Mth.ceil(fadeMs / (1000f / 20f)));
        int suppressUntil = mergeGuiTick + durTicks + 2;
        return new ChatWindowLine(baseContent, mergeGuiTick, stackCount + 1, addedWallTimeMs, pulse, suppressUntil);
    }
}
