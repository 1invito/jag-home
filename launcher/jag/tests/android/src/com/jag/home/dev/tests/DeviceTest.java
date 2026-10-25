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

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
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
        sendKeyDownUpSync(code);
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

    private Activity preferences() {
        ActivityMonitor monitor = addMonitor("com.jag.home.dev.Preferences", null, false);
        key(KeyEvent.KEYCODE_DPAD_DOWN);
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        key(KeyEvent.KEYCODE_DPAD_RIGHT);
        check(focused(home).equals("Settings"), "settings reached from favorites");
        key(KeyEvent.KEYCODE_DPAD_CENTER);
        Activity settings = waitForMonitorWithTimeout(monitor, 10000);
        removeMonitor(monitor);
        check(settings != null, "settings activity opened");
        waitForIdleSync();
        return settings;
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
            getTargetContext().deleteFile("jag.properties");
            home = startActivitySync(new Intent().setComponent(new ComponentName("com.jag.home.dev", "com.jag.home.dev.Home"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            waitForIdleSync();
            long deadline = SystemClock.uptimeMillis() + 15000;
            while (!focused(home).contains("Not installed") && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(100);
            check(focused(home).startsWith("Netflix"), "first favorite focused: " + focused(home));
            check(focused(home).contains("Not installed"), "actual PackageManager missing-app state");
            runOnMainSync(() -> {
                int count = 0;
                for (View v : views(home.getWindow().getDecorView())) if (v.getClass().getSimpleName().equals("Tile")) count++;
                check(count == 6, "exactly six visible favorite controls");
            });
            key(KeyEvent.KEYCODE_DPAD_LEFT);
            check(focused(home).startsWith("Netflix"), "left edge holds focus");
            for (int i = 0; i < 5; i++) key(KeyEvent.KEYCODE_DPAD_RIGHT);
            check(focused(home).startsWith("ExpressVPN"), "sixth favorite reached");
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            check(focused(home).startsWith("ExpressVPN"), "right edge holds focus");
            key(KeyEvent.KEYCODE_DPAD_DOWN);
            check(focused(home).equals("Settings"), "down to settings");
            key(KeyEvent.KEYCODE_DPAD_UP);
            check(focused(home).startsWith("FanCode"), "up to app row");
            for (int i = 0; i < 4; i++) key(KeyEvent.KEYCODE_DPAD_LEFT);
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            check(screenContains("Netflix is unavailable"), "missing app displays honest error");
            key(KeyEvent.KEYCODE_BACK);
            returned(home);
            check(focused(home).startsWith("Netflix"), "missing-app dialog dismisses back to tile");

            key(KeyEvent.KEYCODE_DPAD_DOWN);
            check(focused(home).equals("Free Memory"), "memory action is reachable: " + focused(home));
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            check(screenContains("Currently playing apps and your VPN will be protected"), "memory confirmation explains exclusions");
            key(KeyEvent.KEYCODE_BACK);
            returned(home);
            check(focused(home).equals("Free Memory"), "cancel memory returns focus");
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            check(screenContains("Currently playing apps and your VPN will be protected"), "confirmation is visible before selecting");
            key(KeyEvent.KEYCODE_DPAD_RIGHT);
            key(KeyEvent.KEYCODE_DPAD_CENTER);
            check(screenContains("No apps were stopped"), "unsupported cleanup reports actual result");
            check(screenContains("Available memory now"), "memory snapshot uses available API");
            key(KeyEvent.KEYCODE_BACK);
            returned(home);
            key(KeyEvent.KEYCODE_DPAD_UP);

            wallpaperDecode();

            Activity settings = preferences();
            TextView animation = row(settings, "Focus animation");
            select(animation);
            check(row(settings, "Focus animation").getText().toString().contains("Off"), "animation setting changed through D-pad");
            key(KeyEvent.KEYCODE_BACK);
            returned(home);
            check(!home.isFinishing(), "Back returns to JAG");
            settings = preferences();
            check(row(settings, "Focus animation").getText().toString().contains("Off"), "settings persist across activity recreation");
            select(row(settings, "Restore default appearance"));
            key(KeyEvent.KEYCODE_BACK);
            check(row(settings, "Focus animation").getText().toString().contains("Off"), "cancel restore preserves settings");
            key(KeyEvent.KEYCODE_BACK);

            // The Home action remains the OS launcher because JAG has no HOME intent filter.
            Intent homeQuery = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (android.content.pm.ResolveInfo info : getTargetContext().getPackageManager().queryIntentActivities(homeQuery, 0)) {
                check(!info.activityInfo.packageName.equals("com.jag.home.dev"), "JAG cannot claim HOME");
            }
            check(getTargetContext().getPackageManager().getPackageInfo("com.jag.home.dev", android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions == null,
                "installed APK requests no permissions");
            check(new File(getTargetContext().getFilesDir(), "jag.properties").isFile(), "settings written in JAG private storage");
            result.putString("stream", "JAG_DEVICE_TESTS_PASSED: " + assertions + " assertions\n");
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
