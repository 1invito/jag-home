package com.jag.home.tests;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.XmlResourceParser;
import android.os.Bundle;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Properties;

/** Stable package registration/private-state evidence on a disposable API 28 emulator. */
public final class StableTest extends Instrumentation {
    private int count;
    private void check(boolean value, String name) { count++; if (!value) throw new AssertionError(name); }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            check(android.os.Build.HARDWARE.contains("ranchu") || android.os.Build.HARDWARE.contains("goldfish"), "emulator only");
            check(android.os.Build.VERSION.SDK_INT == 28, "API 28 only");
            PackageManager pm = getTargetContext().getPackageManager();
            PackageInfo stable = pm.getPackageInfo("com.jag.home", PackageManager.GET_PERMISSIONS);
            check(Arrays.asList(stable.requestedPermissions).equals(Arrays.asList("android.permission.RECEIVE_BOOT_COMPLETED")), "one normal permission only");
            try {
                PackageInfo dev = pm.getPackageInfo("com.jag.home.dev", 0);
                check(dev.applicationInfo.uid != stable.applicationInfo.uid, "separate application UID");
                check(!dev.applicationInfo.dataDir.equals(stable.applicationInfo.dataDir), "separate private storage");
                result.putString("coexistence", "passed");
            } catch (PackageManager.NameNotFoundException absent) { result.putString("coexistence", "unrun: preview absent"); }
            ServiceInfo info = pm.getServiceInfo(new ComponentName("com.jag.home", "com.jag.home.integration.HomeGuard"), PackageManager.GET_META_DATA);
            check("android.permission.BIND_ACCESSIBILITY_SERVICE".equals(info.permission), "system-only binding");
            try (XmlResourceParser xml = info.loadXmlMetaData(pm, "android.accessibilityservice")) {
                while (xml.next() != org.xmlpull.v1.XmlPullParser.START_TAG) { /* Find root. */ }
                check(!xml.getAttributeBooleanValue("http://schemas.android.com/apk/res/android", "canRetrieveWindowContent", true), "no screen retrieval");
            }
            android.content.pm.ActivityInfo receiver = pm.getReceiverInfo(new ComponentName("com.jag.home", "com.jag.home.integration.HomeControl"), 0);
            check("android.permission.DUMP".equals(receiver.permission), "owner shell control is protected");
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            for (android.content.pm.ResolveInfo item : pm.queryIntentActivities(home, 0)) {
                check(!"com.jag.home".equals(item.activityInfo.packageName), "install does not claim the HOME resolver");
            }
            check(!getTargetContext().getSharedPreferences("home-integration", 0).getBoolean("armed", false), "fresh stable installation is disarmed");
            Activity activity = startActivitySync(new Intent().setComponent(new ComponentName("com.jag.home", "com.jag.home.dev.Home"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            waitForIdleSync();
            check(activity.getWindow().getDecorView().findFocus() != null, "native v2 scene has focus");
            check(!getTargetContext().getSharedPreferences("home-integration", 0).getBoolean("armed", false), "opening the stable UI does not arm HOME");
            Class<?> config = Class.forName("com.jag.home.dev.Configuration");
            Constructor<?> constructor = config.getDeclaredConstructor(File.class); constructor.setAccessible(true);
            Object settings = constructor.newInstance(new File(getTargetContext().getFilesDir(), "import-test.properties"));
            Method set = config.getDeclaredMethod("set", String.class, String.class); set.setAccessible(true);
            Method get = config.getDeclaredMethod("get", String.class); get.setAccessible(true);
            set.invoke(settings, "recent", "com.netflix.ninja");
            Properties appearance = new Properties(); appearance.setProperty("theme", "Graphite");
            appearance.setProperty("recent", "com.fancode.tv"); appearance.setProperty("account.token", "unapproved");
            Method transfer = config.getDeclaredMethod("importAppearance", Properties.class, String.class); transfer.setAccessible(true);
            transfer.invoke(settings, appearance, null);
            check("Graphite".equals(get.invoke(settings, "theme")), "approved appearance imported");
            check("com.netflix.ninja".equals(get.invoke(settings, "recent")), "history not copied/overwritten");
            check("".equals(get.invoke(settings, "account.token")), "unrelated private keys ignored");
            result.putString("stream", "JAG_STABLE_TESTS_PASSED: " + count + " assertions\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "JAG_STABLE_TESTS_FAILED: " + failure + "\n" + android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
