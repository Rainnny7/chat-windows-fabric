package me.braydon.chatutilities.chat;

import java.util.Objects;

/** User rule: when chat matches {@link #patternSource}, play {@link #soundId} (registry id string). */
public final class MessageSoundRule {
    private final String patternSource;
    private final String soundId;

    public MessageSoundRule(String patternSource, String soundId) {
        this.patternSource = patternSource == null ? "" : patternSource;
        this.soundId = soundId == null ? "" : soundId;
    }

    public String getPatternSource() {
        return patternSource;
    }

    public String getSoundId() {
        return soundId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MessageSoundRule other)) {
            return false;
        }
        return patternSource.equals(other.patternSource) && soundId.equals(other.soundId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(patternSource, soundId);
    }
}
