package com.jag.home.dev;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/** One 16:9 card, bounded artwork and cached drawing geometry. Focus never changes layout. */
final class Tile extends View {
    final String packageId;
    private final Configuration config;
    private final Theme theme;
    private final boolean compact;
    private boolean lowMemory;
    private final Paint surface = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG), image = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final TextPaint title = new TextPaint(Paint.ANTI_ALIAS_FLAG), monogram = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint detail = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF card = new RectF(), artBounds = new RectF();
    private final Path clip = new Path();
    private final String initials;
    private CharSequence label;
    private AppCatalog.App app;
    private String status = "";
    private float radius, unit, labelBaseline;

    Tile(Context c, String id, Configuration config, boolean compact, boolean lowMemory) {
        super(c);
        packageId = id; this.config = config; this.compact = compact; this.lowMemory = lowMemory;
        theme = Theme.named(config.get("theme"));
        android.util.DisplayMetrics display = c.getResources().getDisplayMetrics();
        unit = Math.min(display.density, Math.min(display.widthPixels / 960f, display.heightPixels / 540f));
        initials = initials(id);
        setFocusable(true); setFocusableInTouchMode(true); setClickable(true); setLongClickable(true);
        Ui.canvasFocus(this);
        setContentDescription(Favorites.name(id));
        rim.setStyle(Paint.Style.STROKE);
        title.setTextAlign(Paint.Align.CENTER); title.setColor(Ui.TEXT); title.setTypeface(Ui.MEDIUM);
        title.setTextSize(unit * (compact ? 11 : 13));
        monogram.setTextAlign(Paint.Align.CENTER); monogram.setColor(0xF0F4F7FF); monogram.setTypeface(Ui.LIGHT);
        monogram.setTextSize(unit * (compact ? 21 : 29));
        detail.setTextAlign(Paint.Align.CENTER); detail.setColor(0xBFFFFFFF); detail.setTextSize(unit * (compact ? 8 : 10));
    }

    private static String initials(String id) {
        for (int i = 0; i < Favorites.PACKAGES.length; i++) {
            if (Favorites.PACKAGES[i].equals(id)) return new String[] {"N", "SL", "JH", "YT", "FC", "EV"}[i];
        }
        return "?";
    }

    void bind(AppCatalog.App value) {
        app = value; status = value.status; artGeometry();
        setContentDescription(Favorites.name(packageId) + (status.isEmpty() ? "" : ", " + status));
        invalidate();
    }

    void clearArt() { app = null; invalidate(); }

    void reduceEffects() {
        lowMemory = true; animate().cancel(); setScaleX(1); setScaleY(1); invalidate();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        float inset = unit * 2;
        card.set(inset, inset, w - inset, inset + (w - inset * 2) * 9 / 16f);
        radius = Math.min(card.height() * 0.18f, unit * (compact ? 12 : 15));
        clip.reset(); clip.addRoundRect(card, radius, radius, Path.Direction.CW);
        label = TextUtils.ellipsize(Favorites.name(packageId), title, w - 8 * unit, TextUtils.TruncateAt.END);
        labelBaseline = card.bottom + (h - card.bottom) * 0.68f;
        setPivotX(w / 2f); setPivotY(card.centerY());
        int index = java.util.Arrays.asList(Favorites.PACKAGES).indexOf(packageId);
        int[] tones = {0xFF42202C, 0xFF302B51, 0xFF183C54, 0xFF442329, 0xFF163F39, 0xFF3C2A32};
        int tint = theme.minimal ? theme.surface : tones[Math.max(0, index)];
        surface.setShader(theme.minimal ? null : new LinearGradient(0, 0, w, card.bottom,
            tint, theme.surface, Shader.TileMode.CLAMP));
        surface.setColor(theme.surface);
        sheen.setShader(new LinearGradient(0, card.top, 0, card.bottom,
            0x16FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        if (app != null) artGeometry();
    }

    private void artGeometry() {
        if (app == null || app.art == null) return;
        boolean banner = app.art.getWidth() > app.art.getHeight();
        float maxW = banner ? card.width() : card.height() * 0.66f;
        float maxH = banner ? card.height() : maxW;
        float scale = Math.min(maxW / app.art.getWidth(), maxH / app.art.getHeight());
        float w = app.art.getWidth() * scale, h = app.art.getHeight() * scale;
        artBounds.set(card.centerX() - w / 2, card.centerY() - h / 2, card.centerX() + w / 2, card.centerY() + h / 2);
    }

    @Override protected void onFocusChanged(boolean has, int direction, Rect previous) {
        super.onFocusChanged(has, direction, previous);
        animate().cancel();
        boolean animated = config.animateFocus(lowMemory, isHardwareAccelerated());
        float scale = has && animated ? 1.045f : 1;
        if (animated) animate().scaleX(scale).scaleY(scale).setDuration(120).start();
        else { setScaleX(scale); setScaleY(scale); }
        invalidate();
    }

    @Override protected void onDetachedFromWindow() { animate().cancel(); super.onDetachedFromWindow(); }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawRoundRect(card, radius, radius, surface);
        canvas.save(); canvas.clipPath(clip);
        if (app != null && app.art != null) {
            // Geometry changes only on bind/size, never on a directional key.
            canvas.drawBitmap(app.art, null, artBounds, image);
        } else {
            float baseline = card.centerY() - (monogram.ascent() + monogram.descent()) / 2 - (status.isEmpty() ? 0 : unit * 5);
            canvas.drawText(initials, card.centerX(), baseline, monogram);
            if (!status.isEmpty()) canvas.drawText(status, card.centerX(), card.bottom - unit * 9, detail);
        }
        if (!theme.minimal) canvas.drawRoundRect(card, radius, radius, sheen);
        canvas.restore();
        rim.setColor(isFocused() ? config.accent() : 0x25FFFFFF);
        rim.setStrokeWidth(unit * (isFocused() ? 1.8f : 0.6f));
        canvas.drawRoundRect(card, radius, radius, rim);
        if (!theme.minimal && "Shown".equals(config.get("labels")) && label != null) {
            title.setAlpha(isFocused() ? 255 : 185);
            canvas.drawText(label, 0, label.length(), card.centerX(), labelBaseline, title);
        }
    }
}
