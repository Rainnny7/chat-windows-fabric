package me.braydon.chatutilities.gui;

import me.braydon.chatutilities.chat.ChatUtilitiesManager;
import me.braydon.chatutilities.chat.ServerProfile;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/** Asks before removing a server profile (windows, ignores, message sounds, and server rules are lost). */
public final class DeleteProfileConfirmScreen extends Screen {
    private final Screen cancelDestination;
    private final Screen afterDeleteDestination;
    private final String profileId;

    public DeleteProfileConfirmScreen(
            Screen cancelDestination, Screen afterDeleteDestination, String profileId) {
        super(Component.literal("Delete Profile?"));
        this.cancelDestination = cancelDestination;
        this.afterDeleteDestination = afterDeleteDestination;
        this.profileId = profileId;
    }

    @Override
    protected void init() {
        if (ChatUtilitiesManager.get().getProfile(profileId) == null) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(cancelDestination);
            }
            return;
        }

        int cx = this.width / 2;
        int btnY = this.height / 2 + 36;

        addRenderableWidget(
                Button.builder(Component.literal("Delete"), b -> {
                            ChatUtilitiesManager.get().removeProfile(profileId);
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(afterDeleteDestination);
                            }
                        })
                        .bounds(cx - 105, btnY, 100, 20)
                        .build());
        addRenderableWidget(
                Button.builder(Component.literal("Cancel"), b -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(cancelDestination);
                            }
                        })
                        .bounds(cx + 5, btnY, 100, 20)
                        .build());
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(cancelDestination);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);

        ServerProfile p = ChatUtilitiesManager.get().getProfile(profileId);
        String name = p != null ? p.getDisplayName() : profileId;

        graphics.drawCenteredString(
                this.font,
                this.title,
                this.width / 2,
                ChatUtilitiesScreenLayout.TITLE_Y,
                ChatUtilitiesScreenLayout.TEXT_WHITE);

        int msgW = Math.min(260, this.width - 40);
        int centerY = this.height / 2;
        List<FormattedCharSequence> lines =
                this.font.split(
                        Component.literal(
                                "Remove \"" + name + "\" and its windows, ignores, message sounds, and server rules? You cannot undo this."),
                        msgW);
        int msgH = lines.size() * 12;
        int msgTop = centerY - msgH / 2 - 14;
        int y = msgTop;
        for (FormattedCharSequence line : lines) {
            int lineW = this.font.width(line);
            graphics.drawString(
                    this.font, line, this.width / 2 - lineW / 2, y, ChatUtilitiesScreenLayout.TEXT_GRAY, false);
            y += 12;
        }
    }
}
