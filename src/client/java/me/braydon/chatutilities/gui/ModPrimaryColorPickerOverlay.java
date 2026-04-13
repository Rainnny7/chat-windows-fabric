package me.braydon.chatutilities.gui;

import com.mojang.blaze3d.platform.Window;
import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import me.braydon.chatutilities.client.ModChromaClock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.function.Consumer;

/**
 * Compact in-menu color editor (HSV + alpha, chroma, recent colors). Rendered on top of
 * {@link ChatUtilitiesRootScreen} without replacing it.
 */
public final class ModPrimaryColorPickerOverlay {

    public record ChatHighlightPick(
            int rgb,
            boolean bold,
            boolean italic,
            boolean underlined,
            boolean strikethrough,
            boolean obfuscated) {}

    public enum OverlayMode {
        ACCENT,
        TIMESTAMP_RGB,
        STACKED_MESSAGE_RGB,
        UNREAD_BADGE_RGB,
        /** Same HSV UI as timestamp; {@link #applyAndPersist} invokes highlight consumer with RGB + formats. */
        CHAT_HIGHLIGHT_RGB
    }

    private static final int PANEL_W = 292;
    private static final int PANEL_H = 218;
    private static final int PANEL_H_HIGHLIGHT = 266;
    private static final int SB_SIZE = 64;
    private static final int STRIP_W = 8;
    private static final int STRIP_H = SB_SIZE;

    private final OverlayMode mode;
    /** Non-null only for {@link OverlayMode#CHAT_HIGHLIGHT_RGB}. */
    private final Consumer<ChatHighlightPick> chatHighlightConsumer;

    private float hue;
    private float sat;
    private float val;
    private int alpha;
    private boolean chroma;
    private float chromaSpeed;

    private int panelX;
    private int panelY;

    private int sbLeft;
    private int sbTop;
    private int hueLeft;
    private int hueTop;
    private int alphaLeft;
    private int alphaTop;
    private int previewLeft;
    private int previewTop;
    private int recentLeft;
    private int recentTop;
    private int chromaCbLeft;
    private int chromaCbTop;
    private int speedLeft;
    private int speedTop;
    private int speedW;
    private int speedH;
    private int doneLeft;
    private int doneTop;
    private int cancelLeft;
    private int btnW;
    private int btnH;

    private boolean hlBold;
    private boolean hlItalic;
    private boolean hlUnderlined;
    private boolean hlStrikethrough;
    private boolean hlObfuscated;
    private int hlFmtTop;

    private enum Drag {
        NONE,
        SB,
        HUE,
        ALPHA,
        SPEED
    }

    private Drag drag = Drag.NONE;

    private EditBox hexField;
    private boolean hexResponderSuppress;

    private ModPrimaryColorPickerOverlay(OverlayMode mode, Consumer<ChatHighlightPick> chatHighlightConsumer) {
        this.mode = mode;
        this.chatHighlightConsumer = chatHighlightConsumer;
    }

    public static ModPrimaryColorPickerOverlay create() {
        ModPrimaryColorPickerOverlay o = new ModPrimaryColorPickerOverlay(OverlayMode.ACCENT, null);
        o.loadFromOptions();
        return o;
    }

    public static ModPrimaryColorPickerOverlay createTimestampRgb() {
        ModPrimaryColorPickerOverlay o = new ModPrimaryColorPickerOverlay(OverlayMode.TIMESTAMP_RGB, null);
        o.loadTimestampRgbFromOptions();
        return o;
    }

    public static ModPrimaryColorPickerOverlay createStackedMessageRgb() {
        ModPrimaryColorPickerOverlay o = new ModPrimaryColorPickerOverlay(OverlayMode.STACKED_MESSAGE_RGB, null);
        o.loadStackedMessageRgbFromOptions();
        return o;
    }

    public static ModPrimaryColorPickerOverlay createUnreadBadgeRgb() {
        ModPrimaryColorPickerOverlay o = new ModPrimaryColorPickerOverlay(OverlayMode.UNREAD_BADGE_RGB, null);
        o.loadUnreadBadgeRgbFromOptions();
        return o;
    }

    /**
     * HSV editor for chat highlight; {@link #applyAndPersist} calls {@code onChosen} with RGB and format toggles.
     */
    public static ModPrimaryColorPickerOverlay createChatHighlightRgb(
            int initialRgb,
            boolean bold,
            boolean italic,
            boolean underlined,
            boolean strikethrough,
            boolean obfuscated,
            Consumer<ChatHighlightPick> onChosen) {
        ModPrimaryColorPickerOverlay o = new ModPrimaryColorPickerOverlay(OverlayMode.CHAT_HIGHLIGHT_RGB, onChosen);
        o.loadRgbState(initialRgb & 0xFFFFFF);
        o.hlBold = bold;
        o.hlItalic = italic;
        o.hlUnderlined = underlined;
        o.hlStrikethrough = strikethrough;
        o.hlObfuscated = obfuscated;
        return o;
    }

    private void loadRgbState(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        float[] hsv = ModPrimaryColorUtils.rgbToHsv(r, g, b);
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = hsv[2];
        this.alpha = 255;
        this.chroma = false;
        this.chromaSpeed = ChatUtilitiesClientOptions.getModPrimaryChromaSpeed();
        refreshHexFieldText();
    }

    private void loadTimestampRgbFromOptions() {
        int rgb = ChatUtilitiesClientOptions.getChatTimestampColorRgb() & 0xFFFFFF;
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        float[] hsv = ModPrimaryColorUtils.rgbToHsv(r, g, b);
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = hsv[2];
        this.alpha = 255;
        this.chroma = false;
        this.chromaSpeed = ChatUtilitiesClientOptions.getModPrimaryChromaSpeed();
        refreshHexFieldText();
    }

    private void loadStackedMessageRgbFromOptions() {
        int rgb = ChatUtilitiesClientOptions.getStackedMessageColorRgb() & 0xFFFFFF;
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        float[] hsv = ModPrimaryColorUtils.rgbToHsv(r, g, b);
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = hsv[2];
        this.alpha = 255;
        this.chroma = false;
        this.chromaSpeed = ChatUtilitiesClientOptions.getModPrimaryChromaSpeed();
        refreshHexFieldText();
    }

    private void loadUnreadBadgeRgbFromOptions() {
        int rgb = ChatUtilitiesClientOptions.getTabUnreadBadgeColorRgb() & 0xFFFFFF;
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        float[] hsv = ModPrimaryColorUtils.rgbToHsv(r, g, b);
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = hsv[2];
        this.alpha = 255;
        this.chroma = false;
        this.chromaSpeed = ChatUtilitiesClientOptions.getModPrimaryChromaSpeed();
        refreshHexFieldText();
    }

    private void loadFromOptions() {
        int base = ChatUtilitiesClientOptions.getModPrimaryArgb();
        int r = (base >> 16) & 0xFF;
        int g = (base >> 8) & 0xFF;
        int b = base & 0xFF;
        float[] hsv = ModPrimaryColorUtils.rgbToHsv(r, g, b);
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = hsv[2];
        this.alpha = (base >>> 24) & 0xFF;
        this.chroma = ChatUtilitiesClientOptions.isModPrimaryChroma();
        this.chromaSpeed = ChatUtilitiesClientOptions.getModPrimaryChromaSpeed();
        refreshHexFieldText();
    }

    private void refreshHexFieldText() {
        if (hexField == null) {
            return;
        }
        int rgbOnly = ModPrimaryColorUtils.hsvToArgb(hue, sat, val, 255) & 0xFFFFFF;
        hexResponderSuppress = true;
        try {
            hexField.setValue(String.format("#%06X", rgbOnly));
        } finally {
            hexResponderSuppress = false;
        }
    }

    private void onHexFieldChanged(String raw) {
        if (hexResponderSuppress) {
            return;
        }
        String s = raw == null ? "" : raw.strip();
        if (s.startsWith("#")) {
            s = s.substring(1);
        }
        if (s.length() != 6) {
            return;
        }
        int rgb;
        try {
            rgb = Integer.parseUnsignedInt(s, 16);
        } catch (NumberFormatException e) {
            return;
        }
        rgb &= 0xFFFFFF;
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        float[] hsv = ModPrimaryColorUtils.rgbToHsv(r, g, b);
        this.hue = hsv[0];
        this.sat = hsv[1];
        this.val = hsv[2];
    }

    public void layout(int screenW, int screenH) {
        int pw = PANEL_W;
        int ph = panelH();
        panelX = (screenW - pw) / 2;
        panelY = (screenH - ph) / 2;

        int innerX = panelX + 8;
        int innerY = panelY + 26;

        sbLeft = innerX;
        sbTop = innerY;
        hueLeft = sbLeft + SB_SIZE + 6;
        hueTop = sbTop;
        alphaLeft = hueLeft + STRIP_W + 6;
        alphaTop = sbTop;

        int rightCol =
                mode == OverlayMode.TIMESTAMP_RGB
                                || mode == OverlayMode.CHAT_HIGHLIGHT_RGB
                                || mode == OverlayMode.UNREAD_BADGE_RGB
                        ? hueLeft + STRIP_W + 10
                        : alphaLeft + STRIP_W + 10;
        previewLeft = rightCol;
        previewTop = innerY;
        chromaCbLeft = rightCol;
        chromaCbTop = mode == OverlayMode.ACCENT ? previewTop + 34 : innerY + 18;

        speedLeft = rightCol;
        speedTop = mode == OverlayMode.ACCENT ? chromaCbTop + 38 : innerY + 56;
        speedW = pw - (rightCol - panelX) - 10;
        speedH = 12;

        hlFmtTop = mode == OverlayMode.CHAT_HIGHLIGHT_RGB ? previewTop + 34 : innerY + 34;

        recentLeft = innerX;
        recentTop = panelY + ph - 44;

        int hexW = 96;
        int hexX = previewLeft;
        int hexY = previewTop + 12;
        if (hexField == null) {
            hexField =
                    new EditBox(
                            Minecraft.getInstance().font,
                            hexX,
                            hexY,
                            hexW,
                            18,
                            Component.literal("hex"));
            hexField.setMaxLength(9);
            hexField.setHint(Component.literal("#RRGGBB"));
            hexField.setResponder(this::onHexFieldChanged);
            refreshHexFieldText();
        } else {
            hexField.setX(hexX);
            hexField.setY(hexY);
            hexField.setWidth(hexW);
            hexField.setHeight(18);
            // Do not overwrite user input every frame from {@link ChatUtilitiesRootScreen#renderModPrimaryColorPickerOnTop}.
            if (!hexField.isFocused()) {
                refreshHexFieldText();
            }
        }

        int doneY = panelY + ph - 26;
        btnW = 72;
        btnH = 20;
        doneLeft = panelX + pw - 10 - 2 * btnW - 8;
        doneTop = doneY;
        cancelLeft = panelX + pw - 10 - btnW;
    }

    private int panelW() {
        return PANEL_W;
    }

    private int panelH() {
        return mode == OverlayMode.CHAT_HIGHLIGHT_RGB ? PANEL_H_HIGHLIGHT : PANEL_H;
    }

    public int panelLeft() {
        return panelX;
    }

    public int panelTop() {
        return panelY;
    }

    public int panelRight() {
        return panelX + panelW();
    }

    public int panelBottom() {
        return panelY + panelH();
    }

    private int previewArgbUi() {
        if (mode == OverlayMode.TIMESTAMP_RGB
                || mode == OverlayMode.CHAT_HIGHLIGHT_RGB
                || mode == OverlayMode.UNREAD_BADGE_RGB
                || !chroma) {
            return ModPrimaryColorUtils.hsvToArgb(hue, sat, val, alpha);
        }
        float t = ModChromaClock.phaseSeconds();
        float h = chromaSmoothHueUi(t, chromaSpeed, hue);
        return ModPrimaryColorUtils.hsvToArgb(h, 1f, 1f, alpha);
    }

    private static float chromaSmoothHueUi(float timeSec, float speed, float baseHue) {
        float w = Mth.sin(timeSec * speed * 0.12f) * 0.5f + 0.5f;
        return (baseHue + w * 0.35f) % 1f;
    }

    public void render(GuiGraphics g, Font font, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Window win = mc.getWindow();
        boolean leftDown = GLFW.glfwGetMouseButton(win.handle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (drag != Drag.NONE) {
            if (!leftDown) {
                drag = Drag.NONE;
            } else {
                int mx = (int) mc.mouseHandler.getScaledXPos(win);
                int my = (int) mc.mouseHandler.getScaledYPos(win);
                applyDrag(mx, my);
            }
        }

        int pw = panelW();
        int ph = panelH();
        g.fill(panelX, panelY, panelX + pw, panelY + ph, 0xF0101014);
        g.renderOutline(panelX, panelY, pw, ph, 0xFF505060);

        Component title =
                mode == OverlayMode.TIMESTAMP_RGB
                        ? Component.translatable("chat-utilities.settings.chat_timestamp.color_picker_title")
                        : mode == OverlayMode.CHAT_HIGHLIGHT_RGB
                                ? Component.translatable("chat-utilities.chat_actions.color_highlight.picker_title")
                                : mode == OverlayMode.UNREAD_BADGE_RGB
                                        ? Component.translatable(
                                                "chat-utilities.settings.unread_badge.color_picker_title")
                                        : Component.translatable("chat-utilities.settings.mod_primary_color.picker.title");
        g.drawString(font, title, panelX + 8, panelY + 8, ChatUtilitiesScreenLayout.TEXT_WHITE, false);

        g.drawString(
                font,
                Component.translatable("chat-utilities.settings.mod_primary_color.picker.section_color"),
                panelX + 8,
                panelY + 18,
                ChatUtilitiesScreenLayout.TEXT_LABEL,
                false);

        drawSbField(g);
        drawHueStrip(g);
        if (mode == OverlayMode.ACCENT) {
            drawAlphaStrip(g);
        }

        int pv = previewArgbUi();
        int pr = 14;
        g.fill(previewLeft, previewTop, previewLeft + pr, previewTop + pr, pv);
        g.renderOutline(previewLeft, previewTop, pr, pr, 0xFFAAAAAA);
        if (hexField != null) {
            hexField.render(g, mouseX, mouseY, partialTick);
        }

        if (mode == OverlayMode.CHAT_HIGHLIGHT_RGB) {
            drawHighlightFormatToggles(g, font);
        }

        if (mode == OverlayMode.ACCENT) {
            g.drawString(
                    font,
                    Component.translatable("chat-utilities.settings.mod_primary_color.picker.chroma"),
                    chromaCbLeft + 12,
                    chromaCbTop + 2,
                    ChatUtilitiesScreenLayout.TEXT_LABEL,
                    false);
            int cb = chroma ? 0xFF4488FF : 0xFF404048;
            g.fill(chromaCbLeft, chromaCbTop, chromaCbLeft + 10, chromaCbTop + 10, cb);
            g.renderOutline(chromaCbLeft, chromaCbTop, 10, 10, 0xFF888888);
            if (chroma) {
                drawChromaCheckmark(g, chromaCbLeft, chromaCbTop);
            }

            g.drawString(
                    font,
                    Component.translatable(
                            "chat-utilities.settings.mod_primary_color.picker.speed",
                            String.format("%.1f", chromaSpeed)),
                    speedLeft,
                    speedTop - 12,
                    ChatUtilitiesScreenLayout.TEXT_LABEL,
                    false);
            float smin = ChatUtilitiesClientOptions.MOD_PRIMARY_CHROMA_SPEED_MIN;
            float smax = ChatUtilitiesClientOptions.MOD_PRIMARY_CHROMA_SPEED_MAX;
            double speedNorm = (chromaSpeed - smin) / (double) (smax - smin);
            int knobX = speedLeft + (int) (speedNorm * (speedW - 6)) + 3;
            knobX = Mth.clamp(knobX, speedLeft + 2, speedLeft + speedW - 3);
            int syBar = speedTop + speedH / 2;
            g.fill(speedLeft, syBar - 1, speedLeft + speedW, syBar + 1, chroma ? 0xFF606068 : 0xFF404048);
            g.fill(knobX - 2, speedTop, knobX + 3, speedTop + speedH, chroma ? 0xFFE0E0F0 : 0xFF707078);

            drawRecent(g, font);
        }

        if (mode == OverlayMode.TIMESTAMP_RGB || mode == OverlayMode.CHAT_HIGHLIGHT_RGB) {
            drawRecent(g, font);
        }

        drawFlatButton(
                g,
                font,
                doneLeft,
                doneTop,
                btnW,
                btnH,
                Component.translatable("chat-utilities.settings.mod_primary_color.picker.done"),
                mouseX,
                mouseY);
        drawFlatButton(
                g,
                font,
                cancelLeft,
                doneTop,
                btnW,
                btnH,
                Component.translatable("chat-utilities.settings.mod_primary_color.picker.cancel"),
                mouseX,
                mouseY);
    }

    private void drawHighlightFormatToggles(GuiGraphics g, Font font) {
        g.drawString(
                font,
                Component.translatable("chat-utilities.chat_actions.color_highlight.format"),
                previewLeft,
                hlFmtTop - 11,
                ChatUtilitiesScreenLayout.TEXT_LABEL,
                false);
        int y = hlFmtTop;
        int row = 14;
        drawFmtToggleRow(
                g, font, previewLeft, y, Component.translatable("chat-utilities.chat_actions.color_highlight.fmt_bold"), hlBold);
        y += row;
        drawFmtToggleRow(
                g, font, previewLeft, y, Component.translatable("chat-utilities.chat_actions.color_highlight.fmt_italic"), hlItalic);
        y += row;
        drawFmtToggleRow(
                g,
                font,
                previewLeft,
                y,
                Component.translatable("chat-utilities.chat_actions.color_highlight.fmt_underlined"),
                hlUnderlined);
        y += row;
        drawFmtToggleRow(
                g,
                font,
                previewLeft,
                y,
                Component.translatable("chat-utilities.chat_actions.color_highlight.fmt_strikethrough"),
                hlStrikethrough);
        y += row;
        drawFmtToggleRow(
                g,
                font,
                previewLeft,
                y,
                Component.translatable("chat-utilities.chat_actions.color_highlight.fmt_obfuscated"),
                hlObfuscated);
    }

    private static void drawFmtToggleRow(GuiGraphics g, Font font, int left, int top, Component label, boolean on) {
        int cb = on ? 0xFF4488FF : 0xFF404048;
        g.fill(left, top, left + 10, top + 10, cb);
        g.renderOutline(left, top, 10, 10, 0xFF888888);
        if (on) {
            drawToggleCheckmark(g, left, top);
        }
        g.drawString(font, label, left + 14, top + 1, 0xFFCCCCDD, false);
    }

    /** Checkmark drawn in a 10×10 checkbox (two-stroke “✓” readable at 1× GUI scale). */
    private static void drawToggleCheckmark(GuiGraphics g, int left, int top) {
        int c = 0xFFE8E8F0;
        g.fill(left + 2, top + 5, left + 3, top + 8, c);
        g.fill(left + 3, top + 6, left + 4, top + 8, c);
        g.fill(left + 4, top + 7, left + 5, top + 8, c);
        g.fill(left + 4, top + 6, left + 8, top + 7, c);
        g.fill(left + 5, top + 5, left + 8, top + 6, c);
        g.fill(left + 6, top + 4, left + 8, top + 5, c);
        g.fill(left + 7, top + 3, left + 8, top + 4, c);
    }

    private static void drawChromaCheckmark(GuiGraphics g, int left, int top) {
        drawToggleCheckmark(g, left, top);
    }

    private static void drawFlatButton(
            GuiGraphics g, Font font, int x, int y, int btnW, int btnH, Component label, int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX < x + btnW && mouseY >= y && mouseY < y + btnH;
        int bg = hov ? 0x55FFFFFF : 0x35000000;
        int outline = hov ? 0x70FFFFFF : 0x40FFFFFF;
        int tc = hov ? 0xFFFFFFFF : 0xFFDDDDEE;
        g.fill(x, y, x + btnW, y + btnH, bg);
        g.renderOutline(x, y, btnW, btnH, outline);
        g.drawCenteredString(font, label, x + btnW / 2, y + (btnH - 8) / 2, tc);
    }

    public EditBox getHexField() {
        return hexField;
    }

    /** Hit test for the hex {@link EditBox} after {@link #layout}. */
    public boolean isHexFieldMouseOver(double mouseX, double mouseY) {
        if (hexField == null) {
            return false;
        }
        return hexField.isMouseOver(mouseX, mouseY);
    }

    public boolean keyPressed(KeyEvent event) {
        return hexField != null && hexField.isFocused() && hexField.keyPressed(event);
    }

    public boolean charTyped(CharacterEvent event) {
        return hexField != null && hexField.isFocused() && hexField.charTyped(event);
    }

    public boolean contains(int x, int y) {
        return x >= panelX && x < panelX + panelW() && y >= panelY && y < panelY + panelH();
    }

    private void drawSbField(GuiGraphics g) {
        int step = 3;
        for (int py = 0; py < SB_SIZE; py += step) {
            for (int px = 0; px < SB_SIZE; px += step) {
                float s = (px + step * 0.5f) / SB_SIZE;
                float v = 1f - (py + step * 0.5f) / SB_SIZE;
                int c = ModPrimaryColorUtils.hsvToArgb(hue, s, v, 255);
                int x1 = sbLeft + px;
                int y1 = sbTop + py;
                g.fill(x1, y1, Math.min(sbLeft + SB_SIZE, x1 + step), Math.min(sbTop + SB_SIZE, y1 + step), c);
            }
        }
        g.renderOutline(sbLeft, sbTop, SB_SIZE, SB_SIZE, 0xFF666666);
        int cx = sbLeft + (int) (sat * SB_SIZE);
        int cy = sbTop + (int) ((1f - val) * SB_SIZE);
        cx = Mth.clamp(cx, sbLeft + 2, sbLeft + SB_SIZE - 3);
        cy = Mth.clamp(cy, sbTop + 2, sbTop + SB_SIZE - 3);
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFFFFF);
        g.renderOutline(cx - 2, cy - 2, 5, 5, 0xFF222222);
    }

    private void drawHueStrip(GuiGraphics g) {
        int step = 2;
        for (int i = 0; i < STRIP_H; i += step) {
            float h = i / (float) STRIP_H;
            int c = ModPrimaryColorUtils.hsvToArgb(h, 1f, 1f, 255);
            g.fill(hueLeft, hueTop + i, hueLeft + STRIP_W, hueTop + i + step, c);
        }
        g.renderOutline(hueLeft, hueTop, STRIP_W, STRIP_H, 0xFF666666);
        int hy = hueTop + (int) (hue * STRIP_H);
        hy = Mth.clamp(hy, hueTop, hueTop + STRIP_H - 3);
        g.fill(hueLeft - 1, hy - 1, hueLeft + STRIP_W + 1, hy + 3, 0xFFFFFFFF);
    }

    private void drawAlphaStrip(GuiGraphics g) {
        int cs = 4;
        for (int py = 0; py < STRIP_H; py += cs) {
            for (int px = 0; px < STRIP_W; px += cs) {
                boolean d = ((px / cs + py / cs) & 1) == 0;
                int b = d ? 0xFF888888 : 0xFF444444;
                g.fill(alphaLeft + px, alphaTop + py, alphaLeft + px + cs, alphaTop + py + cs, b);
            }
        }
        int step = 2;
        for (int i = 0; i < STRIP_H; i += step) {
            float t = i / (float) STRIP_H;
            int a = (int) (t * 255);
            int rgb = ModPrimaryColorUtils.hsvToArgb(hue, sat, val, 255) & 0xFFFFFF;
            int c = (a << 24) | rgb;
            g.fill(alphaLeft, alphaTop + i, alphaLeft + STRIP_W, alphaTop + i + step, c);
        }
        g.renderOutline(alphaLeft, alphaTop, STRIP_W, STRIP_H, 0xFF666666);
        int ay = alphaTop + STRIP_H - 1 - (int) (alpha / 255f * (STRIP_H - 4));
        ay = Mth.clamp(ay, alphaTop, alphaTop + STRIP_H - 4);
        g.fill(alphaLeft - 1, ay - 1, alphaLeft + STRIP_W + 1, ay + 3, 0xFFFFFFFF);
    }

    private void drawRecent(GuiGraphics g, Font font) {
        g.drawString(
                font,
                Component.translatable("chat-utilities.settings.mod_primary_color.picker.recent"),
                recentLeft,
                recentTop - 10,
                ChatUtilitiesScreenLayout.TEXT_LABEL,
                false);
        int cell = 16;
        int gap = 3;
        int col = 4;
        int i = 0;
        for (int c : recentListForMode()) {
            int cx = recentLeft + (i % col) * (cell + gap);
            int cy = recentTop + (i / col) * (cell + gap);
            g.fill(cx, cy, cx + cell, cy + cell, c);
            g.renderOutline(cx, cy, cell, cell, 0xFF666666);
            i++;
            if (i >= 8) {
                break;
            }
        }
    }

    private boolean tryToggleHighlightFmt(int ix, int iy) {
        int left = previewLeft;
        int y = hlFmtTop;
        int rowH = 14;
        int rowW = panelW() - (left - panelX) - 10;
        rowW = Math.max(72, rowW);
        for (int s = 0; s < 5; s++) {
            if (iy >= y && iy < y + rowH && ix >= left && ix < left + rowW) {
                switch (s) {
                    case 0 -> hlBold = !hlBold;
                    case 1 -> hlItalic = !hlItalic;
                    case 2 -> hlUnderlined = !hlUnderlined;
                    case 3 -> hlStrikethrough = !hlStrikethrough;
                    case 4 -> hlObfuscated = !hlObfuscated;
                }
                return true;
            }
            y += rowH;
        }
        return false;
    }

    /** @return true if the event was used by the overlay */
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        int ix = (int) mx;
        int iy = (int) my;

        if (mode == OverlayMode.CHAT_HIGHLIGHT_RGB) {
            if (tryToggleHighlightFmt(ix, iy)) {
                return true;
            }
        }

        if (mode == OverlayMode.ACCENT) {
            if (ix >= chromaCbLeft && ix < chromaCbLeft + 10 && iy >= chromaCbTop && iy < chromaCbTop + 10) {
                chroma = !chroma;
                return true;
            }

            if (ix >= speedLeft && ix < speedLeft + speedW && iy >= speedTop && iy < speedTop + speedH && chroma) {
                drag = Drag.SPEED;
                applySpeed(ix);
                return true;
            }

        }

        if (hitRecent(ix, iy)) {
            return true;
        }

        if (hitSb(ix, iy)) {
            drag = Drag.SB;
            applySb(ix, iy);
            return true;
        }
        if (hitHue(ix, iy)) {
            drag = Drag.HUE;
            applyHue(iy);
            return true;
        }
        if (mode == OverlayMode.ACCENT && hitAlpha(ix, iy)) {
            drag = Drag.ALPHA;
            applyAlpha(iy);
            return true;
        }

        if (ix >= doneLeft && ix < doneLeft + btnW && iy >= doneTop && iy < doneTop + btnH) {
            return true;
        }
        if (ix >= cancelLeft && ix < cancelLeft + btnW && iy >= doneTop && iy < doneTop + btnH) {
            return true;
        }

        return ix >= panelX && ix < panelX + panelW() && iy >= panelY && iy < panelY + panelH();
    }

    public boolean isDoneButton(double mx, double my) {
        int ix = (int) mx;
        int iy = (int) my;
        return ix >= doneLeft && ix < doneLeft + btnW && iy >= doneTop && iy < doneTop + btnH;
    }

    public boolean isCancelButton(double mx, double my) {
        int ix = (int) mx;
        int iy = (int) my;
        return ix >= cancelLeft && ix < cancelLeft + btnW && iy >= doneTop && iy < doneTop + btnH;
    }

    private boolean hitSb(int x, int y) {
        return x >= sbLeft && x < sbLeft + SB_SIZE && y >= sbTop && y < sbTop + SB_SIZE;
    }

    private boolean hitHue(int x, int y) {
        return x >= hueLeft && x < hueLeft + STRIP_W && y >= hueTop && y < hueTop + STRIP_H;
    }

    private boolean hitAlpha(int x, int y) {
        return x >= alphaLeft && x < alphaLeft + STRIP_W && y >= alphaTop && y < alphaTop + STRIP_H;
    }

    private boolean hitRecent(int x, int y) {
        int cell = 16;
        int gap = 3;
        int col = 4;
        List<Integer> rec = recentListForMode();
        for (int idx = 0; idx < rec.size() && idx < 8; idx++) {
            int cx = recentLeft + (idx % col) * (cell + gap);
            int cy = recentTop + (idx / col) * (cell + gap);
            if (x >= cx && x < cx + cell && y >= cy && y < cy + cell) {
                applyRecentArgbToState(rec.get(idx));
                return true;
            }
        }
        return false;
    }

    private List<Integer> recentListForMode() {
        if (mode == OverlayMode.TIMESTAMP_RGB) {
            return ChatUtilitiesClientOptions.getChatTimestampRecent();
        }
        return ChatUtilitiesClientOptions.getModPrimaryRecent();
    }

    private void applyRecentArgbToState(int c) {
        int r = (c >> 16) & 0xFF;
        int g = (c >> 8) & 0xFF;
        int b = c & 0xFF;
        float[] hsv = ModPrimaryColorUtils.rgbToHsv(r, g, b);
        hue = hsv[0];
        sat = hsv[1];
        val = hsv[2];
        if (mode == OverlayMode.ACCENT) {
            alpha = (c >>> 24) & 0xFF;
        } else {
            alpha = 255;
        }
        refreshHexFieldText();
    }

    private void applyDrag(int mx, int my) {
        switch (drag) {
            case SB -> applySb(mx, my);
            case HUE -> applyHue(my);
            case ALPHA -> applyAlpha(my);
            case SPEED -> applySpeed(mx);
            default -> {}
        }
    }

    private void applySb(int x, int y) {
        float s = (x - sbLeft) / (float) SB_SIZE;
        float v = 1f - (y - sbTop) / (float) SB_SIZE;
        sat = Mth.clamp(s, 0f, 1f);
        val = Mth.clamp(v, 0f, 1f);
        refreshHexFieldText();
    }

    private void applyHue(int y) {
        float h = (y - hueTop) / (float) STRIP_H;
        hue = Mth.clamp(h, 0f, 0.9999f);
        refreshHexFieldText();
    }

    private void applyAlpha(int y) {
        float t = 1f - (y - alphaTop) / (float) STRIP_H;
        alpha = Mth.clamp(Math.round(t * 255f), 0, 255);
        refreshHexFieldText();
    }

    private void applySpeed(int x) {
        float smin = ChatUtilitiesClientOptions.MOD_PRIMARY_CHROMA_SPEED_MIN;
        float smax = ChatUtilitiesClientOptions.MOD_PRIMARY_CHROMA_SPEED_MAX;
        float t = (x - speedLeft) / (float) speedW;
        chromaSpeed = Mth.lerp(Mth.clamp(t, 0f, 1f), smin, smax);
    }

    public void applyAndPersist(Runnable onApplied) {
        if (mode == OverlayMode.TIMESTAMP_RGB) {
            int rgb = ModPrimaryColorUtils.hsvToArgb(hue, sat, val, 255) & 0xFFFFFF;
            ChatUtilitiesClientOptions.setChatTimestampColorRgb(rgb);
            ChatUtilitiesClientOptions.pushChatTimestampRecent(rgb);
        } else if (mode == OverlayMode.STACKED_MESSAGE_RGB) {
            int rgb = ModPrimaryColorUtils.hsvToArgb(hue, sat, val, 255) & 0xFFFFFF;
            ChatUtilitiesClientOptions.setStackedMessageColorRgb(rgb);
        } else if (mode == OverlayMode.UNREAD_BADGE_RGB) {
            int rgb = ModPrimaryColorUtils.hsvToArgb(hue, sat, val, 255) & 0xFFFFFF;
            ChatUtilitiesClientOptions.setTabUnreadBadgeColorRgb(rgb);
        } else if (mode == OverlayMode.CHAT_HIGHLIGHT_RGB) {
            int rgb = ModPrimaryColorUtils.hsvToArgb(hue, sat, val, 255) & 0xFFFFFF;
            ChatUtilitiesClientOptions.pushModPrimaryRecent(0xFF000000 | rgb);
            if (chatHighlightConsumer != null) {
                chatHighlightConsumer.accept(
                        new ChatHighlightPick(
                                rgb, hlBold, hlItalic, hlUnderlined, hlStrikethrough, hlObfuscated));
            }
        } else {
            int argb = ModPrimaryColorUtils.hsvToArgb(hue, sat, val, alpha);
            ChatUtilitiesClientOptions.setModPrimaryArgb(argb);
            ChatUtilitiesClientOptions.setModPrimaryChroma(chroma);
            ChatUtilitiesClientOptions.setModPrimaryChromaSpeed(chromaSpeed);
            ChatUtilitiesClientOptions.pushModPrimaryRecent(argb);
        }
        onApplied.run();
    }

    public boolean isChatHighlightMode() {
        return mode == OverlayMode.CHAT_HIGHLIGHT_RGB;
    }
}
