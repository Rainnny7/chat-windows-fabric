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
import java.util.regex.PatternSyntaxException;

/** Manage chat windows (filters) for one profile. */
public class ProfileWindowsScreen extends Screen implements ProfileWorkflowScreen {
    private static final int WIN_ROWS = 6;
    private static final int BODY_TOP = 114;

    private final String profileId;
    private final ChatUtilitiesRootScreen chatRoot;
    private EditBox newWinIdField;
    private EditBox newWinPatField;
    private int winScroll;
    private int labelWindowsY;

    public ProfileWindowsScreen(String profileId, ChatUtilitiesRootScreen chatRoot) {
        super(Component.literal("Chat Windows"));
        this.profileId = profileId;
        this.chatRoot = chatRoot;
    }

    @Override
    public ChatUtilitiesRootScreen getChatRoot() {
        return chatRoot;
    }

    @Override
    public Screen recreateForProfile() {
        return new ProfileWindowsScreen(profileId, chatRoot);
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

        labelWindowsY = y - 11;
        newWinIdField = new EditBox(this.font, cx - 100, y, 95, 20, Component.literal("wid"));
        newWinIdField.setMaxLength(64);
        newWinIdField.setHint(Component.literal("Window id"));
        addRenderableWidget(newWinIdField);
        newWinPatField = new EditBox(this.font, cx + 3, y, 102, 20, Component.literal("pat"));
        newWinPatField.setMaxLength(2048);
        newWinPatField.setHint(Component.literal("Text or regex:..."));
        addRenderableWidget(newWinPatField);
        addRenderableWidget(
                Button.builder(Component.literal("Add Chat Window"), b -> {
                            String id = newWinIdField.getValue().strip();
                            if (id.isEmpty()) {
                                return;
                            }
                            try {
                                if (mgr.createWindow(p, id, newWinPatField.getValue())) {
                                    newWinIdField.setValue("");
                                    newWinPatField.setValue("");
                                }
                            } catch (PatternSyntaxException ignored) {
                            }
                            init();
                        })
                        .bounds(cx - 100, y + 24, 200, 20)
                        .build());
        y += 52;

        List<String> winIds = p.getWindowIds();
        int wMax = Math.max(0, winIds.size() - WIN_ROWS);
        winScroll = Math.min(winScroll, wMax);
        if (winScroll > 0) {
            addRenderableWidget(
                    Button.builder(Component.literal("Up"), b -> {
                                winScroll = Math.max(0, winScroll - WIN_ROWS);
                                init();
                            })
                            .bounds(cx - 100, y, 95, 20)
                            .build());
        }
        if (winScroll + WIN_ROWS < winIds.size()) {
            addRenderableWidget(
                    Button.builder(Component.literal("Down"), b -> {
                                winScroll = Math.min(wMax, winScroll + WIN_ROWS);
                                init();
                            })
                            .bounds(winScroll > 0 ? cx + 5 : cx - 100, y, 95, 20)
                            .build());
        }
        if (!winIds.isEmpty()) {
            y += 24;
        }
        int wEnd = Math.min(winScroll + WIN_ROWS, winIds.size());
        for (int i = winScroll; i < wEnd; i++) {
            String wid = winIds.get(i);
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("Open: " + wid),
                                    b -> Minecraft.getInstance()
                                            .setScreen(new WindowManageScreen(profileId, wid, this)))
                            .bounds(cx - 100, y, 200, 20)
                            .build());
            y += 22;
        }

        int footerY = ChatUtilitiesScreenLayout.footerRowY(this);
        int btnW = 100;
        int gap = 8;
        int rowW = btnW * 2 + gap;
        int footLeft = cx - rowW / 2;
        addRenderableWidget(
                Button.builder(Component.literal("Back"), b -> onClose())
                        .bounds(footLeft, footerY, btnW, 20)
                        .build());
        addRenderableWidget(
                Button.builder(
                                Component.literal("Done"),
                                b -> ChatUtilitiesScreenLayout.closeEntireChatUtilitiesMenu(chatRoot))
                        .bounds(footLeft + btnW + gap, footerY, btnW, 20)
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
                        "Chat windows are extra panes that show a copy of lines matching your patterns—useful for separating lobby, party, or mod messages. "
                                + "Matching text still appears in the main chat unless you hide it inside that window's settings. "
                                + "Use regex: before a pattern for a regular expression."),
                cx,
                ChatUtilitiesScreenLayout.TITLE_Y + 12,
                wrapW,
                ChatUtilitiesScreenLayout.TEXT_GRAY,
                10);

        graphics.drawString(
                this.font, "Windows", margin, labelWindowsY, ChatUtilitiesScreenLayout.TEXT_LABEL, false);
    }
}
