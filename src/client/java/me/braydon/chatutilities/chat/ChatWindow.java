package me.braydon.chatutilities.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import net.minecraft.util.Mth;

public final class ChatWindow {
    public static final float DEFAULT_WIDTH_FRAC = 0.32f;
    public static final int DEFAULT_MAX_VISIBLE_LINES = 12;
    public static final float MIN_WIDTH_FRAC = 0.12f;
    public static final float MAX_WIDTH_FRAC = 0.98f;
    public static final int MIN_VISIBLE_LINES = 2;
    public static final int MAX_VISIBLE_LINES_CAP = 512;

    /** Per-window chat text scale in the HUD (1 = match vanilla chat line height). */
    public static final float DEFAULT_TEXT_SCALE = 1f;
    public static final float MIN_TEXT_SCALE = 0.55f;
    public static final float MAX_TEXT_SCALE = 1.75f;

    public static final String DEFAULT_TAB_NAME = "Default";

    private final String id;
    private final List<ChatWindowTab> tabs = new ArrayList<>();
    private int selectedTabIndex;

    private float anchorX = 0.02f;
    private float anchorY = 0.85f;
    private float widthFrac = DEFAULT_WIDTH_FRAC;
    private int maxVisibleLines = DEFAULT_MAX_VISIBLE_LINES;
    private float textScale = DEFAULT_TEXT_SCALE;
    private boolean visible = true;
    private boolean positioningMode;

    public ChatWindow(String id, Pattern firstPattern, String firstSource) {
        this.id = id;
        ChatWindowTab t = new ChatWindowTab(UUID.randomUUID().toString(), DEFAULT_TAB_NAME);
        t.addPattern(firstPattern, firstSource);
        tabs.add(t);
    }

    public ChatWindow(String id, List<Pattern> compiled, List<String> sources) {
        this.id = id;
        ChatWindowTab t = new ChatWindowTab(UUID.randomUUID().toString(), DEFAULT_TAB_NAME, compiled, sources);
        tabs.add(t);
    }

    /** Restore from persistence with explicit tab list (at least one tab). */
    public ChatWindow(String id, List<ChatWindowTab> restoredTabs) {
        this.id = id;
        if (restoredTabs == null || restoredTabs.isEmpty()) {
            throw new IllegalArgumentException("ChatWindow requires at least one tab");
        }
        tabs.addAll(restoredTabs);
    }

    public String getId() {
        return id;
    }

    public List<ChatWindowTab> getTabs() {
        return Collections.unmodifiableList(tabs);
    }

    public int getTabCount() {
        return tabs.size();
    }

    public int getSelectedTabIndex() {
        return tabs.isEmpty() ? 0 : Mth.clamp(selectedTabIndex, 0, tabs.size() - 1);
    }

    public void setSelectedTabIndex(int index) {
        if (tabs.isEmpty()) {
            return;
        }
        selectedTabIndex = Mth.clamp(index, 0, tabs.size() - 1);
        tabs.get(selectedTabIndex).clearUnreadCount();
    }

    public ChatWindowTab getSelectedTab() {
        int i = getSelectedTabIndex();
        return tabs.get(i);
    }

    public @org.jspecify.annotations.Nullable ChatWindowTab getTabById(String tabId) {
        if (tabId == null) {
            return null;
        }
        for (ChatWindowTab t : tabs) {
            if (t.getId().equals(tabId)) {
                return t;
            }
        }
        return null;
    }

    /**
     * Same window state under a new id (for rename). Preserves tabs, HUD history, layout, and positioning flags.
     */
    public ChatWindow withId(String newId) {
        List<ChatWindowTab> copied = new ArrayList<>();
        for (ChatWindowTab t : tabs) {
            copied.add(t.copyForNewWindowId(UUID.randomUUID().toString(), t.getDisplayName()));
        }
        ChatWindow nw = new ChatWindow(newId, copied);
        nw.selectedTabIndex = this.selectedTabIndex;
        nw.anchorX = this.anchorX;
        nw.anchorY = this.anchorY;
        nw.widthFrac = this.widthFrac;
        nw.maxVisibleLines = this.maxVisibleLines;
        nw.textScale = this.textScale;
        nw.visible = this.visible;
        nw.positioningMode = this.positioningMode;
        return nw;
    }

    /** First tab id (for APIs that omit tab). */
    public String getDefaultTabId() {
        return tabs.getFirst().getId();
    }

    public List<String> getPatternSources() {
        return getSelectedTab().getPatternSources();
    }

    public void addPattern(Pattern pattern, String source) {
        getSelectedTab().addPattern(pattern, source);
    }

    public boolean removePatternAtUserIndex(int userPosition) {
        return getSelectedTab().removePatternAtUserIndex(userPosition);
    }

    public boolean setPatternAtUserIndex(int userPosition, Pattern pattern, String source) {
        return getSelectedTab().setPatternAtUserIndex(userPosition, pattern, source);
    }

    public boolean matches(String text) {
        for (ChatWindowTab t : tabs) {
            if (t.matches(text)) {
                return true;
            }
        }
        return false;
    }

    /** Append the same line instance to every tab whose patterns match {@code text}. */
    public void addLineToMatchingTabs(ChatWindowLine line, String text) {
        int sel = getSelectedTabIndex();
        for (int i = 0; i < tabs.size(); i++) {
            ChatWindowTab t = tabs.get(i);
            if (t.matches(text)) {
                t.addLine(line);
                if (i != sel && ChatUtilitiesClientOptions.isChatWindowTabUnreadBadgesEnabled()) {
                    t.incrementUnreadCount();
                }
            }
        }
    }

    public Deque<ChatWindowLine> getLines() {
        return getSelectedTab().getLines();
    }

    public int getHistoryScrollRows() {
        return getSelectedTab().getHistoryScrollRows();
    }

    public void setHistoryScrollRows(int historyScrollRows) {
        getSelectedTab().setHistoryScrollRows(historyScrollRows);
    }

    public void clampHistoryScroll(int maxScroll) {
        getSelectedTab().clampHistoryScroll(maxScroll);
    }

    public void addHistoryScrollRows(int delta, int totalWrappedRows, int viewportRows) {
        getSelectedTab().addHistoryScrollRows(delta, totalWrappedRows, viewportRows);
    }

    public void resetHistoryScroll() {
        for (ChatWindowTab t : tabs) {
            t.resetHistoryScroll();
        }
    }

    public void clearStoredChat() {
        for (ChatWindowTab t : tabs) {
            t.clearStoredChat();
        }
    }

    void addTab(ChatWindowTab tab) {
        tabs.add(Objects.requireNonNull(tab));
    }

    boolean removeTabById(String tabId) {
        if (tabs.size() <= 1 || tabId == null) {
            return false;
        }
        int idx = -1;
        for (int i = 0; i < tabs.size(); i++) {
            if (tabs.get(i).getId().equals(tabId)) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            return false;
        }
        tabs.remove(idx);
        selectedTabIndex = Mth.clamp(selectedTabIndex, 0, tabs.size() - 1);
        return true;
    }

    /**
     * Move a tab within this window (for drag-to-reorder).
     *
     * @return true if the order changed
     */
    public boolean moveTab(int fromIndex, int toIndex) {
        if (tabs.size() <= 1) {
            return false;
        }
        int n = tabs.size();
        int from = Mth.clamp(fromIndex, 0, n - 1);
        int to = Mth.clamp(toIndex, 0, n - 1);
        if (from == to) {
            return false;
        }
        ChatWindowTab selected = tabs.get(getSelectedTabIndex());
        ChatWindowTab moved = tabs.remove(from);
        int insertAt = to;
        if (from < to) {
            insertAt = Math.max(0, to - 1);
        }
        tabs.add(insertAt, moved);
        int selNow = tabs.indexOf(selected);
        selectedTabIndex = selNow >= 0 ? selNow : Mth.clamp(selectedTabIndex, 0, tabs.size() - 1);
        return true;
    }

    public float getAnchorX() {
        return anchorX;
    }

    public void setAnchorX(float anchorX) {
        this.anchorX = clamp01(anchorX);
    }

    public float getAnchorY() {
        return anchorY;
    }

    public void setAnchorY(float anchorY) {
        this.anchorY = clamp01(anchorY);
    }

    public float getWidthFrac() {
        return widthFrac;
    }

    public void setWidthFrac(float widthFrac) {
        this.widthFrac = Math.max(MIN_WIDTH_FRAC, Math.min(MAX_WIDTH_FRAC, widthFrac));
    }

    public int getMaxVisibleLines() {
        return maxVisibleLines;
    }

    public void setMaxVisibleLines(int maxVisibleLines) {
        this.maxVisibleLines = Math.max(MIN_VISIBLE_LINES, Math.min(MAX_VISIBLE_LINES_CAP, maxVisibleLines));
    }

    public float getTextScale() {
        return textScale;
    }

    public void setTextScale(float textScale) {
        this.textScale = Mth.clamp(textScale, MIN_TEXT_SCALE, MAX_TEXT_SCALE);
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void toggleVisible() {
        this.visible = !this.visible;
    }

    public boolean isPositioningMode() {
        return positioningMode;
    }

    public void setPositioningMode(boolean positioningMode) {
        this.positioningMode = positioningMode;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
