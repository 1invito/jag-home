package com.jag.home.dev;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Only successful JAG-initiated launches. Four IDs, no timestamps or content metadata. */
final class RecentHistory {
    private RecentHistory() {}

    static List<String> read(String saved) {
        List<String> ids = new ArrayList<>();
        if (saved == null) return ids;
        for (String id : saved.split(",")) {
            if (Arrays.asList(Favorites.PACKAGES).contains(id) && !ids.contains(id)) ids.add(id);
            if (ids.size() == 4) break;
        }
        return ids;
    }

    static String launched(String saved, String id) {
        List<String> ids = read(saved);
        if (!Arrays.asList(Favorites.PACKAGES).contains(id)) return join(ids);
        ids.remove(id);
        ids.add(0, id);
        while (ids.size() > 4) ids.remove(ids.size() - 1);
        return join(ids);
    }

    private static String join(List<String> ids) {
        StringBuilder out = new StringBuilder();
        for (String id : ids) {
            if (out.length() > 0) out.append(',');
            out.append(id);
        }
        return out.toString();
    }
}
