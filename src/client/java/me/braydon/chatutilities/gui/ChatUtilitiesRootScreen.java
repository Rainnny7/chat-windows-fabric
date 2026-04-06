package me.braydon.chatutilities.gui;

import me.braydon.chatutilities.chat.ChatUtilitiesManager;
import me.braydon.chatutilities.chat.ServerProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.List;

public class ChatUtilitiesRootScreen extends Screen {
    private static final String ROOT_HINT =
            "Each profile stores chat windows, ignored-message rules, and message sounds, tied to the server addresses you configure. "
                    + "When you join a server, the mod uses the profile whose hosts match that connection. "
                    + "Select a profile to open its tools or ignored chat; double-click or use Edit Profile to change its name and servers.";

    private static final int FOOTER_GAP = 4;

    private final Screen parent;
    private ServerProfileListWidget profileList;
    private Button deleteProfileButton;
    private Button chatWindowsButton;
    private Button ignoredMessagesButton;
    private Button messageSoundsButton;
    private int profileListTop;
    private int profileListBottom;

    public ChatUtilitiesRootScreen(Screen parent) {
        super(Component.literal("Server Profiles"));
        this.parent = parent;
    }

    public Screen getParentScreen() {
        return parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        Minecraft mc = Minecraft.getInstance();
        int lw = ChatUtilitiesScreenLayout.listWidth(this);
        int lx = ChatUtilitiesScreenLayout.listLeft(this);

        int hintMaxW = Math.min(lw, 340);
        List<FormattedCharSequence> hintLines =
                this.font.split(Component.literal(ROOT_HINT), hintMaxW);
        int hintBottom = ChatUtilitiesScreenLayout.TITLE_Y + 12 + hintLines.size() * 10;
        profileListTop = hintBottom + 10;

        if (profileList == null) {
            profileList =
                    new ServerProfileListWidget(
                            mc,
                            p -> mc.setScreen(new ProfileEditorScreen(p.getId(), this)),
                            this::syncSelectionDependentButtons);
        }
        profileList.replaceProfiles(ChatUtilitiesManager.get().getProfiles());

        int bottom = ChatUtilitiesScreenLayout.contentBottomReserved(this);
        profileListBottom = bottom;
        profileList.setBounds(lx, profileListTop, lw, Math.max(40, profileListBottom - profileListTop));

        int cx = this.width / 2;
        int footerUpperY = ChatUtilitiesScreenLayout.footerSecondaryY(this);
        int footerLowerY = ChatUtilitiesScreenLayout.footerPrimaryY(this);
        int perRow = 3;
        int btnW =
                Mth.clamp(
                        (this.width - 40 - (perRow - 1) * FOOTER_GAP) / perRow,
                        72,
                        130);
        int rowW = perRow * btnW + (perRow - 1) * FOOTER_GAP;
        int left = cx - rowW / 2;

        // Upper row: Create Profile, Chat Windows, Chat Sounds
        addRenderableWidget(
                Button.builder(Component.literal("Create Profile"), b -> {
                            ServerProfile p =
                                    ChatUtilitiesManager.get()
                                            .createProfileForCurrentServer("New Profile");
                            mc.setScreen(new ProfileEditorScreen(p.getId(), this));
                        })
                        .bounds(left, footerUpperY, btnW, 20)
                        .build());

        chatWindowsButton =
                Button.builder(
                                Component.literal("Chat Windows"),
                                b -> {
                                    ServerProfile sel = profileList.getSelectedProfile();
                                    if (sel != null) {
                                        mc.setScreen(new ProfileWindowsScreen(sel.getId(), this));
                                    }
                                })
                        .bounds(left + btnW + FOOTER_GAP, footerUpperY, btnW, 20)
                        .build();
        addRenderableWidget(chatWindowsButton);

        messageSoundsButton =
                Button.builder(
                                Component.literal("Chat Sounds"),
                                b -> {
                                    ServerProfile sel = profileList.getSelectedProfile();
                                    if (sel != null) {
                                        mc.setScreen(new ProfileMessageSoundsScreen(sel.getId(), this));
                                    }
                                })
                        .bounds(left + 2 * (btnW + FOOTER_GAP), footerUpperY, btnW, 20)
                        .build();
        addRenderableWidget(messageSoundsButton);

        // Lower row: Ignored Chat, Delete Profile, Done
        ignoredMessagesButton =
                Button.builder(
                                Component.literal("Ignored Chat"),
                                b -> {
                                    ServerProfile sel = profileList.getSelectedProfile();
                                    if (sel != null) {
                                        mc.setScreen(new ProfileIgnoresScreen(sel.getId(), this));
                                    }
                                })
                        .bounds(left, footerLowerY, btnW, 20)
                        .build();
        addRenderableWidget(ignoredMessagesButton);

        deleteProfileButton =
                Button.builder(
                                Component.literal("Delete Profile"),
                                b -> {
                                    ServerProfile sel = profileList.getSelectedProfile();
                                    if (sel == null) {
                                        return;
                                    }
                                    mc.setScreen(
                                            new DeleteProfileConfirmScreen(
                                                    this, new ChatUtilitiesRootScreen(parent), sel.getId()));
                                })
                        .bounds(left + btnW + FOOTER_GAP, footerLowerY, btnW, 20)
                        .build();
        addRenderableWidget(deleteProfileButton);

        addRenderableWidget(
                Button.builder(Component.literal("Done"), b -> onClose())
                        .bounds(left + 2 * (btnW + FOOTER_GAP), footerLowerY, btnW, 20)
                        .build());

        syncSelectionDependentButtons();
    }

    private void syncSelectionDependentButtons() {
        boolean sel = profileList != null && profileList.getSelectedProfile() != null;
        if (deleteProfileButton != null) {
            deleteProfileButton.active = sel;
        }
        if (chatWindowsButton != null) {
            chatWindowsButton.active = sel;
        }
        if (ignoredMessagesButton != null) {
            ignoredMessagesButton.active = sel;
        }
        if (messageSoundsButton != null) {
            messageSoundsButton.active = sel;
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (profileList != null
                && profileList.mouseClicked(event.x(), event.y(), event.button(), doubleClick)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (profileList != null && profileList.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int lx = ChatUtilitiesScreenLayout.listLeft(this);
        int lw = ChatUtilitiesScreenLayout.listWidth(this);

        graphics.drawCenteredString(
                this.font,
                this.title,
                this.width / 2,
                ChatUtilitiesScreenLayout.TITLE_Y,
                ChatUtilitiesScreenLayout.TEXT_WHITE);

        int hintMaxW = Math.min(lw, 340);
        ChatUtilitiesScreenLayout.drawCenteredWrapped(
                this.font,
                graphics,
                Component.literal(ROOT_HINT),
                this.width / 2,
                ChatUtilitiesScreenLayout.TITLE_Y + 12,
                hintMaxW,
                ChatUtilitiesScreenLayout.TEXT_GRAY,
                10);

        graphics.fill(lx - 2, profileListTop - 2, lx + lw + 2, profileListBottom + 2, 0x66000000);
        graphics.renderOutline(
                lx - 2, profileListTop - 2, lw + 4, profileListBottom - profileListTop + 4, 0xFF555555);
        if (profileList != null) {
            profileList.render(graphics, mouseX, mouseY);
        }
    }
}
