package com.jag.home.dev;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** D-pad rows and native dialogs; no touch-only controls or editable text. */
public final class Preferences extends Activity {
    private static final int PICK_IMAGE = 41;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<TextView> buttons = new ArrayList<>();
    private final List<Runnable> refresh = new ArrayList<>();
    private Configuration config;
    private LinearLayout list;
    private boolean destroyed;

    private interface Change { void apply() throws IOException; }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        config = new Configuration(new File(getFilesDir(), "jag.properties"));
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Theme.named(config.get("theme")).bottom);
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(64), dp(36), dp(64), dp(36));
        scroll.addView(list);
        TextView title = Ui.text(this, "Make it yours", 30, Ui.TEXT);
        title.setTypeface(Ui.LIGHT);
        title.setPadding(dp(16), 0, 0, dp(10));
        list.addView(title);
        TextView help = Ui.text(this, "Left / right to adjust  ·  Select for options  ·  Back to return", 13, Ui.MUTED);
        help.setPadding(dp(16), 0, 0, dp(22));
        list.addView(help);
        TextView done = action("Done · return to JAG Home", this::finish);
        action("Wallpaper", this::wallpapers);
        for (String[] c : Configuration.CHOICES) {
            TextView row = action("", () -> change(() -> config.cycle(c[0], 1)));
            Runnable show = () -> row.setText(c[1] + "     ‹ " + config.get(c[0]) + " ›");
            refresh.add(show);
            show.run();
            row.setOnKeyListener((v, code, event) -> {
                if (code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN) change(() -> config.cycle(c[0], code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1));
                return true;
            });
        }
        action("Application ordering", this::ordering);
        action("Clear recent history", () -> Ui.dialog(this).setTitle("Clear Recently Opened?")
            .setMessage("Removes only the four local launch-history entries. Favorite ordering and app data are preserved.")
            .setPositiveButton("Clear", (d, which) -> change(() -> config.set("recent", "")))
            .setNegativeButton("Cancel", null).show());
        action("Memory Status", () -> Memory.information(this));
        action("Android system settings", () -> Recovery.systemSettings(this));
        action("Application management", () -> Recovery.applications(this));
        action("Return to existing dumbtv", () -> Recovery.dumbtv(this));
        action("About JAG Home", this::about);
        action("Restore visual defaults", () -> Ui.dialog(this)
            .setTitle("Restore Cinematic defaults?")
            .setMessage("Resets JAG's appearance. Favorite order, recent history, installed apps, accounts and dumbtv are preserved.")
            .setPositiveButton("Restore", (d, which) -> change(config::resetAppearance))
            .setNegativeButton("Cancel", null).show());
        setContentView(scroll);
        done.requestFocus();
        if (state == null) {
            if (getIntent().getBooleanExtra("wallpaper", false)) list.post(this::wallpapers);
            else if (getIntent().getBooleanExtra("order", false)) list.post(this::ordering);
        }
    }

    private int dp(int n) { return Ui.dp(this, n); }

    private TextView action(String name, Runnable task) {
        TextView row = Ui.button(this, name, config.accent(), task);
        row.setId(android.view.View.generateViewId());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = dp(8);
        list.addView(row, params);
        buttons.add(row);
        return row;
    }

    private boolean change(Change task) {
        try {
            task.apply();
            for (Runnable show : refresh) show.run();
            for (TextView row : buttons) Ui.style(row, config.accent());
            return true;
        } catch (IOException e) {
            Ui.message(this, "Could not save settings. Previous settings were kept.");
            return false;
        }
    }

    private void ordering() {
        List<String> ids = Favorites.order(config.get("order"));
        String[] names = new String[ids.size()];
        for (int i = 0; i < ids.size(); i++) names[i] = (i + 1) + "   " + Favorites.name(ids.get(i));
        Ui.dialog(this).setTitle("Choose an app to move")
            .setItems(names, (d, from) -> Ui.dialog(this)
                .setTitle("Move " + Favorites.name(ids.get(from)) + " to position…")
                .setSingleChoiceItems(names, from, (positionDialog, to) -> {
                    change(() -> config.set("order", Favorites.move(config.get("order"), from, to)));
                    positionDialog.dismiss();
                }).setNegativeButton("Cancel", null).show())
            .setNegativeButton("Cancel", null).show();
    }

    private void wallpapers() {
        io.execute(() -> {
            List<File> files = Wallpapers.list(getApplicationContext());
            ui.post(() -> {
                if (destroyed || isFinishing()) return;
                List<String> names = new ArrayList<>();
                names.add("Default · midnight navy");
                names.add("Choose from device…");
                for (File file : files) names.add(file.getName());
                Ui.dialog(this).setTitle("Wallpaper")
                    .setItems(names.toArray(new String[0]), (d, which) -> {
                        if (which == 0) change(() -> config.set("wallpaper", ""));
                        else if (which == 1) picker();
                        else choose(files.get(which - 2));
                    })
                    .setNeutralButton("Add a local image", (d, which) -> wallpaperHelp())
                    .setNegativeButton("Cancel", null).show();
            });
        });
    }

    private void choose(File file) {
        io.execute(() -> {
            Bitmap test = Wallpapers.decode(file);
            boolean valid = test != null;
            if (test != null) test.recycle();
            ui.post(() -> {
                if (destroyed || isFinishing()) return;
                if (valid) {
                    if (change(() -> config.set("wallpaper", file.getPath()))) {
                        Ui.message(this, "Wallpaper selected. Back returns to the preview.");
                    }
                } else Ui.message(this, "This image cannot be decoded. The previous wallpaper was kept.");
            });
        });
    }

    private void picker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*")
            .addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        try {
            startActivityForResult(intent, PICK_IMAGE);
        } catch (ActivityNotFoundException | SecurityException e) {
            wallpaperHelp();
        }
    }

    private void wallpaperHelp() {
        Ui.dialog(this).setTitle("Local wallpapers")
            .setMessage("Select a JPG, PNG or WebP with the device's file picker. Some Fire TVs do not include one. "
                + "You can also add images to JAG's own wallpapers folder using the development guide, "
                + "then choose them here with your remote. Images stay on this device; there are no downloads or rotations.")
            .setPositiveButton("OK", null).show();
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK_IMAGE || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        Ui.message(this, "Importing local image…");
        io.execute(() -> {
            File imported = null;
            try {
                imported = Wallpapers.importPicture(getApplicationContext(), uri);
            } catch (IOException | RuntimeException e) {
                // Show a failure rather than replacing the current selection with invalid bytes.
            }
            File selected = imported;
            ui.post(() -> {
                if (destroyed || isFinishing()) return;
                if (selected == null) Ui.message(this, "Image could not be imported. Use a local JPG, PNG or WebP under 32 MB.");
                else {
                    if (change(() -> config.set("wallpaper", selected.getPath()))) {
                        Ui.message(this, "Wallpaper selected. Back returns to the preview.");
                    }
                }
            });
        });
    }

    private void about() {
        Ui.dialog(this).setTitle("JAG Home · cinematic development preview 0.2")
            .setMessage("An original, quiet TV home inspired by spatial clarity and simple navigation. "
                + "Native Java and Android Views. No ads, tracking, network permission or background services. "
                + "Built on dumbtv by the dumbtv contributors, MIT licensed. "
                + "This separate development app does not replace dumbtv or change the Home button. "
                + "Performance and compatibility measurements on your Fire Stick are still required.")
            .setPositiveButton("Close", null).show();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        io.shutdownNow();
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
