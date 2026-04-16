package me.braydon.chatutilities.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Searchable list of all registered {@link net.minecraft.sounds.SoundEvent} ids. */
public final class SoundPickerScreen extends Screen {
    private static final int VISIBLE_ROWS = 14;
    private static final int ROW_H = 12;

    private final Screen returnTo;
    private final Consumer<String> onPick;
    private EditBox search;
    private List<String> matches = new ArrayList<>();
    private int scrollRow;
    private String lastSearchQuery = "\u0000";
    private int listLeft;
    private int listTop;
    private int listWidth;

    public SoundPickerScreen(Screen returnTo, Consumer<String> onPick) {
        super(Component.literal("Select Sound"));
        this.returnTo = returnTo;
        this.onPick = onPick;
    }

    @Override
    protected void init() {
        clearWidgets();
        int cx = this.width / 2;
        listLeft = cx - 160;
        listTop = 96;
        listWidth = 320;
        search = new EditBox(this.font, listLeft, 66, listWidth, 20, Component.literal("search"));
        search.setMaxLength(256);
        search.setHint(Component.literal("Filter..."));
        search.setResponder(q -> scrollRow = 0);
        addRenderableWidget(search);
        setInitialFocus(search);
        addRenderableWidget(
                Button.builder(Component.literal("Cancel"), b -> onClose())
                        .bounds(cx - 100, this.height - 28, 200, 20)
                        .build());
        lastSearchQuery = "\u0000";
        refreshMatches();
    }

    private void refreshMatches() {
        String q = search != null ? search.getValue() : "";
        if (!q.equals(lastSearchQuery)) {
            lastSearchQuery = q;
            matches = SoundRegistryList.filterContains(q, 800);
        }
        int maxScroll = Math.max(0, matches.size() - VISIBLE_ROWS);
        scrollRow = Math.min(scrollRow, maxScroll);
    }

    private void pickAt(int index) {
        if (index < 0 || index >= matches.size()) {
            return;
        }
        onPick.accept(matches.get(index));
        onClose();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        if (event.button() == 0
                && mx >= listLeft
                && mx < listLeft + listWidth
                && my >= listTop
                && my < listTop + VISIBLE_ROWS * ROW_H) {
            int row = (int) ((my - listTop) / ROW_H) + scrollRow;
            pickAt(row);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        refreshMatches();
        if (mouseX >= listLeft
                && mouseX < listLeft + listWidth
                && mouseY >= listTop
                && mouseY < listTop + VISIBLE_ROWS * ROW_H) {
            int maxScroll = Math.max(0, matches.size() - VISIBLE_ROWS);
            int delta = verticalAmount > 0 ? -1 : verticalAmount < 0 ? 1 : 0;
            scrollRow = Math.max(0, Math.min(maxScroll, scrollRow + delta));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        refreshMatches();
        graphics.centeredText(this.font, this.title, this.width / 2, 44, ChatUtilitiesScreenLayout.TEXT_WHITE);
        ChatUtilitiesScreenLayout.drawCenteredWrapped(
                this.font,
                graphics,
                Component.literal(
                        "Browse game sounds and pick one to use in your message rules."),
                this.width / 2,
                56,
                listWidth,
                ChatUtilitiesScreenLayout.TEXT_GRAY,
                10);
        int listBottom = listTop + VISIBLE_ROWS * ROW_H;
        graphics.fill(listLeft - 2, listTop - 2, listLeft + listWidth + 2, listBottom + 2, 0xFF000000);
        for (int i = 0; i < VISIBLE_ROWS; i++) {
            int idx = scrollRow + i;
            if (idx >= matches.size()) {
                break;
            }
            String line = matches.get(idx);
            if (line.length() > 52) {
                line = line.substring(0, 49) + "...";
            }
            int ry = listTop + i * ROW_H;
            boolean hovered =
                    mouseX >= listLeft
                            && mouseX < listLeft + listWidth
                            && mouseY >= ry
                            && mouseY < ry + ROW_H;
            if (hovered) {
                graphics.fill(listLeft, ry, listLeft + listWidth, ry + ROW_H, 0x336666FF);
            }
            graphics.text(this.font, line, listLeft + 2, ry + 2, ChatUtilitiesScreenLayout.TEXT_WHITE, false);
        }
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(returnTo);
        }
    }
}
