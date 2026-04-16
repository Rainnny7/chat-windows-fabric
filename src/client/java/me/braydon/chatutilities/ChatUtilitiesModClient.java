package me.braydon.chatutilities;

import me.braydon.chatutilities.chat.ChatUtilitiesHud;
import me.braydon.chatutilities.chat.ChatUtilitiesManager;
import me.braydon.chatutilities.chat.CommandAliasOutgoingHook;
import me.braydon.chatutilities.chat.ChatUtilitiesScreenHooks;
import me.braydon.chatutilities.chat.ChatUtilitiesTick;
import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import me.braydon.chatutilities.command.ChatUtilitiesCommands;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLevelEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

public class ChatUtilitiesModClient implements ClientModInitializer {
    /** Keybind to open the Chat Utilities menu. Defaults to the Menu key; configurable in Controls or in-game Settings. */
    public static KeyMapping OPEN_MENU_KEY;

    /** Hold to temporarily expand all chat windows (peek). */
    public static KeyMapping CHAT_PEEK_KEY;

    /** Own category so the binding is easy to find in vanilla Controls and matches our Settings panel. */
    public static KeyMapping.Category CHAT_UTILITIES_KEY_CATEGORY;

    @Override
    public void onInitializeClient() {
        ChatUtilitiesClientOptions.init();
        CHAT_UTILITIES_KEY_CATEGORY =
                KeyMapping.Category.register(Identifier.fromNamespaceAndPath("chatutilities", "root"));
        OPEN_MENU_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.chatutilities.open_menu",
                GLFW.GLFW_KEY_MENU,
                CHAT_UTILITIES_KEY_CATEGORY
        ));
        CHAT_PEEK_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.chatutilities.chat_peek",
                GLFW.GLFW_KEY_RIGHT_ALT,
                CHAT_UTILITIES_KEY_CATEGORY
        ));

        ChatUtilitiesManager.get().init();
        CommandAliasOutgoingHook.register();
        ChatUtilitiesHud.register();
        ChatUtilitiesScreenHooks.register();
        ChatUtilitiesTick.register();
        ChatUtilitiesCommands.register();

        // Signal the manager as early as possible so early-arriving chat (sent before the
        // game-join packet) can be buffered and replayed with the correct server profile.
        // INIT fires at the very start of the login phase, before any play-state packets.
        ClientLoginConnectionEvents.INIT.register((handler, client) ->
                ChatUtilitiesManager.get().onLoginStart());

        // Eagerly resolve the active profile when the player enters the play state.
        // Run SYNCHRONOUSLY (no client.execute()) so the profile cache is populated
        // before any server messages can arrive.  Pass the server hostname from the
        // handler directly — handler.getServerData() is always populated at JOIN time,
        // unlike mc.getCurrentServer() which can lag on first login (causing the raw-IP
        // fallback to return an IP that never matches a hostname-configured profile).
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ServerData sd = handler.getServerData();
            String host = null;
            if (sd != null && sd.ip != null && !sd.ip.isBlank()) {
                host = ChatUtilitiesManager.stripPortFromAddress(sd.ip);
            }
            if (host == null || host.isBlank()) {
                ServerData sd2 = client.getCurrentServer();
                if (sd2 != null && sd2.ip != null && !sd2.ip.isBlank()) {
                    host = ChatUtilitiesManager.stripPortFromAddress(sd2.ip);
                }
            }
            ChatUtilitiesManager.get().onPlayJoin(host);
        });

        // Clear stale per-connection profile state on disconnect so the next server gets a clean
        // profile resolution rather than inheriting the previous server's cached selection.
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ChatUtilitiesManager.get().onPlayDisconnect());

        // When the client level is torn down (e.g. disconnect to title), capture chat before it is replaced/cleared.
        ClientLevelEvents.AFTER_CLIENT_LEVEL_CHANGE.register((client, level) -> {
            if (level == null) {
                ChatUtilitiesManager.get().snapshotVanillaChatIfPreserving(client);
            }
        });
    }
}
