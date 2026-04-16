package me.braydon.chatutilities.chat;

import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import me.braydon.chatutilities.mixin.client.ChatComponentAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.ChatVisiblity;

import java.util.List;

/**
 * Keeps vanilla chat scroll aligned with the search filter so the user cannot scroll into a viewport where every
 * visible line is hidden (opacity 0).
 */
public final class ChatSearchVanillaScrollClamp {
    private ChatSearchVanillaScrollClamp() {}

    public static void apply(Minecraft mc) {
        if (mc == null || !(mc.screen instanceof ChatScreen)) {
            return;
        }
        if (!ChatUtilitiesClientOptions.isChatSearchBarEnabled() || !ChatSearchState.isFiltering()) {
            return;
        }
        ChatComponent chat = mc.gui.getChat();
        if (mc.options.chatVisibility().get() == ChatVisiblity.HIDDEN) {
            return;
        }
        ChatComponentAccess access = (ChatComponentAccess) chat;
        ChatMessageRebuildGuard.enter();
        try {
            access.chatUtilities$refreshTrimmedMessages();
        } finally {
            ChatMessageRebuildGuard.exit();
        }
        List<?> trimmed = access.chatUtilities$getTrimmedMessages();
        int page = chat.getLinesPerPage();
        int maxVanilla = Math.max(0, trimmed.size() - page);
        int pos = Mth.clamp(access.chatUtilities$getChatScrollbarPos(), 0, maxVanilla);

        while (pos > 0) {
            access.chatUtilities$setChatScrollbarPos(pos);
            if (!VanillaChatLinePicker.collectVisibleGuiLines(chat).isEmpty()) {
                break;
            }
            pos--;
        }
        access.chatUtilities$setChatScrollbarPos(pos);

        while (pos < maxVanilla) {
            access.chatUtilities$setChatScrollbarPos(pos + 1);
            if (VanillaChatLinePicker.collectVisibleGuiLines(chat).isEmpty()) {
                access.chatUtilities$setChatScrollbarPos(pos);
                break;
            }
            pos++;
        }
        access.chatUtilities$setChatScrollbarPos(pos);
    }
}
