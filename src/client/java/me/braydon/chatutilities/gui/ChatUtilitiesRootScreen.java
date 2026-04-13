package me.braydon.chatutilities.gui;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import me.braydon.chatutilities.ChatUtilitiesModClient;
import me.braydon.chatutilities.chat.*;
import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import me.braydon.chatutilities.client.ModAccentAnimator;
import me.braydon.chatutilities.client.ModUpdateChecker;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.regex.PatternSyntaxException;

/**
 * Main UI screen. Sodium-style: a centered floating panel with a left sidebar (profiles + sections)
 * and an inline content area on the right. All panels are shown without navigating away.
 */
public class ChatUtilitiesRootScreen extends Screen implements ProfileWorkflowScreen {

    // ── Panel geometry ─────────────────────────────────────────────────────────
    /** Minimum horizontal inset; {@link #marginX()} also scales with screen width. */
    private static final int MARGIN_X_MIN = 56;
    /** Minimum vertical inset; {@link #marginY()} also scales with screen height. */
    private static final int MARGIN_Y_MIN = 40;
    /** Corner radius for the main panel fill + frame (clamped per side in {@link RoundedPanelRenderer}). */
    private static final int PANEL_CORNER_RADIUS = 8;
    /** Outline thickness drawn <strong>outside</strong> the panel bounds ({@link RoundedPanelRenderer#fillRoundedRectOutsideBorder}). */
    private static final int PANEL_BORDER_PX = 2;
    /** Space between scrollable content and the {@link ThinScrollbar} track. */
    private static final int SCROLLBAR_CONTENT_GAP = 10;
    /** Default folder suggested in the native export/import file dialogs. */
    private static final Path PROFILES_JSON_DEFAULT_DIR =
            FabricLoader.getInstance().getConfigDir().resolve("chat-utilities");
    /** Width of the left sidebar within the panel. */
    private static final int SIDEBAR_W = 152;
    /** Height of the top “Chat Utilities” strip in the sidebar. */
    private static final int SIDEBAR_TITLE_H = 30;
    private static final String SIDEBAR_AUTHOR_LINK = "https://github.com/Rainnny7";
    /** Footer rows: New Profile, Settings. */
    private static final int SIDEBAR_FOOTER_ROW_H = 26;
    /**
     * Left inset for footer labels — tighter than {@link #SUB_INDENT} (profile sub-rows stay indented under
     * headers).
     */
    private static final int SIDEBAR_FOOTER_TEXT_INSET = 6;

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
    /** Per-row reset (↺) at the right edge; main control sits to its left inside a fixed 160px block. */
    private static final int SETTINGS_ROW_RESET_W = 30;
    private static final int SETTINGS_ROW_RESET_GAP = 4;
    private static final int SETTINGS_ROW_CONTROLS_TOTAL_W = 160;
    /** Compact pill switch (no row chrome); drawn right-aligned inside the settings control cell. */
    private static final int SETTINGS_BOOLEAN_SWITCH_W = 44;
    private static final int SETTINGS_BOOLEAN_SWITCH_H = 16;

    /** Base scale for {@link #settingsScrollStepPixels(double)} (wheel delta is typically ±1). */
    private static final int SETTINGS_SCROLL_STEP = 28;

    private static int settingsScrollStepPixels(double verticalAmount) {
        if (verticalAmount == 0.0) {
            return 0;
        }
        int sign = verticalAmount > 0 ? -1 : 1;
        int mag = (int) Math.round(Math.abs(verticalAmount) * SETTINGS_SCROLL_STEP * 1.35);
        return sign * Mth.clamp(mag, 12, 80);
    }
    /** Cap for Edit Profile + Chat Sounds: every input/button in those panels shares this width. */
    private static final int PROFILE_SOUNDS_FORM_MAX_W = 280;
    /** Chat Actions panel width: capped so the section does not span the full content area; grows when the Play sound row needs room. */
    private static final int CHAT_ACTIONS_FORM_CAP = 420;
    private static final int COMMAND_ALIAS_PAGE = 12;
    /** Add-row pattern field width (fixed so it does not resize when switching action type). */
    private static final int CHAT_ACTION_ADD_PAT_W = 168;
    /** Add-row “Action: …” toggle width (fits localized “Action: Color Highlight”). */
    private static final int CHAT_ACTION_ACTION_BTN_W = 148;
    private static final int CHAT_ACTION_ADD_BTN_W = 80;
    private static final int CHAT_ACTION_SOUND_FIELD_W = 100;
    private static final int CHAT_ACTION_TEST_BTN_W = 40;
    /** “Pick color” for color-highlight rows (add row + list rows). */
    private static final int CHAT_ACTION_HIGHLIGHT_PICK_W = 100;
    /** Slightly shorter than default {@code 20} for pattern / sound fields on this panel. */
    private static final int CHAT_ACTION_FIELD_H = 18;

    /** Background / outline for expanded chat window group (header + patterns). */
    private static final int C_WIN_GROUP_BG   = 0x480C1018;
    private static final int C_WIN_GROUP_EDGE = 0xFF2C2C3A;

    /** Eye alone (U+1F441 only — no VS16) to avoid stray combining marks in some fonts. */
    private static final String CHAT_WIN_EMOJI_VISIBLE = "\uD83D\uDC41";
    /** See-no-evil when the window is hidden. */
    private static final String CHAT_WIN_EMOJI_HIDDEN = "\uD83D\uDE48";
    /** Pencil (U+270F), left of “Rename” like ✕ on Remove and the eye on Hide/Show. */
    private static final String CHAT_WIN_SYMBOL_RENAME = "\u270F";
    /**
     * Expand/collapse affordance (sidebar profile rows, chat window headers). Filled triangles read larger than
     * ▸/▾ in the default font.
     */
    private static final String UI_BRANCH_COLLAPSED = "\u25B6";

    private static final String UI_BRANCH_EXPANDED = "\u25BC";
    /** Unicode code point U+00A7 (section sign); must not be drawn via {@code drawString(Font, String)} — see caption helper. */
    private static final int SECTION_SIGN_CODEPOINT = 0xA7;
    private static final int FOOTER_INSET = 26; // from panelBottom

    // ── Sidebar colors ─────────────────────────────────────────────────────────
    private static final int C_PANEL_BG       = 0xF0101012;
    /** Same family as {@link #C_PANEL_BG} RGB (0x101012), nudged lighter so the 2px ring is barely perceptible. */
    private static final int C_PANEL_BORDER   = 0xFF19191C;
    private static final int C_SIDEBAR_BG     = 0xFF080810;
    private static final int C_SIDEBAR_SEP    = 0xFF1E1E28;
    private static final int C_PROFILE_SEL    = 0xFF12203A;
    private static final int C_ACTIVE_BG      = 0xFF0E1C36;
    private static final int C_HOVER          = 0x18FFFFFF;
    private static final int C_PROFILE_NAME   = 0xFFEEEEEE;
    private static final int C_PROFILE_DETAIL = 0xFF888898;
    private static final int C_NEW_PROFILE    = 0xFF6EBF6E;
    /** Brighter green for hover on {@link #flatButtonPositive} (pairs with {@link #C_NEW_PROFILE}). */
    private static final int C_POS_TEXT_H     = 0xFF92E592;
    /** Destructive actions (delete / remove) — reddish accent like {@link #C_NEW_PROFILE} strength. */
    private static final int C_DANGER_TEXT    = 0xFFE07878;

    private int modAccentArgb() {
        return ModAccentAnimator.currentArgb();
    }

    /** Global sidebar accent RGB (no alpha); Settings tab uses this when not using a profile favicon tint. */
    private int modGlobalAccentRgb() {
        return modAccentArgb() & 0xFFFFFF;
    }
    private static final int C_DANGER_TEXT_H  = 0xFFFFA0A0;

    // ── Panels ─────────────────────────────────────────────────────────────────
    public enum Panel { NONE, EDIT_PROFILE, CHAT_WINDOWS, CHAT_ACTIONS, COMMAND_ALIASES, SETTINGS }

    // ── Sidebar hit-test entries (rebuilt each render cycle) ───────────────────
    private record SidebarEntry(int y, int h, boolean isHeader, String profileId, Panel panel) {}
    private final List<SidebarEntry> sidebarEntries = new ArrayList<>();

    // ── Screen state ───────────────────────────────────────────────────────────
    private final long menuOpenAnimStartMs = System.currentTimeMillis();
    private static final int MENU_OPEN_DURATION_MS = 220;

    private final Screen parent;
    /** Sidebar rows showing section links; multiple profiles may stay open at once. */
    private final LinkedHashSet<String> expandedProfileIds = new LinkedHashSet<>();
    /** Profile whose panel is shown in the content area; independent of expansion. */
    private String selectedProfileId;
    private Panel activePanel = Panel.NONE;
    private int sidebarScroll;

    // Per-panel scroll positions (survive resize-triggered init() calls)
    private int serverScroll, winScroll, actionScroll, aliasScroll;

    /** Vertical scroll offset (px) for Settings content between {@link #bodyY()} and the footer strip. */
    private int settingsContentScroll;

    /** Settings widgets drawn inside the scroll viewport (clipped); excludes Done + Reset All. */
    private final List<Renderable> settingsScrollClipRenderables = new ArrayList<>();
    /** Pre-scroll logical Y for {@link #applySettingsScrollToWidgets()}. */
    private final IdentityHashMap<AbstractWidget, Integer> settingsScrollWidgetLogicalY = new IdentityHashMap<>();
    /** On Settings: Done + Reset All — drawn outside the scroll scissor (same order as widget registration). */
    private final List<Renderable> settingsNonClipRenderables = new ArrayList<>();

    /** Chat Windows widgets drawn inside the scroll viewport (clipped); excludes footer buttons + modals. */
    private final List<Renderable> chatWindowsScrollClipRenderables = new ArrayList<>();
    /** Pre-scroll logical Y for {@link #applyChatWindowsScrollToWidgets()}. */
    private final IdentityHashMap<AbstractWidget, Integer> chatWindowsScrollWidgetLogicalY =
            new IdentityHashMap<>();
    /** Chat Windows widgets drawn outside the scroll scissor (Create Window / Adjust Layout, modals, etc). */
    private final List<Renderable> chatWindowsNonClipRenderables = new ArrayList<>();

    /** Drag-to-scroll state for Chat Windows panel content area. */
    private boolean chatWindowsContentDragScroll;
    private int chatWindowsContentDragAnchorMy;
    private int chatWindowsContentDragAnchorScroll;

    /** Hit box for the “Rainnny” author link in the sidebar header (updated each render). */
    private int sidebarAuthorLinkL, sidebarAuthorLinkR, sidebarAuthorLinkT, sidebarAuthorLinkB;

    /** Milliseconds to confirm destructive actions after first click. */
    private static final long DESTRUCTIVE_CONFIRM_MS = 3000L;

    /** 0 = not armed; otherwise deadline (ms) for second click to delete profile. */
    private long deleteProfileConfirmDeadlineMs;
    /** Settings panel: deadline (ms) for second click to reset all options and controls. */
    private long resetDefaultsConfirmDeadlineMs;
    /** Per window id: deadline (ms) for second click to remove. */
    private final Map<String, Long> removeWindowConfirmDeadlines = new HashMap<>();

    /** Inline-expanded windows on Chat Windows panel (any subset). */
    private final Set<String> expandedWindowIds = new LinkedHashSet<>();

    /**
     * Survives closing the menu (same JVM): restored when reopening Chat Windows for the same profile.
     */
    private static String menuExpandedChatWindowsProfileId;

    private static final Set<String> menuExpandedChatWindowsPersisted = new LinkedHashSet<>();

    /** Key {@code windowId + "\\0" + tabId} → confirm deadline (ms). */
    private final Map<String, Long> removeTabConfirmDeadlines = new HashMap<>();
    /** Per (window id, tab id) pattern list scroll inside an expanded block. */
    private final Map<String, Integer> windowTabPatScroll = new HashMap<>();
    /** Selected tab for menu editing per window id (HUD selection is on {@link ChatWindow}). */
    private final Map<String, String> menuWindowTabId = new HashMap<>();

    private boolean createWinDialogOpen;
    private EditBox dlgWinIdField, dlgWinPatField;
    private AbstractWidget dlgCreateButton, dlgCancelButton;
    private int createDlgX, createDlgY, createDlgW, createDlgH;

    private boolean renameDialogOpen;
    private String renameDlgOldId;
    private EditBox dlgRenameIdField;
    private AbstractWidget dlgRenameOkButton, dlgRenameCancelButton;
    private int renameDlgX, renameDlgY, renameDlgW, renameDlgH;

    private boolean newTabDialogOpen;
    private String newTabDlgWindowId;
    private EditBox dlgNewTabNameField;
    private AbstractWidget dlgNewTabOkButton, dlgNewTabCancelButton;
    private int newTabDlgX, newTabDlgY, newTabDlgW, newTabDlgH;

    private boolean renameTabDialogOpen;
    private String renameTabDlgWindowId;
    private String renameTabDlgTabId;
    private EditBox dlgRenameTabNameField;
    private AbstractWidget dlgRenameTabOkButton, dlgRenameTabCancelButton;
    private int renameTabDlgX, renameTabDlgY, renameTabDlgW, renameTabDlgH;

    private boolean createProfileDialogOpen;
    private EditBox dlgCreateProfileNameField;
    private AbstractWidget dlgCreateProfileOkButton, dlgCreateProfileCancelButton;
    private int createProfileDlgX, createProfileDlgY, createProfileDlgW, createProfileDlgH;

    private boolean importExportChoiceDialogOpen;
    private boolean importExportChoiceIsImport;
    private AbstractWidget importExportProfilesOnlyBtn;
    private AbstractWidget importExportProfilesAndSettingsBtn;
    private AbstractWidget importExportCancelBtn;
    private int importExportDlgX, importExportDlgY, importExportDlgW, importExportDlgH;

    /** In-menu color editor (accent or timestamp RGB); does not replace this screen. */
    private ModPrimaryColorPickerOverlay modColorPickerOverlay;

    /** Domain whitelist for chat image hover previews. */
    private ImagePreviewWhitelistOverlay imageWhitelistOverlay;

    private EditBox settingsSearchField;
    private EditBox settingsTimestampFormatField;
    /** Width reserved left of the timestamp format {@link EditBox} for the live preview (see {@link #buildSettingsWidgets}). */
    private int settingsTimestampPreviewSlotW = 72;

    private EditBox settingsStackedMessageFormatField;
    /** Width reserved left of the stacked format {@link EditBox} for the live preview. */
    private int settingsStackedMessagePreviewSlotW = 72;

    /** Filter string for the Settings panel search field (lowercased substring match). */
    private String settingsSearchQuery = "";

    private static final int SETTINGS_SEARCH_FIELD_H = 22;
    private static final int SETTINGS_SEARCH_GAP = 8;
    /** Vertical space reserved under {@link #bodyY()} for the search field before the first section title. */
    private static final int SETTINGS_SEARCH_RESERVE = SETTINGS_SEARCH_FIELD_H + SETTINGS_SEARCH_GAP;

    /** Bottom Y (exclusive) of scrollable settings content; computed in {@link #rebuildSettingsFormLayout()}. */
    private int settingsFormContentBottom;

    private static final int SETTINGS_SEC_MOD = 0;
    private static final int SETTINGS_SEC_CONTROLS = 1;
    private static final int SETTINGS_SEC_CLICK_COPY = 2;
    private static final int SETTINGS_SEC_SMOOTH = 3;
    private static final int SETTINGS_SEC_HISTORY = 4;
    private static final int SETTINGS_SEC_STACK = 5;
    private static final int SETTINGS_SEC_TIMESTAMP = 6;
    private static final int SETTINGS_SEC_CHAT_SEARCH = 7;
    private static final int SETTINGS_SEC_IMAGE = 8;
    private static final int SETTINGS_SEC_SYMBOL = 9;
    private static final int SETTINGS_SEC_UNREAD = 10;
    private static final int SETTINGS_SEC_OTHER = 11;
    private static final int SETTINGS_SEC_PROFILES = 12;
    private static final int SETTINGS_SEC_COUNT = 13;

    /** One row per settings control, top-to-bottom order (must match {@link #buildSettingsWidgets}). */
    private enum SettingsRow {
        CHECK_FOR_UPDATES,
        OPEN_MENU,
        CHAT_PEEK,
        COPY_PLAIN_BIND,
        COPY_FORMATTED_BIND,
        FULLSCREEN_IMAGE_CLICK,
        CLICK_TO_COPY,
        COPY_FORMATTED_STYLE,
        SMOOTH_CHAT,
        SMOOTH_CHAT_DELAY_MS,
        SMOOTH_CHAT_BAR_OPEN_MS,
        LONGER_CHAT_HISTORY,
        CHAT_HISTORY_LIMIT,
        STACK_REPEATED_MESSAGES,
        STACKED_MESSAGE_COLOR,
        STACKED_MESSAGE_FORMAT,
        CHAT_TIMESTAMPS,
        CHAT_TIMESTAMP_COLOR,
        CHAT_TIMESTAMP_FORMAT,
        CHAT_SEARCH_BAR,
        CHAT_SEARCH_BAR_POSITION,
        IMAGE_PREVIEW_ENABLED,
        IMAGE_PREVIEW_WHITELIST,
        IMAGE_PREVIEW_ALLOW_UNTRUSTED,
        CHAT_SYMBOL_SELECTOR,
        SYMBOL_PALETTE_INSERT_STYLE,
        MOD_PRIMARY_COLOR,
        CHAT_PANEL_BACKGROUND_OPACITY,
        CHAT_PANEL_BACKGROUND_FOCUSED_OPACITY,
        CHAT_BAR_BACKGROUND_OPACITY,
        CHAT_TEXT_SHADOW,
        CHAT_BAR_MENU_BUTTON,
        TAB_UNREAD_BADGES,
        ALWAYS_SHOW_UNREAD_TABS,
        UNREAD_BADGE_STYLE,
        UNREAD_BADGE_COLOR,
        IGNORE_SELF_CHAT_ACTIONS,
        PRESERVE_VANILLA_CHAT_LOG_ON_LEAVE,
        PROFILES_IMPORT_EXPORT_ROW,
        PROFILES_LABY_ROW;

        static final int COUNT = values().length;
    }

    private final int[] settingsSectionTitleY = new int[SETTINGS_SEC_COUNT];
    private final int[] settingsFormRowY = new int[SettingsRow.COUNT];

    /** Animated thumb position (0 = off, 1 = on); keyed by stable id so toggles survive {@link #init()}. */
    private final HashMap<String, Float> settingsBooleanSwitchThumbTravel = new HashMap<>();

    /** Previous {@link System#nanoTime()} per switch id for frame-rate-independent thumb easing. */
    private final HashMap<String, Long> settingsBooleanSwitchLastAnimNs = new HashMap<>();

    /**
     * Background rects for expanded window blocks. When {@code chatListYLogical}, {@code t}/{@code b} store
     * {@code screenY + scrollAtBuild} so they stay aligned when {@link #chatWindowsListScrollPixels} changes
     * without a full {@link #init()}.
     */
    private record WinChromeRect(int l, int t, int r, int b, boolean chatListYLogical) {
        WinChromeRect(int l, int t, int r, int b) {
            this(l, t, r, b, false);
        }
    }

    private final List<WinChromeRect> winChromeRects = new ArrayList<>();

    /** Tab chip rects inside expanded window blocks; filled in {@link #buildChatWindowsWidgets}. */
    private record MenuTabRect(String windowId, String tabId, int tabIndex, int l, int t, int r, int b) {}

    private final List<MenuTabRect> menuTabRects = new ArrayList<>();

    private @org.jspecify.annotations.Nullable String menuDragWindowId;
    private @org.jspecify.annotations.Nullable String menuDragTabId;
    private int menuDragFromIndex = -1;
    private int menuDragHoverIndex = -1;
    private double menuDragPressX;
    private double menuDragPressY;
    private boolean menuDragDidMove;

    /** Chat Windows list scrollbar (see {@link ThinScrollbar}); metrics from {@link #buildChatWindowsWidgets}. */
    private boolean chatWindowsShowScrollbar;
    private int chatWindowsListScrollPixels;
    private int chatWindowsTotalListHeight;
    private int chatWindowsListViewportHeight;

    private enum ThinScrollDrag {
        NONE,
        SETTINGS_THUMB,
        CHAT_WINDOWS_THUMB
    }

    private ThinScrollDrag thinScrollDrag = ThinScrollDrag.NONE;
    private double thinScrollDragAnchorScroll;
    private int thinScrollDragAnchorMy;
    private int thinScrollDragMaxTravel;
    private double thinScrollDragMaxScroll;

    // Widget references rebuilt each init()
    private EditBox nameField, newServerField;
    private EditBox patternField, soundField;
    private EditBox commandAliasAddFromField, commandAliasAddToField;
    private String commandAliasNewFromDraft = "";
    private String commandAliasNewToDraft = "";
    private ChatActionEffect.Type chatActionNewType = ChatActionEffect.Type.PLAY_SOUND;
    /** Preserved across effect-type cycles and {@link #init()} rebuilds (add row pattern field). */
    private String chatActionNewPatternDraft = "";
    private int chatActionNewHighlightRgb = ChatActionEffect.DEFAULT_HIGHLIGHT_RGB;
    private boolean chatActionNewHlBold;
    private boolean chatActionNewHlItalic;
    private boolean chatActionNewHlUnderlined;
    private boolean chatActionNewHlStrikethrough;
    private boolean chatActionNewHlObfuscated;
    private String chatHighlightPickProfileId;
    private int chatHighlightPickGroupIndex = -1;
    private int chatHighlightPickEffectIndex = -1;

    // Chat Actions: effect type dropdown (replaces cycle button)
    private boolean chatActionTypeDropdownOpen;
    private String chatActionTypePickProfileId;
    private int chatActionTypePickGroupIndex = -1;
    private int chatActionTypePickEffectIndex = -1;
    private int chatActionTypeDropX, chatActionTypeDropY, chatActionTypeDropW, chatActionTypeDropH;

    /** When true, hex field receives {@link #charTyped} / {@link #keyPressed} until user clicks elsewhere on the picker. */
    private boolean colorPickerHexKeyboardCapture;

    /** Settings: copy / symbol style dropdown (same interaction model as {@link #chatActionTypeDropdownOpen}). */
    private boolean settingsFormatDropdownOpen;

    private boolean settingsUnreadBadgeStyleDropdownOpen;
    private int settingsUnreadBadgeStyleDropX;
    private int settingsUnreadBadgeStyleDropY;
    private int settingsUnreadBadgeStyleDropW;
    private int settingsUnreadBadgeStyleDropH;
    /** {@code true} = symbol palette insert style; {@code false} = chat copy formatted style. */
    private boolean settingsFormatDropdownSymbolPalette;
    private int settingsFormatDropX, settingsFormatDropY, settingsFormatDropW, settingsFormatDropH;

    // Sound autocomplete popup (scrollable when many matches)
    private static final int SUGGEST_VISIBLE_ROWS = 8;
    private static final int SUGGEST_MAX_MATCHES  = 1024;
    private static final int SUGGEST_ROW_H        = 12;
    private List<String> sugFiltered = List.of();
    private int sugScroll;
    private String sugLastQuery;
    private int sugLeft, sugTop, sugWidth, sugVisibleRows;

    // Command autocomplete popup (Command Aliases panel)
    private List<String> cmdSugFiltered = List.of();
    private int cmdSugScroll;
    private String cmdSugLastQuery;
    private int cmdSugLeft, cmdSugTop, cmdSugWidth, cmdSugVisibleRows;

    private record PendingPatternEdit(
            EditBox box, ServerProfile profile, String windowId, String tabId, int userPosition) {}
    private final List<PendingPatternEdit> pendingPatternEdits = new ArrayList<>();
    private record PendingNewPattern(EditBox box, ServerProfile profile, String windowId, String tabId) {}
    private final List<PendingNewPattern> pendingNewPatterns = new ArrayList<>();
    private record PendingChatGroupPattern(EditBox patternBox, ServerProfile profile, int groupIndex) {}
    /** Boxes may be null depending on effect type (sound vs target text). */
    private record PendingChatEffectEdit(EditBox soundBox, EditBox targetBox, ServerProfile profile, int groupIndex, int effectIndex) {}
    private final List<PendingChatGroupPattern> pendingChatGroupPatterns = new ArrayList<>();
    private final List<PendingChatEffectEdit> pendingChatEffectEdits = new ArrayList<>();
    /** Session-only collapse state for Chat Actions groups (index-based; rebuilt on init). */
    private final java.util.Set<Integer> collapsedChatActionGroups = new java.util.HashSet<>();
    /** One-time seed so Chat Actions groups start collapsed by default. */
    private boolean chatActionsCollapseSeeded;
    private record PendingCommandAliasEdit(EditBox fromBox, EditBox toBox, ServerProfile profile, int index) {}
    private final List<PendingCommandAliasEdit> pendingCommandAliasEdits = new ArrayList<>();

    /** Waiting for the next key / mouse button to assign {@link ChatUtilitiesModClient#OPEN_MENU_KEY}. */
    private boolean rebindingOpenMenuKey;
    private boolean rebindingChatPeekKey;
    /** Waiting for the next mouse button/modifiers to assign fullscreen image preview click binding. */
    private boolean rebindingFullscreenImageClick;
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
            this.serverScroll = ChatUtilitiesClientOptions.getLastMenuServerScroll();
            this.winScroll = ChatUtilitiesClientOptions.getLastMenuWinScroll();
            this.actionScroll = ChatUtilitiesClientOptions.getLastMenuActionScroll();
            this.aliasScroll = ChatUtilitiesClientOptions.getLastMenuAliasScroll();
            this.settingsContentScroll = ChatUtilitiesClientOptions.getLastMenuSettingsContentScroll();
            this.chatWindowsListScrollPixels = ChatUtilitiesClientOptions.getLastMenuChatWindowsListScrollPixels();
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

    /** Parent screen when this menu was opened (e.g. chat). */
    public Screen getMenuParent() {
        return parent;
    }

    @Override
    public Screen recreateForProfile() {
        if (selectedProfileId == null) {
            return new ChatUtilitiesRootScreen(parent);
        }
        return new ChatUtilitiesRootScreen(parent, selectedProfileId, activePanel);
    }

    public Screen getParentScreen() { return parent; }

    // ── Panel / content coordinates ────────────────────────────────────────────

    /** Inset from left/right edges — larger on wide screens so the panel stays a comfortable size. */
    private int marginX() {
        return Math.max(MARGIN_X_MIN, this.width * 11 / 100);
    }

    private int marginY() {
        return Math.max(MARGIN_Y_MIN, this.height * 9 / 100);
    }

    private int panelLeft() {
        return marginX();
    }

    private int panelRight() {
        return this.width - marginX();
    }

    private int panelTop() {
        return marginY();
    }

    private int panelBottom() {
        return this.height - marginY();
    }

    private int scrollbarReserve(boolean scrollbarVisible) {
        return scrollbarVisible ? ThinScrollbar.W + SCROLLBAR_CONTENT_GAP : 0;
    }
    private int panelW()      { return panelRight()  - panelLeft(); }
    private int panelH()      { return panelBottom() - panelTop(); }

    private int sidebarLeft()   { return panelLeft(); }
    private int sidebarRight()  { return panelLeft() + SIDEBAR_W; }
    private int sidebarTop()    { return panelTop(); }
    private int sidebarBottom() { return panelBottom(); }

    private boolean sidebarShowUpdateFooterRow() {
        return ChatUtilitiesClientOptions.isCheckForUpdatesEnabled() && ModUpdateChecker.isUpdateAvailable();
    }

    /** Rounded panel interior used for sidebar clipping (full logical panel; border is outside these bounds). */
    private int panelInnerX() {
        return panelLeft();
    }

    private int panelInnerY() {
        return panelTop();
    }

    private int panelInnerW() {
        return panelW();
    }

    private int panelInnerH() {
        return panelH();
    }

    private int panelInnerCornerRadius() {
        return Math.min(PANEL_CORNER_RADIUS, Math.min(panelW(), panelH()) / 2);
    }

    /** Sidebar (or separator) pixels clipped to the panel’s rounded fill (same bounds as the solid panel background). */
    private void fillSidebarClipped(GuiGraphics g, int ax, int ay, int aw, int ah, int color) {
        RoundedPanelRenderer.fillRectIntersectRounded(
                g,
                panelInnerX(),
                panelInnerY(),
                panelInnerW(),
                panelInnerH(),
                panelInnerCornerRadius(),
                ax,
                ay,
                aw,
                ah,
                color);
    }

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

    private static String stripSlashCommand(String raw) {
        if (raw == null) {
            return "";
        }
        String s = raw.strip();
        while (s.startsWith("/")) {
            s = s.substring(1);
        }
        return s.strip();
    }

    private static String displaySlashCommand(String raw) {
        String s = stripSlashCommand(raw);
        return s.isEmpty() ? "" : "/" + s;
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
        if ("IGNORED_CHAT".equals(name) || "CHAT_SOUNDS".equals(name)) {
            return Panel.CHAT_ACTIONS;
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
        g.fill(0, 0, this.width, this.height, menuFadeArgb(0x55000000));
        int pl = panelLeft();
        int pt = panelTop();
        int pr = panelRight();
        int pb = panelBottom();
        int pw = pr - pl;
        int ph = pb - pt;
        int panelBg = menuFadeArgb(multiplyAlpha(C_PANEL_BG, 0.92f));
        float menuSc = menuOpenVisualScale();
        boolean menuScalePose = menuSc < 0.9995f;
        if (menuScalePose) {
            float mcx = (pl + pr) / 2f;
            float mcy = (pt + pb) / 2f;
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(mcx, mcy);
            pose.scale(menuSc, menuSc);
            pose.translate(-mcx, -mcy);
        }
        try {
            RoundedPanelRenderer.fillRoundedRectOutsideBorder(
                    g, pl, pt, pw, ph, PANEL_CORNER_RADIUS, panelBg, menuFadeArgb(C_PANEL_BORDER), PANEL_BORDER_PX);
            if ((activePanel == Panel.CHAT_WINDOWS || activePanel == Panel.CHAT_ACTIONS)
                    && !winChromeRects.isEmpty()) {
                int cws = chatWindowsListScrollPixels;
                for (WinChromeRect rc : winChromeRects) {
                    int rt = rc.chatListYLogical() ? rc.t() - cws : rc.t();
                    int rb = rc.chatListYLogical() ? rc.b() - cws : rc.b();
                    g.fill(rc.l(), rt, rc.r(), rb, menuFadeArgb(C_WIN_GROUP_BG));
                    g.renderOutline(rc.l(), rt, rc.r() - rc.l(), rb - rt, menuFadeArgb(C_WIN_GROUP_EDGE));
                }
            }
        } finally {
            if (menuScalePose) {
                g.pose().popMatrix();
            }
        }
    }

    private void afterOpenColorPicker() {
        if (modColorPickerOverlay == null || this.minecraft == null) {
            return;
        }
        modColorPickerOverlay.layout(this.width, this.height);
        EditBox hex = modColorPickerOverlay.getHexField();
        if (hex != null) {
            hex.setFocused(true);
            setFocused(hex);
        }
    }

    private float menuOpenEase() {
        float u =
                Mth.clamp(
                        (System.currentTimeMillis() - menuOpenAnimStartMs) / (float) MENU_OPEN_DURATION_MS,
                        0f,
                        1f);
        // Smoothstep then gentle ease-out so the panel does not “snap” at the end.
        float s = u * u * (3f - 2f * u);
        float t = 1f - (float) Math.pow(1f - s, 2.2f);
        return t;
    }

    private int menuFadeArgb(int argb) {
        float t = menuOpenEase();
        int a = (argb >>> 24) & 0xFF;
        int na = Mth.clamp(Math.round(a * t), 0, 255);
        return (na << 24) | (argb & 0xFFFFFF);
    }

    /** Visual scale for open animation (1 = full size). Matches fade timing; smoothstep for a soft settle. */
    private float menuOpenVisualScale() {
        float t = menuOpenEase();
        float st = t * t * (3f - 2f * t);
        return Mth.lerp(0.90f, 1f, st);
    }

    private double menuPointerToLogicalX(double screenX) {
        float s = menuOpenVisualScale();
        if (s >= 0.9995f) {
            return screenX;
        }
        double cx = (panelLeft() + panelRight()) / 2.0;
        return (screenX - cx) / s + cx;
    }

    private double menuPointerToLogicalY(double screenY) {
        float s = menuOpenVisualScale();
        if (s >= 0.9995f) {
            return screenY;
        }
        double cy = (panelTop() + panelBottom()) / 2.0;
        return (screenY - cy) / s + cy;
    }

    private MouseButtonEvent remapMenuPointerForHitTest(MouseButtonEvent ev) {
        double nx = menuPointerToLogicalX(ev.x());
        double ny = menuPointerToLogicalY(ev.y());
        if (Math.abs(nx - ev.x()) < 1e-4 && Math.abs(ny - ev.y()) < 1e-4) {
            return ev;
        }
        return new MouseButtonEvent(nx, ny, ev.buttonInfo());
    }

    private static int multiplyAlpha(int argb, float m) {
        int a = (argb >>> 24) & 0xFF;
        int rgb = argb & 0xFFFFFF;
        int na = Mth.clamp(Math.round(a * m), 0, 255);
        return (na << 24) | rgb;
    }

    /**
     * Rectangular switch (track + thumb). {@code animKey} must be stable across {@link #init()} so the thumb can
     * animate after options toggle; easing uses real time between renders.
     */
    private void renderSettingsBooleanSwitch(
            GuiGraphics g,
            String animKey,
            int x,
            int y,
            int w,
            int h,
            boolean on,
            boolean hovered,
            boolean widgetActive,
            float partialTick) {
        int trackH = Math.min(h, SETTINGS_BOOLEAN_SWITCH_H);
        int trackW = Math.min(w, SETTINGS_BOOLEAN_SWITCH_W);
        trackH = Math.max(10, trackH);
        trackW = Math.max(trackH * 2 + 8, trackW);
        int rx = x + w - trackW;
        int ry = y + (h - trackH) / 2;
        float target = on ? 1f : 0f;
        float travelVis;
        if (animKey == null || animKey.isEmpty() || !widgetActive) {
            travelVis = target;
            if (animKey != null && !animKey.isEmpty()) {
                settingsBooleanSwitchThumbTravel.put(animKey, target);
                settingsBooleanSwitchLastAnimNs.remove(animKey);
            }
        } else {
            long nowNs = System.nanoTime();
            Long prevNs = settingsBooleanSwitchLastAnimNs.put(animKey, nowNs);
            float dtSec =
                    prevNs == null
                            ? 1f / 60f
                            : Math.min(0.09f, (nowNs - prevNs) / 1_000_000_000f);
            float cur = settingsBooleanSwitchThumbTravel.getOrDefault(animKey, target);
            float k = 1f - (float) Math.exp(-dtSec * 26f);
            travelVis = Mth.lerp(k, cur, target);
            if (Math.abs(travelVis - target) < 0.004f) {
                travelVis = target;
            }
            settingsBooleanSwitchThumbTravel.put(animKey, travelVis);
        }
        int trackOff = !widgetActive ? 0xFF303038 : hovered ? 0xFF404450 : 0xFF363644;
        int trackOn = 0xFF000000 | (modGlobalAccentRgb() & 0xFFFFFF);
        int fillOff = trackOff;
        int fillOn = trackOn;
        float uFill = Mth.clamp(travelVis, 0f, 1f);
        int fillA = (int) (uFill * 255);
        int fillCol = fillOff;
        if (fillA > 0) {
            int r0 = (fillOff >> 16) & 0xFF;
            int g0 = (fillOff >> 8) & 0xFF;
            int b0 = fillOff & 0xFF;
            int r1 = (fillOn >> 16) & 0xFF;
            int g1 = (fillOn >> 8) & 0xFF;
            int b1 = fillOn & 0xFF;
            float k = fillA / 255f;
            int r = Mth.clamp(Math.round(Mth.lerp(k, r0, r1)), 0, 255);
            int gr = Mth.clamp(Math.round(Mth.lerp(k, g0, g1)), 0, 255);
            int bl = Mth.clamp(Math.round(Mth.lerp(k, b0, b1)), 0, 255);
            fillCol = 0xFF000000 | (r << 16) | (gr << 8) | bl;
        }
        g.fill(rx, ry, rx + trackW, ry + trackH, fillCol);
        g.renderOutline(rx, ry, trackW, trackH, 0xFF1A1A22);
        int margin = 2;
        int thumb = trackH - 2 * margin;
        int thumbY = ry + margin;
        int travel = Math.max(0, trackW - thumb - 2 * margin);
        float uThumb = Mth.clamp(travelVis, 0f, 1f);
        int thumbX = rx + margin + Math.round(uThumb * travel);
        int thumbCol = 0xFFF4F4FA;
        g.fill(thumbX + 1, thumbY + 1, thumbX + thumb + 1, thumbY + thumb + 1, 0x48000000);
        g.fill(thumbX, thumbY, thumbX + thumb, thumbY + thumb, thumbCol);
        g.renderOutline(thumbX, thumbY, thumb, thumb, 0xFF4A4A58);
    }

    /**
     * Hit test for the right-aligned pill drawn by {@link #renderSettingsBooleanSwitch}; the widget cell is often
     * wider than the track, so the full {@link AbstractWidget} bounds must not be used for hover/click.
     */
    private static boolean settingsBooleanSwitchHit(int x, int y, int w, int h, double mx, double my) {
        int trackH = Math.min(h, SETTINGS_BOOLEAN_SWITCH_H);
        int trackW = Math.min(w, SETTINGS_BOOLEAN_SWITCH_W);
        trackH = Math.max(10, trackH);
        trackW = Math.max(trackH * 2 + 8, trackW);
        int rx = x + w - trackW;
        int ry = y + (h - trackH) / 2;
        return mx >= rx && mx < rx + trackW && my >= ry && my < ry + trackH;
    }

    private abstract class SettingsBooleanToggleWidget extends AbstractWidget {
        SettingsBooleanToggleWidget(int x, int y, int w, int h) {
            super(x, y, w, h, Component.empty());
        }

        /**
         * {@link AbstractWidget#render} sets {@code isHovered} from this, which drives tooltips — not only
         * {@link #isMouseOver}; must match {@link #renderSettingsBooleanSwitch} geometry.
         */
        @Override
        protected boolean areCoordinatesInRectangle(double mouseX, double mouseY) {
            return settingsBooleanSwitchHit(
                    getX(), getY(), getWidth(), getHeight(), mouseX, mouseY);
        }
    }

    // ── init() ────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        boolean keepImportExportChoiceDialogOpen = importExportChoiceDialogOpen;
        boolean keepImportExportChoiceIsImport = importExportChoiceIsImport;
        clearWidgets();
        colorPickerHexKeyboardCapture = false;
        clearSettingsFormatDropdown();
        clearSettingsUnreadBadgeStyleDropdown();
        modColorPickerOverlay = null;
        clearPendingChatHighlightPickState();
        settingsSearchField = null;
        settingsTimestampFormatField = null;
        settingsScrollClipRenderables.clear();
        settingsScrollWidgetLogicalY.clear();
        settingsNonClipRenderables.clear();
        chatWindowsScrollClipRenderables.clear();
        chatWindowsScrollWidgetLogicalY.clear();
        chatWindowsNonClipRenderables.clear();
        // Preserve scroll across close/reopen; only clamp when building settings.
        sugFiltered = List.of();
        pendingPatternEdits.clear();
        pendingNewPatterns.clear();
        pendingChatGroupPatterns.clear();
        pendingChatEffectEdits.clear();
        pendingCommandAliasEdits.clear();
        dlgWinIdField = null;
        dlgWinPatField = null;
        dlgCreateButton = null;
        dlgCancelButton = null;
        dlgRenameIdField = null;
        dlgRenameOkButton = null;
        dlgRenameCancelButton = null;
        dlgNewTabNameField = null;
        dlgNewTabOkButton = null;
        dlgNewTabCancelButton = null;
        dlgRenameTabNameField = null;
        dlgRenameTabOkButton = null;
        dlgRenameTabCancelButton = null;
        dlgCreateProfileNameField = null;
        dlgCreateProfileOkButton = null;
        dlgCreateProfileCancelButton = null;
        importExportProfilesOnlyBtn = null;
        importExportProfilesAndSettingsBtn = null;
        importExportCancelBtn = null;
        importExportChoiceDialogOpen = keepImportExportChoiceDialogOpen;
        importExportChoiceIsImport = keepImportExportChoiceIsImport;
        importExportDlgX = 0;
        importExportDlgY = 0;
        importExportDlgW = 0;
        importExportDlgH = 0;
        chatActionTypeDropdownOpen = false;
        chatActionTypePickProfileId = null;
        chatActionTypePickGroupIndex = -1;
        chatActionTypePickEffectIndex = -1;
        chatActionTypeDropX = 0;
        chatActionTypeDropY = 0;
        chatActionTypeDropW = 0;
        chatActionTypeDropH = 0;
        chatWindowsContentDragScroll = false;
        chatWindowsContentDragAnchorMy = 0;
        chatWindowsContentDragAnchorScroll = 0;

        if (activePanel != Panel.CHAT_WINDOWS) {
            createWinDialogOpen = false;
            renameDialogOpen = false;
            newTabDialogOpen = false;
            newTabDlgWindowId = null;
            renameTabDialogOpen = false;
            renameTabDlgWindowId = null;
            renameTabDlgTabId = null;
            ServerProfile persistProf = profile();
            if (persistProf != null
                    && selectedProfileId != null
                    && selectedProfileId.equals(persistProf.getId())) {
                persistMenuExpandedChatWindows(persistProf);
            }
            expandedWindowIds.clear();
            removeWindowConfirmDeadlines.clear();
            removeTabConfirmDeadlines.clear();
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
            newTabDialogOpen = false;
            newTabDlgWindowId = null;
            renameTabDialogOpen = false;
            renameTabDlgWindowId = null;
            renameTabDlgTabId = null;
        }

        winChromeRects.clear();
        thinScrollDrag = ThinScrollDrag.NONE;

        if (activePanel != Panel.SETTINGS) {
            rebindingOpenMenuKey = false;
            rebindingFullscreenImageClick = false;
            rebindingCopyPlain = false;
            rebindingCopyFormatted = false;
            resetDefaultsConfirmDeadlineMs = 0;
            imageWhitelistOverlay = null;
        }

        // Done button — always at bottom-right of content area
        AbstractWidget doneButton = primaryButton(
                ChatUtilitiesScreenLayout.BUTTON_DONE, () -> onClose(),
                contentRight() - 80, footerY(), 80, 20);
        addRenderableWidget(doneButton);
        if (activePanel == Panel.SETTINGS) {
            settingsNonClipRenderables.add(doneButton);
        }

        switch (activePanel) {
            case EDIT_PROFILE -> buildEditProfileWidgets();
            case CHAT_WINDOWS -> buildChatWindowsWidgets();
            case CHAT_ACTIONS -> buildChatActionsWidgets();
            case COMMAND_ALIASES -> buildCommandAliasesWidgets();
            case SETTINGS -> buildSettingsWidgets();
            default -> {}
        }
        if (createProfileDialogOpen) {
            buildCreateProfileDialog(ChatUtilitiesManager.get());
        }

        if (activePanel == Panel.SETTINGS && imageWhitelistOverlay != null) {
            imageWhitelistOverlay.rebuildAddField(this.font);
            imageWhitelistOverlay.layout(this.width, this.height);
            addRenderableWidget(imageWhitelistOverlay.getAddField());
        }

        // After all widgets exist, focus the modal field on the next tick (Done is added first and
        // otherwise wins initial focus; setFocused must run after Screen finishes wiring children).
        if (activePanel == Panel.CHAT_WINDOWS) {
            if (createWinDialogOpen && dlgWinIdField != null) {
                scheduleDialogFirstFieldFocus(dlgWinIdField);
            } else if (renameDialogOpen && dlgRenameIdField != null) {
                scheduleDialogSelectAllFocus(dlgRenameIdField);
            } else if (renameTabDialogOpen && dlgRenameTabNameField != null) {
                scheduleDialogSelectAllFocus(dlgRenameTabNameField);
            } else if (newTabDialogOpen && dlgNewTabNameField != null) {
                scheduleDialogFirstFieldFocus(dlgNewTabNameField);
            }
        }
        if (createProfileDialogOpen && dlgCreateProfileNameField != null) {
            scheduleDialogFirstFieldFocus(dlgCreateProfileNameField);
        }
    }

    @Override
    public void tick() {
        super.tick();
        thinScrollbarDragTick();
        long now = System.currentTimeMillis();
        boolean refresh = false;
        if (deleteProfileConfirmDeadlineMs != 0 && now >= deleteProfileConfirmDeadlineMs) {
            deleteProfileConfirmDeadlineMs = 0;
            if (activePanel == Panel.EDIT_PROFILE) {
                refresh = true;
            }
        }
        if (resetDefaultsConfirmDeadlineMs != 0 && now >= resetDefaultsConfirmDeadlineMs) {
            resetDefaultsConfirmDeadlineMs = 0;
            if (activePanel == Panel.SETTINGS) {
                refresh = true;
            }
        }
        int beforeWin = removeWindowConfirmDeadlines.size();
        removeWindowConfirmDeadlines.entrySet().removeIf(e -> now >= e.getValue());
        if (beforeWin != removeWindowConfirmDeadlines.size() && activePanel == Panel.CHAT_WINDOWS) {
            refresh = true;
        }
        int beforeTab = removeTabConfirmDeadlines.size();
        removeTabConfirmDeadlines.entrySet().removeIf(e -> now >= e.getValue());
        if (beforeTab != removeTabConfirmDeadlines.size() && activePanel == Panel.CHAT_WINDOWS) {
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

    private void scheduleDialogSelectAllFocus(EditBox box) {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.execute(
                () -> {
                    this.setFocused(box);
                    box.setFocused(true);
                    int len = box.getValue().length();
                    box.setCursorPosition(0);
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
                int acc = modAccentArgb();
                int r = (acc >> 16) & 0xFF;
                int gv = (acc >> 8) & 0xFF;
                int b = acc & 0xFF;
                int bg = (0xCC << 24)
                        | (Mth.clamp((int) (r * (hov ? 0.55f : 0.45f) + 26), 0, 255) << 16)
                        | (Mth.clamp((int) (gv * (hov ? 0.55f : 0.45f) + 74), 0, 255) << 8)
                        | Mth.clamp((int) (b * (hov ? 0.55f : 0.45f) + 138), 0, 255);
                int lr = Mth.clamp((int) (r * (hov ? 1.15f : 1f)), 0, 255);
                int lg = Mth.clamp((int) (gv * (hov ? 1.15f : 1f)), 0, 255);
                int lb = Mth.clamp((int) (b * (hov ? 1.15f : 1f)), 0, 255);
                int outline = 0xFF000000 | (lr << 16) | (lg << 8) | lb;
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
    /** Tab strip row inside an expanded chat window block. */
    private static final int WIN_TAB_STRIP_H = 22;

    /** Inset for tab strip / pattern rows inside the window chrome so controls stay inside the group outline. */
    private static final int WIN_EXPAND_INNER_PAD = 6;

    private static String windowTabPatScrollKey(String windowId, String tabId) {
        return windowId + "\t" + tabId;
    }

    private void removePatScrollKeysForWindow(String windowId) {
        String prefix = windowId + "\t";
        windowTabPatScroll.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /**
     * Tab id used for menu pattern editing for this window; falls back to the window’s default tab and
     * remembers the choice.
     */
    private String resolvedMenuTabId(ServerProfile p, String windowId) {
        ChatWindow w = p.getWindow(windowId);
        if (w == null) {
            return "";
        }
        String t = menuWindowTabId.get(windowId);
        if (t == null || w.getTabById(t) == null) {
            t = w.getDefaultTabId();
            menuWindowTabId.put(windowId, t);
        }
        return t;
    }

    private int patScrollForWindowTab(ServerProfile p, String wid) {
        String tabId = resolvedMenuTabId(p, wid);
        return windowTabPatScroll.getOrDefault(windowTabPatScrollKey(wid, tabId), 0);
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

    private int expandedWindowContentHeight(ServerProfile p, String wid, ChatWindow w) {
        String tabId = resolvedMenuTabId(p, wid);
        ChatWindowTab tab = w.getTabById(tabId);
        if (tab == null) {
            tab = w.getSelectedTab();
        }
        List<String> sources = tab.getPatternSources();
        int visiblePatRows = sources.size();
        int navH = 0;
        int pW = Math.max(40, contentW() - 2 * WIN_EXPAND_INNER_PAD);
        int tabStripH = expandedWindowTabStripHeight(w, pW);
        // Inner padding + tab strip + pattern row + nav + pattern list + bottom padding
        return 6 + tabStripH + 26 + navH + visiblePatRows * 20 + 8;
    }

    private int expandedWindowTabStripHeight(ChatWindow w, int pW) {
        int plusW = 22;
        int renameTabW = 102;
        int removeTabW = w.getTabCount() > 1 ? 80 : 0;
        int gap = 3;
        int trail = plusW + gap + renameTabW + (removeTabW > 0 ? gap + removeTabW : 0);
        int maxTabsW = Math.max(36, pW - trail);
        int rows = 1;
        int x = 0;
        int rowH = 18;
        List<ChatWindowTab> tabs = w.getTabs();
        for (ChatWindowTab tab : tabs) {
            int want = this.font.width(tab.getDisplayName()) + 10;
            int tw = Mth.clamp(want, 36, 96);
            if (x > 0 && x + tw > maxTabsW) {
                rows++;
                x = 0;
            }
            x += tw + gap;
        }
        return rows * rowH + (rows - 1) * gap;
    }

    private int chatWindowBlockHeight(ServerProfile p, String wid) {
        int h = 20;
        if (expandedWindowIds.contains(wid)) {
            ChatWindow w = p.getWindow(wid);
            if (w != null) {
                h += expandedWindowContentHeight(p, wid, w);
                h += WIN_EXPANDED_TAIL_GAP;
            }
        }
        return h;
    }

    private int buildExpandedWindowBlock(
            ChatUtilitiesManager mgr, ServerProfile p, String wid, ChatWindow w,
            int fx, int fW, int y) {
        y += 6;

        int px = fx + WIN_EXPAND_INNER_PAD;
        int pW = Math.max(40, fW - 2 * WIN_EXPAND_INNER_PAD);

        String selTabId = resolvedMenuTabId(p, wid);
        ChatWindowTab selTab = w.getTabById(selTabId);
        if (selTab == null) {
            selTab = w.getSelectedTab();
            menuWindowTabId.put(wid, selTab.getId());
            selTabId = selTab.getId();
        }

        int tabRowY = y;
        int plusW = 22;
        int renameTabW = 102;
        int removeTabW = w.getTabCount() > 1 ? 80 : 0;
        int gap = 3;
        List<ChatWindowTab> tabs = w.getTabs();
        int trail = plusW + gap + renameTabW + (removeTabW > 0 ? gap + removeTabW : 0);
        int maxTabsW = Math.max(36, pW - trail);
        int tabsRight = px + maxTabsW;

        int rowH = 18;
        int tabX = px;
        int tabY = tabRowY;
        int tabCs = chatWindowsListScrollPixels;
        for (int tabIndex = 0; tabIndex < tabs.size(); tabIndex++) {
            ChatWindowTab tab = tabs.get(tabIndex);
            boolean on = tab.getId().equals(selTabId);
            int want = this.font.width(tab.getDisplayName()) + 10;
            int tw = Mth.clamp(want, 36, 96);
            if (tabX > px && tabX + tw > tabsRight) {
                tabX = px;
                tabY += rowH + gap;
            }
            String label = truncateToWidth(tab.getDisplayName(), tw - 10);
            final String tid = tab.getId();
            Runnable select = () -> {
                menuWindowTabId.put(wid, tid);
                init();
            };
            addChatWindowsScrollClipWidget(
                    on
                            ? flatButtonPositive(Component.literal(label), select, tabX, tabY, tw, 18)
                            : flatButton(Component.literal(label), select, tabX, tabY, tw, 18));
            menuTabRects.add(new MenuTabRect(wid, tid, tabIndex, tabX, tabY + tabCs, tabX + tw, tabY + 18 + tabCs));
            tabX += tw + gap;
        }

        int buttonsX = px + maxTabsW + gap;
        addChatWindowsScrollClipWidget(flatButtonPositive(Component.literal("+"), () -> {
            createWinDialogOpen = false;
            renameDialogOpen = false;
            renameTabDialogOpen = false;
            newTabDlgWindowId = wid;
            newTabDialogOpen = true;
            init();
        }, buttonsX, tabRowY, plusW, 18));
        buttonsX += plusW + gap;
        final String renameTid = selTabId;
        addChatWindowsScrollClipWidget(
                flatButton(
                        Component.literal(CHAT_WIN_SYMBOL_RENAME + " Rename Tab"),
                        () -> {
                            createWinDialogOpen = false;
                            renameDialogOpen = false;
                            newTabDialogOpen = false;
                            renameTabDlgWindowId = wid;
                            renameTabDlgTabId = renameTid;
                            renameTabDialogOpen = true;
                            init();
                        },
                        buttonsX,
                        tabRowY,
                        renameTabW,
                        18));
        buttonsX += renameTabW + gap;
        if (removeTabW > 0) {
            final String removeTid = selTabId;
            String tabKey = removeTabConfirmKey(wid, removeTid);
            long nowTab = System.currentTimeMillis();
            boolean tabArmed = removeTabConfirmDeadlines.getOrDefault(tabKey, 0L) > nowTab;
            addChatWindowsScrollClipWidget(flatButtonDestructive(
                    Component.literal(tabArmed ? "✕ Confirm Tab" : "✕ Tab"),
                    () -> {
                        long t = System.currentTimeMillis();
                        if (removeTabConfirmDeadlines.getOrDefault(tabKey, 0L) > t) {
                            removeTabConfirmDeadlines.remove(tabKey);
                            if (mgr.removeChatWindowTab(p, wid, removeTid)) {
                                windowTabPatScroll.remove(windowTabPatScrollKey(wid, removeTid));
                                ChatWindow w2 = p.getWindow(wid);
                                if (w2 != null) {
                                    menuWindowTabId.put(wid, w2.getDefaultTabId());
                                }
                            }
                            init();
                        } else {
                            removeTabConfirmDeadlines.put(tabKey, t + DESTRUCTIVE_CONFIRM_MS);
                            init();
                        }
                    },
                    buttonsX,
                    tabRowY,
                    removeTabW,
                    18));
        }
        int tabStripH = expandedWindowTabStripHeight(w, pW);
        y += tabStripH + 4;

        final String activeTabId = selTabId;
        int addPatBtnW = 72;
        int patRowGap = 4;
        int minField = 64;
        int minPatRowTotal = minField + addPatBtnW + patRowGap;
        int desiredPatRowTotal = (int) Math.round(pW * 0.47);
        int patRowTotal;
        if (pW <= minPatRowTotal) {
            patRowTotal = pW;
        } else {
            patRowTotal = Math.min(pW, Math.max(minPatRowTotal, desiredPatRowTotal));
        }
        int fieldW = patRowTotal - addPatBtnW - patRowGap;
        EditBox newPat = new EditBox(this.font, px, y, fieldW, 20, Component.literal("pat"));
        newPat.setMaxLength(2048);
        newPat.setHint(ChatUtilitiesScreenLayout.PATTERN_INPUT_HINT);
        addChatWindowsScrollClipWidget(newPat);
        pendingNewPatterns.add(new PendingNewPattern(newPat, p, wid, activeTabId));
        addChatWindowsScrollClipWidget(flatButtonPositive(Component.literal("+ Add"), () -> {
            try {
                mgr.addPattern(p, wid, activeTabId, newPat.getValue());
                newPat.setValue("");
            } catch (PatternSyntaxException ignored) {}
            init();
        }, px + fieldW + patRowGap, y, addPatBtnW, 20));
        y += 26;

        List<String> sources = selTab.getPatternSources();
        int from = 0;
        int to = sources.size();

        int patXW = 24;
        int patGap = 4;
        for (int pi = from; pi < to; pi++) {
            String src = sources.get(pi);
            int pos = pi + 1;
            EditBox patEb = new EditBox(this.font, px, y, pW - patXW - patGap, 20, Component.literal("wp" + pos));
            patEb.setMaxLength(2048);
            patEb.setValue(src);
            patEb.setHint(ChatUtilitiesScreenLayout.PATTERN_INPUT_HINT);
            addChatWindowsScrollClipWidget(patEb);
            addChatWindowsScrollClipWidget(flatButtonDestructive(Component.literal("✕"), () -> {
                mgr.removePattern(p, wid, activeTabId, pos);
                init();
            }, px + pW - patXW, y, patXW, 20));
            pendingPatternEdits.add(new PendingPatternEdit(patEb, p, wid, activeTabId, pos));
            y += 20;
        }

        return y + 8;
    }

    private void buildNewTabDialog(ChatUtilitiesManager mgr, ServerProfile p) {
        if (newTabDlgWindowId == null || p.getWindow(newTabDlgWindowId) == null) {
            return;
        }
        int dlgW = Math.min(320, Math.max(220, contentW() - 24));
        int cx = contentCX();
        int dlgX = cx - dlgW / 2;
        int dlgY = bodyY() + 20;
        int dlgInnerPad = 12;
        int dlgGapAfterTitle = 10;
        int dlgGapFieldsToButtons = 14;
        int fieldH = 20;
        int btnH = 20;
        newTabDlgX = dlgX;
        newTabDlgY = dlgY;
        newTabDlgW = dlgW;

        int titleBaseline = dlgY + dlgInnerPad;
        int row1Y = titleBaseline + this.font.lineHeight + dlgGapAfterTitle;
        int btnY = row1Y + fieldH + dlgGapFieldsToButtons;
        newTabDlgH = btnY - dlgY + btnH + dlgInnerPad;

        int fieldW = dlgW - 2 * dlgInnerPad;
        dlgNewTabNameField = new EditBox(this.font, dlgX + dlgInnerPad, row1Y, fieldW, fieldH,
                Component.literal("ntab"));
        dlgNewTabNameField.setMaxLength(64);
        dlgNewTabNameField.setHint(Component.literal("Tab name"));
        addRenderableWidget(dlgNewTabNameField);

        dlgNewTabOkButton = flatButton(
                Component.literal("Add tab"),
                () -> submitNewTabDialog(mgr, p),
                dlgX + dlgInnerPad,
                btnY,
                76,
                btnH);
        addRenderableWidget(dlgNewTabOkButton);

        dlgNewTabCancelButton = flatButton(Component.literal("Cancel"), () -> closeNewTabDialog(),
                dlgX + dlgW - dlgInnerPad - 76, btnY, 76, btnH);
        addRenderableWidget(dlgNewTabCancelButton);
    }

    private void buildCreateProfileDialog(ChatUtilitiesManager mgr) {
        int dlgW = Math.min(320, Math.max(220, contentW() - 24));
        int cx = contentCX();
        int dlgX = cx - dlgW / 2;
        int dlgY = bodyY() + 20;
        int dlgInnerPad = 12;
        int dlgGapAfterTitle = 10;
        int dlgGapFieldsToButtons = 14;
        int fieldH = 20;
        int btnH = 20;
        createProfileDlgX = dlgX;
        createProfileDlgY = dlgY;
        createProfileDlgW = dlgW;

        int titleBaseline = dlgY + dlgInnerPad;
        int row1Y = titleBaseline + this.font.lineHeight + dlgGapAfterTitle;
        int btnY = row1Y + fieldH + dlgGapFieldsToButtons;
        createProfileDlgH = btnY - dlgY + btnH + dlgInnerPad;

        int fieldW = dlgW - 2 * dlgInnerPad;
        dlgCreateProfileNameField =
                new EditBox(this.font, dlgX + dlgInnerPad, row1Y, fieldW, fieldH, Component.literal("nprof"));
        dlgCreateProfileNameField.setMaxLength(64);
        dlgCreateProfileNameField.setValue("New Profile");
        dlgCreateProfileNameField.setHint(Component.literal("Profile name"));
        addRenderableWidget(dlgCreateProfileNameField);

        dlgCreateProfileOkButton =
                flatButton(
                        Component.literal("Create"),
                        () -> submitCreateProfileDialog(mgr),
                        dlgX + dlgInnerPad,
                        btnY,
                        76,
                        btnH);
        addRenderableWidget(dlgCreateProfileOkButton);

        dlgCreateProfileCancelButton =
                flatButton(
                        Component.literal("Cancel"),
                        this::closeCreateProfileDialog,
                        dlgX + dlgW - dlgInnerPad - 76,
                        btnY,
                        76,
                        btnH);
        addRenderableWidget(dlgCreateProfileCancelButton);
    }

    private void buildImportExportChoiceDialog() {
        int dlgW = Math.min(360, Math.max(240, contentW() - 24));
        int cx = contentCX();
        int dlgX = cx - dlgW / 2;
        int dlgY = bodyY() + 20;
        int pad = 12;
        int gap = 8;
        int btnH = 20;
        importExportDlgX = dlgX;
        importExportDlgY = dlgY;
        importExportDlgW = dlgW;
        String title = importExportChoiceIsImport ? "Import" : "Export";
        int titleBaseline = dlgY + pad;
        int rowY = titleBaseline + this.font.lineHeight + 12;
        int btnW = dlgW - pad * 2;
        importExportProfilesOnlyBtn =
                flatButton(
                        Component.literal(title + " Profiles Only"),
                        () -> {
                            importExportChoiceDialogOpen = false;
                            if (importExportChoiceIsImport) {
                                runImportProfilesDialog(false);
                            } else {
                                runExportProfilesDialog(false);
                            }
                            init();
                        },
                        dlgX + pad,
                        rowY,
                        btnW,
                        btnH);
        addRenderableWidget(importExportProfilesOnlyBtn);
        rowY += btnH + gap;
        importExportProfilesAndSettingsBtn =
                flatButton(
                        Component.literal(title + " Profiles + Settings"),
                        () -> {
                            importExportChoiceDialogOpen = false;
                            if (importExportChoiceIsImport) {
                                runImportProfilesDialog(true);
                            } else {
                                runExportProfilesDialog(true);
                            }
                            init();
                        },
                        dlgX + pad,
                        rowY,
                        btnW,
                        btnH);
        addRenderableWidget(importExportProfilesAndSettingsBtn);
        rowY += btnH + gap + 6;
        importExportCancelBtn =
                flatButton(
                        Component.literal("Cancel"),
                        () -> {
                            importExportChoiceDialogOpen = false;
                            init();
                        },
                        dlgX + pad,
                        rowY,
                        btnW,
                        btnH);
        addRenderableWidget(importExportCancelBtn);
        importExportDlgH = rowY - dlgY + btnH + pad;
    }

    private void closeCreateProfileDialog() {
        createProfileDialogOpen = false;
        init();
    }

    private void submitCreateProfileDialog(ChatUtilitiesManager mgr) {
        if (dlgCreateProfileNameField == null) {
            return;
        }
        String name = dlgCreateProfileNameField.getValue().strip();
        if (name.isEmpty()) {
            return;
        }
        ServerProfile created = mgr.createProfileForCurrentServer(name);
        selectedProfileId = created.getId();
        activePanel = Panel.EDIT_PROFILE;
        createProfileDialogOpen = false;
        init();
    }

    private void closeNewTabDialog() {
        newTabDialogOpen = false;
        newTabDlgWindowId = null;
        init();
    }

    private void submitNewTabDialog(ChatUtilitiesManager mgr, ServerProfile p) {
        if (dlgNewTabNameField == null || newTabDlgWindowId == null) {
            return;
        }
        String name = dlgNewTabNameField.getValue().strip();
        if (name.isEmpty()) {
            return;
        }
        if (mgr.addChatWindowTab(p, newTabDlgWindowId, name)) {
            ChatWindow w = p.getWindow(newTabDlgWindowId);
            if (w != null) {
                ChatWindowTab last = w.getTabs().get(w.getTabCount() - 1);
                menuWindowTabId.put(newTabDlgWindowId, last.getId());
            }
            newTabDialogOpen = false;
            newTabDlgWindowId = null;
            init();
        }
    }

    private void buildRenameTabDialog(ChatUtilitiesManager mgr, ServerProfile p) {
        if (renameTabDlgWindowId == null
                || renameTabDlgTabId == null
                || p.getWindow(renameTabDlgWindowId) == null) {
            return;
        }
        ChatWindowTab tab = p.getWindow(renameTabDlgWindowId).getTabById(renameTabDlgTabId);
        if (tab == null) {
            return;
        }
        int dlgW = Math.min(320, Math.max(220, contentW() - 24));
        int cx = contentCX();
        int dlgX = cx - dlgW / 2;
        int dlgY = bodyY() + 20;
        int dlgInnerPad = 12;
        int dlgGapAfterTitle = 10;
        int dlgGapFieldsToButtons = 14;
        int fieldH = 20;
        int btnH = 20;
        renameTabDlgX = dlgX;
        renameTabDlgY = dlgY;
        renameTabDlgW = dlgW;

        int titleBaseline = dlgY + dlgInnerPad;
        int row1Y = titleBaseline + this.font.lineHeight + dlgGapAfterTitle;
        int btnY = row1Y + fieldH + dlgGapFieldsToButtons;
        renameTabDlgH = btnY - dlgY + btnH + dlgInnerPad;

        int fieldW = dlgW - 2 * dlgInnerPad;
        dlgRenameTabNameField =
                new EditBox(this.font, dlgX + dlgInnerPad, row1Y, fieldW, fieldH, Component.literal("rtab"));
        dlgRenameTabNameField.setMaxLength(64);
        dlgRenameTabNameField.setValue(tab.getDisplayName());
        dlgRenameTabNameField.setHint(Component.literal("Tab name"));
        addRenderableWidget(dlgRenameTabNameField);

        dlgRenameTabOkButton =
                flatButton(
                        Component.literal("Rename"),
                        () -> submitRenameTabDialog(mgr, p),
                        dlgX + dlgInnerPad,
                        btnY,
                        76,
                        btnH);
        addRenderableWidget(dlgRenameTabOkButton);

        dlgRenameTabCancelButton =
                flatButton(
                        Component.literal("Cancel"),
                        () -> closeRenameTabDialog(),
                        dlgX + dlgW - dlgInnerPad - 76,
                        btnY,
                        76,
                        btnH);
        addRenderableWidget(dlgRenameTabCancelButton);
    }

    private void closeRenameTabDialog() {
        renameTabDialogOpen = false;
        renameTabDlgWindowId = null;
        renameTabDlgTabId = null;
        init();
    }

    private void submitRenameTabDialog(ChatUtilitiesManager mgr, ServerProfile p) {
        if (dlgRenameTabNameField == null || renameTabDlgWindowId == null || renameTabDlgTabId == null) {
            return;
        }
        String name = dlgRenameTabNameField.getValue().strip();
        if (name.isEmpty()) {
            return;
        }
        if (mgr.renameChatWindowTab(p, renameTabDlgWindowId, renameTabDlgTabId, name)) {
            renameTabDialogOpen = false;
            renameTabDlgWindowId = null;
            renameTabDlgTabId = null;
            init();
        }
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
            ChatWindow renamed = p.getWindow(newId);
            if (renamed != null) {
                for (ChatWindowTab tab : renamed.getTabs()) {
                    String ok = windowTabPatScrollKey(renameDlgOldId, tab.getId());
                    String nk = windowTabPatScrollKey(newId, tab.getId());
                    if (windowTabPatScroll.containsKey(ok)) {
                        windowTabPatScroll.put(nk, windowTabPatScroll.remove(ok));
                    }
                }
            }
            String sel = menuWindowTabId.remove(renameDlgOldId);
            if (sel != null) {
                menuWindowTabId.put(newId, sel);
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
        if (p == null) {
            chatWindowsShowScrollbar = false;
            return;
        }
        pendingPatternEdits.clear();
        pendingNewPatterns.clear();
        winChromeRects.clear();
        menuTabRects.clear();
        menuDragWindowId = null;
        menuDragTabId = null;
        menuDragFromIndex = -1;
        menuDragHoverIndex = -1;
        menuDragPressX = 0;
        menuDragPressY = 0;
        menuDragDidMove = false;
        ChatUtilitiesManager mgr = ChatUtilitiesManager.get();

        int fx = contentLeft();
        final String pid = selectedProfileId;

        List<String> winIds = validWindowIds(p);
        if (p.getId().equals(menuExpandedChatWindowsProfileId)) {
            expandedWindowIds.addAll(menuExpandedChatWindowsPersisted);
        }
        expandedWindowIds.removeIf(w -> p.getWindow(w) == null);

        int listTop = chatWindowsScrollViewportTop();
        int listBottom = chatWindowsScrollViewportBottom();
        chatWindowsListViewportHeight = Math.max(0, listBottom - listTop);
        chatWindowsTotalListHeight = 0;
        for (String wid : winIds) {
            chatWindowsTotalListHeight += chatWindowBlockHeight(p, wid);
        }
        chatWindowsShowScrollbar =
                chatWindowsTotalListHeight > chatWindowsListViewportHeight && !winIds.isEmpty();
        chatWindowsListScrollPixels = Mth.clamp(chatWindowsListScrollPixels, 0, chatWindowsMaxContentScroll());
        int fW = contentW() - scrollbarReserve(chatWindowsShowScrollbar);
        int yLogical = listTop;

        int rowW = fW;
        int gapBtn = 3;
        int removeW = 118;
        int visW = 72;
        int renameW = 68;
        int rightBlockW = visW + renameW + removeW + 3 * gapBtn;
        int toggleW = Math.max(48, rowW - rightBlockW);

        // Map the stored pixel scroll to the first visible block index so removed() persistence stays reasonable.
        int accumBefore = 0;
        winScroll = 0;
        for (int j = 0; j < winIds.size(); j++) {
            int bh = chatWindowBlockHeight(p, winIds.get(j));
            if (accumBefore + bh > chatWindowsListScrollPixels) {
                winScroll = j;
                break;
            }
            accumBefore += bh;
            winScroll = j + 1;
        }
        winScroll = Mth.clamp(winScroll, 0, Math.max(0, winIds.size() - 1));

        for (int i = 0; i < winIds.size(); i++) {
            String wid = winIds.get(i);
            int blockH = chatWindowBlockHeight(p, wid);
            int y = yLogical - chatWindowsListScrollPixels;
            if (y + blockH < listTop - 40) {
                yLogical += blockH;
                continue;
            }
            if (y > listBottom + 40) {
                break;
            }
            ChatWindow wObj = p.getWindow(wid);
            if (wObj == null) {
                yLogical += blockH;
                continue;
            }
            boolean expanded = expandedWindowIds.contains(wid);
            String headText = (expanded ? UI_BRANCH_EXPANDED + " " : UI_BRANCH_COLLAPSED + " ") + wid;
            headText = truncateToWidth(headText, toggleW - 10);

            final String fwid = wid;
            int rowTop = y;
            addChatWindowsScrollClipWidget(flatButton(Component.literal(headText), () -> {
                if (expandedWindowIds.contains(fwid)) {
                    expandedWindowIds.remove(fwid);
                } else {
                    expandedWindowIds.add(fwid);
                }
                persistMenuExpandedChatWindows(p);
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
            addChatWindowsScrollClipWidget(visBtn);
            bx += visW + gapBtn;
            addChatWindowsScrollClipWidget(flatButton(
                    Component.literal(CHAT_WIN_SYMBOL_RENAME + " Rename"),
                    () -> {
                        createWinDialogOpen = false;
                        renameTabDialogOpen = false;
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
            addChatWindowsScrollClipWidget(flatButtonDestructive(
                    Component.literal(removeArmed ? "✕ Confirm Removal" : "✕ Remove"),
                    () -> {
                        long t = System.currentTimeMillis();
                        if (removeWindowConfirmDeadlines.getOrDefault(fwid, 0L) > t) {
                            removeWindowConfirmDeadlines.remove(fwid);
                            mgr.removeWindow(p, fwid);
                            expandedWindowIds.remove(fwid);
                            removePatScrollKeysForWindow(fwid);
                            menuWindowTabId.remove(fwid);
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
                int cs = chatWindowsListScrollPixels;
                winChromeRects.add(
                        new WinChromeRect(fx - 4, rowTop - 4 + cs, fx + rowW + 4, y + 4 + cs, true));
                y += WIN_EXPANDED_TAIL_GAP;
            }
            yLogical += blockH;
        }

        int footY = footerY();
        int createBtnW = 124;
        int footerBtnGap = 6;
        int adjustBtnW = 130;
        addChatWindowsNonClipWidget(flatButtonPositive(Component.literal("+ Create Window"), () -> {
            renameDialogOpen = false;
            renameDlgOldId = null;
            newTabDialogOpen = false;
            renameTabDialogOpen = false;
            createWinDialogOpen = true;
            init();
        }, contentLeft(), footY, createBtnW, 20));
        addChatWindowsNonClipWidget(flatButton(ChatUtilitiesScreenLayout.BUTTON_ADJUST_LAYOUT, () -> {
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
        if (newTabDialogOpen) {
            buildNewTabDialog(mgr, p);
        }
        if (renameTabDialogOpen) {
            buildRenameTabDialog(mgr, p);
        }
    }

    // ── Chat Actions panel (ignore + play sound + color highlight) ─────────────

    /** Max number of pattern groups listed before the pager scrolls. */
    private static final int ACTION_GROUP_PAGE = 6;

    private static ChatActionEffect.Type cycleChatActionType(ChatActionEffect.Type t) {
        return switch (t) {
            case IGNORE -> ChatActionEffect.Type.PLAY_SOUND;
            case PLAY_SOUND -> ChatActionEffect.Type.COLOR_HIGHLIGHT;
            case COLOR_HIGHLIGHT -> ChatActionEffect.Type.TEXT_REPLACEMENT;
            case TEXT_REPLACEMENT -> ChatActionEffect.Type.AUTO_RESPONSE;
            case AUTO_RESPONSE -> ChatActionEffect.Type.IGNORE;
        };
    }

    private static Component chatEffectTypeButtonCaption(ChatActionEffect.Type t) {
        String key =
                switch (t) {
                    case IGNORE -> "chat-utilities.chat_actions.type.ignore";
                    case PLAY_SOUND -> "chat-utilities.chat_actions.type.play_sound";
                    case COLOR_HIGHLIGHT -> "chat-utilities.chat_actions.type.color_highlight";
                    case TEXT_REPLACEMENT -> "chat-utilities.chat_actions.type.text_replacement";
                    case AUTO_RESPONSE -> "chat-utilities.chat_actions.type.auto_response";
                };
        return Component.translatable(key);
    }

    private static String chatActionGroupPreview(ChatActionGroup grp) {
        if (grp == null) {
            return "<pattern> -> <effects>";
        }
        String pat = grp.getPatternSource() == null ? "" : grp.getPatternSource().strip();
        if (pat.isEmpty()) {
            pat = "<pattern>";
        }
        if (pat.length() > 36) {
            pat = pat.substring(0, 36) + "…";
        }
        StringBuilder b = new StringBuilder();
        b.append(pat);
        List<ChatActionEffect> effs = grp.getEffects();
        if (effs == null || effs.isEmpty()) {
            b.append(" -> <no effects>");
            return b.toString();
        }
        for (ChatActionEffect e : effs) {
            b.append(" -> ").append(effectPreviewName(e != null ? e.getType() : null));
        }
        return b.toString();
    }

    private static String effectPreviewName(ChatActionEffect.Type type) {
        if (type == null) {
            return "Ignore";
        }
        return switch (type) {
            case IGNORE -> "Ignore";
            case PLAY_SOUND -> "Play Sound";
            case COLOR_HIGHLIGHT -> "Color Highlight";
            case TEXT_REPLACEMENT -> "Text Replacement";
            case AUTO_RESPONSE -> "Auto Response";
        };
    }

    private void clearPendingChatHighlightPickState() {
        chatHighlightPickProfileId = null;
        chatHighlightPickGroupIndex = -1;
        chatHighlightPickEffectIndex = -1;
    }

    private void clearPendingChatActionTypePickState() {
        chatActionTypePickProfileId = null;
        chatActionTypePickGroupIndex = -1;
        chatActionTypePickEffectIndex = -1;
        chatActionTypeDropdownOpen = false;
        chatActionTypeDropX = 0;
        chatActionTypeDropY = 0;
        chatActionTypeDropW = 0;
        chatActionTypeDropH = 0;
        clearSettingsFormatDropdown();
        clearSettingsUnreadBadgeStyleDropdown();
    }

    private void openChatActionTypeDropdown(ServerProfile prof, int groupIndex, int effectIndex, int anchorX, int anchorY, int anchorW) {
        clearPendingChatActionTypePickState();
        chatActionTypePickProfileId = prof != null ? prof.getId() : null;
        chatActionTypePickGroupIndex = groupIndex;
        chatActionTypePickEffectIndex = effectIndex;
        int rowH = 18;
        int pad = 6;
        int widest = 0;
        for (ChatActionEffect.Type t : ChatActionEffect.Type.values()) {
            widest = Math.max(widest, this.font.width(chatEffectTypeButtonCaption(t)));
        }
        int w = Math.max(anchorW, widest + pad * 2);
        int h = ChatActionEffect.Type.values().length * rowH;
        int x = anchorX;
        int y = anchorY + rowH + 2;
        // Keep inside content panel.
        int maxX = contentRight() - 4;
        x = Math.min(x, maxX - w);
        x = Math.max(x, contentLeft() + 4);
        int maxY = footerY() - 6;
        if (y + h > maxY) {
            y = Math.max(bodyY(), anchorY - h - 2);
        }
        chatActionTypeDropX = x;
        chatActionTypeDropY = y;
        chatActionTypeDropW = w;
        chatActionTypeDropH = h;
        chatActionTypeDropdownOpen = true;
    }

    private void renderChatActionTypeDropdownOnTop(GuiGraphics g, int mouseX, int mouseY) {
        if (!chatActionTypeDropdownOpen || activePanel != Panel.CHAT_ACTIONS) {
            return;
        }
        int x = chatActionTypeDropX;
        int y = chatActionTypeDropY;
        int w = chatActionTypeDropW;
        int h = chatActionTypeDropH;
        int rowH = 18;
        g.fill(x, y, x + w, y + h, 0xF0101012);
        g.renderOutline(x, y, w, h, 0xFF2C2C3A);
        ChatActionEffect.Type[] types = ChatActionEffect.Type.values();
        for (int i = 0; i < types.length; i++) {
            int ry = y + i * rowH;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + rowH;
            if (hover) {
                g.fill(x + 1, ry, x + w - 1, ry + rowH, 0x203A6AC8);
            }
            Component cap = chatEffectTypeButtonCaption(types[i]);
            g.drawString(this.font, cap, x + 6, ry + (rowH - 8) / 2, 0xFFE0E0E8, false);
        }
    }

    private void openChatHighlightColorPicker(ServerProfile prof, int groupIndex, int effectIndex) {
        clearPendingChatHighlightPickState();
        chatHighlightPickProfileId = prof.getId();
        chatHighlightPickGroupIndex = groupIndex;
        chatHighlightPickEffectIndex = effectIndex;
        int initialRgb;
        boolean b0, i0, u0, s0, o0;
        if (groupIndex >= 0 && effectIndex >= 0) {
            ChatActionEffect eff =
                    prof.getChatActionGroups().get(groupIndex).getEffects().get(effectIndex);
            initialRgb = eff.getHighlightColorRgb();
            b0 = eff.isHighlightBold();
            i0 = eff.isHighlightItalic();
            u0 = eff.isHighlightUnderlined();
            s0 = eff.isHighlightStrikethrough();
            o0 = eff.isHighlightObfuscated();
        } else {
            initialRgb = chatActionNewHighlightRgb;
            b0 = chatActionNewHlBold;
            i0 = chatActionNewHlItalic;
            u0 = chatActionNewHlUnderlined;
            s0 = chatActionNewHlStrikethrough;
            o0 = chatActionNewHlObfuscated;
        }
        modColorPickerOverlay =
                ModPrimaryColorPickerOverlay.createChatHighlightRgb(
                        initialRgb, b0, i0, u0, s0, o0, this::applyChatHighlightPickFromPicker);
        afterOpenColorPicker();
    }

    private void applyChatHighlightPickFromPicker(ModPrimaryColorPickerOverlay.ChatHighlightPick pick) {
        ChatUtilitiesManager mgr = ChatUtilitiesManager.get();
        ServerProfile prof =
                chatHighlightPickProfileId != null ? mgr.getProfile(chatHighlightPickProfileId) : profile();
        if (prof == null) {
            clearPendingChatHighlightPickState();
            return;
        }
        int rgb = pick.rgb() & 0xFFFFFF;
        if (chatHighlightPickGroupIndex >= 0 && chatHighlightPickEffectIndex >= 0) {
            mgr.setChatActionHighlightStyle(
                    prof,
                    chatHighlightPickGroupIndex,
                    chatHighlightPickEffectIndex,
                    rgb,
                    pick.bold(),
                    pick.italic(),
                    pick.underlined(),
                    pick.strikethrough(),
                    pick.obfuscated());
        } else {
            chatActionNewHighlightRgb = rgb;
            chatActionNewHlBold = pick.bold();
            chatActionNewHlItalic = pick.italic();
            chatActionNewHlUnderlined = pick.underlined();
            chatActionNewHlStrikethrough = pick.strikethrough();
            chatActionNewHlObfuscated = pick.obfuscated();
        }
        clearPendingChatHighlightPickState();
    }

    private AbstractWidget chatHighlightPickColorRowWidget(int x, int y, int w, int h, int rgb, Runnable onOpenPicker) {
        return new AbstractWidget(x, y, w, h, Component.empty()) {
            @Override
            public void onClick(MouseButtonEvent event, boolean dbl) {
                onOpenPicker.run();
            }

            @Override
            protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                boolean hov = this.isHovered();
                boolean act = this.active;
                int bg = !act ? 0x25000000 : hov ? 0x55FFFFFF : 0x35000000;
                int outline = !act ? 0x25FFFFFF : hov ? 0x70FFFFFF : 0x40FFFFFF;
                g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
                g.renderOutline(getX(), getY(), getWidth(), getHeight(), outline);
                int sw = rgb | 0xFF000000;
                int sx = getX() + 6;
                int sy = getY() + (getHeight() - 14) / 2;
                g.fill(sx, sy, sx + 14, sy + 14, sw);
                g.renderOutline(sx, sy, 14, 14, 0xFFAAAAAA);
                String cap = I18n.get("chat-utilities.chat_actions.color_highlight.pick");
                g.drawString(
                        Minecraft.getInstance().font,
                        cap,
                        sx + 18,
                        getY() + (getHeight() - 8) / 2,
                        0xFFBBBBCC,
                        false);
            }

            @Override
            public void updateWidgetNarration(NarrationElementOutput n) {
                defaultButtonNarrationText(n);
            }
        };
    }

    private void buildChatActionsWidgets() {
        ServerProfile p = profile();
        if (p == null) {
            return;
        }
        ChatUtilitiesManager mgr = ChatUtilitiesManager.get();

        int fx = contentLeft();
        int y = bodyY();
        soundField = null;
        EditBox targetField = null;

        int g = 3;
        int fh = CHAT_ACTION_FIELD_H;
        int patW = CHAT_ACTION_ADD_PAT_W;
        int actW = CHAT_ACTION_ACTION_BTN_W;
        int addW = CHAT_ACTION_ADD_BTN_W;
        int testW = Mth.clamp(this.font.width("🔊 Test Sound") + 12, 56, 120);
        int sndW = CHAT_ACTION_SOUND_FIELD_W;
        int tgtW = CHAT_ACTION_SOUND_FIELD_W;

        int minWPlay = patW + g + actW + g + sndW + g + testW + g + addW;
        int minWHighlight = patW + g + actW + g + CHAT_ACTION_HIGHLIGHT_PICK_W + g + addW;
        int minWTarget = patW + g + actW + g + tgtW + g + addW;
        int baseFormW = Math.min(CHAT_ACTIONS_FORM_CAP, contentW());
        int listRowFormW =
                Math.min(contentW(), Math.max(baseFormW, Math.max(minWPlay, minWHighlight)));

        int bx = fx + patW + g;
        patternField = new EditBox(this.font, fx, y, patW, fh, Component.literal("capat"));
        patternField.setMaxLength(2048);
        patternField.setHint(ChatUtilitiesScreenLayout.PATTERN_INPUT_HINT);
        patternField.setValue(chatActionNewPatternDraft);
        patternField.setResponder(v -> chatActionNewPatternDraft = v);
        addRenderableWidget(patternField);
        int typeBtnX = bx;
        int typeBtnY = y;
        addRenderableWidget(
                flatButton(
                        Component.literal("⚙ ").append(chatEffectTypeButtonCaption(chatActionNewType)),
                        () -> {
                            openChatActionTypeDropdown(p, -1, -1, typeBtnX, typeBtnY, actW);
                        },
                        bx,
                        y,
                        actW,
                        fh));
        bx += actW + g;

        if (chatActionNewType == ChatActionEffect.Type.PLAY_SOUND) {
            soundField = new EditBox(this.font, bx, y, sndW, fh, Component.literal("casnd"));
            soundField.setMaxLength(256);
            soundField.setHint(Component.literal("Sound id…"));
            addRenderableWidget(soundField);
            bx += sndW + g;
            addRenderableWidget(
                    flatButton(
                            Component.literal("🔊 Test Sound"),
                            () -> ChatUtilitiesManager.parseSoundId(soundField.getValue())
                                    .filter(ChatUtilitiesManager::isRegisteredSound)
                                    .ifPresent(ChatUtilitiesManager::playSoundPreview),
                            bx,
                            y,
                            testW,
                            fh));
            bx += testW + g;
            addRenderableWidget(
                    flatButtonPositive(
                            Component.literal("＋ Add Action"),
                            () -> {
                                try {
                                    mgr.addChatAction(
                                            p,
                                            chatActionNewType,
                                            patternField.getValue(),
                                            soundField.getValue());
                                } catch (IllegalArgumentException ignored) {
                                }
                                chatActionNewPatternDraft = "";
                                patternField.setValue("");
                                if (soundField != null) {
                                    soundField.setValue("");
                                }
                                init();
                            },
                            bx,
                            y,
                            addW,
                            fh));
        } else if (chatActionNewType == ChatActionEffect.Type.TEXT_REPLACEMENT
                || chatActionNewType == ChatActionEffect.Type.AUTO_RESPONSE) {
            targetField = new EditBox(this.font, bx, y, tgtW, fh, Component.literal("catgt"));
            targetField.setMaxLength(2048);
            targetField.setHint(
                    Component.literal(
                            chatActionNewType == ChatActionEffect.Type.AUTO_RESPONSE ? "Response text…" : "Replacement text…"));
            addRenderableWidget(targetField);
            bx += tgtW + g;
            EditBox finalTargetField = targetField;
            addRenderableWidget(
                    flatButtonPositive(
                            Component.literal("＋ Add Action"),
                            () -> {
                                try {
                                    mgr.addChatAction(
                                            p,
                                            chatActionNewType,
                                            patternField.getValue(),
                                            finalTargetField.getValue());
                                } catch (IllegalArgumentException ignored) {
                                }
                                chatActionNewPatternDraft = "";
                                patternField.setValue("");
                                finalTargetField.setValue("");
                                init();
                            },
                            bx,
                            y,
                            addW,
                            fh));
        } else if (chatActionNewType == ChatActionEffect.Type.COLOR_HIGHLIGHT) {
            addRenderableWidget(
                    chatHighlightPickColorRowWidget(
                            bx,
                            y,
                            CHAT_ACTION_HIGHLIGHT_PICK_W,
                            fh,
                            chatActionNewHighlightRgb,
                            () -> openChatHighlightColorPicker(p, -1, -1)));
            bx += CHAT_ACTION_HIGHLIGHT_PICK_W + g;
            addRenderableWidget(
                    flatButtonPositive(
                            Component.literal("＋ Add Action"),
                            () -> {
                                try {
                                    mgr.addChatAction(
                                            p,
                                            chatActionNewType,
                                            patternField.getValue(),
                                            "",
                                            chatActionNewHighlightRgb,
                                            chatActionNewHlBold,
                                            chatActionNewHlItalic,
                                            chatActionNewHlUnderlined,
                                            chatActionNewHlStrikethrough,
                                            chatActionNewHlObfuscated);
                                } catch (IllegalArgumentException ignored) {
                                }
                                chatActionNewPatternDraft = "";
                                patternField.setValue("");
                                init();
                            },
                            bx,
                            y,
                            addW,
                            fh));
        } else {
            addRenderableWidget(
                    flatButtonPositive(
                            Component.literal("＋ Add Action"),
                            () -> {
                                try {
                                    mgr.addChatAction(
                                            p,
                                            chatActionNewType,
                                            patternField.getValue(),
                                            "");
                                } catch (IllegalArgumentException ignored) {
                                }
                                chatActionNewPatternDraft = "";
                                patternField.setValue("");
                                init();
                            },
                            bx,
                            y,
                            addW,
                            fh));
        }
        y += fh + 14;

        List<ChatActionGroup> groups = p.getChatActionGroups();
        if (!chatActionsCollapseSeeded) {
            collapsedChatActionGroups.clear();
            for (int i = 0; i < groups.size(); i++) {
                collapsedChatActionGroups.add(i);
            }
            chatActionsCollapseSeeded = true;
        }
        int rMax = Math.max(0, groups.size() - ACTION_GROUP_PAGE);
        actionScroll = Math.min(actionScroll, rMax);
        int gEnd = Math.min(actionScroll + ACTION_GROUP_PAGE, groups.size());
        int ruleXW = 24;
        int ruleGap = 4;
        int collapseW = 18;
        int typeBtnW = CHAT_ACTION_ACTION_BTN_W;
        int ruleTestW = Mth.clamp(this.font.width("🔊 Test Sound") + 12, 56, 120);
        int rfh = CHAT_ACTION_FIELD_H;
        int ruleInner = listRowFormW - ruleXW - ruleGap;
        for (int gi = actionScroll; gi < gEnd; gi++) {
            final int gix = gi;
            ChatActionGroup grp = groups.get(gi);
            boolean collapsed = collapsedChatActionGroups.contains(gi);
            int groupChromeTop = y;
            int rx = fx;
            int groupPatW = Math.max(40, ruleInner - ruleXW - ruleGap - collapseW - ruleGap);
            EditBox groupPatEb =
                    new EditBox(this.font, rx, y, groupPatW, rfh, Component.literal("cagpat" + gi));
            groupPatEb.setMaxLength(2048);
            groupPatEb.setValue(grp.getPatternSource());
            groupPatEb.setHint(ChatUtilitiesScreenLayout.PATTERN_INPUT_HINT);
            addRenderableWidget(groupPatEb);
            rx += groupPatW + ruleGap;
            addRenderableWidget(
                    flatButtonDestructive(
                            Component.literal("✕"),
                            () -> {
                                mgr.removeChatActionGroupAt(p, gix);
                                init();
                            },
                            rx,
                            y,
                            ruleXW,
                            rfh));
            pendingChatGroupPatterns.add(new PendingChatGroupPattern(groupPatEb, p, gix));
            rx += ruleXW + ruleGap;
            addRenderableWidget(
                    flatButton(
                            Component.literal(collapsed ? "▸" : "▾"),
                            () -> {
                                if (collapsedChatActionGroups.contains(gix)) {
                                    collapsedChatActionGroups.remove(gix);
                                } else {
                                    collapsedChatActionGroups.add(gix);
                                }
                                init();
                            },
                            rx,
                            y,
                            collapseW,
                            rfh));
            y += rfh + 10;

            if (collapsed) {
                String preview = chatActionGroupPreview(grp);
                final String previewFinal = preview;
                addRenderableWidget(
                        new AbstractWidget(fx, y, listRowFormW, 18, Component.empty()) {
                            @Override
                            protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                                int bg = this.isHovered() ? 0x28000000 : 0x18000000;
                                g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
                                g.drawString(
                                        Minecraft.getInstance().font,
                                        previewFinal,
                                        getX() + 4,
                                        getY() + (getHeight() - 8) / 2,
                                        0xFFBBBBCC,
                                        false);
                            }

                            @Override
                            public void updateWidgetNarration(NarrationElementOutput n) {
                                defaultButtonNarrationText(n);
                            }
                        });
                y += 18 + 16;
                winChromeRects.add(
                        new WinChromeRect(fx - 4, groupChromeTop - 4, fx + listRowFormW + 4, y + 4));
                y += 10;
                continue;
            }

            List<ChatActionEffect> effects = grp.getEffects();
            for (int ei = 0; ei < effects.size(); ei++) {
                final int eix = ei;
                ChatActionEffect eff = effects.get(ei);
                rx = fx;
                if (eff.getType() == ChatActionEffect.Type.IGNORE) {
                        int ebX = rx;
                        int ebY = y;
                        addRenderableWidget(
                                flatButton(
                                        chatEffectTypeButtonCaption(eff.getType()),
                                        () -> {
                                            openChatActionTypeDropdown(p, gix, eix, ebX, ebY, typeBtnW);
                                        },
                                        rx,
                                        y,
                                        typeBtnW,
                                        rfh));
                    rx += typeBtnW + ruleGap;
                    addRenderableWidget(
                            flatButtonDestructive(
                                    Component.literal("✕"),
                                    () -> {
                                        mgr.removeChatActionEffectAt(p, gix, eix);
                                        init();
                                    },
                                    rx,
                                    y,
                                    ruleXW,
                                    rfh));
                } else if (eff.getType() == ChatActionEffect.Type.COLOR_HIGHLIGHT) {
                        int ebX = rx;
                        int ebY = y;
                        addRenderableWidget(
                                flatButton(
                                        Component.literal("⚙ ").append(chatEffectTypeButtonCaption(eff.getType())),
                                        () -> {
                                            openChatActionTypeDropdown(p, gix, eix, ebX, ebY, typeBtnW);
                                        },
                                        rx,
                                        y,
                                        typeBtnW,
                                        rfh));
                    rx += typeBtnW + ruleGap;
                    addRenderableWidget(
                            chatHighlightPickColorRowWidget(
                                    rx,
                                    y,
                                    CHAT_ACTION_HIGHLIGHT_PICK_W,
                                    rfh,
                                    eff.getHighlightColorRgb(),
                                    () -> openChatHighlightColorPicker(p, gix, eix)));
                    rx += CHAT_ACTION_HIGHLIGHT_PICK_W + ruleGap;
                    addRenderableWidget(
                            flatButtonDestructive(
                                    Component.literal("✕"),
                                    () -> {
                                        mgr.removeChatActionEffectAt(p, gix, eix);
                                        init();
                                    },
                                    rx,
                                    y,
                                    ruleXW,
                                    rfh));
                } else if (eff.getType() == ChatActionEffect.Type.TEXT_REPLACEMENT
                        || eff.getType() == ChatActionEffect.Type.AUTO_RESPONSE) {
                    int ruleTgtW = Math.min(220, Math.max(90, ruleInner - typeBtnW - ruleXW - ruleGap * 2));
                    EditBox tgtEb =
                            new EditBox(this.font, rx, y, ruleTgtW, rfh, Component.literal("catgt" + gi + "_" + ei));
                    tgtEb.setMaxLength(2048);
                    tgtEb.setValue(eff.getTargetText());
                    tgtEb.setHint(
                            Component.literal(
                                    eff.getType() == ChatActionEffect.Type.AUTO_RESPONSE ? "Response text…" : "Replacement text…"));
                    addRenderableWidget(tgtEb);
                    rx += ruleTgtW + ruleGap;

                    int ebX = rx;
                    int ebY = y;
                    addRenderableWidget(
                            flatButton(
                                    Component.literal("⚙ ").append(chatEffectTypeButtonCaption(eff.getType())),
                                    () -> openChatActionTypeDropdown(p, gix, eix, ebX, ebY, typeBtnW),
                                    rx,
                                    y,
                                    typeBtnW,
                                    rfh));
                    rx += typeBtnW + ruleGap;

                    addRenderableWidget(
                            flatButtonDestructive(
                                    Component.literal("✕"),
                                    () -> {
                                        mgr.removeChatActionEffectAt(p, gix, eix);
                                        init();
                                    },
                                    rx,
                                    y,
                                    ruleXW,
                                    rfh));
                    pendingChatEffectEdits.add(new PendingChatEffectEdit(null, tgtEb, p, gix, eix));
                } else {
                    int ruleSndW =
                                Math.min(160, Math.max(72, (ruleInner - typeBtnW - ruleTestW - ruleGap * 3) / 2));
                    Identifier sid = ChatUtilitiesManager.parseSoundId(eff.getSoundId()).orElse(null);
                    String sndDisp = sid != null ? sid.toString() : eff.getSoundId();
                    EditBox sndEb =
                            new EditBox(this.font, rx, y, ruleSndW, rfh, Component.literal("caeff" + gi + "_" + ei));
                    sndEb.setMaxLength(256);
                    sndEb.setValue(sndDisp);
                    sndEb.setHint(Component.literal("Sound id…"));
                    addRenderableWidget(sndEb);
                    rx += ruleSndW + ruleGap;

                        int ebX = rx;
                        int ebY = y;
                    addRenderableWidget(
                            flatButton(
                                    Component.literal("⚙ ").append(chatEffectTypeButtonCaption(eff.getType())),
                                    () -> {
                                            openChatActionTypeDropdown(p, gix, eix, ebX, ebY, typeBtnW);
                                    },
                                    rx,
                                    y,
                                    typeBtnW,
                                    rfh));
                    rx += typeBtnW + ruleGap;

                    addRenderableWidget(
                            flatButton(
                                    Component.literal("🔊 Test Sound"),
                                    () -> ChatUtilitiesManager.parseSoundId(sndEb.getValue())
                                            .filter(ChatUtilitiesManager::isRegisteredSound)
                                            .ifPresent(ChatUtilitiesManager::playSoundPreview),
                                    rx,
                                    y,
                                    ruleTestW,
                                    rfh));
                    rx += ruleTestW + ruleGap;

                    addRenderableWidget(
                            flatButtonDestructive(
                                    Component.literal("✕"),
                                    () -> {
                                        mgr.removeChatActionEffectAt(p, gix, eix);
                                        init();
                                    },
                                    rx,
                                    y,
                                    ruleXW,
                                    rfh));

                    pendingChatEffectEdits.add(new PendingChatEffectEdit(sndEb, null, p, gix, eix));
                }
                y += rfh + 6;
            }

            // Keep the "+ Add effect" control pinned to the bottom of the expanded group.
            addRenderableWidget(
                    flatButton(
                            Component.literal("+ Add effect"),
                            () -> {
                                try {
                                    mgr.addEffectToGroup(
                                            p, gix, ChatActionEffect.Type.PLAY_SOUND, "");
                                } catch (IllegalArgumentException ignored) {
                                }
                                init();
                            },
                            fx,
                            y,
                            Math.min(listRowFormW, 120),
                            rfh));
            y += rfh + 12;
            winChromeRects.add(
                    new WinChromeRect(fx - 4, groupChromeTop - 4, fx + listRowFormW + 4, y + 4));
            y += 10;
        }
        if (rMax > 0) {
            boolean up = actionScroll > 0;
            boolean down = actionScroll < rMax;
            if (up && down) {
                int half = (listRowFormW - 4) / 2;
                addRenderableWidget(
                        flatButton(
                                Component.literal("▲"),
                                () -> {
                                    actionScroll = Math.max(0, actionScroll - 1);
                                    init();
                                },
                                fx,
                                y,
                                half,
                                20));
                addRenderableWidget(
                        flatButton(
                                Component.literal("▼"),
                                () -> {
                                    actionScroll = Math.min(rMax, actionScroll + 1);
                                    init();
                                },
                                fx + half + 4,
                                y,
                                half,
                                20));
            } else if (up) {
                addRenderableWidget(
                        flatButton(
                                Component.literal("▲"),
                                () -> {
                                    actionScroll = Math.max(0, actionScroll - 1);
                                    init();
                                },
                                fx,
                                y,
                                listRowFormW,
                                20));
            } else {
                addRenderableWidget(
                        flatButton(
                                Component.literal("▼"),
                                () -> {
                                    actionScroll = Math.min(rMax, actionScroll + 1);
                                    init();
                                },
                                fx,
                                y,
                                listRowFormW,
                                20));
            }
        }
    }

    private void buildCommandAliasesWidgets() {
        ServerProfile p = profile();
        if (p == null) {
            return;
        }
        ChatUtilitiesManager mgr = ChatUtilitiesManager.get();
        int fx = contentLeft();
        int y = bodyY();
        int g = 3;
        int fh = CHAT_ACTION_FIELD_H;
        int baseFormW = Math.min(CHAT_ACTIONS_FORM_CAP, contentW());
        int addBtnW = 72;
        int rmW = 24;
        int fromW = 112;
        int addToW = baseFormW - fromW - g - addBtnW - g;

        commandAliasAddFromField = new EditBox(this.font, fx, y, fromW, fh, Component.literal("cafrom"));
        commandAliasAddFromField.setMaxLength(64);
        commandAliasAddFromField.setValue(displaySlashCommand(commandAliasNewFromDraft));
        commandAliasAddFromField.setResponder(v -> commandAliasNewFromDraft = stripSlashCommand(v));
        commandAliasAddFromField.setHint(Component.literal(I18n.get("chat-utilities.command_aliases.hint.from")));
        addRenderableWidget(commandAliasAddFromField);

        commandAliasAddToField =
                new EditBox(this.font, fx + fromW + g, y, addToW, fh, Component.literal("cato"));
        commandAliasAddToField.setMaxLength(64);
        commandAliasAddToField.setValue(displaySlashCommand(commandAliasNewToDraft));
        commandAliasAddToField.setResponder(v -> commandAliasNewToDraft = stripSlashCommand(v));
        commandAliasAddToField.setHint(Component.literal(I18n.get("chat-utilities.command_aliases.hint.to")));
        addRenderableWidget(commandAliasAddToField);

        addRenderableWidget(
                flatButtonPositive(
                        Component.literal(I18n.get("chat-utilities.command_aliases.add")),
                        () -> {
                            if (mgr.addCommandAlias(
                                    p,
                                    stripSlashCommand(commandAliasAddFromField.getValue()),
                                    stripSlashCommand(commandAliasAddToField.getValue()))) {
                                commandAliasNewFromDraft = "";
                                commandAliasNewToDraft = "";
                            }
                            init();
                        },
                        fx + fromW + g + addToW + g,
                        y,
                        addBtnW,
                        fh));
        y += fh + 8;

        List<CommandAlias> aliases = p.getCommandAliases();
        int rMax = Math.max(0, aliases.size() - COMMAND_ALIAS_PAGE);
        aliasScroll = Math.min(aliasScroll, rMax);
        int rowEnd = Math.min(aliasScroll + COMMAND_ALIAS_PAGE, aliases.size());
        for (int i = aliasScroll; i < rowEnd; i++) {
            final int idx = i;
            CommandAlias ca = aliases.get(i);
            int rowFromW = fromW;
            int rowToW = baseFormW - rowFromW - g - rmW - g;
            EditBox fromEb =
                    new EditBox(this.font, fx, y, rowFromW, fh, Component.literal("caef" + i));
            fromEb.setMaxLength(64);
            fromEb.setValue(displaySlashCommand(ca.from()));
            addRenderableWidget(fromEb);
            EditBox toEb =
                    new EditBox(this.font, fx + rowFromW + g, y, rowToW, fh, Component.literal("caet" + i));
            toEb.setMaxLength(64);
            toEb.setValue(displaySlashCommand(ca.to()));
            addRenderableWidget(toEb);
            pendingCommandAliasEdits.add(new PendingCommandAliasEdit(fromEb, toEb, p, idx));
            addRenderableWidget(
                    flatButtonDestructive(
                            Component.literal("✕"),
                            () -> {
                                mgr.removeCommandAliasAt(p, idx);
                                init();
                            },
                            fx + rowFromW + g + rowToW + g,
                            y,
                            rmW,
                            fh));
            y += fh + 4;
        }

        if (rMax > 0) {
            boolean up = aliasScroll > 0;
            boolean down = aliasScroll < rMax;
            if (up && down) {
                int half = (baseFormW - 4) / 2;
                addRenderableWidget(
                        flatButton(
                                Component.literal("▲"),
                                () -> {
                                    aliasScroll = Math.max(0, aliasScroll - 1);
                                    init();
                                },
                                fx,
                                y,
                                half,
                                20));
                addRenderableWidget(
                        flatButton(
                                Component.literal("▼"),
                                () -> {
                                    aliasScroll = Math.min(rMax, aliasScroll + 1);
                                    init();
                                },
                                fx + half + 4,
                                y,
                                half,
                                20));
            } else if (up) {
                addRenderableWidget(
                        flatButton(
                                Component.literal("▲"),
                                () -> {
                                    aliasScroll = Math.max(0, aliasScroll - 1);
                                    init();
                                },
                                fx,
                                y,
                                baseFormW,
                                20));
            } else {
                addRenderableWidget(
                        flatButton(
                                Component.literal("▼"),
                                () -> {
                                    aliasScroll = Math.min(rMax, aliasScroll + 1);
                                    init();
                                },
                                fx,
                                y,
                                baseFormW,
                                20));
            }
        }
    }

    private static boolean sameClickMouseBinding(
            ChatUtilitiesClientOptions.ClickMouseBinding a, ChatUtilitiesClientOptions.ClickMouseBinding b) {
        return a.mouseButton == b.mouseButton
                && a.requireControl == b.requireControl
                && a.requireShift == b.requireShift
                && a.requireAlt == b.requireAlt;
    }

    private boolean isSettingsRowAtBuiltInDefault(SettingsRow r) {
        return switch (r) {
            case CHECK_FOR_UPDATES -> ChatUtilitiesClientOptions.isCheckForUpdatesEnabled();
            case OPEN_MENU -> ChatUtilitiesModClient.OPEN_MENU_KEY.isDefault();
            case CHAT_PEEK -> ChatUtilitiesModClient.CHAT_PEEK_KEY.isDefault();
            case COPY_PLAIN_BIND ->
                    sameClickMouseBinding(
                            ChatUtilitiesClientOptions.getCopyPlainBinding(),
                            ChatUtilitiesClientOptions.ClickMouseBinding.defaultPlain());
            case COPY_FORMATTED_BIND ->
                    sameClickMouseBinding(
                            ChatUtilitiesClientOptions.getCopyFormattedBinding(),
                            ChatUtilitiesClientOptions.ClickMouseBinding.defaultFormatted());
            case FULLSCREEN_IMAGE_CLICK ->
                    sameClickMouseBinding(
                            ChatUtilitiesClientOptions.getFullscreenImagePreviewClickBinding(),
                            ChatUtilitiesClientOptions.ClickMouseBinding.defaultFullscreenImagePreview());
            case CLICK_TO_COPY -> ChatUtilitiesClientOptions.isClickToCopyEnabled();
            case COPY_FORMATTED_STYLE ->
                    ChatUtilitiesClientOptions.getCopyFormattedStyle()
                            == ChatUtilitiesClientOptions.CopyFormattedStyle.MINIMESSAGE;
            case SMOOTH_CHAT -> !ChatUtilitiesClientOptions.isSmoothChat();
            case SMOOTH_CHAT_DELAY_MS -> ChatUtilitiesClientOptions.getSmoothChatFadeMs() == 200;
            case SMOOTH_CHAT_BAR_OPEN_MS -> ChatUtilitiesClientOptions.getSmoothChatBarOpenMs() == 200;
            case LONGER_CHAT_HISTORY -> !ChatUtilitiesClientOptions.isLongerChatHistory();
            case CHAT_HISTORY_LIMIT ->
                    ChatUtilitiesClientOptions.getChatHistoryLimitLines()
                            == ChatUtilitiesClientOptions.CHAT_HISTORY_LIMIT_DEFAULT;
            case STACK_REPEATED_MESSAGES -> !ChatUtilitiesClientOptions.isStackRepeatedMessages();
            case STACKED_MESSAGE_COLOR ->
                    (ChatUtilitiesClientOptions.getStackedMessageColorRgb() & 0xFFFFFF)
                            == ChatUtilitiesClientOptions.STACKED_MESSAGE_COLOR_RGB_DEFAULT;
            case STACKED_MESSAGE_FORMAT ->
                    ChatUtilitiesClientOptions.getStackedMessageFormat()
                            .equals(ChatUtilitiesClientOptions.STACKED_MESSAGE_FORMAT_DEFAULT);
            case CHAT_TIMESTAMPS -> !ChatUtilitiesClientOptions.isChatTimestampsEnabled();
            case CHAT_TIMESTAMP_COLOR ->
                    (ChatUtilitiesClientOptions.getChatTimestampColorRgb() & 0xFFFFFF)
                                    == ChatUtilitiesClientOptions.CHAT_TIMESTAMP_COLOR_RGB_DEFAULT
                            && ChatUtilitiesClientOptions.getChatTimestampRecent().isEmpty();
            case CHAT_TIMESTAMP_FORMAT ->
                    ChatUtilitiesClientOptions.getChatTimestampFormatPattern()
                            .equals(ChatUtilitiesClientOptions.CHAT_TIMESTAMP_FORMAT_DEFAULT);
            case CHAT_SEARCH_BAR -> !ChatUtilitiesClientOptions.isChatSearchBarEnabled();
            case CHAT_SEARCH_BAR_POSITION ->
                    ChatUtilitiesClientOptions.getChatSearchBarPosition()
                            == ChatUtilitiesClientOptions.ChatSearchBarPosition.ABOVE_CHAT;
            case IMAGE_PREVIEW_ENABLED -> ChatUtilitiesClientOptions.isImageChatPreviewEnabled();
            case IMAGE_PREVIEW_WHITELIST -> ChatUtilitiesClientOptions.isImagePreviewWhitelistAtBuiltInDefaults();
            case IMAGE_PREVIEW_ALLOW_UNTRUSTED ->
                    !ChatUtilitiesClientOptions.isAllowUntrustedImagePreviewDomains();
            case CHAT_SYMBOL_SELECTOR -> ChatUtilitiesClientOptions.isShowChatSymbolSelector();
            case SYMBOL_PALETTE_INSERT_STYLE ->
                    ChatUtilitiesClientOptions.getSymbolPaletteInsertStyle()
                            == ChatUtilitiesClientOptions.CopyFormattedStyle.MINIMESSAGE;
            case TAB_UNREAD_BADGES -> ChatUtilitiesClientOptions.isChatWindowTabUnreadBadgesEnabled();
            case ALWAYS_SHOW_UNREAD_TABS -> ChatUtilitiesClientOptions.isAlwaysShowUnreadTabs();
            case UNREAD_BADGE_STYLE ->
                    ChatUtilitiesClientOptions.getTabUnreadBadgeStyle()
                            == ChatUtilitiesClientOptions.TabUnreadBadgeStyle.CIRCLE;
            case UNREAD_BADGE_COLOR ->
                    (ChatUtilitiesClientOptions.getTabUnreadBadgeColorRgb() & 0xFFFFFF)
                            == ChatUtilitiesClientOptions.TAB_UNREAD_BADGE_COLOR_RGB_DEFAULT;
            case CHAT_PANEL_BACKGROUND_OPACITY ->
                    ChatUtilitiesClientOptions.getChatPanelBackgroundOpacityUnfocusedPercent()
                            == ChatUtilitiesClientOptions.CHAT_PANEL_BG_OPACITY_DEFAULT;
            case CHAT_PANEL_BACKGROUND_FOCUSED_OPACITY ->
                    ChatUtilitiesClientOptions.getChatPanelBackgroundOpacityFocusedPercent()
                            == ChatUtilitiesClientOptions.CHAT_PANEL_BG_OPACITY_DEFAULT;
            case CHAT_BAR_BACKGROUND_OPACITY ->
                    ChatUtilitiesClientOptions.getChatBarBackgroundOpacityPercent()
                            == ChatUtilitiesClientOptions.CHAT_PANEL_BG_OPACITY_DEFAULT;
            case CHAT_TEXT_SHADOW -> ChatUtilitiesClientOptions.isChatTextShadow();
            case CHAT_BAR_MENU_BUTTON -> ChatUtilitiesClientOptions.isShowChatBarMenuButton();
            case MOD_PRIMARY_COLOR ->
                    (ChatUtilitiesClientOptions.getModPrimaryArgb() & 0xFFFFFF)
                                    == (ChatUtilitiesClientOptions.MOD_PRIMARY_DEFAULT_ARGB & 0xFFFFFF)
                            && !ChatUtilitiesClientOptions.isModPrimaryChroma()
                            && Math.abs(ChatUtilitiesClientOptions.getModPrimaryChromaSpeed() - 10f) < 0.01f
                            && ChatUtilitiesClientOptions.getModPrimaryRecent().isEmpty();
            case IGNORE_SELF_CHAT_ACTIONS -> ChatUtilitiesClientOptions.isIgnoreSelfInChatActions();
            case PRESERVE_VANILLA_CHAT_LOG_ON_LEAVE ->
                    ChatUtilitiesClientOptions.isPreserveVanillaChatOnDisconnect();
            case PROFILES_IMPORT_EXPORT_ROW, PROFILES_LABY_ROW -> true;
        };
    }

    private void clearSettingsFormatDropdown() {
        settingsFormatDropdownOpen = false;
        settingsFormatDropX = 0;
        settingsFormatDropY = 0;
        settingsFormatDropW = 0;
        settingsFormatDropH = 0;
    }

    private void clearSettingsUnreadBadgeStyleDropdown() {
        settingsUnreadBadgeStyleDropdownOpen = false;
        settingsUnreadBadgeStyleDropX = 0;
        settingsUnreadBadgeStyleDropY = 0;
        settingsUnreadBadgeStyleDropW = 0;
        settingsUnreadBadgeStyleDropH = 0;
    }

    private void openSettingsUnreadBadgeStyleDropdown(int anchorX, int anchorBottomY, int anchorW) {
        chatActionTypeDropdownOpen = false;
        clearSettingsFormatDropdown();
        ChatUtilitiesClientOptions.TabUnreadBadgeStyle[] vals =
                ChatUtilitiesClientOptions.TabUnreadBadgeStyle.values();
        int rowH = 18;
        int pad = 6;
        int widest = 0;
        for (ChatUtilitiesClientOptions.TabUnreadBadgeStyle s : vals) {
            widest =
                    Math.max(
                            widest,
                            this.font.width(
                                    Component.translatable(
                                            "chat-utilities.settings.unread_badge.style.value." + s.name().toLowerCase(Locale.ROOT))));
        }
        int w = Math.max(anchorW, widest + pad * 2);
        int h = vals.length * rowH;
        int x = anchorX;
        int y = anchorBottomY + 2;
        int maxX = contentRight() - 4;
        x = Math.min(x, maxX - w);
        x = Math.max(x, contentLeft() + 4);
        int maxY = footerY() - 6;
        if (y + h > maxY) {
            y = Math.max(bodyY(), anchorBottomY - h - rowH - 2);
        }
        settingsUnreadBadgeStyleDropX = x;
        settingsUnreadBadgeStyleDropY = y;
        settingsUnreadBadgeStyleDropW = w;
        settingsUnreadBadgeStyleDropH = h;
        settingsUnreadBadgeStyleDropdownOpen = true;
    }

    private void openSettingsFormatDropdown(boolean symbolPalette, int anchorX, int anchorBottomY, int anchorW) {
        chatActionTypeDropdownOpen = false;
        clearSettingsUnreadBadgeStyleDropdown();
        settingsFormatDropdownSymbolPalette = symbolPalette;
        ChatUtilitiesClientOptions.CopyFormattedStyle[] vals = ChatUtilitiesClientOptions.CopyFormattedStyle.values();
        int rowH = 18;
        int pad = 6;
        int widest = 0;
        for (ChatUtilitiesClientOptions.CopyFormattedStyle s : vals) {
            widest =
                    Math.max(
                            widest,
                            switch (s) {
                                case VANILLA -> this.font.width(
                                        Component.translatable(
                                                "chat-utilities.settings.copy_formatted_style.value.vanilla"));
                                case SECTION_SYMBOL -> {
                                    String lead =
                                            I18n.get("chat-utilities.settings.copy_formatted_style.value.section_symbol.lead");
                                    String trail =
                                            I18n.get("chat-utilities.settings.copy_formatted_style.value.section_symbol.trail");
                                    FormattedCharSequence secSeq =
                                            FormattedCharSequence.codepoint(SECTION_SIGN_CODEPOINT, Style.EMPTY);
                                    yield this.font.width(lead) + this.font.width(secSeq) + this.font.width(trail);
                                }
                                case MINIMESSAGE -> this.font.width(
                                        Component.translatable(
                                                "chat-utilities.settings.copy_formatted_style.value.minimessage"));
                            });
        }
        int w = Math.max(anchorW, widest + pad * 2);
        int h = vals.length * rowH;
        int x = anchorX;
        int y = anchorBottomY + 2;
        int maxX = contentRight() - 4;
        x = Math.min(x, maxX - w);
        x = Math.max(x, contentLeft() + 4);
        int maxY = footerY() - 6;
        if (y + h > maxY) {
            y = Math.max(bodyY(), anchorBottomY - h - rowH - 2);
        }
        settingsFormatDropX = x;
        settingsFormatDropY = y;
        settingsFormatDropW = w;
        settingsFormatDropH = h;
        settingsFormatDropdownOpen = true;
    }

    private void renderSettingsFormatDropdownOnTop(GuiGraphics g, int mouseX, int mouseY) {
        if (!settingsFormatDropdownOpen || activePanel != Panel.SETTINGS) {
            return;
        }
        int x = settingsFormatDropX;
        int y = settingsFormatDropY;
        int w = settingsFormatDropW;
        int h = settingsFormatDropH;
        int rowH = 18;
        g.fill(x, y, x + w, y + h, 0xF0101012);
        g.renderOutline(x, y, w, h, 0xFF2C2C3A);
        ChatUtilitiesClientOptions.CopyFormattedStyle[] vals = ChatUtilitiesClientOptions.CopyFormattedStyle.values();
        for (int i = 0; i < vals.length; i++) {
            int ry = y + i * rowH;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + rowH;
            if (hover) {
                g.fill(x + 1, ry, x + w - 1, ry + rowH, 0x203A6AC8);
            }
            drawSettingsCycleFormattedStyleCaptionAt(
                    g,
                    this.font,
                    vals[i],
                    x + w / 2,
                    ry + (rowH - 8) / 2,
                    hover ? 0xFFFFFFFF : 0xFFE0E0E8);
        }
    }

    private void renderSettingsUnreadBadgeStyleDropdownOnTop(GuiGraphics g, int mouseX, int mouseY) {
        if (!settingsUnreadBadgeStyleDropdownOpen || activePanel != Panel.SETTINGS) {
            return;
        }
        int x = settingsUnreadBadgeStyleDropX;
        int y = settingsUnreadBadgeStyleDropY;
        int w = settingsUnreadBadgeStyleDropW;
        int h = settingsUnreadBadgeStyleDropH;
        int rowH = 18;
        g.fill(x, y, x + w, y + h, 0xF0101012);
        g.renderOutline(x, y, w, h, 0xFF2C2C3A);
        ChatUtilitiesClientOptions.TabUnreadBadgeStyle[] vals =
                ChatUtilitiesClientOptions.TabUnreadBadgeStyle.values();
        for (int i = 0; i < vals.length; i++) {
            int ry = y + i * rowH;
            boolean hover = mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + rowH;
            if (hover) {
                g.fill(x + 1, ry, x + w - 1, ry + rowH, 0x203A6AC8);
            }
            Component cap =
                    Component.translatable(
                            "chat-utilities.settings.unread_badge.style.value."
                                    + vals[i].name().toLowerCase(Locale.ROOT));
            g.drawString(
                    this.font,
                    cap,
                    x + w / 2 - this.font.width(cap) / 2,
                    ry + (rowH - 8) / 2,
                    hover ? 0xFFFFFFFF : 0xFFE0E0E8,
                    false);
        }
    }

    private void addSettingsRowResetsAll(int resetX) {
        for (SettingsRow r : SettingsRow.values()) {
            if (r == SettingsRow.PROFILES_IMPORT_EXPORT_ROW || r == SettingsRow.PROFILES_LABY_ROW) {
                continue;
            }
            if (!settingsLayoutRowOn(r)) {
                continue;
            }
            int y = settingsClipYCandidate(r);
            final boolean atDefault = isSettingsRowAtBuiltInDefault(r);
            AbstractWidget rb =
                    new AbstractWidget(resetX, y, SETTINGS_ROW_RESET_W, 22, Component.empty()) {
                        @Override
                        public void onClick(MouseButtonEvent event, boolean dbl) {
                            if (atDefault) {
                                return;
                            }
                            resetSettingsRowToDefault(r);
                            saveOptions();
                            init();
                        }

                        @Override
                        protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                            boolean hov = this.isHovered() && !atDefault;
                            int edge = atDefault ? 0xFF303038 : hov ? 0xFF707088 : 0xFF505060;
                            g.renderOutline(getX(), getY(), getWidth(), getHeight(), edge);
                            net.minecraft.client.gui.Font fn = ChatUtilitiesRootScreen.this.font;
                            String sym = "↺";
                            float sc = 2.05f;
                            int tw0 = fn.width(sym);
                            int blockH = Math.round(fn.lineHeight * sc);
                            int top = getY() + (getHeight() - blockH) / 2;
                            int col = atDefault ? 0xFF505058 : hov ? 0xFFFFFFFF : 0xFFC8C8D8;
                            var pose = g.pose();
                            pose.pushMatrix();
                            pose.translate(getX() + getWidth() / 2f, top);
                            pose.scale(sc, sc);
                            g.drawString(fn, sym, Math.round(-tw0 / 2f), 0, col, false);
                            pose.popMatrix();
                        }

                        @Override
                        public void updateWidgetNarration(NarrationElementOutput n) {
                            defaultButtonNarrationText(n);
                        }
                    };
            rb.active = !atDefault;
            rb.setTooltip(
                    Tooltip.create(
                            Component.translatable(
                                    atDefault
                                            ? "chat-utilities.settings.reset_row.tooltip_at_default"
                                            : "chat-utilities.settings.reset_row.tooltip")));
            int ly = settingsLayoutRowY(r);
            if (ly >= 0) {
                settingsScrollWidgetLogicalY.put(rb, ly);
            }
            addSettingsScrollClipWidget(rb);
        }
    }

    private void resetSettingsRowToDefault(SettingsRow r) {
        switch (r) {
            case CHECK_FOR_UPDATES -> ChatUtilitiesClientOptions.setCheckForUpdatesEnabled(true);
            case OPEN_MENU -> {
                ChatUtilitiesModClient.OPEN_MENU_KEY.setKey(ChatUtilitiesModClient.OPEN_MENU_KEY.getDefaultKey());
                KeyMapping.resetMapping();
                rebindingOpenMenuKey = false;
            }
            case CHAT_PEEK -> {
                ChatUtilitiesModClient.CHAT_PEEK_KEY.setKey(ChatUtilitiesModClient.CHAT_PEEK_KEY.getDefaultKey());
                KeyMapping.resetMapping();
                rebindingChatPeekKey = false;
            }
            case COPY_PLAIN_BIND -> ChatUtilitiesClientOptions.setCopyPlainBinding(
                    ChatUtilitiesClientOptions.ClickMouseBinding.defaultPlain());
            case COPY_FORMATTED_BIND -> ChatUtilitiesClientOptions.setCopyFormattedBinding(
                    ChatUtilitiesClientOptions.ClickMouseBinding.defaultFormatted());
            case FULLSCREEN_IMAGE_CLICK -> ChatUtilitiesClientOptions.setFullscreenImagePreviewClickBinding(
                    ChatUtilitiesClientOptions.ClickMouseBinding.defaultFullscreenImagePreview());
            case CLICK_TO_COPY -> ChatUtilitiesClientOptions.setClickToCopyEnabled(true);
            case COPY_FORMATTED_STYLE -> ChatUtilitiesClientOptions.setCopyFormattedStyle(
                    ChatUtilitiesClientOptions.CopyFormattedStyle.MINIMESSAGE);
            case SMOOTH_CHAT -> ChatUtilitiesClientOptions.setSmoothChat(false);
            case SMOOTH_CHAT_DELAY_MS -> ChatUtilitiesClientOptions.setSmoothChatFadeMs(200);
            case SMOOTH_CHAT_BAR_OPEN_MS -> ChatUtilitiesClientOptions.setSmoothChatBarOpenMs(200);
            case LONGER_CHAT_HISTORY -> ChatUtilitiesClientOptions.setLongerChatHistory(false);
            case CHAT_HISTORY_LIMIT -> ChatUtilitiesClientOptions.setChatHistoryLimitLines(
                    ChatUtilitiesClientOptions.CHAT_HISTORY_LIMIT_DEFAULT);
            case STACK_REPEATED_MESSAGES -> ChatUtilitiesClientOptions.setStackRepeatedMessages(false);
            case STACKED_MESSAGE_COLOR -> ChatUtilitiesClientOptions.setStackedMessageColorRgb(
                    ChatUtilitiesClientOptions.STACKED_MESSAGE_COLOR_RGB_DEFAULT);
            case STACKED_MESSAGE_FORMAT -> ChatUtilitiesClientOptions.setStackedMessageFormat(
                    ChatUtilitiesClientOptions.STACKED_MESSAGE_FORMAT_DEFAULT);
            case CHAT_TIMESTAMPS -> ChatUtilitiesClientOptions.setChatTimestampsEnabled(false);
            case CHAT_TIMESTAMP_COLOR -> {
                ChatUtilitiesClientOptions.setChatTimestampColorRgb(
                        ChatUtilitiesClientOptions.CHAT_TIMESTAMP_COLOR_RGB_DEFAULT);
                ChatUtilitiesClientOptions.clearChatTimestampRecentColors();
            }
            case CHAT_TIMESTAMP_FORMAT -> ChatUtilitiesClientOptions.setChatTimestampFormatPattern(
                    ChatUtilitiesClientOptions.CHAT_TIMESTAMP_FORMAT_DEFAULT);
            case CHAT_SEARCH_BAR -> ChatUtilitiesClientOptions.setChatSearchBarEnabled(false);
            case CHAT_SEARCH_BAR_POSITION -> ChatUtilitiesClientOptions.setChatSearchBarPosition(
                    ChatUtilitiesClientOptions.ChatSearchBarPosition.ABOVE_CHAT);
            case IMAGE_PREVIEW_ENABLED -> ChatUtilitiesClientOptions.setImageChatPreviewEnabled(true);
            case IMAGE_PREVIEW_WHITELIST -> ChatUtilitiesClientOptions.resetImagePreviewWhitelistToBuiltInDefaults();
            case IMAGE_PREVIEW_ALLOW_UNTRUSTED -> ChatUtilitiesClientOptions.setAllowUntrustedImagePreviewDomains(
                    false);
            case CHAT_SYMBOL_SELECTOR -> ChatUtilitiesClientOptions.setShowChatSymbolSelector(true);
            case SYMBOL_PALETTE_INSERT_STYLE -> ChatUtilitiesClientOptions.setSymbolPaletteInsertStyle(
                    ChatUtilitiesClientOptions.CopyFormattedStyle.MINIMESSAGE);
            case TAB_UNREAD_BADGES -> ChatUtilitiesClientOptions.setChatWindowTabUnreadBadgesEnabled(true);
            case ALWAYS_SHOW_UNREAD_TABS -> ChatUtilitiesClientOptions.setAlwaysShowUnreadTabs(true);
            case UNREAD_BADGE_STYLE -> ChatUtilitiesClientOptions.setTabUnreadBadgeStyle(
                    ChatUtilitiesClientOptions.TabUnreadBadgeStyle.CIRCLE);
            case UNREAD_BADGE_COLOR -> ChatUtilitiesClientOptions.setTabUnreadBadgeColorRgb(
                    ChatUtilitiesClientOptions.TAB_UNREAD_BADGE_COLOR_RGB_DEFAULT);
            case CHAT_PANEL_BACKGROUND_OPACITY -> ChatUtilitiesClientOptions.setChatPanelBackgroundOpacityUnfocusedPercent(
                    ChatUtilitiesClientOptions.CHAT_PANEL_BG_OPACITY_DEFAULT);
            case CHAT_PANEL_BACKGROUND_FOCUSED_OPACITY ->
                    ChatUtilitiesClientOptions.setChatPanelBackgroundOpacityFocusedPercent(
                            ChatUtilitiesClientOptions.CHAT_PANEL_BG_OPACITY_DEFAULT);
            case CHAT_BAR_BACKGROUND_OPACITY -> ChatUtilitiesClientOptions.setChatBarBackgroundOpacityPercent(
                    ChatUtilitiesClientOptions.CHAT_PANEL_BG_OPACITY_DEFAULT);
            case CHAT_TEXT_SHADOW -> ChatUtilitiesClientOptions.setChatTextShadow(true);
            case CHAT_BAR_MENU_BUTTON -> ChatUtilitiesClientOptions.setShowChatBarMenuButton(true);
            case MOD_PRIMARY_COLOR -> {
                ChatUtilitiesClientOptions.setModPrimaryArgb(ChatUtilitiesClientOptions.MOD_PRIMARY_DEFAULT_ARGB);
                ChatUtilitiesClientOptions.setModPrimaryChroma(false);
                ChatUtilitiesClientOptions.setModPrimaryChromaSpeed(10f);
                ChatUtilitiesClientOptions.clearModPrimaryRecentColors();
            }
            case IGNORE_SELF_CHAT_ACTIONS -> ChatUtilitiesClientOptions.setIgnoreSelfInChatActions(true);
            case PRESERVE_VANILLA_CHAT_LOG_ON_LEAVE ->
                    ChatUtilitiesClientOptions.setPreserveVanillaChatOnDisconnect(true);
            case PROFILES_IMPORT_EXPORT_ROW, PROFILES_LABY_ROW -> {}
        }
    }

    private void buildSettingsWidgets() {
        rebuildSettingsFormLayout();
        settingsContentScroll = Mth.clamp(settingsContentScroll, 0, settingsMaxContentScroll());
        int settingsRight = contentRight() - scrollbarReserve(settingsMaxContentScroll() > 0);
        int mainCtrlW = SETTINGS_ROW_CONTROLS_TOTAL_W - SETTINGS_ROW_RESET_W - SETTINGS_ROW_RESET_GAP;
        int resetRowX = settingsRight - SETTINGS_ROW_RESET_W;
        int mainCtrlX = settingsRight - SETTINGS_ROW_CONTROLS_TOTAL_W;

        int searchW = settingsRight - contentLeft();
        settingsSearchField =
                new EditBox(
                        this.font,
                        contentLeft(),
                        settingsScrolledY(bodyY()),
                        searchW,
                        SETTINGS_SEARCH_FIELD_H,
                        Component.literal("settings_search"));
        settingsSearchField.setMaxLength(120);
        settingsSearchField.setValue(settingsSearchQuery);
        settingsSearchField.setHint(Component.translatable("chat-utilities.settings.search_hint"));
        settingsSearchField.setResponder(
                v -> {
                    if (!v.equals(settingsSearchQuery)) {
                        settingsSearchQuery = v;
                        init();
                    }
                });
        addSettingsScrollClipWidget(settingsSearchField);
        settingsScrollWidgetLogicalY.put(settingsSearchField, bodyY());

        settingsFinishRowClip(
                flatButtonCheckForUpdates(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.CHECK_FOR_UPDATES), mainCtrlW, 22),
                SettingsRow.CHECK_FOR_UPDATES);

        AbstractWidget openMenuKeyButton =
                new AbstractWidget(
                        mainCtrlX,
                        settingsClipYCandidate(SettingsRow.OPEN_MENU),
                        mainCtrlW,
                        22,
                        Component.literal(keybindCaptionOnly(ChatUtilitiesModClient.OPEN_MENU_KEY, rebindingOpenMenuKey))) {
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
                            rebindingChatPeekKey = false;
                            rebindingFullscreenImageClick = false;
                            rebindingCopyPlain = false;
                            rebindingCopyFormatted = false;
                            init();
                            return;
                        }
                        rebindingCopyPlain = false;
                        rebindingCopyFormatted = false;
                        rebindingFullscreenImageClick = false;
                        rebindingChatPeekKey = false;
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

        settingsFinishRowWidget(openMenuKeyButton, SettingsRow.OPEN_MENU);

        AbstractWidget chatPeekKeyButton =
                new AbstractWidget(
                        mainCtrlX,
                        settingsClipYCandidate(SettingsRow.CHAT_PEEK),
                        mainCtrlW,
                        22,
                        Component.literal(keybindCaptionOnly(ChatUtilitiesModClient.CHAT_PEEK_KEY, rebindingChatPeekKey))) {
                    @Override
                    protected boolean isValidClickButton(MouseButtonInfo info) {
                        int b = info.button();
                        return b == 0 || b == 1;
                    }

                    @Override
                    public void onClick(MouseButtonEvent event, boolean doubleClick) {
                        if (event.button() == 1) {
                            ChatUtilitiesModClient.CHAT_PEEK_KEY.setKey(InputConstants.UNKNOWN);
                            KeyMapping.resetMapping();
                            saveOptions();
                            rebindingOpenMenuKey = false;
                            rebindingChatPeekKey = false;
                            rebindingFullscreenImageClick = false;
                            rebindingCopyPlain = false;
                            rebindingCopyFormatted = false;
                            init();
                            return;
                        }
                        rebindingCopyPlain = false;
                        rebindingCopyFormatted = false;
                        rebindingFullscreenImageClick = false;
                        rebindingOpenMenuKey = false;
                        rebindingChatPeekKey = true;
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
                                keybindCaptionOnly(ChatUtilitiesModClient.CHAT_PEEK_KEY, rebindingChatPeekKey),
                                getX() + getWidth() / 2,
                                getY() + (getHeight() - 8) / 2,
                                tc);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        chatPeekKeyButton.setTooltip(
                Tooltip.create(Component.literal("Hold to temporarily show expanded chat (like T) without opening the input.")));
        settingsFinishRowWidget(chatPeekKeyButton, SettingsRow.CHAT_PEEK);
        settingsFinishRowClip(
                flatButtonCopyMouseBinding(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.COPY_PLAIN_BIND), mainCtrlW, 22, true),
                SettingsRow.COPY_PLAIN_BIND);
        settingsFinishRowClip(
                flatButtonCopyMouseBinding(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.COPY_FORMATTED_BIND), mainCtrlW, 22, false),
                SettingsRow.COPY_FORMATTED_BIND);
        settingsFinishRowClip(
                flatButtonFullscreenImageClickBinding(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.FULLSCREEN_IMAGE_CLICK), mainCtrlW, 22),
                SettingsRow.FULLSCREEN_IMAGE_CLICK);

        settingsFinishRowClip(
                flatButtonClickToCopy(mainCtrlX, settingsClipYCandidate(SettingsRow.CLICK_TO_COPY), mainCtrlW, 22),
                SettingsRow.CLICK_TO_COPY);
        settingsFinishRowClip(
                flatButtonCopyFormattedStyle(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.COPY_FORMATTED_STYLE), mainCtrlW, 22),
                SettingsRow.COPY_FORMATTED_STYLE);

        settingsFinishRowClip(
                flatButtonSmoothChat(mainCtrlX, settingsClipYCandidate(SettingsRow.SMOOTH_CHAT), mainCtrlW, 22),
                SettingsRow.SMOOTH_CHAT);
        settingsFinishRowClip(
                smoothChatFadeMsSlider(mainCtrlX, settingsClipYCandidate(SettingsRow.SMOOTH_CHAT_DELAY_MS), mainCtrlW, 22),
                SettingsRow.SMOOTH_CHAT_DELAY_MS);
        settingsFinishRowClip(
                smoothChatBarOpenMsSlider(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.SMOOTH_CHAT_BAR_OPEN_MS), mainCtrlW, 22),
                SettingsRow.SMOOTH_CHAT_BAR_OPEN_MS);

        settingsFinishRowClip(
                flatButtonLongerChatHistory(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.LONGER_CHAT_HISTORY), mainCtrlW, 22),
                SettingsRow.LONGER_CHAT_HISTORY);
        settingsFinishRowClip(
                chatHistoryLimitSlider(mainCtrlX, settingsClipYCandidate(SettingsRow.CHAT_HISTORY_LIMIT), mainCtrlW, 22),
                SettingsRow.CHAT_HISTORY_LIMIT);

        // ── Message stacking ─────────────────────────────────────────────────
        settingsFinishRowClip(
                flatButtonStackRepeatedMessages(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.STACK_REPEATED_MESSAGES), mainCtrlW, 22),
                SettingsRow.STACK_REPEATED_MESSAGES);
        settingsFinishRowClip(
                stackedMessageColorSettingButton(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.STACKED_MESSAGE_COLOR), mainCtrlW, 22),
                SettingsRow.STACKED_MESSAGE_COLOR);
        int fmtGap2 = 4;
        String fmtSample2 =
                ChatUtilitiesClientOptions.getStackedMessageFormat().replace("%amount%", "3");
        int smPreviewW = Mth.clamp(this.font.width(fmtSample2) + 8, 72, 120);
        settingsStackedMessagePreviewSlotW = smPreviewW;
        int smFieldX = mainCtrlX;
        int smFieldW = mainCtrlW;
        settingsStackedMessageFormatField =
                new EditBox(
                        this.font,
                        smFieldX,
                        settingsClipYCandidate(SettingsRow.STACKED_MESSAGE_FORMAT),
                        smFieldW,
                        20,
                        Component.literal("stackfmt"));
        settingsStackedMessageFormatField.setMaxLength(96);
        settingsStackedMessageFormatField.setValue(ChatUtilitiesClientOptions.getStackedMessageFormat());
        settingsStackedMessageFormatField.setResponder(ChatUtilitiesClientOptions::setStackedMessageFormat);
        settingsStackedMessageFormatField.setHint(
                Component.translatable("chat-utilities.settings.stacked_message.format_hint"));
        settingsFinishRowWidget(settingsStackedMessageFormatField, SettingsRow.STACKED_MESSAGE_FORMAT);

        settingsFinishRowClip(
                flatButtonChatTimestamps(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.CHAT_TIMESTAMPS), mainCtrlW, 22),
                SettingsRow.CHAT_TIMESTAMPS);
        settingsFinishRowClip(
                chatTimestampColorSettingButton(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.CHAT_TIMESTAMP_COLOR), mainCtrlW, 22),
                SettingsRow.CHAT_TIMESTAMP_COLOR);
        int fmtGap = 4;
        String fmtSample =
                ChatTimestampFormatter.previewPlainForPattern(
                        ChatUtilitiesClientOptions.getChatTimestampFormatPattern(),
                        System.currentTimeMillis());
        int tsPreviewW = Mth.clamp(this.font.width(fmtSample) + 8, 72, 120);
        settingsTimestampPreviewSlotW = tsPreviewW;
        int tsFieldX = mainCtrlX;
        int tsFieldW = mainCtrlW;
        settingsTimestampFormatField =
                new EditBox(
                        this.font,
                        tsFieldX,
                        settingsClipYCandidate(SettingsRow.CHAT_TIMESTAMP_FORMAT),
                        tsFieldW,
                        20,
                        Component.literal("tsfmt"));
        settingsTimestampFormatField.setMaxLength(96);
        settingsTimestampFormatField.setValue(ChatUtilitiesClientOptions.getChatTimestampFormatPattern());
        settingsTimestampFormatField.setResponder(ChatUtilitiesClientOptions::setChatTimestampFormatPattern);
        settingsTimestampFormatField.setHint(
                Component.translatable("chat-utilities.settings.chat_timestamp.format_hint"));
        settingsFinishRowWidget(settingsTimestampFormatField, SettingsRow.CHAT_TIMESTAMP_FORMAT);

        settingsFinishRowClip(
                flatButtonChatSearchBar(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.CHAT_SEARCH_BAR), mainCtrlW, 22),
                SettingsRow.CHAT_SEARCH_BAR);
        settingsFinishRowClip(
                flatButtonChatSearchBarPosition(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.CHAT_SEARCH_BAR_POSITION), mainCtrlW, 22),
                SettingsRow.CHAT_SEARCH_BAR_POSITION);

        settingsFinishRowClip(
                flatButtonImageChatPreviewEnabled(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.IMAGE_PREVIEW_ENABLED), mainCtrlW, 22),
                SettingsRow.IMAGE_PREVIEW_ENABLED);
        settingsFinishRowClip(
                flatButtonImagePreviewWhitelist(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.IMAGE_PREVIEW_WHITELIST), mainCtrlW, 22),
                SettingsRow.IMAGE_PREVIEW_WHITELIST);
        settingsFinishRowClip(
                flatButtonAllowUntrustedDomains(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.IMAGE_PREVIEW_ALLOW_UNTRUSTED), mainCtrlW, 22),
                SettingsRow.IMAGE_PREVIEW_ALLOW_UNTRUSTED);
        settingsFinishRowClip(
                flatButtonSymbolSelector(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.CHAT_SYMBOL_SELECTOR), mainCtrlW, 22),
                SettingsRow.CHAT_SYMBOL_SELECTOR);
        settingsFinishRowClip(
                flatButtonSymbolPaletteInsertStyle(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.SYMBOL_PALETTE_INSERT_STYLE), mainCtrlW, 22),
                SettingsRow.SYMBOL_PALETTE_INSERT_STYLE);

        settingsFinishRowClip(
                flatButtonTabUnreadBadges(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.TAB_UNREAD_BADGES), mainCtrlW, 22),
                SettingsRow.TAB_UNREAD_BADGES);
        settingsFinishRowClip(
                flatButtonAlwaysShowUnreadTabs(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.ALWAYS_SHOW_UNREAD_TABS), mainCtrlW, 22),
                SettingsRow.ALWAYS_SHOW_UNREAD_TABS);
        settingsFinishRowClip(
                flatButtonUnreadBadgeStyle(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.UNREAD_BADGE_STYLE), mainCtrlW, 22),
                SettingsRow.UNREAD_BADGE_STYLE);
        settingsFinishRowClip(
                unreadBadgeColorSettingButton(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.UNREAD_BADGE_COLOR), mainCtrlW, 22),
                SettingsRow.UNREAD_BADGE_COLOR);

        settingsFinishRowClip(
                chatPanelBackgroundOpacitySlider(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.CHAT_PANEL_BACKGROUND_OPACITY), mainCtrlW, 22),
                SettingsRow.CHAT_PANEL_BACKGROUND_OPACITY);
        settingsFinishRowClip(
                chatPanelBackgroundFocusedOpacitySlider(
                        mainCtrlX,
                        settingsClipYCandidate(SettingsRow.CHAT_PANEL_BACKGROUND_FOCUSED_OPACITY),
                        mainCtrlW,
                        22),
                SettingsRow.CHAT_PANEL_BACKGROUND_FOCUSED_OPACITY);
        settingsFinishRowClip(
                chatBarBackgroundOpacitySlider(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.CHAT_BAR_BACKGROUND_OPACITY), mainCtrlW, 22),
                SettingsRow.CHAT_BAR_BACKGROUND_OPACITY);
        settingsFinishRowClip(
                flatButtonChatTextShadow(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.CHAT_TEXT_SHADOW), mainCtrlW, 22),
                SettingsRow.CHAT_TEXT_SHADOW);
        settingsFinishRowClip(
                flatButtonChatBarMenuButton(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.CHAT_BAR_MENU_BUTTON), mainCtrlW, 22),
                SettingsRow.CHAT_BAR_MENU_BUTTON);
        settingsFinishRowClip(
                modPrimaryColorSettingButton(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.MOD_PRIMARY_COLOR), mainCtrlW, 22),
                SettingsRow.MOD_PRIMARY_COLOR);
        settingsFinishRowClip(
                flatButtonIgnoreSelfChatActions(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.IGNORE_SELF_CHAT_ACTIONS), mainCtrlW, 22),
                SettingsRow.IGNORE_SELF_CHAT_ACTIONS);
        settingsFinishRowClip(
                flatButtonPreserveVanillaChatLog(
                        mainCtrlX, settingsClipYCandidate(SettingsRow.PRESERVE_VANILLA_CHAT_LOG_ON_LEAVE), mainCtrlW, 22),
                SettingsRow.PRESERVE_VANILLA_CHAT_LOG_ON_LEAVE);

        addSettingsRowResetsAll(resetRowX);

        int profImpY = settingsLayoutRowY(SettingsRow.PROFILES_IMPORT_EXPORT_ROW);
        int profLabyY = settingsLayoutRowY(SettingsRow.PROFILES_LABY_ROW);
        int gap = 8;
        int btnW = 132;
        int leftX = contentLeft();
        if (settingsLayoutRowOn(SettingsRow.PROFILES_IMPORT_EXPORT_ROW)) {
            AbstractWidget importBtn =
                    flatButton(
                            Component.literal("⬆ Import Profiles"),
                            () -> {
                                importExportChoiceDialogOpen = true;
                                importExportChoiceIsImport = true;
                                init();
                            },
                            leftX,
                            settingsScrolledY(profImpY),
                            btnW,
                            20);
            importBtn.setTooltip(
                    Tooltip.create(Component.translatable("chat-utilities.settings.import_profiles.tooltip")));
            settingsScrollWidgetLogicalY.put(importBtn, profImpY);
            addSettingsScrollClipWidget(importBtn);
            AbstractWidget exportBtn =
                    flatButton(
                            Component.literal("⬇ Export Profiles"),
                            () -> {
                                importExportChoiceDialogOpen = true;
                                importExportChoiceIsImport = false;
                                init();
                            },
                            leftX + btnW + gap,
                            settingsScrolledY(profImpY),
                            btnW,
                            20);
            exportBtn.setTooltip(
                    Tooltip.create(Component.translatable("chat-utilities.settings.export_profiles.tooltip")));
            settingsScrollWidgetLogicalY.put(exportBtn, profImpY);
            addSettingsScrollClipWidget(exportBtn);
        }

        if (settingsLayoutRowOn(SettingsRow.PROFILES_LABY_ROW)) {
            AbstractWidget labyBtn =
                    flatButton(
                            Component.literal("⬆ Import from LabyMod"),
                            () -> runImportLabyModProfilesDialog(),
                            leftX,
                            settingsScrolledY(profLabyY),
                            btnW * 2 + gap,
                            20);
            labyBtn.setTooltip(Tooltip.create(Component.literal("Import a LabyMod chat windows JSON export.")));
            settingsScrollWidgetLogicalY.put(labyBtn, profLabyY);
            addSettingsScrollClipWidget(labyBtn);
        }

        if (importExportChoiceDialogOpen) {
            buildImportExportChoiceDialog();
        }

        long nowReset = System.currentTimeMillis();
        boolean resetArmed = resetDefaultsConfirmDeadlineMs > nowReset;
        int resetW = 200;
        int resetX = contentLeft();
        int resetY = footerY();
        Component resetText =
                Component.translatable(
                        resetArmed
                                ? "chat-utilities.settings.reset_all.confirm"
                                : "chat-utilities.settings.reset_all");
        Runnable resetPress =
                () -> {
                    long t = System.currentTimeMillis();
                    if (resetDefaultsConfirmDeadlineMs > t) {
                        resetDefaultsConfirmDeadlineMs = 0;
                        ChatUtilitiesClientOptions.resetAllToDefaults();
                        KeyMapping km = ChatUtilitiesModClient.OPEN_MENU_KEY;
                        km.setKey(km.getDefaultKey());
                        KeyMapping.resetMapping();
                        saveOptions();
                        rebindingOpenMenuKey = false;
                        rebindingCopyPlain = false;
                        rebindingCopyFormatted = false;
                        showSettingsToast(
                                Component.translatable("chat-utilities.settings.reset_all.toast.title")
                                        .getString(),
                                Component.translatable("chat-utilities.settings.reset_all.toast.detail")
                                        .getString());
                        init();
                    } else {
                        resetDefaultsConfirmDeadlineMs = t + DESTRUCTIVE_CONFIRM_MS;
                        init();
                    }
                };
        AbstractWidget resetAllBtn =
                new AbstractWidget(resetX, resetY, resetW, 20, resetText) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        resetPress.run();
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        int bg = !act ? 0x35402020 : hov ? 0x55903030 : 0x45302828;
                        int outline = !act ? 0x45C06060 : hov ? 0x85FF9090 : 0x65D07070;
                        int tc = !act ? C_DANGER_TEXT : hov ? C_DANGER_TEXT_H : 0xFFF0A0A0;
                        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
                        g.renderOutline(getX(), getY(), getWidth(), getHeight(), outline);
                        net.minecraft.client.gui.Font f = ChatUtilitiesRootScreen.this.font;
                        String sym = "\u21ba";
                        float iconScale = 2.12f;
                        int textW = f.width(getMessage());
                        int gap = 6;
                        int symW = Math.round(f.width(sym) * iconScale);
                        int cx = getX() + getWidth() / 2;
                        int fh = f.lineHeight;
                        int textY = getY() + (getHeight() - fh) / 2 + 1;
                        int startX = cx - (symW + gap + textW) / 2;
                        int iconBlockH = Math.round(fh * iconScale);
                        int iconTop = getY() + (getHeight() - iconBlockH) / 2;
                        var pose = g.pose();
                        pose.pushMatrix();
                        pose.translate(startX + symW / 2f, iconTop);
                        pose.scale(iconScale, iconScale);
                        int tw = f.width(sym);
                        int ic = hov ? 0xFFFFFFFF : 0xFFF0A0A0;
                        g.drawString(f, sym, Math.round(-tw / 2f), 0, ic, false);
                        pose.popMatrix();
                        g.drawString(f, getMessage(), startX + symW + gap, textY, tc, false);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        resetAllBtn.setTooltip(
                Tooltip.create(Component.translatable("chat-utilities.settings.reset_all.tooltip")));
        addRenderableWidget(resetAllBtn);
        settingsNonClipRenderables.add(resetAllBtn);
    }

    private AbstractWidget flatButtonCheckForUpdates(int x, int y, int w, int h) {
        AbstractWidget btn =
                new SettingsBooleanToggleWidget(x, y, w, h) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        ChatUtilitiesClientOptions.toggleCheckForUpdatesEnabled();
                        init();
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean on = ChatUtilitiesClientOptions.isCheckForUpdatesEnabled();
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        renderSettingsBooleanSwitch(
                                g,
                                "opt.checkForUpdates",
                                getX(),
                                getY(),
                                getWidth(),
                                getHeight(),
                                on,
                                hov,
                                act,
                                pt);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        btn.setTooltip(Tooltip.create(Component.translatable("chat-utilities.settings.check_for_updates.tooltip")));
        return btn;
    }

    private AbstractWidget flatButtonImageChatPreviewEnabled(int x, int y, int w, int h) {
        AbstractWidget btn =
                new SettingsBooleanToggleWidget(x, y, w, h) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        ChatUtilitiesClientOptions.toggleImageChatPreviewEnabled();
                        init();
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean on = ChatUtilitiesClientOptions.isImageChatPreviewEnabled();
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        renderSettingsBooleanSwitch(
                                g,
                                "opt.imagePreview",
                                getX(),
                                getY(),
                                getWidth(),
                                getHeight(),
                                on,
                                hov,
                                act,
                                pt);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        btn.setTooltip(
                Tooltip.create(Component.translatable("chat-utilities.settings.image_preview.enabled.tooltip")));
        return btn;
    }

    private AbstractWidget flatButtonImagePreviewWhitelist(int x, int y, int w, int h) {
        AbstractWidget btn =
                new AbstractWidget(x, y, w, h, Component.empty()) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        ChatUtilitiesRootScreen.this.imageWhitelistOverlay =
                                new ImagePreviewWhitelistOverlay(
                                        () -> {
                                            ChatUtilitiesRootScreen.this.imageWhitelistOverlay = null;
                                            ChatUtilitiesRootScreen.this.init();
                                        });
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
                        g.drawCenteredString(
                                Minecraft.getInstance().font,
                                Component.translatable("chat-utilities.settings.image_preview.whitelist_open"),
                                getX() + getWidth() / 2,
                                getY() + (getHeight() - 8) / 2,
                                0xFFBBBBCC);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        btn.setTooltip(
                Tooltip.create(Component.translatable("chat-utilities.settings.image_preview.whitelist.tooltip")));
        return btn;
    }

    private AbstractWidget flatButtonAllowUntrustedDomains(int x, int y, int w, int h) {
        AbstractWidget btn =
                new SettingsBooleanToggleWidget(x, y, w, h) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        ChatUtilitiesClientOptions.toggleAllowUntrustedImagePreviewDomains();
                        init();
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        boolean on = ChatUtilitiesClientOptions.isAllowUntrustedImagePreviewDomains();
                        renderSettingsBooleanSwitch(
                                g,
                                "opt.allowUntrustedImage",
                                getX(),
                                getY(),
                                getWidth(),
                                getHeight(),
                                on,
                                hov,
                                act,
                                pt);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        btn.setTooltip(
                Tooltip.create(Component.translatable("chat-utilities.settings.image_preview.allow_untrusted_domains.tooltip")));
        return btn;
    }

    private AbstractWidget flatButtonSymbolSelector(int x, int y, int w, int h) {
        AbstractWidget sym =
                new SettingsBooleanToggleWidget(x, y, w, h) {
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
                        renderSettingsBooleanSwitch(
                                g,
                                "opt.chatSymbolSelector",
                                getX(),
                                getY(),
                                getWidth(),
                                getHeight(),
                                on,
                                hov,
                                act,
                                pt);
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

    /**
     * Cycle-style setting buttons (copy format + symbol palette insert) share the same three captions. The section
     * sign is drawn with {@link FormattedCharSequence#codepoint(int, Style)} — {@link GuiGraphics#drawString} with a
     * {@link String} still parses legacy {@code §} and draws nothing between the parentheses.
     */
    private static void drawSettingsCycleFormattedStyleCaptionAt(
            GuiGraphics g,
            Font font,
            ChatUtilitiesClientOptions.CopyFormattedStyle style,
            int cx,
            int y,
            int tc) {
        switch (style) {
            case VANILLA -> g.drawCenteredString(
                    font,
                    Component.translatable("chat-utilities.settings.copy_formatted_style.value.vanilla"),
                    cx,
                    y,
                    tc);
            case SECTION_SYMBOL -> {
                String lead =
                        I18n.get("chat-utilities.settings.copy_formatted_style.value.section_symbol.lead");
                String trail =
                        I18n.get("chat-utilities.settings.copy_formatted_style.value.section_symbol.trail");
                FormattedCharSequence secSeq =
                        FormattedCharSequence.codepoint(SECTION_SIGN_CODEPOINT, Style.EMPTY);
                int tw = font.width(lead) + font.width(secSeq) + font.width(trail);
                int x = cx - tw / 2;
                g.drawString(font, lead, x, y, tc, false);
                x += font.width(lead);
                g.drawString(font, secSeq, x, y, tc, false);
                x += font.width(secSeq);
                g.drawString(font, trail, x, y, tc, false);
            }
            case MINIMESSAGE -> g.drawCenteredString(
                    font,
                    Component.translatable("chat-utilities.settings.copy_formatted_style.value.minimessage"),
                    cx,
                    y,
                    tc);
        }
    }

    private static void drawSettingsCycleFormattedStyleCaption(
            GuiGraphics g, AbstractWidget widget, ChatUtilitiesClientOptions.CopyFormattedStyle style) {
        Font font = Minecraft.getInstance().font;
        int cx = widget.getX() + widget.getWidth() / 2;
        int y = widget.getY() + (widget.getHeight() - 8) / 2;
        drawSettingsCycleFormattedStyleCaptionAt(g, font, style, cx, y, 0xFFBBBBCC);
    }

    private AbstractWidget flatButtonCopyFormattedStyle(int x, int y, int w, int h) {
        AbstractWidget btn =
                new AbstractWidget(x, y, w, h, Component.empty()) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        openSettingsFormatDropdown(false, getX(), getY() + getHeight(), getWidth());
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        int bg = !act ? 0x25000000 : hov ? 0x55FFFFFF : 0x35000000;
                        int outline = !act ? 0x25FFFFFF : hov ? 0x70FFFFFF : 0x40FFFFFF;
                        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
                        g.renderOutline(getX(), getY(), getWidth(), getHeight(), outline);
                        drawSettingsCycleFormattedStyleCaption(
                                g, this, ChatUtilitiesClientOptions.getCopyFormattedStyle());
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

    private AbstractWidget flatButtonSymbolPaletteInsertStyle(int x, int y, int w, int h) {
        AbstractWidget btn =
                new AbstractWidget(x, y, w, h, Component.empty()) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        openSettingsFormatDropdown(true, getX(), getY() + getHeight(), getWidth());
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        int bg = !act ? 0x25000000 : hov ? 0x55FFFFFF : 0x35000000;
                        int outline = !act ? 0x25FFFFFF : hov ? 0x70FFFFFF : 0x40FFFFFF;
                        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
                        g.renderOutline(getX(), getY(), getWidth(), getHeight(), outline);
                        drawSettingsCycleFormattedStyleCaption(
                                g, this, ChatUtilitiesClientOptions.getSymbolPaletteInsertStyle());
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        btn.setTooltip(
                Tooltip.create(
                        Component.translatable("chat-utilities.settings.symbol_palette_insert_style.tooltip")));
        return btn;
    }

    private AbstractWidget flatButtonUnreadBadgeStyle(int x, int y, int w, int h) {
        AbstractWidget btn =
                new AbstractWidget(x, y, w, h, Component.empty()) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        openSettingsUnreadBadgeStyleDropdown(getX(), getY() + getHeight(), getWidth());
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        int bg = !act ? 0x25000000 : hov ? 0x55FFFFFF : 0x35000000;
                        int outline = !act ? 0x25FFFFFF : hov ? 0x70FFFFFF : 0x40FFFFFF;
                        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
                        g.renderOutline(getX(), getY(), getWidth(), getHeight(), outline);
                        ChatUtilitiesClientOptions.TabUnreadBadgeStyle st =
                                ChatUtilitiesClientOptions.getTabUnreadBadgeStyle();
                        Component cap =
                                Component.translatable(
                                        "chat-utilities.settings.unread_badge.style.value."
                                                + st.name().toLowerCase(Locale.ROOT));
                        g.drawString(
                                Minecraft.getInstance().font,
                                cap,
                                getX() + getWidth() / 2 - Minecraft.getInstance().font.width(cap) / 2,
                                getY() + (getHeight() - 8) / 2,
                                0xFFBBBBCC,
                                false);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        btn.setTooltip(Tooltip.create(Component.translatable("chat-utilities.settings.unread_badge.style.tooltip")));
        return btn;
    }

    private AbstractWidget flatButtonChatTextShadow(int x, int y, int w, int h) {
        AbstractWidget sh =
                new SettingsBooleanToggleWidget(x, y, w, h) {
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
                        renderSettingsBooleanSwitch(
                                g,
                                "opt.chatTextShadow",
                                getX(),
                                getY(),
                                getWidth(),
                                getHeight(),
                                on,
                                hov,
                                act,
                                pt);
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

    private AbstractWidget flatButtonSmoothChat(int x, int y, int w, int h) {
        AbstractWidget btn =
                new SettingsBooleanToggleWidget(x, y, w, h) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        ChatUtilitiesClientOptions.toggleSmoothChat();
                        init();
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean on = ChatUtilitiesClientOptions.isSmoothChat();
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        renderSettingsBooleanSwitch(
                                g,
                                "opt.smoothChat",
                                getX(),
                                getY(),
                                getWidth(),
                                getHeight(),
                                on,
                                hov,
                                act,
                                pt);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        btn.setTooltip(Tooltip.create(Component.translatable("chat-utilities.settings.smooth_chat.tooltip")));
        return btn;
    }

    private static int snapSmoothChatSliderMs(int ms) {
        int step = ChatUtilitiesClientOptions.SMOOTH_CHAT_SLIDER_STEP_MS;
        int min = ChatUtilitiesClientOptions.SMOOTH_CHAT_FADE_MS_MIN;
        int max = ChatUtilitiesClientOptions.SMOOTH_CHAT_FADE_MS_MAX;
        int snapped = min + Math.round((ms - min) / (float) step) * step;
        return Mth.clamp(snapped, min, max);
    }

    /** Flat panel; label in upper area, thin track + thumb along the bottom (no line through the text). */
    private static void renderFlatSliderWidget(
            GuiGraphics g, AbstractSliderButton slider, int mx, int my, float pt, double value01) {
        boolean hov = slider.isHovered();
        boolean act = slider.active;
        int bg = !act ? 0x25000000 : hov ? 0x55FFFFFF : 0x35000000;
        int outline = !act ? 0x25FFFFFF : hov ? 0x70FFFFFF : 0x40FFFFFF;
        int tc = !act ? 0xFF555565 : hov ? 0xFFFFFFFF : 0xFFBBBBCC;
        int x = slider.getX();
        int y = slider.getY();
        int w = slider.getWidth();
        int h = slider.getHeight();
        g.fill(x, y, x + w, y + h, bg);
        g.renderOutline(x, y, w, h, outline);

        int pad = 5;
        int innerL = x + pad;
        int innerR = x + w - pad;
        int innerW = Math.max(0, innerR - innerL);
        double v = Mth.clamp(value01, 0.0, 1.0);

        int trackH = 2;
        int trackBottom = y + h - 4;
        int trackTop = trackBottom - trackH;
        g.fill(innerL, trackTop, innerR, trackBottom, 0x40000000);
        int fillW = (int) Math.round(innerW * v);
        if (fillW > 0) {
            g.fill(innerL, trackTop, innerL + fillW, trackBottom, act ? 0x90BBBBCC : 0x50555665);
        }
        int thumbW = 3;
        int tx = innerL + (int) Math.round((innerW - thumbW) * v);
        int thumbTop = trackTop - 1;
        int thumbBot = trackBottom + 1;
        g.fill(tx, thumbTop, tx + thumbW, thumbBot, act ? 0xFFDDDDEE : 0xFF666678);

        int textY = y + Math.max(2, trackTop - y - 10);
        g.drawCenteredString(
                Minecraft.getInstance().font,
                slider.getMessage(),
                x + w / 2,
                textY,
                tc);
    }

    private AbstractWidget smoothChatFadeMsSlider(int x, int y, int w, int h) {
        int min = ChatUtilitiesClientOptions.SMOOTH_CHAT_FADE_MS_MIN;
        int max = ChatUtilitiesClientOptions.SMOOTH_CHAT_FADE_MS_MAX;
        int cur = snapSmoothChatSliderMs(ChatUtilitiesClientOptions.getSmoothChatFadeMs());
        double initial = (cur - min) / (double) (max - min);
        AbstractSliderButton slider =
                new AbstractSliderButton(x, y, w, h, Component.empty(), initial) {
                    {
                        updateMessage();
                    }

                    @Override
                    protected void updateMessage() {
                        setMessage(Component.literal(msFromSlider() + " ms"));
                    }

                    private int msFromSlider() {
                        return Mth.clamp(
                                (int) Math.round(Mth.lerp(value, min, max)), min, max);
                    }

                    @Override
                    protected void applyValue() {
                        int snapped = snapSmoothChatSliderMs(msFromSlider());
                        value = (snapped - min) / (double) (max - min);
                        ChatUtilitiesClientOptions.setSmoothChatFadeMs(snapped);
                        updateMessage();
                    }

                    @Override
                    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        renderFlatSliderWidget(g, this, mx, my, pt, value);
                    }

                    @Override
                    public boolean mouseScrolled(
                            double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
                        if (!active || !isMouseOver(mouseX, mouseY) || verticalAmount == 0) {
                            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
                        }
                        int step = verticalAmount > 0 ? -50 : 50;
                        int next =
                                snapSmoothChatSliderMs(
                                        Mth.clamp(msFromSlider() + step, min, max));
                        value = (next - min) / (double) (max - min);
                        ChatUtilitiesClientOptions.setSmoothChatFadeMs(next);
                        updateMessage();
                        return true;
                    }
                };
        slider.setTooltip(
                Tooltip.create(Component.translatable("chat-utilities.settings.smooth_chat_delay.tooltip")));
        slider.active = ChatUtilitiesClientOptions.isSmoothChat();
        return slider;
    }

    private AbstractWidget smoothChatBarOpenMsSlider(int x, int y, int w, int h) {
        int min = ChatUtilitiesClientOptions.SMOOTH_CHAT_FADE_MS_MIN;
        int max = ChatUtilitiesClientOptions.SMOOTH_CHAT_FADE_MS_MAX;
        int cur = snapSmoothChatSliderMs(ChatUtilitiesClientOptions.getSmoothChatBarOpenMs());
        double initial = (cur - min) / (double) (max - min);
        AbstractSliderButton slider =
                new AbstractSliderButton(x, y, w, h, Component.empty(), initial) {
                    {
                        updateMessage();
                    }

                    @Override
                    protected void updateMessage() {
                        setMessage(Component.literal(msFromSlider() + " ms"));
                    }

                    private int msFromSlider() {
                        return Mth.clamp(
                                (int) Math.round(Mth.lerp(value, min, max)), min, max);
                    }

                    @Override
                    protected void applyValue() {
                        int snapped = snapSmoothChatSliderMs(msFromSlider());
                        value = (snapped - min) / (double) (max - min);
                        ChatUtilitiesClientOptions.setSmoothChatBarOpenMs(snapped);
                        updateMessage();
                    }

                    @Override
                    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        renderFlatSliderWidget(g, this, mx, my, pt, value);
                    }

                    @Override
                    public boolean mouseScrolled(
                            double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
                        if (!active || !isMouseOver(mouseX, mouseY) || verticalAmount == 0) {
                            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
                        }
                        int step = verticalAmount > 0 ? -50 : 50;
                        int next =
                                snapSmoothChatSliderMs(
                                        Mth.clamp(msFromSlider() + step, min, max));
                        value = (next - min) / (double) (max - min);
                        ChatUtilitiesClientOptions.setSmoothChatBarOpenMs(next);
                        updateMessage();
                        return true;
                    }
                };
        slider.setTooltip(
                Tooltip.create(Component.translatable("chat-utilities.settings.smooth_chat_bar_open.tooltip")));
        slider.active = ChatUtilitiesClientOptions.isSmoothChat();
        return slider;
    }

    private static int snapChatHistoryLimitLines(int lines) {
        int step = ChatUtilitiesClientOptions.CHAT_HISTORY_LIMIT_STEP;
        int min = ChatUtilitiesClientOptions.CHAT_HISTORY_LIMIT_MIN;
        int max = ChatUtilitiesClientOptions.CHAT_HISTORY_LIMIT_MAX;
        int snapped = min + Math.round((lines - min) / (float) step) * step;
        return Mth.clamp(snapped, min, max);
    }

    private AbstractWidget flatButtonLongerChatHistory(int x, int y, int w, int h) {
        AbstractWidget btn =
                new SettingsBooleanToggleWidget(x, y, w, h) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        ChatUtilitiesClientOptions.toggleLongerChatHistory();
                        init();
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean on = ChatUtilitiesClientOptions.isLongerChatHistory();
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        renderSettingsBooleanSwitch(
                                g,
                                "opt.longerChatHistory",
                                getX(),
                                getY(),
                                getWidth(),
                                getHeight(),
                                on,
                                hov,
                                act,
                                pt);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        btn.setTooltip(
                Tooltip.create(Component.translatable("chat-utilities.settings.longer_chat_history.tooltip")));
        return btn;
    }

    private AbstractWidget flatButtonStackRepeatedMessages(int x, int y, int w, int h) {
        AbstractWidget btn =
                new SettingsBooleanToggleWidget(x, y, w, h) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        ChatUtilitiesClientOptions.toggleStackRepeatedMessages();
                        init();
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean on = ChatUtilitiesClientOptions.isStackRepeatedMessages();
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        renderSettingsBooleanSwitch(
                                g,
                                "opt.stackRepeated",
                                getX(),
                                getY(),
                                getWidth(),
                                getHeight(),
                                on,
                                hov,
                                act,
                                pt);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        btn.setTooltip(
                Tooltip.create(Component.translatable("chat-utilities.settings.stack_repeated_messages.tooltip")));
        return btn;
    }

    private AbstractWidget flatButtonChatBarMenuButton(int x, int y, int w, int h) {
        AbstractWidget btn =
                new SettingsBooleanToggleWidget(x, y, w, h) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        ChatUtilitiesClientOptions.toggleShowChatBarMenuButton();
                        init();
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean on = ChatUtilitiesClientOptions.isShowChatBarMenuButton();
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        renderSettingsBooleanSwitch(
                                g,
                                "opt.chatBarMenuButton",
                                getX(),
                                getY(),
                                getWidth(),
                                getHeight(),
                                on,
                                hov,
                                act,
                                pt);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        btn.setTooltip(
                Tooltip.create(Component.translatable("chat-utilities.settings.chat_bar_menu_button.tooltip")));
        return btn;
    }

    private AbstractWidget flatButtonTabUnreadBadges(int x, int y, int w, int h) {
        AbstractWidget btn =
                new SettingsBooleanToggleWidget(x, y, w, h) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        ChatUtilitiesClientOptions.toggleChatWindowTabUnreadBadgesEnabled();
                        init();
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean on = ChatUtilitiesClientOptions.isChatWindowTabUnreadBadgesEnabled();
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        renderSettingsBooleanSwitch(
                                g,
                                "opt.tabUnreadBadges",
                                getX(),
                                getY(),
                                getWidth(),
                                getHeight(),
                                on,
                                hov,
                                act,
                                pt);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        btn.setTooltip(Tooltip.create(Component.translatable("chat-utilities.settings.tab_unread_badges.tooltip")));
        return btn;
    }

    private AbstractWidget flatButtonAlwaysShowUnreadTabs(int x, int y, int w, int h) {
        AbstractWidget btn =
                new SettingsBooleanToggleWidget(x, y, w, h) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        ChatUtilitiesClientOptions.toggleAlwaysShowUnreadTabs();
                        init();
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean on = ChatUtilitiesClientOptions.isAlwaysShowUnreadTabs();
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        renderSettingsBooleanSwitch(
                                g,
                                "opt.alwaysUnreadTabs",
                                getX(),
                                getY(),
                                getWidth(),
                                getHeight(),
                                on,
                                hov,
                                act,
                                pt);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        btn.setTooltip(Tooltip.create(Component.literal("Show unread tabs on HUD when chat is closed.")));
        return btn;
    }

    private AbstractWidget flatButtonIgnoreSelfChatActions(int x, int y, int w, int h) {
        AbstractWidget btn =
                new SettingsBooleanToggleWidget(x, y, w, h) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        ChatUtilitiesClientOptions.toggleIgnoreSelfInChatActions();
                        init();
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean on = ChatUtilitiesClientOptions.isIgnoreSelfInChatActions();
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        renderSettingsBooleanSwitch(
                                g,
                                "opt.ignoreSelfChatActions",
                                getX(),
                                getY(),
                                getWidth(),
                                getHeight(),
                                on,
                                hov,
                                act,
                                pt);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        btn.setTooltip(
                Tooltip.create(Component.translatable("chat-utilities.settings.ignore_self_chat_actions.tooltip")));
        return btn;
    }

    private AbstractWidget flatButtonPreserveVanillaChatLog(int x, int y, int w, int h) {
        AbstractWidget btn =
                new SettingsBooleanToggleWidget(x, y, w, h) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        ChatUtilitiesClientOptions.togglePreserveVanillaChatOnDisconnect();
                        init();
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean on = ChatUtilitiesClientOptions.isPreserveVanillaChatOnDisconnect();
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        renderSettingsBooleanSwitch(
                                g,
                                "opt.preserveVanillaChat",
                                getX(),
                                getY(),
                                getWidth(),
                                getHeight(),
                                on,
                                hov,
                                act,
                                pt);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        btn.setTooltip(
                Tooltip.create(
                        Component.translatable("chat-utilities.settings.preserve_vanilla_chat_log.tooltip")));
        return btn;
    }

    private AbstractWidget flatButtonChatSearchBar(int x, int y, int w, int h) {
        AbstractWidget btn =
                new SettingsBooleanToggleWidget(x, y, w, h) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        ChatUtilitiesClientOptions.toggleChatSearchBarEnabled();
                        init();
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean on = ChatUtilitiesClientOptions.isChatSearchBarEnabled();
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        renderSettingsBooleanSwitch(
                                g,
                                "opt.chatSearchBar",
                                getX(),
                                getY(),
                                getWidth(),
                                getHeight(),
                                on,
                                hov,
                                act,
                                pt);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        btn.setTooltip(
                Tooltip.create(Component.translatable("chat-utilities.settings.chat_search_bar.tooltip")));
        return btn;
    }

    private AbstractWidget flatButtonChatSearchBarPosition(int x, int y, int w, int h) {
        AbstractWidget btn =
                new AbstractWidget(x, y, w, h, Component.empty()) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        ChatUtilitiesClientOptions.cycleChatSearchBarPosition();
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
                        var pos = ChatUtilitiesClientOptions.getChatSearchBarPosition();
                        Component cap =
                                Component.translatable(
                                        "chat-utilities.settings.chat_search_bar_position."
                                                + pos.name().toLowerCase());
                        g.drawCenteredString(
                                Minecraft.getInstance().font,
                                cap,
                                getX() + getWidth() / 2,
                                getY() + (getHeight() - 8) / 2,
                                0xFFE8EEF8);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        btn.setTooltip(
                Tooltip.create(Component.translatable("chat-utilities.settings.chat_search_bar_position.tooltip")));
        return btn;
    }

    private AbstractWidget chatHistoryLimitSlider(int x, int y, int w, int h) {
        int min = ChatUtilitiesClientOptions.CHAT_HISTORY_LIMIT_MIN;
        int max = ChatUtilitiesClientOptions.CHAT_HISTORY_LIMIT_MAX;
        int cur = snapChatHistoryLimitLines(ChatUtilitiesClientOptions.getChatHistoryLimitLines());
        double initial = (cur - min) / (double) (max - min);
        AbstractSliderButton slider =
                new AbstractSliderButton(x, y, w, h, Component.empty(), initial) {
                    {
                        updateMessage();
                    }

                    @Override
                    protected void updateMessage() {
                        setMessage(Component.literal(linesFromSlider() + " lines"));
                    }

                    private int linesFromSlider() {
                        return Mth.clamp(
                                (int) Math.round(Mth.lerp(value, min, max)), min, max);
                    }

                    @Override
                    protected void applyValue() {
                        int snapped = snapChatHistoryLimitLines(linesFromSlider());
                        value = (snapped - min) / (double) (max - min);
                        ChatUtilitiesClientOptions.setChatHistoryLimitLines(snapped);
                        updateMessage();
                    }

                    @Override
                    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        renderFlatSliderWidget(g, this, mx, my, pt, value);
                    }

                    @Override
                    public boolean mouseScrolled(
                            double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
                        if (!active || !isMouseOver(mouseX, mouseY) || verticalAmount == 0) {
                            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
                        }
                        int step = ChatUtilitiesClientOptions.CHAT_HISTORY_LIMIT_STEP;
                        if (verticalAmount > 0) {
                            step = -step;
                        }
                        int next = snapChatHistoryLimitLines(linesFromSlider() + step);
                        value = (next - min) / (double) (max - min);
                        ChatUtilitiesClientOptions.setChatHistoryLimitLines(next);
                        updateMessage();
                        return true;
                    }
                };
        slider.setTooltip(
                Tooltip.create(Component.translatable("chat-utilities.settings.chat_history_limit.tooltip")));
        slider.active = ChatUtilitiesClientOptions.isLongerChatHistory();
        return slider;
    }

    private AbstractWidget chatPanelBackgroundOpacitySlider(int x, int y, int w, int h) {
        return chatOpacitySlider(
                x,
                y,
                w,
                h,
                () -> ChatUtilitiesClientOptions.getChatPanelBackgroundOpacityUnfocusedPercent(),
                ChatUtilitiesClientOptions::setChatPanelBackgroundOpacityUnfocusedPercent,
                "chat-utilities.settings.chat_panel_background_opacity.unfocused.value",
                "chat-utilities.settings.chat_panel_background_opacity.unfocused.tooltip");
    }

    private AbstractWidget chatPanelBackgroundFocusedOpacitySlider(int x, int y, int w, int h) {
        return chatOpacitySlider(
                x,
                y,
                w,
                h,
                () -> ChatUtilitiesClientOptions.getChatPanelBackgroundOpacityFocusedPercent(),
                ChatUtilitiesClientOptions::setChatPanelBackgroundOpacityFocusedPercent,
                "chat-utilities.settings.chat_panel_background_opacity.focused.value",
                "chat-utilities.settings.chat_panel_background_opacity.focused.tooltip");
    }

    private AbstractWidget chatBarBackgroundOpacitySlider(int x, int y, int w, int h) {
        return chatOpacitySlider(
                x,
                y,
                w,
                h,
                () -> ChatUtilitiesClientOptions.getChatBarBackgroundOpacityPercent(),
                ChatUtilitiesClientOptions::setChatBarBackgroundOpacityPercent,
                "chat-utilities.settings.chat_bar_background_opacity.value",
                "chat-utilities.settings.chat_bar_background_opacity.tooltip");
    }

    private AbstractWidget chatOpacitySlider(
            int x,
            int y,
            int w,
            int h,
            java.util.function.IntSupplier current,
            java.util.function.IntConsumer setter,
            String valueLangKey,
            String tooltipLangKey) {
        int min = ChatUtilitiesClientOptions.CHAT_PANEL_BG_OPACITY_MIN;
        int max = ChatUtilitiesClientOptions.CHAT_PANEL_BG_OPACITY_MAX;
        int cur = current.getAsInt();
        double initial = (cur - min) / (double) (max - min);
        AbstractSliderButton slider =
                new AbstractSliderButton(x, y, w, h, Component.empty(), initial) {
                    {
                        updateMessage();
                    }

                    @Override
                    protected void updateMessage() {
                        int p = Mth.clamp((int) Math.round(Mth.lerp(value, min, max)), min, max);
                        setMessage(Component.translatable(valueLangKey, p));
                    }

                    @Override
                    protected void applyValue() {
                        int snapped = Mth.clamp((int) Math.round(Mth.lerp(value, min, max)), min, max);
                        value = (snapped - min) / (double) (max - min);
                        setter.accept(snapped);
                        updateMessage();
                    }

                    @Override
                    public void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        renderFlatSliderWidget(g, this, mx, my, pt, value);
                    }

                    @Override
                    public boolean mouseScrolled(
                            double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
                        if (!active || !isMouseOver(mouseX, mouseY) || verticalAmount == 0) {
                            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
                        }
                        int step = verticalAmount > 0 ? -5 : 5;
                        int next =
                                Mth.clamp(
                                        (int) Math.round(Mth.lerp(value, min, max)) + step, min, max);
                        value = (next - min) / (double) (max - min);
                        setter.accept(next);
                        updateMessage();
                        return true;
                    }
                };
        slider.setTooltip(Tooltip.create(Component.translatable(tooltipLangKey)));
        return slider;
    }

    private AbstractWidget modPrimaryColorSettingButton(int x, int y, int w, int h) {
        AbstractWidget b =
                new AbstractWidget(x, y, w, h, Component.empty()) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        ChatUtilitiesRootScreen.this.clearPendingChatHighlightPickState();
                        ChatUtilitiesRootScreen.this.modColorPickerOverlay = ModPrimaryColorPickerOverlay.create();
                        ChatUtilitiesRootScreen.this.afterOpenColorPicker();
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        int bg = !act ? 0x25000000 : hov ? 0x55FFFFFF : 0x35000000;
                        int outline = !act ? 0x25FFFFFF : hov ? 0x70FFFFFF : 0x40FFFFFF;
                        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
                        g.renderOutline(getX(), getY(), getWidth(), getHeight(), outline);
                        int sw = ModAccentAnimator.currentArgb();
                        int sx = getX() + 6;
                        int sy = getY() + (getHeight() - 14) / 2;
                        g.fill(sx, sy, sx + 14, sy + 14, sw);
                        g.renderOutline(sx, sy, 14, 14, 0xFFAAAAAA);
                        String cap = I18n.get("chat-utilities.settings.mod_primary_color.change");
                        g.drawString(
                                Minecraft.getInstance().font,
                                cap,
                                sx + 18,
                                getY() + (getHeight() - 8) / 2,
                                0xFFBBBBCC,
                                false);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        b.setTooltip(Tooltip.create(Component.translatable("chat-utilities.settings.mod_primary_color.tooltip")));
        return b;
    }

    private AbstractWidget flatButtonChatTimestamps(int x, int y, int w, int h) {
        AbstractWidget btn =
                new SettingsBooleanToggleWidget(x, y, w, h) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        ChatUtilitiesClientOptions.toggleChatTimestampsEnabled();
                        init();
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean on = ChatUtilitiesClientOptions.isChatTimestampsEnabled();
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        renderSettingsBooleanSwitch(
                                g,
                                "opt.chatTimestamps",
                                getX(),
                                getY(),
                                getWidth(),
                                getHeight(),
                                on,
                                hov,
                                act,
                                pt);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        btn.setTooltip(Tooltip.create(Component.translatable("chat-utilities.settings.chat_timestamps.tooltip")));
        return btn;
    }

    private AbstractWidget chatTimestampColorSettingButton(int x, int y, int w, int h) {
        AbstractWidget b =
                new AbstractWidget(x, y, w, h, Component.empty()) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        ChatUtilitiesRootScreen.this.clearPendingChatHighlightPickState();
                        ChatUtilitiesRootScreen.this.modColorPickerOverlay =
                                ModPrimaryColorPickerOverlay.createTimestampRgb();
                        ChatUtilitiesRootScreen.this.afterOpenColorPicker();
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        int bg = !act ? 0x25000000 : hov ? 0x55FFFFFF : 0x35000000;
                        int outline = !act ? 0x25FFFFFF : hov ? 0x70FFFFFF : 0x40FFFFFF;
                        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
                        g.renderOutline(getX(), getY(), getWidth(), getHeight(), outline);
                        int rgb = ChatUtilitiesClientOptions.getChatTimestampColorRgb() | 0xFF000000;
                        int sx = getX() + 6;
                        int sy = getY() + (getHeight() - 14) / 2;
                        g.fill(sx, sy, sx + 14, sy + 14, rgb);
                        g.renderOutline(sx, sy, 14, 14, 0xFFAAAAAA);
                        String cap = I18n.get("chat-utilities.settings.chat_timestamp_color.change");
                        g.drawString(
                                Minecraft.getInstance().font,
                                cap,
                                sx + 18,
                                getY() + (getHeight() - 8) / 2,
                                0xFFBBBBCC,
                                false);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        b.setTooltip(Tooltip.create(Component.translatable("chat-utilities.settings.chat_timestamp_color.tooltip")));
        return b;
    }

    private AbstractWidget stackedMessageColorSettingButton(int x, int y, int w, int h) {
        AbstractWidget b =
                new AbstractWidget(x, y, w, h, Component.empty()) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        ChatUtilitiesRootScreen.this.clearPendingChatHighlightPickState();
                        ChatUtilitiesRootScreen.this.modColorPickerOverlay =
                                ModPrimaryColorPickerOverlay.createStackedMessageRgb();
                        ChatUtilitiesRootScreen.this.afterOpenColorPicker();
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        int bg = !act ? 0x25000000 : hov ? 0x55FFFFFF : 0x35000000;
                        int outline = !act ? 0x25FFFFFF : hov ? 0x70FFFFFF : 0x40FFFFFF;
                        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
                        g.renderOutline(getX(), getY(), getWidth(), getHeight(), outline);
                        int rgb = ChatUtilitiesClientOptions.getStackedMessageColorRgb() | 0xFF000000;
                        int sx = getX() + 6;
                        int sy = getY() + (getHeight() - 14) / 2;
                        g.fill(sx, sy, sx + 14, sy + 14, rgb);
                        g.renderOutline(sx, sy, 14, 14, 0xFFAAAAAA);
                        String cap = I18n.get("chat-utilities.settings.stacked_message.color.change");
                        g.drawString(
                                Minecraft.getInstance().font,
                                cap,
                                sx + 18,
                                getY() + (getHeight() - 8) / 2,
                                0xFFBBBBCC,
                                false);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        b.setTooltip(Tooltip.create(Component.translatable("chat-utilities.settings.stacked_message.color.tooltip")));
        return b;
    }

    private AbstractWidget unreadBadgeColorSettingButton(int x, int y, int w, int h) {
        AbstractWidget b =
                new AbstractWidget(x, y, w, h, Component.empty()) {
                    @Override
                    public void onClick(MouseButtonEvent event, boolean dbl) {
                        ChatUtilitiesRootScreen.this.clearPendingChatHighlightPickState();
                        ChatUtilitiesRootScreen.this.modColorPickerOverlay =
                                ModPrimaryColorPickerOverlay.createUnreadBadgeRgb();
                        ChatUtilitiesRootScreen.this.afterOpenColorPicker();
                    }

                    @Override
                    protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                        boolean hov = this.isHovered();
                        boolean act = this.active;
                        int bg = !act ? 0x25000000 : hov ? 0x55FFFFFF : 0x35000000;
                        int outline = !act ? 0x25FFFFFF : hov ? 0x70FFFFFF : 0x40FFFFFF;
                        g.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), bg);
                        g.renderOutline(getX(), getY(), getWidth(), getHeight(), outline);
                        int rgb = ChatUtilitiesClientOptions.getTabUnreadBadgeColorRgb() | 0xFF000000;
                        int sx = getX() + 6;
                        int sy = getY() + (getHeight() - 14) / 2;
                        g.fill(sx, sy, sx + 14, sy + 14, rgb);
                        g.renderOutline(sx, sy, 14, 14, 0xFFAAAAAA);
                        String cap = I18n.get("chat-utilities.settings.unread_badge.color.change");
                        g.drawString(
                                Minecraft.getInstance().font,
                                cap,
                                sx + 18,
                                getY() + (getHeight() - 8) / 2,
                                0xFFBBBBCC,
                                false);
                    }

                    @Override
                    public void updateWidgetNarration(NarrationElementOutput n) {
                        defaultButtonNarrationText(n);
                    }
                };
        b.setTooltip(Tooltip.create(Component.translatable("chat-utilities.settings.unread_badge.color.tooltip")));
        return b;
    }

    private AbstractWidget flatButtonClickToCopy(int x, int y, int w, int h) {
        AbstractWidget c =
                new SettingsBooleanToggleWidget(x, y, w, h) {
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
                        renderSettingsBooleanSwitch(
                                g,
                                "opt.clickToCopy",
                                getX(),
                                getY(),
                                getWidth(),
                                getHeight(),
                                on,
                                hov,
                                act,
                                pt);
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

    private AbstractWidget flatButtonFullscreenImageClickBinding(int x, int y, int width, int h) {
        AbstractWidget bindBtn =
                new AbstractWidget(x, y, width, h, Component.literal(fullscreenClickBindCaptionOnly())) {
                    @Override
                    protected boolean isValidClickButton(MouseButtonInfo info) {
                        int b = info.button();
                        return b == 0 || b == 1;
                    }

                    @Override
                    public void onClick(MouseButtonEvent event, boolean doubleClick) {
                        if (event.button() == 1) {
                            ChatUtilitiesClientOptions.setFullscreenImagePreviewClickBinding(
                                    ChatUtilitiesClientOptions.ClickMouseBinding.defaultFullscreenImagePreview());
                            rebindingOpenMenuKey = false;
                            rebindingFullscreenImageClick = false;
                            rebindingCopyPlain = false;
                            rebindingCopyFormatted = false;
                            init();
                            return;
                        }
                        rebindingOpenMenuKey = false;
                        rebindingCopyPlain = false;
                        rebindingCopyFormatted = false;
                        rebindingFullscreenImageClick = true;
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
                                Component.literal(fullscreenClickBindCaptionOnly()),
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
                        Component.translatable("chat-utilities.settings.image_preview_fullscreen_click.tooltip")));
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

    private String fullscreenClickBindCaptionOnly() {
        if (rebindingFullscreenImageClick) {
            return Component.translatable("chat-utilities.settings.rebind_click_prompt").getString();
        }
        return describeCopyMouseBinding(ChatUtilitiesClientOptions.getFullscreenImagePreviewClickBinding());
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

    private boolean settingsLayoutRowOn(SettingsRow r) {
        return settingsFormRowY[r.ordinal()] >= 0;
    }

    private boolean settingsLayoutSecOn(int sec) {
        return sec >= 0 && sec < SETTINGS_SEC_COUNT && settingsSectionTitleY[sec] >= 0;
    }

    private int settingsLayoutRowY(SettingsRow r) {
        return settingsFormRowY[r.ordinal()];
    }

    private int settingsLayoutSecTitleY(int sec) {
        return settingsSectionTitleY[sec];
    }

    private int settingsClipYCandidate(SettingsRow r) {
        int ly = settingsLayoutRowY(r);
        return ly >= 0 ? settingsScrolledY(ly) : settingsScrolledY(bodyY());
    }

    private void settingsFinishRowWidget(AbstractWidget w, SettingsRow r) {
        w.visible = settingsLayoutRowOn(r);
        if (w.visible) {
            int ly = settingsLayoutRowY(r);
            if (ly >= 0) {
                settingsScrollWidgetLogicalY.put(w, ly);
            }
        }
        addSettingsScrollClipWidget(w);
    }

    private void settingsFinishRowClip(AbstractWidget w, SettingsRow r) {
        settingsFinishRowWidget(w, r);
    }

    private void settingsDrawSectionHeaderIf(GuiGraphics g, int fx, int fr, int sec, String i18nTitleKey) {
        if (!settingsLayoutSecOn(sec)) {
            return;
        }
        int y = settingsScrolledY(settingsLayoutSecTitleY(sec));
        String title = I18n.get(i18nTitleKey);
        drawSettingsSectionHeader(g, fx, fr, y, title);
    }

    private void settingsDrawRowLabel(GuiGraphics g, SettingsRow row, Component text, int fx) {
        if (!settingsLayoutRowOn(row)) {
            return;
        }
        g.drawString(
                this.font,
                text,
                fx,
                settingsScrolledY(settingsLayoutRowY(row)) + 7,
                ChatUtilitiesScreenLayout.TEXT_LABEL,
                false);
    }

    private void rebuildSettingsFormLayout() {
        Arrays.fill(settingsFormRowY, -1);
        Arrays.fill(settingsSectionTitleY, -1);
        String needle =
                settingsSearchQuery == null ? "" : settingsSearchQuery.strip().toLowerCase(Locale.ROOT);
        int hdr = settingsSectionHeaderH();
        int gap = SETTINGS_SECTION_GAP;
        final int rowH = 28;
        int y = bodyY() + SETTINGS_SEARCH_RESERVE;
        int furthest = y;

        java.util.function.BiPredicate<String, String[]> hit =
                (key, extra) -> {
                    if (needle.isEmpty()) {
                        return true;
                    }
                    StringBuilder b = new StringBuilder();
                    if (key != null && !key.isEmpty()) {
                        b.append(I18n.get(key)).append(' ');
                    }
                    if (extra != null) {
                        for (String s : extra) {
                            if (s != null) {
                                b.append(s).append(' ');
                            }
                        }
                    }
                    return b.toString().toLowerCase(Locale.ROOT).contains(needle);
                };

        boolean cOpen =
                hit.test(
                        "key.chatutilities.open_menu",
                        new String[] {"open", "menu", "keybind", "chatutilities", "shortcut"});
        boolean cPeek =
                hit.test(
                        "key.chatutilities.chat_peek",
                        new String[] {"peek", "expand", "hold", "temporary", "chat"});
        boolean cPln =
                hit.test(
                        "chat-utilities.settings.copy_plain_bind",
                        new String[] {"plain", "copy", "text", "click", "mouse"});
        boolean cFmt =
                hit.test(
                        "chat-utilities.settings.copy_formatted_bind",
                        new String[] {"formatted", "copy", "codes", "click", "mouse"});
        boolean cFull =
                hit.test(
                        "key.chatutilities.image_preview_fullscreen",
                        new String[] {"fullscreen", "image", "preview", "click", "mouse"});
        if (cOpen || cPeek || cPln || cFmt || cFull) {
            settingsSectionTitleY[SETTINGS_SEC_CONTROLS] = y;
            y += hdr + 6;
            if (cOpen) {
                settingsFormRowY[SettingsRow.OPEN_MENU.ordinal()] = y;
                y += rowH;
            }
            if (cPeek) {
                settingsFormRowY[SettingsRow.CHAT_PEEK.ordinal()] = y;
                y += rowH;
            }
            if (cPln) {
                settingsFormRowY[SettingsRow.COPY_PLAIN_BIND.ordinal()] = y;
                y += rowH;
            }
            if (cFmt) {
                settingsFormRowY[SettingsRow.COPY_FORMATTED_BIND.ordinal()] = y;
                y += rowH;
            }
            if (cFull) {
                settingsFormRowY[SettingsRow.FULLSCREEN_IMAGE_CLICK.ordinal()] = y;
                y += rowH;
            }
            furthest = Math.max(furthest, y);
            y += gap;
        }

        boolean mCheck =
                hit.test(
                        "chat-utilities.settings.check_for_updates",
                        new String[] {"update", "github", "release", "version", "download"});
        boolean mSec =
                hit.test(
                        "chat-utilities.settings.section.mod",
                        new String[] {"mod", "settings", "utilities", "chat"});
        boolean o0 =
                hit.test("chat-utilities.settings.primary_color", new String[] {"accent", "color", "primary", "rgb"});
        if (mCheck || mSec || o0) {
            settingsSectionTitleY[SETTINGS_SEC_MOD] = y;
            y += hdr + 6;
            if (mCheck) {
                settingsFormRowY[SettingsRow.CHECK_FOR_UPDATES.ordinal()] = y;
                y += rowH;
            }
            if (o0) {
                settingsFormRowY[SettingsRow.MOD_PRIMARY_COLOR.ordinal()] = y;
                y += rowH;
            }
            furthest = Math.max(furthest, y);
            y += gap;
        }

        boolean kClick = hit.test("chat-utilities.settings.click_to_copy", new String[] {"click", "copy", "pick"});
        boolean kStyle =
                hit.test(
                        "chat-utilities.settings.copy_formatted_style",
                        new String[] {"style", "formatted", "hover"});
        if (kClick || kStyle) {
            settingsSectionTitleY[SETTINGS_SEC_CLICK_COPY] = y;
            y += hdr + 6;
            if (kClick) {
                settingsFormRowY[SettingsRow.CLICK_TO_COPY.ordinal()] = y;
                y += rowH;
            }
            if (kStyle) {
                settingsFormRowY[SettingsRow.COPY_FORMATTED_STYLE.ordinal()] = y;
                y += rowH;
            }
            furthest = Math.max(furthest, y);
            y += gap;
        }

        boolean s0 = hit.test("chat-utilities.settings.smooth_chat", new String[] {"smooth", "animation", "slide"});
        boolean s1 =
                hit.test("chat-utilities.settings.smooth_chat_delay", new String[] {"smooth", "delay", "fade", "ms"});
        boolean s2 =
                hit.test(
                        "chat-utilities.settings.smooth_chat_bar_open",
                        new String[] {"smooth", "bar", "open", "input"});
        if (s0 || s1 || s2) {
            settingsSectionTitleY[SETTINGS_SEC_SMOOTH] = y;
            y += hdr + 6;
            if (s0) {
                settingsFormRowY[SettingsRow.SMOOTH_CHAT.ordinal()] = y;
                y += rowH;
            }
            if (s1) {
                settingsFormRowY[SettingsRow.SMOOTH_CHAT_DELAY_MS.ordinal()] = y;
                y += rowH;
            }
            if (s2) {
                settingsFormRowY[SettingsRow.SMOOTH_CHAT_BAR_OPEN_MS.ordinal()] = y;
                y += rowH;
            }
            furthest = Math.max(furthest, y);
            y += gap;
        }

        boolean h0 =
                hit.test("chat-utilities.settings.longer_chat_history", new String[] {"longer", "history", "lines"});
        boolean h1 =
                hit.test("chat-utilities.settings.chat_history_limit", new String[] {"limit", "messages", "history"});
        if (h0 || h1) {
            settingsSectionTitleY[SETTINGS_SEC_HISTORY] = y;
            y += hdr + 6;
            if (h0) {
                settingsFormRowY[SettingsRow.LONGER_CHAT_HISTORY.ordinal()] = y;
                y += rowH;
            }
            if (h1) {
                settingsFormRowY[SettingsRow.CHAT_HISTORY_LIMIT.ordinal()] = y;
                y += rowH;
            }
            furthest = Math.max(furthest, y);
            y += gap;
        }

        boolean st0 =
                hit.test(
                        "chat-utilities.settings.stack_repeated_messages",
                        new String[] {"stack", "repeat", "duplicate", "counter"});
        boolean st1 =
                hit.test("chat-utilities.settings.stacked_message.color", new String[] {"stack", "color", "counter"});
        boolean st2 =
                hit.test(
                        "chat-utilities.settings.stacked_message.format",
                        new String[] {"stack", "format", "%amount%"});
        if (st0 || st1 || st2) {
            settingsSectionTitleY[SETTINGS_SEC_STACK] = y;
            y += hdr + 6;
            if (st0) {
                settingsFormRowY[SettingsRow.STACK_REPEATED_MESSAGES.ordinal()] = y;
                y += rowH;
            }
            if (st1) {
                settingsFormRowY[SettingsRow.STACKED_MESSAGE_COLOR.ordinal()] = y;
                y += rowH;
            }
            if (st2) {
                settingsFormRowY[SettingsRow.STACKED_MESSAGE_FORMAT.ordinal()] = y;
                y += rowH;
            }
            furthest = Math.max(furthest, y);
            y += gap;
        }

        boolean t0 =
                hit.test("chat-utilities.settings.chat_timestamps", new String[] {"timestamp", "time", "prefix"});
        boolean t1 =
                hit.test("chat-utilities.settings.chat_timestamp_color", new String[] {"timestamp", "color"});
        boolean t2 =
                hit.test(
                        "chat-utilities.settings.chat_timestamp_format",
                        new String[] {"timestamp", "format", "pattern", "date"});
        if (t0 || t1 || t2) {
            settingsSectionTitleY[SETTINGS_SEC_TIMESTAMP] = y;
            y += hdr + 6;
            if (t0) {
                settingsFormRowY[SettingsRow.CHAT_TIMESTAMPS.ordinal()] = y;
                y += rowH;
            }
            if (t1) {
                settingsFormRowY[SettingsRow.CHAT_TIMESTAMP_COLOR.ordinal()] = y;
                y += rowH;
            }
            if (t2) {
                settingsFormRowY[SettingsRow.CHAT_TIMESTAMP_FORMAT.ordinal()] = y;
                y += rowH;
            }
            furthest = Math.max(furthest, y);
            y += gap;
        }

        boolean q0 =
                hit.test("chat-utilities.settings.chat_search_bar", new String[] {"search", "find", "bar"});
        boolean q1 =
                hit.test(
                        "chat-utilities.settings.chat_search_bar_position",
                        new String[] {"search", "position", "corner"});
        if (q0 || q1) {
            settingsSectionTitleY[SETTINGS_SEC_CHAT_SEARCH] = y;
            y += hdr + 6;
            if (q0) {
                settingsFormRowY[SettingsRow.CHAT_SEARCH_BAR.ordinal()] = y;
                y += rowH;
            }
            if (q1) {
                settingsFormRowY[SettingsRow.CHAT_SEARCH_BAR_POSITION.ordinal()] = y;
                y += rowH;
            }
            furthest = Math.max(furthest, y);
            y += gap;
        }

        boolean i0 =
                hit.test(
                        "chat-utilities.settings.image_preview.enabled",
                        new String[] {"image", "preview", "hover", "link"});
        boolean i1 =
                hit.test(
                        "chat-utilities.settings.image_preview.whitelist_button_label",
                        new String[] {"whitelist", "domain", "allowed", "hosts"});
        boolean i2 =
                hit.test(
                        "chat-utilities.settings.image_preview.allow_untrusted_domains",
                        new String[] {"untrusted", "domain", "security", "http"});
        if (i0 || i1 || i2) {
            settingsSectionTitleY[SETTINGS_SEC_IMAGE] = y;
            y += hdr + 6;
            if (i0) {
                settingsFormRowY[SettingsRow.IMAGE_PREVIEW_ENABLED.ordinal()] = y;
                y += rowH;
            }
            if (i1) {
                settingsFormRowY[SettingsRow.IMAGE_PREVIEW_WHITELIST.ordinal()] = y;
                y += rowH;
            }
            if (i2) {
                settingsFormRowY[SettingsRow.IMAGE_PREVIEW_ALLOW_UNTRUSTED.ordinal()] = y;
                y += rowH;
            }
            furthest = Math.max(furthest, y);
            y += gap;
        }

        boolean y0 =
                hit.test(
                        "chat-utilities.settings.chat_symbol_selector",
                        new String[] {"symbol", "emoji", "picker", "special"});
        boolean y1 =
                hit.test(
                        "chat-utilities.settings.symbol_palette_insert_style",
                        new String[] {"symbol", "insert", "replace", "palette"});
        if (y0 || y1) {
            settingsSectionTitleY[SETTINGS_SEC_SYMBOL] = y;
            y += hdr + 6;
            if (y0) {
                settingsFormRowY[SettingsRow.CHAT_SYMBOL_SELECTOR.ordinal()] = y;
                y += rowH;
            }
            if (y1) {
                settingsFormRowY[SettingsRow.SYMBOL_PALETTE_INSERT_STYLE.ordinal()] = y;
                y += rowH;
            }
            furthest = Math.max(furthest, y);
            y += gap;
        }

        boolean u0 =
                hit.test("chat-utilities.settings.tab_unread_badges", new String[] {"tab", "unread", "badge", "count"});
        boolean u1 =
                hit.test(
                        "chat-utilities.settings.always_show_unread_tabs",
                        new String[] {"always", "show", "unread", "tabs", "hud"});
        boolean u3 =
                hit.test(
                        "chat-utilities.settings.unread_badge.style",
                        new String[] {"unread", "badge", "style", "circle", "heart"});
        boolean u2 =
                hit.test(
                        "chat-utilities.settings.unread_badge.color",
                        new String[] {"unread", "badge", "color", "red", "rgb"});
        if (u0 || u1 || u2 || u3) {
            settingsSectionTitleY[SETTINGS_SEC_UNREAD] = y;
            y += hdr + 6;
            if (u0) {
                settingsFormRowY[SettingsRow.TAB_UNREAD_BADGES.ordinal()] = y;
                y += rowH;
            }
            if (u1) {
                settingsFormRowY[SettingsRow.ALWAYS_SHOW_UNREAD_TABS.ordinal()] = y;
                y += rowH;
            }
            if (u3) {
                settingsFormRowY[SettingsRow.UNREAD_BADGE_STYLE.ordinal()] = y;
                y += rowH;
            }
            if (u2) {
                settingsFormRowY[SettingsRow.UNREAD_BADGE_COLOR.ordinal()] = y;
                y += rowH;
            }
            furthest = Math.max(furthest, y);
            y += gap;
        }

        boolean o1 =
                hit.test(
                        "chat-utilities.settings.chat_panel_background_opacity.unfocused",
                        new String[] {"background", "panel", "alpha", "unfocused", "opacity"});
        boolean o2 =
                hit.test(
                        "chat-utilities.settings.chat_panel_background_opacity.focused",
                        new String[] {"background", "panel", "focused", "opacity"});
        boolean o3 =
                hit.test(
                        "chat-utilities.settings.chat_bar_background_opacity",
                        new String[] {"bar", "background", "opacity", "hud"});
        boolean o4 = hit.test("chat-utilities.settings.chat_text_shadow", new String[] {"shadow", "text", "font"});
        boolean o5 =
                hit.test("chat-utilities.settings.chat_bar_menu_button", new String[] {"menu", "button", "bar", "icon"});
        boolean o8 =
                hit.test(
                        "chat-utilities.settings.ignore_self_chat_actions",
                        new String[] {"ignore", "self", "actions", "spam"});
        boolean o9 =
                hit.test(
                        "chat-utilities.settings.preserve_vanilla_chat_log",
                        new String[] {
                            "relog", "reconnect", "disconnect", "history", "clear", "preserve", "vanilla", "main", "chat"
                        });
        if (o1 || o2 || o3 || o4 || o5 || o8 || o9) {
            settingsSectionTitleY[SETTINGS_SEC_OTHER] = y;
            y += hdr + 6;
            if (o1) {
                settingsFormRowY[SettingsRow.CHAT_PANEL_BACKGROUND_OPACITY.ordinal()] = y;
                y += rowH;
            }
            if (o2) {
                settingsFormRowY[SettingsRow.CHAT_PANEL_BACKGROUND_FOCUSED_OPACITY.ordinal()] = y;
                y += rowH;
            }
            if (o3) {
                settingsFormRowY[SettingsRow.CHAT_BAR_BACKGROUND_OPACITY.ordinal()] = y;
                y += rowH;
            }
            if (o4) {
                settingsFormRowY[SettingsRow.CHAT_TEXT_SHADOW.ordinal()] = y;
                y += rowH;
            }
            if (o5) {
                settingsFormRowY[SettingsRow.CHAT_BAR_MENU_BUTTON.ordinal()] = y;
                y += rowH;
            }
            if (o8) {
                settingsFormRowY[SettingsRow.IGNORE_SELF_CHAT_ACTIONS.ordinal()] = y;
                y += rowH;
            }
            if (o9) {
                settingsFormRowY[SettingsRow.PRESERVE_VANILLA_CHAT_LOG_ON_LEAVE.ordinal()] = y;
                y += rowH;
            }
            furthest = Math.max(furthest, y);
            y += gap;
        }

        boolean pHead =
                hit.test(
                        "chat-utilities.settings.section.profiles",
                        new String[] {"profile", "import", "export", "json", "backup"});
        boolean pImp =
                hit.test("chat-utilities.settings.import_profiles.tooltip", new String[] {"import", "merge", "json"});
        boolean pExp =
                hit.test("chat-utilities.settings.export_profiles.tooltip", new String[] {"export", "save", "clipboard"});
        boolean pLaby = hit.test("", new String[] {"labymod", "laby"});
        boolean showImpRow = pImp || pExp || pHead;
        boolean showLabyRow = pLaby || pHead;
        if (showImpRow || showLabyRow) {
            settingsSectionTitleY[SETTINGS_SEC_PROFILES] = y;
            y += hdr + 6;
            if (showImpRow) {
                settingsFormRowY[SettingsRow.PROFILES_IMPORT_EXPORT_ROW.ordinal()] = y;
                y += rowH;
            }
            if (showLabyRow) {
                settingsFormRowY[SettingsRow.PROFILES_LABY_ROW.ordinal()] = y;
                y += rowH;
            }
            furthest = Math.max(furthest, y);
        }

        settingsFormContentBottom = furthest + 48;
    }

    private int settingsControlsSectionTitleY() {
        return settingsLayoutSecTitleY(SETTINGS_SEC_CONTROLS);
    }

    private int settingsOpenMenuKeyRowY() {
        return settingsLayoutRowY(SettingsRow.OPEN_MENU);
    }

    private int settingsChatPeekKeyRowY() {
        return settingsLayoutRowY(SettingsRow.CHAT_PEEK);
    }

    private int settingsCopyPlainBindRowY() {
        return settingsLayoutRowY(SettingsRow.COPY_PLAIN_BIND);
    }

    private int settingsCopyFormattedBindRowY() {
        return settingsLayoutRowY(SettingsRow.COPY_FORMATTED_BIND);
    }

    private int settingsFullscreenImageClickRowY() {
        return settingsLayoutRowY(SettingsRow.FULLSCREEN_IMAGE_CLICK);
    }

    /** Click to copy section (labels in {@link #renderSettingsPanelExtras}). */
    private int settingsClickCopySectionTitleY() {
        return settingsLayoutSecTitleY(SETTINGS_SEC_CLICK_COPY);
    }

    private int settingsClickCopyFormY() {
        return settingsClickCopySectionTitleY() + settingsSectionHeaderH() + 6;
    }

    private int settingsClickToCopyRowY() {
        return settingsLayoutRowY(SettingsRow.CLICK_TO_COPY);
    }

    private int settingsCopyFormattedStyleRowY() {
        return settingsLayoutRowY(SettingsRow.COPY_FORMATTED_STYLE);
    }

    private int settingsSmoothChatSectionTitleY() {
        return settingsLayoutSecTitleY(SETTINGS_SEC_SMOOTH);
    }

    private int settingsSmoothChatFormY() {
        return settingsSmoothChatSectionTitleY() + settingsSectionHeaderH() + 6;
    }

    private int settingsSmoothChatRowY() {
        return settingsLayoutRowY(SettingsRow.SMOOTH_CHAT);
    }

    private int settingsSmoothChatDelayRowY() {
        return settingsLayoutRowY(SettingsRow.SMOOTH_CHAT_DELAY_MS);
    }

    private int settingsSmoothChatBarOpenRowY() {
        return settingsLayoutRowY(SettingsRow.SMOOTH_CHAT_BAR_OPEN_MS);
    }

    private int settingsHistorySectionTitleY() {
        return settingsLayoutSecTitleY(SETTINGS_SEC_HISTORY);
    }

    private int settingsHistoryFormY() {
        return settingsHistorySectionTitleY() + settingsSectionHeaderH() + 6;
    }

    private int settingsLongerChatHistoryRowY() {
        return settingsLayoutRowY(SettingsRow.LONGER_CHAT_HISTORY);
    }

    private int settingsChatHistoryLimitRowY() {
        return settingsLayoutRowY(SettingsRow.CHAT_HISTORY_LIMIT);
    }

    private int settingsMessageStackingSectionTitleY() {
        return settingsLayoutSecTitleY(SETTINGS_SEC_STACK);
    }

    private int settingsMessageStackingFormY() {
        return settingsMessageStackingSectionTitleY() + settingsSectionHeaderH() + 6;
    }

    private int settingsStackRepeatedMessagesRowY() {
        return settingsLayoutRowY(SettingsRow.STACK_REPEATED_MESSAGES);
    }

    private int settingsStackedMessageColorRowY() {
        return settingsLayoutRowY(SettingsRow.STACKED_MESSAGE_COLOR);
    }

    private int settingsStackedMessageFormatRowY() {
        return settingsLayoutRowY(SettingsRow.STACKED_MESSAGE_FORMAT);
    }

    private int settingsTimestampSectionTitleY() {
        return settingsLayoutSecTitleY(SETTINGS_SEC_TIMESTAMP);
    }

    private int settingsTimestampFormY() {
        return settingsTimestampSectionTitleY() + settingsSectionHeaderH() + 6;
    }

    private int settingsTimestampShowRowY() {
        return settingsLayoutRowY(SettingsRow.CHAT_TIMESTAMPS);
    }

    private int settingsTimestampColorRowY() {
        return settingsLayoutRowY(SettingsRow.CHAT_TIMESTAMP_COLOR);
    }

    private int settingsTimestampFormatRowY() {
        return settingsLayoutRowY(SettingsRow.CHAT_TIMESTAMP_FORMAT);
    }

    private int settingsChatSearchSectionTitleY() {
        return settingsLayoutSecTitleY(SETTINGS_SEC_CHAT_SEARCH);
    }

    private int settingsChatSearchFormY() {
        return settingsChatSearchSectionTitleY() + settingsSectionHeaderH() + 6;
    }

    private int settingsChatSearchEnabledRowY() {
        return settingsLayoutRowY(SettingsRow.CHAT_SEARCH_BAR);
    }

    private int settingsChatSearchPositionRowY() {
        return settingsLayoutRowY(SettingsRow.CHAT_SEARCH_BAR_POSITION);
    }

    private int settingsImagePreviewSectionTitleY() {
        return settingsLayoutSecTitleY(SETTINGS_SEC_IMAGE);
    }

    private int settingsImagePreviewFormY() {
        return settingsImagePreviewSectionTitleY() + settingsSectionHeaderH() + 6;
    }

    private int settingsImagePreviewEnabledRowY() {
        return settingsLayoutRowY(SettingsRow.IMAGE_PREVIEW_ENABLED);
    }

    private int settingsImagePreviewWhitelistRowY() {
        return settingsLayoutRowY(SettingsRow.IMAGE_PREVIEW_WHITELIST);
    }

    private int settingsImagePreviewAllowUntrustedDomainsRowY() {
        return settingsLayoutRowY(SettingsRow.IMAGE_PREVIEW_ALLOW_UNTRUSTED);
    }

    private int settingsSymbolSectionTitleY() {
        return settingsLayoutSecTitleY(SETTINGS_SEC_SYMBOL);
    }

    private int settingsSymbolSectionFormY() {
        return settingsSymbolSectionTitleY() + settingsSectionHeaderH() + 6;
    }

    private int settingsChatSymbolSelectorRowY() {
        return settingsLayoutRowY(SettingsRow.CHAT_SYMBOL_SELECTOR);
    }

    private int settingsSymbolPaletteInsertStyleRowY() {
        return settingsLayoutRowY(SettingsRow.SYMBOL_PALETTE_INSERT_STYLE);
    }

    private int settingsOtherSectionTitleY() {
        return settingsLayoutSecTitleY(SETTINGS_SEC_OTHER);
    }

    private int settingsOtherSectionFormY() {
        return settingsOtherSectionTitleY() + settingsSectionHeaderH() + 6;
    }

    private int settingsChatPanelBackgroundRowY() {
        return settingsLayoutRowY(SettingsRow.CHAT_PANEL_BACKGROUND_OPACITY);
    }

    private int settingsChatPanelBackgroundFocusedRowY() {
        return settingsLayoutRowY(SettingsRow.CHAT_PANEL_BACKGROUND_FOCUSED_OPACITY);
    }

    private int settingsChatBarBackgroundRowY() {
        return settingsLayoutRowY(SettingsRow.CHAT_BAR_BACKGROUND_OPACITY);
    }

    private int settingsShadowRowY() {
        return settingsLayoutRowY(SettingsRow.CHAT_TEXT_SHADOW);
    }

    private int settingsChatBarMenuButtonRowY() {
        return settingsLayoutRowY(SettingsRow.CHAT_BAR_MENU_BUTTON);
    }

    private int settingsModPrimaryColorRowY() {
        return settingsLayoutRowY(SettingsRow.MOD_PRIMARY_COLOR);
    }

    private int settingsTabUnreadBadgesRowY() {
        return settingsLayoutRowY(SettingsRow.TAB_UNREAD_BADGES);
    }

    private int settingsAlwaysShowUnreadTabsRowY() {
        return settingsLayoutRowY(SettingsRow.ALWAYS_SHOW_UNREAD_TABS);
    }

    private int settingsIgnoreSelfChatActionsRowY() {
        return settingsLayoutRowY(SettingsRow.IGNORE_SELF_CHAT_ACTIONS);
    }

    private int settingsProfilesTitleY() {
        return settingsLayoutSecTitleY(SETTINGS_SEC_PROFILES);
    }

    private int settingsProfilesFormY() {
        return settingsLayoutRowY(SettingsRow.PROFILES_IMPORT_EXPORT_ROW);
    }

    /** Bottom Y (exclusive) of the scrollable settings block in logical (unscrolled) coordinates. */
    private int settingsScrollableBottomLogical() {
        return settingsFormContentBottom;
    }
    private int settingsScrollViewportTop() {
        return bodyY();
    }

    private int settingsScrollViewportBottom() {
        return footerY() - 6;
    }

    private int settingsScrollableContentHeight() {
        return settingsScrollableBottomLogical() - settingsScrollViewportTop();
    }

    private int settingsScrollViewportHeight() {
        return Math.max(0, settingsScrollViewportBottom() - settingsScrollViewportTop());
    }

    private int settingsMaxContentScroll() {
        return Math.max(0, settingsScrollableContentHeight() - settingsScrollViewportHeight());
    }

    private int settingsScrolledY(int logicalY) {
        return logicalY - settingsContentScroll;
    }

    private void applySettingsScrollToWidgets() {
        for (Map.Entry<AbstractWidget, Integer> e : settingsScrollWidgetLogicalY.entrySet()) {
            AbstractWidget w = e.getKey();
            Integer ly = e.getValue();
            if (ly != null && ly >= 0) {
                w.setY(settingsScrolledY(ly));
            }
        }
    }

    private void addSettingsScrollClipWidget(AbstractWidget w) {
        addRenderableWidget(w);
        settingsScrollClipRenderables.add(w);
    }

    private void addChatWindowsScrollClipWidget(AbstractWidget w) {
        addRenderableWidget(w);
        chatWindowsScrollClipRenderables.add(w);
        chatWindowsScrollWidgetLogicalY.put(w, w.getY() + chatWindowsListScrollPixels);
    }

    private void addChatWindowsNonClipWidget(AbstractWidget w) {
        addRenderableWidget(w);
        chatWindowsNonClipRenderables.add(w);
    }

    private int chatWindowsScrollViewportTop() {
        return bodyY();
    }

    private int chatWindowsScrollViewportBottom() {
        return footerY() - WIN_LIST_FOOTER_GAP;
    }

    private int chatWindowsScrollViewportHeight() {
        return Math.max(0, chatWindowsScrollViewportBottom() - chatWindowsScrollViewportTop());
    }

    private int chatWindowsMaxContentScroll() {
        return Math.max(0, chatWindowsTotalListHeight - chatWindowsListViewportHeight);
    }

    private int chatWindowsScrolledY(int logicalY) {
        return logicalY - chatWindowsListScrollPixels;
    }

    private void applyChatWindowsScrollToWidgets() {
        for (Map.Entry<AbstractWidget, Integer> e : chatWindowsScrollWidgetLogicalY.entrySet()) {
            AbstractWidget w = e.getKey();
            Integer ly = e.getValue();
            if (ly != null && ly >= 0) {
                w.setY(chatWindowsScrolledY(ly));
            }
        }
    }

    private boolean menuTabRectContains(MenuTabRect rc, double mx, double my) {
        int s = chatWindowsListScrollPixels;
        return mx >= rc.l()
                && mx < rc.r()
                && my >= rc.t() - s
                && my < rc.b() - s;
    }

    private void relayoutChatWindowsDuringThinThumbDrag() {
        double anchorScroll = thinScrollDragAnchorScroll;
        int anchorMy = thinScrollDragAnchorMy;
        applyChatWindowsScrollToWidgets();
        thinScrollDrag = ThinScrollDrag.CHAT_WINDOWS_THUMB;
        thinScrollDragAnchorScroll = anchorScroll;
        thinScrollDragAnchorMy = anchorMy;
        int vt = chatWindowsScrollViewportTop();
        int vh = chatWindowsListViewportHeight;
        int ch = chatWindowsTotalListHeight;
        ThinScrollbar.Metrics m = ThinScrollbar.Metrics.compute(vt, vh, ch, chatWindowsListScrollPixels);
        if (m != null) {
            thinScrollDragMaxTravel = m.maxTravel;
            thinScrollDragMaxScroll = m.maxScroll;
        }
    }

    private void relayoutChatWindowsDuringContentDragScroll() {
        int anchorMy = chatWindowsContentDragAnchorMy;
        int anchorScroll = chatWindowsContentDragAnchorScroll;
        applyChatWindowsScrollToWidgets();
        chatWindowsContentDragScroll = true;
        chatWindowsContentDragAnchorMy = anchorMy;
        chatWindowsContentDragAnchorScroll = anchorScroll;
    }

    private void relayoutChatWindowsDuringMenuTabDrag() {
        String wid = menuDragWindowId;
        String tid = menuDragTabId;
        int from = menuDragFromIndex;
        int hover = menuDragHoverIndex;
        double pressX = menuDragPressX;
        double pressY = menuDragPressY;
        boolean didMove = menuDragDidMove;
        init();
        menuDragWindowId = wid;
        menuDragTabId = tid;
        menuDragFromIndex = from;
        menuDragHoverIndex = hover;
        menuDragPressX = pressX;
        menuDragPressY = pressY;
        menuDragDidMove = didMove;
    }

    private void renderSettingsPanelExtras(GuiGraphics g) {
        if (activePanel != Panel.SETTINGS) {
            return;
        }
        int fx = contentLeft();
        int fr = contentRight() - scrollbarReserve(settingsMaxContentScroll() > 0);
        int vt = settingsScrollViewportTop();
        int vb = settingsScrollViewportBottom();
        g.enableScissor(fx, vt, fr, vb);
        try {
            settingsDrawSectionHeaderIf(
                    g, fx, fr, SETTINGS_SEC_CONTROLS, "chat-utilities.settings.section.controls");
            settingsDrawRowLabel(g, SettingsRow.OPEN_MENU, Component.translatable("key.chatutilities.open_menu"), fx);
            settingsDrawRowLabel(g, SettingsRow.CHAT_PEEK, Component.translatable("key.chatutilities.chat_peek"), fx);
            settingsDrawRowLabel(
                    g, SettingsRow.COPY_PLAIN_BIND, Component.translatable("chat-utilities.settings.copy_plain_bind"), fx);
            settingsDrawRowLabel(
                    g,
                    SettingsRow.COPY_FORMATTED_BIND,
                    Component.translatable("chat-utilities.settings.copy_formatted_bind"),
                    fx);
            settingsDrawRowLabel(
                    g,
                    SettingsRow.FULLSCREEN_IMAGE_CLICK,
                    Component.translatable("key.chatutilities.image_preview_fullscreen"),
                    fx);

            settingsDrawSectionHeaderIf(
                    g, fx, fr, SETTINGS_SEC_MOD, "chat-utilities.settings.section.mod");
            settingsDrawRowLabel(
                    g,
                    SettingsRow.CHECK_FOR_UPDATES,
                    Component.translatable("chat-utilities.settings.check_for_updates"),
                    fx);
            settingsDrawRowLabel(
                    g,
                    SettingsRow.MOD_PRIMARY_COLOR,
                    Component.translatable("chat-utilities.settings.primary_color"),
                    fx);

            settingsDrawSectionHeaderIf(
                    g, fx, fr, SETTINGS_SEC_CLICK_COPY, "chat-utilities.settings.section.click_to_copy");
            settingsDrawRowLabel(
                    g, SettingsRow.CLICK_TO_COPY, Component.translatable("chat-utilities.settings.click_to_copy"), fx);
            settingsDrawRowLabel(
                    g,
                    SettingsRow.COPY_FORMATTED_STYLE,
                    Component.translatable("chat-utilities.settings.copy_formatted_style"),
                    fx);

            settingsDrawSectionHeaderIf(
                    g, fx, fr, SETTINGS_SEC_SMOOTH, "chat-utilities.settings.section.smooth_chat");
            settingsDrawRowLabel(
                    g, SettingsRow.SMOOTH_CHAT, Component.translatable("chat-utilities.settings.smooth_chat"), fx);
            settingsDrawRowLabel(
                    g,
                    SettingsRow.SMOOTH_CHAT_DELAY_MS,
                    Component.translatable("chat-utilities.settings.smooth_chat_delay"),
                    fx);
            settingsDrawRowLabel(
                    g,
                    SettingsRow.SMOOTH_CHAT_BAR_OPEN_MS,
                    Component.translatable("chat-utilities.settings.smooth_chat_bar_open"),
                    fx);

            settingsDrawSectionHeaderIf(
                    g, fx, fr, SETTINGS_SEC_HISTORY, "chat-utilities.settings.section.chat_history");
            settingsDrawRowLabel(
                    g,
                    SettingsRow.LONGER_CHAT_HISTORY,
                    Component.translatable("chat-utilities.settings.longer_chat_history"),
                    fx);
            settingsDrawRowLabel(
                    g,
                    SettingsRow.CHAT_HISTORY_LIMIT,
                    Component.translatable("chat-utilities.settings.chat_history_limit"),
                    fx);

            settingsDrawSectionHeaderIf(
                    g, fx, fr, SETTINGS_SEC_TIMESTAMP, "chat-utilities.settings.section.timestamp");
            settingsDrawRowLabel(
                    g, SettingsRow.CHAT_TIMESTAMPS, Component.translatable("chat-utilities.settings.chat_timestamps"), fx);
            settingsDrawRowLabel(
                    g,
                    SettingsRow.CHAT_TIMESTAMP_COLOR,
                    Component.translatable("chat-utilities.settings.chat_timestamp_color"),
                    fx);
            settingsDrawRowLabel(
                    g,
                    SettingsRow.CHAT_TIMESTAMP_FORMAT,
                    Component.translatable("chat-utilities.settings.chat_timestamp_format"),
                    fx);
            if (settingsTimestampFormatField != null && settingsLayoutRowOn(SettingsRow.CHAT_TIMESTAMP_FORMAT)) {
                String pv =
                        ChatTimestampFormatter.previewPlainForPattern(
                                settingsTimestampFormatField.getValue(), System.currentTimeMillis());
                int pRgb = ChatUtilitiesClientOptions.getChatTimestampColorRgb() | 0xFF000000;
                int rowY = settingsScrolledY(settingsLayoutRowY(SettingsRow.CHAT_TIMESTAMP_FORMAT)) + 6;
                int fmtGap = 4;
                int slotLeft =
                        settingsTimestampFormatField.getX() - settingsTimestampPreviewSlotW - fmtGap;
                int tx = slotLeft + settingsTimestampPreviewSlotW - this.font.width(pv);
                g.drawString(this.font, pv, Math.max(slotLeft, tx), rowY, pRgb, false);
            }

            settingsDrawSectionHeaderIf(
                    g, fx, fr, SETTINGS_SEC_STACK, "chat-utilities.settings.section.message_stacking");
            settingsDrawRowLabel(
                    g,
                    SettingsRow.STACK_REPEATED_MESSAGES,
                    Component.translatable("chat-utilities.settings.stack_repeated_messages"),
                    fx);
            settingsDrawRowLabel(
                    g,
                    SettingsRow.STACKED_MESSAGE_COLOR,
                    Component.translatable("chat-utilities.settings.stacked_message.color"),
                    fx);
            settingsDrawRowLabel(
                    g,
                    SettingsRow.STACKED_MESSAGE_FORMAT,
                    Component.translatable("chat-utilities.settings.stacked_message.format"),
                    fx);
            if (settingsStackedMessageFormatField != null && settingsLayoutRowOn(SettingsRow.STACKED_MESSAGE_FORMAT)) {
                String pv = settingsStackedMessageFormatField.getValue().replace("%amount%", "3");
                int pRgb = ChatUtilitiesClientOptions.getStackedMessageColorRgb() | 0xFF000000;
                int rowY = settingsScrolledY(settingsLayoutRowY(SettingsRow.STACKED_MESSAGE_FORMAT)) + 6;
                int fmtGap = 4;
                int slotLeft =
                        settingsStackedMessageFormatField.getX() - settingsStackedMessagePreviewSlotW - fmtGap;
                int tx = slotLeft + settingsStackedMessagePreviewSlotW - this.font.width(pv);
                g.drawString(this.font, pv, Math.max(slotLeft, tx), rowY, pRgb, false);
            }

            settingsDrawSectionHeaderIf(
                    g, fx, fr, SETTINGS_SEC_CHAT_SEARCH, "chat-utilities.settings.section.chat_search");
            settingsDrawRowLabel(
                    g,
                    SettingsRow.CHAT_SEARCH_BAR,
                    Component.translatable("chat-utilities.settings.chat_search_bar"),
                    fx);
            settingsDrawRowLabel(
                    g,
                    SettingsRow.CHAT_SEARCH_BAR_POSITION,
                    Component.translatable("chat-utilities.settings.chat_search_bar_position"),
                    fx);

            settingsDrawSectionHeaderIf(
                    g, fx, fr, SETTINGS_SEC_IMAGE, "chat-utilities.settings.section.image_preview");
            settingsDrawRowLabel(
                    g,
                    SettingsRow.IMAGE_PREVIEW_ENABLED,
                    Component.translatable("chat-utilities.settings.image_preview.enabled"),
                    fx);
            settingsDrawRowLabel(
                    g,
                    SettingsRow.IMAGE_PREVIEW_WHITELIST,
                    Component.translatable("chat-utilities.settings.image_preview.whitelist_button_label"),
                    fx);
            settingsDrawRowLabel(
                    g,
                    SettingsRow.IMAGE_PREVIEW_ALLOW_UNTRUSTED,
                    Component.translatable("chat-utilities.settings.image_preview.allow_untrusted_domains"),
                    fx);

            settingsDrawSectionHeaderIf(
                    g, fx, fr, SETTINGS_SEC_SYMBOL, "chat-utilities.settings.section.symbol_selector");
            settingsDrawRowLabel(
                    g,
                    SettingsRow.CHAT_SYMBOL_SELECTOR,
                    Component.translatable("chat-utilities.settings.chat_symbol_selector"),
                    fx);
            settingsDrawRowLabel(
                    g,
                    SettingsRow.SYMBOL_PALETTE_INSERT_STYLE,
                    Component.translatable("chat-utilities.settings.symbol_palette_insert_style"),
                    fx);

            settingsDrawSectionHeaderIf(
                    g, fx, fr, SETTINGS_SEC_UNREAD, "chat-utilities.settings.section.unread_badge");
            settingsDrawRowLabel(
                    g,
                    SettingsRow.TAB_UNREAD_BADGES,
                    Component.translatable("chat-utilities.settings.tab_unread_badges"),
                    fx);
            settingsDrawRowLabel(
                    g,
                    SettingsRow.ALWAYS_SHOW_UNREAD_TABS,
                    Component.translatable("chat-utilities.settings.always_show_unread_tabs"),
                    fx);
            settingsDrawRowLabel(
                    g,
                    SettingsRow.UNREAD_BADGE_STYLE,
                    Component.translatable("chat-utilities.settings.unread_badge.style"),
                    fx);
            settingsDrawRowLabel(
                    g,
                    SettingsRow.UNREAD_BADGE_COLOR,
                    Component.translatable("chat-utilities.settings.unread_badge.color"),
                    fx);

            settingsDrawSectionHeaderIf(g, fx, fr, SETTINGS_SEC_OTHER, "chat-utilities.settings.section.other");
            settingsDrawRowLabel(
                    g,
                    SettingsRow.CHAT_PANEL_BACKGROUND_OPACITY,
                    Component.translatable("chat-utilities.settings.chat_panel_background_opacity.unfocused"),
                    fx);
            settingsDrawRowLabel(
                    g,
                    SettingsRow.CHAT_PANEL_BACKGROUND_FOCUSED_OPACITY,
                    Component.translatable("chat-utilities.settings.chat_panel_background_opacity.focused"),
                    fx);
            settingsDrawRowLabel(
                    g,
                    SettingsRow.CHAT_BAR_BACKGROUND_OPACITY,
                    Component.translatable("chat-utilities.settings.chat_bar_background_opacity"),
                    fx);
            settingsDrawRowLabel(
                    g, SettingsRow.CHAT_TEXT_SHADOW, Component.translatable("chat-utilities.settings.chat_text_shadow"), fx);
            settingsDrawRowLabel(
                    g,
                    SettingsRow.CHAT_BAR_MENU_BUTTON,
                    Component.translatable("chat-utilities.settings.chat_bar_menu_button"),
                    fx);
            settingsDrawRowLabel(
                    g,
                    SettingsRow.IGNORE_SELF_CHAT_ACTIONS,
                    Component.translatable("chat-utilities.settings.ignore_self_chat_actions"),
                    fx);
            settingsDrawRowLabel(
                    g,
                    SettingsRow.PRESERVE_VANILLA_CHAT_LOG_ON_LEAVE,
                    Component.translatable("chat-utilities.settings.preserve_vanilla_chat_log"),
                    fx);

            settingsDrawSectionHeaderIf(
                    g, fx, fr, SETTINGS_SEC_PROFILES, "chat-utilities.settings.section.profiles");
        } finally {
            g.disableScissor();
        }
    }

    private void drawSettingsSectionHeader(GuiGraphics g, int fx, int fr, int y, String title) {
        int h = settingsSectionHeaderH();
        g.fill(fx, y, fr, y + h, C_WIN_GROUP_BG);
        g.renderOutline(fx, y, fr - fx, h, C_WIN_GROUP_EDGE);
        g.fill(fx, y, fx + 2, y + h, modAccentArgb());
        Component line =
                Component.literal(title.toUpperCase(Locale.ROOT)).withStyle(ChatFormatting.BOLD);
        g.drawString(this.font, line, fx + 8, y + (h - 8) / 2, 0xFFE8EEF8, false);
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
        runExportProfilesDialog(false);
    }

    private void runExportProfilesDialog(boolean includeSettings) {
        if (this.minecraft == null) {
            return;
        }
        this.minecraft.setScreen(ProfileImportExportSelectScreen.forExport(this, includeSettings));
    }

    private void runImportProfilesDialog() {
        runImportProfilesDialog(false);
    }

    private void runImportProfilesDialog(boolean includeSettings) {
        if (this.minecraft == null) {
            return;
        }
        try {
            String json = null;
            Optional<Path> src = ProfileJsonFileDialog.pickOpenJson(PROFILES_JSON_DEFAULT_DIR);
            if (src.isPresent() && Files.isRegularFile(src.get())) {
                json = Files.readString(src.get(), StandardCharsets.UTF_8);
            }
            if (json == null || json.isBlank()) {
                json = this.minecraft.keyboardHandler.getClipboard();
            }
            if (json == null || json.isBlank()) {
                showSettingsToast(
                        I18n.get("chat-utilities.settings.import_profiles.toast.empty.title"),
                        I18n.get("chat-utilities.settings.import_profiles.toast.empty.detail"));
                return;
            }
            // Accept either a raw profiles export or a combined object (profiles + settings).
            JsonObject obj;
            try {
                obj = JsonParser.parseString(json).getAsJsonObject();
            } catch (Exception ignored) {
                obj = new JsonObject();
                obj.add("profiles", JsonParser.parseString(json));
            }
            this.minecraft.setScreen(ProfileImportExportSelectScreen.forImport(this, obj.toString(), includeSettings));
        } catch (JsonParseException e) {
            showSettingsToast(
                    I18n.get("chat-utilities.settings.import_profiles.toast.invalid.title"),
                    I18n.get("chat-utilities.settings.import_profiles.toast.invalid.detail"));
        } catch (IOException e) {
            showSettingsToast(
                    I18n.get("chat-utilities.settings.import_profiles.toast.error.title"),
                    e.getMessage() != null ? e.getMessage() : "");
        }
    }

    private void runImportLabyModProfilesDialog() {
        if (this.minecraft == null) {
            return;
        }
        try {
            String json = null;
            Optional<Path> src = ProfileJsonFileDialog.pickOpenJson(PROFILES_JSON_DEFAULT_DIR);
            if (src.isPresent() && Files.isRegularFile(src.get())) {
                json = Files.readString(src.get(), StandardCharsets.UTF_8);
            }
            if (json == null || json.isBlank()) {
                showSettingsToast(
                        I18n.get("chat-utilities.settings.import_profiles.toast.empty.title"),
                        "No file selected.");
                return;
            }
            int added = ChatUtilitiesManager.get().importProfileFromLabyModJson(json);
            if (added == 0) {
                showSettingsToast(
                        I18n.get("chat-utilities.settings.import_profiles.toast.none.title"),
                        "Nothing imported.");
            } else {
                showSettingsToast(
                        I18n.get("chat-utilities.settings.import_profiles.toast.ok.title"),
                        "Imported LabyMod profile.");
            }
            init();
        } catch (JsonParseException e) {
            showSettingsToast(
                    I18n.get("chat-utilities.settings.import_profiles.toast.invalid.title"),
                    "Invalid LabyMod JSON.");
        } catch (IOException e) {
            showSettingsToast(
                    I18n.get("chat-utilities.settings.import_profiles.toast.error.title"),
                    e.getMessage() != null ? e.getMessage() : "");
        }
    }

    /** Short label for the keybind button (name is shown beside it as static text). */
    private String keybindCaptionOnly(KeyMapping km, boolean rebinding) {
        if (km == null) {
            return "Not bound";
        }
        if (rebinding) {
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

    /**
     * Repositions settings widgets after {@link #settingsContentScroll} changes while keeping thin-scrollbar thumb
     * drag active ({@code init()} would clear {@link #thinScrollDrag}).
     */
    private void relayoutSettingsDuringThinThumbDrag() {
        double anchorScroll = thinScrollDragAnchorScroll;
        int anchorMy = thinScrollDragAnchorMy;
        applySettingsScrollToWidgets();
        thinScrollDrag = ThinScrollDrag.SETTINGS_THUMB;
        thinScrollDragAnchorScroll = anchorScroll;
        thinScrollDragAnchorMy = anchorMy;
        int vt = settingsScrollViewportTop();
        int vh = settingsScrollViewportHeight();
        int ch = settingsScrollableContentHeight();
        ThinScrollbar.Metrics m = ThinScrollbar.Metrics.compute(vt, vh, ch, settingsContentScroll);
        if (m != null) {
            thinScrollDragMaxTravel = m.maxTravel;
            thinScrollDragMaxScroll = m.maxScroll;
        }
    }

    private void thinScrollbarDragTick() {
        if (thinScrollDrag == ThinScrollDrag.NONE || this.minecraft == null) {
            return;
        }
        long h = this.minecraft.getWindow().handle();
        if (GLFW.glfwGetMouseButton(h, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            thinScrollDrag = ThinScrollDrag.NONE;
            return;
        }
        int my =
                (int)
                        Math.round(
                                menuPointerToLogicalY(
                                        this.minecraft.mouseHandler.getScaledYPos(this.minecraft.getWindow())));
        switch (thinScrollDrag) {
            case SETTINGS_THUMB -> {
                int max = settingsMaxContentScroll();
                if (max <= 0) {
                    thinScrollDrag = ThinScrollDrag.NONE;
                    return;
                }
                double next =
                        thinScrollDragAnchorScroll
                                + (my - thinScrollDragAnchorMy)
                                        * thinScrollDragMaxScroll
                                        / (double) thinScrollDragMaxTravel;
                int clamped = Mth.clamp((int) Math.round(next), 0, max);
                if (clamped != settingsContentScroll) {
                    settingsContentScroll = clamped;
                    relayoutSettingsDuringThinThumbDrag();
                }
            }
            case CHAT_WINDOWS_THUMB -> {
                int max = Math.max(0, chatWindowsTotalListHeight - chatWindowsListViewportHeight);
                if (max <= 0) {
                    thinScrollDrag = ThinScrollDrag.NONE;
                    return;
                }
                double next =
                        thinScrollDragAnchorScroll
                                + (my - thinScrollDragAnchorMy)
                                        * thinScrollDragMaxScroll
                                        / (double) thinScrollDragMaxTravel;
                int clamped = Mth.clamp((int) Math.round(next), 0, max);
                if (clamped != chatWindowsListScrollPixels) {
                    chatWindowsListScrollPixels = clamped;
                    relayoutChatWindowsDuringThinThumbDrag();
                }
            }
            default -> {}
        }
    }

    private boolean tryThinScrollbarMouseDown(double mx, double my) {
        if (modColorPickerOverlay != null || imageWhitelistOverlay != null) {
            return false;
        }
        int ix = (int) mx;
        int iy = (int) my;
        int barX = contentRight() - ThinScrollbar.W;
        int hitL = barX + ThinScrollbar.W - ThinScrollbar.HIT_W;
        int hitR = barX + ThinScrollbar.W;
        if (activePanel == Panel.SETTINGS && settingsMaxContentScroll() > 0) {
            int vt = settingsScrollViewportTop();
            int vh = settingsScrollViewportHeight();
            int ch = settingsScrollableContentHeight();
            if (ix >= hitL && ix < hitR && iy >= vt && iy < vt + vh) {
                ThinScrollbar.Metrics m =
                        ThinScrollbar.Metrics.compute(vt, vh, ch, settingsContentScroll);
                if (m == null) {
                    return false;
                }
                if (m.thumbContainsY(iy)) {
                    thinScrollDrag = ThinScrollDrag.SETTINGS_THUMB;
                    thinScrollDragAnchorScroll = settingsContentScroll;
                    thinScrollDragAnchorMy = iy;
                    thinScrollDragMaxTravel = m.maxTravel;
                    thinScrollDragMaxScroll = m.maxScroll;
                } else {
                    settingsContentScroll =
                            Mth.clamp(m.scrollPixelsForTrackClickY(vt, iy), 0, settingsMaxContentScroll());
                    applySettingsScrollToWidgets();
                }
                return true;
            }
        }
        if (activePanel == Panel.CHAT_WINDOWS && chatWindowsShowScrollbar) {
            int vt = bodyY();
            int vh = chatWindowsListViewportHeight;
            int ch = chatWindowsTotalListHeight;
            if (ix >= hitL && ix < hitR && iy >= vt && iy < vt + vh) {
                ThinScrollbar.Metrics m =
                        ThinScrollbar.Metrics.compute(vt, vh, ch, chatWindowsListScrollPixels);
                if (m == null) {
                    return false;
                }
                int max = Math.max(0, ch - vh);
                if (m.thumbContainsY(iy)) {
                    thinScrollDrag = ThinScrollDrag.CHAT_WINDOWS_THUMB;
                    thinScrollDragAnchorScroll = chatWindowsListScrollPixels;
                    thinScrollDragAnchorMy = iy;
                    thinScrollDragMaxTravel = m.maxTravel;
                    thinScrollDragMaxScroll = m.maxScroll;
                } else {
                    chatWindowsListScrollPixels =
                            Mth.clamp(m.scrollPixelsForTrackClickY(vt, iy), 0, max);
                    applyChatWindowsScrollToWidgets();
                }
                return true;
            }
        }
        return false;
    }

    private void resetScrolls() {
        serverScroll = 0;
        winScroll = 0;
        actionScroll = 0;
        aliasScroll = 0;
        settingsContentScroll = 0;
        thinScrollDrag = ThinScrollDrag.NONE;
        expandedWindowIds.clear();
        windowTabPatScroll.clear();
        menuWindowTabId.clear();
        menuExpandedChatWindowsPersisted.clear();
        menuExpandedChatWindowsProfileId = null;
    }

    private static String removeTabConfirmKey(String windowId, String tabId) {
        return windowId + "\0" + tabId;
    }

    private void persistMenuExpandedChatWindows(ServerProfile p) {
        if (p == null) {
            return;
        }
        menuExpandedChatWindowsProfileId = p.getId();
        menuExpandedChatWindowsPersisted.clear();
        menuExpandedChatWindowsPersisted.addAll(expandedWindowIds);
    }

    /** Returns the profile list sorted so the currently active server's profile comes first. */
    private List<ServerProfile> sortedProfiles() {
        return sortedProfilesList(ChatUtilitiesManager.get());
    }

    private String headerTitleText() {
        return switch (activePanel) {
            case EDIT_PROFILE -> "Edit Profile";
            case CHAT_WINDOWS -> "Chat Windows";
            case CHAT_ACTIONS -> "Chat Actions";
            case COMMAND_ALIASES -> I18n.get("chat-utilities.command_aliases.title");
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
            case CHAT_ACTIONS ->
                    "Ignore matching messages or play a sound when they arrive — one list, pick the action per rule.";
            case COMMAND_ALIASES -> I18n.get("chat-utilities.command_aliases.description");
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
        float menuSc = menuOpenVisualScale();
        boolean menuScalePose = menuSc < 0.9995f;
        final int imx = menuScalePose ? (int) Math.round(menuPointerToLogicalX(mouseX)) : mouseX;
        final int imy = menuScalePose ? (int) Math.round(menuPointerToLogicalY(mouseY)) : mouseY;
        if (menuScalePose) {
            float mcx = (panelLeft() + panelRight()) / 2f;
            float mcy = (panelTop() + panelBottom()) / 2f;
            var pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(mcx, mcy);
            pose.scale(menuSc, menuSc);
            pose.translate(-mcx, -mcy);
        }
        try {
            EditBox soundAnchor = activeSoundSuggestionField();
            if (!(activePanel == Panel.CHAT_ACTIONS && soundAnchor != null)) {
                sugFiltered = List.of();
            }
            EditBox cmdAnchor = activeCommandSuggestionField();
            if (!(activePanel == Panel.COMMAND_ALIASES && cmdAnchor != null)) {
                cmdSugFiltered = List.of();
            }
            if (activePanel == Panel.SETTINGS && !settingsScrollClipRenderables.isEmpty()) {
                int cl = contentLeft();
                int cr = contentRight() - scrollbarReserve(settingsMaxContentScroll() > 0);
                int vt = settingsScrollViewportTop();
                int vb = settingsScrollViewportBottom();
                graphics.enableScissor(cl, vt, cr, vb);
                try {
                    for (Renderable renderable : settingsScrollClipRenderables) {
                        renderable.render(graphics, imx, imy, partialTick);
                    }
                } finally {
                    graphics.disableScissor();
                }
                for (Renderable renderable : settingsNonClipRenderables) {
                    renderable.render(graphics, imx, imy, partialTick);
                }
            } else if (activePanel == Panel.CHAT_WINDOWS && !chatWindowsScrollClipRenderables.isEmpty()) {
                int cl = contentLeft();
                int cr = contentRight() - scrollbarReserve(chatWindowsShowScrollbar);
                int vt = chatWindowsScrollViewportTop();
                int vb = chatWindowsScrollViewportBottom();
                graphics.enableScissor(cl, vt, cr, vb);
                try {
                    for (Renderable renderable : chatWindowsScrollClipRenderables) {
                        renderable.render(graphics, imx, imy, partialTick);
                    }
                } finally {
                    graphics.disableScissor();
                }
                for (Renderable renderable : chatWindowsNonClipRenderables) {
                    renderable.render(graphics, imx, imy, partialTick);
                }
            } else {
                super.render(graphics, imx, imy, partialTick);
            }
            renderSidebar(graphics, imx, imy);
            renderContentHeader(graphics);
            renderSettingsPanelExtras(graphics);
            float menuFadeT = menuOpenEase();
            if (menuFadeT < 0.999f) {
                int veilA = Mth.clamp(Math.round(255 * (1f - menuFadeT)), 0, 255);
                int veil = (veilA << 24);
                int pl = panelLeft();
                int pt = panelTop();
                int pr = panelRight();
                int pb = panelBottom();
                graphics.fill(pl, pt, pr, pb, veil);
            }
            if (activePanel == Panel.SETTINGS && settingsMaxContentScroll() > 0) {
                ThinScrollbar.render(
                        graphics,
                        contentRight() - ThinScrollbar.W,
                        settingsScrollViewportTop(),
                        settingsScrollViewportHeight(),
                        settingsScrollableContentHeight(),
                        settingsContentScroll,
                        menuOpenEase());
            }
            if (activePanel == Panel.CHAT_WINDOWS && chatWindowsShowScrollbar) {
                ThinScrollbar.render(
                        graphics,
                        contentRight() - ThinScrollbar.W,
                        bodyY(),
                        chatWindowsListViewportHeight,
                        chatWindowsTotalListHeight,
                        chatWindowsListScrollPixels,
                        menuOpenEase());
            }
            renderChatActionTypeDropdownOnTop(graphics, imx, imy);
            renderSettingsFormatDropdownOnTop(graphics, imx, imy);
            renderSettingsUnreadBadgeStyleDropdownOnTop(graphics, imx, imy);
            soundAnchor = activeSoundSuggestionField();
            if (activePanel == Panel.CHAT_ACTIONS && soundAnchor != null) {
                renderSoundSuggestions(graphics, imx, imy, soundAnchor);
            }
            cmdAnchor = activeCommandSuggestionField();
            if (activePanel == Panel.COMMAND_ALIASES && cmdAnchor != null) {
                renderCommandSuggestions(graphics, imx, imy, cmdAnchor);
            }
            if (createWinDialogOpen && activePanel == Panel.CHAT_WINDOWS) {
                renderCreateWindowDialogOnTop(graphics, imx, imy, partialTick);
            }
            if (renameDialogOpen && activePanel == Panel.CHAT_WINDOWS) {
                renderRenameWindowDialogOnTop(graphics, imx, imy, partialTick);
            }
            if (newTabDialogOpen && activePanel == Panel.CHAT_WINDOWS) {
                renderNewTabDialogOnTop(graphics, imx, imy, partialTick);
            }
            if (renameTabDialogOpen && activePanel == Panel.CHAT_WINDOWS) {
                renderRenameTabDialogOnTop(graphics, imx, imy, partialTick);
            }
            if (createProfileDialogOpen) {
                renderCreateProfileDialogOnTop(graphics, imx, imy, partialTick);
            }
            if (importExportChoiceDialogOpen && activePanel == Panel.SETTINGS) {
                renderImportExportChoiceDialogOnTop(graphics, imx, imy, partialTick);
            }
            if (modColorPickerOverlay != null) {
                renderModPrimaryColorPickerOnTop(graphics, imx, imy, partialTick);
            }
            if (imageWhitelistOverlay != null) {
                renderImageWhitelistOverlayOnTop(graphics, imx, imy, partialTick);
            }
        } finally {
            if (menuScalePose) {
                graphics.pose().popMatrix();
            }
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

    private void renderImportExportChoiceDialogOnTop(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int pl = panelLeft(), pr = panelRight(), pt = panelTop(), pb = panelBottom();
        int dx = importExportDlgX, dy = importExportDlgY, dR = dx + importExportDlgW, dB = dy + importExportDlgH;
        int dim = 0x78000000;
        g.fill(pl, pt, pr, Math.min(dy, pb), dim);
        g.fill(pl, Math.max(dB, pt), pr, pb, dim);
        g.fill(pl, dy, dx, dB, dim);
        g.fill(dR, dy, pr, dB, dim);

        g.fill(dx, dy, dR, dB, C_PANEL_BG & 0x00FFFFFF | 0xFF000000);
        g.renderOutline(dx, dy, importExportDlgW, importExportDlgH, 0xFF505068);
        String title = importExportChoiceIsImport ? "Import" : "Export";
        g.drawString(this.font, title, dx + 12, dy + 12, 0xFFFFFFFF, false);

        if (importExportProfilesOnlyBtn != null) {
            importExportProfilesOnlyBtn.render(g, mouseX, mouseY, partialTick);
        }
        if (importExportProfilesAndSettingsBtn != null) {
            importExportProfilesAndSettingsBtn.render(g, mouseX, mouseY, partialTick);
        }
        if (importExportCancelBtn != null) {
            importExportCancelBtn.render(g, mouseX, mouseY, partialTick);
        }
    }

    private void renderNewTabDialogOnTop(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int pl = panelLeft(), pr = panelRight(), pt = panelTop(), pb = panelBottom();
        int dx = newTabDlgX, dy = newTabDlgY, dR = dx + newTabDlgW, dB = dy + newTabDlgH;
        int dim = 0x78000000;
        g.fill(pl, pt, pr, Math.min(dy, pb), dim);
        g.fill(pl, Math.max(dB, pt), pr, pb, dim);
        g.fill(pl, dy, dx, dB, dim);
        g.fill(dR, dy, pr, dB, dim);

        g.fill(dx, dy, dR, dB, C_PANEL_BG & 0x00FFFFFF | 0xFF000000);
        g.renderOutline(dx, dy, newTabDlgW, newTabDlgH, 0xFF505068);
        g.drawString(this.font, "New Chat Tab", dx + 12, dy + 12, 0xFFFFFFFF, false);

        if (dlgNewTabNameField != null) {
            dlgNewTabNameField.render(g, mouseX, mouseY, partialTick);
        }
        if (dlgNewTabOkButton != null) {
            dlgNewTabOkButton.render(g, mouseX, mouseY, partialTick);
        }
        if (dlgNewTabCancelButton != null) {
            dlgNewTabCancelButton.render(g, mouseX, mouseY, partialTick);
        }
    }

    private void renderRenameTabDialogOnTop(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int pl = panelLeft(), pr = panelRight(), pt = panelTop(), pb = panelBottom();
        int dx = renameTabDlgX, dy = renameTabDlgY, dR = dx + renameTabDlgW, dB = dy + renameTabDlgH;
        int dim = 0x78000000;
        g.fill(pl, pt, pr, Math.min(dy, pb), dim);
        g.fill(pl, Math.max(dB, pt), pr, pb, dim);
        g.fill(pl, dy, dx, dB, dim);
        g.fill(dR, dy, pr, dB, dim);

        g.fill(dx, dy, dR, dB, C_PANEL_BG & 0x00FFFFFF | 0xFF000000);
        g.renderOutline(dx, dy, renameTabDlgW, renameTabDlgH, 0xFF505068);
        g.drawString(this.font, "Rename Tab", dx + 12, dy + 12, 0xFFFFFFFF, false);

        if (dlgRenameTabNameField != null) {
            dlgRenameTabNameField.render(g, mouseX, mouseY, partialTick);
        }
        if (dlgRenameTabOkButton != null) {
            dlgRenameTabOkButton.render(g, mouseX, mouseY, partialTick);
        }
        if (dlgRenameTabCancelButton != null) {
            dlgRenameTabCancelButton.render(g, mouseX, mouseY, partialTick);
        }
    }

    private void renderCreateProfileDialogOnTop(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        int pl = panelLeft(), pr = panelRight(), pt = panelTop(), pb = panelBottom();
        int dx = createProfileDlgX, dy = createProfileDlgY, dR = dx + createProfileDlgW, dB = dy + createProfileDlgH;
        int dim = 0x78000000;
        g.fill(pl, pt, pr, Math.min(dy, pb), dim);
        g.fill(pl, Math.max(dB, pt), pr, pb, dim);
        g.fill(pl, dy, dx, dB, dim);
        g.fill(dR, dy, pr, dB, dim);

        g.fill(dx, dy, dR, dB, C_PANEL_BG & 0x00FFFFFF | 0xFF000000);
        g.renderOutline(dx, dy, createProfileDlgW, createProfileDlgH, 0xFF505068);
        g.drawString(this.font, "Create New Profile", dx + 12, dy + 12, 0xFFFFFFFF, false);

        if (dlgCreateProfileNameField != null) {
            dlgCreateProfileNameField.render(g, mouseX, mouseY, partialTick);
        }
        if (dlgCreateProfileOkButton != null) {
            dlgCreateProfileOkButton.render(g, mouseX, mouseY, partialTick);
        }
        if (dlgCreateProfileCancelButton != null) {
            dlgCreateProfileCancelButton.render(g, mouseX, mouseY, partialTick);
        }
    }

    private void renderModPrimaryColorPickerOnTop(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (modColorPickerOverlay == null) {
            return;
        }
        modColorPickerOverlay.layout(this.width, this.height);
        int pl = panelLeft(), pr = panelRight(), pt = panelTop(), pb = panelBottom();
        int dx = modColorPickerOverlay.panelLeft();
        int dy = modColorPickerOverlay.panelTop();
        int dR = modColorPickerOverlay.panelRight();
        int dB = modColorPickerOverlay.panelBottom();
        int dim = 0x78000000;
        g.fill(pl, pt, pr, Math.min(dy, pb), dim);
        g.fill(pl, Math.max(dB, pt), pr, pb, dim);
        g.fill(pl, dy, dx, dB, dim);
        g.fill(dR, dy, pr, dB, dim);
        modColorPickerOverlay.render(g, this.font, mouseX, mouseY, partialTick);
    }

    private void renderImageWhitelistOverlayOnTop(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (imageWhitelistOverlay == null) {
            return;
        }
        imageWhitelistOverlay.layout(this.width, this.height);
        int pl = panelLeft(), pr = panelRight(), pt = panelTop(), pb = panelBottom();
        int dx = imageWhitelistOverlay.panelLeft();
        int dy = imageWhitelistOverlay.panelTop();
        int dR = imageWhitelistOverlay.panelRight();
        int dB = imageWhitelistOverlay.panelBottom();
        int dim = 0x78000000;
        g.fill(pl, pt, pr, Math.min(dy, pb), dim);
        g.fill(pl, Math.max(dB, pt), pr, pb, dim);
        g.fill(pl, dy, dx, dB, dim);
        g.fill(dR, dy, pr, dB, dim);
        imageWhitelistOverlay.render(g, this.font, mouseX, mouseY, partialTick);
    }

    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
        int sl = sidebarLeft(), sr = sidebarRight();
        int st = sidebarTop(),  sb = sidebarBottom();

        // Sidebar background (slightly darker than panel); clipped to inner rounded rect (left corners match shell)
        fillSidebarClipped(g, sl, st, SIDEBAR_W, sb - st, C_SIDEBAR_BG);
        // Right separator line
        fillSidebarClipped(g, sr, st, 1, sb - st, C_SIDEBAR_SEP);

        // "Chat Utilities" header strip + “by Rainnny” (link)
        fillSidebarClipped(g, sl, st, SIDEBAR_W, SIDEBAR_TITLE_H, 0xFF040408);
        fillSidebarClipped(g, sl, st + SIDEBAR_TITLE_H, SIDEBAR_W, 1, C_SIDEBAR_SEP);
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
        int authorHoverRgb = ModPrimaryColorUtils.brightenRgb(modGlobalAccentRgb(), 1.35f);
        int authorColor = hovAuthor ? (0xFF000000 | authorHoverRgb) : modAccentArgb();
        g.drawString(this.font, byAuthor, nameX, line2Y, authorColor, false);
        if (hovAuthor) {
            g.fill(nameX, line2Y + this.font.lineHeight, nameX + nameW, line2Y + this.font.lineHeight + 1, authorColor);
        }
        sidebarAuthorLinkL = nameX;
        sidebarAuthorLinkR = nameX + nameW;
        sidebarAuthorLinkT = line2Y;
        sidebarAuthorLinkB = line2Y + this.font.lineHeight;

        // Footer: "+ New Profile", optional "Update Available!", then "Settings"
        boolean showUpdateRow = sidebarShowUpdateFooterRow();
        int footerRows = showUpdateRow ? 3 : 2;
        int footerTop = sb - footerRows * SIDEBAR_FOOTER_ROW_H;
        fillSidebarClipped(g, sl, footerTop - 1, SIDEBAR_W, 1, C_SIDEBAR_SEP);
        int row0y = footerTop;
        fillSidebarClipped(g, sl, row0y, SIDEBAR_W, SIDEBAR_FOOTER_ROW_H, 0xFF040408);
        boolean hovNew = mouseX >= sl && mouseX < sr
                && mouseY >= row0y && mouseY < row0y + SIDEBAR_FOOTER_ROW_H;
        if (hovNew) {
            fillSidebarClipped(g, sl, row0y, SIDEBAR_W, SIDEBAR_FOOTER_ROW_H, C_HOVER);
        }
        g.drawString(this.font, "+ New Profile",
                sl + SIDEBAR_FOOTER_TEXT_INSET, row0y + (SIDEBAR_FOOTER_ROW_H - 8) / 2, C_NEW_PROFILE, false);
        fillSidebarClipped(g, sl, row0y + SIDEBAR_FOOTER_ROW_H - 1, SIDEBAR_W, 1, C_SIDEBAR_SEP);
        int row1y = row0y + SIDEBAR_FOOTER_ROW_H;
        int settingsRowY;
        if (showUpdateRow) {
            int subActiveBg = mixProfileAccentArgb(C_ACTIVE_BG, modGlobalAccentRgb(), 0.38f);
            float wave = 0.5f * (1f + Mth.sin((float) (System.currentTimeMillis() * 0.0025f)));
            int updBg = mixProfileAccentArgb(subActiveBg, 0xFF000000, 0.045f + 0.065f * wave);
            fillSidebarClipped(g, sl, row1y, SIDEBAR_W, SIDEBAR_FOOTER_ROW_H, updBg);
            fillSidebarClipped(g, sl, row1y, 2, SIDEBAR_FOOTER_ROW_H, sidebarTabAccentBar(modGlobalAccentRgb()));
            boolean hovUpd = mouseX >= sl && mouseX < sr
                    && mouseY >= row1y && mouseY < row1y + SIDEBAR_FOOTER_ROW_H;
            if (hovUpd) {
                fillSidebarClipped(g, sl, row1y, SIDEBAR_W, SIDEBAR_FOOTER_ROW_H, C_HOVER);
            }
            float updIconScale = 0.5f;
            int updIconPx = Math.round(16 * updIconScale);
            int updIconY = row1y + (SIDEBAR_FOOTER_ROW_H - updIconPx) / 2;
            int updIconX = sl + SIDEBAR_FOOTER_TEXT_INSET;
            int updTextX = updIconX + updIconPx + 3;
            var updPose = g.pose();
            updPose.pushMatrix();
            updPose.translate(updIconX, updIconY);
            updPose.scale(updIconScale, updIconScale);
            g.renderItem(new ItemStack(Items.SPYGLASS), 0, 0);
            updPose.popMatrix();
            String updLabel = I18n.get("chat-utilities.sidebar.update_available");
            g.drawString(
                    this.font,
                    updLabel,
                    updTextX,
                    row1y + (SIDEBAR_FOOTER_ROW_H - 8) / 2,
                    0xFFFFFFFF,
                    false);
            fillSidebarClipped(g, sl, row1y + SIDEBAR_FOOTER_ROW_H - 1, SIDEBAR_W, 1, C_SIDEBAR_SEP);
            settingsRowY = row1y + SIDEBAR_FOOTER_ROW_H;
        } else {
            settingsRowY = row1y;
        }
        fillSidebarClipped(g, sl, settingsRowY, SIDEBAR_W, sb - settingsRowY, 0xFF040408);
        boolean settingsActive = activePanel == Panel.SETTINGS;
        boolean hovSet = mouseX >= sl && mouseX < sr && mouseY >= settingsRowY && mouseY < sb;
        if (settingsActive) {
            int subActiveBg = mixProfileAccentArgb(C_ACTIVE_BG, modGlobalAccentRgb(), 0.38f);
            fillSidebarClipped(g, sl, settingsRowY, SIDEBAR_W, sb - settingsRowY, subActiveBg);
            fillSidebarClipped(g, sl, settingsRowY, 2, sb - settingsRowY, sidebarTabAccentBar(modGlobalAccentRgb()));
        } else if (hovSet) {
            fillSidebarClipped(g, sl, settingsRowY, SIDEBAR_W, sb - settingsRowY, C_HOVER);
        }
        int settingsTextColor = 0xFFFFFFFF;
        g.drawString(this.font, "\u2699 Settings",
                sl + SIDEBAR_FOOTER_TEXT_INSET,
                settingsRowY + (SIDEBAR_FOOTER_ROW_H - 8) / 2,
                settingsTextColor,
                false);

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
                    if (expanded) {
                        fillSidebarClipped(g, sl, rowY, SIDEBAR_W, PROFILE_ROW_H, profileSelBg);
                    }
                    if (hov) {
                        fillSidebarClipped(g, sl, rowY, SIDEBAR_W, PROFILE_ROW_H, C_HOVER);
                    }
                    // Bottom hairline
                    fillSidebarClipped(g, sl, rowY + PROFILE_ROW_H - 1, SIDEBAR_W, 1, 0x18FFFFFF);

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
                    int maxNW = SIDEBAR_W - 30 - 20;
                    boolean trunc = false;
                    while (name.length() > 1 && this.font.width(name + "…") > maxNW) {
                        name = name.substring(0, name.length() - 1);
                        trunc = true;
                    }
                    if (trunc) name += "…";
                    g.drawString(this.font, name,
                            iconX + 20, rowY + (PROFILE_ROW_H - 8) / 2, C_PROFILE_NAME, false);

                    String branchGlyph = expanded ? UI_BRANCH_EXPANDED : UI_BRANCH_COLLAPSED;
                    g.drawString(
                            this.font,
                            branchGlyph,
                            sr - this.font.width(branchGlyph) - 6,
                            rowY + (PROFILE_ROW_H - 8) / 2,
                            0xFF606070,
                            false);

                    sidebarEntries.add(new SidebarEntry(rowY, PROFILE_ROW_H, true, p.getId(), null));
                }
                iy += PROFILE_ROW_H;

                // ── Sub-items ───────────────────────────────────────────────
                if (expanded) {
                    Panel[] panels = {
                        Panel.CHAT_WINDOWS, Panel.CHAT_ACTIONS, Panel.COMMAND_ALIASES, Panel.EDIT_PROFILE
                    };
                    String[] labels = {
                        "Chat Windows",
                        "Chat Actions",
                        I18n.get("chat-utilities.panel.command_aliases"),
                        "Edit Profile"
                    };
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
                            if (active) {
                                fillSidebarClipped(g, sl, subY, SIDEBAR_W, SUB_ROW_H, subActiveBg);
                            }
                            if (subHov && !active) {
                                fillSidebarClipped(g, sl, subY, SIDEBAR_W, SUB_ROW_H, C_HOVER);
                            }
                            // Left accent bar
                            if (active) {
                                fillSidebarClipped(g, sl, subY, 2, SUB_ROW_H, subAccentBar);
                            }
                            g.drawString(this.font, sl2,
                                    sl + SUB_INDENT, subY + (SUB_ROW_H - 8) / 2,
                                    active ? subTextActive : subTextInactive, false);
                            sidebarEntries.add(new SidebarEntry(subY, SUB_ROW_H, false, p.getId(), sp));
                        }
                        iy += SUB_ROW_H;
                    }
                    // Hairline separator after expanded group
                    if (iy > listTop && iy < listBottom) {
                        fillSidebarClipped(g, sl, iy, SIDEBAR_W, 1, 0x20FFFFFF);
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
        if (activePanel != Panel.CHAT_ACTIONS) {
            return null;
        }
        if (soundField != null && soundField.isFocused()) {
            return soundField;
        }
        for (PendingChatEffectEdit pr : pendingChatEffectEdits) {
            EditBox snd = pr.soundBox();
            if (snd != null && snd.isFocused()) {
                return snd;
            }
        }
        return null;
    }

    private EditBox activeCommandSuggestionField() {
        if (activePanel != Panel.COMMAND_ALIASES) {
            return null;
        }
        if (commandAliasAddToField != null && commandAliasAddToField.isFocused()) {
            return commandAliasAddToField;
        }
        for (PendingCommandAliasEdit pr : pendingCommandAliasEdits) {
            EditBox to = pr.toBox();
            if (to != null && to.isFocused()) {
                return to;
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

        sugLeft = anchor.getX();
        sugTop = anchor.getY() + anchor.getHeight() + 1;
        int textPad = 6;
        int widest = 0;
        for (int i = 0; i < sugVisibleRows; i++) {
            widest = Math.max(widest, this.font.width(sugFiltered.get(sugScroll + i)));
        }
        int roomRight = Math.min(contentRight() - 4, this.width - 4);
        int maxW = Math.max(120, roomRight - sugLeft);
        sugWidth = Mth.clamp(Math.max(widest + textPad, anchor.getWidth()), 120, maxW);
        int sugBottom = sugTop + sugVisibleRows * SUGGEST_ROW_H;
        g.fill(sugLeft - 1, sugTop - 1, sugLeft + sugWidth + 1, sugBottom + 1, 0xFF000000);
        g.fill(sugLeft, sugTop, sugLeft + sugWidth, sugBottom, C_SIDEBAR_BG);
        g.enableScissor(sugLeft, sugTop, sugLeft + sugWidth, sugBottom);
        try {
            int innerMax = sugWidth - textPad;
            for (int i = 0; i < sugVisibleRows; i++) {
                String line = ellipsizeToWidth(this.font, sugFiltered.get(sugScroll + i), innerMax);
                int ry = sugTop + i * SUGGEST_ROW_H;
                boolean hov =
                        mouseX >= sugLeft
                                && mouseX < sugLeft + sugWidth
                                && mouseY >= ry
                                && mouseY < ry + SUGGEST_ROW_H;
                if (hov) {
                    g.fill(sugLeft, ry, sugLeft + sugWidth, ry + SUGGEST_ROW_H, 0x336688FF);
                }
                g.drawString(this.font, line, sugLeft + 3, ry + 2, 0xFFFFFFFF, false);
            }
        } finally {
            g.disableScissor();
        }
    }

    private void renderCommandSuggestions(GuiGraphics g, int mouseX, int mouseY, EditBox anchor) {
        if (anchor == null) {
            cmdSugFiltered = List.of();
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() == null) {
            cmdSugFiltered = List.of();
            return;
        }
        String raw = anchor.getValue();
        if (!Objects.equals(raw, cmdSugLastQuery)) {
            cmdSugLastQuery = raw;
            cmdSugScroll = 0;
        }
        String t = raw == null ? "" : raw.stripLeading();
        if (!t.startsWith("/") || t.indexOf(' ') >= 0) {
            cmdSugFiltered = List.of();
            return;
        }
        String prefix = t.substring(1).toLowerCase(Locale.ROOT);
        CommandDispatcher<ClientSuggestionProvider> disp = mc.getConnection().getCommands();
        if (disp == null) {
            cmdSugFiltered = List.of();
            return;
        }
        List<String> out = new ArrayList<>();
        for (CommandNode<ClientSuggestionProvider> child : disp.getRoot().getChildren()) {
            String name = child.getName();
            if (name != null && name.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                out.add("/" + name);
                if (out.size() >= SUGGEST_MAX_MATCHES) {
                    break;
                }
            }
        }
        out.sort(String::compareToIgnoreCase);
        cmdSugFiltered = out;
        if (cmdSugFiltered.isEmpty()) {
            return;
        }
        int maxScroll = Math.max(0, cmdSugFiltered.size() - SUGGEST_VISIBLE_ROWS);
        cmdSugScroll = Mth.clamp(cmdSugScroll, 0, maxScroll);
        cmdSugVisibleRows = Math.min(SUGGEST_VISIBLE_ROWS, cmdSugFiltered.size());

        cmdSugLeft = anchor.getX();
        cmdSugTop = anchor.getY() + anchor.getHeight() + 1;
        int textPad = 6;
        int widest = 0;
        for (int i = 0; i < cmdSugVisibleRows; i++) {
            widest = Math.max(widest, this.font.width(cmdSugFiltered.get(cmdSugScroll + i)));
        }
        int roomRight = Math.min(contentRight() - 4, this.width - 4);
        int maxW = Math.max(120, roomRight - cmdSugLeft);
        cmdSugWidth = Mth.clamp(Math.max(widest + textPad, anchor.getWidth()), 120, maxW);
        int sugBottom = cmdSugTop + cmdSugVisibleRows * SUGGEST_ROW_H;
        g.fill(cmdSugLeft - 1, cmdSugTop - 1, cmdSugLeft + cmdSugWidth + 1, sugBottom + 1, 0xFF000000);
        g.fill(cmdSugLeft, cmdSugTop, cmdSugLeft + cmdSugWidth, sugBottom, C_SIDEBAR_BG);
        g.enableScissor(cmdSugLeft, cmdSugTop, cmdSugLeft + cmdSugWidth, sugBottom);
        try {
            int innerMax = cmdSugWidth - textPad;
            for (int i = 0; i < cmdSugVisibleRows; i++) {
                String line = ellipsizeToWidth(this.font, cmdSugFiltered.get(cmdSugScroll + i), innerMax);
                int ry = cmdSugTop + i * SUGGEST_ROW_H;
                boolean hov =
                        mouseX >= cmdSugLeft
                                && mouseX < cmdSugLeft + cmdSugWidth
                                && mouseY >= ry
                                && mouseY < ry + SUGGEST_ROW_H;
                if (hov) {
                    g.fill(cmdSugLeft, ry, cmdSugLeft + cmdSugWidth, ry + SUGGEST_ROW_H, 0x336688FF);
                }
                g.drawString(this.font, line, cmdSugLeft + 3, ry + 2, 0xFFFFFFFF, false);
            }
        } finally {
            g.disableScissor();
        }
    }

    private static String ellipsizeToWidth(net.minecraft.client.gui.Font font, String line, int maxW) {
        if (line == null || line.isEmpty()) {
            return "";
        }
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

    // ── Mouse input ───────────────────────────────────────────────────────────

    private boolean chatWindowsModalBlocksContentClicks() {
        return (createWinDialogOpen
                        || renameDialogOpen
                        || newTabDialogOpen
                        || renameTabDialogOpen)
                && activePanel == Panel.CHAT_WINDOWS;
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
    public boolean charTyped(CharacterEvent event) {
        if (modColorPickerOverlay != null) {
            EditBox hex = modColorPickerOverlay.getHexField();
            if (hex != null
                    && (colorPickerHexKeyboardCapture || getFocused() == hex)
                    && hex.charTyped(event)) {
                return true;
            }
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (modColorPickerOverlay != null) {
            if (event.key() != InputConstants.KEY_ESCAPE) {
                EditBox hex = modColorPickerOverlay.getHexField();
                if (hex != null
                        && (colorPickerHexKeyboardCapture || getFocused() == hex)
                        && hex.keyPressed(event)) {
                    return true;
                }
            }
        }
        if (modColorPickerOverlay != null && event.key() == InputConstants.KEY_ESCAPE) {
            if (modColorPickerOverlay.isChatHighlightMode()) {
                clearPendingChatHighlightPickState();
            }
            modColorPickerOverlay = null;
            colorPickerHexKeyboardCapture = false;
            return true;
        }
        if (imageWhitelistOverlay != null && event.key() == InputConstants.KEY_ESCAPE) {
            imageWhitelistOverlay = null;
            init();
            return true;
        }
        if (imageWhitelistOverlay != null && imageWhitelistOverlay.keyPressed(event.key())) {
            return true;
        }
        if (createProfileDialogOpen && event.key() == InputConstants.KEY_ESCAPE) {
            closeCreateProfileDialog();
            return true;
        }
        if (renameDialogOpen && activePanel == Panel.CHAT_WINDOWS && event.key() == InputConstants.KEY_ESCAPE) {
            closeRenameWindowDialog();
            return true;
        }
        if (createWinDialogOpen && activePanel == Panel.CHAT_WINDOWS && event.key() == InputConstants.KEY_ESCAPE) {
            closeCreateWindowDialog();
            return true;
        }
        if (newTabDialogOpen && activePanel == Panel.CHAT_WINDOWS && event.key() == InputConstants.KEY_ESCAPE) {
            closeNewTabDialog();
            return true;
        }
        if (renameTabDialogOpen && activePanel == Panel.CHAT_WINDOWS && event.key() == InputConstants.KEY_ESCAPE) {
            closeRenameTabDialog();
            return true;
        }
        if (activePanel == Panel.SETTINGS && rebindingFullscreenImageClick) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                rebindingFullscreenImageClick = false;
                init();
                return true;
            }
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
        if (activePanel == Panel.SETTINGS && rebindingChatPeekKey) {
            if (event.key() == InputConstants.KEY_ESCAPE) {
                rebindingChatPeekKey = false;
                init();
                return true;
            }
            InputConstants.Key newKey = keyFromKeyEvent(event);
            if (!newKey.equals(InputConstants.UNKNOWN)) {
                ChatUtilitiesModClient.CHAT_PEEK_KEY.setKey(newKey);
                KeyMapping.resetMapping();
                saveOptions();
                rebindingChatPeekKey = false;
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
            if (newTabDialogOpen
                    && activePanel == Panel.CHAT_WINDOWS
                    && p != null
                    && dlgNewTabNameField != null
                    && dlgNewTabNameField.isFocused()) {
                submitNewTabDialog(mgr, p);
                return true;
            }
            if (renameTabDialogOpen
                    && activePanel == Panel.CHAT_WINDOWS
                    && p != null
                    && dlgRenameTabNameField != null
                    && dlgRenameTabNameField.isFocused()) {
                submitRenameTabDialog(mgr, p);
                return true;
            }
            if (createProfileDialogOpen
                    && dlgCreateProfileNameField != null
                    && dlgCreateProfileNameField.isFocused()) {
                submitCreateProfileDialog(mgr);
                return true;
            }

            if (activePanel == Panel.CHAT_WINDOWS && p != null) {
                for (PendingNewPattern pn : pendingNewPatterns) {
                    if (pn.box.isFocused()) {
                        try {
                            mgr.addPattern(pn.profile(), pn.windowId(), pn.tabId(), pn.box.getValue());
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
                            mgr.setPatternAt(
                                    pe.profile(), pe.windowId(), pe.tabId(), pe.userPosition(), pe.box.getValue());
                            init();
                        } catch (PatternSyntaxException ignored) {
                        }
                        return true;
                    }
                }
            }
            if (activePanel == Panel.CHAT_ACTIONS && p != null) {
                for (PendingChatGroupPattern pr : pendingChatGroupPatterns) {
                    if (pr.patternBox().isFocused()) {
                        try {
                            mgr.setChatActionGroupPattern(p, pr.groupIndex(), pr.patternBox().getValue());
                            init();
                        } catch (IllegalArgumentException ignored) {
                        }
                        return true;
                    }
                }
                for (PendingChatEffectEdit pr : pendingChatEffectEdits) {
                    EditBox sndBox = pr.soundBox();
                    EditBox tgtBox = pr.targetBox();
                    if ((sndBox != null && sndBox.isFocused()) || (tgtBox != null && tgtBox.isFocused())) {
                        ChatActionEffect.Type t =
                                p.getChatActionGroups()
                                        .get(pr.groupIndex())
                                        .getEffects()
                                        .get(pr.effectIndex())
                                        .getType();
                        try {
                            mgr.setChatActionEffectAt(
                                    p,
                                    pr.groupIndex(),
                                    pr.effectIndex(),
                                    t,
                                    sndBox != null && sndBox.isFocused()
                                            ? sndBox.getValue()
                                            : tgtBox != null
                                                    ? tgtBox.getValue()
                                                    : "");
                            init();
                        } catch (IllegalArgumentException ignored) {
                        }
                        return true;
                    }
                }
            }
            if (activePanel == Panel.CHAT_ACTIONS
                    && p != null
                    && patternField != null
                    && (patternField.isFocused() || (soundField != null && soundField.isFocused()))
                    && !patternField.getValue().strip().isEmpty()) {
                try {
                    if (chatActionNewType == ChatActionEffect.Type.COLOR_HIGHLIGHT) {
                        mgr.addChatAction(
                                p,
                                chatActionNewType,
                                patternField.getValue(),
                                "",
                                chatActionNewHighlightRgb,
                                chatActionNewHlBold,
                                chatActionNewHlItalic,
                                chatActionNewHlUnderlined,
                                chatActionNewHlStrikethrough,
                                chatActionNewHlObfuscated);
                    } else {
                        mgr.addChatAction(
                                p,
                                chatActionNewType,
                                patternField.getValue(),
                                soundField != null ? soundField.getValue() : "");
                    }
                    chatActionNewPatternDraft = "";
                    patternField.setValue("");
                    if (soundField != null) {
                        soundField.setValue("");
                    }
                } catch (IllegalArgumentException ignored) {
                }
                init();
                return true;
            }
            if (activePanel == Panel.COMMAND_ALIASES && p != null) {
                for (PendingCommandAliasEdit pr : pendingCommandAliasEdits) {
                    if (pr.fromBox().isFocused() || pr.toBox().isFocused()) {
                        mgr.updateCommandAliasAt(
                                p,
                                pr.index(),
                                stripSlashCommand(pr.fromBox().getValue()),
                                stripSlashCommand(pr.toBox().getValue()));
                        init();
                        return true;
                    }
                }
            }
            if (activePanel == Panel.COMMAND_ALIASES
                    && p != null
                    && commandAliasAddFromField != null
                    && commandAliasAddToField != null
                    && (commandAliasAddFromField.isFocused() || commandAliasAddToField.isFocused())
                    && !commandAliasAddFromField.getValue().strip().isEmpty()
                    && !commandAliasAddToField.getValue().strip().isEmpty()) {
                if (mgr.addCommandAlias(
                        p,
                        stripSlashCommand(commandAliasAddFromField.getValue()),
                        stripSlashCommand(commandAliasAddToField.getValue()))) {
                    commandAliasNewFromDraft = "";
                    commandAliasNewToDraft = "";
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
        MouseButtonEvent e = remapMenuPointerForHitTest(event);
        if (settingsUnreadBadgeStyleDropdownOpen && activePanel == Panel.SETTINGS) {
            double mx = e.x();
            double my = e.y();
            if (e.button() == 0) {
                int x = settingsUnreadBadgeStyleDropX;
                int y = settingsUnreadBadgeStyleDropY;
                int w = settingsUnreadBadgeStyleDropW;
                int h = settingsUnreadBadgeStyleDropH;
                boolean inside = mx >= x && mx < x + w && my >= y && my < y + h;
                if (!inside) {
                    clearSettingsUnreadBadgeStyleDropdown();
                    return true;
                }
                int rowH = 18;
                int idx = (int) ((my - y) / rowH);
                ChatUtilitiesClientOptions.TabUnreadBadgeStyle[] vals =
                        ChatUtilitiesClientOptions.TabUnreadBadgeStyle.values();
                if (idx >= 0 && idx < vals.length) {
                    ChatUtilitiesClientOptions.setTabUnreadBadgeStyle(vals[idx]);
                    clearSettingsUnreadBadgeStyleDropdown();
                    saveOptions();
                    init();
                    return true;
                }
            }
            return true;
        }
        if (settingsFormatDropdownOpen && activePanel == Panel.SETTINGS) {
            double mx = e.x();
            double my = e.y();
            if (e.button() == 0) {
                int x = settingsFormatDropX;
                int y = settingsFormatDropY;
                int w = settingsFormatDropW;
                int h = settingsFormatDropH;
                boolean inside = mx >= x && mx < x + w && my >= y && my < y + h;
                if (!inside) {
                    clearSettingsFormatDropdown();
                    clearSettingsUnreadBadgeStyleDropdown();
                    return true;
                }
                int rowH = 18;
                int idx = (int) ((my - y) / rowH);
                ChatUtilitiesClientOptions.CopyFormattedStyle[] vals =
                        ChatUtilitiesClientOptions.CopyFormattedStyle.values();
                if (idx >= 0 && idx < vals.length) {
                    if (settingsFormatDropdownSymbolPalette) {
                        ChatUtilitiesClientOptions.setSymbolPaletteInsertStyle(vals[idx]);
                    } else {
                        ChatUtilitiesClientOptions.setCopyFormattedStyle(vals[idx]);
                    }
                    clearSettingsFormatDropdown();
                    clearSettingsUnreadBadgeStyleDropdown();
                    saveOptions();
                    init();
                    return true;
                }
            }
            return true;
        }
        if (chatActionTypeDropdownOpen && activePanel == Panel.CHAT_ACTIONS) {
            double mx = e.x();
            double my = e.y();
            if (e.button() == 0) {
                int x = chatActionTypeDropX;
                int y = chatActionTypeDropY;
                int w = chatActionTypeDropW;
                int h = chatActionTypeDropH;
                boolean inside = mx >= x && mx < x + w && my >= y && my < y + h;
                if (!inside) {
                    clearPendingChatActionTypePickState();
                    return true;
                }
                int rowH = 18;
                int idx = (int) ((my - y) / rowH);
                ChatActionEffect.Type[] types = ChatActionEffect.Type.values();
                if (idx >= 0 && idx < types.length) {
                    ChatActionEffect.Type picked = types[idx];
                    ChatUtilitiesManager mgr = ChatUtilitiesManager.get();
                    ServerProfile p =
                            chatActionTypePickProfileId != null
                                    ? mgr.getProfile(chatActionTypePickProfileId)
                                    : profile();
                    if (p != null) {
                        if (chatActionTypePickGroupIndex < 0 || chatActionTypePickEffectIndex < 0) {
                            chatActionNewType = picked;
                        } else {
                            ChatActionEffect eff =
                                    p.getChatActionGroups()
                                            .get(chatActionTypePickGroupIndex)
                                            .getEffects()
                                            .get(chatActionTypePickEffectIndex);
                            String snd = eff != null ? eff.getSoundId() : "";
                            try {
                                mgr.setChatActionEffectAt(
                                        p,
                                        chatActionTypePickGroupIndex,
                                        chatActionTypePickEffectIndex,
                                        picked,
                                        snd);
                            } catch (IllegalArgumentException ignored) {
                            }
                        }
                    }
                    clearPendingChatActionTypePickState();
                    init();
                    return true;
                }
            }
            return true;
        }
        if (modColorPickerOverlay != null) {
            modColorPickerOverlay.layout(this.width, this.height);
            double mx = e.x();
            double my = e.y();
            if (e.button() == 0) {
                if (dispatchModalWidgetClick(modColorPickerOverlay.getHexField(), event, doubleClick)) {
                    colorPickerHexKeyboardCapture = true;
                    return true;
                }
                if (modColorPickerOverlay.isDoneButton(mx, my)) {
                    ModPrimaryColorPickerOverlay p = modColorPickerOverlay;
                    modColorPickerOverlay = null;
                    colorPickerHexKeyboardCapture = false;
                    p.applyAndPersist(this::init);
                    return true;
                }
                if (modColorPickerOverlay.isCancelButton(mx, my)) {
                    if (modColorPickerOverlay.isChatHighlightMode()) {
                        clearPendingChatHighlightPickState();
                    }
                    modColorPickerOverlay = null;
                    colorPickerHexKeyboardCapture = false;
                    return true;
                }
                if (!modColorPickerOverlay.contains((int) mx, (int) my)) {
                    if (modColorPickerOverlay.isChatHighlightMode()) {
                        clearPendingChatHighlightPickState();
                    }
                    modColorPickerOverlay = null;
                    colorPickerHexKeyboardCapture = false;
                    return true;
                }
                modColorPickerOverlay.mouseClicked(mx, my, 0);
                if (!modColorPickerOverlay.isHexFieldMouseOver(mx, my)) {
                    colorPickerHexKeyboardCapture = false;
                }
            }
            return true;
        }
        if (imageWhitelistOverlay != null) {
            imageWhitelistOverlay.layout(this.width, this.height);
            double mx = e.x();
            double my = e.y();
            if (e.button() == 0) {
                if (!imageWhitelistOverlay.contains((int) mx, (int) my)) {
                    imageWhitelistOverlay = null;
                    init();
                    return true;
                }
                // The overlay renders its own EditBox but we bypass Screen's normal widget routing here,
                // so we must replicate focus bookkeeping to make the field clickable/typable.
                if (dispatchModalWidgetClick(imageWhitelistOverlay.getAddField(), event, doubleClick)) {
                    return true;
                }
                imageWhitelistOverlay.mouseClicked(event, doubleClick);
                return true;
            }
            return true;
        }
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
                            e.button(), control, shift, alt);
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
        if (rebindingFullscreenImageClick && activePanel == Panel.SETTINGS) {
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
                    new ChatUtilitiesClientOptions.ClickMouseBinding(e.button(), control, shift, alt);
            ChatUtilitiesClientOptions.setFullscreenImagePreviewClickBinding(nb);
            rebindingFullscreenImageClick = false;
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
                    InputConstants.Type.MOUSE.getOrCreate(e.button()));
            KeyMapping.resetMapping();
            saveOptions();
            rebindingOpenMenuKey = false;
            init();
            return true;
        }
        if (rebindingChatPeekKey && activePanel == Panel.SETTINGS) {
            boolean handled = super.mouseClicked(event, doubleClick);
            if (handled) {
                rebindingChatPeekKey = false;
                return true;
            }
            ChatUtilitiesModClient.CHAT_PEEK_KEY.setKey(
                    InputConstants.Type.MOUSE.getOrCreate(e.button()));
            KeyMapping.resetMapping();
            saveOptions();
            rebindingChatPeekKey = false;
            init();
            return true;
        }
        if (createProfileDialogOpen) {
            if (e.button() == 0) {
                int dx = createProfileDlgX;
                int dy = createProfileDlgY;
                int dr = dx + createProfileDlgW;
                int db = dy + createProfileDlgH;
                int ix = (int) e.x();
                int iy = (int) e.y();
                if (ix < dx || ix >= dr || iy < dy || iy >= db) {
                    createProfileDialogOpen = false;
                    init();
                    return true;
                }
            }
            if (dispatchModalWidgetClick(dlgCreateProfileNameField, event, doubleClick)) {
                return true;
            }
            if (dispatchModalWidgetClick(dlgCreateProfileOkButton, event, doubleClick)) {
                return true;
            }
            if (dispatchModalWidgetClick(dlgCreateProfileCancelButton, event, doubleClick)) {
                return true;
            }
            return true;
        }
        if (importExportChoiceDialogOpen && activePanel == Panel.SETTINGS) {
            if (e.button() == 0) {
                int dx = importExportDlgX;
                int dy = importExportDlgY;
                int dr = dx + importExportDlgW;
                int db = dy + importExportDlgH;
                int ix = (int) e.x();
                int iy = (int) e.y();
                if (ix < dx || ix >= dr || iy < dy || iy >= db) {
                    importExportChoiceDialogOpen = false;
                    init();
                    return true;
                }
            }
            if (dispatchModalWidgetClick(importExportProfilesOnlyBtn, event, doubleClick)) {
                return true;
            }
            if (dispatchModalWidgetClick(importExportProfilesAndSettingsBtn, event, doubleClick)) {
                return true;
            }
            if (dispatchModalWidgetClick(importExportCancelBtn, event, doubleClick)) {
                return true;
            }
            return true;
        }
        if (chatWindowsModalBlocksContentClicks()) {
            double mx = e.x();
            double my = e.y();
            boolean inSidebar = mx >= sidebarLeft() && mx < sidebarRight();
            if (!inSidebar) {
                if (isChatWindowsFooterBar(mx, my)) {
                    return super.mouseClicked(event, doubleClick);
                }
                if (e.button() == 0) {
                    int ix = (int) mx;
                    int iy = (int) my;
                    if (renameDialogOpen) {
                        int dx = renameDlgX, dy = renameDlgY, dr = dx + renameDlgW, db = dy + renameDlgH;
                        if (ix < dx || ix >= dr || iy < dy || iy >= db) {
                            closeRenameWindowDialog();
                            init();
                            return true;
                        }
                    } else if (createWinDialogOpen) {
                        int dx = createDlgX, dy = createDlgY, dr = dx + createDlgW, db = dy + createDlgH;
                        if (ix < dx || ix >= dr || iy < dy || iy >= db) {
                            closeCreateWindowDialog();
                            init();
                            return true;
                        }
                    } else if (renameTabDialogOpen) {
                        int dx = renameTabDlgX, dy = renameTabDlgY, dr = dx + renameTabDlgW, db = dy + renameTabDlgH;
                        if (ix < dx || ix >= dr || iy < dy || iy >= db) {
                            closeRenameTabDialog();
                            init();
                            return true;
                        }
                    } else if (newTabDialogOpen) {
                        int dx = newTabDlgX, dy = newTabDlgY, dr = dx + newTabDlgW, db = dy + newTabDlgH;
                        if (ix < dx || ix >= dr || iy < dy || iy >= db) {
                            closeNewTabDialog();
                            init();
                            return true;
                        }
                    }
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
                } else if (createWinDialogOpen) {
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
                } else if (renameTabDialogOpen) {
                    if (dispatchModalWidgetClick(dlgRenameTabNameField, event, doubleClick)) {
                        return true;
                    }
                    if (dispatchModalWidgetClick(dlgRenameTabOkButton, event, doubleClick)) {
                        return true;
                    }
                    if (dispatchModalWidgetClick(dlgRenameTabCancelButton, event, doubleClick)) {
                        return true;
                    }
                } else if (newTabDialogOpen) {
                    if (dispatchModalWidgetClick(dlgNewTabNameField, event, doubleClick)) {
                        return true;
                    }
                    if (dispatchModalWidgetClick(dlgNewTabOkButton, event, doubleClick)) {
                        return true;
                    }
                    if (dispatchModalWidgetClick(dlgNewTabCancelButton, event, doubleClick)) {
                        return true;
                    }
                }
                if (e.button() == 0) {
                    return true;
                }
            }
        }

        if (e.button() == 0) {
            double mx = e.x();
            double my = e.y();

            if (activePanel == Panel.CHAT_ACTIONS) {
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
            if (activePanel == Panel.CHAT_ACTIONS && soundSugTarget != null && !sugFiltered.isEmpty()) {
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

            // Command suggestion click (Command Aliases → target command field)
            EditBox cmdSugTarget = activeCommandSuggestionField();
            if (activePanel == Panel.COMMAND_ALIASES && cmdSugTarget != null && !cmdSugFiltered.isEmpty()) {
                if (mx >= cmdSugLeft
                        && mx < cmdSugLeft + cmdSugWidth
                        && my >= cmdSugTop
                        && my < cmdSugTop + cmdSugVisibleRows * SUGGEST_ROW_H) {
                    int row = (int) ((my - cmdSugTop) / SUGGEST_ROW_H);
                    int idx = cmdSugScroll + row;
                    if (row >= 0 && row < cmdSugVisibleRows && idx >= 0 && idx < cmdSugFiltered.size()) {
                        cmdSugTarget.setValue(cmdSugFiltered.get(idx));
                        cmdSugTarget.moveCursorToEnd(false);
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
                boolean showUpdFooter = sidebarShowUpdateFooterRow();
                int footerRows = showUpdFooter ? 3 : 2;
                int footerTop = sidebarBottom() - footerRows * SIDEBAR_FOOTER_ROW_H;
                if (my >= footerTop && my < footerTop + SIDEBAR_FOOTER_ROW_H) {
                    playUiClick();
                    createProfileDialogOpen = true;
                    init();
                    return true;
                }
                if (showUpdFooter) {
                    int updY = footerTop + SIDEBAR_FOOTER_ROW_H;
                    if (my >= updY && my < updY + SIDEBAR_FOOTER_ROW_H) {
                        playUiClick();
                        try {
                            if (Desktop.isDesktopSupported()) {
                                Desktop d = Desktop.getDesktop();
                                if (d.isSupported(Desktop.Action.BROWSE)) {
                                    d.browse(URI.create(ModUpdateChecker.RELEASES_URL));
                                }
                            }
                        } catch (Exception ignored) {
                        }
                        return true;
                    }
                }
                int settingsY = footerTop + (showUpdFooter ? 2 : 1) * SIDEBAR_FOOTER_ROW_H;
                if (my >= settingsY && my < sidebarBottom()) {
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
        if (e.button() == 0 && tryThinScrollbarMouseDown(e.x(), e.y())) {
            return true;
        }
        if (activePanel == Panel.CHAT_WINDOWS && e.button() == 0) {
            double mx = e.x();
            double my = e.y();
            for (MenuTabRect rc : menuTabRects) {
                if (menuTabRectContains(rc, mx, my)) {
                    menuDragWindowId = rc.windowId();
                    menuDragTabId = rc.tabId();
                    menuDragFromIndex = rc.tabIndex();
                    menuDragHoverIndex = rc.tabIndex();
                    menuDragPressX = mx;
                    menuDragPressY = my;
                    menuDragDidMove = false;
                    break;
                }
            }
            if (menuDragWindowId != null) {
                // Prevent the underlying tab widget click from firing; we'll treat release-without-drag as a click.
                return true;
            }
        }
        boolean handled = super.mouseClicked(event, doubleClick);
        if (handled) {
            return true;
        }
        if (activePanel == Panel.CHAT_WINDOWS
                && e.button() == 0
                && !chatWindowsModalBlocksContentClicks()) {
            double mx = e.x();
            double my = e.y();
            int vt = chatWindowsScrollViewportTop();
            int vb = chatWindowsScrollViewportBottom();
            boolean inViewport =
                    mx >= contentLeft()
                            && mx < contentRight() - scrollbarReserve(chatWindowsShowScrollbar)
                            && my >= vt
                            && my < vb;
            if (inViewport) {
                boolean onWidget = false;
                for (GuiEventListener child : this.children()) {
                    if (child instanceof AbstractWidget w && w.visible && w.isMouseOver(mx, my)) {
                        onWidget = true;
                        break;
                    }
                }
                if (!onWidget) {
                    chatWindowsContentDragScroll = true;
                    chatWindowsContentDragAnchorMy = (int) my;
                    chatWindowsContentDragAnchorScroll = chatWindowsListScrollPixels;
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        MouseButtonEvent e = remapMenuPointerForHitTest(event);
        if (activePanel == Panel.CHAT_WINDOWS && e.button() == 0 && chatWindowsContentDragScroll) {
            int max = chatWindowsMaxContentScroll();
            if (max > 0) {
                int my = (int) e.y();
                int delta = my - chatWindowsContentDragAnchorMy;
                int next = Mth.clamp(chatWindowsContentDragAnchorScroll - delta, 0, max);
                if (next != chatWindowsListScrollPixels) {
                    chatWindowsListScrollPixels = next;
                    relayoutChatWindowsDuringContentDragScroll();
                }
            }
            return true;
        }
        if (activePanel == Panel.CHAT_WINDOWS
                && e.button() == 0
                && menuDragWindowId != null
                && menuDragTabId != null
                && menuDragFromIndex >= 0) {
            double mx = e.x();
            double my = e.y();
            if (!menuDragDidMove) {
                double ddx = mx - menuDragPressX;
                double ddy = my - menuDragPressY;
                if (ddx * ddx + ddy * ddy >= 4.0) { // 2px threshold
                    menuDragDidMove = true;
                }
            }
            int hover = -1;
            for (MenuTabRect rc : menuTabRects) {
                if (!rc.windowId().equals(menuDragWindowId)) {
                    continue;
                }
                if (menuTabRectContains(rc, mx, my)) {
                    hover = rc.tabIndex();
                    break;
                }
            }
            menuDragHoverIndex = hover;
            if (menuDragDidMove && hover >= 0 && hover != menuDragFromIndex) {
                ServerProfile p = profile();
                if (p != null) {
                    ChatWindow w = p.getWindow(menuDragWindowId);
                    if (w != null && w.getTabCount() > 1) {
                        boolean moved = w.moveTab(menuDragFromIndex, hover);
                        if (moved) {
                            // Keep dragging the same tab after mutation.
                            int now = -1;
                            if (menuDragTabId != null) {
                                for (int ti = 0; ti < w.getTabs().size(); ti++) {
                                    ChatWindowTab t = w.getTabs().get(ti);
                                    if (t != null && menuDragTabId.equals(t.getId())) {
                                        now = ti;
                                        break;
                                    }
                                }
                            }
                            menuDragFromIndex = now >= 0 ? now : hover;
                            menuDragHoverIndex = menuDragFromIndex;
                            relayoutChatWindowsDuringMenuTabDrag();
                        }
                    }
                }
            }
            return true;
        }
        return super.mouseDragged(e, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        MouseButtonEvent e = remapMenuPointerForHitTest(event);
        if (e.button() == 0 && chatWindowsContentDragScroll) {
            chatWindowsContentDragScroll = false;
            chatWindowsContentDragAnchorMy = 0;
            chatWindowsContentDragAnchorScroll = 0;
            return true;
        }
        if (e.button() == 0
                && menuDragWindowId != null
                && menuDragTabId != null
                && menuDragFromIndex >= 0) {
            try {
                ServerProfile p = profile();
                if (activePanel == Panel.CHAT_WINDOWS && p != null) {
                    ChatWindow w = p.getWindow(menuDragWindowId);
                    if (w != null && w.getTabCount() > 1) {
                        int from = menuDragFromIndex;
                        int to = menuDragHoverIndex;
                        if (menuDragDidMove) {
                            ChatUtilitiesManager.get().save();
                            init();
                        } else if (!menuDragDidMove) {
                            // Click: select tab without reordering.
                            menuWindowTabId.put(menuDragWindowId, menuDragTabId);
                            init();
                        }
                    }
                }
            } finally {
                menuDragWindowId = null;
                menuDragTabId = null;
                menuDragFromIndex = -1;
                menuDragHoverIndex = -1;
                menuDragPressX = 0;
                menuDragPressY = 0;
                menuDragDidMove = false;
            }
            return true;
        }
        return super.mouseReleased(e);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (modColorPickerOverlay != null) {
            return true;
        }
        double lmx = menuPointerToLogicalX(mouseX);
        double lmy = menuPointerToLogicalY(mouseY);
        if (imageWhitelistOverlay != null) {
            imageWhitelistOverlay.layout(this.width, this.height);
            if (imageWhitelistOverlay.mouseScrolled(lmx, lmy, horizontalAmount, verticalAmount)) {
                return true;
            }
            return true;
        }
        if (chatWindowsModalBlocksContentClicks() && lmx >= sidebarRight()) {
            return true;
        }
        if (activePanel == Panel.CHAT_ACTIONS && activeSoundSuggestionField() != null
                && !sugFiltered.isEmpty()) {
            int maxScroll = Math.max(0, sugFiltered.size() - SUGGEST_VISIBLE_ROWS);
            if (maxScroll > 0) {
                int visH = sugVisibleRows * SUGGEST_ROW_H;
                if (lmx >= sugLeft && lmx < sugLeft + sugWidth
                        && lmy >= sugTop && lmy < sugTop + visH) {
                    int step = verticalAmount > 0 ? -1 : 1;
                    sugScroll = Mth.clamp(sugScroll + step, 0, maxScroll);
                    return true;
                }
            }
        }
        if (activePanel == Panel.COMMAND_ALIASES && activeCommandSuggestionField() != null
                && !cmdSugFiltered.isEmpty()) {
            int maxScroll = Math.max(0, cmdSugFiltered.size() - SUGGEST_VISIBLE_ROWS);
            if (maxScroll > 0) {
                int visH = cmdSugVisibleRows * SUGGEST_ROW_H;
                if (lmx >= cmdSugLeft && lmx < cmdSugLeft + cmdSugWidth
                        && lmy >= cmdSugTop && lmy < cmdSugTop + visH) {
                    int step = verticalAmount > 0 ? -1 : 1;
                    cmdSugScroll = Mth.clamp(cmdSugScroll + step, 0, maxScroll);
                    return true;
                }
            }
        }
        if (activePanel == Panel.CHAT_WINDOWS && lmx >= contentLeft() && lmx <= contentRight()) {
            int vt = chatWindowsScrollViewportTop();
            int vb = chatWindowsScrollViewportBottom();
            if (lmy >= vt && lmy < vb) {
                int max = chatWindowsMaxContentScroll();
                if (max > 0) {
                    int step = settingsScrollStepPixels(verticalAmount);
                    int next = Mth.clamp(chatWindowsListScrollPixels + step, 0, max);
                    if (next != chatWindowsListScrollPixels) {
                        chatWindowsListScrollPixels = next;
                        applyChatWindowsScrollToWidgets();
                    }
                }
                return true;
            }
        }
        if (activePanel == Panel.SETTINGS
                && lmx >= contentLeft()
                && lmx <= contentRight()
                && lmy >= settingsScrollViewportTop()
                && lmy < settingsScrollViewportBottom()) {
            int maxSet = settingsMaxContentScroll();
            if (maxSet > 0) {
                int step = settingsScrollStepPixels(verticalAmount);
                int next = Mth.clamp(settingsContentScroll + step, 0, maxSet);
                if (next != settingsContentScroll) {
                    settingsContentScroll = next;
                    applySettingsScrollToWidgets();
                    return true;
                }
            }
            return true;
        }
        if (lmx >= sidebarLeft() && lmx < sidebarRight()) {
            List<ServerProfile> profiles = sortedProfiles();
            int titleH = 30, footerH = 26;
            int listAreaH = panelH() - titleH - 1 - footerH - 2;
            int maxScroll = Math.max(0, computeSidebarTotalH(profiles) - listAreaH);
            int delta = verticalAmount > 0 ? -PROFILE_ROW_H : PROFILE_ROW_H;
            sidebarScroll = Mth.clamp(sidebarScroll + delta, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(lmx, lmy, horizontalAmount, verticalAmount);
    }

    @Override
    public void removed() {
        ServerProfile p = profile();
        if (activePanel == Panel.CHAT_WINDOWS && p != null) {
            persistMenuExpandedChatWindows(p);
        }
        super.removed();
        ChatUtilitiesClientOptions.setLastMenuState(
                selectedProfileId,
                activePanel.name(),
                serverScroll,
                winScroll,
                actionScroll,
                aliasScroll,
                settingsContentScroll,
                chatWindowsListScrollPixels);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }
}
