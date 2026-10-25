package com.jag.home.integration;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import org.json.JSONObject;

/** Owner ADB control; manifest requires sender DUMP permission. No configuration is armed on install. */
public final class HomeControl extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        JSONObject result;
        try {
            String op = intent.getStringExtra("operation");
            if ("disarm".equals(op)) GuardState.disarm(c);
            else if ("arm".equals(op)) {
                String nonce = intent.getStringExtra("nonce"), activity = intent.getStringExtra("amazon_class");
                long expiry = intent.getLongExtra("expiry", 0);
                if (!GuardState.compatible() || !HomeGuard.bound() || GuardPolicy.oldGuardEnabled(GuardState.services(c))
                        || nonce == null || !nonce.matches("[a-f0-9]{32}")
                        || activity == null || !activity.equals("com.amazon.tv.launcher.ui.HomeActivity_vNext")
                        || expiry <= System.currentTimeMillis() || expiry > System.currentTimeMillis() + 3600000L) {
                    throw new IllegalArgumentException("Incompatible or unsafe handover");
                }
                GuardState.arm(c, nonce, activity, expiry);
                HomeGuard.leaseChanged();
            } else if ("commit".equals(op)) {
                if (!GuardState.eligible(c) || !HomeGuard.bound()
                        || !GuardState.data(c).getString("nonce", "").equals(intent.getStringExtra("nonce"))
                        || !GuardState.status(c).getBoolean("ready")) throw new IllegalArgumentException("Trial is not verified");
                if (!GuardState.data(c).edit().putLong("expiry", 0).putBoolean("committed", true).commit()) {
                    throw new IllegalStateException("Commit could not be saved");
                }
                HomeGuard.leaseChanged();
            } else if (!"status".equals(op)) throw new IllegalArgumentException("Unknown operation");
            result = GuardState.status(c);
        } catch (Exception failure) {
            result = new JSONObject();
            try { result.put("error", "HOME operation rejected; original settings must be restored by the owner tool"); }
            catch (org.json.JSONException impossible) { throw new IllegalStateException(impossible); }
        }
        setResultData("JAG_HOME:" + Base64.encodeToString(result.toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));
    }
}
