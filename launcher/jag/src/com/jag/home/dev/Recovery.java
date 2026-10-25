package com.jag.home.dev;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;

final class Recovery {
    private Recovery() {}

    static void systemSettings(Activity a) {
        if (!Ui.open(a, new Intent(Settings.ACTION_SETTINGS))) Ui.message(a, "System settings are unavailable. Press Home to return to dumbtv.");
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
