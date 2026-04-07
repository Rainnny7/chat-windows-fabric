package me.braydon.chatutilities.gui;

import com.mojang.blaze3d.platform.InputConstants;

import me.braydon.chatutilities.chat.ChatUtilitiesManager;
import me.braydon.chatutilities.chat.MessageSoundRule;
import me.braydon.chatutilities.chat.ServerProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.PatternSyntaxException;

/** Per-profile rules: play a sound when chat matches a pattern (plain or {@code regex:}). */
public final class ProfileMessageSoundsScreen extends Screen implements ProfileWorkflowScreen {
    private static final int RULE_ROWS = 6;
    private static final int BODY_TOP = 114;
    private static final int SUGGEST_VISIBLE_ROWS = 8;
    private static final int SUGGEST_MAX_MATCHES = 1024;
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
    /** Hit-test / draw region for suggestion popup (updated each render while a sound field is focused). */
    private int sugLeft;
    private int sugTop;
    private int sugWidth;
    private int sugVisibleRows;
    private List<String> sugFiltered = List.of();
    private int sugScroll;
    private String sugLastQuery;

    private record PendingRuleEdit(EditBox patternBox, EditBox soundBox, ServerProfile profile, int listIndex) {}
    private final List<PendingRuleEdit> pendingRuleEdits = new ArrayList<>();

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
        pendingRuleEdits.clear();
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
        patternField.setHint(ChatUtilitiesScreenLayout.PATTERN_INPUT_HINT);
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
        int ruleRowLeft = cx - 100;
        int ruleRowW = 252;
        int ruleXBtnW = 24;
        int ruleGap = 4;
        int ruleInner = ruleRowW - ruleXBtnW - ruleGap;
        int ruleSndW = 100;
        int rulePatW = ruleInner - ruleSndW - ruleGap;
        for (int i = ruleScroll; i < rEnd; i++) {
            MessageSoundRule rule = rules.get(i);
            int idx = i;
            Identifier sid = ChatUtilitiesManager.parseSoundId(rule.getSoundId()).orElse(null);
            String sndDisp = sid != null ? sid.toString() : rule.getSoundId();

            EditBox patEb = new EditBox(this.font, ruleRowLeft, y, rulePatW, 20, Component.literal("rpat" + idx));
            patEb.setMaxLength(2048);
            patEb.setValue(rule.getPatternSource());
            patEb.setHint(ChatUtilitiesScreenLayout.PATTERN_INPUT_HINT);
            addRenderableWidget(patEb);

            EditBox sndEb =
                    new EditBox(
                            this.font, ruleRowLeft + rulePatW + ruleGap, y, ruleSndW, 20, Component.literal("rsnd" + idx));
            sndEb.setMaxLength(256);
            sndEb.setValue(sndDisp);
            sndEb.setHint(Component.literal("Sound id…"));
            addRenderableWidget(sndEb);

            addRenderableWidget(
                    Button.builder(Component.literal("✕"), b -> {
                                mgr.removeMessageSound(p, idx);
                                init();
                            })
                            .bounds(ruleRowLeft + ruleRowW - ruleXBtnW, y, ruleXBtnW, 20)
                            .build());
            pendingRuleEdits.add(new PendingRuleEdit(patEb, sndEb, p, idx));
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
                                ChatUtilitiesScreenLayout.BUTTON_DONE,
                                b -> ChatUtilitiesScreenLayout.closeEntireChatUtilitiesMenu(chatRoot))
                        .bounds(footLeft + btnW + gap, footerY, btnW, 20)
                        .build());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
            ChatUtilitiesManager mgr = ChatUtilitiesManager.get();
            ServerProfile p = mgr.getProfile(profileId);
            if (p != null) {
                for (PendingRuleEdit pr : pendingRuleEdits) {
                    if (pr.patternBox().isFocused() || pr.soundBox().isFocused()) {
                        try {
                            mgr.setMessageSoundAt(
                                    p,
                                    pr.listIndex(),
                                    pr.patternBox().getValue(),
                                    pr.soundBox().getValue());
                            init();
                        } catch (PatternSyntaxException ignored) {
                        } catch (IllegalArgumentException ignored) {
                        }
                        return true;
                    }
                }
                if (patternField != null
                        && soundField != null
                        && (patternField.isFocused() || soundField.isFocused())
                        && !patternField.getValue().strip().isEmpty()
                        && !soundField.getValue().strip().isEmpty()) {
                    try {
                        mgr.addMessageSound(p, patternField.getValue(), soundField.getValue());
                        patternField.setValue("");
                    } catch (PatternSyntaxException ignored) {
                    } catch (IllegalArgumentException ignored) {
                    }
                    init();
                    return true;
                }
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            EditBox anchor = activeSoundSuggestionField();
            if (anchor != null) {
                double mx = event.x();
                double my = event.y();
                boolean inAnchor =
                        mx >= anchor.getX()
                                && mx < anchor.getX() + anchor.getWidth()
                                && my >= anchor.getY()
                                && my < anchor.getY() + anchor.getHeight();
                boolean inSug =
                        !sugFiltered.isEmpty()
                                && mx >= sugLeft
                                && mx < sugLeft + sugWidth
                                && my >= sugTop
                                && my < sugTop + sugVisibleRows * SUGGESTION_ROW_H;
                if (!inAnchor && !inSug) {
                    anchor.setFocused(false);
                    sugFiltered = List.of();
                    sugLastQuery = null;
                } else if (!sugFiltered.isEmpty() && inSug) {
                    int row = (int) ((my - sugTop) / SUGGESTION_ROW_H);
                    int idx = sugScroll + row;
                    if (row >= 0 && row < sugVisibleRows && idx >= 0 && idx < sugFiltered.size()) {
                        anchor.setValue(sugFiltered.get(idx));
                        anchor.moveCursorToEnd(false);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (activeSoundSuggestionField() != null && !sugFiltered.isEmpty()) {
            int maxScroll = Math.max(0, sugFiltered.size() - SUGGEST_VISIBLE_ROWS);
            if (maxScroll > 0) {
                int visH = sugVisibleRows * SUGGESTION_ROW_H;
                if (mouseX >= sugLeft && mouseX < sugLeft + sugWidth
                        && mouseY >= sugTop && mouseY < sugTop + visH) {
                    int step = verticalAmount > 0 ? -1 : 1;
                    sugScroll = Mth.clamp(sugScroll + step, 0, maxScroll);
                    return true;
                }
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(chatRoot);
        }
    }

    private EditBox activeSoundSuggestionField() {
        if (soundField != null && soundField.isFocused()) {
            return soundField;
        }
        for (PendingRuleEdit pr : pendingRuleEdits) {
            if (pr.soundBox().isFocused()) {
                return pr.soundBox();
            }
        }
        return null;
    }

    private void renderSoundSuggestionPopup(GuiGraphics graphics, int mouseX, int mouseY, EditBox anchor) {
        String filter = anchor.getValue();
        if (!Objects.equals(filter, sugLastQuery)) {
            sugLastQuery = filter;
            sugScroll = 0;
        }
        sugFiltered = SoundRegistryList.filterContains(filter, SUGGEST_MAX_MATCHES);
        if (sugFiltered.isEmpty()) {
            return;
        }
        int maxScroll = Math.max(0, sugFiltered.size() - SUGGEST_VISIBLE_ROWS);
        sugScroll = Mth.clamp(sugScroll, 0, maxScroll);
        sugVisibleRows = Math.min(SUGGEST_VISIBLE_ROWS, sugFiltered.size());

        sugLeft = anchor.getX();
        sugTop = anchor.getY() + anchor.getHeight() + 1;
        sugWidth = Math.max(200, anchor.getWidth());
        int sugBottom = sugTop + sugVisibleRows * SUGGESTION_ROW_H;
        graphics.fill(sugLeft - 1, sugTop - 1, sugLeft + sugWidth + 1, sugBottom + 1, 0xFF000000);
        graphics.fill(sugLeft, sugTop, sugLeft + sugWidth, sugBottom, 0xE8101010);
        for (int i = 0; i < sugVisibleRows; i++) {
            String line = sugFiltered.get(sugScroll + i);
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
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
                        "Pair chat patterns with sounds so important messages get your attention even when you are not reading the log."),
                cx,
                ChatUtilitiesScreenLayout.TITLE_Y + 12,
                wrapW,
                ChatUtilitiesScreenLayout.TEXT_GRAY,
                10);

        graphics.drawString(
                this.font, "Match pattern", margin, labelPatternY, ChatUtilitiesScreenLayout.TEXT_LABEL, false);
        graphics.drawString(
                this.font, "Sound", margin, labelSoundY, ChatUtilitiesScreenLayout.TEXT_LABEL, false);

        EditBox soundAnchor = activeSoundSuggestionField();
        if (soundAnchor != null) {
            renderSoundSuggestionPopup(graphics, mouseX, mouseY, soundAnchor);
        }
    }
}
