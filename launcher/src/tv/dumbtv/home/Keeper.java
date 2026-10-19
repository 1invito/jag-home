package tv.dumbtv.home;

import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * Watches for Amazon's launcher coming back to the foreground and puts dumbtv home back on top.
 *
 * Fire OS 6.7 refuses third-party accessibility services, so Guard cannot run there. This service
 * needs no accessibility: it reads the foreground package from UsageStats (granted with
 * `appops set tv.dumbtv.home GET_USAGE_STATS allow`) and starts Home through an AlarmManager
 * PendingIntent, which keeps the launch out of the 5 s app-switch delay.
 */
public final class Keeper extends Service {
    private static final String AMAZON_HOME = "com.amazon.tv.launcher";
    private static final long POLL_MS = 250;
    private static final long DEBOUNCE_MS = 1500;

    private static volatile boolean paused;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private UsageStatsManager usage;
    private long lastLaunch;

    /** Lets Amazon's home stay on screen until the user opens another app, like Guard.pause(). */
    static void pause() {
        paused = true;
    }

    static void start(Context c) {
        // Fire OS 6 only: 7 and 8 still use the accessibility Guard.
        if (android.os.Build.VERSION.SDK_INT >= 28) return;
        c.startService(new Intent(c, Keeper.class));
    }

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            try {
                check();
            } catch (Throwable ignored) {
                // Never let one bad query kill the watchdog.
            }
            ui.postDelayed(this, POLL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        usage = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        startForeground(1, new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_menu_recent_history)
                .setContentTitle("dumbtv")
                .setContentText("home keeper")
                .build());
        ui.post(poll);
        ui.postDelayed(this::firstStart, 2500);
    }

    /** After a boot or an update there is no Home task yet, and UsageStats saw no new event. */
    private void firstStart() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null || !manager.getAppTasks().isEmpty()) return;
        android.util.Log.i("dumbtvtest", "keeper: first start");
        launch(System.currentTimeMillis());
    }

    private void check() {
        long now = System.currentTimeMillis();
        String top = top(now);
        if (top == null) return;
        if (!AMAZON_HOME.equals(top)) {
            if (!"com.android.systemui".equals(top) && !"android".equals(top)) paused = false;
            return;
        }
        if (paused) return;
        if (now - lastLaunch < DEBOUNCE_MS) return;
        launch(now);
    }

    private String top(long now) {
        if (usage == null) return null;
        UsageEvents events = usage.queryEvents(now - 4000, now);
        UsageEvents.Event e = new UsageEvents.Event();
        String top = null;
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) top = e.getPackageName();
        }
        return top;
    }

    private void launch(long now) {
        lastLaunch = now;
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        if (manager != null) {
            try {
                for (ActivityManager.AppTask task : manager.getAppTasks()) {
                    task.moveToFront();
                    android.util.Log.i("dumbtvtest", "keeper: moveToFront");
                    return;
                }
            } catch (Throwable ignored) {
                // Fall through to the alarm below.
            }
        }
        Intent i = new Intent(this, Home.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 1, i, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        am.setExact(AlarmManager.RTC, now, pi);
        android.util.Log.i("dumbtvtest", "keeper: alarm set");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
