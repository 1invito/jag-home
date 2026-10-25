package com.jag.home.integration;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Android rebinds the enabled Guard; this one-shot marker handles a missed startup HOME event. */
public final class Boot extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) HomeGuard.boot(c);
    }
}
