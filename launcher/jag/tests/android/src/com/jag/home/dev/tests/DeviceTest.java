package com.jag.home.dev.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.view.accessibility.AccessibilityNodeInfo;
import android.graphics.Bitmap;
import android.graphics.Color;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Dependency-free instrumentation for a disposable API 28 emulator only. */
public final class DeviceTest extends Instrumentation {
    private Activity home;
    private int assertions;
    private String phase;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        phase = args == null ? "missing" : args.getString("phase", "missing");
        start();
    }

    private void check(boolean ok, String description) {
        assertions++;
        if (!ok) throw new AssertionError(description);
    }

    private List<View> views(View root) {
        List<View> all = new ArrayList<>();
        all.add(root);
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) all.addAll(views(group.getChildAt(i)));
        }
        return all;
    }

    private String focused(Activity a) {
        AtomicReference<String> result = new AtomicReference<>("");
        runOnMainSync(() -> {
            View view = a.getWindow().getDecorView().findFocus();
            if (view != null) {
                CharSequence label = view.getContentDescription();
                if (label == null && view instanceof TextView) label = ((TextView) view).getText();
                result.set(label == null ? view.getClass().getName() : label.toString());
            }
        });
        return result.get();
    }

    private void key(int code) {
        long now = SystemClock.uptimeMillis();
        getUiAutomation().injectInputEvent(new KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0), true);
        getUiAutomation().injectInputEvent(new KeyEvent(now, now + 1, KeyEvent.ACTION_UP, code, 0), true);
        waitForIdleSync();
    }

    private void returned(Activity a) {
        long deadline = SystemClock.uptimeMillis() + 10000;
        AtomicReference<Boolean> ready = new AtomicReference<>(false);
        do {
            runOnMainSync(() -> ready.set(a.getWindow().getDecorView().hasWindowFocus()));
            if (ready.get()) return;
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        check(false, "activity regains window focus after dialog/Back");
    }

    private boolean screenContains(String text) {
        long deadline = SystemClock.uptimeMillis() + 10000;
        do {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                List<AccessibilityNodeInfo> matches = root.findAccessibilityNodeInfosByText(text);
                boolean found = !matches.isEmpty();
                for (AccessibilityNodeInfo node : matches) node.recycle();
                root.recycle();
                if (found) return true;
            }
            SystemClock.sleep(100);
        } while (SystemClock.uptimeMillis() < deadline);
        return false;
    }

    private void wallpaperDecode() throws Exception {
        File image = new File(getTargetContext().getFilesDir(), "image-test.png");
        Bitmap source = Bitmap.createBitmap(3840, 2160, Bitmap.Config.RGB_565);
        source.eraseColor(Color.BLUE);
        try (FileOutputStream out = new FileOutputStream(image)) {
            check(source.compress(Bitmap.CompressFormat.PNG, 100, out), "write local test image");
        }
        source.recycle();
        // Exercise the production decoder, not a duplicate test implementation.
        Class<?> wallpapers = Class.forName("com.jag.home.dev.Wallpapers");
        Method decode = wallpapers.getDeclaredMethod("decode", File.class);
        decode.setAccessible(true);
        Bitmap decoded = (Bitmap) decode.invoke(null, image);
        check(decoded != null && decoded.getWidth() <= 1920 && decoded.getHeight() <= 1080, "real 4K decode bounded to 1080p");
        decoded.recycle();
        try (FileOutputStream out = new FileOutputStream(image)) { out.write(new byte[] {1, 2, 3}); }
        check(decode.invoke(null, image) == null, "corrupt file falls back safely");
        check(image.delete(), "test wallpaper removed");
        check(decode.invoke(null, image) == null, "missing selected file falls back safely");
    }

    private TextView row(Activity a, String starts) {
        AtomicReference<TextView> found = new AtomicReference<>();
        runOnMainSync(() -> {
            for (View v : views(a.getWindow().getDecorView())) {
                if (v instanceof TextView && ((TextView) v).getText().toString().startsWith(starts)) {
                    found.set((TextView) v);
                    return;
                }
            }
        });
        check(found.get() != null, "row exists: " + starts);
        return found.get();
    }

    private void select(View view) {
        runOnMainSync(() -> check(view.requestFocus(), "row is D-pad focusable"));
        key(KeyEvent.KEYCODE_DPAD_CENTER);
    }

    private View control(String tag) {
        AtomicReference<View> found = new AtomicReference<>();
        runOnMainSync(() -> {
            for (View view : views(home.getWindow().getDecorView())) if (tag.equals(view.getTag())) found.set(view);
        });
        check(found.get() != null, "visible control: " + tag);
        return found.get();
    }

    private void focus(String tag) {
        View control = control(tag);
        runOnMainSync(() -> check(control.requestFocus(), "control can receive focus"));
        waitForIdleSync();
    }

    private Activity preferences() {
        ActivityMonitor monitor = addMonitor("com.jag.home.dev.Preferences", null, false);
        focus("dock:1");
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        Activity settings = waitForMonitorWithTimeout(monitor, 10000); removeMonitor(monitor);
        check(settings != null, "Customize opens settings"); waitForIdleSync(); returned(settings); return settings;
    }

    private java.util.Properties saved() throws Exception {
        java.util.Properties values = new java.util.Properties();
        File file = new File(getTargetContext().getFilesDir(), "jag.properties");
        if (file.isFile()) try (java.io.FileInputStream in = new java.io.FileInputStream(file)) { values.load(in); }
        return values;
    }

    private void writeLegacy() throws Exception {
        java.util.Properties old = new java.util.Properties();
        old.setProperty("accent", "Teal"); old.setProperty("clock", "Hidden");
        old.setProperty("time", "12-hour"); old.setProperty("labels", "Hidden");
        old.setProperty("tiles", "Small"); old.setProperty("animation", "Off"); old.setProperty("dim", "40");
        old.setProperty("order", "com.fancode.tv,com.netflix.ninja,com.onemainstream.sonyliv.android,in.startv.hotstar,com.amazon.firetv.youtube,com.expressvpn.vpn");
        try (FileOutputStream out = getTargetContext().openFileOutput("jag.properties", 0)) { old.store(out, "v1 schema emulator fixture"); }
    }

    private void startHome() {
        home = startActivitySync(new Intent().setComponent(new ComponentName("com.jag.home.dev", "com.jag.home.dev.Home"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitForIdleSync(); returned(home);
    }

    private int tiles() {
        AtomicReference<Integer> count = new AtomicReference<>(0);
        runOnMainSync(() -> {
            for (View view : views(home.getWindow().getDecorView())) if (view.getClass().getSimpleName().equals("Tile")) count.set(count.get() + 1);
        });
        return count.get();
    }

    private void screenshot(String name) throws Exception {
        getUiAutomation().waitForIdle(200, 5000);
        Bitmap image = getUiAutomation().takeScreenshot();
        check(image != null, "rendered screenshot: " + name);
        try (FileOutputStream out = new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null), name + ".png"))) {
            image.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        image.recycle();
    }

    private void migration() throws Exception {
        java.util.Properties old = saved();
        check(old.getProperty("tiles").equals("Small") && old.getProperty("time").equals("12-hour"), "real APK update kept old fields");
        check(old.getProperty("order").startsWith("com.fancode.tv"), "real APK update kept custom favorite order");
        startHome();
        check(focused(home).startsWith("FanCode"), "cold update focuses the first favorite in preserved order");
        Activity settings = preferences();
        check(row(settings, "Theme").getText().toString().contains("Cinematic"), "missing v2 key defaults safely");
        check(row(settings, "Recently Opened").getText().toString().contains("On"), "new history default enabled");
        check(row(settings, "Clock format").getText().toString().contains("12-hour"), "legacy format visible in UI");
        key(KeyEvent.KEYCODE_BACK); returned(home);
        check(tiles() == 6, "legacy update has no fabricated recent history");
    }

    private void missing() throws Exception {
        getTargetContext().deleteFile("jag.properties"); startHome();
        long deadline = SystemClock.uptimeMillis() + 15000;
        while (!focused(home).contains("Not installed") && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100);
        check(focused(home).startsWith("Netflix") && focused(home).contains("Not installed"), "actual missing-app state");
        check(tiles() == 6, "six favorites; empty recent row hidden");
        key(KeyEvent.KEYCODE_DPAD_LEFT); check(focused(home).startsWith("Netflix"), "left boundary");
        for (int i = 0; i < 5; i++) key(KeyEvent.KEYCODE_DPAD_RIGHT);
        check(focused(home).startsWith("ExpressVPN"), "all favorites reachable");
        key(KeyEvent.KEYCODE_DPAD_RIGHT); check(focused(home).startsWith("ExpressVPN"), "right boundary");
        key(KeyEvent.KEYCODE_DPAD_DOWN); check(focused(home).equals("System Settings"), "direct primary to dock without recents");
        key(KeyEvent.KEYCODE_DPAD_UP); check(focused(home).startsWith("ExpressVPN"), "dock to primary");
        focus("favorite:com.netflix.ninja"); key(KeyEvent.KEYCODE_DPAD_CENTER);
        check(screenContains("Netflix is unavailable"), "honest missing-app dialog");
        key(KeyEvent.KEYCODE_BACK); returned(home);
        check(saved().getProperty("recent", "").isEmpty(), "failed launch never recorded");
        focus("dock:2"); key(KeyEvent.KEYCODE_DPAD_CENTER);
        check(screenContains("Memory Status") && screenContains("Available memory now"), "one real memory snapshot");
        check(screenContains("No apps were stopped"), "no simulated memory-cleaning claim");
        key(KeyEvent.KEYCODE_BACK); returned(home);
        check(focused(home).equals("Memory Status"), "memory dialog returns focus");
        wallpaperDecode();
        for (String name : new String[] {"Cinematic", "Midnight", "Graphite", "Ultra Minimal"}) {
            Activity settings = preferences();
            TextView theme = row(settings, "Theme");
            int safety = 0;
            while (!theme.getText().toString().contains(name) && safety++ < 4) select(theme);
            check(theme.getText().toString().contains(name), "select theme using remote: " + name);
            key(KeyEvent.KEYCODE_BACK); returned(home);
            check(saved().getProperty("theme", "Cinematic").equals(name), "theme persisted: " + name);
            screenshot("v2-" + name.replace(' ', '-').toLowerCase(java.util.Locale.ROOT));
        }
        Activity settings = preferences();
        select(row(settings, "Focus animation"));
        check(row(settings, "Focus animation").getText().toString().contains("Off"), "retain v1 animation preference");
        key(KeyEvent.KEYCODE_BACK); returned(home);
        settings = preferences();
        check(row(settings, "Focus animation").getText().toString().contains("Off"), "setting survives activity recreation");
        select(row(settings, "Restore visual defaults"));
        check(screenContains("Restore Cinematic defaults"), "restore confirmation");
        key(KeyEvent.KEYCODE_BACK); returned(settings);
        check(row(settings, "Focus animation").getText().toString().contains("Off"), "cancel restore keeps preferences");
        key(KeyEvent.KEYCODE_BACK); returned(home);
        runOnMainSync(() -> home.onTrimMemory(android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL));
        runOnMainSync(() -> {
            for (View view : views(home.getWindow().getDecorView())) if (view.getClass().getSimpleName().equals("Tile")) {
                check(view.getScaleX() == 1 && view.getScaleY() == 1, "memory pressure disables focus scaling");
            }
        });
    }

    private void fixtures() throws Exception {
        getTargetContext().deleteFile("jag.properties"); startHome();
        String[] ids = {"com.netflix.ninja", "com.onemainstream.sonyliv.android", "in.startv.hotstar", "com.amazon.firetv.youtube", "com.fancode.tv"};
        for (String id : ids) {
            focus("favorite:" + id); key(KeyEvent.KEYCODE_DPAD_CENTER);
            check(screenContains("JAG Test Fixture"), "PackageManager resolves actual installed fixture: " + id);
            key(KeyEvent.KEYCODE_BACK); returned(home);
            check(saved().getProperty("recent", "").startsWith(id), "only a successful JAG launch records history");
        }
        check(saved().getProperty("recent").split(",").length == 4, "real launches bounded to four unique entries");
        check(tiles() == 10, "four compact recent tiles share the six favorite artwork objects");
        View favorite = control("favorite:com.fancode.tv"), recent = control("recent:com.fancode.tv");
        java.lang.reflect.Field appField = favorite.getClass().getDeclaredField("app");
        appField.setAccessible(true);
        long artworkDeadline = SystemClock.uptimeMillis() + 15000;
        while (appField.get(favorite) == null && SystemClock.uptimeMillis() < artworkDeadline) SystemClock.sleep(100);
        check(appField.get(favorite) != null && appField.get(favorite) == appField.get(recent), "recent/favorite share one actual artwork object");
        java.lang.reflect.Field cardField = favorite.getClass().getDeclaredField("card");
        cardField.setAccessible(true);
        android.graphics.RectF bounds = (android.graphics.RectF) cardField.get(favorite);
        check(Math.abs(bounds.width() / bounds.height() - 16f / 9) < 0.01f, "real artwork area remains 16:9");
        focus("favorite:com.netflix.ninja"); key(KeyEvent.KEYCODE_DPAD_DOWN);
        check(focused(home).startsWith("FanCode"), "primary enters newest recent");
        key(KeyEvent.KEYCODE_DPAD_DOWN); check(focused(home).equals("Wallpaper"), "recent enters dock");
        key(KeyEvent.KEYCODE_DPAD_UP); check(focused(home).startsWith("FanCode"), "dock returns to recent");
        screenshot("v2-recent-fixtures");
        Activity settings = preferences();
        select(row(settings, "Recently Opened")); key(KeyEvent.KEYCODE_BACK); returned(home);
        check(tiles() == 6, "disable recent row without deleting history");
        String retained = saved().getProperty("recent");
        focus("favorite:com.netflix.ninja"); key(KeyEvent.KEYCODE_DPAD_CENTER);
        check(screenContains("JAG Test Fixture"), "launch while history disabled");
        key(KeyEvent.KEYCODE_BACK); returned(home);
        check(saved().getProperty("recent").equals(retained), "disabled feature does not track launches");
        settings = preferences(); select(row(settings, "Recently Opened")); key(KeyEvent.KEYCODE_BACK); returned(home);
        check(tiles() == 10, "re-enable recents restores existing history");
        settings = preferences(); select(row(settings, "Restore visual defaults"));
        check(screenContains("Restore Cinematic defaults"), "visual reset confirmation visible");
        key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_CENTER); returned(settings);
        check(saved().getProperty("recent").equals(retained), "visual defaults preserve actual launch history");
        select(row(settings, "Clear recent history")); check(screenContains("Clear Recently Opened"), "explicit history clear confirmation");
        key(KeyEvent.KEYCODE_DPAD_RIGHT); key(KeyEvent.KEYCODE_DPAD_CENTER); returned(settings);
        key(KeyEvent.KEYCODE_BACK); returned(home);
        check(tiles() == 6 && saved().getProperty("recent", "").isEmpty(), "clear hides row and safely restores focus");
        check(!focused(home).isEmpty(), "focus survives removing row");
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            // This suite is explicitly destructive to JAG's own test settings; never run on a household TV.
            String hardware = android.os.Build.HARDWARE;
            check(hardware.contains("ranchu") || hardware.contains("goldfish"), "disposable emulator required");
            check(android.os.Build.VERSION.SDK_INT == 28, "Android 9 compatibility run");
            AccessibilityServiceInfo accessibility = getUiAutomation().getServiceInfo();
            accessibility.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                | AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
            getUiAutomation().setServiceInfo(accessibility);
            if ("seed-v1".equals(phase)) writeLegacy();
            else if ("migration".equals(phase)) migration();
            else if ("fixtures".equals(phase)) fixtures();
            else missing();

            // The Home action remains the OS launcher because JAG has no HOME intent filter.
            Intent homeQuery = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (android.content.pm.ResolveInfo info : getTargetContext().getPackageManager().queryIntentActivities(homeQuery, 0)) {
                check(!info.activityInfo.packageName.equals("com.jag.home.dev"), "JAG cannot claim HOME");
            }
            check(getTargetContext().getPackageManager().getPackageInfo("com.jag.home.dev", android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions == null,
                "installed APK requests no permissions");

            result.putString("stream", "JAG_DEVICE_TESTS_PASSED [" + phase + "]: " + assertions + " assertions\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            try {
                Bitmap screenshot = getUiAutomation().takeScreenshot();
                if (screenshot != null) {
                    File output = new File(getTargetContext().getExternalFilesDir(null), "test-failure.png");
                    try (FileOutputStream out = new FileOutputStream(output)) {
                        screenshot.compress(Bitmap.CompressFormat.PNG, 100, out);
                    }
                    screenshot.recycle();
                }
            } catch (Exception ignored) {
                // The original assertion remains the runner's result even if capture is unavailable.
            }
            result.putString("stream", "JAG_DEVICE_TESTS_FAILED: " + failure + "\n" + android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
