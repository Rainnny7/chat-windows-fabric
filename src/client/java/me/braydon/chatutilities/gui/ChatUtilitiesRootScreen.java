package me.braydon.chatutilities.gui;

import com.mojang.blaze3d.platform.InputConstants;

import me.braydon.chatutilities.ChatUtilitiesModClient;
import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import me.braydon.chatutilities.chat.ChatUtilitiesManager;
import me.braydon.chatutilities.chat.ChatWindow;
import me.braydon.chatutilities.chat.MessageSoundRule;
import me.braydon.chatutilities.chat.ProfileFaviconCache;
import me.braydon.chatutilities.chat.ServerProfile;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.google.gson.JsonParseException;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.FileDialog;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.PatternSyntaxException;

import org.lwjgl.glfw.GLFW;

/**
 * Main UI screen. Sodium-style: a centered floating panel with a left sidebar (profiles + sections)
 * and an inline content area on the right. All panels are shown without navigating away.
 */
public class ChatUtilitiesRootScreen extends Screen implements ProfileWorkflowScreen {

    // ── Panel geometry ─────────────────────────────────────────────────────────
    /** How far the panel is inset from each screen edge. */
    private static final int MARGIN_X = 42;
    private static final int MARGIN_Y = 28;
    /** Width of the left sidebar within the panel. */
    private static final int SIDEBAR_W = 185;
    /** Height of the top “Chat Utilities” strip in the sidebar. */
    private static final int SIDEBAR_TITLE_H = 30;
    private static final String SIDEBAR_AUTHOR_LINK = "https://github.com/Rainnny7";
    /** Footer rows: New Profile, Settings. */
    private static final int SIDEBAR_FOOTER_ROW_H = 26;

    // ── Sidebar row heights ────────────────────────────────────────────────────
    private static final int PROFILE_ROW_H = 28;
    private static final int SUB_ROW_H     = 18;
    /** Left indent for sub-item text. */
    private static final int SUB_INDENT    = 28;

    // ── Content area insets ────────────────────────────────────────────────────
    private static final int CONTENT_PAD_X      = 18;
    /** Title baseline within the content area (relative to panelTop). */
    private static final int TITLE_Y_OFF       = 10;
    /** Pixels below title baseline before wrapped description begins. */
    private static final int TITLE_TO_DESC_GAP = 13;
    /** Pixels below last description line before the header separator rule. */
    private static final int DESC_TO_SEP_GAP   = 8;
    /** Line spacing for wrapped header description. */
    private static final int HEADER_DESC_LINE_SPACING = 10;
    /** Pixels below the separator before form body widgets. */
    private static final int HEADER_BODY_GAP   = 10;
    /** Vertical gap between Settings subsections (Controls / Settings / Profiles). */
    private static final int SETTINGS_SECTION_GAP = 20;
    /** Cap for Edit Profile + Chat Sounds: every input/button in those panels shares this width. */
    private static final int PROFILE_SOUNDS_FORM_MAX_W = 280;

    /** Background / outline for expanded chat window group (header + patterns). */
    private static final int C_WIN_GROUP_BG   = 0x480C1018;
    private static final int C_WIN_GROUP_EDGE = 0xFF2C2C3A;

    /** Eye alone (U+1F441 only — no VS16) to avoid stray combining marks in some fonts. */
    private static final String CHAT_WIN_EMOJI_VISIBLE = "\uD83D\uDC41";
    /** See-no-evil when the window is hidden. */
    private static final String CHAT_WIN_EMOJI_HIDDEN = "\uD83D\uDE48";
    /** Pencil (U+270F), left of “Rename” like ✕ on Remove and the eye on Hide/Show. */
    private static final String CHAT_WIN_SYMBOL_RENAME = "\u270F";
    private static final int FOOTER_INSET = 26; // from panelBottom

    // ── Sidebar colors ─────────────────────────────────────────────────────────
    private static final int C_PANEL_BG       = 0xF0101012;
    private static final int C_SIDEBAR_BG     = 0xFF080810;
    private static final int C_SIDEBAR_SEP    = 0xFF1E1E28;
    private static final int C_PROFILE_SEL    = 0xFF12203A;
    private static final int C_ACTIVE_BG      = 0xFF0E1C36;
    private static final int C_ACCENT         = 0xFF3A9FE0;
    private static final int C_HOVER          = 0x18FFFFFF;
    private static final int C_PROFILE_NAME   = 0xFFEEEEEE;
    private static final int C_PROFILE_DETAIL = 0xFF888898;
    private static final int C_NEW_PROFILE    = 0xFF6EBF6E;
    /** Brighter green for hover on {@link #flatButtonPositive} (pairs with {@link #C_NEW_PROFILE}). */
    private static final int C_POS_TEXT_H     = 0xFF92E592;
    /** Destructive actions (delete / remove) — reddish accent like {@link #C_NEW_PROFILE} strength. */
    private static final int C_DANGER_TEXT    = 0xFFE07878;
    private static final int C_DANGER_TEXT_H  = 0xFFFFA0A0;

    // ── Panels ─────────────────────────────────────────────────────────────────
    public enum Panel { NONE, EDIT_PROFILE, CHAT_WINDOWS, IGNORED_CHAT, CHAT_SOUNDS, SETTINGS }

    // ── Sidebar hit-test entries (rebuilt each render cycle) ───────────────────
    private record SidebarEntry(int y, int h, boolean isHeader, String profileId, Panel panel) {}
    private final List<SidebarEntry> sidebarEntries = new ArrayList<>();

    // ── Screen state ───────────────────────────────────────────────────────────
    private final Screen parent;
    /** Sidebar rows showing section links; multiple profiles may stay open at once. */
    private final LinkedHashSet<String> expandedProfileIds = new LinkedHashSet<>();
    /** Profile whose panel is shown in the content area; independent of expansion. */
    private String selectedProfileId;
    private Panel activePanel = Panel.NONE;
    private int sidebarScroll;

    // Per-panel scroll positions (survive resize-triggered init() calls)
    private int serverScroll, winScroll, ignScroll, ruleScroll;

    /** Hit box for the “Rainnny” author link in the sidebar header (updated each render). */
    private int sidebarAuthorLinkL, sidebarAuthorLinkR, sidebarAuthorLinkT, sidebarAuthorLinkB;

    /** Milliseconds to confirm destructive actions after first click. */
    private static final long DESTRUCTIVE_CONFIRM_MS = 3000L;

    /** 0 = not armed; otherwise deadline (ms) for second click to delete profile. */
    private long deleteProfileConfirmDeadlineMs;
    /** Per window id: deadline (ms) for second click to remove. */
    private final Map<String, Long> removeWindowConfirmDeadlines = new HashMap<>();

    /** Inline-expanded windows on Chat Windows panel (any subset). */
    private final Set<String> expandedWindowIds = new LinkedHashSet<>();
    /** Per-window pattern list scroll inside an expanded block. */
    private final Map<String, Integer> windowPatScroll = new HashMap<>();

    private boolean createWinDialogOpen;
    private EditBox dlgWinIdField, dlgWinPatField;
    private AbstractWidget dlgCreateButton, dlgCancelButton;
    private int createDlgX, createDlgY, createDlgW, createDlgH;

    private boolean renameDialogOpen;
    private String renameDlgOldId;
    private EditBox dlgRenameIdField;
    private AbstractWidget dlgRenameOkButton, dlgRenameCancelButton;
    private int renameDlgX, renameDlgY, renameDlgW, renameDlgH;

    /** Background rects for expanded window blocks; filled in {@link #buildChatWindowsWidgets}. */
    private record WinChromeRect(int l, int t, int r, int b) {}
    private final List<WinChromeRect> winChromeRects = new ArrayList<>();

    // Widget references rebuilt each init()
    private EditBox nameField, newServerField;
    private EditBox newIgnoreField;
    private EditBox patternField, soundField;

    // Sound autocomplete popup (scrollable when many matches)
    private static final int SUGGEST_VISIBLE_ROWS = 8;
    private static final int SUGGEST_MAX_MATCHES  = 1024;
    private static final int SUGGEST_ROW_H        = 12;
    private List<String> sugFiltered = List.of();
    private int sugScroll;
    private String sugLastQuery;
    private int sugLeft, sugTop, sugWidth, sugVisibleRows;

    private record PendingPatternEdit(EditBox box, ServerProfile profile, String windowId, int userPosition) {}
    private final List<PendingPatternEdit> pendingPatternEdits = new ArrayList<>();
    private record PendingNewPattern(EditBox box, ServerProfile profile, String windowId) {}
    private final List<PendingNewPattern> pendingNewPatterns = new ArrayList<>();
    private record PendingIgnoreEdit(EditBox box, ServerProfile profile, int listIndex) {}
    private final List<PendingIgnoreEdit> pendingIgnoreEdits = new ArrayList<>();
    private record PendingMessageSoundEdit(EditBox patternBox, EditBox soundBox, ServerProfile profile, int listIndex) {}
    private final List<PendingMessageSoundEdit> pendingMessageSoundEdits = new ArrayList<>();

    /** Waiting for the next key / mouse button to assign {@link ChatUtilitiesModClient#OPEN_MENU_KEY}. */
    private boolean rebindingOpenMenuKey;
    private boolean rebindingCopyPlain;
    private boolean rebindingCopyFormatted;

    // ── Constructors ───────────────────────────────────────────────────────────

    public ChatUtilitiesRootScreen(Screen parent) {
        super(Component.literal("Chat Utilities"));
        this.parent = parent;
        ChatUtilitiesManager mgr = ChatUtilitiesManager.get();
        List<ServerProfile> ordered = sortedProfilesList(mgr);
        String savedProfile = ChatUtilitiesClientOptions.getLastMenuProfileId();
        String savedPanelStr = ChatUtilitiesClientOptions.getLastMenuPanel();
        if (savedProfile != null && mgr.getProfile(savedProfile) != null) {
            this.expandedProfileIds.add(savedProfile);
            this.selectedProfileId = savedProfile;
            this.activePanel = parseSavedPanel(savedPanelStr);
        } else {
            ServerProfile active = mgr.getActiveProfile();
            if (active != null) {
                this.expandedProfileIds.add(active.getId());
                this.selectedProfileId = active.getId();
                this.activePanel = Panel.CHAT_WINDOWS;
            } else if (!ordered.isEmpty()) {
                String firstId = ordered.getFirst().getId();
                this.expandedProfileIds.add(firstId);
                this.selectedProfileId = firstId;
                this.activePanel = Panel.CHAT_WINDOWS;
            }
        }
    }

    /** Restore to a specific state after returning from in-world position mode. */
    public ChatUtilitiesRootScreen(Screen parent, String profileId, Panel panel) {
        super(Component.literal("Chat Utilities"));
        this.parent = parent;
        this.expandedProfileIds.add(profileId);
        this.selectedProfileId = profileId;
        this.activePanel = panel;
    }

    // ── ProfileWorkflowScreen ──────────────────────────────────────────────────

    @Override public ChatUtilitiesRootScreen getChatRoot() { return this; }

    @Override
    public Screen recreateForProfile() {
        if (selectedProfileId == null) {
            return new ChatUtilitiesRootScreen(parent);
        }
        return new ChatUtilitiesRootScreen(parent, selectedProfileId, activePanel);
    }

    public Screen getParentScreen() { return parent; }

    // ── Panel / content coordinates ────────────────────────────────────────────

    private int panelLeft()   { return MARGIN_X; }
    private int panelRight()  { return this.width  - MARGIN_X; }
    private int panelTop()    { return MARGIN_Y; }
    private int panelBottom() { return this.height - MARGIN_Y; }
    private int panelW()      { return panelRight()  - panelLeft(); }
    private int panelH()      { return panelBottom() - panelTop(); }

    private int sidebarLeft()   { return panelLeft(); }
    private int sidebarRight()  { return panelLeft() + SIDEBAR_W; }
    private int sidebarTop()    { return panelTop(); }
    private int sidebarBottom() { return panelBottom(); }

    private int contentLeft()  { return sidebarRight() + CONTENT_PAD_X; }
    private int contentRight() { return panelRight()   - CONTENT_PAD_X; }
    private int contentCX()    { return sidebarRight() + (panelRight() - sidebarRight()) / 2; }
    private int contentW()     { return contentRight() - contentLeft(); }
    private int footerY()      { return panelBottom() - FOOTER_INSET; }

    private int profileSoundsFormWidth() {
        return Math.min(PROFILE_SOUNDS_FORM_MAX_W, contentW());
    }

    /** First Y coordinate for form widgets; clears wrapped header text for any panel. */
    private int bodyY() {
        return contentBodyTopY();
    }

    private int headerDescStartY() {
        return panelTop() + TITLE_Y_OFF + TITLE_TO_DESC_GAP;
    }

    private int headerDescBottomY() {
        int descW = Math.min(contentW() - CONTENT_PAD_X, 420);
        int lineCount = this.font.split(Component.literal(headerDescriptionText()), descW).size();
        return headerDescStartY() + lineCount * HEADER_DESC_LINE_SPACING;
    }

    /** Y of the 1px horizontal rule under the page description. */
    private int headerSepTopY() {
        return headerDescBottomY() + DESC_TO_SEP_GAP;
    }

    private int contentBodyTopY() {
        return headerSepTopY() + 1 + HEADER_BODY_GAP;
    }

    private String truncateToWidth(String s, int maxW) {
        if (this.font.width(s) <= maxW) {
            return s;
        }
        String ell = "…";
        int ew = this.font.width(ell);
        if (maxW <= ew) {
            return ell;
        }
        while (s.length() > 1 && this.font.width(s + ell) > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s + ell;
    }

    private static List<ServerProfile> sortedProfilesList(ChatUtilitiesManager mgr) {
        List<ServerProfile> all = new ArrayList<>(mgr.getProfiles());
        ServerProfile active = mgr.getActiveProfile();
        if (active != null && all.remove(active)) {
            all.add(0, active);
        }
        return all;
    }

    private static Panel parseSavedPanel(String name) {
        if (name == null || name.isEmpty()) {
            return Panel.CHAT_WINDOWS;
        }
        try {
            return Panel.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Panel.CHAT_WINDOWS;
        }
    }

    private static void playUiClick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
        }
    }

    private static String modVersionCached;

    private static String modVersionLabel() {
        if (modVersionCached == null) {
            modVersionCached =
                    FabricLoader.getInstance()
                            .getModContainer("chat-utilities")
                            .map(c -> "v" + c.getMetadata().getVersion().getFriendlyString())
                            .orElse("v?");
        }
        return modVersionCached;
    }

    // ── Background override (panel only, game world visible at margins) ─────────

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Subtle global dim so the game world at the margins is not distracting
        g.fill(0, 0, this.width, this.height, 0x55000000);
        // Opaque panel background
        g.fill(panelLeft(), panelTop(), panelRight(), panelBottom(), C_PANEL_BG);
        if (activePanel == Panel.CHAT_WINDOWS && !winChromeRects.isEmpty()) {
            for (WinChromeRect rc : winChromeRects) {
                g.fill(rc.l(), rc.t(), rc.r(), rc.b(), C_WIN_GROUP_BG);
                g.renderOutline(rc.l(), rc.t(), rc.r() - rc.l(), rc.b() - rc.t(), C_WIN_GROUP_EDGE);
            }
        }
    }

    // ── init() ────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        clearWidgets();
        sugFiltered = List.of();
        pendingPatternEdits.clear();
        pendingNewPatterns.clear();
        pendingIgnoreEdits.clear();
        pendingMessageSoundEdits.clear();
        dlgWinIdField = null;
        dlgWinPatField = null;
        dlgCreateButton = null;
        dlgCancelButton = null;
        dlgRenameIdField = null;
        dlgRenameOkButton = null;
        dlgRenameCancelButton = null;

        if (activePanel != Panel.CHAT_WINDOWS) {
            createWinDialogOpen = false;
            renameDialogOpen = false;
            expandedWindowIds.clear();
            removeWindowConfirmDeadlines.clear();
        }
        if (activePanel != Panel.EDIT_PROFILE) {
            deleteProfileConfirmDeadlineMs = 0;
        }

        ChatUtilitiesManager mgrProfiles = ChatUtilitiesManager.get();
        expandedProfileIds.removeIf(id -> mgrProfiles.getProfile(id) == null);
        if (selectedProfileId != null && mgrProfiles.getProfile(selectedProfileId) == null) {
            selectedProfileId = null;
            for (String id : expandedProfileIds) {
                if (mgrProfiles.getProfile(id) != null) {
                    selectedProfileId = id;
                    break;
                }
            }
            if (selectedProfileId == null) {
                activePanel = Panel.NONE;
            }
            expandedWindowIds.clear();
            createWinDialogOpen = false;
            renameDialogOpen = false;
        }

        winChromeRects.clear();

        if (activePanel != Panel.SETTINGS) {
            rebindingOpenMenuKey = false;
            rebindingCopyPlain = false;
            rebindingCopyFormatted = false;
        }

        // Done button — always at bottom-right of content area
        addRenderableWidget(primaryButton(
                Component.literal("Done"), () -> onClose(),
                contentRight() - 80, footerY(), 80, 20));

        switch (activePanel) {
            case EDIT_PROFILE -> buildEditProfileWidgets();
            case CHAT_WINDOWS -> buildChatWindowsWidgets();
            case IGNORED_CHAT -> buildIgnoredChatWidgets();
            case CHAT_SOUNDS  -> buildChatSoundsWidgets();
            case SETTINGS -> buildSettingsWidgets();
            default -> {}
        }

        // After all widgets exist, focus the modal field on the next tick (Done is added first and
        // otherwise wins initial focus; setFocused must run after Screen finishes wiring children).
        if (activePanel == Panel.CHAT_WINDOWS) {
            if (createWinDialogOpen && dlgWinIdField != null) {
                scheduleDialogFirstFieldFocus(dlgWinIdField);
            } else if (renameDialogOpen && dlgRenameIdField != null) {
                scheduleDialogFirstFieldFocus(dlgRenameIdField);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        boolean refresh = false;
        if (deleteProfileConfirmDeadlineMs != 0 && now >= deleteProfileConfirmDeadlineMs) {
            deleteProfileConfirmDeadlineMs = 0;
            if (activePanel == Panel.EDIT_PROFILE) {
                refresh = true;
            }
        }
        int beforeWin = removeWindowConfirmDeadlines.size();
        removeWindowConfirmDeadlines.entrySet().removeIf(e -> now >= e.getValue());
        if (beforeWin != removeWindowConfirmDeadlines.size() && activePanel == Panel.CHAT_WINDOWS) {
            refresh = true;
        }
        if (refresh) {
            init();
        }
    }

    private void scheduleDialogFirstFieldFocus(EditBox box) {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.execute(
                () -> {
                    this.setFocused(box);
                    box.setFocused(true);
                    String v = box.getValue();
                    int len = v.length();
                    box.setCursorPosition(len);
                    box.setHighlightPos(len);
                });
    }

    // ── Flat button helpers ────────────────────────────────────────────────────

    /** Standard flat button used for most actions. */
    private AbstractWidget flatButton(Component label, Runnable press, int x, int y, int w, int h) {
        return new AbstractWidget(x, y, w, h, label) {
            @Override public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean dbl) { press.run(); }
            @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                boolean hov = this.isHovered();
                boolean act = this.active;
                int bg      = !act ? 0x25000000 : hov ? 0x55FFFFFF : 0x35000000;
                int outline = !act ? 0x25FFFFFF : hov ? 0x70FFFFFF : 0x40FFFFFF;
                int tc      = !act ? 0xFF555565 : hov ? 0xFFFFFFFF : 0xFFBBBBCC;
                g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
                g.renderOutline(getX(), getY(), getWidth(), getHeight(), outline);
                g.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                        getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, tc);
            }
            @Override public void updateWidgetNarration(NarrationElementOutput n) {
                defaultButtonNarrationText(n);
            }
        };
    }

    /** Flat button with reddish destructive styling. */
    private AbstractWidget flatButtonDestructive(Component label, Runnable press, int x, int y, int w, int h) {
        return new AbstractWidget(x, y, w, h, label) {
            @Override public void onClick(MouseButtonEvent event, boolean dbl) { press.run(); }
            @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                boolean hov = this.isHovered();
                boolean act = this.active;
                int bg = !act ? 0x35402020 : hov ? 0x55903030 : 0x45302828;
                int outline = !act ? 0x45C06060 : hov ? 0x85FF9090 : 0x65D07070;
                int tc = !act ? C_DANGER_TEXT : hov ? C_DANGER_TEXT_H : 0xFFF0A0A0;
                g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
                g.renderOutline(getX(), getY(), getWidth(), getHeight(), outline);
                g.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                        getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, tc);
            }
            @Override public void updateWidgetNarration(NarrationElementOutput n) {
                defaultButtonNarrationText(n);
            }
        };
    }

    /** Flat button with green “add” styling (same family as sidebar + New Profile). */
    private AbstractWidget flatButtonPositive(Component label, Runnable press, int x, int y, int w, int h) {
        return new AbstractWidget(x, y, w, h, label) {
            @Override public void onClick(MouseButtonEvent event, boolean dbl) { press.run(); }
            @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                boolean hov = this.isHovered();
                boolean act = this.active;
                // Cool green tint — avoid yellow/brown (high R + G) on outline
                int bg = !act ? 0x301C2834 : hov ? 0x40385848 : 0x38283040;
                int outline = !act ? 0x40487058 : hov ? 0x6598C082 : 0x5078A068;
                int tc = !act ? C_NEW_PROFILE : hov ? C_POS_TEXT_H : 0xFF7ED47E;
                g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
                g.renderOutline(getX(), getY(), getWidth(), getHeight(), outline);
                g.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                        getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, tc);
            }
            @Override public void updateWidgetNarration(NarrationElementOutput n) {
                defaultButtonNarrationText(n);
            }
        };
    }

    private void closeCreateWindowDialog() {
        createWinDialogOpen = false;
        init();
    }

    private void closeRenameWindowDialog() {
        renameDialogOpen = false;
        renameDlgOldId = null;
        init();
    }

    /** Accent (blue) button used for primary actions like Done. */
    private AbstractWidget primaryButton(Component label, Runnable press, int x, int y, int w, int h) {
        return new AbstractWidget(x, y, w, h, label) {
            @Override public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean dbl) { press.run(); }
            @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                boolean hov = this.isHovered();
                int bg      = hov ? 0xCC2060A8 : 0xCC1A4A8A;
                int outline = hov ? 0xFF5BAAFF : 0xFF3A80CC;
                g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
                g.renderOutline(getX(), getY(), getWidth(), getHeight(), outline);
                g.drawCenteredString(Minecraft.getInstance().font, getMessage(),
                        getX() + getWidth() / 2, getY() + (getHeight() - 8) / 2, 0xFFFFFFFF);
            }
            @Override public void updateWidgetNarration(NarrationElementOutput n) {
                defaultButtonNarrationText(n);
            }
        };
    }

    // ── Edit Profile panel ────────────────────────────────────────────────────

    private static final int SERVER_ROWS = 6;

    private void buildEditProfileWidgets() {
        ServerProfile p = profile();
        if (p == null) return;
        ChatUtilitiesManager mgr = ChatUtilitiesManager.get();

        int fx = contentLeft();
        int fW = profileSoundsFormWidth();
        int y  = bodyY();

        nameField = new EditBox(this.font, fx, y, fW, 20, Component.literal("name"));
        nameField.setValue(p.getDisplayName());
        nameField.setMaxLength(128);
        nameField.setHint(Component.literal("Profile name"));
        nameField.setResponder(s -> syncProfileDisplayNameFromField(mgr, p, s));
        addRenderableWidget(nameField);
        y += 26;

        newServerField = new EditBox(this.font, fx, y, fW, 20, Component.literal("host"));
        newServerField.setMaxLength(255);
        newServerField.setHint(Component.literal("e.g. play.example.net"));
        addRenderableWidget(newServerField);
        y += 26;

        addRenderableWidget(flatButton(Component.literal("Add Server"), () -> {
            applyName(mgr, p);
            String h = newServerField.getValue().strip().toLowerCase(Locale.ROOT);
            if (!h.isEmpty() && !p.getServers().contains(h)) {
                p.getServers().add(h);
                mgr.markProfileServersDirty();
                newServerField.setValue("");
            }
            init();
        }, fx, y, fW, 20));
        y += 26;

        addRenderableWidget(flatButton(Component.literal("Add Current Server"), () -> {
            applyName(mgr, p);
            String h = ChatUtilitiesManager.currentConnectionHostNormalized();
            if (h != null && !h.isEmpty() && !p.getServers().contains(h)) {
                p.getServers().add(h);
                mgr.markProfileServersDirty();
            }
            init();
        }, fx, y, fW, 20));
        y += 26;

        List<String> servers = p.getServers();
        int srvMax = Math.max(0, servers.size() - SERVER_ROWS);
        serverScroll = Math.min(serverScroll, srvMax);
        int srvEnd = Math.min(serverScroll + SERVER_ROWS, servers.size());
        for (int i = serverScroll; i < srvEnd; i++) {
            String s = servers.get(i);
            String label = s.length() > 32 ? s.substring(0, 29) + "..." : s;
            int idx = i;
            addRenderableWidget(flatButtonDestructive(Component.literal("✕  " + label), () -> {
                applyName(mgr, p);
                p.getServers().remove(idx);
                mgr.markProfileServersDirty();
                serverScroll = Math.min(serverScroll, Math.max(0, p.getServers().size() - SERVER_ROWS));
                init();
            }, fx, y, fW, 20));
            y += 26;
        }
        if (srvMax > 0) {
            boolean up = serverScroll > 0;
            boolean down = serverScroll < srvMax;
            if (up && down) {
                int half = (fW - 4) / 2;
                addRenderableWidget(flatButton(Component.literal("▲"),
                        () -> { serverScroll = Math.max(0, serverScroll - 1); init(); },
                        fx, y, half, 20));
                addRenderableWidget(flatButton(Component.literal("▼"),
                        () -> { serverScroll = Math.min(srvMax, serverScroll + 1); init(); },
                        fx + half + 4, y, half, 20));
            } else if (up) {
                addRenderableWidget(flatButton(Component.literal("▲"),
                        () -> { serverScroll = Math.max(0, serverScroll - 1); init(); },
                        fx, y, fW, 20));
            } else {
                addRenderableWidget(flatButton(Component.literal("▼"),
                        () -> { serverScroll = Math.min(srvMax, serverScroll + 1); init(); },
                        fx, y, fW, 20));
            }
            y += 26;
        }

        long nowDel = System.currentTimeMillis();
        boolean deleteArmed = deleteProfileConfirmDeadlineMs > nowDel;
        addRenderableWidget(flatButtonDestructive(
                Component.literal(deleteArmed ? "✕ Confirm Deletion" : "✕ Delete Profile"),
                () -> {
                    long t = System.currentTimeMillis();
                    if (deleteProfileConfirmDeadlineMs > t) {
                        deleteProfileConfirmDeadlineMs = 0;
                        ChatUtilitiesManager.get().removeProfile(selectedProfileId);
                        if (minecraft != null) {
                            minecraft.setScreen(new ChatUtilitiesRootScreen(parent));
                        }
                    } else {
                        deleteProfileConfirmDeadlineMs = t + DESTRUCTIVE_CONFIRM_MS;
                        init();
                    }
                },
                fx,
                footerY(),
                140,
                20));
    }

    private void applyName(ChatUtilitiesManager mgr, ServerProfile p) {
        if (nameField == null) return;
        syncProfileDisplayNameFromField(mgr, p, nameField.getValue());
    }

    /** Keeps {@link ServerProfile} display name in sync with the name field (sidebar + config). */
    private static void syncProfileDisplayNameFromField(
            ChatUtilitiesManager mgr, ServerProfile p, String raw) {
        String n = raw.strip();
        p.setDisplayName(n.isEmpty() ? p.getId() : n);
        mgr.markProfileServersDirty();
    }

    // ── Chat Windows panel ────────────────────────────────────────────────────

    private static final int WIN_PAT_VISIBLE = 5;
    /** Vertical space after each expanded window block before the next row. */
    private static final int WIN_EXPANDED_TAIL_GAP = 12;
    /** Space above footer buttons (Create Window / Adjust Layout). */
    private static final int WIN_LIST_FOOTER_GAP = 26;

    private int patScrollFor(String wid) {
        return windowPatScroll.getOrDefault(wid, 0);
    }

    private static List<String> validWindowIds(ServerProfile p) {
        List<String> winIds = new ArrayList<>();
        for (String wid : p.getWindowIds()) {
            if (p.getWindow(wid) != null) {
                winIds.add(wid);
            }
        }
        return winIds;
    }

    /** Largest first-index {@code winScroll} so at least one full window row still fits in {@code listBottom}. */
    private int maxChatWindowListScroll(ServerProfile p, List<String> winIds, int listBottom) {
        int n = winIds.size();
        int best = 0;
        for (int start = 0; start < n; start++) {
            int y = bodyY();
            boolean any = false;
            for (int j = start; j < n; j++) {
                int bh = chatWindowBlockHeight(p, winIds.get(j));
                if (y + bh > listBottom) {
                    break;
                }
                any = true;
                y += bh;
            }
            if (any) {
                best = start;
            }
        }
        return best;
    }

    private int expandedWindowContentHeight(ChatWindow w, int patScroll) {
        List<String> sources = w.getPatternSources();
        int patMax = Math.max(0, sources.size() - WIN_PAT_VISIBLE);
        int scroll = Mth.clamp(patScroll, 0, patMax);
        int visiblePatRows = Math.min(WIN_PAT_VISIBLE, Math.max(0, sources.size() - scroll));
        int navH = sources.size() > WIN_PAT_VISIBLE ? 18 : 0;
        // Inner padding + pattern row + nav + pattern list + bottom padding (Hide/Remove live in header row)
        return 6 + 26 + navH + visiblePatRows * 20 + 8;
    }

    private int chatWindowBlockHeight(ServerProfile p, String wid) {
        int h = 20;
        if (expandedWindowIds.contains(wid)) {
            ChatWindow w = p.getWindow(wid);
            if (w != null) {
                h += expandedWindowContentHeight(w, patScrollFor(wid));
                h += WIN_EXPANDED_TAIL_GAP;
            }
        }
        return h;
    }

    private int buildExpandedWindowBlock(
            ChatUtilitiesManager mgr, ServerProfile p, String wid, ChatWindow w,
            int fx, int fW, int y) {
        y += 6;

        int fieldW = Math.min(130, fW - 68);
        EditBox newPat = new EditBox(this.font, fx, y, fieldW, 20, Component.literal("pat"));
        newPat.setMaxLength(2048);
        newPat.setHint(ChatUtilitiesScreenLayout.PATTERN_INPUT_HINT);
        addRenderableWidget(newPat);
        pendingNewPatterns.add(new PendingNewPattern(newPat, p, wid));
        addRenderableWidget(flatButton(Component.literal("Add"), () -> {
            try {
                mgr.addPattern(p, wid, newPat.getValue());
                newPat.setValue("");
            } catch (PatternSyntaxException ignored) {}
            init();
        }, fx + fieldW + 4, y, 62, 20));
        y += 26;

        List<String> sources = w.getPatternSources();
        int patMax = Math.max(0, sources.size() - WIN_PAT_VISIBLE);
        int rawScroll = patScrollFor(wid);
        int scroll = Mth.clamp(rawScroll, 0, patMax);
        if (scroll != rawScroll) {
            windowPatScroll.put(wid, scroll);
        }
        final String scrollKey = wid;
        int from = scroll;
        int to = Math.min(from + WIN_PAT_VISIBLE, sources.size());

        if (sources.size() > WIN_PAT_VISIBLE) {
            int navBtnW = 40;
            if (scroll > 0) {
                addRenderableWidget(flatButton(Component.literal("Earlier"),
                        () -> {
                            int s = windowPatScroll.getOrDefault(scrollKey, 0);
                            windowPatScroll.put(scrollKey, Math.max(0, s - WIN_PAT_VISIBLE));
                            init();
                        },
                        fx, y, navBtnW, 14));
            }
            if (scroll < patMax) {
                addRenderableWidget(flatButton(Component.literal("Later"),
                        () -> {
                            int s = windowPatScroll.getOrDefault(scrollKey, 0);
                            windowPatScroll.put(scrollKey, Math.min(patMax, s + WIN_PAT_VISIBLE));
                            init();
                        },
                        fx + navBtnW + 6, y, navBtnW, 14));
            }
            y += 18;
        }

        int patXW = 24;
        int patGap = 4;
        for (int pi = from; pi < to; pi++) {
            String src = sources.get(pi);
            int pos = pi + 1;
            EditBox patEb = new EditBox(this.font, fx, y, fW - patXW - patGap, 20, Component.literal("wp" + pos));
            patEb.setMaxLength(2048);
            patEb.setValue(src);
            patEb.setHint(ChatUtilitiesScreenLayout.PATTERN_INPUT_HINT);
            addRenderableWidget(patEb);
            addRenderableWidget(flatButtonDestructive(Component.literal("✕"), () -> {
                mgr.removePattern(p, wid, pos);
                init();
            }, fx + fW - patXW, y, patXW, 20));
            pendingPatternEdits.add(new PendingPatternEdit(patEb, p, wid, pos));
            y += 20;
        }

        return y + 8;
    }

    private void buildCreateWindowDialog(ChatUtilitiesManager mgr, ServerProfile p) {
        int dlgW = Math.min(340, Math.max(240, contentW() - 24));
        int cx = contentCX();
        int dlgX = cx - dlgW / 2;
        int dlgY = bodyY() + 20;
        int dlgInnerPad = 12;
        int dlgGapAfterTitle = 10;
        int dlgGapFieldsToButtons = 14;
        int fieldH = 20;
        int btnH = 20;
        createDlgX = dlgX;
        createDlgY = dlgY;
        createDlgW = dlgW;

        int titleBaseline = dlgY + dlgInnerPad;
        int row1Y = titleBaseline + this.font.lineHeight + dlgGapAfterTitle;
        int btnY = row1Y + fieldH + dlgGapFieldsToButtons;
        createDlgH = btnY - dlgY + btnH + dlgInnerPad;

        int idW = Math.min(120, dlgW / 2 - dlgInnerPad);
        dlgWinIdField = new EditBox(this.font, dlgX + dlgInnerPad, row1Y, idW, fieldH, Component.literal("nwid"));
        dlgWinIdField.setMaxLength(64);
        dlgWinIdField.setHint(Component.literal("Window id"));
        addRenderableWidget(dlgWinIdField);

        int patLeft = dlgX + dlgInnerPad + idW + 10;
        int patRight = dlgX + dlgW - dlgInnerPad;
        int patW = Math.max(80, patRight - patLeft);
        dlgWinPatField = new EditBox(this.font, patLeft, row1Y, patW, fieldH, Component.literal("nwpat"));
        dlgWinPatField.setMaxLength(2048);
        dlgWinPatField.setHint(ChatUtilitiesScreenLayout.PATTERN_INPUT_HINT);
        addRenderableWidget(dlgWinPatField);

        dlgCreateButton = flatButton(
                Component.literal("Create"),
                () -> submitNewChatWindowDialog(mgr, p),
                dlgX + dlgInnerPad,
                btnY,
                76,
                btnH);
        addRenderableWidget(dlgCreateButton);

        dlgCancelButton = flatButton(Component.literal("Cancel"), () -> closeCreateWindowDialog(),
                dlgX + dlgW - dlgInnerPad - 76, btnY, 76, btnH);
        addRenderableWidget(dlgCancelButton);
    }

    private void buildRenameWindowDialog(ChatUtilitiesManager mgr, ServerProfile p) {
        int dlgW = Math.min(340, Math.max(240, contentW() - 24));
        int cx = contentCX();
        int dlgX = cx - dlgW / 2;
        int dlgY = bodyY() + 20;
        int dlgInnerPad = 12;
        int dlgGapAfterTitle = 10;
        int dlgGapFieldsToButtons = 14;
        int fieldH = 20;
        int btnH = 20;
        renameDlgX = dlgX;
        renameDlgY = dlgY;
        renameDlgW = dlgW;

        int titleBaseline = dlgY + dlgInnerPad;
        int row1Y = titleBaseline + this.font.lineHeight + dlgGapAfterTitle;
        int btnY = row1Y + fieldH + dlgGapFieldsToButtons;
        renameDlgH = btnY - dlgY + btnH + dlgInnerPad;

        int fieldW = dlgW - 2 * dlgInnerPad;
        dlgRenameIdField = new EditBox(this.font, dlgX + dlgInnerPad, row1Y, fieldW, fieldH,
                Component.literal("rnwid"));
        dlgRenameIdField.setMaxLength(64);
        dlgRenameIdField.setValue(renameDlgOldId != null ? renameDlgOldId : "");
        dlgRenameIdField.setHint(Component.literal("Window id"));
        addRenderableWidget(dlgRenameIdField);

        dlgRenameOkButton = flatButton(
                Component.literal("Rename"),
                () -> submitRenameWindowDialog(mgr, p),
                dlgX + dlgInnerPad,
                btnY,
                76,
                btnH);
        addRenderableWidget(dlgRenameOkButton);

        dlgRenameCancelButton = flatButton(Component.literal("Cancel"), () -> closeRenameWindowDialog(),
                dlgX + dlgW - dlgInnerPad - 76, btnY, 76, btnH);
        addRenderableWidget(dlgRenameCancelButton);
    }

    private void submitRenameWindowDialog(ChatUtilitiesManager mgr, ServerProfile p) {
        if (dlgRenameIdField == null || renameDlgOldId == null) {
            return;
        }
        String newId = dlgRenameIdField.getValue().strip();
        if (newId.isEmpty()) {
            return;
        }
        if (mgr.renameWindow(p, renameDlgOldId, newId)) {
            if (expandedWindowIds.remove(renameDlgOldId)) {
                expandedWindowIds.add(newId);
            }
            Integer patScroll = windowPatScroll.remove(renameDlgOldId);
            if (patScroll != null) {
                windowPatScroll.put(newId, patScroll);
            }
            renameDialogOpen = false;
            renameDlgOldId = null;
            init();
        }
    }

    /** Same behavior as the New Chat Window dialog’s Create button. */
    private void submitNewChatWindowDialog(ChatUtilitiesManager mgr, ServerProfile p) {
        if (dlgWinIdField == null) {
            return;
        }
        String id = dlgWinIdField.getValue().strip();
        if (id.isEmpty()) {
            return;
        }
        try {
            String pat = dlgWinPatField != null ? dlgWinPatField.getValue() : "";
            if (mgr.createWindow(p, id, pat)) {
                createWinDialogOpen = false;
                init();
                return;
            }
        } catch (PatternSyntaxException ignored) {
        }
        init();
    }

    private void buildChatWindowsWidgets() {
        ServerProfile p = profile();
        if (p == null) return;
        pendingPatternEdits.clear();
        pendingNewPatterns.clear();
        winChromeRects.clear();
        ChatUtilitiesManager mgr = ChatUtilitiesManager.get();

        int fx = contentLeft();
        int fW = contentW();
        int y = bodyY();

        final String pid = selectedProfileId;

        List<String> winIds = validWindowIds(p);
        expandedWindowIds.removeIf(w -> p.getWindow(w) == null);

        int listBottom = footerY() - WIN_LIST_FOOTER_GAP;
        int maxWinScroll = maxChatWindowListScroll(p, winIds, listBottom);
        winScroll = Mth.clamp(winScroll, 0, maxWinScroll);

        int rowW = fW;
        int gapBtn = 3;
        int removeW = 118;
        int visW = 72;
        int renameW = 68;
        int rightBlockW = visW + renameW + removeW + 3 * gapBtn;
        int toggleW = Math.max(48, rowW - rightBlockW);

        int i = winScroll;
        for (; i < winIds.size(); i++) {
            String wid = winIds.get(i);
            int blockH = chatWindowBlockHeight(p, wid);
            if (y + blockH > listBottom) {
                break;
            }
            ChatWindow wObj = p.getWindow(wid);
            if (wObj == null) {
                continue;
            }
            boolean expanded = expandedWindowIds.contains(wid);
            String headText = (expanded ? "▾ " : "▸ ") + wid;
            headText = truncateToWidth(headText, toggleW - 10);

            final String fwid = wid;
            int rowTop = y;
            addRenderableWidget(flatButton(Component.literal(headText), () -> {
                if (expandedWindowIds.contains(fwid)) {
                    expandedWindowIds.remove(fwid);
                    windowPatScroll.remove(fwid);
                } else {
                    expandedWindowIds.add(fwid);
                }
                init();
            }, fx, y, toggleW, 18));

            int bx = fx + toggleW + gapBtn;
            String visLabel = wObj.isVisible()
                    ? CHAT_WIN_EMOJI_VISIBLE + " Hide"
                    : CHAT_WIN_EMOJI_HIDDEN + " Show";
            AbstractWidget visBtn = flatButton(
                    Component.literal(visLabel),
                    () -> {
                        mgr.toggleVisibility(p, fwid);
                        init();
                    },
                    bx,
                    y,
                    visW,
                    18);
            visBtn.setTooltip(Tooltip.create(Component.literal(
                    wObj.isVisible() ? "Hide window" : "Show window")));
            addRenderableWidget(visBtn);
            bx += visW + gapBtn;
            addRenderableWidget(flatButton(
                    Component.literal(CHAT_WIN_SYMBOL_RENAME + " Rename"),
                    () -> {
                        createWinDialogOpen = false;
                        renameDlgOldId = fwid;
                        renameDialogOpen = true;
                        init();
                    },
                    bx,
                    y,
                    renameW,
                    18));
            bx += renameW + gapBtn;
            long nowRm = System.currentTimeMillis();
            boolean removeArmed = removeWindowConfirmDeadlines.getOrDefault(fwid, 0L) > nowRm;
            addRenderableWidget(flatButtonDestructive(
                    Component.literal(removeArmed ? "✕ Confirm Removal" : "✕ Remove"),
                    () -> {
                        long t = System.currentTimeMillis();
                        if (removeWindowConfirmDeadlines.getOrDefault(fwid, 0L) > t) {
                            removeWindowConfirmDeadlines.remove(fwid);
                            mgr.removeWindow(p, fwid);
                            expandedWindowIds.remove(fwid);
                            windowPatScroll.remove(fwid);
                            List<String> ids = p.getWindowIds();
                            winScroll = Math.min(winScroll, Math.max(0, ids.size() - 1));
                            init();
                        } else {
                            removeWindowConfirmDeadlines.put(fwid, t + DESTRUCTIVE_CONFIRM_MS);
                            init();
                        }
                    },
                    bx,
                    y,
                    removeW,
                    18));

            y += 20;

            if (expanded) {
                y = buildExpandedWindowBlock(mgr, p, wid, wObj, fx, rowW, y);
                winChromeRects.add(new WinChromeRect(fx - 4, rowTop - 4, fx + rowW + 4, y + 4));
                y += WIN_EXPANDED_TAIL_GAP;
            }
        }

        int footY = footerY();
        int createBtnW = 124;
        int footerBtnGap = 6;
        int adjustBtnW = 118;
        addRenderableWidget(flatButtonPositive(Component.literal("+ Create Window"), () -> {
            renameDialogOpen = false;
            renameDlgOldId = null;
            createWinDialogOpen = true;
            init();
        }, contentLeft(), footY, createBtnW, 20));
        addRenderableWidget(flatButton(Component.literal("Adjust Layout"), () -> {
            if (pid == null || profile() == null) return;
            mgr.enableAllWindowsPositioning(pid);
            mgr.setRestoreScreenAfterPosition(
                    () -> new ChatUtilitiesRootScreen(parent, pid, Panel.CHAT_WINDOWS));
            Minecraft.getInstance().setScreen(new ChatScreen("", false));
        }, contentLeft() + createBtnW + footerBtnGap, footY, adjustBtnW, 20));

        if (createWinDialogOpen) {
            buildCreateWindowDialog(mgr, p);
        }
        if (renameDialogOpen) {
            buildRenameWindowDialog(mgr, p);
        }
    }

    // ── Ignored Chat panel ────────────────────────────────────────────────────

    private static final int IGN_ROWS = 9;

    private void buildIgnoredChatWidgets() {
        ServerProfile p = profile();
        if (p == null) return;
        ChatUtilitiesManager mgr = ChatUtilitiesManager.get();

        int fx  = contentLeft();
        int fW  = Math.min(240, contentW());
        int addW = 72;
        int y   = bodyY();

        newIgnoreField = new EditBox(this.font, fx, y, fW - addW - 4, 20, Component.literal("ign"));
        newIgnoreField.setMaxLength(2048);
        newIgnoreField.setHint(ChatUtilitiesScreenLayout.PATTERN_INPUT_HINT);
        addRenderableWidget(newIgnoreField);
        addRenderableWidget(flatButton(Component.literal("Add Ignore"), () -> {
            try {
                mgr.addIgnorePattern(p, newIgnoreField.getValue());
                newIgnoreField.setValue("");
            } catch (PatternSyntaxException ignored) {}
            init();
        }, fx + fW - addW, y, addW, 20));
        y += 26;

        List<String> ignores = p.getIgnorePatternSources();
        int igMax = Math.max(0, ignores.size() - IGN_ROWS);
        ignScroll = Math.min(ignScroll, igMax);
        int igEnd = Math.min(ignScroll + IGN_ROWS, ignores.size());
        int ignPatXW = 24;
        int ignPatGap = 4;
        for (int i = ignScroll; i < igEnd; i++) {
            String pat = ignores.get(i);
            int idx = i;
            EditBox ignEb = new EditBox(this.font, fx, y, fW - ignPatXW - ignPatGap, 20, Component.literal("ign" + idx));
            ignEb.setMaxLength(2048);
            ignEb.setValue(pat);
            ignEb.setHint(ChatUtilitiesScreenLayout.PATTERN_INPUT_HINT);
            addRenderableWidget(ignEb);
            addRenderableWidget(flatButtonDestructive(Component.literal("✕"), () -> {
                mgr.removeIgnorePattern(p, idx);
                init();
            }, fx + fW - ignPatXW, y, ignPatXW, 20));
            pendingIgnoreEdits.add(new PendingIgnoreEdit(ignEb, p, idx));
            y += 20;
        }
        if (igMax > 0) {
            if (ignScroll > 0)
                addRenderableWidget(flatButton(Component.literal("▲"),
                        () -> { ignScroll = Math.max(0, ignScroll - 1); init(); },
                        fx, y, 20, 14));
            if (ignScroll < igMax)
                addRenderableWidget(flatButton(Component.literal("▼"),
                        () -> { ignScroll = Math.min(igMax, ignScroll + 1); init(); },
                        fx + 24, y, 20, 14));
        }
    }

    // ── Chat Sounds panel ─────────────────────────────────────────────────────

    private static final int RULE_ROWS = 7;

    private void buildChatSoundsWidgets() {
        ServerProfile p = profile();
        if (p == null) return;
        ChatUtilitiesManager mgr = ChatUtilitiesManager.get();

        int fx = contentLeft();
        int fW = profileSoundsFormWidth();
        int y  = bodyY();

        patternField = new EditBox(this.font, fx, y, fW, 20, Component.literal("pat"));
        patternField.setMaxLength(2048);
        patternField.setHint(ChatUtilitiesScreenLayout.PATTERN_INPUT_HINT);
        addRenderableWidget(patternField);
        y += 26;

        soundField = new EditBox(this.font, fx, y, fW, 20, Component.literal("snd"));
        soundField.setMaxLength(256);
        soundField.setHint(Component.literal("Sound id…"));
        addRenderableWidget(soundField);
        y += 26;

        addRenderableWidget(flatButton(Component.literal("Test Sound"), () ->
                ChatUtilitiesManager.parseSoundId(soundField.getValue())
                        .filter(ChatUtilitiesManager::isRegisteredSound)
                        .ifPresent(id -> ChatUtilitiesManager.playSoundPreview(id))
        , fx, y, fW, 20));
        y += 26;
        addRenderableWidget(flatButton(Component.literal("Add Rule"), () -> {
            try {
                mgr.addMessageSound(p, patternField.getValue(), soundField.getValue());
                patternField.setValue("");
            } catch (IllegalArgumentException ignored) {}
            init();
        }, fx, y, fW, 20));
        y += 26;

        List<me.braydon.chatutilities.chat.MessageSoundRule> rules = p.getMessageSounds();
        int rMax = Math.max(0, rules.size() - RULE_ROWS);
        ruleScroll = Math.min(ruleScroll, rMax);
        int rEnd = Math.min(ruleScroll + RULE_ROWS, rules.size());
        int ruleXW = 24;
        int ruleGap = 4;
        int ruleInner = fW - ruleXW - ruleGap;
        int ruleSndW = Math.min(140, ruleInner / 2);
        int rulePatW = ruleInner - ruleSndW - ruleGap;
        for (int i = ruleScroll; i < rEnd; i++) {
            MessageSoundRule rule = rules.get(i);
            int idx = i;
            Identifier sid = ChatUtilitiesManager.parseSoundId(rule.getSoundId()).orElse(null);
            String sndDisp = sid != null ? sid.toString() : rule.getSoundId();

            EditBox patEb = new EditBox(this.font, fx, y, rulePatW, 20, Component.literal("rpat" + idx));
            patEb.setMaxLength(2048);
            patEb.setValue(rule.getPatternSource());
            patEb.setHint(ChatUtilitiesScreenLayout.PATTERN_INPUT_HINT);
            addRenderableWidget(patEb);

            EditBox sndEb = new EditBox(this.font, fx + rulePatW + ruleGap, y, ruleSndW, 20, Component.literal("rsnd" + idx));
            sndEb.setMaxLength(256);
            sndEb.setValue(sndDisp);
            sndEb.setHint(Component.literal("Sound id…"));
            addRenderableWidget(sndEb);

            addRenderableWidget(flatButtonDestructive(Component.literal("✕"), () -> {
                mgr.removeMessageSound(p, idx);
                init();
            }, fx + fW - ruleXW, y, ruleXW, 20));

            pendingMessageSoundEdits.add(new PendingMessageSoundEdit(patEb, sndEb, p, idx));
            y += 26;
        }
        if (rMax > 0) {
            boolean up = ruleScroll > 0;
            boolean down = ruleScroll < rMax;
            if (up && down) {
                int half = (fW - 4) / 2;
                addRenderableWidget(flatButton(Component.literal("▲"),
                        () -> { ruleScroll = Math.max(0, ruleScroll - 1); init(); },
                        fx, y, half, 20));
                addRenderableWidget(flatButton(Component.literal("▼"),
                        () -> { ruleScroll = Math.min(rMax, ruleScroll + 1); init(); },
                        fx + half + 4, y, half, 20));
            } else if (up) {
                addRenderableWidget(flatButton(Component.literal("▲"),
                        () -> { ruleScroll = Math.max(0, ruleScroll - 1); init(); },
                        fx, y, fW, 20));
            } else {
                addRenderableWidget(flatButton(Component.literal("▼"),
                        () -> { ruleScroll = Math.min(rMax, ruleScroll + 1); init(); },
                        fx, y, fW, 20));
            }
        }
    }

    private void buildSettingsWidgets() {
        int y = settingsFormBodyY();
        int keyBtnW = 160;
        int keyBtnX = contentRight() - keyBtnW;

        AbstractWidget openMenuKeyButton =
                new AbstractWidget(keyBtnX, y, keyBtnW, 22, Component.literal(keybindCaptionOnly())) {
                    @Override
                    protected boolean isValidClickButton(MouseButtonInfo info) {
                        int b = info.button();
                        return b == 0 || b == 1;
                    }

                    @Override
                    public void onClick(MouseButtonEvent event, boolean doubleClick) {
                        if (event.button() == 1) {
                            ChatUtilitiesModClient.OPEN_MENU_KEY.setKey(InputConstants.UNKNOWN);
                            KeyMapping.resetMapping();
                            saveOptions();
                            rebindingOpenMenuKey = false;
                            rebindingCopyPlain = false;
                            rebindingCopyFormatted = false;
                            init();
                            return;
                        }
                        rebindingCopyPlain = false;
                        rebindingCopyFormatted = false;
                        rebindingOpenMenuKey = true;
                        init();
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        int bg = !act ? 0x25000000 : hov ? 0x55FFFFFF : 0x35000000;
                        int outline = !act ? 0x25FFFFFF : hov ? 0x70FFFFFF : 0x40FFFFFF;
                        int tc = !act ? 0xFF555565 : hov ? 0xFFFFFFFF : 0xFFBBBBCC;
                        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
                        g.renderOutline(getX(), getY(), getWidth(), getHeight(), outline);
                        g.drawCenteredString(
                                Minecraft.getInstance().font,
                                getMessage(),
                                getX() + getWidth() / 2,
                                getY() + (getHeight() - 8) / 2,
                                tc);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        openMenuKeyButton.setTooltip(
                Tooltip.create(Component.translatable("chat-utilities.settings.open_menu.tooltip")));
        addRenderableWidget(openMenuKeyButton);
        y += 28;

        addRenderableWidget(flatButtonCopyMouseBinding(keyBtnX, y, keyBtnW, 22, true));
        y += 28;
        addRenderableWidget(flatButtonCopyMouseBinding(keyBtnX, y, keyBtnW, 22, false));
        y += 28;

        AbstractWidget resetControls =
                flatButton(Component.literal("Reset to default"), () -> {
                    KeyMapping km = ChatUtilitiesModClient.OPEN_MENU_KEY;
                    km.setKey(km.getDefaultKey());
                    KeyMapping.resetMapping();
                    saveOptions();
                    rebindingOpenMenuKey = false;
                    rebindingCopyPlain = false;
                    rebindingCopyFormatted = false;
                    init();
                }, keyBtnX, y, keyBtnW, 20);
        resetControls.setTooltip(
                Tooltip.create(Component.translatable("chat-utilities.settings.reset_controls.tooltip")));
        addRenderableWidget(resetControls);

        int clickCopyY = settingsClickToCopyRowY();
        addRenderableWidget(flatButtonClickToCopy(keyBtnX, clickCopyY, keyBtnW, 22));

        int fmtStyleY = settingsCopyFormattedStyleRowY();
        addRenderableWidget(flatButtonCopyFormattedStyle(keyBtnX, fmtStyleY, keyBtnW, 22));

        int symY = settingsSymbolSelectorRowY();
        addRenderableWidget(flatButtonSymbolSelector(keyBtnX, symY, keyBtnW, 22));

        int shadowY = settingsShadowRowY();
        addRenderableWidget(flatButtonChatTextShadow(keyBtnX, shadowY, keyBtnW, 22));

        int profY = settingsProfilesFormY();
        int gap = 8;
        int btnW = 132;
        int leftX = contentLeft();
        AbstractWidget importBtn =
                flatButton(
                        Component.literal("⬆ Import Profiles"),
                        () -> runImportProfilesDialog(),
                        leftX,
                        profY,
                        btnW,
                        20);
        importBtn.setTooltip(
                Tooltip.create(Component.translatable("chat-utilities.settings.import_profiles.tooltip")));
        addRenderableWidget(importBtn);
        AbstractWidget exportBtn =
                flatButton(
                        Component.literal("⬇ Export Profiles"),
                        () -> runExportProfilesDialog(),
                        leftX + btnW + gap,
                        profY,
                        btnW,
                        20);
        exportBtn.setTooltip(
                Tooltip.create(Component.translatable("chat-utilities.settings.export_profiles.tooltip")));
        addRenderableWidget(exportBtn);
    }

    private AbstractWidget flatButtonSymbolSelector(int x, int y, int w, int h) {
        AbstractWidget sym =
                new AbstractWidget(x, y, w, h, Component.empty()) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        ChatUtilitiesClientOptions.toggleShowChatSymbolSelector();
                        init();
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean on = ChatUtilitiesClientOptions.isShowChatSymbolSelector();
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        int bg = !act ? 0x25000000 : hov ? 0x55FFFFFF : 0x35000000;
                        int outline = !act ? 0x25FFFFFF : hov ? 0x70FFFFFF : 0x40FFFFFF;
                        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
                        g.renderOutline(getX(), getY(), getWidth(), getHeight(), outline);
                        Component cap = Component.literal(on ? "Enabled" : "Disabled");
                        int tc = on ? 0xFF55FF55 : 0xFFFF5555;
                        g.drawCenteredString(
                                Minecraft.getInstance().font,
                                cap,
                                getX() + getWidth() / 2,
                                getY() + (getHeight() - 8) / 2,
                                tc);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        sym.setTooltip(
                Tooltip.create(
                        Component.translatable("chat-utilities.settings.chat_symbol_selector.tooltip")));
        return sym;
    }

    private AbstractWidget flatButtonCopyFormattedStyle(int x, int y, int w, int h) {
        AbstractWidget btn =
                new AbstractWidget(x, y, w, h, Component.empty()) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        ChatUtilitiesClientOptions.cycleCopyFormattedStyle();
                        init();
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        int bg = !act ? 0x25000000 : hov ? 0x55FFFFFF : 0x35000000;
                        int outline = !act ? 0x25FFFFFF : hov ? 0x70FFFFFF : 0x40FFFFFF;
                        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
                        g.renderOutline(getX(), getY(), getWidth(), getHeight(), outline);
                        Component cap =
                                switch (ChatUtilitiesClientOptions.getCopyFormattedStyle()) {
                                    case SECTION_SYMBOL -> Component.translatable(
                                            "chat-utilities.settings.copy_formatted_style.value.section_symbol");
                                    case MINIMESSAGE -> Component.translatable(
                                            "chat-utilities.settings.copy_formatted_style.value.minimessage");
                                };
                        int tc = 0xFFBBBBCC;
                        g.drawCenteredString(
                                Minecraft.getInstance().font,
                                cap,
                                getX() + getWidth() / 2,
                                getY() + (getHeight() - 8) / 2,
                                tc);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        btn.setTooltip(
                Tooltip.create(
                        Component.translatable("chat-utilities.settings.copy_formatted_style.tooltip")));
        return btn;
    }

    private AbstractWidget flatButtonChatTextShadow(int x, int y, int w, int h) {
        AbstractWidget sh =
                new AbstractWidget(x, y, w, h, Component.empty()) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        ChatUtilitiesClientOptions.toggleChatTextShadow();
                        init();
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean on = ChatUtilitiesClientOptions.isChatTextShadow();
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        int bg = !act ? 0x25000000 : hov ? 0x55FFFFFF : 0x35000000;
                        int outline = !act ? 0x25FFFFFF : hov ? 0x70FFFFFF : 0x40FFFFFF;
                        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
                        g.renderOutline(getX(), getY(), getWidth(), getHeight(), outline);
                        Component cap = Component.literal(on ? "Enabled" : "Disabled");
                        int tc = on ? 0xFF55FF55 : 0xFFFF5555;
                        g.drawCenteredString(
                                Minecraft.getInstance().font,
                                cap,
                                getX() + getWidth() / 2,
                                getY() + (getHeight() - 8) / 2,
                                tc);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        sh.setTooltip(
                Tooltip.create(Component.translatable("chat-utilities.settings.chat_text_shadow.tooltip")));
        return sh;
    }

    private AbstractWidget flatButtonClickToCopy(int x, int y, int w, int h) {
        AbstractWidget c =
                new AbstractWidget(x, y, w, h, Component.empty()) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        ChatUtilitiesClientOptions.toggleClickToCopyEnabled();
                        init();
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean on = ChatUtilitiesClientOptions.isClickToCopyEnabled();
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        int bg = !act ? 0x25000000 : hov ? 0x55FFFFFF : 0x35000000;
                        int outline = !act ? 0x25FFFFFF : hov ? 0x70FFFFFF : 0x40FFFFFF;
                        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
                        g.renderOutline(getX(), getY(), getWidth(), getHeight(), outline);
                        Component cap = Component.literal(on ? "Enabled" : "Disabled");
                        int tc = on ? 0xFF55FF55 : 0xFFFF5555;
                        g.drawCenteredString(
                                Minecraft.getInstance().font,
                                cap,
                                getX() + getWidth() / 2,
                                getY() + (getHeight() - 8) / 2,
                                tc);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        c.setTooltip(
                Tooltip.create(Component.translatable("chat-utilities.settings.click_to_copy.tooltip")));
        return c;
    }

    private AbstractWidget flatButtonCopyMouseBinding(int x, int y, int width, int h, boolean plain) {
        AbstractWidget bindBtn =
                new AbstractWidget(x, y, width, h, Component.literal(copyBindCaptionOnly(plain))) {
                    @Override
                    protected boolean isValidClickButton(MouseButtonInfo info) {
                        int b = info.button();
                        return b == 0 || b == 1;
                    }

                    @Override
                    public void onClick(MouseButtonEvent event, boolean doubleClick) {
                        if (event.button() == 1) {
                            if (plain) {
                                ChatUtilitiesClientOptions.setCopyPlainBinding(
                                        ChatUtilitiesClientOptions.ClickMouseBinding.defaultPlain());
                            } else {
                                ChatUtilitiesClientOptions.setCopyFormattedBinding(
                                        ChatUtilitiesClientOptions.ClickMouseBinding.defaultFormatted());
                            }
                            rebindingOpenMenuKey = false;
                            rebindingCopyPlain = false;
                            rebindingCopyFormatted = false;
                            init();
                            return;
                        }
                        rebindingOpenMenuKey = false;
                        rebindingCopyPlain = plain;
                        rebindingCopyFormatted = !plain;
                        init();
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        int bg = !act ? 0x25000000 : hov ? 0x55FFFFFF : 0x35000000;
                        int outline = !act ? 0x25FFFFFF : hov ? 0x70FFFFFF : 0x40FFFFFF;
                        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
                        g.renderOutline(getX(), getY(), getWidth(), getHeight(), outline);
                        int tc = !act ? 0xFF555565 : hov ? 0xFFFFFFFF : 0xFFBBBBCC;
                        g.drawCenteredString(
                                Minecraft.getInstance().font,
                                Component.literal(copyBindCaptionOnly(plain)),
                                getX() + getWidth() / 2,
                                getY() + (getHeight() - 8) / 2,
                                tc);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        bindBtn.setTooltip(
                Tooltip.create(
                        Component.translatable(
                                plain
                                        ? "chat-utilities.settings.copy_plain_bind.tooltip"
                                        : "chat-utilities.settings.copy_formatted_bind.tooltip")));
        return bindBtn;
    }

    private String copyBindCaptionOnly(boolean plain) {
        if (plain ? rebindingCopyPlain : rebindingCopyFormatted) {
            return Component.translatable("chat-utilities.settings.rebind_click_prompt").getString();
        }
        return describeCopyMouseBinding(
                plain
                        ? ChatUtilitiesClientOptions.getCopyPlainBinding()
                        : ChatUtilitiesClientOptions.getCopyFormattedBinding());
    }

    private static String describeCopyMouseBinding(ChatUtilitiesClientOptions.ClickMouseBinding b) {
        StringBuilder sb = new StringBuilder();
        if (b.requireControl) {
            sb.append("Ctrl+");
        }
        if (b.requireShift) {
            sb.append("Shift+");
        }
        if (b.requireAlt) {
            sb.append("Alt+");
        }
        sb.append(
                switch (b.mouseButton) {
                    case 1 -> "RMB";
                    case 2 -> "MMB";
                    default -> "LMB";
                });
        return sb.toString();
    }

    private int settingsSectionHeaderH() {
        return this.font.lineHeight + 10;
    }

    /** First row under the “Controls” header block (label drawn in {@link #renderSettingsPanelExtras}). */
    private int settingsFormBodyY() {
        return bodyY() + settingsSectionHeaderH() + 6;
    }

    private int settingsOtherTitleY() {
        return settingsFormBodyY() + 106 + SETTINGS_SECTION_GAP;
    }

    private int settingsOtherFormY() {
        return settingsOtherTitleY() + settingsSectionHeaderH() + 6;
    }

    private int settingsClickToCopyRowY() {
        return settingsOtherFormY();
    }

    private int settingsCopyFormattedStyleRowY() {
        return settingsClickToCopyRowY() + 28;
    }

    private int settingsSymbolSelectorRowY() {
        return settingsCopyFormattedStyleRowY() + 28;
    }

    private int settingsShadowRowY() {
        return settingsSymbolSelectorRowY() + 28;
    }

    private int settingsProfilesTitleY() {
        return settingsShadowRowY() + 28 + SETTINGS_SECTION_GAP;
    }

    private int settingsProfilesFormY() {
        return settingsProfilesTitleY() + settingsSectionHeaderH() + 6;
    }

    private void renderSettingsPanelExtras(GuiGraphics g) {
        if (activePanel != Panel.SETTINGS) {
            return;
        }
        int fx = contentLeft();
        int fr = contentRight();
        drawSettingsSectionHeader(g, fx, fr, bodyY(), "Controls");
        int yRow = settingsFormBodyY();
        g.drawString(this.font, "Open Chat Utilities", fx, yRow + 7, ChatUtilitiesScreenLayout.TEXT_LABEL, false);
        int yCopyPlain = settingsFormBodyY() + 28;
        g.drawString(
                this.font,
                Component.translatable("chat-utilities.settings.copy_plain_bind"),
                fx,
                yCopyPlain + 7,
                ChatUtilitiesScreenLayout.TEXT_LABEL,
                false);
        int yCopyFmt = settingsFormBodyY() + 56;
        g.drawString(
                this.font,
                Component.translatable("chat-utilities.settings.copy_formatted_bind"),
                fx,
                yCopyFmt + 7,
                ChatUtilitiesScreenLayout.TEXT_LABEL,
                false);
        int yOtherTitle = settingsOtherTitleY();
        drawSettingsSectionHeader(g, fx, fr, yOtherTitle, "Settings");
        int yClickCopy = settingsClickToCopyRowY();
        g.drawString(
                this.font,
                Component.translatable("chat-utilities.settings.click_to_copy"),
                fx,
                yClickCopy + 7,
                ChatUtilitiesScreenLayout.TEXT_LABEL,
                false);
        int yFmtStyle = settingsCopyFormattedStyleRowY();
        g.drawString(
                this.font,
                Component.translatable("chat-utilities.settings.copy_formatted_style"),
                fx,
                yFmtStyle + 7,
                ChatUtilitiesScreenLayout.TEXT_LABEL,
                false);
        int ySym = settingsSymbolSelectorRowY();
        g.drawString(
                this.font,
                Component.translatable("chat-utilities.settings.chat_symbol_selector"),
                fx,
                ySym + 7,
                ChatUtilitiesScreenLayout.TEXT_LABEL,
                false);
        int yShadow = settingsShadowRowY();
        g.drawString(
                this.font,
                Component.translatable("chat-utilities.settings.chat_text_shadow"),
                fx,
                yShadow + 7,
                ChatUtilitiesScreenLayout.TEXT_LABEL,
                false);
        int yProfTitle = settingsProfilesTitleY();
        drawSettingsSectionHeader(g, fx, fr, yProfTitle, "Profiles");
    }

    private void drawSettingsSectionHeader(GuiGraphics g, int fx, int fr, int y, String title) {
        int h = settingsSectionHeaderH();
        g.fill(fx, y, fr, y + h, C_WIN_GROUP_BG);
        g.renderOutline(fx, y, fr - fx, h, C_WIN_GROUP_EDGE);
        g.fill(fx, y, fx + 2, y + h, C_ACCENT);
        g.drawString(this.font, title, fx + 8, y + (h - 8) / 2, 0xFFE8EEF8, false);
    }

    private void showSettingsToast(String title, String detail) {
        if (this.minecraft == null) {
            return;
        }
        SystemToast.add(
                this.minecraft.getToastManager(),
                SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                Component.literal(title),
                Component.literal(detail));
    }

    private void runExportProfilesDialog() {
        if (this.minecraft == null) {
            return;
        }
        try {
            if (GraphicsEnvironment.isHeadless()) {
                showSettingsToast("Export", "File dialog unavailable in this environment");
                return;
            }
            FileDialog fd = new FileDialog((java.awt.Frame) null, "Export Profiles", FileDialog.SAVE);
            fd.setFile("chat-utilities-profiles.json");
            fd.setVisible(true);
            String f = fd.getFile();
            String d = fd.getDirectory();
            if (f == null || d == null) {
                return;
            }
            Path path = Path.of(d, f);
            Files.writeString(path, ChatUtilitiesManager.get().serializeProfilesToJson(), StandardCharsets.UTF_8);
            showSettingsToast("Profiles exported", path.getFileName().toString());
        } catch (IOException e) {
            showSettingsToast("Export failed", e.getMessage() != null ? e.getMessage() : "");
        }
    }

    private void runImportProfilesDialog() {
        if (this.minecraft == null) {
            return;
        }
        try {
            if (GraphicsEnvironment.isHeadless()) {
                showSettingsToast("Import", "File dialog unavailable in this environment");
                return;
            }
            FileDialog fd = new FileDialog((java.awt.Frame) null, "Import Profiles", FileDialog.LOAD);
            fd.setVisible(true);
            String f = fd.getFile();
            String d = fd.getDirectory();
            if (f == null || d == null) {
                return;
            }
            Path path = Path.of(d, f);
            String json = Files.readString(path, StandardCharsets.UTF_8);
            int added = ChatUtilitiesManager.get().importProfilesFromJson(json);
            if (added == 0) {
                showSettingsToast("Import", "No profiles were added");
            } else {
                showSettingsToast("Profiles imported", added + " profile(s) merged");
            }
            init();
        } catch (JsonParseException e) {
            showSettingsToast("Import failed", "Invalid JSON");
        } catch (IOException e) {
            showSettingsToast("Import failed", e.getMessage() != null ? e.getMessage() : "");
        }
    }

    /** Short label for the keybind button (name is shown beside it as static text). */
    private String keybindCaptionOnly() {
        KeyMapping km = ChatUtilitiesModClient.OPEN_MENU_KEY;
        if (rebindingOpenMenuKey) {
            return "Press a key…";
        }
        if (km.isUnbound()) {
            return "Not bound";
        }
        return km.getTranslatedKeyMessage().getString();
    }

    private void saveOptions() {
        if (this.minecraft != null) {
            this.minecraft.options.save();
        }
    }

    private static InputConstants.Key keyFromKeyEvent(KeyEvent event) {
        if (event.key() == InputConstants.UNKNOWN.getValue()) {
            return InputConstants.Type.SCANCODE.getOrCreate(event.scancode());
        }
        return InputConstants.Type.KEYSYM.getOrCreate(event.key());
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ServerProfile profile() {
        if (selectedProfileId == null) {
            return null;
        }
        return ChatUtilitiesManager.get().getProfile(selectedProfileId);
    }

    /** Profile tint for sidebar (footer Settings row); default blue if none. */
    private int selectedProfileAccentRgb() {
        ServerProfile p = profile();
        if (p == null || p.getServers().isEmpty() || this.minecraft == null) {
            return 0x3A9FE0;
        }
        return ProfileFaviconCache.getProfileAccentRgb(this.minecraft, p.getServers().getFirst());
    }

    /** Blend a dark base ARGB with a profile accent RGB (0xRRGGBB). */
    private static int mixProfileAccentArgb(int baseArgb, int accentRgb, float t) {
        int br = (baseArgb >> 16) & 0xFF;
        int bg = (baseArgb >> 8) & 0xFF;
        int bb = baseArgb & 0xFF;
        int ar = (accentRgb >> 16) & 0xFF;
        int ag = (accentRgb >> 8) & 0xFF;
        int ab = accentRgb & 0xFF;
        int r = (int) (br * (1 - t) + ar * t);
        int g = (int) (bg * (1 - t) + ag * t);
        int b = (int) (bb * (1 - t) + ab * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * Brighter tint of the favicon primary for sidebar tab bar + labels (easier to see on dark UI).
     */
    private static int lightenAccentForSidebar(int accentRgb) {
        int r = (accentRgb >> 16) & 0xFF;
        int g = (accentRgb >> 8) & 0xFF;
        int b = accentRgb & 0xFF;
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        hsb[1] = Math.min(1f, hsb[1] * 1.1f);
        hsb[2] = Math.min(1f, hsb[2] * 1.28f + 0.24f);
        int packed = Color.HSBtoRGB(hsb[0], hsb[1], hsb[2]);
        return ((packed >> 16) & 0xFF) << 16 | ((packed >> 8) & 0xFF) << 8 | (packed & 0xFF);
    }

    /** Active sub-tab left bar: lighter version of the logo primary. */
    private static int sidebarTabAccentBar(int accentRgb) {
        int light = lightenAccentForSidebar(accentRgb);
        return mixProfileAccentArgb(0xFF3D4450, light, 0.88f);
    }

    /** Sub-tab labels: light tints of the logo primary for readability. */
    private static int sidebarTabLabelArgb(int accentRgb, boolean active) {
        int light = lightenAccentForSidebar(accentRgb);
        int base = active ? 0xFFF6F8FC : 0xFFC8CAD4;
        return mixProfileAccentArgb(base, light, active ? 0.88f : 0.80f);
    }

    private void resetScrolls() {
        serverScroll = 0;
        winScroll = 0;
        ignScroll = 0;
        ruleScroll = 0;
        expandedWindowIds.clear();
        windowPatScroll.clear();
    }

    /** Returns the profile list sorted so the currently active server's profile comes first. */
    private List<ServerProfile> sortedProfiles() {
        return sortedProfilesList(ChatUtilitiesManager.get());
    }

    private String headerTitleText() {
        return switch (activePanel) {
            case EDIT_PROFILE -> "Edit Profile";
            case CHAT_WINDOWS -> "Chat Windows";
            case IGNORED_CHAT -> "Ignored Chat";
            case CHAT_SOUNDS -> "Chat Sounds";
            case SETTINGS -> "Settings";
            default -> "Server Profiles";
        };
    }

    private String headerDescriptionText() {
        return switch (activePanel) {
            case EDIT_PROFILE ->
                    "Define how this profile is identified and which servers it applies to, so the right rules "
                            + "and windows load when you connect.";
            case CHAT_WINDOWS ->
                    "Create separate chat surfaces on your HUD so you can follow different streams of "
                            + "messages without losing the main chat.";
            case IGNORED_CHAT ->
                    "Choose which messages are filtered out entirely so your chat stays focused on what matters.";
            case CHAT_SOUNDS ->
                    "Get audio feedback when messages match what you care about, separate from how they appear on screen.";
            case SETTINGS ->
                    "Adjust how this mod behaves on your client: shortcuts, convenience options, and backing up or "
                            + "sharing your profiles.";
            default ->
                    "Choose a server profile to configure how chat is filtered, routed, and sounded for that world.";
        };
    }

    // ── Render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        EditBox soundAnchor = activeSoundSuggestionField();
        if (!(activePanel == Panel.CHAT_SOUNDS && soundAnchor != null)) {
            sugFiltered = List.of();
        }
        super.render(graphics, mouseX, mouseY, partialTick); // renderBackground() + widgets
        renderSidebar(graphics, mouseX, mouseY);
        renderContentHeader(graphics);
        renderSettingsPanelExtras(graphics);
        soundAnchor = activeSoundSuggestionField();
        if (activePanel == Panel.CHAT_SOUNDS && soundAnchor != null) {
            renderSoundSuggestions(graphics, mouseX, mouseY, soundAnchor);
        }
        if (createWinDialogOpen && activePanel == Panel.CHAT_WINDOWS) {
            renderCreateWindowDialogOnTop(graphics, mouseX, mouseY, partialTick);
        }
        if (renameDialogOpen && activePanel == Panel.CHAT_WINDOWS) {
            renderRenameWindowDialogOnTop(graphics, mouseX, mouseY, partialTick);
        }
    }

    /**
     * Drawn last so the modal sits above sidebar, header, and list. Opaque dialog fill + second
     * widget pass so fields/buttons are not covered by earlier layers.
     */
    private void renderCreateWindowDialogOnTop(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int pl = panelLeft(), pr = panelRight(), pt = panelTop(), pb = panelBottom();
        int dx = createDlgX, dy = createDlgY, dR = dx + createDlgW, dB = dy + createDlgH;
        int dim = 0x78000000;
        g.fill(pl, pt, pr, Math.min(dy, pb), dim);
        g.fill(pl, Math.max(dB, pt), pr, pb, dim);
        g.fill(pl, dy, dx, dB, dim);
        g.fill(dR, dy, pr, dB, dim);

        g.fill(dx, dy, dR, dB, C_PANEL_BG & 0x00FFFFFF | 0xFF000000);
        g.renderOutline(dx, dy, createDlgW, createDlgH, 0xFF505068);
        g.drawString(this.font, "New Chat Window", dx + 12, dy + 12, 0xFFFFFFFF, false);

        if (dlgWinIdField != null) {
            dlgWinIdField.render(g, mouseX, mouseY, partialTick);
        }
        if (dlgWinPatField != null) {
            dlgWinPatField.render(g, mouseX, mouseY, partialTick);
        }
        if (dlgCreateButton != null) {
            dlgCreateButton.render(g, mouseX, mouseY, partialTick);
        }
        if (dlgCancelButton != null) {
            dlgCancelButton.render(g, mouseX, mouseY, partialTick);
        }
    }

    private void renderRenameWindowDialogOnTop(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int pl = panelLeft(), pr = panelRight(), pt = panelTop(), pb = panelBottom();
        int dx = renameDlgX, dy = renameDlgY, dR = dx + renameDlgW, dB = dy + renameDlgH;
        int dim = 0x78000000;
        g.fill(pl, pt, pr, Math.min(dy, pb), dim);
        g.fill(pl, Math.max(dB, pt), pr, pb, dim);
        g.fill(pl, dy, dx, dB, dim);
        g.fill(dR, dy, pr, dB, dim);

        g.fill(dx, dy, dR, dB, C_PANEL_BG & 0x00FFFFFF | 0xFF000000);
        g.renderOutline(dx, dy, renameDlgW, renameDlgH, 0xFF505068);
        g.drawString(this.font, "Rename Chat Window", dx + 12, dy + 12, 0xFFFFFFFF, false);

        if (dlgRenameIdField != null) {
            dlgRenameIdField.render(g, mouseX, mouseY, partialTick);
        }
        if (dlgRenameOkButton != null) {
            dlgRenameOkButton.render(g, mouseX, mouseY, partialTick);
        }
        if (dlgRenameCancelButton != null) {
            dlgRenameCancelButton.render(g, mouseX, mouseY, partialTick);
        }
    }

    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
        int sl = sidebarLeft(), sr = sidebarRight();
        int st = sidebarTop(),  sb = sidebarBottom();

        // Sidebar background (slightly darker than panel)
        g.fill(sl, st, sr, sb, C_SIDEBAR_BG);
        // Right separator line
        g.fill(sr, st, sr + 1, sb, C_SIDEBAR_SEP);

        // "Chat Utilities" header strip + “by Rainnny” (link)
        g.fill(sl, st, sr, st + SIDEBAR_TITLE_H, 0xFF040408);
        g.fill(sl, st + SIDEBAR_TITLE_H, sr, st + SIDEBAR_TITLE_H + 1, C_SIDEBAR_SEP);
        int lh = this.font.lineHeight;
        int gapTitle = 1;
        int blockH = lh + gapTitle + lh;
        int blockTop = st + (SIDEBAR_TITLE_H - blockH) / 2;
        int line1Y = blockTop;
        String titleMain = "Chat Utilities ";
        String titleVer = modVersionLabel();
        int titleW = this.font.width(titleMain) + this.font.width(titleVer);
        int titleX = sl + (SIDEBAR_W - titleW) / 2;
        g.drawString(this.font, titleMain, titleX, line1Y, 0xFFE0E0F0, false);
        g.drawString(this.font, titleVer, titleX + this.font.width(titleMain), line1Y, 0xFF9090A8, false);
        String byPrefix = "by ";
        String byAuthor = "Rainnny";
        int line2Y = blockTop + lh + gapTitle;
        int line2W = this.font.width(byPrefix) + this.font.width(byAuthor);
        int line2X = sl + (SIDEBAR_W - line2W) / 2;
        g.drawString(this.font, byPrefix, line2X, line2Y, 0xFF9090A8, false);
        int nameX = line2X + this.font.width(byPrefix);
        int nameW = this.font.width(byAuthor);
        boolean hovAuthor = mouseX >= nameX && mouseX < nameX + nameW
                && mouseY >= line2Y && mouseY < line2Y + this.font.lineHeight
                && mouseX >= sl && mouseX < sr
                && mouseY >= st && mouseY < st + SIDEBAR_TITLE_H;
        int authorColor = hovAuthor ? 0xFF7EC8FF : C_ACCENT;
        g.drawString(this.font, byAuthor, nameX, line2Y, authorColor, false);
        if (hovAuthor) {
            g.fill(nameX, line2Y + this.font.lineHeight, nameX + nameW, line2Y + this.font.lineHeight + 1, authorColor);
        }
        sidebarAuthorLinkL = nameX;
        sidebarAuthorLinkR = nameX + nameW;
        sidebarAuthorLinkT = line2Y;
        sidebarAuthorLinkB = line2Y + this.font.lineHeight;

        // Footer: "+ New Profile", then "Settings"
        int footerTop = sb - 2 * SIDEBAR_FOOTER_ROW_H;
        g.fill(sl, footerTop - 1, sr, footerTop, C_SIDEBAR_SEP);
        int row0y = footerTop;
        g.fill(sl, row0y, sr, row0y + SIDEBAR_FOOTER_ROW_H, 0xFF040408);
        boolean hovNew = mouseX >= sl && mouseX < sr
                && mouseY >= row0y && mouseY < row0y + SIDEBAR_FOOTER_ROW_H;
        if (hovNew) {
            g.fill(sl, row0y, sr, row0y + SIDEBAR_FOOTER_ROW_H, C_HOVER);
        }
        g.drawString(this.font, "+ New Profile",
                sl + SUB_INDENT, row0y + (SIDEBAR_FOOTER_ROW_H - 8) / 2, C_NEW_PROFILE, false);
        g.fill(sl, row0y + SIDEBAR_FOOTER_ROW_H - 1, sr, row0y + SIDEBAR_FOOTER_ROW_H, C_SIDEBAR_SEP);
        int row1y = row0y + SIDEBAR_FOOTER_ROW_H;
        g.fill(sl, row1y, sr, sb, 0xFF040408);
        boolean settingsActive = activePanel == Panel.SETTINGS;
        int footAccentRgb = selectedProfileAccentRgb();
        boolean hovSet = mouseX >= sl && mouseX < sr && mouseY >= row1y && mouseY < sb;
        if (settingsActive) {
            int subActiveBg = mixProfileAccentArgb(C_ACTIVE_BG, footAccentRgb, 0.38f);
            g.fill(sl, row1y, sr, sb, subActiveBg);
            g.fill(sl, row1y, sl + 2, sb, sidebarTabAccentBar(footAccentRgb));
        } else if (hovSet) {
            g.fill(sl, row1y, sr, sb, C_HOVER);
        }
        int settingsTextColor =
                settingsActive
                        ? sidebarTabLabelArgb(footAccentRgb, true)
                        : sidebarTabLabelArgb(footAccentRgb, false);
        g.drawString(this.font, "\u2699 Settings",
                sl + SUB_INDENT, row1y + (SIDEBAR_FOOTER_ROW_H - 8) / 2, settingsTextColor, false);

        // Scrollable profile list
        sidebarEntries.clear();
        List<ServerProfile> profiles = sortedProfiles();
        int listTop    = st + SIDEBAR_TITLE_H + 1;
        int listBottom = footerTop - 1;
        int listAreaH  = listBottom - listTop;
        int totalH     = computeSidebarTotalH(profiles);
        int maxScroll  = Math.max(0, totalH - listAreaH);
        sidebarScroll  = Mth.clamp(sidebarScroll, 0, maxScroll);

        g.enableScissor(sl, listTop, sr, listBottom);
        try {
            int iy = listTop - sidebarScroll;

            if (profiles.isEmpty()) {
                int ey = listTop + listAreaH / 2 - 4;
                g.drawCenteredString(this.font, "No profiles yet.", sl + SIDEBAR_W / 2, ey, 0xFF505060);
            }

            for (ServerProfile p : profiles) {
                boolean expanded = expandedProfileIds.contains(p.getId());
                String firstHost = p.getServers().isEmpty() ? null : p.getServers().getFirst();
                int accentRgb = ProfileFaviconCache.getProfileAccentRgb(this.minecraft, firstHost);
                int profileSelBg = mixProfileAccentArgb(C_PROFILE_SEL, accentRgb, 0.42f);

                // ── Profile header ──────────────────────────────────────────
                int rowY = iy;
                if (rowY + PROFILE_ROW_H > listTop && rowY < listBottom) {
                    boolean hov = mouseX >= sl && mouseX < sr
                            && mouseY >= rowY && mouseY < rowY + PROFILE_ROW_H;
                    if (expanded) g.fill(sl, rowY, sr, rowY + PROFILE_ROW_H, profileSelBg);
                    if (hov)      g.fill(sl, rowY, sr, rowY + PROFILE_ROW_H, C_HOVER);
                    // Bottom hairline
                    g.fill(sl, rowY + PROFILE_ROW_H - 1, sr, rowY + PROFILE_ROW_H, 0x18FFFFFF);

                    // Favicon / item icon (16x16)
                    int iconX = sl + 6;
                    int iconY = rowY + (PROFILE_ROW_H - 16) / 2;
                    Identifier favicon = ProfileFaviconCache.getIcon(this.minecraft, firstHost);
                    if (favicon != null) {
                        g.blit(RenderPipelines.GUI_TEXTURED, favicon,
                                iconX, iconY, 0f, 0f, 16, 16, 16, 16);
                    } else {
                        g.renderItem(new ItemStack(Items.COMPASS), iconX, iconY);
                    }

                    // Profile name (truncated)
                    String name = p.getDisplayName();
                    int maxNW = SIDEBAR_W - 30 - 14;
                    boolean trunc = false;
                    while (name.length() > 1 && this.font.width(name + "…") > maxNW) {
                        name = name.substring(0, name.length() - 1);
                        trunc = true;
                    }
                    if (trunc) name += "…";
                    g.drawString(this.font, name,
                            iconX + 20, rowY + (PROFILE_ROW_H - 8) / 2, C_PROFILE_NAME, false);

                    // Expand arrow
                    g.drawString(this.font, expanded ? "▾" : "▸",
                            sr - 12, rowY + (PROFILE_ROW_H - 8) / 2, 0xFF606070, false);

                    sidebarEntries.add(new SidebarEntry(rowY, PROFILE_ROW_H, true, p.getId(), null));
                }
                iy += PROFILE_ROW_H;

                // ── Sub-items ───────────────────────────────────────────────
                if (expanded) {
                    Panel[]  panels = {Panel.CHAT_WINDOWS, Panel.IGNORED_CHAT, Panel.CHAT_SOUNDS, Panel.EDIT_PROFILE};
                    String[] labels = {"Chat Windows",     "Ignored Chat",     "Chat Sounds",     "Edit Profile"};
                    for (int si = 0; si < panels.length; si++) {
                        Panel  sp = panels[si];
                        String sl2 = labels[si];
                        int    subY = iy;
                        if (subY + SUB_ROW_H > listTop && subY < listBottom) {
                            boolean active =
                                    sp == activePanel && p.getId().equals(selectedProfileId);
                            boolean subHov  = mouseX >= sl && mouseX < sr
                                    && mouseY >= subY && mouseY < subY + SUB_ROW_H;
                            int subActiveBg = mixProfileAccentArgb(C_ACTIVE_BG, accentRgb, 0.38f);
                            int subAccentBar = sidebarTabAccentBar(accentRgb);
                            int subTextActive = sidebarTabLabelArgb(accentRgb, true);
                            int subTextInactive = sidebarTabLabelArgb(accentRgb, false);
                            if (active)            g.fill(sl, subY, sr, subY + SUB_ROW_H, subActiveBg);
                            if (subHov && !active) g.fill(sl, subY, sr, subY + SUB_ROW_H, C_HOVER);
                            // Left accent bar
                            if (active) g.fill(sl, subY, sl + 2, subY + SUB_ROW_H, subAccentBar);
                            g.drawString(this.font, sl2,
                                    sl + SUB_INDENT, subY + (SUB_ROW_H - 8) / 2,
                                    active ? subTextActive : subTextInactive, false);
                            sidebarEntries.add(new SidebarEntry(subY, SUB_ROW_H, false, p.getId(), sp));
                        }
                        iy += SUB_ROW_H;
                    }
                    // Hairline separator after expanded group
                    if (iy > listTop && iy < listBottom) {
                        g.fill(sl, iy, sr, iy + 1, 0x20FFFFFF);
                    }
                }
            }
        } finally {
            g.disableScissor();
        }
    }

    private int computeSidebarTotalH(List<ServerProfile> profiles) {
        int h = 0;
        for (ServerProfile p : profiles) {
            h += PROFILE_ROW_H;
            if (expandedProfileIds.contains(p.getId())) h += SUB_ROW_H * 4;
        }
        return h;
    }

    private void renderContentHeader(GuiGraphics g) {
        int cx = contentCX();
        int descW = Math.min(contentW() - CONTENT_PAD_X, 420);

        int titleY = panelTop() + TITLE_Y_OFF;
        g.drawCenteredString(this.font, headerTitleText(), cx, titleY, 0xFFFFFFFF);

        int descStart = headerDescStartY();
        ChatUtilitiesScreenLayout.drawCenteredWrapped(
                this.font, g, Component.literal(headerDescriptionText()),
                cx, descStart, descW, ChatUtilitiesScreenLayout.TEXT_GRAY, HEADER_DESC_LINE_SPACING);

        int sepTop = headerSepTopY();
        g.fill(sidebarRight() + CONTENT_PAD_X, sepTop,
                panelRight() - CONTENT_PAD_X, sepTop + 1, 0x30FFFFFF);
    }

    /** Which sound {@link EditBox} is focused for registry autocomplete (add row or an existing rule). */
    private EditBox activeSoundSuggestionField() {
        if (activePanel != Panel.CHAT_SOUNDS) {
            return null;
        }
        if (soundField != null && soundField.isFocused()) {
            return soundField;
        }
        for (PendingMessageSoundEdit pr : pendingMessageSoundEdits) {
            if (pr.soundBox().isFocused()) {
                return pr.soundBox();
            }
        }
        return null;
    }

    private void renderSoundSuggestions(GuiGraphics g, int mouseX, int mouseY, EditBox anchor) {
        if (anchor == null) {
            sugFiltered = List.of();
            return;
        }
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

        sugLeft  = anchor.getX();
        sugTop   = anchor.getY() + anchor.getHeight() + 1;
        sugWidth = Math.max(200, anchor.getWidth());
        int sugBottom = sugTop + sugVisibleRows * SUGGEST_ROW_H;
        g.fill(sugLeft - 1, sugTop - 1, sugLeft + sugWidth + 1, sugBottom + 1, 0xFF000000);
        g.fill(sugLeft,     sugTop,     sugLeft + sugWidth,     sugBottom,     C_SIDEBAR_BG);
        for (int i = 0; i < sugVisibleRows; i++) {
            String line = sugFiltered.get(sugScroll + i);
            if (line.length() > 48) line = line.substring(0, 45) + "...";
            int ry = sugTop + i * SUGGEST_ROW_H;
            boolean hov = mouseX >= sugLeft && mouseX < sugLeft + sugWidth
                    && mouseY >= ry && mouseY < ry + SUGGEST_ROW_H;
            if (hov) g.fill(sugLeft, ry, sugLeft + sugWidth, ry + SUGGEST_ROW_H, 0x336688FF);
            g.drawString(this.font, line, sugLeft + 3, ry + 2, 0xFFFFFFFF, false);
        }
    }

    // ── Mouse input ───────────────────────────────────────────────────────────

    private boolean chatWindowsModalBlocksContentClicks() {
        return (createWinDialogOpen || renameDialogOpen) && activePanel == Panel.CHAT_WINDOWS;
    }

    /** Bottom control strip on Chat Windows (Done at right; row includes + Create / Adjust Layout). */
    private boolean isChatWindowsFooterBar(double mx, double my) {
        if (activePanel != Panel.CHAT_WINDOWS) {
            return false;
        }
        if (my < footerY() || my >= footerY() + 24) {
            return false;
        }
        return mx >= contentLeft() && mx <= contentRight();
    }

    /**
     * When a modal bypasses {@link Screen#mouseClicked}, widgets still need the same focus/drag
     * bookkeeping that {@link net.minecraft.client.gui.components.events.ContainerEventHandler}
     * normally performs after {@link GuiEventListener#mouseClicked}.
     */
    private boolean dispatchModalWidgetClick(GuiEventListener widget, MouseButtonEvent event, boolean doubleClick) {
        if (widget == null || !widget.mouseClicked(event, doubleClick)) {
            return false;
        }
        if (widget.shouldTakeFocusAfterInteraction()) {
            setFocused(widget);
        }
        if (event.button() == 0) {
            setDragging(true);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (renameDialogOpen && activePanel == Panel.CHAT_WINDOWS && event.key() == InputConstants.KEY_ESCAPE) {
            closeRenameWindowDialog();
            return true;
        }
        if (createWinDialogOpen && activePanel == Panel.CHAT_WINDOWS && event.key() == InputConstants.KEY_ESCAPE) {
            closeCreateWindowDialog();
            return true;
        }
        if (activePanel == Panel.SETTINGS && (rebindingCopyPlain || rebindingCopyFormatted)) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                rebindingCopyPlain = false;
                rebindingCopyFormatted = false;
                init();
                return true;
            }
            return true;
        }
        if (activePanel == Panel.SETTINGS && rebindingOpenMenuKey) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                rebindingOpenMenuKey = false;
                init();
                return true;
            }
            InputConstants.Key newKey = keyFromKeyEvent(event);
            if (!newKey.equals(InputConstants.UNKNOWN)) {
                ChatUtilitiesModClient.OPEN_MENU_KEY.setKey(newKey);
                KeyMapping.resetMapping();
                saveOptions();
                rebindingOpenMenuKey = false;
                init();
                return true;
            }
            return true;
        }
        int key = event.key();
        boolean enter = key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER;
        if (enter) {
            ChatUtilitiesManager mgr = ChatUtilitiesManager.get();
            ServerProfile p = profile();

            if (renameDialogOpen
                    && activePanel == Panel.CHAT_WINDOWS
                    && p != null
                    && dlgRenameIdField != null
                    && dlgRenameIdField.isFocused()) {
                submitRenameWindowDialog(mgr, p);
                return true;
            }
            if (createWinDialogOpen
                    && activePanel == Panel.CHAT_WINDOWS
                    && p != null
                    && dlgWinIdField != null
                    && (dlgWinIdField.isFocused()
                            || dlgWinPatField != null && dlgWinPatField.isFocused())) {
                submitNewChatWindowDialog(mgr, p);
                return true;
            }

            if (activePanel == Panel.CHAT_WINDOWS && p != null) {
                for (PendingNewPattern pn : pendingNewPatterns) {
                    if (pn.box.isFocused()) {
                        try {
                            mgr.addPattern(pn.profile(), pn.windowId(), pn.box.getValue());
                            pn.box.setValue("");
                        } catch (PatternSyntaxException ignored) {
                        }
                        init();
                        return true;
                    }
                }
                for (PendingPatternEdit pe : pendingPatternEdits) {
                    if (pe.box.isFocused()) {
                        try {
                            mgr.setPatternAt(pe.profile(), pe.windowId(), pe.userPosition(), pe.box.getValue());
                            init();
                        } catch (PatternSyntaxException ignored) {
                        }
                        return true;
                    }
                }
            }
            if (activePanel == Panel.IGNORED_CHAT && p != null) {
                for (PendingIgnoreEdit pie : pendingIgnoreEdits) {
                    if (pie.box().isFocused()) {
                        try {
                            mgr.setIgnorePatternAt(pie.profile(), pie.listIndex(), pie.box().getValue());
                            init();
                        } catch (PatternSyntaxException ignored) {
                        }
                        return true;
                    }
                }
            }
            if (activePanel == Panel.IGNORED_CHAT && p != null
                    && newIgnoreField != null && newIgnoreField.isFocused()) {
                try {
                    mgr.addIgnorePattern(p, newIgnoreField.getValue());
                    newIgnoreField.setValue("");
                } catch (PatternSyntaxException ignored) {
                }
                init();
                return true;
            }
            if (activePanel == Panel.CHAT_SOUNDS && p != null) {
                for (PendingMessageSoundEdit pr : pendingMessageSoundEdits) {
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
            }
            if (activePanel == Panel.CHAT_SOUNDS && p != null
                    && patternField != null && soundField != null
                    && (patternField.isFocused() || soundField.isFocused())
                    && !patternField.getValue().strip().isEmpty()
                    && !soundField.getValue().strip().isEmpty()) {
                try {
                    mgr.addMessageSound(p, patternField.getValue(), soundField.getValue());
                    patternField.setValue("");
                } catch (IllegalArgumentException ignored) {
                }
                init();
                return true;
            }
            if (activePanel == Panel.EDIT_PROFILE && p != null
                    && newServerField != null && newServerField.isFocused()) {
                applyName(mgr, p);
                String h = newServerField.getValue().strip().toLowerCase(Locale.ROOT);
                if (!h.isEmpty() && !p.getServers().contains(h)) {
                    p.getServers().add(h);
                    mgr.markProfileServersDirty();
                    newServerField.setValue("");
                }
                init();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if ((rebindingCopyPlain || rebindingCopyFormatted) && activePanel == Panel.SETTINGS) {
            Minecraft mc = Minecraft.getInstance();
            long win = mc.getWindow().handle();
            boolean control =
                    GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                            || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
            boolean shift =
                    GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                            || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
            boolean alt =
                    GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                            || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
            ChatUtilitiesClientOptions.ClickMouseBinding nb =
                    new ChatUtilitiesClientOptions.ClickMouseBinding(
                            event.button(), control, shift, alt);
            if (rebindingCopyPlain) {
                ChatUtilitiesClientOptions.setCopyPlainBinding(nb);
            } else {
                ChatUtilitiesClientOptions.setCopyFormattedBinding(nb);
            }
            rebindingCopyPlain = false;
            rebindingCopyFormatted = false;
            init();
            return true;
        }
        if (rebindingOpenMenuKey && activePanel == Panel.SETTINGS) {
            boolean handled = super.mouseClicked(event, doubleClick);
            if (handled) {
                rebindingOpenMenuKey = false;
                return true;
            }
            ChatUtilitiesModClient.OPEN_MENU_KEY.setKey(
                    InputConstants.Type.MOUSE.getOrCreate(event.button()));
            KeyMapping.resetMapping();
            saveOptions();
            rebindingOpenMenuKey = false;
            init();
            return true;
        }
        if (chatWindowsModalBlocksContentClicks()) {
            double mx = event.x();
            double my = event.y();
            boolean inSidebar = mx >= sidebarLeft() && mx < sidebarRight();
            if (!inSidebar) {
                if (isChatWindowsFooterBar(mx, my)) {
                    return super.mouseClicked(event, doubleClick);
                }
                if (renameDialogOpen) {
                    if (dispatchModalWidgetClick(dlgRenameIdField, event, doubleClick)) {
                        return true;
                    }
                    if (dispatchModalWidgetClick(dlgRenameOkButton, event, doubleClick)) {
                        return true;
                    }
                    if (dispatchModalWidgetClick(dlgRenameCancelButton, event, doubleClick)) {
                        return true;
                    }
                } else {
                    if (dispatchModalWidgetClick(dlgWinIdField, event, doubleClick)) {
                        return true;
                    }
                    if (dispatchModalWidgetClick(dlgWinPatField, event, doubleClick)) {
                        return true;
                    }
                    if (dispatchModalWidgetClick(dlgCreateButton, event, doubleClick)) {
                        return true;
                    }
                    if (dispatchModalWidgetClick(dlgCancelButton, event, doubleClick)) {
                        return true;
                    }
                }
                if (event.button() == 0) {
                    return true;
                }
            }
        }

        if (event.button() == 0) {
            double mx = event.x();
            double my = event.y();

            if (activePanel == Panel.CHAT_SOUNDS) {
                EditBox soundAnchor = activeSoundSuggestionField();
                if (soundAnchor != null) {
                    boolean inAnchor =
                            mx >= soundAnchor.getX()
                                    && mx < soundAnchor.getX() + soundAnchor.getWidth()
                                    && my >= soundAnchor.getY()
                                    && my < soundAnchor.getY() + soundAnchor.getHeight();
                    boolean inSug =
                            !sugFiltered.isEmpty()
                                    && mx >= sugLeft
                                    && mx < sugLeft + sugWidth
                                    && my >= sugTop
                                    && my < sugTop + sugVisibleRows * SUGGEST_ROW_H;
                    if (!inAnchor && !inSug) {
                        soundAnchor.setFocused(false);
                        sugFiltered = List.of();
                        sugLastQuery = null;
                    }
                }
            }

            // Sound suggestion click (add form or rule row sound field)
            EditBox soundSugTarget = activeSoundSuggestionField();
            if (activePanel == Panel.CHAT_SOUNDS && soundSugTarget != null && !sugFiltered.isEmpty()) {
                if (mx >= sugLeft && mx < sugLeft + sugWidth
                        && my >= sugTop && my < sugTop + sugVisibleRows * SUGGEST_ROW_H) {
                    int row = (int) ((my - sugTop) / SUGGEST_ROW_H);
                    int idx = sugScroll + row;
                    if (row >= 0 && row < sugVisibleRows && idx >= 0 && idx < sugFiltered.size()) {
                        soundSugTarget.setValue(sugFiltered.get(idx));
                        soundSugTarget.moveCursorToEnd(false);
                        return true;
                    }
                }
            }

            // Sidebar click (including its header/footer strips)
            if (mx >= sidebarLeft() && mx < sidebarRight()) {
                int st = sidebarTop();
                if (my >= st && my < st + SIDEBAR_TITLE_H
                        && mx >= sidebarAuthorLinkL && mx < sidebarAuthorLinkR
                        && my >= sidebarAuthorLinkT && my < sidebarAuthorLinkB) {
                    playUiClick();
                    try {
                        if (Desktop.isDesktopSupported()) {
                            Desktop d = Desktop.getDesktop();
                            if (d.isSupported(Desktop.Action.BROWSE)) {
                                d.browse(URI.create(SIDEBAR_AUTHOR_LINK));
                            }
                        }
                    } catch (Exception ignored) {
                    }
                    return true;
                }
                int footerTop = sidebarBottom() - 2 * SIDEBAR_FOOTER_ROW_H;
                if (my >= footerTop && my < footerTop + SIDEBAR_FOOTER_ROW_H) {
                    playUiClick();
                    ServerProfile np = ChatUtilitiesManager.get()
                            .createProfileForCurrentServer("New Profile");
                    expandedProfileIds.add(np.getId());
                    selectedProfileId = np.getId();
                    activePanel = Panel.EDIT_PROFILE;
                    serverScroll = 0;
                    init();
                    return true;
                }
                if (my >= footerTop + SIDEBAR_FOOTER_ROW_H && my < sidebarBottom()) {
                    playUiClick();
                    activePanel = Panel.SETTINGS;
                    init();
                    return true;
                }
                // Profile headers and sub-items
                for (SidebarEntry entry : sidebarEntries) {
                    if (my >= entry.y() && my < entry.y() + entry.h()) {
                        playUiClick();
                        if (entry.isHeader()) {
                            String pid = entry.profileId();
                            if (expandedProfileIds.contains(pid)) {
                                expandedProfileIds.remove(pid);
                                if (selectedProfileId != null && selectedProfileId.equals(pid)) {
                                    selectedProfileId = null;
                                    activePanel = Panel.NONE;
                                }
                            } else {
                                expandedProfileIds.add(pid);
                            }
                        } else {
                            expandedProfileIds.add(entry.profileId());
                            selectedProfileId = entry.profileId();
                            activePanel = entry.panel();
                            resetScrolls();
                        }
                        init();
                        return true;
                    }
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (chatWindowsModalBlocksContentClicks() && mouseX >= sidebarRight()) {
            return true;
        }
        if (activePanel == Panel.CHAT_SOUNDS && activeSoundSuggestionField() != null
                && !sugFiltered.isEmpty()) {
            int maxScroll = Math.max(0, sugFiltered.size() - SUGGEST_VISIBLE_ROWS);
            if (maxScroll > 0) {
                int visH = sugVisibleRows * SUGGEST_ROW_H;
                if (mouseX >= sugLeft && mouseX < sugLeft + sugWidth
                        && mouseY >= sugTop && mouseY < sugTop + visH) {
                    int step = verticalAmount > 0 ? -1 : 1;
                    sugScroll = Mth.clamp(sugScroll + step, 0, maxScroll);
                    return true;
                }
            }
        }
        if (activePanel == Panel.CHAT_WINDOWS && mouseX >= contentLeft() && mouseX <= contentRight()) {
            int listEnd = footerY() - WIN_LIST_FOOTER_GAP;
            if (mouseY >= bodyY() && mouseY < listEnd) {
                ServerProfile p = profile();
                if (p != null) {
                    List<String> winIds = validWindowIds(p);
                    int maxWin = maxChatWindowListScroll(p, winIds, listEnd);
                    if (maxWin > 0) {
                        int step = verticalAmount > 0 ? -1 : 1;
                        int ns = Mth.clamp(winScroll + step, 0, maxWin);
                        if (ns != winScroll) {
                            winScroll = ns;
                            init();
                            return true;
                        }
                    }
                }
            }
        }
        if (mouseX >= sidebarLeft() && mouseX < sidebarRight()) {
            List<ServerProfile> profiles = sortedProfiles();
            int titleH = 30, footerH = 26;
            int listAreaH = panelH() - titleH - 1 - footerH - 2;
            int maxScroll = Math.max(0, computeSidebarTotalH(profiles) - listAreaH);
            int delta = verticalAmount > 0 ? -PROFILE_ROW_H : PROFILE_ROW_H;
            sidebarScroll = Mth.clamp(sidebarScroll + delta, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void removed() {
        super.removed();
        ChatUtilitiesClientOptions.setLastMenuState(selectedProfileId, activePanel.name());
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
