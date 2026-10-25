package com.jag.home.dev;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

final class Recovery {
    private Recovery() {}

    static void systemSettings(Activity a) {
        // On the verified Fire TV Stick 3rd Gen, the generic Android Settings action
        // resolves to SettingsActivity, but the physical long-Home menu opens
        // MainSettingsActivity. Try the observed Fire OS entry point first.
        // An explicit shell launch succeeded; the app launch still needs a real-device check.
        if ("Amazon".equalsIgnoreCase(Build.MANUFACTURER) && "AFTSSS".equals(Build.MODEL)) {
            Intent fireSettings = new Intent().setClassName(
                "com.amazon.tv.launcher", "com.amazon.tv.launcher.ui.MainSettingsActivity");
            if (Ui.open(a, fireSettings)) return;
        }
        // Preserve normal Android/other Fire OS behavior when the Fire TV route fails.
        if (!Ui.open(a, new Intent(Settings.ACTION_SETTINGS))) {
            Ui.message(a, "System settings are unavailable. Hold Home and choose Settings. "
                + HomeIntegration.recoveryHint());
        }
    }

    static void applications(Activity a) {
        if (!Ui.open(a, new Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))) systemSettings(a);
    }

    static void details(Activity a, String id) {
        if (!Ui.open(a, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + id)))) applications(a);
    }

    static void dumbtv(Activity a) {
        if (!Ui.open(a, AppCatalog.launch(a.getPackageManager(), "tv.dumbtv.home"))) {
            Ui.message(a, "Could not open dumbtv. Press the remote's Home button to use your existing Home integration.");
        }
    }
}
