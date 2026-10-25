package com.jag.home.integration;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.provider.Settings;
import org.json.JSONException;
import org.json.JSONObject;

/** Same-process state, private to the stable package. A finite trial lease survives process death. */
public final class GuardState {
    private GuardState() {}
    static SharedPreferences data(Context c) { return c.getSharedPreferences("home-integration", Context.MODE_PRIVATE); }
    static int boot(Context c) { return Settings.Global.getInt(c.getContentResolver(), "boot_count", -1); }
    static String services(Context c) { return Settings.Secure.getString(c.getContentResolver(), "enabled_accessibility_services"); }
    static boolean compatible() { return GuardPolicy.compatible(Build.VERSION.SDK_INT, Build.MANUFACTURER); }
    static boolean armed(Context c) {
        return data(c).getBoolean("armed", false) && (data(c).getLong("expiry", 0) == 0 || remainingLease(c) > 0);
    }
    static long remainingLease(Context c) {
        if (boot(c) >= 0 && data(c).getInt("armBoot", -2) == boot(c)) {
            return data(c).getLong("expiryElapsed", 0) - SystemClock.elapsedRealtime();
        }
        return data(c).getLong("expiry", 0) - System.currentTimeMillis();
    }
    static boolean eligible(Context c) { return compatible() && armed(c) && !GuardPolicy.oldGuardEnabled(services(c)); }
    static void arm(Context c, String nonce, String activity, long expiry) {
        if (!data(c).edit().putString("nonce", nonce).putString("amazonClass", activity)
                .putLong("expiry", expiry).putBoolean("armed", true).putBoolean("committed", false)
                .putLong("expiryElapsed", SystemClock.elapsedRealtime() + Math.max(0, expiry - System.currentTimeMillis())).putInt("armBoot", boot(c))
                .putString("drawNonce", "").putBoolean("bootPending", false).commit()) throw new IllegalStateException("State could not be saved");
    }
    public static void disarm(Context c) {
        if (!data(c).edit().putBoolean("armed", false).putBoolean("committed", false)
                .putBoolean("bootPending", false).putString("drawNonce", "").commit()) throw new IllegalStateException("State could not be saved");
        HomeGuard.cancel(c);
    }
    public static void drawn(Context c) {
        if (armed(c)) data(c).edit().putString("drawNonce", data(c).getString("nonce", ""))
            .putInt("drawBoot", boot(c)).putLong("drawTime", System.currentTimeMillis()).apply();
        HomeGuard.shown();
    }
    static JSONObject status(Context c) throws JSONException {
        JSONObject out = new JSONObject();
        out.put("protocol", 1); out.put("package", c.getPackageName()); out.put("compatible", compatible());
        out.put("armed", armed(c)); out.put("bound", HomeGuard.bound());
        out.put("old_guard_enabled", GuardPolicy.oldGuardEnabled(services(c)));
        out.put("nonce", data(c).getString("nonce", "")); out.put("committed", data(c).getBoolean("committed", false));
        out.put("ready", armed(c) && !data(c).getString("nonce", "").isEmpty()
            && data(c).getString("nonce", "").equals(data(c).getString("drawNonce", ""))
            && data(c).getInt("drawBoot", -2) == boot(c));
        out.put("draw_time", data(c).getLong("drawTime", 0));
        return out;
    }
}
