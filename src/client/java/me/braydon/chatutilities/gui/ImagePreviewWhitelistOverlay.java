package me.braydon.chatutilities.gui;

import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/** Modal domain whitelist editor (opened from Settings). */
public final class ImagePreviewWhitelistOverlay {

    private static final int PANEL_W = 300;
    private static final int PANEL_H = 220;

    private final List<String> draft = new ArrayList<>();
    private final Runnable onClose;
    private int selectedIndex = -1;
    private int panelX;
    private int panelY;
    private int listTop;
    private final int rowH = 12;
    private int scrollRows;
    private int addFieldY;
    private int btnRowY;
    private int doneLeft;
    private int cancelLeft;
    private int removeLeft;
    private final int btnW = 72;
    private final int btnH = 18;
    private EditBox addField;

    public ImagePreviewWhitelistOverlay(Runnable onClose) {
        this.onClose = onClose;
        draft.addAll(ChatUtilitiesClientOptions.getImagePreviewWhitelistHosts());
    }

    /** Recreate the add field after {@link net.minecraft.client.gui.screens.Screen#clearWidgets()}. */
    public void rebuildAddField(Font font) {
        addField =
                new EditBox(
                        font,
                        0,
                        0,
                        160,
                        16,
                        Component.literal("imgwhitelist"));
        addField.setMaxLength(128);
        addField.setHint(Component.translatable("chat-utilities.image_preview.whitelist.add_hint"));
    }

    public EditBox getAddField() {
        return addField;
    }

    public void layout(int screenW, int screenH) {
        panelX = (screenW - PANEL_W) / 2;
        panelY = (screenH - PANEL_H) / 2;
        listTop = panelY + 28;
        addFieldY = panelY + PANEL_H - 72;
        btnRowY = panelY + PANEL_H - 36;
        if (addField != null) {
            addField.setX(panelX + 10);
            addField.setY(addFieldY);
            addField.setWidth(PANEL_W - 100);
        }
        removeLeft = panelX + 10;
        doneLeft = panelX + PANEL_W - 10 - btnW * 2 - 8;
        cancelLeft = doneLeft + btnW + 8;
        clampScrollAndSelection();
    }

    public boolean contains(int mx, int my) {
        return mx >= panelX && mx < panelX + PANEL_W && my >= panelY && my < panelY + PANEL_H;
    }

    public int panelLeft() {
        return panelX;
    }

    public int panelTop() {
        return panelY;
    }

    public int panelRight() {
        return panelX + PANEL_W;
    }

    public int panelBottom() {
        return panelY + PANEL_H;
    }

    public boolean isDoneButton(double mx, double my) {
        return mx >= doneLeft && mx < doneLeft + btnW && my >= btnRowY && my < btnRowY + btnH;
    }

    public boolean isCancelButton(double mx, double my) {
        return mx >= cancelLeft && mx < cancelLeft + btnW && my >= btnRowY && my < btnRowY + btnH;
    }

    public boolean isRemoveButton(double mx, double my) {
        return mx >= removeLeft && mx < removeLeft + btnW && my >= btnRowY && my < btnRowY + btnH;
    }

    public void render(GuiGraphicsExtractor g, Font font, int mouseX, int mouseY, float partialTick) {
        g.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, 0xF0101018);
        g.outline(panelX, panelY, PANEL_W, PANEL_H, 0xFF6A6A80);
        g.text(
                font,
                Component.translatable("chat-utilities.image_preview.whitelist.title"),
                panelX + 10,
                panelY + 8,
                ChatUtilitiesScreenLayout.TEXT_WHITE,
                false);
        int y = listTop;
        int maxRows = Math.max(1, (addFieldY - listTop - 4) / rowH);
        int first = Math.max(0, Math.min(scrollRows, Math.max(0, draft.size() - maxRows)));
        for (int ri = 0; ri < maxRows; ri++) {
            int i = first + ri;
            if (i >= draft.size()) {
                break;
            }
            boolean sel = i == selectedIndex;
            if (sel) {
                g.fill(panelX + 6, y - 1, panelX + PANEL_W - 6, y + rowH - 1, 0x336688FF);
            }
            String line = draft.get(i);
            line = truncate(font, line, PANEL_W - 24);
            g.text(font, line, panelX + 10, y, sel ? 0xFFFFFFFF : ChatUtilitiesScreenLayout.TEXT_GRAY, false);
            y += rowH;
        }
        g.text(
                font,
                Component.translatable("chat-utilities.image_preview.whitelist.add_label"),
                panelX + 10,
                addFieldY - 12,
                ChatUtilitiesScreenLayout.TEXT_LABEL,
                false);
        if (addField != null) {
            addField.extractRenderState(g, mouseX, mouseY, partialTick);
        }
        drawBtn(g, font, removeLeft, btnRowY, "chat-utilities.image_preview.whitelist.remove", mouseX, mouseY);
        drawBtn(g, font, doneLeft, btnRowY, "chat-utilities.image_preview.whitelist.done", mouseX, mouseY);
        drawBtn(g, font, cancelLeft, btnRowY, "chat-utilities.image_preview.whitelist.cancel", mouseX, mouseY);
    }

    private static String truncate(Font font, String line, int maxW) {
        if (font.width(line) <= maxW) {
            return line;
        }
        String ell = "…";
        String s = line;
        while (s.length() > 1 && font.width(s + ell) > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s + ell;
    }

    private void drawBtn(GuiGraphicsExtractor g, Font font, int x, int y, String key, int mx, int my) {
        boolean h = mx >= x && mx < x + btnW && my >= y && my < y + btnH;
        g.fill(x, y, x + btnW, y + btnH, h ? 0x55FFFFFF : 0x35000000);
        g.outline(x, y, btnW, btnH, 0x55FFFFFF);
        Component msg = Component.translatable(key);
        g.text(
                font,
                msg,
                x + (btnW - font.width(msg)) / 2,
                y + (btnH - 8) / 2,
                0xFFFFFFFF,
                false);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        int button = event.button();
        if (button != 0) {
            return false;
        }
        if (addField != null && addField.mouseClicked(event, doubleClick)) {
            return true;
        }
        int listBottom = addFieldY - 4;
        if (mx >= panelX + 6 && mx < panelX + PANEL_W - 6 && my >= listTop && my < listBottom) {
            int maxRows = Math.max(1, (addFieldY - listTop - 4) / rowH);
            int first = Math.max(0, Math.min(scrollRows, Math.max(0, draft.size() - maxRows)));
            int rel = (int) ((my - listTop) / rowH);
            int idx = first + rel;
            if (idx >= 0 && idx < draft.size()) {
                selectedIndex = idx;
            }
            return true;
        }
        if (isRemoveButton(mx, my) && selectedIndex >= 0 && selectedIndex < draft.size()) {
            draft.remove(selectedIndex);
            selectedIndex = Math.min(selectedIndex, draft.size() - 1);
            clampScrollAndSelection();
            return true;
        }
        if (isDoneButton(mx, my)) {
            ChatUtilitiesClientOptions.setImagePreviewWhitelistHosts(draft);
            onClose.run();
            return true;
        }
        if (isCancelButton(mx, my)) {
            onClose.run();
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (addField != null && addField.isFocused()) {
                String raw = addField.getValue();
                String host = normalizeHostOnly(raw);
                if (host != null && !host.isEmpty() && !containsIgnoreCase(draft, host)) {
                    draft.add(host);
                    addField.setValue("");
                    clampScrollAndSelection();
                }
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int listBottom = addFieldY - 4;
        if (!(mouseX >= panelX + 6 && mouseX < panelX + PANEL_W - 6 && mouseY >= listTop && mouseY < listBottom)) {
            return false;
        }
        int maxRows = Math.max(1, (addFieldY - listTop - 4) / rowH);
        int maxScroll = Math.max(0, draft.size() - maxRows);
        int step = verticalAmount > 0 ? -1 : 1;
        scrollRows = Math.max(0, Math.min(maxScroll, scrollRows + step));
        return true;
    }

    private void clampScrollAndSelection() {
        int maxRows = Math.max(1, (addFieldY - listTop - 4) / rowH);
        int maxScroll = Math.max(0, draft.size() - maxRows);
        scrollRows = Math.max(0, Math.min(maxScroll, scrollRows));
        if (selectedIndex >= draft.size()) {
            selectedIndex = draft.isEmpty() ? -1 : (draft.size() - 1);
        }
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        if (value == null) {
            return false;
        }
        for (String s : list) {
            if (s != null && s.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Accepts pasted URLs or hostnames and returns a normalized host only (lowercase, no port, no path).
     * Returns {@code null} if it can't extract a host.
     */
    private static String normalizeHostOnly(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.strip();
        if (s.isEmpty()) {
            return null;
        }
        // If user pasted a full URL, parse it. If they pasted a bare hostname, prepend a scheme.
        String toParse = s.contains("://") ? s : "https://" + s;
        try {
            URI uri = new URI(toParse);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return null;
            }
            String h = host.strip().toLowerCase();
            while (h.endsWith(".")) {
                h = h.substring(0, h.length() - 1);
            }
            if (h.isEmpty()) {
                return null;
            }
            return h;
        } catch (URISyntaxException ignored) {
            return null;
        }
    }
}
