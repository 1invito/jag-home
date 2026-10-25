package com.jag.home.dev;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/** One canvas pass, preallocated drawing geometry, no layout changes on focus. */
final class Tile extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint detail = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF card = new RectF(), artBounds = new RectF();
    private final Path clip = new Path();
    private final Configuration config;
    private final String id;
    private AppCatalog.App app;
    private CharSequence label, status;
    private float radius, cardHeight;

    Tile(Context c, String id, Configuration config) {
        super(c);
        this.id = id;
        this.config = config;
        setFocusable(true);
        setFocusableInTouchMode(true);
        setClickable(true);
        setLongClickable(true);
        setContentDescription(Favorites.name(id));
        fill.setColor(Ui.SURFACE);
        ring.setColor(config.accent());
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Ui.dp(c, 1.5f));
        title.setTextAlign(Paint.Align.CENTER);
        title.setColor(Ui.TEXT);
        title.setTypeface(Ui.MEDIUM);
        title.setTextSize(Ui.dp(c, 13));
        detail.setTextAlign(Paint.Align.CENTER);
        detail.setColor(Ui.MUTED);
        detail.setTextSize(Ui.dp(c, 10));
        status = "Loading…";
    }

    void bind(AppCatalog.App value) {
        app = value;
        status = value.status;
        setContentDescription(Favorites.name(id) + (status.length() == 0 ? "" : ", " + status));
        invalidate();
    }

    void clearArt() {
        app = null;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        cardHeight = h - Ui.dp(getContext(), 32);
        radius = Ui.dp(getContext(), 11);
        card.set(2, 2, w - 2, cardHeight - 2);
        clip.reset();
        clip.addRoundRect(card, radius, radius, Path.Direction.CW);
        label = TextUtils.ellipsize(Favorites.name(id), title, w - Ui.dp(getContext(), 8), TextUtils.TruncateAt.END);
        setPivotX(w / 2f);
        setPivotY(cardHeight / 2f);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        animate().cancel();
        float scale = focused ? 1.045f : 1;
        if ("On".equals(config.get("animation"))) animate().scaleX(scale).scaleY(scale).setDuration(120).start();
        else { setScaleX(scale); setScaleY(scale); }
        invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        animate().cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawRoundRect(card, radius, radius, fill);
        canvas.save();
        canvas.clipPath(clip);
        if (app != null && app.art != null) {
            boolean banner = app.art.getWidth() > app.art.getHeight();
            float w = banner ? card.width() : card.height() * 0.6f;
            float h = banner ? card.height() : w;
            artBounds.set(card.centerX() - w / 2, card.centerY() - h / 2,
                    card.centerX() + w / 2, card.centerY() + h / 2);
            canvas.drawBitmap(app.art, null, artBounds, image);
        } else if (label != null) {
            canvas.drawText(label, 0, label.length(), card.centerX(), card.centerY(), title);
        }
        if (status.length() > 0) canvas.drawText(status, 0, status.length(), card.centerX(), card.bottom - Ui.dp(getContext(), 12), detail);
        canvas.restore();
        if (isFocused()) canvas.drawRoundRect(card, radius, radius, ring);
        if ("Shown".equals(config.get("labels")) && label != null) {
            canvas.drawText(label, 0, label.length(), card.centerX(), cardHeight + Ui.dp(getContext(), 22), title);
        }
    }
}
