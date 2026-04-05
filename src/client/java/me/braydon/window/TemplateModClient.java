package me.braydon.window;

import me.braydon.window.chat.ChatWindowManager;
import me.braydon.window.chat.ChatWindowsCommands;
import me.braydon.window.chat.ChatWindowsHud;
import me.braydon.window.chat.ChatWindowsScreenHooks;
import me.braydon.window.chat.ChatWindowsTick;
import net.fabricmc.api.ClientModInitializer;

public class TemplateModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ChatWindowManager.get().init();
        ChatWindowsHud.register();
        ChatWindowsScreenHooks.register();
        ChatWindowsTick.register();
        ChatWindowsCommands.register();
    }
}
