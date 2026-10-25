package com.jag.home.dev;

import android.app.Activity;
import android.view.View;
import android.widget.LinearLayout;

/** Development build hook: deliberately has no HOME capability. Stable build substitutes its adapter. */
final class HomeIntegration {
    private HomeIntegration() {}
    static void arrived(Activity activity) {}
    static void observeDraw(View root) {}
    static void preferences(Activity activity, LinearLayout list) {}
    static String title() { return "JAG Home · cinematic development preview 0.2"; }
    static String about() { return "The development preview has no background services and leaves the existing Home binding unchanged."; }
    static String recoveryHint() { return "Press Home to use the existing dumbtv binding."; }
    static boolean consumeBack(Activity activity) { return false; }
}
