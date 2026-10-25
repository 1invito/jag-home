package com.jag.home.dev;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;
import android.widget.ListView;
import android.widget.Toast;

/** Native View helpers, adapted from dumbtv's MIT-licensed Ui. */
final class Ui {
    static final int TEXT = 0xFFF1F5FC, MUTED = 0xFFABB8CD, SURFACE = 0xAA132238;
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    private Ui() {}

    static int dp(Context c, float n) {
        return Math.round(n * c.getResources().getDisplayMetrics().density);
    }

    static TextView text(Context c, String text, float size, int color) {
        TextView v = new TextView(c);
        v.setText(text);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(MEDIUM);
        v.setIncludeFontPadding(false);
        return v;
    }

    static GradientDrawable round(int color, int stroke, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        d.setStroke(stroke == 0 ? 0 : 2, stroke);
        return d;
    }

    static void style(View v, int accent) {
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, round(0xFF253B58, accent, dp(v.getContext(), 12)));
        bg.addState(new int[0], round(SURFACE, 0, dp(v.getContext(), 12)));
        v.setBackground(bg);
    }

    /** Mark focus as handled without API 26 calls, avoiding Android's square fallback highlight. */
    static void canvasFocus(View v) {
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, new ColorDrawable(0));
        bg.addState(new int[0], new ColorDrawable(0));
        v.setBackground(bg);
    }

    static TextView button(Context c, String name, int accent, Runnable action) {
        TextView v = text(c, name, 16, TEXT);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(c, 20), dp(c, 14), dp(c, 20), dp(c, 14));
        v.setFocusable(true);
        v.setFocusableInTouchMode(true);
        v.setClickable(true);
        v.setTextColor(new ColorStateList(new int[][] {{android.R.attr.state_focused}, {}},
                new int[] {0xFFFFFFFF, TEXT}));
        style(v, accent);
        v.setOnClickListener(view -> action.run());
        return v;
    }

    static void message(Context c, String text) {
        Toast.makeText(c, text, Toast.LENGTH_LONG).show();
    }

    static AlertDialog.Builder dialog(Context c) {
        return new AlertDialog.Builder(c) {
            @Override public AlertDialog show() {
                AlertDialog shown = super.show();
                focusDialog(shown);
                return shown;
            }
        };
    }

    static void focusDialog(AlertDialog dialog) {
        for (int button : new int[] {AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
            View view = dialog.getButton(button);
            if (view != null) view.setFocusableInTouchMode(true);
        }
        ListView list = dialog.getListView();
        if (list != null) {
            list.setFocusableInTouchMode(true);
            list.requestFocus();
            list.setSelection(Math.max(0, list.getCheckedItemPosition()));
        } else {
            View cancel = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            View accept = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (cancel != null && cancel.getVisibility() == View.VISIBLE) cancel.requestFocus();
            else if (accept != null) accept.requestFocus();
        }
    }

    static boolean open(Activity a, Intent intent) {
        if (intent == null) return false;
        try {
            a.startActivity(intent);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
