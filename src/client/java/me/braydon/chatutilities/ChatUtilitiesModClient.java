package me.braydon.chatutilities;

import me.braydon.chatutilities.chat.*;
import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import me.braydon.chatutilities.command.ChatUtilitiesCommands;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class ChatUtilitiesModClient implements ClientModInitializer {
    /** Keybind to open the Chat Utilities menu. Defaults to the Menu key; configurable in Controls or in-game Settings. */
    public static KeyMapping OPEN_MENU_KEY;

    /** Own category so the binding is easy to find in vanilla Controls and matches our Settings panel. */
    public static KeyMapping.Category CHAT_UTILITIES_KEY_CATEGORY;

    @Override
    public void onInitializeClient() {
        ChatUtilitiesClientOptions.init();
        CHAT_UTILITIES_KEY_CATEGORY =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("chatutilities", "root"));
        OPEN_MENU_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.chatutilities.open_menu",
                GLFW.GLFW_KEY_MENU,
                CHAT_UTILITIES_KEY_CATEGORY
        ));

        ChatUtilitiesManager.get().init();
        ChatUtilitiesHud.register();
        ChatUtilitiesScreenHooks.register();
        ChatUtilitiesTick.register();
        ChatUtilitiesCommands.register();
    }
}
