package com.jag.home.dev;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Separate ordinary activity. No HOME takeover, background services or polling. */
public final class Home extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<View> focus = new ArrayList<>();
    private final List<Tile> tiles = new ArrayList<>();
    private List<AppCatalog.App> apps = new ArrayList<>();
    private Configuration config;
    private List<String> order;
    private FrameLayout root;
    private ImageView wallpaper;
    private TextView clock, date, hint;
    private Bitmap background;
    private String backgroundKey = "";
    private String applied = "";
    private boolean visible;
    private int generation, lastFocus;
    private SimpleDateFormat timeFormat, dateFormat;

    private final BroadcastReceiver changes = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (!visible) return;
            if (i.getAction() != null && i.getAction().startsWith("android.intent.action.PACKAGE_")) load();
            else { formats(); tick(); }
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) lastFocus = Math.max(0, Math.min(8, state.getInt("focus", 0)));
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("focus", lastFocus);
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() {
        super.onResume();
        visible = true;
        config = new Configuration(new File(getFilesDir(), "jag.properties"));
        StringBuilder signature = new StringBuilder(config.get("order"));
        for (String[] choice : Configuration.CHOICES) signature.append('|').append(config.get(choice[0]));
        if (root == null || !applied.equals(signature.toString())) {
            applied = signature.toString();
            build();
        }
        formats();
        tick();
        IntentFilter times = new IntentFilter(Intent.ACTION_TIME_TICK);
        times.addAction(Intent.ACTION_TIME_CHANGED);
        times.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        times.addAction(Intent.ACTION_LOCALE_CHANGED);
        registerReceiver(changes, times);
        IntentFilter packages = new IntentFilter();
        packages.addAction(Intent.ACTION_PACKAGE_ADDED);
        packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packages.addDataScheme("package");
        registerReceiver(changes, packages);
        load();
        focus.get(lastFocus).requestFocus();
    }

    @Override protected void onPause() {
        visible = false;
        generation++;
        unregisterReceiver(changes);
        super.onPause();
    }

    @Override protected void onStop() {
        for (Tile tile : tiles) tile.clearArt();
        apps = new ArrayList<>();
        wallpaper.setImageDrawable(null);
        background = null;
        backgroundKey = "";
        super.onStop();
    }

    @Override protected void onDestroy() {
        io.shutdownNow();
        ui.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void formats() {
        timeFormat = new SimpleDateFormat("12-hour".equals(config.get("time")) ? "h:mm a" : "HH:mm", Locale.getDefault());
        dateFormat = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault());
    }

    private void tick() {
        Date now = new Date();
        clock.setText(timeFormat.format(now));
        date.setText(dateFormat.format(now));
    }

    private int dp(float value) { return Ui.dp(this, value); }

    private void build() {
        focus.clear();
        tiles.clear();
        apps = new ArrayList<>();
        order = Favorites.order(config.get("order"));
        root = new FrameLayout(this);
        root.setClipChildren(false);
        root.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFF18314D, 0xFF0B192C, 0xFF060D18}));
        wallpaper = new ImageView(this);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));
        if (background != null) wallpaper.setImageBitmap(background);
        View dim = new View(this);
        dim.setBackgroundColor((Integer.parseInt(config.get("dim")) * 255 / 100) << 24);
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        // A static legibility wash; no blur, live effects or wallpaper animation.
        View wash = new View(this);
        wash.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x22081120, 0x00081120, 0xB3081120}));
        root.addView(wash, new FrameLayout.LayoutParams(-1, -1));

        TextView wordmark = Ui.text(this, "JAG  /  HOME", 17, Ui.TEXT);
        wordmark.setLetterSpacing(0.12f);
        FrameLayout.LayoutParams word = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        word.setMargins(dp(48), dp(38), 0, 0);
        root.addView(wordmark, word);
        LinearLayout time = new LinearLayout(this);
        time.setOrientation(LinearLayout.VERTICAL);
        time.setGravity(Gravity.END);
        clock = Ui.text(this, "", 27, Ui.TEXT);
        clock.setTypeface(Ui.LIGHT);
        date = Ui.text(this, "", 12, Ui.MUTED);
        date.setPadding(0, dp(5), 0, 0);
        time.addView(clock);
        time.addView(date);
        time.setVisibility("Shown".equals(config.get("clock")) ? View.VISIBLE : View.GONE);
        FrameLayout.LayoutParams when = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        when.setMargins(0, dp(33), dp(48), 0);
        root.addView(time, when);

        LinearLayout center = new LinearLayout(this);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setClipChildren(false);
        center.setClipToPadding(false);
        TextView heading = Ui.text(this, "Your favorites", 26, Ui.TEXT);
        heading.setTypeface(Ui.LIGHT);
        heading.setPadding(dp(4), 0, 0, dp(24));
        center.addView(heading);
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        int gap = dp(12);
        float size = "Small".equals(config.get("tiles")) ? 0.86f : "Large".equals(config.get("tiles")) ? 1 : 0.94f;
        int available = getResources().getDisplayMetrics().widthPixels - dp(96);
        int width = Math.max(1, Math.round((available - gap * 5) / 6f * size));
        int height = width * 9 / 16 + dp(32);
        for (int i = 0; i < order.size(); i++) {
            final String id = order.get(i);
            Tile tile = new Tile(this, id, config);
            tile.setOnClickListener(v -> launch(id));
            tile.setOnLongClickListener(v -> { options(id); return true; });
            registerFocus(tile, i);
            LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(width, height);
            at.rightMargin = i == 5 ? 0 : gap;
            row.addView(tile, at);
            tiles.add(tile);
        }
        center.addView(row, new LinearLayout.LayoutParams(-2, -2));
        hint = Ui.text(this, "Select to open   ·   Hold Select for options", 12, Ui.MUTED);
        hint.setPadding(dp(4), dp(12), 0, 0);
        center.addView(hint);
        FrameLayout.LayoutParams main = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL | Gravity.CENTER_HORIZONTAL);
        root.addView(center, main);

        LinearLayout actions = new LinearLayout(this);
        String[] names = {"Free Memory", "Wallpaper", "Settings"};
        Runnable[] tasks = {() -> Memory.confirm(this), () -> preferences(true), () -> preferences(false)};
        for (int i = 0; i < names.length; i++) {
            TextView button = Ui.button(this, names[i], config.accent(), tasks[i]);
            button.setGravity(Gravity.CENTER);
            registerFocus(button, i + 6);
            LinearLayout.LayoutParams at = new LinearLayout.LayoutParams(dp(145), dp(46));
            at.setMargins(dp(6), 0, dp(6), 0);
            actions.addView(button, at);
        }
        FrameLayout.LayoutParams quick = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        quick.bottomMargin = dp(57);
        root.addView(actions, quick);
        TextView development = Ui.text(this, "DEVELOPMENT PREVIEW  ·  Home returns to dumbtv", 10, Ui.MUTED);
        development.setLetterSpacing(0.07f);
        FrameLayout.LayoutParams foot = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        foot.bottomMargin = dp(26);
        root.addView(development, foot);
        setContentView(root);
    }

    private void registerFocus(View view, int position) {
        focus.add(view);
        view.setId(View.generateViewId());
        view.setOnFocusChangeListener((v, has) -> {
            if (has) lastFocus = position;
        });
        view.setOnKeyListener((v, code, event) -> {
            int direction = code == KeyEvent.KEYCODE_DPAD_LEFT ? Navigation.LEFT
                : code == KeyEvent.KEYCODE_DPAD_RIGHT ? Navigation.RIGHT
                : code == KeyEvent.KEYCODE_DPAD_UP ? Navigation.UP
                : code == KeyEvent.KEYCODE_DPAD_DOWN ? Navigation.DOWN : -1;
            if (direction < 0) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) focus.get(Navigation.next(position, direction)).requestFocus();
            return true;
        });
    }

    private void load() {
        final int ticket = ++generation;
        final List<String> ids = new ArrayList<>(order);
        final String selected = config.get("wallpaper");
        final String cachedKey = backgroundKey;
        final boolean cached = background != null;
        io.execute(() -> {
            List<AppCatalog.App> loaded = new ArrayList<>();
            for (String id : ids) loaded.add(AppCatalog.load(getApplicationContext(), id));
            File file = Wallpapers.selected(getApplicationContext(), selected);
            String key = file == null ? "" : file.getPath() + ':' + file.lastModified() + ':' + file.length();
            boolean reuse = cached && key.equals(cachedKey);
            Bitmap decoded = reuse ? null : Wallpapers.decode(file);
            ui.post(() -> {
                if (!visible || ticket != generation) {
                    AppCatalog.release(loaded);
                    if (decoded != null) decoded.recycle();
                    return;
                }
                apps = loaded;
                for (int i = 0; i < tiles.size(); i++) tiles.get(i).bind(apps.get(i));
                if (!reuse) {
                    background = decoded;
                    backgroundKey = key;
                    wallpaper.setImageBitmap(decoded);
                }
                hint.setText(!selected.isEmpty() && background == null
                    ? "Wallpaper unavailable · using navy background"
                    : "Select to open   ·   Hold Select for options");
            });
        });
    }

    private void launch(String id) {
        // Resolve again: app could have been disabled or uninstalled since the tile loaded.
        if (!Ui.open(this, AppCatalog.launch(getPackageManager(), id))) {
            Ui.dialog(this).setTitle(Favorites.name(id) + " is unavailable")
                .setMessage("This app is missing, disabled or cannot be opened. Your apps and sign-in data have not been changed.")
                .setPositiveButton("App settings", (d, which) -> Recovery.details(this, id))
                .setNegativeButton("Close", null).show();
        }
    }

    private void options(String id) {
        Ui.dialog(this).setTitle(Favorites.name(id))
            .setItems(new String[] {"Open app", "Change app order", "App information", "Return to dumbtv"}, (d, which) -> {
                if (which == 0) launch(id);
                else if (which == 1) startActivity(new Intent(this, Preferences.class).putExtra("order", true));
                else if (which == 2) Recovery.details(this, id);
                else Recovery.dumbtv(this);
            }).setNegativeButton("Cancel", null).show();
    }

    private void preferences(boolean wall) {
        startActivity(new Intent(this, Preferences.class).putExtra("wallpaper", wall));
    }

    @Override public boolean onKeyDown(int code, KeyEvent event) {
        if (code == KeyEvent.KEYCODE_MENU) {
            if (lastFocus < 6) options(order.get(lastFocus));
            else preferences(false);
            return true;
        }
        return super.onKeyDown(code, event);
    }

    @Override public void onBackPressed() {
        // Ordinary activity Back: leaves the development preview, preserving HOME integration.
        finish();
    }
}
