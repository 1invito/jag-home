package com.jag.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

/** Disposable emulator fixture, never a real streaming client or part of the JAG APK. */
public final class Screen extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView label = new TextView(this);
        label.setText("JAG Test Fixture\n" + getPackageName() + "\nNot a real streaming application");
        label.setTextSize(24); label.setGravity(Gravity.CENTER); label.setTextColor(0xFFFFFFFF);
        label.setBackgroundColor(0xFF12314D); setContentView(label);
    }
}
