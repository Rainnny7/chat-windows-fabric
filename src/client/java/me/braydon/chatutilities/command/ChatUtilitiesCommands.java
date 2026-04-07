package me.braydon.chatutilities.command;

import me.braydon.chatutilities.chat.ChatUtilitiesManager;
import me.braydon.chatutilities.gui.ChatUtilitiesRootScreen;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

public final class ChatUtilitiesCommands {
    private ChatUtilitiesCommands() {}

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> {
                    dispatcher.register(literal("chatutilities").executes(ctx -> openUi(ctx.getSource())));
                    dispatcher.register(literal("chatutils").executes(ctx -> openUi(ctx.getSource())));
                });
    }

    private static int openUi(FabricClientCommandSource source) {
        openMenuNextTick(source.getClient());
        return 1;
    }

    /**
     * Opens the Chat Utilities root screen on the next client tick (same as {@code /chatutils}). Deferred so callers
     * from {@link net.minecraft.client.gui.screens.ChatScreen} do not fight screen teardown in the same tick.
     */
    public static void openMenuNextTick(Minecraft minecraft) {
        if (minecraft == null) {
            return;
        }
        ChatUtilitiesManager mgr = ChatUtilitiesManager.get();
        mgr.pushClientCommandFeedback();
        try {
            minecraft.execute(() -> minecraft.setScreen(new ChatUtilitiesRootScreen(null)));
        } finally {
            mgr.popClientCommandFeedback();
        }
    }
}
