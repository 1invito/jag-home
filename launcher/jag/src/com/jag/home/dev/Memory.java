package com.jag.home.dev;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.text.format.Formatter;

import java.util.ArrayList;
import java.util.List;

/** On-demand observation and Settings navigation; no process-control capability. */
final class Memory {
    private Memory() {}

    static void confirm(Activity a) {
        AlertDialog dialog = Ui.dialog(a)
            .setTitle("Free Memory")
            .setMessage("Try to free memory by stopping eligible background apps? Currently playing apps and your VPN will be protected.")
            .setPositiveButton("Free Memory", (d, which) -> information(a))
            .setNegativeButton("Cancel", null).create();
        dialog.setOnShowListener(d -> Ui.focusDialog(dialog));
        dialog.show();
    }

    static String snapshot(Activity a) {
        try {
            ActivityManager am = (ActivityManager) a.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) return "Available memory could not be read.";
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            am.getMemoryInfo(info);
            return "Available memory now: " + Formatter.formatShortFileSize(a, info.availMem)
                + " of " + Formatter.formatShortFileSize(a, info.totalMem)
                + (info.lowMemory ? " (system reports low memory)." : ".")
                + "\nThis is a system snapshot, not a cleanup result.";
        } catch (RuntimeException e) {
            return "Available memory could not be read. No cleanup was attempted.";
        }
    }

    static void information(Activity a) {
        Ui.dialog(a).setTitle("Memory & background apps")
            .setMessage(snapshot(a) + "\n\n" + MemoryPolicy.explanation(Build.VERSION.SDK_INT))
            .setPositiveButton("Manage an app", (d, which) -> pickApp(a))
            .setNeutralButton("App settings", (d, which) -> Recovery.applications(a))
            .setNegativeButton("Close", null).show();
    }

    private static void pickApp(Activity a) {
        List<String> ids = new ArrayList<>(), names = new ArrayList<>();
        for (String id : Favorites.PACKAGES) {
            if ("com.expressvpn.vpn".equals(id)) continue;
            try {
                ApplicationInfo info = a.getPackageManager().getApplicationInfo(id, 0);
                if ((info.flags & (ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0) continue;
                ids.add(id);
                names.add(Favorites.name(id));
            } catch (PackageManager.NameNotFoundException | SecurityException e) {
                // Missing and system applications are not offered for manual review.
            }
        }
        if (ids.isEmpty()) {
            Ui.dialog(a).setTitle("No eligible favorites")
                .setMessage("There are no installed non-system favorites to review. JAG never stops any app automatically.")
                .setPositiveButton("App settings", (d, which) -> Recovery.applications(a))
                .setNegativeButton("Cancel", null).show();
            return;
        }
        Ui.dialog(a).setTitle("Review an app · leave active playback alone")
            .setItems(names.toArray(new String[0]), (d, which) -> Recovery.details(a, ids.get(which)))
            .setNegativeButton("Cancel", null).show();
    }
}
