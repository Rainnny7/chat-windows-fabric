package me.braydon.chatutilities;

import me.braydon.chatutilities.chat.*;
import me.braydon.chatutilities.command.ChatUtilitiesCommands;
import net.fabricmc.api.ClientModInitializer;

public class ChatUtilitiesModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ChatUtilitiesManager.get().init();
        ChatUtilitiesHud.register();
        ChatUtilitiesScreenHooks.register();
        ChatUtilitiesTick.register();
        ChatUtilitiesCommands.register();
    }
}
