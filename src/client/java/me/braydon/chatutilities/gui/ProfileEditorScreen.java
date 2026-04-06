package me.braydon.chatutilities.gui;

import me.braydon.chatutilities.chat.ChatUtilitiesManager;
import me.braydon.chatutilities.chat.ServerProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Locale;

/** Profile display name and which servers use this profile. */
public class ProfileEditorScreen extends Screen implements ProfileWorkflowScreen {
    private static final int SERVER_ROWS = 5;
    /** First row of widgets; leaves room for title + intro under title. */
    /** Below title + wrapped intro (intro can be several lines). */
    private static final int BODY_TOP = 114;

    private final String profileId;
    private final ChatUtilitiesRootScreen chatRoot;
    private EditBox nameField;
    private EditBox newServerField;
    private int serverScroll;

    private int labelNameY;
    private int labelServersY;

    public ProfileEditorScreen(String profileId, ChatUtilitiesRootScreen chatRoot) {
        super(Component.literal("Edit Profile"));
        this.profileId = profileId;
        this.chatRoot = chatRoot;
    }

    @Override
    public ChatUtilitiesRootScreen getChatRoot() {
        return chatRoot;
    }

    @Override
    public Screen recreateForProfile() {
        return new ProfileEditorScreen(profileId, chatRoot);
    }

    /** Persists the name field into the profile when non-empty (call before {@code init()} from button handlers). */
    private void applyNameFieldToProfile(ChatUtilitiesManager mgr, ServerProfile p) {
        if (nameField == null) {
            return;
        }
        String n = nameField.getValue().strip();
        if (!n.isEmpty()) {
            p.setDisplayName(n);
            mgr.markProfileServersDirty();
        }
    }

    @Override
    protected void init() {
        clearWidgets();
        ChatUtilitiesManager mgr = ChatUtilitiesManager.get();
        ServerProfile p = mgr.getProfile(profileId);
        if (p == null) {
            onClose();
            return;
        }

        int cx = this.width / 2;
        int y = BODY_TOP;

        labelNameY = y - 11;
        nameField = new EditBox(this.font, cx - 100, y, 200, 20, Component.literal("name"));
        nameField.setValue(p.getDisplayName());
        nameField.setMaxLength(128);
        nameField.setHint(Component.literal("e.g. Hypixel or My SMP"));
        addRenderableWidget(nameField);
        y += 26;

        labelServersY = y - 11;
        newServerField = new EditBox(this.font, cx - 100, y, 130, 20, Component.literal("host"));
        newServerField.setMaxLength(255);
        newServerField.setHint(Component.literal("e.g. wildnetwork.net"));
        addRenderableWidget(newServerField);
        addRenderableWidget(
                Button.builder(Component.literal("Add Server"), b -> {
                            applyNameFieldToProfile(mgr, p);
                            String h = newServerField.getValue().strip().toLowerCase(Locale.ROOT);
                            if (!h.isEmpty() && !p.getServers().contains(h)) {
                                p.getServers().add(h);
                                mgr.markProfileServersDirty();
                                newServerField.setValue("");
                            }
                            init();
                        })
                        .bounds(cx + 38, y, 62, 20)
                        .build());
        y += 24;

        List<String> servers = p.getServers();
        int srvMax = Math.max(0, servers.size() - SERVER_ROWS);
        serverScroll = Math.min(serverScroll, srvMax);
        if (serverScroll > 0) {
            addRenderableWidget(
                    Button.builder(Component.literal("Up"), b -> {
                                applyNameFieldToProfile(mgr, p);
                                serverScroll = Math.max(0, serverScroll - SERVER_ROWS);
                                init();
                            })
                            .bounds(cx - 100, y, 95, 20)
                            .build());
        }
        if (serverScroll + SERVER_ROWS < servers.size()) {
            addRenderableWidget(
                    Button.builder(Component.literal("Down"), b -> {
                                applyNameFieldToProfile(mgr, p);
                                serverScroll = Math.min(srvMax, serverScroll + SERVER_ROWS);
                                init();
                            })
                            .bounds(serverScroll > 0 ? cx + 5 : cx - 100, y, 95, 20)
                            .build());
        }
        if (!servers.isEmpty()) {
            y += 24;
        }
        int srvEnd = Math.min(serverScroll + SERVER_ROWS, servers.size());
        for (int i = serverScroll; i < srvEnd; i++) {
            String s = servers.get(i);
            String label = s.length() > 26 ? s.substring(0, 23) + "..." : s;
            int idx = i;
            addRenderableWidget(
                    Button.builder(Component.literal("Remove: " + label), b -> {
                                applyNameFieldToProfile(mgr, p);
                                p.getServers().remove(idx);
                                mgr.markProfileServersDirty();
                                if (serverScroll >= p.getServers().size() && serverScroll > 0) {
                                    serverScroll = Math.max(0, serverScroll - 1);
                                }
                                init();
                            })
                            .bounds(cx - 100, y, 200, 20)
                            .build());
            y += 22;
        }

        addRenderableWidget(
                Button.builder(Component.literal("Add Current Server"), b -> {
                            applyNameFieldToProfile(mgr, p);
                            String h = ChatUtilitiesManager.currentConnectionHostNormalized();
                            if (h != null && !h.isEmpty() && !p.getServers().contains(h)) {
                                p.getServers().add(h);
                                mgr.markProfileServersDirty();
                            }
                            init();
                        })
                        .bounds(cx - 100, y, 200, 20)
                        .build());

        int footerY = ChatUtilitiesScreenLayout.footerRowY(this);
        int btnW = 98;
        int gap = 6;
        int rowW = btnW * 3 + gap * 2;
        int footLeft = cx - rowW / 2;

        addRenderableWidget(
                Button.builder(Component.literal("Delete Profile"), b -> {
                            Screen above = chatRoot.getParentScreen();
                            Minecraft.getInstance()
                                    .setScreen(
                                            new DeleteProfileConfirmScreen(
                                                    this, new ChatUtilitiesRootScreen(above), profileId));
                        })
                        .bounds(footLeft, footerY, btnW, 20)
                        .build());

        addRenderableWidget(
                Button.builder(Component.literal("Back"), b -> {
                            applyNameFieldToProfile(mgr, p);
                            onClose();
                        })
                        .bounds(footLeft + btnW + gap, footerY, btnW, 20)
                        .build());

        addRenderableWidget(
                Button.builder(Component.literal("Done"), b -> {
                            applyNameFieldToProfile(mgr, p);
                            ChatUtilitiesScreenLayout.closeEntireChatUtilitiesMenu(chatRoot);
                        })
                        .bounds(footLeft + 2 * (btnW + gap), footerY, btnW, 20)
                        .build());
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(chatRoot);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        int margin = 30;
        int wrapW = Math.min(320, this.width - 40);

        graphics.drawCenteredString(
                this.font, this.title, cx, ChatUtilitiesScreenLayout.TITLE_Y, ChatUtilitiesScreenLayout.TEXT_WHITE);

        ChatUtilitiesScreenLayout.drawCenteredWrapped(
                this.font,
                graphics,
                Component.literal(
                        "Set which servers use this profile. The mod compares your connection host to this list; "
                                + "subdomains count as the same main domain (e.g. play.example.net matches example.net)."),
                cx,
                ChatUtilitiesScreenLayout.TITLE_Y + 12,
                wrapW,
                ChatUtilitiesScreenLayout.TEXT_GRAY,
                10);

        graphics.drawString(this.font, "Name", margin, labelNameY, ChatUtilitiesScreenLayout.TEXT_LABEL, false);
        graphics.drawString(
                this.font, "Servers", margin, labelServersY, ChatUtilitiesScreenLayout.TEXT_LABEL, false);
    }
}
