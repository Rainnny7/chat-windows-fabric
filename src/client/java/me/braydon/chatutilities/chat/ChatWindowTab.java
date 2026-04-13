package me.braydon.chatutilities.chat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import me.braydon.chatutilities.client.ChatUtilitiesClientOptions;
import net.minecraft.client.Minecraft;

/** One filter tab inside a {@link ChatWindow}: patterns and isolated message history. */
public final class ChatWindowTab {
    private final String id;
    private String displayName;
    private final List<Pattern> patterns = new ArrayList<>();
    private final List<String> patternSources = new ArrayList<>();
    private final Deque<ChatWindowLine> lines = new ArrayDeque<>();
    private int historyScrollRows;
    private int unreadCount;

    /**
     * Cached total wrapped-row count for this tab's full history (no search filter).
     * Keyed by the {@code maxLineWidth} used for wrapping; -1 means stale/uncached.
     * Invalidated whenever lines are added or removed.
     */
    private int cachedTotalWrappedRows = -1;
    private int cachedTotalWrappedWidth = -1;

    public ChatWindowTab(String id, String displayName) {
        this.id = Objects.requireNonNull(id);
        this.displayName = displayName == null || displayName.isBlank() ? "Tab" : displayName.strip();
    }

    public ChatWindowTab(String id, String displayName, List<Pattern> compiled, List<String> sources) {
        this(id, displayName);
        this.patterns.addAll(compiled);
        this.patternSources.addAll(sources);
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName == null || displayName.isBlank() ? "Tab" : displayName.strip();
    }

    public List<String> getPatternSources() {
        return Collections.unmodifiableList(patternSources);
    }

    public void addPattern(Pattern pattern, String source) {
        patterns.add(pattern);
        patternSources.add(source);
    }

    public boolean removePatternAtUserIndex(int userPosition) {
        int i = userPosition - 1;
        if (i < 0 || i >= patterns.size()) {
            return false;
        }
        patterns.remove(i);
        patternSources.remove(i);
        return true;
    }

    public boolean setPatternAtUserIndex(int userPosition, Pattern pattern, String source) {
        int i = userPosition - 1;
        if (i < 0 || i >= patterns.size()) {
            return false;
        }
        patterns.set(i, pattern);
        patternSources.set(i, source);
        return true;
    }

    public boolean matches(String text) {
        if (patterns.isEmpty()) {
            return false;
        }
        for (Pattern p : patterns) {
            if (p.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    public Deque<ChatWindowLine> getLines() {
        return lines;
    }

    public int getHistoryScrollRows() {
        return historyScrollRows;
    }

    public void setHistoryScrollRows(int historyScrollRows) {
        this.historyScrollRows = Math.max(0, historyScrollRows);
    }

    public void clampHistoryScroll(int maxScroll) {
        historyScrollRows = Math.min(historyScrollRows, Math.max(0, maxScroll));
    }

    public void addHistoryScrollRows(int delta, int totalWrappedRows, int viewportRows) {
        int maxScroll = Math.max(0, totalWrappedRows - viewportRows);
        historyScrollRows = Math.max(0, Math.min(historyScrollRows + delta, maxScroll));
    }

    public void resetHistoryScroll() {
        historyScrollRows = 0;
    }

    public void addLine(ChatWindowLine line) {
        ChatWindowLine toAdd = line;
        if (ChatUtilitiesClientOptions.isStackRepeatedMessages()) {
            ChatWindowLine last = lines.peekLast();
            if (last != null && last.sameStackAs(line)) {
                lines.removeLast();
                Minecraft mc = Minecraft.getInstance();
                int mergeTick = mc != null ? mc.gui.getGuiTicks() : line.addedGuiTick();
                toAdd = last.mergedWithRepeat(mergeTick);
            }
        }
        int cap = ChatUtilitiesClientOptions.getEffectiveChatHistoryLimit();
        while (lines.size() >= cap) {
            lines.removeFirst();
        }
        lines.addLast(toAdd);
        cachedTotalWrappedRows = -1; // Invalidate wrap count cache.
    }

    /**
     * Returns the cached total wrapped-row count for this tab (all lines, no search filter), or
     * -1 if the cache is stale for the given width. Call {@link #setCachedTotalWrappedRows} after
     * a full recount to prime the cache.
     */
    public int getCachedTotalWrappedRows(int maxWidth) {
        return (cachedTotalWrappedRows >= 0 && cachedTotalWrappedWidth == maxWidth)
                ? cachedTotalWrappedRows
                : -1;
    }

    public void setCachedTotalWrappedRows(int count, int maxWidth) {
        cachedTotalWrappedRows = count;
        cachedTotalWrappedWidth = maxWidth;
    }

    public int getUnreadCount() {
        return Math.max(0, unreadCount);
    }

    public void incrementUnreadCount() {
        if (unreadCount < Integer.MAX_VALUE) {
            unreadCount++;
        }
    }

    public void clearUnreadCount() {
        unreadCount = 0;
    }

    public void clearStoredChat() {
        lines.clear();
        resetHistoryScroll();
        unreadCount = 0;
        cachedTotalWrappedRows = -1;
    }

    /** Deep copy of patterns/lines/scroll for window rename. */
    public ChatWindowTab copyForNewWindowId(String newTabId, String newDisplayName) {
        ChatWindowTab nt = new ChatWindowTab(newTabId, newDisplayName);
        nt.patterns.addAll(this.patterns);
        nt.patternSources.addAll(this.patternSources);
        nt.historyScrollRows = this.historyScrollRows;
        nt.unreadCount = this.unreadCount;
        for (ChatWindowLine line : this.lines) {
            nt.lines.addLast(line);
        }
        return nt;
    }
}
