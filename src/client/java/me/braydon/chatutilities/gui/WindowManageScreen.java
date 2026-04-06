package me.braydon.chatutilities.gui;

import me.braydon.chatutilities.chat.ChatUtilitiesManager;
import me.braydon.chatutilities.chat.ChatWindow;
import me.braydon.chatutilities.chat.ServerProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.regex.PatternSyntaxException;

public class WindowManageScreen extends Screen {
    private static final int C_DANGER_TEXT = 0xFFE07878;
    private static final int C_DANGER_TEXT_H = 0xFFFFA0A0;

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

    private static final long DESTRUCTIVE_CONFIRM_MS = 3000L;
    private long removeWindowConfirmDeadlineMs;

    public WindowManageScreen(String profileId, String windowId, Screen parent) {
        super(Component.literal("Window Patterns"));
        this.profileId = profileId;
        this.windowId = windowId;
        this.parent = parent;
    }

    private AbstractWidget destructiveFlatButton(Component label, Runnable press, int x, int y, int w, int h) {
        return new AbstractWidget(x, y, w, h, label) {
            @Override
            public void onClick(MouseButtonEvent event, boolean doubleClick) {
                press.run();
            }

            @Override
            protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                boolean hov = isHovered();
                boolean act = active;
                int bg = !act ? 0x35402020 : hov ? 0x55903030 : 0x45302828;
                int outline = !act ? 0x45C06060 : hov ? 0x85FF9090 : 0x65D07070;
                int tc = !act ? C_DANGER_TEXT : hov ? C_DANGER_TEXT_H : 0xFFF0A0A0;
                g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
                g.renderOutline(getX(), getY(), getWidth(), getHeight(), outline);
                g.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                        getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, tc);
            }

            @Override
            public void updateWidgetNarration(NarrationElementOutput n) {
                defaultButtonNarrationText(n);
            }
        };
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
                        .bounds(cx - 100, y, 200, 20)
                        .build());
        y += 26;

        newPatternField = new EditBox(this.font, cx - 100, y, 130, 20, Component.literal("pat"));
        newPatternField.setMaxLength(2048);
        newPatternField.setHint(ChatUtilitiesScreenLayout.PATTERN_INPUT_HINT);
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
            AbstractWidget removePat = destructiveFlatButton(Component.literal("Remove Pattern: " + patLabel), () -> {
                mgr.removePattern(p, windowId, pos);
                init();
            }, cx - 100, y, 200, 20);
            removePat.setTooltip(Tooltip.create(Component.literal(tip)));
            addRenderableWidget(removePat);
            y += 22;
        }

        int footerY = ChatUtilitiesScreenLayout.footerRowY(this);
        int btnW = 88;
        int gap = 6;
        int rowW = btnW * 3 + gap * 2;
        int footLeft = cx - rowW / 2;

        long nowRm = System.currentTimeMillis();
        boolean removeArmed = removeWindowConfirmDeadlineMs > nowRm;
        addRenderableWidget(
                destructiveFlatButton(
                        Component.literal(removeArmed ? "✕ Confirm Removal" : "✕ Remove Window"),
                        () -> {
                            long t = System.currentTimeMillis();
                            if (removeWindowConfirmDeadlineMs > t) {
                                removeWindowConfirmDeadlineMs = 0;
                                mgr.removeWindow(p, windowId);
                                if (parent instanceof ProfileWorkflowScreen wf) {
                                    Minecraft.getInstance().setScreen(wf.recreateForProfile());
                                } else {
                                    onClose();
                                }
                            } else {
                                removeWindowConfirmDeadlineMs = t + DESTRUCTIVE_CONFIRM_MS;
                                init();
                            }
                        },
                        footLeft,
                        footerY,
                        btnW,
                        20));

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
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        if (removeWindowConfirmDeadlineMs != 0 && now >= removeWindowConfirmDeadlineMs) {
            removeWindowConfirmDeadlineMs = 0;
            init();
        }
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
                        "Decide which messages belong in this window and how it behaves on your HUD."),
                cx,
                ChatUtilitiesScreenLayout.TITLE_Y + 24,
                wrapW,
                ChatUtilitiesScreenLayout.TEXT_GRAY,
                10);
    }
}
