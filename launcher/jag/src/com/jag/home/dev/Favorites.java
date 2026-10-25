package com.jag.home.dev;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Pure model: exactly six owner-selected packages, including unavailable entries. */
final class Favorites {
    static final String[] PACKAGES = {"com.netflix.ninja", "com.onemainstream.sonyliv.android",
        "in.startv.hotstar", "com.amazon.firetv.youtube", "com.fancode.tv", "com.expressvpn.vpn"};
    static final String[] NAMES = {"Netflix", "Sony LIV", "JioHotstar", "YouTube", "FanCode", "ExpressVPN"};
    private Favorites() {}

    static List<String> order(String saved) {
        List<String> out = new ArrayList<>();
        if (saved != null) {
            for (String id : saved.split(",")) {
                if (Arrays.asList(PACKAGES).contains(id) && !out.contains(id)) out.add(id);
            }
        }
        for (String id : PACKAGES) if (!out.contains(id)) out.add(id);
        return out;
    }

    static String name(String id) {
        int i = Arrays.asList(PACKAGES).indexOf(id);
        return i < 0 ? "Application" : NAMES[i];
    }

    static String move(String saved, int from, int to) {
        List<String> ids = order(saved);
        if (from >= 0 && from < ids.size() && to >= 0 && to < ids.size()) {
            String id = ids.remove(from);
            ids.add(to, id);
        }
        StringBuilder joined = new StringBuilder();
        for (String id : ids) {
            if (joined.length() > 0) joined.append(',');
            joined.append(id);
        }
        return joined.toString();
    }

    static String availability(boolean installed, boolean launchable) {
        return !installed ? "Not installed" : !launchable ? "Unavailable" : "";
    }
}
