package me.braydon.chatutilities.chat;

import java.util.*;

/** One server profile: host rules, ignore patterns, and chat windows. */
public final class ServerProfile {
    private final String id;
    private String displayName;
    private final List<String> servers = new ArrayList<>();
    private final List<String> ignorePatternSources = new ArrayList<>();
    private final List<MessageSoundRule> messageSounds = new ArrayList<>();
    private final LinkedHashMap<String, ChatWindow> windows = new LinkedHashMap<>();

    public ServerProfile(String id, String displayName) {
        this.id = Objects.requireNonNull(id);
        this.displayName = displayName == null ? id : displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName == null ? "" : displayName;
    }

    public List<String> getServers() {
        return servers;
    }

    public List<String> getIgnorePatternSources() {
        return ignorePatternSources;
    }

    public List<MessageSoundRule> getMessageSounds() {
        return messageSounds;
    }

    public LinkedHashMap<String, ChatWindow> getWindows() {
        return windows;
    }

    public ChatWindow getWindow(String windowId) {
        return windows.get(windowId);
    }

    public boolean hasWindow(String windowId) {
        return windows.containsKey(windowId);
    }

    public List<String> getWindowIds() {
        return new ArrayList<>(windows.keySet());
    }
}
