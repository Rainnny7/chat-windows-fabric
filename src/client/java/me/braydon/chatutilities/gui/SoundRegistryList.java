package me.braydon.chatutilities.gui;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Lazily-built sorted list of registered sound event ids for pickers and autocomplete. */
public final class SoundRegistryList {
    private static List<String> allSorted;

    private SoundRegistryList() {}

    public static List<String> allSorted() {
        if (allSorted == null) {
            List<String> list = new ArrayList<>();
            for (Identifier id : BuiltInRegistries.SOUND_EVENT.keySet()) {
                list.add(id.toString());
            }
            Collections.sort(list);
            allSorted = List.copyOf(list);
        }
        return allSorted;
    }

    /** Substring match on full id (case-insensitive), stable order. */
    public static List<String> filterContains(String query, int maxResults) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).strip();
        if (q.isEmpty()) {
            List<String> all = allSorted();
            return all.size() <= maxResults ? all : all.subList(0, maxResults);
        }
        List<String> out = new ArrayList<>();
        for (String id : allSorted()) {
            if (id.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(id);
                if (out.size() >= maxResults) {
                    break;
                }
            }
        }
        return out;
    }

    /** Prefix match on the path segment after {@code namespace:} (or whole id if no colon in query). */
    public static List<String> filterPrefixPath(String query, int maxResults) {
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT).strip();
        if (q.isEmpty()) {
            return filterContains("", maxResults);
        }
        List<String> out = new ArrayList<>();
        for (String id : allSorted()) {
            if (matchesPrefix(id, q)) {
                out.add(id);
                if (out.size() >= maxResults) {
                    break;
                }
            }
        }
        return out;
    }

    private static boolean matchesPrefix(String fullId, String qLower) {
        int colon = fullId.indexOf(':');
        String path = colon >= 0 ? fullId.substring(colon + 1) : fullId;
        return path.toLowerCase(Locale.ROOT).startsWith(qLower)
                || fullId.toLowerCase(Locale.ROOT).startsWith(qLower);
    }
}
