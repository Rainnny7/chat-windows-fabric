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
        ChatUtilitiesManager mgr = ChatUtilitiesManager.get();
        mgr.pushClientCommandFeedback();
        try {
            // Opening a Screen in the same tick as chat command handling often fails: ChatScreen
            // closes afterward and replaces the screen. Defer to next client tick.
            Minecraft mc = source.getClient();
            mc.execute(() -> mc.setScreen(new ChatUtilitiesRootScreen(null)));
            return 1;
        } finally {
            mgr.popClientCommandFeedback();
        }
    }
}
