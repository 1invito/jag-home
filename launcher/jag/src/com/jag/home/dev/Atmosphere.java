package com.jag.home.dev;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/** Static atmospheric light, built once per layout. No bitmap, blur or animation. */
final class Atmosphere extends View {
    private final Theme theme;
    private final Paint base = new Paint(), light = new Paint(), horizon = new Paint();

    Atmosphere(Context c, Theme theme) {
        super(c);
        this.theme = theme;
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (theme.minimal) { base.setColor(theme.bottom); return; }
        base.setShader(new LinearGradient(0, 0, w, h, theme.top, theme.bottom, Shader.TileMode.CLAMP));
        light.setShader(new RadialGradient(w * 0.79f, h * 0.13f, w * 0.68f,
                new int[] {theme.light, theme.top & 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        horizon.setShader(new RadialGradient(w * 0.12f, h * 0.62f, w * 0.6f,
                new int[] {0x273F6B8D, 0x003F6B8D}, null, Shader.TileMode.CLAMP));
    }

    @Override protected void onDraw(Canvas c) {
        c.drawRect(0, 0, getWidth(), getHeight(), base);
        if (!theme.minimal) {
            c.drawRect(0, 0, getWidth(), getHeight(), light);
            c.drawRect(0, 0, getWidth(), getHeight(), horizon);
        }
    }
}
