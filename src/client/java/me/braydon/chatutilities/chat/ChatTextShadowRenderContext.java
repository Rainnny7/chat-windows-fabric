package me.braydon.chatutilities.chat;

/**
 * Marks call stacks where vanilla {@link net.minecraft.client.gui.components.ChatComponent} is
 * rendering, so {@link me.braydon.chatutilities.mixin.client.GuiGraphicsMixin} can override text shadow.
 */
public final class ChatTextShadowRenderContext {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);

    public static void enter() {
        DEPTH.set(DEPTH.get() + 1);
    }

    public static void exit() {
        int n = DEPTH.get() - 1;
        DEPTH.set(Math.max(0, n));
    }

    public static boolean isActive() {
        return DEPTH.get() > 0;
    }

    private ChatTextShadowRenderContext() {}
}
