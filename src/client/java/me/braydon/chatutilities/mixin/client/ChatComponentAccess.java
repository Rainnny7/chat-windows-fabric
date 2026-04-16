package me.braydon.chatutilities.mixin.client;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatComponent.class)
public interface ChatComponentAccess {
    @Accessor("allMessages")
    List<GuiMessage> chatUtilities$getAllMessages();

    @Accessor("trimmedMessages")
    List<GuiMessage.Line> chatUtilities$getTrimmedMessages();

    @Accessor("chatScrollbarPos")
    int chatUtilities$getChatScrollbarPos();

    @Accessor("chatScrollbarPos")
    void chatUtilities$setChatScrollbarPos(int pos);

    @Invoker("refreshTrimmedMessages")
    void chatUtilities$refreshTrimmedMessages();

    @Invoker("resetChatScroll")
    void chatUtilities$resetChatScroll();
}
