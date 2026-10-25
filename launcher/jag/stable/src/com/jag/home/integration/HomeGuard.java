package com.jag.home.integration;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityOptions;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;

/** Fire OS 7 event-driven HOME adapter, derived from dumbtv's MIT Guard. No key/content access. */
public final class HomeGuard extends AccessibilityService {
    private static HomeGuard running;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final GuardPolicy policy = new GuardPolicy();
    private boolean queued;
    private final Runnable releasePending = () -> { queued = false; policy.arrived(); };
    private final Runnable expired = () -> {
        if (!GuardState.armed(this)) GuardState.disarm(this);
    };

    static boolean bound() { return running != null; }
    public static boolean active(Context c) { return bound() && GuardState.eligible(c); }

    private static PendingIntent pending(Context c) {
        Intent intent = new Intent(c, com.jag.home.dev.Home.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, 73, intent, PendingIntent.FLAG_UPDATE_CURRENT,
            ActivityOptions.makeCustomAnimation(c, 0, 0).toBundle());
    }

    public static void arrived(Context c) {
        ((AlarmManager) c.getSystemService(Context.ALARM_SERVICE)).cancel(pending(c));
        if (running != null) {
            running.policy.arrived(); running.queued = false;
            running.handler.removeCallbacks(running.releasePending);
        }
    }

    static void cancel(Context c) {
        arrived(c);
        if (running != null) running.handler.removeCallbacks(running.releasePending);
    }

    public static void shown() { if (running != null) { running.queued = false; running.policy.arrived(); } }

    static void leaseChanged() {
        if (running == null) return;
        running.handler.removeCallbacks(running.expired);
        long expiry = GuardState.data(running).getLong("expiry", 0);
        if (expiry > 0 && GuardState.armed(running)) {
            running.handler.postDelayed(running.expired, Math.max(1, GuardState.remainingLease(running)));
        }
    }

    @Override protected void onServiceConnected() {
        running = this;
        leaseChanged();
        if (GuardState.eligible(this) && GuardState.data(this).getBoolean("bootPending", false)) {
            GuardState.data(this).edit().putBoolean("bootPending", false).apply();
            launch();
        }
    }

    static void boot(Context c) {
        if (!GuardState.armed(c)) return;
        GuardState.data(c).edit().putBoolean("bootPending", true).putString("drawNonce", "").commit();
        if (running != null && GuardState.eligible(c)) {
            GuardState.data(c).edit().putBoolean("bootPending", false).apply();
            running.launch();
        }
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        if (!GuardState.eligible(this)) { cancel(this); return; }
        String pkg = event.getPackageName().toString();
        if ("android".equals(pkg) || "com.android.systemui".equals(pkg)) return; // long-HOME quick menu
        if (!"com.amazon.tv.launcher".equals(pkg)) {
            if (!getPackageName().equals(pkg)) { policy.leftForApp(); cancel(this); }
            return;
        }
        String cls = event.getClassName() == null ? "" : event.getClassName().toString();
        if (policy.request(true, GuardState.data(this).getString("amazonClass", ""), pkg, cls, SystemClock.uptimeMillis())) launch();
    }

    private void launch() {
        if (!GuardState.eligible(this) || queued) return;
        queued = true;
        // API 28's alarm path needs no accessibility overlay. Keep no window above the native quick menu.
        handler.postDelayed(releasePending, 1500);
        try {
            ((AlarmManager) getSystemService(Context.ALARM_SERVICE)).setExact(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime(), pending(this));
        } catch (RuntimeException failure) {
            queued = false; policy.arrived(); handler.removeCallbacks(releasePending);
        }
    }

    @Override public void onInterrupt() { cancel(this); }
    @Override public void onDestroy() {
        cancel(this); handler.removeCallbacksAndMessages(null);
        if (running == this) running = null;
        super.onDestroy();
    }
}
