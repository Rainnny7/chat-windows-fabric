package me.braydon.chatutilities.chat;

import me.braydon.chatutilities.mixin.client.ChatComponentAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;

import java.util.List;

final class ChatSearchJump {
    private ChatSearchJump() {}

    static void scrollVanillaToLine(Minecraft mc, GuiMessage.Line target) {
        if (target == null) {
            return;
        }
        ChatComponent chat = mc.gui.getChat();
        ChatComponentAccess access = (ChatComponentAccess) chat;
        access.chatUtilities$refreshTrimmedMessages();
        List<GuiMessage.Line> trimmed = access.chatUtilities$getTrimmedMessages();
        int page = chat.getLinesPerPage();
        int maxScroll = Math.max(0, trimmed.size() - page);
        for (int s = 0; s <= maxScroll; s++) {
            access.chatUtilities$setChatScrollbarPos(s);
            if (VanillaChatLinePicker.collectVisibleGuiLines(chat).contains(target)) {
                return;
            }
        }
    }
}
