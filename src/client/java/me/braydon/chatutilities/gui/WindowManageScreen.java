package me.braydon.chatutilities.gui;

import me.braydon.chatutilities.chat.ChatUtilitiesManager;
import me.braydon.chatutilities.chat.ChatWindow;
import me.braydon.chatutilities.chat.ServerProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.regex.PatternSyntaxException;

public class WindowManageScreen extends Screen {
    private static final int PAT_ROWS = 6;
    /** Max visible characters of the pattern on the remove button after the "Remove Pattern: " prefix. */
    private static final int REMOVE_PATTERN_LABEL_MAX = 22;
    /** First row of controls; leave room for title, window id, and wrapped description. */
    private static final int BODY_TOP = 118;

    private final String profileId;
    private final String windowId;
    private final Screen parent;
    private EditBox newPatternField;
    private int patternScroll;

    public WindowManageScreen(String profileId, String windowId, Screen parent) {
        super(Component.literal("Window Patterns"));
        this.profileId = profileId;
        this.windowId = windowId;
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        ChatUtilitiesManager mgr = ChatUtilitiesManager.get();
        ServerProfile p = mgr.getProfile(profileId);
        ChatWindow w = p != null ? p.getWindow(windowId) : null;
        if (w == null) {
            onClose();
            return;
        }

        int cx = this.width / 2;
        int y = BODY_TOP;

        String vis = w.isVisible() ? "Hide" : "Show";
        addRenderableWidget(
                Button.builder(Component.literal(vis), b -> {
                            mgr.toggleVisibility(p, windowId);
                            init();
                        })
                        .bounds(cx - 100, y, 95, 20)
                        .build());
        addRenderableWidget(
                Button.builder(Component.literal("Position"), b -> {
                            if (!(parent instanceof ProfileWorkflowScreen wf)) {
                                return;
                            }
                            mgr.setRestoreScreenAfterPosition(
                                    () ->
                                            new WindowManageScreen(
                                                    profileId,
                                                    windowId,
                                                    wf.recreateForProfile()));
                            mgr.togglePosition(profileId, windowId);
                            Minecraft.getInstance().setScreen(null);
                        })
                        .bounds(cx + 5, y, 95, 20)
                        .build());
        y += 26;

        newPatternField = new EditBox(this.font, cx - 100, y, 130, 20, Component.literal("pat"));
        newPatternField.setMaxLength(2048);
        newPatternField.setHint(Component.literal("Plain text or regex:..."));
        addRenderableWidget(newPatternField);
        addRenderableWidget(
                Button.builder(Component.literal("Add Pattern"), b -> {
                            try {
                                mgr.addPattern(p, windowId, newPatternField.getValue());
                                newPatternField.setValue("");
                            } catch (PatternSyntaxException ignored) {
                            }
                            init();
                        })
                        .bounds(cx + 38, y, 62, 20)
                        .build());
        y += 26;

        List<String> sources = w.getPatternSources();
        int maxScroll = Math.max(0, sources.size() - PAT_ROWS);
        patternScroll = Math.min(patternScroll, maxScroll);

        if (patternScroll > 0) {
            addRenderableWidget(
                    Button.builder(Component.literal("Up"), b -> {
                                patternScroll = Math.max(0, patternScroll - PAT_ROWS);
                                init();
                            })
                            .bounds(cx - 100, y, 95, 20)
                            .build());
        }
        if (patternScroll + PAT_ROWS < sources.size()) {
            addRenderableWidget(
                    Button.builder(Component.literal("Down"), b -> {
                                patternScroll = Math.min(maxScroll, patternScroll + PAT_ROWS);
                                init();
                            })
                            .bounds(patternScroll > 0 ? cx + 5 : cx - 100, y, 95, 20)
                            .build());
        }
        if (!sources.isEmpty()) {
            y += 24;
        }

        int end = Math.min(patternScroll + PAT_ROWS, sources.size());
        for (int i = patternScroll; i < end; i++) {
            String src = sources.get(i);
            int pos = i + 1;
            String tip = src.length() > 240 ? src.substring(0, 237) + "..." : src;
            String patLabel =
                    src.length() > REMOVE_PATTERN_LABEL_MAX
                            ? src.substring(0, REMOVE_PATTERN_LABEL_MAX - 3) + "..."
                            : src;
            addRenderableWidget(
                    Button.builder(Component.literal("Remove Pattern: " + patLabel), b -> {
                                mgr.removePattern(p, windowId, pos);
                                init();
                            })
                            .bounds(cx - 100, y, 200, 20)
                            .tooltip(Tooltip.create(Component.literal(tip)))
                            .build());
            y += 22;
        }

        int footerY = ChatUtilitiesScreenLayout.footerRowY(this);
        int btnW = 88;
        int gap = 6;
        int rowW = btnW * 3 + gap * 2;
        int footLeft = cx - rowW / 2;

        addRenderableWidget(
                Button.builder(Component.literal("Remove Window"), b -> {
                            mgr.removeWindow(p, windowId);
                            if (parent instanceof ProfileWorkflowScreen wf) {
                                Minecraft.getInstance().setScreen(wf.recreateForProfile());
                            } else {
                                onClose();
                            }
                        })
                        .bounds(footLeft, footerY, btnW, 20)
                        .build());

        addRenderableWidget(
                Button.builder(Component.literal("Back"), b -> onClose())
                        .bounds(footLeft + btnW + gap, footerY, btnW, 20)
                        .build());

        addRenderableWidget(
                Button.builder(
                                Component.literal("Done"),
                                b -> {
                                    if (parent instanceof ProfileWorkflowScreen wf) {
                                        ChatUtilitiesScreenLayout.closeEntireChatUtilitiesMenu(
                                                wf.getChatRoot());
                                    } else {
                                        onClose();
                                    }
                                })
                        .bounds(footLeft + 2 * (btnW + gap), footerY, btnW, 20)
                        .build());
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
        int cx = this.width / 2;
        int wrapW = Math.min(320, this.width - 40);
        graphics.drawCenteredString(
                this.font, this.title, cx, ChatUtilitiesScreenLayout.TITLE_Y, ChatUtilitiesScreenLayout.TEXT_WHITE);
        graphics.drawCenteredString(
                this.font,
                Component.literal(windowId),
                cx,
                ChatUtilitiesScreenLayout.TITLE_Y + 12,
                ChatUtilitiesScreenLayout.TEXT_GRAY);
        ChatUtilitiesScreenLayout.drawCenteredWrapped(
                this.font,
                graphics,
                Component.literal(
                        "Only chat lines that match at least one pattern below are copied into this window. "
                                + "Plain text matches as a substring; use regex: for a full regular expression. "
                                + "Hide or Show controls whether the pane is drawn; Position lets you place it on screen while playing."),
                cx,
                ChatUtilitiesScreenLayout.TITLE_Y + 24,
                wrapW,
                ChatUtilitiesScreenLayout.TEXT_GRAY,
                10);
    }
}
