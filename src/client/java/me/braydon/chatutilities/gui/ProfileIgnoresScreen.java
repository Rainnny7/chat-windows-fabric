package me.braydon.chatutilities.gui;

import me.braydon.chatutilities.chat.ChatUtilitiesManager;
import me.braydon.chatutilities.chat.ServerProfile;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.regex.PatternSyntaxException;

/** Manage ignored chat patterns for one profile. */
public class ProfileIgnoresScreen extends Screen implements ProfileWorkflowScreen {
    private static final int IGN_ROWS = 8;
    private static final int BODY_TOP = 114;

    private final String profileId;
    private final ChatUtilitiesRootScreen chatRoot;
    private EditBox newIgnoreField;
    private int ignScroll;
    private int labelIgnoresY;

    public ProfileIgnoresScreen(String profileId, ChatUtilitiesRootScreen chatRoot) {
        super(Component.literal("Ignored Chat"));
        this.profileId = profileId;
        this.chatRoot = chatRoot;
    }

    @Override
    public ChatUtilitiesRootScreen getChatRoot() {
        return chatRoot;
    }

    @Override
    public Screen recreateForProfile() {
        return new ProfileIgnoresScreen(profileId, chatRoot);
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

        labelIgnoresY = y - 11;
        newIgnoreField = new EditBox(this.font, cx - 100, y, 130, 20, Component.literal("ign"));
        newIgnoreField.setMaxLength(2048);
        newIgnoreField.setHint(Component.literal("Hide matching chat..."));
        addRenderableWidget(newIgnoreField);
        addRenderableWidget(
                Button.builder(Component.literal("Add Ignore"), b -> {
                            try {
                                mgr.addIgnorePattern(p, newIgnoreField.getValue());
                                newIgnoreField.setValue("");
                            } catch (PatternSyntaxException ignored) {
                            }
                            init();
                        })
                        .bounds(cx + 38, y, 62, 20)
                        .build());
        y += 24;

        List<String> ignores = p.getIgnorePatternSources();
        int igMax = Math.max(0, ignores.size() - IGN_ROWS);
        ignScroll = Math.min(ignScroll, igMax);
        if (ignScroll > 0) {
            addRenderableWidget(
                    Button.builder(Component.literal("Up"), b -> {
                                ignScroll = Math.max(0, ignScroll - IGN_ROWS);
                                init();
                            })
                            .bounds(cx - 100, y, 95, 20)
                            .build());
        }
        if (ignScroll + IGN_ROWS < ignores.size()) {
            addRenderableWidget(
                    Button.builder(Component.literal("Down"), b -> {
                                ignScroll = Math.min(igMax, ignScroll + IGN_ROWS);
                                init();
                            })
                            .bounds(ignScroll > 0 ? cx + 5 : cx - 100, y, 95, 20)
                            .build());
        }
        if (!ignores.isEmpty()) {
            y += 24;
        }
        int igEnd = Math.min(ignScroll + IGN_ROWS, ignores.size());
        for (int i = ignScroll; i < igEnd; i++) {
            String pat = ignores.get(i);
            String label = pat.length() > 24 ? pat.substring(0, 21) + "..." : pat;
            int idx = i;
            addRenderableWidget(
                    Button.builder(Component.literal("Remove: " + label), b -> {
                                mgr.removeIgnorePattern(p, idx);
                                init();
                            })
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
                        "Ignored patterns remove matching chat lines completely—they never appear in-game. "
                                + "If you still want to read those lines in a separate pane, use Chat Windows instead. "
                                + "Prefix a pattern with regex: for a regular expression."),
                cx,
                ChatUtilitiesScreenLayout.TITLE_Y + 12,
                wrapW,
                ChatUtilitiesScreenLayout.TEXT_GRAY,
                10);

        graphics.drawString(
                this.font, "Patterns", margin, labelIgnoresY, ChatUtilitiesScreenLayout.TEXT_LABEL, false);
    }
}
