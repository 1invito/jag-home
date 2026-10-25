package com.jag.home.dev;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Original small line icons and visible labels, drawn without drawable/image allocations. */
final class DockButton extends View {
    private final Paint icon = new Paint(Paint.ANTI_ALIAS_FLAG), text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint focus = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final Path path = new Path();
    private final int kind;
    private final String label;
    private final float unit;

    DockButton(Context c, int kind, String label, int accent) {
        super(c);
        this.kind = kind; this.label = label;
        android.util.DisplayMetrics display = c.getResources().getDisplayMetrics();
        unit = Math.min(display.density, Math.min(display.widthPixels / 960f, display.heightPixels / 540f));
        setFocusable(true); setFocusableInTouchMode(true); setClickable(true);
        Ui.canvasFocus(this);
        setContentDescription(label);
        icon.setColor(Ui.TEXT); icon.setStyle(Paint.Style.STROKE); icon.setStrokeWidth(1.35f);
        icon.setStrokeCap(Paint.Cap.ROUND); icon.setStrokeJoin(Paint.Join.ROUND);
        text.setColor(Ui.TEXT); text.setTypeface(Ui.MEDIUM); text.setTextSize(13 * unit);
        focus.setStyle(Paint.Style.STROKE); focus.setStrokeWidth(1.2f * unit); focus.setColor(accent);
        fill.setColor(0x35B6D2F9);
        setOnFocusChangeListener((v, has) -> invalidate());
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        bounds.set(2 * unit, 2 * unit, w - 2 * unit, h - 2 * unit);
        path.reset();
        if (kind == 0) {
            path.addRoundRect(new RectF(0, 1, 19, 16), 2, 2, Path.Direction.CW);
            path.moveTo(1, 13); path.lineTo(7, 7); path.lineTo(11, 11); path.lineTo(14, 8); path.lineTo(18, 12);
            path.addCircle(14, 5, 1, Path.Direction.CW);
        } else if (kind == 1) {
            for (int i = 0; i < 3; i++) {
                float y = 3 + i * 6;
                path.moveTo(0, y); path.lineTo(19, y);
                path.addCircle(i == 1 ? 6 : 13, y, 2.2f, Path.Direction.CW);
            }
        } else if (kind == 2) {
            path.addRoundRect(new RectF(1, 2, 18, 15), 2, 2, Path.Direction.CW);
            for (int i = 0; i < 3; i++) {
                path.moveTo(5 + i * 5, 5); path.lineTo(5 + i * 5, 12);
                path.moveTo(5 + i * 5, 0); path.lineTo(5 + i * 5, 2);
                path.moveTo(5 + i * 5, 15); path.lineTo(5 + i * 5, 17);
            }
        } else {
            path.addCircle(9.5f, 8.5f, 6.7f, Path.Direction.CW);
            path.addCircle(9.5f, 8.5f, 2.3f, Path.Direction.CW);
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4;
                path.moveTo(9.5f + (float) Math.cos(angle) * 7, 8.5f + (float) Math.sin(angle) * 7);
                path.lineTo(9.5f + (float) Math.cos(angle) * 9, 8.5f + (float) Math.sin(angle) * 9);
            }
        }
    }

    @Override protected void onDraw(Canvas c) {
        if (isFocused()) {
            c.drawRoundRect(bounds, 11 * unit, 11 * unit, fill);
            c.drawRoundRect(bounds, 11 * unit, 11 * unit, focus);
        }
        float combined = text.measureText(label) + 31 * unit;
        float x = (getWidth() - combined) / 2;
        c.save(); c.translate(x, (getHeight() - 17 * unit) / 2); c.scale(unit, unit);
        c.drawPath(path, icon); c.restore();
        c.drawText(label, x + 31 * unit, getHeight() / 2f - (text.ascent() + text.descent()) / 2, text);
    }
}
