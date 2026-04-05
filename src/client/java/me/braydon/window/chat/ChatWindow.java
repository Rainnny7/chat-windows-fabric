package me.braydon.window.chat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;

public final class ChatWindow {
    private static final int MAX_STORED_LINES = 100;
    public static final float DEFAULT_WIDTH_FRAC = 0.32f;
    public static final int DEFAULT_MAX_VISIBLE_LINES = 12;
    public static final float MIN_WIDTH_FRAC = 0.12f;
    public static final float MAX_WIDTH_FRAC = 0.98f;
    public static final int MIN_VISIBLE_LINES = 2;
    /** Enough for a tall viewport on large GUIs; not tied to vanilla chat line count. */
    public static final int MAX_VISIBLE_LINES_CAP = 512;

    private final String id;
    private final List<Pattern> patterns = new ArrayList<>();
    private final List<String> patternSources = new ArrayList<>();
    private final Deque<ChatWindowLine> lines = new ArrayDeque<>();
    private int historyScrollRows;
    private float anchorX = 0.02f;
    private float anchorY = 0.85f;
    private float widthFrac = DEFAULT_WIDTH_FRAC;
    private int maxVisibleLines = DEFAULT_MAX_VISIBLE_LINES;
    private boolean visible = true;
    private boolean positioningMode;

    public ChatWindow(String id, Pattern firstPattern, String firstSource) {
        this.id = id;
        this.patterns.add(firstPattern);
        this.patternSources.add(firstSource);
    }

    /** Restore from persistence (compiled list must match sources). */
    public ChatWindow(String id, List<Pattern> compiled, List<String> sources) {
        this.id = id;
        this.patterns.addAll(compiled);
        this.patternSources.addAll(sources);
    }

    public String getId() {
        return id;
    }

    public List<String> getPatternSources() {
        return Collections.unmodifiableList(patternSources);
    }

    public int getPatternCount() {
        return patternSources.size();
    }

    public String getPrimaryRegexSource() {
        return patternSources.isEmpty() ? "" : patternSources.getFirst();
    }

    public void addPattern(Pattern pattern, String source) {
        patterns.add(pattern);
        patternSources.add(source);
    }

    public boolean removePatternAtUserIndex(int userPosition) {
        int i = userPosition - 1;
        if (i < 0 || i >= patterns.size() || patterns.size() <= 1) {
            return false;
        }
        patterns.remove(i);
        patternSources.remove(i);
        return true;
    }

    public boolean matches(String text) {
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
        while (lines.size() >= MAX_STORED_LINES) {
            lines.removeFirst();
        }
        lines.addLast(line);
    }

    public void clearLines() {
        lines.clear();
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

    public void togglePositioningMode() {
        this.positioningMode = !this.positioningMode;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
