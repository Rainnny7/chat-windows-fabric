package me.braydon.chatutilities.gui;

import me.braydon.chatutilities.chat.ChatUtilitiesManager;
import me.braydon.chatutilities.chat.MessageSoundRule;
import me.braydon.chatutilities.chat.ServerProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.regex.PatternSyntaxException;

/** Per-profile rules: play a sound when chat matches a pattern (plain or {@code regex:}). */
public final class ProfileMessageSoundsScreen extends Screen implements ProfileWorkflowScreen {
    private static final int RULE_ROWS = 6;
    private static final int BODY_TOP = 114;
    private static final int SUGGESTION_ROWS = 8;
    private static final int SUGGESTION_ROW_H = 12;

    private final String profileId;
    private final ChatUtilitiesRootScreen chatRoot;
    private EditBox patternField;
    private EditBox soundField;
    private int ruleScroll;
    private int labelPatternY;
    private int labelSoundY;
    /** Preserved when opening {@link SoundPickerScreen} so {@link #init()} can restore fields. */
    private String stashPattern;
    private String stashSound;
    /** Hit-test for suggestion popup (updated each render while sound field focused). */
    private int sugLeft;
    private int sugTop;
    private int sugWidth;
    private List<String> sugLines = List.of();

    public ProfileMessageSoundsScreen(String profileId, ChatUtilitiesRootScreen chatRoot) {
        super(Component.literal("Chat Sounds"));
        this.profileId = profileId;
        this.chatRoot = chatRoot;
    }

    @Override
    public ChatUtilitiesRootScreen getChatRoot() {
        return chatRoot;
    }

    @Override
    public Screen recreateForProfile() {
        return new ProfileMessageSoundsScreen(profileId, chatRoot);
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

        String restorePattern = stashPattern;
        String restoreSound = stashSound;
        stashPattern = null;
        stashSound = null;

        labelPatternY = y - 11;
        patternField = new EditBox(this.font, cx - 100, y, 200, 20, Component.literal("pat"));
        patternField.setMaxLength(2048);
        patternField.setHint(Component.literal("Match chat (or regex:…)"));
        if (restorePattern != null) {
            patternField.setValue(restorePattern);
        }
        addRenderableWidget(patternField);
        y += 24;

        labelSoundY = y - 11;
        soundField = new EditBox(this.font, cx - 100, y, 152, 20, Component.literal("snd"));
        soundField.setMaxLength(256);
        soundField.setHint(Component.literal("Sound id…"));
        if (restoreSound != null) {
            soundField.setValue(restoreSound);
        }
        addRenderableWidget(soundField);

        Minecraft mc = Minecraft.getInstance();
        addRenderableWidget(
                Button.builder(
                                Component.literal("Pick"),
                                b -> {
                                    stashPattern = patternField.getValue();
                                    stashSound = soundField.getValue();
                                    mc.setScreen(
                                            new SoundPickerScreen(
                                                    this, id -> stashSound = id));
                                })
                        .bounds(cx + 56, y, 44, 20)
                        .build());
        y += 24;

        addRenderableWidget(
                Button.builder(Component.literal("Test"), b -> {
                            ChatUtilitiesManager.parseSoundId(soundField.getValue())
                                    .filter(ChatUtilitiesManager::isRegisteredSound)
                                    .ifPresent(id -> ChatUtilitiesManager.playSoundPreview(id));
                        })
                        .bounds(cx - 100, y, 62, 20)
                        .build());
        addRenderableWidget(
                Button.builder(Component.literal("Add Rule"), b -> {
                            try {
                                mgr.addMessageSound(p, patternField.getValue(), soundField.getValue());
                                patternField.setValue("");
                            } catch (PatternSyntaxException ignored) {
                            } catch (IllegalArgumentException ignored) {
                            }
                            init();
                        })
                        .bounds(cx + 38, y, 62, 20)
                        .build());
        y += 26;

        List<MessageSoundRule> rules = p.getMessageSounds();
        int rMax = Math.max(0, rules.size() - RULE_ROWS);
        ruleScroll = Math.min(ruleScroll, rMax);
        if (ruleScroll > 0) {
            addRenderableWidget(
                    Button.builder(Component.literal("Up"), b -> {
                                ruleScroll = Math.max(0, ruleScroll - RULE_ROWS);
                                init();
                            })
                            .bounds(cx - 100, y, 95, 20)
                            .build());
        }
        if (ruleScroll + RULE_ROWS < rules.size()) {
            addRenderableWidget(
                    Button.builder(Component.literal("Down"), b -> {
                                ruleScroll = Math.min(rMax, ruleScroll + RULE_ROWS);
                                init();
                            })
                            .bounds(ruleScroll > 0 ? cx + 5 : cx - 100, y, 95, 20)
                            .build());
        }
        if (!rules.isEmpty()) {
            y += 24;
        }
        int rEnd = Math.min(ruleScroll + RULE_ROWS, rules.size());
        for (int i = ruleScroll; i < rEnd; i++) {
            MessageSoundRule rule = rules.get(i);
            String pat = rule.getPatternSource();
            if (pat.length() > 18) {
                pat = pat.substring(0, 15) + "...";
            }
            Identifier sid = ChatUtilitiesManager.parseSoundId(rule.getSoundId()).orElse(null);
            String snd = sid != null ? sid.toString() : rule.getSoundId();
            if (snd.length() > 22) {
                snd = snd.substring(0, 19) + "...";
            }
            String label = pat + " → " + snd;
            int idx = i;
            addRenderableWidget(
                    Button.builder(Component.literal("Remove: " + label), b -> {
                                mgr.removeMessageSound(p, idx);
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && soundField != null && soundField.isFocused() && !sugLines.isEmpty()) {
            double mx = event.x();
            double my = event.y();
            if (mx >= sugLeft
                    && mx < sugLeft + sugWidth
                    && my >= sugTop
                    && my < sugTop + sugLines.size() * SUGGESTION_ROW_H) {
                int row = (int) ((my - sugTop) / SUGGESTION_ROW_H);
                if (row >= 0 && row < sugLines.size()) {
                    soundField.setValue(sugLines.get(row));
                    soundField.moveCursorToEnd(false);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(chatRoot);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        sugLines = List.of();
        super.render(graphics, mouseX, mouseY, partialTick);
        int cx = this.width / 2;
        int margin = 30;
        int wrapW = Math.min(340, this.width - 40);

        graphics.drawCenteredString(
                this.font, this.title, cx, ChatUtilitiesScreenLayout.TITLE_Y, ChatUtilitiesScreenLayout.TEXT_WHITE);

        ChatUtilitiesScreenLayout.drawCenteredWrapped(
                this.font,
                graphics,
                Component.literal(
                        "When chat matches a rule, the sound plays in the UI. Ignored messages never trigger sounds. "
                                + "Use the same pattern rules as chat windows: plain text, or prefix with regex: for a regex."),
                cx,
                ChatUtilitiesScreenLayout.TITLE_Y + 12,
                wrapW,
                ChatUtilitiesScreenLayout.TEXT_GRAY,
                10);

        graphics.drawString(
                this.font, "Match pattern", margin, labelPatternY, ChatUtilitiesScreenLayout.TEXT_LABEL, false);
        graphics.drawString(
                this.font, "Sound", margin, labelSoundY, ChatUtilitiesScreenLayout.TEXT_LABEL, false);

        if (soundField != null && soundField.isFocused()) {
            sugLines = SoundRegistryList.filterContains(soundField.getValue(), SUGGESTION_ROWS);
            if (!sugLines.isEmpty()) {
                sugLeft = soundField.getX();
                sugTop = soundField.getY() + soundField.getHeight() + 1;
                sugWidth = Math.max(200, soundField.getWidth() + 48);
                int sugBottom = sugTop + sugLines.size() * SUGGESTION_ROW_H;
                graphics.fill(sugLeft - 1, sugTop - 1, sugLeft + sugWidth + 1, sugBottom + 1, 0xFF000000);
                graphics.fill(sugLeft, sugTop, sugLeft + sugWidth, sugBottom, 0xE8101010);
                for (int i = 0; i < sugLines.size(); i++) {
                    String line = sugLines.get(i);
                    if (line.length() > 48) {
                        line = line.substring(0, 45) + "...";
                    }
                    int ry = sugTop + i * SUGGESTION_ROW_H;
                    boolean hovered =
                            mouseX >= sugLeft
                                    && mouseX < sugLeft + sugWidth
                                    && mouseY >= ry
                                    && mouseY < ry + SUGGESTION_ROW_H;
                    if (hovered) {
                        graphics.fill(sugLeft, ry, sugLeft + sugWidth, ry + SUGGESTION_ROW_H, 0x336666FF);
                    }
                    graphics.drawString(this.font, line, sugLeft + 3, ry + 2, ChatUtilitiesScreenLayout.TEXT_WHITE, false);
                }
            }
        }
    }
}
