package com.jag.home.dev;

import android.app.Activity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.jag.home.integration.GuardState;
import com.jag.home.integration.HomeGuard;

/** Stable-only adapter. Java namespace is shared; Android application ID is com.jag.home. */
final class HomeIntegration {
    private HomeIntegration() {}
    static String title() { return "JAG Home · stable HOME integration 0.3"; }
    static String about() { return "This stable package has an owner-controlled Home redirector. Installation leaves it disarmed; trial/rollback are managed by the owner tool. dumbtv remains installed for recovery."; }
    static String recoveryHint() { return "Use Return to existing dumbtv, or the owner's saved rollback procedure."; }
    static boolean consumeBack(Activity activity) { return HomeGuard.active(activity); }
    static void arrived(Activity activity) { HomeGuard.arrived(activity); }
    static void observeDraw(View root) {
        root.getViewTreeObserver().addOnDrawListener(new ViewTreeObserver.OnDrawListener() {
            private boolean posted;
            @Override public void onDraw() {
                if (posted) return;
                posted = true;
                root.post(() -> {
                    if (root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnDrawListener(this);
                    if (root.isAttachedToWindow()) GuardState.drawn(root.getContext());
                });
            }
        });
    }
    static void preferences(Activity activity, LinearLayout list) {
        TextView importButton = Ui.button(activity, "Import selected appearance", 0xFFD3E8FF,
            () -> AppearanceImport.choose(activity));
        list.addView(importButton, new LinearLayout.LayoutParams(-1, -2));
    }
}
