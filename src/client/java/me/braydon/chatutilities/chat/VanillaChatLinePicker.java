package me.braydon.chatutilities.chat;

import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Resolves which visible vanilla chat line is under the cursor using the same layout as {@link
 * ChatComponent#forEachLine} (private API; reached via reflection). The {@code int} passed to
 * {@link ChatComponent.LineConsumer#accept} is a <em>line index</em> into the visible window, not a pixel Y;
 * vanilla maps it to screen space in {@code ChatComponent}'s render consumer. Mouse coordinates are converted
 * to chat-local space (inverse of the chat pose: scale + 4px X translate) to match expanded-chat hit tests.
 */
public final class VanillaChatLinePicker {
    /**
     * While non-null, {@link me.braydon.chatutilities.mixin.client.ChatComponentMixin} forwards each
     * {@code LineConsumer.accept} from {@link ChatComponent#forEachLine} here (Proxy dispatch is unreliable for
     * package-private functional interfaces on some runtimes).
     */
    private static final AtomicReference<PickerState> PICK_CAPTURE = new AtomicReference<>();

    private static final Method FOR_EACH_LINE;

    static {
        try {
            // Do not use getDeclaredMethod("forEachLine", ...): in production the JVM uses intermediary
            // names (e.g. method_71990), not Mojmap "forEachLine". Match by parameter types instead.
            FOR_EACH_LINE = resolveForEachLine();
            FOR_EACH_LINE.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Method resolveForEachLine() throws NoSuchMethodException {
        Class<?> alpha = ChatComponent.AlphaCalculator.class;
        Class<?> consumer = ChatComponent.LineConsumer.class;
        for (Method m : ChatComponent.class.getDeclaredMethods()) {
            if (m.getReturnType() != int.class || m.getParameterCount() != 2) {
                continue;
            }
            Class<?>[] p = m.getParameterTypes();
            if (p[0].equals(alpha) && p[1].equals(consumer)) {
                return m;
            }
        }
        throw new NoSuchMethodException(
                "ChatComponent line iterator (int, AlphaCalculator, LineConsumer) not found");
    }

    private VanillaChatLinePicker() {}

    @FunctionalInterface
    private interface DrawnViewportLineSink {
        void onLine(GuiMessage.Line line, int lineIndex, float opacity);
    }

    /**
     * Invokes {@link ChatComponent}'s line iterator with full opacity so each {@code accept} matches expanded-chat
     * rendering (including open-chat search filtering). {@code lineIndex} is vanilla’s viewport slot (same as draw).
     */
    private static void visitDrawnViewportLines(ChatComponent chat, DrawnViewportLineSink sink) {
        ChatComponent.AlphaCalculator alpha = ChatComponent.AlphaCalculator.FULLY_VISIBLE;
        Object consumer =
                Proxy.newProxyInstance(
                        ChatComponent.LineConsumer.class.getClassLoader(),
                        new Class<?>[] {ChatComponent.LineConsumer.class},
                        new ViewportWalkHandler(sink));
        try {
            FOR_EACH_LINE.invoke(chat, alpha, consumer);
        } catch (ReflectiveOperationException ignored) {
        }
    }

    private static final class ViewportWalkHandler implements InvocationHandler {
        private final DrawnViewportLineSink sink;

        ViewportWalkHandler(DrawnViewportLineSink sink) {
            this.sink = sink;
        }

        @Override
        public @Nullable Object invoke(Object proxy, Method method, @Nullable Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" ->
                            args != null && args.length == 1 && proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "VanillaChatLinePicker$ViewportWalkHandler";
                    default -> null;
                };
            }
            if ("accept".equals(method.getName())
                    && args != null
                    && args.length >= 3
                    && args[0] instanceof GuiMessage.Line ln) {
                float op = ((Number) args[2]).floatValue();
                int idx = (Integer) args[1];
                if (op > 0.01f) {
                    sink.onLine(ln, idx, op);
                }
            }
            return null;
        }
    }

    /**
     * While true, {@link me.braydon.chatutilities.mixin.client.ChatComponentMixin} skips smooth-chat fade on line
     * opacity so hit-testing still sees full alpha (picker uses {@code AlphaCalculator.FULLY_VISIBLE}).
     */
    public static boolean isPickCaptureActive() {
        return PICK_CAPTURE.get() != null;
    }

    /** Called from {@link me.braydon.chatutilities.mixin.client.ChatComponentMixin} around each chat line. */
    public static void notifyLineDuringPick(GuiMessage.Line line, int lineIndex, float opacity) {
        PickerState state = PICK_CAPTURE.get();
        if (state != null) {
            state.onLine(line, lineIndex, opacity);
        }
    }

    public static Optional<Component> pickLineAt(Minecraft mc, int mouseX, int mouseY) {
        ChatComponent chat = mc.gui.getChat();
        if (mc.options.chatVisibility().get() == ChatVisiblity.HIDDEN) {
            return Optional.empty();
        }
        // Expanded chat (ChatScreen) draws lines fully opaque; timeBased applies HUD fade and yields ~0 for most lines.
        ChatComponent.AlphaCalculator alpha = ChatComponent.AlphaCalculator.FULLY_VISIBLE;
        PickerState state = new PickerState(mc, chat, mouseX, mouseY, guiScaledHeight(mc));
        Object consumer =
                Proxy.newProxyInstance(
                        ChatComponent.LineConsumer.class.getClassLoader(),
                        new Class<?>[] {ChatComponent.LineConsumer.class},
                        new LineConsumerHandler());
        PICK_CAPTURE.set(state);
        try {
            FOR_EACH_LINE.invoke(chat, alpha, consumer);
        } catch (ReflectiveOperationException e) {
            return Optional.empty();
        } finally {
            PICK_CAPTURE.set(null);
        }
        return entryComponentForLine(chat, state.pickedLine);
    }

    /** Same hit test as {@link #pickLineAt}, but returns the wrapped {@link GuiMessage.Line} under the cursor. */
    public static Optional<GuiMessage.Line> pickGuiLineAt(Minecraft mc, int mouseX, int mouseY) {
        ChatComponent chat = mc.gui.getChat();
        if (mc.options.chatVisibility().get() == ChatVisiblity.HIDDEN) {
            return Optional.empty();
        }
        ChatComponent.AlphaCalculator alpha = ChatComponent.AlphaCalculator.FULLY_VISIBLE;
        PickerState state = new PickerState(mc, chat, mouseX, mouseY, guiScaledHeight(mc));
        Object consumer =
                Proxy.newProxyInstance(
                        ChatComponent.LineConsumer.class.getClassLoader(),
                        new Class<?>[] {ChatComponent.LineConsumer.class},
                        new LineConsumerHandler());
        PICK_CAPTURE.set(state);
        try {
            FOR_EACH_LINE.invoke(chat, alpha, consumer);
        } catch (ReflectiveOperationException e) {
            return Optional.empty();
        } finally {
            PICK_CAPTURE.set(null);
        }
        return Optional.ofNullable(state.pickedLine);
    }

    /**
     * Visible chat lines for the current {@link ChatComponent#getChatScrollbarPos()} and focused layout, in the same
     * order as {@link ChatComponent#forEachLine} invokes the consumer.
     */
    public static List<GuiMessage.Line> collectVisibleGuiLines(ChatComponent chat) {
        List<GuiMessage.Line> out = new ArrayList<>();
        ChatComponent.AlphaCalculator alpha = ChatComponent.AlphaCalculator.FULLY_VISIBLE;
        Object consumer =
                Proxy.newProxyInstance(
                        ChatComponent.LineConsumer.class.getClassLoader(),
                        new Class<?>[] {ChatComponent.LineConsumer.class},
                        new LinesCollectHandler(out));
        try {
            FOR_EACH_LINE.invoke(chat, alpha, consumer);
        } catch (ReflectiveOperationException ignored) {
        }
        return out;
    }

    /**
     * Plain text of the whole logical chat entry (all wrapped rows) for the block containing {@code hit}, in
     * on-screen order, concatenated without line breaks so search can match across wraps.
     */
    public static String entryPlainTextForMatching(ChatComponent chat, GuiMessage.Line hit) {
        if (hit == null) {
            return "";
        }
        @SuppressWarnings("unchecked")
        List<GuiMessage.Line> lines = (List<GuiMessage.Line>) chat.trimmedMessages;
        int i = indexOfLine(lines, hit);
        if (i < 0) {
            return ChatUtilitiesManager.plainTextForMatching(hit.content());
        }
        int blockStart = i;
        while (blockStart > 0 && !lines.get(blockStart).endOfEntry()) {
            blockStart--;
        }
        int blockEnd = i;
        while (blockEnd < lines.size() - 1 && !lines.get(blockEnd + 1).endOfEntry()) {
            blockEnd++;
        }
        StringBuilder sb = new StringBuilder();
        for (int k = blockEnd; k >= blockStart; k--) {
            FormattedCharSequence seq = lines.get(k).content();
            if (seq != null && !FormattedCharSequence.EMPTY.equals(seq)) {
                sb.append(ChatUtilitiesManager.plainTextForMatching(seq));
            }
        }
        return sb.toString();
    }

    /** Whether this trimmed row’s logical message matches the open-chat search (full entry, not one wrap row). */
    public static boolean vanillaTrimmedLineMatchesOpenChatSearch(ChatComponent chat, GuiMessage.Line line) {
        if (!ChatSearchState.isFiltering()) {
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || !(mc.screen instanceof ChatScreen)) {
            return true;
        }
        if (!ChatUtilitiesClientOptions.isChatSearchBarEnabled()) {
            return true;
        }
        return ChatSearchState.matchesPlain(entryPlainTextForMatching(chat, line));
    }

    /** No-op consumer; real work happens in {@link #notifyLineDuringPick} via mixin redirect. */
    private static final class LineConsumerHandler implements InvocationHandler {
        @Override
        public @Nullable Object invoke(Object proxy, Method method, @Nullable Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" ->
                            args != null && args.length == 1 && proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "VanillaChatLinePicker$LineConsumer";
                    default -> null;
                };
            }
            return null;
        }
    }

    private static final class LinesCollectHandler implements InvocationHandler {
        private final List<GuiMessage.Line> out;

        LinesCollectHandler(List<GuiMessage.Line> out) {
            this.out = out;
        }

        @Override
        public @Nullable Object invoke(Object proxy, Method method, @Nullable Object[] args) {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" ->
                            args != null && args.length == 1 && proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "VanillaChatLinePicker$LinesCollectHandler";
                    default -> null;
                };
            }
            if ("accept".equals(method.getName())
                    && args != null
                    && args.length >= 1
                    && args[0] instanceof GuiMessage.Line ln) {
                float op = 1f;
                if (args.length >= 3 && args[2] instanceof Number num) {
                    op = num.floatValue();
                }
                if (op > 0.01f) {
                    out.add(ln);
                }
            }
            return null;
        }
    }

    /** Hit on a wrapped chat row with X offset into {@link GuiMessage.Line#content()} for style picking. */
    public record LineHit(GuiMessage.Line line, int relXInContent) {}

    /**
     * Same layout as {@link #pickGuiLineAt}, plus horizontal offset along the row for
     * {@link me.braydon.chatutilities.chat.ChatWindowClickHandler#styleAtRelativeX hit-testing}.
     */
    public static Optional<LineHit> pickLineHitAt(Minecraft mc, int mouseX, int mouseY) {
        ChatComponent chat = mc.gui.getChat();
        if (mc.options.chatVisibility().get() == ChatVisiblity.HIDDEN) {
            return Optional.empty();
        }
        ChatComponent.AlphaCalculator alpha = ChatComponent.AlphaCalculator.FULLY_VISIBLE;
        PickerState state = new PickerState(mc, chat, mouseX, mouseY, guiScaledHeight(mc));
        Object consumer =
                Proxy.newProxyInstance(
                        ChatComponent.LineConsumer.class.getClassLoader(),
                        new Class<?>[] {ChatComponent.LineConsumer.class},
                        new LineConsumerHandler());
        PICK_CAPTURE.set(state);
        try {
            FOR_EACH_LINE.invoke(chat, alpha, consumer);
        } catch (ReflectiveOperationException e) {
            return Optional.empty();
        } finally {
            PICK_CAPTURE.set(null);
        }
        if (state.pickedLine == null || state.pickedRelX < 0) {
            return Optional.empty();
        }
        return Optional.of(new LineHit(state.pickedLine, state.pickedRelX));
    }

    private static final class PickerState {
        /** Mouse in chat-local space (matches {@link ChatComponent} pose after updatePose). */
        private final float localMouseX;
        private final float localMouseY;
        /** Same as {@code ChatComponent} render: {@code floor((guiHeight - 40) / scale)}. */
        private final int chatBottom;
        /** Row height; same formula as {@link ChatComponent#getLineHeight()}. */
        private final int entryHeight;
        /** {@code ceil(getWidth() / scale)}; background fill uses x in {@code [-4, this+8)}. */
        private final int rowInnerWidth;
        /** Extra width in chat-local X so the cursor can sit on the Jump chip to the right of the text column. */
        private final float searchJumpPadLocalX;
        private GuiMessage.@Nullable Line pickedLine;
        /** Set with {@link #pickedLine}; horizontal offset into {@link GuiMessage.Line#content()}. */
        private int pickedRelX = -1;

        PickerState(Minecraft mc, ChatComponent chat, int mouseX, int mouseY, int guiHeight) {
            double scale = chat.getScale();
            float sf = (float) scale;
            var pose = new Matrix3x2f();
            pose.scale(sf, sf);
            pose.translate(4.0f, 0.0f);
            var inv = new Matrix3x2f(pose);
            inv.invert();
            Vector2f local = inv.transformPosition((float) mouseX, (float) mouseY, new Vector2f());
            this.localMouseX = local.x;
            this.localMouseY = local.y;
            this.chatBottom = Mth.floor((guiHeight - 40) / sf);
            this.entryHeight = Math.max(1, chat.getLineHeight());
            this.rowInnerWidth = Mth.ceil(chat.getWidth() / sf);
            float pad = 0f;
            if (ChatUtilitiesClientOptions.isChatSearchBarEnabled() && mc.screen instanceof ChatScreen) {
                if (ChatSearchState.isFiltering()) {
                    int wScr =
                            mc.font.width(Component.translatable("chat-utilities.chat_search.jump")) + 24;
                    pad = wScr / sf;
                }
            }
            this.searchJumpPadLocalX = pad;
        }

        void onLine(GuiMessage.Line line, int lineIndex, float opacity) {
            if (opacity <= 0.01f) {
                return;
            }
            FormattedCharSequence seq = line.content();
            if (seq == null || FormattedCharSequence.EMPTY.equals(seq)) {
                return;
            }
            int slide = vanillaLineSlideY(line);
            int rowBottom = this.chatBottom - lineIndex * this.entryHeight + slide;
            int rowTop = rowBottom - this.entryHeight;
            if (localMouseX < -4 || localMouseX >= rowInnerWidth + 8 + searchJumpPadLocalX) {
                return;
            }
            if (localMouseY < rowTop || localMouseY >= rowBottom) {
                return;
            }
            pickedLine = line;
            pickedRelX = Mth.clamp(Mth.floor(localMouseX + 4f), 0, 16384);
        }
    }

    /**
     * One logical message becomes several {@link GuiMessage.Line}s when wrapped. Vanilla pushes each wrapped row
     * with {@code addFirst}, so {@code trimmedMessages} runs <strong>newest-first</strong>: within one message, the
     * row with {@link GuiMessage.Line#endOfEntry()} {@code true} is the <em>last</em> wrapped row when splitting but
     * sits at the <em>lowest</em> index of that message's contiguous run. Copy joins the whole run in on-screen
     * order (top line first).
     */
    private static Optional<Component> entryComponentForLine(ChatComponent chat, GuiMessage.@Nullable Line hit) {
        if (hit == null) {
            return Optional.empty();
        }
        @SuppressWarnings("unchecked")
        List<GuiMessage.Line> lines = (List<GuiMessage.Line>) chat.trimmedMessages;
        int i = indexOfLine(lines, hit);
        if (i < 0) {
            return Optional.of(formattedSequenceToComponent(hit.content()));
        }
        int blockStart = i;
        while (blockStart > 0 && !lines.get(blockStart).endOfEntry()) {
            blockStart--;
        }
        int blockEnd = i;
        while (blockEnd < lines.size() - 1 && !lines.get(blockEnd + 1).endOfEntry()) {
            blockEnd++;
        }
        MutableComponent out = Component.empty();
        for (int k = blockEnd; k >= blockStart; k--) {
            if (k < blockEnd) {
                out.append(Component.literal("\n"));
            }
            FormattedCharSequence seq = lines.get(k).content();
            if (seq != null && !FormattedCharSequence.EMPTY.equals(seq)) {
                out.append(formattedSequenceToComponent(seq));
            }
        }
        return Optional.of(out);
    }

    private static int indexOfLine(List<GuiMessage.Line> lines, GuiMessage.Line hit) {
        for (int j = 0; j < lines.size(); j++) {
            if (lines.get(j) == hit) {
                return j;
            }
        }
        int tick = hit.addedTime();
        for (int j = 0; j < lines.size(); j++) {
            GuiMessage.Line line = lines.get(j);
            if (line.addedTime() == tick && line.equals(hit)) {
                return j;
            }
        }
        return -1;
    }

    private static int guiScaledHeight(Minecraft mc) {
        return mc.getWindow().getGuiScaledHeight();
    }

    /**
     * Screen-space Y (gui px) of the bottom edge of the lowest visible expanded-chat row (same pose as vanilla
     * {@link ChatComponent} rendering). Used to lay out UI (e.g. search bar) in the gutter above the chat input.
     */
    public static int expandedChatLowestLineBottomScreenY(Minecraft mc) {
        ChatComponent chat = mc.gui.getChat();
        double scale = chat.getScale();
        float sf = (float) scale;
        int gh = guiScaledHeight(mc);
        int chatBottomLocal = Mth.floor((gh - 40) / sf);
        int rowBottomLocal = chatBottomLocal;
        AtomicReference<GuiMessage.Line> bottom = new AtomicReference<>();
        AtomicInteger minIdx = new AtomicInteger(Integer.MAX_VALUE);
        visitDrawnViewportLines(
                chat,
                (ln, idx, op) -> {
                    if (idx < minIdx.get()) {
                        minIdx.set(idx);
                        bottom.set(ln);
                    }
                });
        GuiMessage.Line bl = bottom.get();
        if (bl != null) {
            int slide = vanillaLineSlideY(bl);
            rowBottomLocal = chatBottomLocal + slide;
        }
        var pose = new Matrix3x2f();
        pose.scale(sf, sf);
        pose.translate(4.0f, 0.0f);
        Vector2f sp = pose.transformPosition(0f, (float) rowBottomLocal, new Vector2f());
        return Mth.ceil(sp.y);
    }

    /**
     * Screen-space Y (gui px) of the top edge of the expanded chat column as if the viewport were full (same pose as
     * vanilla {@link ChatComponent} rendering), not tied to the current scroll position. Used to place UI above the
     * whole chat column.
     */
    public static int expandedChatHighestLineTopScreenY(Minecraft mc) {
        ChatComponent chat = mc.gui.getChat();
        double scale = chat.getScale();
        float sf = (float) scale;
        int gh = guiScaledHeight(mc);
        int chatBottomLocal = Mth.floor((gh - 40) / sf);
        int entryHeight = Math.max(1, chat.getLineHeight());
        int rowsFitting = Math.max(1, chatBottomLocal / entryHeight);
        int rowTopLocal = chatBottomLocal - rowsFitting * entryHeight;
        var pose = new Matrix3x2f();
        pose.scale(sf, sf);
        pose.translate(4.0f, 0.0f);
        Vector2f sp = pose.transformPosition(0f, (float) rowTopLocal, new Vector2f());
        return Mth.floor(sp.y);
    }

    /**
     * Screen-space Y (gui px) of the top edge of the topmost <em>visible</em> expanded-chat row (same pose as vanilla
     * {@link ChatComponent} rendering). Used to place UI immediately above the on-screen chat stack.
     */
    public static int expandedChatTopVisibleLineTopScreenY(Minecraft mc) {
        ChatComponent chat = mc.gui.getChat();
        if (mc.options.chatVisibility().get() == ChatVisiblity.HIDDEN) {
            return expandedChatHighestLineTopScreenY(mc);
        }
        AtomicReference<GuiMessage.Line> top = new AtomicReference<>();
        AtomicInteger maxIdx = new AtomicInteger(-1);
        visitDrawnViewportLines(
                chat,
                (ln, idx, op) -> {
                    if (idx > maxIdx.get()) {
                        maxIdx.set(idx);
                        top.set(ln);
                    }
                });
        GuiMessage.Line topLine = top.get();
        if (topLine == null) {
            return expandedChatHighestLineTopScreenY(mc);
        }
        int idx = maxIdx.get();
        double scale = chat.getScale();
        float sf = (float) scale;
        int gh = guiScaledHeight(mc);
        int chatBottom = Mth.floor((gh - 40) / sf);
        int entryHeight = Math.max(1, chat.getLineHeight());
        int slide = vanillaLineSlideY(topLine);
        int rowBottom = chatBottom - idx * entryHeight + slide;
        int rowTop = rowBottom - entryHeight;
        var pose = new Matrix3x2f();
        pose.scale(sf, sf);
        pose.translate(4.0f, 0.0f);
        Vector2f sp = pose.transformPosition(0f, (float) rowTop, new Vector2f());
        return Mth.floor(sp.y);
    }

    /**
     * Screen-space Y (gui px) at the vertical center of the row for {@code line}, using the same viewport slot index
     * as vanilla chat rendering (including open-chat search: non-matching rows are skipped but slots stay in send
     * order).
     */
    public static Optional<Float> guiScreenCenterYForGuiLine(Minecraft mc, GuiMessage.Line line) {
        ChatComponent chat = mc.gui.getChat();
        if (mc.options.chatVisibility().get() == ChatVisiblity.HIDDEN) {
            return Optional.empty();
        }
        AtomicInteger idx = new AtomicInteger(-1);
        visitDrawnViewportLines(
                chat,
                (ln, i, op) -> {
                    if (ln == line) {
                        idx.set(i);
                    }
                });
        if (idx.get() < 0) {
            return Optional.empty();
        }
        double scale = chat.getScale();
        float sf = (float) scale;
        int gh = guiScaledHeight(mc);
        int chatBottom = Mth.floor((gh - 40) / sf);
        int entryHeight = Math.max(1, chat.getLineHeight());
        int slide = vanillaLineSlideY(line);
        int rowBottom = chatBottom - idx.get() * entryHeight + slide;
        int rowTop = rowBottom - entryHeight;
        float midLocalY = (rowTop + rowBottom) * 0.5f;
        var pose = new Matrix3x2f();
        pose.scale(sf, sf);
        pose.translate(4.0f, 0.0f);
        Vector2f sp = pose.transformPosition(0f, midLocalY, new Vector2f());
        return Optional.of(sp.y);
    }

    /**
     * Screen-space Y range {@code [min, max)} for the chat row containing {@code line} (same layout as
     * {@link #guiScreenCenterYForGuiLine}). Used to keep Jump UI active while the cursor moves from the text to the
     * button within the same row.
     */
    public static Optional<int[]> guiScreenRowScreenYBoundsForGuiLine(Minecraft mc, GuiMessage.Line line) {
        ChatComponent chat = mc.gui.getChat();
        if (mc.options.chatVisibility().get() == ChatVisiblity.HIDDEN) {
            return Optional.empty();
        }
        AtomicInteger idx = new AtomicInteger(-1);
        visitDrawnViewportLines(
                chat,
                (ln, i, op) -> {
                    if (ln == line) {
                        idx.set(i);
                    }
                });
        if (idx.get() < 0) {
            return Optional.empty();
        }
        double scale = chat.getScale();
        float sf = (float) scale;
        int gh = guiScaledHeight(mc);
        int chatBottom = Mth.floor((gh - 40) / sf);
        int entryHeight = Math.max(1, chat.getLineHeight());
        int slide = vanillaLineSlideY(line);
        int rowBottom = chatBottom - idx.get() * entryHeight + slide;
        int rowTop = rowBottom - entryHeight;
        var pose = new Matrix3x2f();
        pose.scale(sf, sf);
        pose.translate(4.0f, 0.0f);
        Vector2f tTop = pose.transformPosition(0f, (float) rowTop, new Vector2f());
        Vector2f tBot = pose.transformPosition(0f, (float) rowBottom, new Vector2f());
        int yMin = Mth.floor(Math.min(tTop.y, tBot.y));
        int yMax = Mth.ceil(Math.max(tTop.y, tBot.y));
        int yPad = 4;
        return Optional.of(new int[] {yMin - yPad, yMax + yPad});
    }

    /** True if {@code (mouseX, mouseY)} lies in the full-width horizontal strip for that chat row. */
    public static boolean guiScreenMouseInVerticalBandForGuiLine(
            Minecraft mc, GuiMessage.Line line, int mouseX, int mouseY, int screenW, int screenH) {
        Optional<int[]> span = guiScreenRowScreenYBoundsForGuiLine(mc, line);
        if (span.isEmpty()) {
            return false;
        }
        int yMin = span.get()[0];
        int yMax = span.get()[1];
        if (mouseY < yMin || mouseY >= yMax) {
            return false;
        }
        return mouseX >= 0 && mouseX < screenW && mouseY >= 0 && mouseY < screenH;
    }

    private static Component formattedSequenceToComponent(FormattedCharSequence seq) {
        MutableComponent out = Component.empty();
        seq.accept(
                (index, style, codePoint) -> {
                    out.append(
                            Component.literal(new String(Character.toChars(codePoint)))
                                    .withStyle(style));
                    return true;
                });
        return out;
    }

    private static int vanillaLineSlideY(GuiMessage.Line line) {
        if (ChatUtilitiesManager.get().shouldSuppressVanillaSmoothForLine(line)) {
            return 0;
        }
        return ChatSmoothAppearance.fadeSlideOffsetYPixels(line.addedTime());
    }
}
