package me.braydon.chatutilities.chat;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import me.braydon.chatutilities.mixin.client.ChatComponentAccess;
import net.minecraft.ChatFormatting;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Merges consecutive duplicate chat lines in the vanilla HUD into one line with a gray {@code (xN)} suffix.
 */
public final class VanillaChatRepeatStacker {
    /**
     * Plain text after formatting strip; matches a trailing repeat suffix from this mod.
     * Uses {@code (?:^| )} so it also matches when the base is empty and
     * {@link ChatUtilitiesManager#plainTextForMatching} has stripped the leading space
     * (e.g. {@code "(x2)"} rather than {@code " (x2)"}).
     */
    private static final Pattern PLAIN_STACK_SUFFIX = Pattern.compile("(?:^| )\\(x(\\d+)\\)$");

    /** Last sibling is only {@code (xN)} (stack counter). */
    private static final Pattern LITERAL_COUNTER_ONLY = Pattern.compile("^\\(x\\d+\\)$");

    /** Plain after optional timestamp is only {@code (xN)} — must not join empty-key stacks. */
    private static final Pattern PLAIN_BODY_COUNTER_ONLY = Pattern.compile("^\\s*\\(x\\d+\\)\\s*$");

    /**
     * Leading timestamp injected by {@link ChatTimestampFormatter} — e.g. {@code [12:34] } or
     * {@code [12:34:56] }.  Stripped before key comparison so messages that arrive in different
     * clock-minutes still merge into a single stack.
     */
    private static final Pattern TIMESTAMP_PREFIX =
            Pattern.compile("^\\s*\\[\\d{1,2}:\\d{2}(?::\\d{2})?\\]\\s*");

    private VanillaChatRepeatStacker() {}

    public static void afterAddMessage(ChatComponent chat, Component ignoredLatestDuplicateParam) {
        if (!ChatUtilitiesClientOptions.isStackRepeatedMessages()) {
            return;
        }
        ChatComponentAccess access = (ChatComponentAccess) chat;
        List<GuiMessage> msgs = access.chatUtilities$getAllMessages();
        if (msgs.size() < 2) {
            return;
        }
        GuiMessage newest = msgs.get(0);
        GuiMessage older = msgs.get(1);
        if (!plainStackKeysEqual(newest.content(), older.content())) {
            return;
        }
        int next = parseDisplayedCount(older.content()) + 1;
        Component base = stackBaseContent(newest.content(), older.content());
        Minecraft mc = Minecraft.getInstance();
        int mergeTick = mc != null ? mc.gui.getGuiTicks() : older.addedTime();
        GuiMessage merged =
                new GuiMessage(
                        // Current tick so the merged line is treated like a fresh HUD row (visible when chat is
                        // closed). Smooth-chat slide/fade for this line is suppressed for one fade window — see manager.
                        mergeTick,
                        withStackSuffix(base, next),
                        newest.signature(),
                        newest.source(),
                        newest.tag());
        msgs.remove(0);
        msgs.remove(0);
        msgs.add(0, merged);
        ChatUtilitiesManager.get().noteVanillaStackMergeForHud(mergeTick);
        ChatMessageRebuildGuard.enter();
        try {
            access.chatUtilities$refreshTrimmedMessages();
        } finally {
            ChatMessageRebuildGuard.exit();
        }
    }

    private static Component stackBaseContent(Component newest, Component older) {
        Component strippedNew = stripTrailingCounterSibling(newest);
        // Never allow the base to collapse to nothing, or we end up rendering lines that look like "(x2)" only.
        if (ChatUtilitiesManager.plainTextForMatching(strippedNew).strip().isEmpty()) {
            return newest;
        }
        boolean shortened =
                !ChatUtilitiesManager.plainTextForMatching(strippedNew)
                        .equals(ChatUtilitiesManager.plainTextForMatching(newest));
        if (parseDisplayedCount(newest) <= 1 || shortened) {
            return strippedNew;
        }
        Component strippedOlder = stripTrailingCounterSibling(older);
        if (ChatUtilitiesManager.plainTextForMatching(strippedOlder).strip().isEmpty()) {
            return older;
        }
        return strippedOlder;
    }

    /**
     * Removes a trailing sibling that is only {@code (xN)} (gray counter), matching how {@link #withStackSuffix}
     * appends the counter.
     */
    private static Component stripTrailingCounterSibling(Component c) {
        if (!(c instanceof MutableComponent mc)) {
            return c;
        }
        List<Component> sibs = mc.getSiblings();
        if (sibs.isEmpty()) {
            return c;
        }
        Component last = sibs.get(sibs.size() - 1);
        String pl = ChatUtilitiesManager.plainTextForMatching(last).strip();
        if (!LITERAL_COUNTER_ONLY.matcher(pl).matches()) {
            return c;
        }
        MutableComponent out = Component.empty();
        for (int i = 0; i < sibs.size() - 1; i++) {
            out.append(sibs.get(i));
        }
        if (sibs.size() == 1 && out.getString().isEmpty()) {
            return c;
        }
        return out;
    }

    private static boolean plainStackKeysEqual(Component a, Component b) {
        String ka = plainStackKey(a);
        String kb = plainStackKey(b);
        // Both keys are empty — both are empty messages or stacks of empty messages; allow merging.
        // Checking this before the artifact guard is intentional: the guard fires on stacked-empty
        // lines (plain text is only "(xN)"), which would otherwise prevent further stacking.
        if (ka.isEmpty() && kb.isEmpty()) {
            return true;
        }
        if (isCounterSuffixOnlyArtifact(a) || isCounterSuffixOnlyArtifact(b)) {
            return false;
        }
        return ka.equals(kb);
    }

    /**
     * True when the line’s plain body (ignoring leading chat timestamps) is only {@code (xN)}, so it must not
     * participate in empty-key stacking.
     */
    private static boolean isCounterSuffixOnlyArtifact(Component c) {
        String p = ChatUtilitiesManager.plainTextForMatching(c);
        Matcher ts = TIMESTAMP_PREFIX.matcher(p);
        if (ts.find()) {
            p = p.substring(ts.end());
        }
        p = stripInvisibleEverywhere(p).replaceAll("\\s+", " ").strip();
        return !p.isEmpty() && PLAIN_BODY_COUNTER_ONLY.matcher(p).matches();
    }

    /**
     * Whether two stored window lines (no leading timestamp in {@code baseContent}) should merge into one stack.
     */
    public static boolean chatWindowPlainStackEqual(String plainA, String plainB) {
        String pa = windowPlainStackKey(plainA);
        String pb = windowPlainStackKey(plainB);
        if (isPlainBodyCounterOnly(pa) || isPlainBodyCounterOnly(pb)) {
            return false;
        }
        if (!pa.isEmpty()) {
            return pa.equals(pb);
        }
        return pb.isEmpty();
    }

    private static String windowPlainStackKey(String plain) {
        if (plain == null) {
            return "";
        }
        String p = stripInvisibleEverywhere(plain).replaceAll("\\s+", " ").strip();
        Matcher m = PLAIN_STACK_SUFFIX.matcher(p);
        if (m.find()) {
            p = p.substring(0, m.start());
        }
        return p.replaceAll("\\s+", " ").strip();
    }

    private static boolean isPlainBodyCounterOnly(String pAfterSuffixStrip) {
        String s = pAfterSuffixStrip == null ? "" : pAfterSuffixStrip.strip();
        return !s.isEmpty() && PLAIN_BODY_COUNTER_ONLY.matcher(s).matches();
    }

    private static String plainStackKey(Component c) {
        String p = ChatUtilitiesManager.plainTextForMatching(c);
        // Strip a leading timestamp added by ChatTimestampFormatter so messages that arrive in
        // different clock-minutes still merge into one stack entry.
        Matcher ts = TIMESTAMP_PREFIX.matcher(p);
        if (ts.find()) {
            p = p.substring(ts.end());
        }
        Matcher m = PLAIN_STACK_SUFFIX.matcher(p);
        if (m.find()) {
            p = p.substring(0, m.start());
        }
        p = stripInvisibleEverywhere(p);
        // Collapse whitespace so visually identical lines stack even if spacing differs.
        p = p.replaceAll("\\s+", " ").strip();
        return p;
    }

    private static String stripInvisibleEverywhere(String s) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\uFEFF'
                    || c == '\u200B'
                    || c == '\u200C'
                    || c == '\u200D'
                    || c == '\u200E'
                    || c == '\u200F'
                    || c == '\u2060'
                    || (c >= '\u2066' && c <= '\u2069')) {
                continue;
            }
            b.append(c);
        }
        return b.toString();
    }

    private static int parseDisplayedCount(Component c) {
        String p = ChatUtilitiesManager.plainTextForMatching(c);
        Matcher m = PLAIN_STACK_SUFFIX.matcher(p);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return 1;
    }

    private static Component withStackSuffix(Component base, int count) {
        return Component.empty()
                .append(base)
                .append(Component.literal(" (x" + count + ")").withStyle(ChatFormatting.GRAY));
    }
}
