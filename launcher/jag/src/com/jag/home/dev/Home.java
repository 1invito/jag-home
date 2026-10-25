package com.jag.home.dev;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Visible-only native scene. No HOME takeover, tracking service or polling. */
public final class Home extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<View> focus = new ArrayList<>();
    private final List<Tile> tiles = new ArrayList<>();
    private List<AppCatalog.App> apps = new ArrayList<>();
    private Configuration config;
    private List<String> order, recents;
    private FrameLayout root;
    private ImageView wallpaper;
    private TextView clock, date;
    private Bitmap background;
    private String backgroundKey = "", focusToken = "";
    private boolean visible, lowMemory, loading, reloadRequested, launchPending, wallpaperSuppressed;
    private int generation;
    private float sceneUnit;
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
        if (state != null) focusToken = state.getString("focusToken", focusToken);
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        lowMemory = manager != null && manager.isLowRamDevice();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("focusToken", focusToken);
        super.onSaveInstanceState(state);
    }

    @Override protected void onResume() {
        super.onResume(); visible = true;
        config = new Configuration(new File(getFilesDir(), "jag.properties"));
        build(); formats(); tick();
        IntentFilter times = new IntentFilter(Intent.ACTION_TIME_TICK);
        times.addAction(Intent.ACTION_TIME_CHANGED); times.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        times.addAction(Intent.ACTION_LOCALE_CHANGED);
        registerReceiver(changes, times);
        IntentFilter packages = new IntentFilter();
        packages.addAction(Intent.ACTION_PACKAGE_ADDED); packages.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packages.addAction(Intent.ACTION_PACKAGE_CHANGED); packages.addDataScheme("package");
        registerReceiver(changes, packages);
        load(); restoreFocus();
    }

    @Override protected void onPause() {
        visible = false; generation++; unregisterReceiver(changes); super.onPause();
    }

    @Override protected void onStop() { releaseImages(); super.onStop(); }

    private void releaseImages() {
        for (Tile tile : tiles) tile.clearArt();
        apps = new ArrayList<>();
        if (wallpaper != null) wallpaper.setImageDrawable(null);
        // Let the renderer/GC release displayed bitmaps; never recycle a live display-list reference.
        background = null; backgroundKey = "";
    }

    @Override public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_RUNNING_LOW && level < TRIM_MEMORY_UI_HIDDEN) {
            lowMemory = true;
            for (Tile tile : tiles) tile.reduceEffects();
            if (level >= TRIM_MEMORY_RUNNING_CRITICAL) {
                wallpaperSuppressed = true;
                if (wallpaper != null) wallpaper.setImageDrawable(null);
                background = null; backgroundKey = "";
            }
        }
    }

    @Override protected void onDestroy() {
        io.shutdownNow(); ui.removeCallbacksAndMessages(null); super.onDestroy();
    }

    private void formats() {
        timeFormat = new SimpleDateFormat("12-hour".equals(config.get("time")) ? "h:mm a" : "HH:mm", Locale.getDefault());
        dateFormat = new SimpleDateFormat("EEEE, d MMMM", Locale.getDefault());
    }

    private void tick() {
        Date now = new Date(); clock.setText(timeFormat.format(now)); date.setText(dateFormat.format(now));
    }

    private int dp(float value) { return Math.round(sceneUnit * value); }

    private TextView text(String value, float size, int color) {
        return Ui.text(this, value, size * sceneUnit / getResources().getDisplayMetrics().density, color);
    }

    private void build() {
        focus.clear(); tiles.clear();
        order = Favorites.order(config.get("order"));
        recents = "On".equals(config.get("recent.enabled")) ? RecentHistory.read(config.get("recent")) : new ArrayList<>();
        Theme theme = Theme.named(config.get("theme"));
        DisplayMetrics display = getResources().getDisplayMetrics();
        LayoutPolicy layout = new LayoutPolicy(display.widthPixels, display.heightPixels, display.density,
                config.get("tiles"), !recents.isEmpty());
        sceneUnit = layout.unit;
        root = new FrameLayout(this); root.setClipChildren(false);
        root.addView(new Atmosphere(this, theme), new FrameLayout.LayoutParams(-1, -1));
        wallpaper = new ImageView(this); wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));
        if (theme.photograph && background != null) wallpaper.setImageBitmap(background);
        View dim = new View(this);
        dim.setBackgroundColor(Integer.parseInt(config.get("dim")) * 255 / 100 << 24);
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));
        if (!theme.minimal) {
            View wash = new View(this);
            wash.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x15050B16, 0x33050B16, 0xBE050B16, 0xE5050B16}));
            root.addView(wash, new FrameLayout.LayoutParams(-1, -1));
        }

        LinearLayout brand = new LinearLayout(this); brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView jag = text("JAG", 22, Ui.TEXT); jag.setLetterSpacing(0.15f);
        brand.addView(jag);
        View divider = new View(this); divider.setBackgroundColor(0x60BBCDE5);
        LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(dp(1), dp(17));
        line.setMargins(dp(16), 0, dp(16), 0); brand.addView(divider, line);
        TextView home = text("HOME", 11, Ui.MUTED); home.setLetterSpacing(0.27f); brand.addView(home);
        FrameLayout.LayoutParams word = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        word.setMargins(layout.edge, dp(37), 0, 0); root.addView(brand, word);
        LinearLayout when = new LinearLayout(this); when.setOrientation(LinearLayout.VERTICAL); when.setGravity(Gravity.END);
        clock = text("", 31, Ui.TEXT); clock.setTypeface(Ui.LIGHT);
        date = text("", 11, Ui.MUTED); date.setPadding(0, dp(6), 0, 0);
        when.addView(clock); when.addView(date);
        when.setVisibility("Shown".equals(config.get("clock")) ? View.VISIBLE : View.GONE);
        date.setVisibility(theme.minimal ? View.GONE : View.VISIBLE);
        FrameLayout.LayoutParams at = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        at.setMargins(0, dp(31), layout.edge, 0); root.addView(when, at);

        int left = (display.widthPixels - layout.rowWidth) / 2;
        if (!theme.minimal) section("FAVORITES", left + dp(3), layout.top - dp(25));
        LinearLayout row = strip();
        for (int i = 0; i < order.size(); i++) {
            Tile tile = tile(order.get(i), false, "favorite:");
            LinearLayout.LayoutParams card = new LinearLayout.LayoutParams(layout.cardWidth, layout.cardHeight);
            card.rightMargin = i == 5 ? 0 : layout.gap; row.addView(tile, card);
        }
        FrameLayout.LayoutParams primary = new FrameLayout.LayoutParams(layout.rowWidth, layout.cardHeight);
        primary.leftMargin = left; primary.topMargin = layout.top; root.addView(row, primary);
        if (!recents.isEmpty()) {
            if (!theme.minimal) section("RECENTLY OPENED", left + dp(3), layout.recentTop - dp(23));
            LinearLayout recentRow = strip();
            for (int i = 0; i < recents.size(); i++) {
                Tile tile = tile(recents.get(i), true, "recent:");
                LinearLayout.LayoutParams card = new LinearLayout.LayoutParams(layout.recentWidth, layout.recentHeight);
                card.rightMargin = layout.gap; recentRow.addView(tile, card);
            }
            FrameLayout.LayoutParams secondary = new FrameLayout.LayoutParams(-2, layout.recentHeight);
            secondary.leftMargin = left; secondary.topMargin = layout.recentTop; root.addView(recentRow, secondary);
        }

        LinearLayout dock = strip(); dock.setPadding(dp(5), dp(5), dp(5), dp(5));
        dock.setBackground(Ui.round(theme.dock, 0x24D6E4FF, dp(17)));
        String[] names = {"Wallpaper", "Customize", "Memory Status", "System Settings"};
        Runnable[] tasks = {() -> preferences(true), () -> preferences(false), () -> Memory.information(this), () -> Recovery.systemSettings(this)};
        int width = Math.min(layout.rowWidth, dp(650));
        for (int i = 0; i < names.length; i++) {
            DockButton button = new DockButton(this, i, names[i], config.accent());
            final Runnable action = tasks[i]; button.setOnClickListener(v -> action.run());
            registerFocus(button, "dock:" + i);
            dock.addView(button, new LinearLayout.LayoutParams(0, layout.dockHeight, 1));
        }
        FrameLayout.LayoutParams quick = new FrameLayout.LayoutParams(width, layout.dockHeight + dp(10), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        quick.bottomMargin = layout.dockBottom; root.addView(dock, quick);
        setContentView(root);
    }

    private LinearLayout strip() {
        LinearLayout row = new LinearLayout(this); row.setClipChildren(false); row.setClipToPadding(false); return row;
    }

    private void section(String name, int left, int top) {
        TextView section = text(name, 9, 0xB4BCCCE0); section.setLetterSpacing(0.22f);
        FrameLayout.LayoutParams place = new FrameLayout.LayoutParams(-2, -2);
        place.leftMargin = left; place.topMargin = top; root.addView(section, place);
    }

    private Tile tile(String id, boolean compact, String kind) {
        Tile tile = new Tile(this, id, config, compact, lowMemory);
        tile.setOnClickListener(v -> launch(id));
        tile.setOnLongClickListener(v -> { options(id); return true; });
        registerFocus(tile, kind + id); tiles.add(tile); return tile;
    }

    private void registerFocus(View view, String token) {
        final int position = focus.size(); focus.add(view); view.setId(View.generateViewId()); view.setTag(token);
        view.setOnFocusChangeListener((v, has) -> { if (has) focusToken = token; });
        view.setOnKeyListener((v, code, event) -> {
            int direction = code == KeyEvent.KEYCODE_DPAD_LEFT ? Navigation.LEFT : code == KeyEvent.KEYCODE_DPAD_RIGHT ? Navigation.RIGHT
                : code == KeyEvent.KEYCODE_DPAD_UP ? Navigation.UP : code == KeyEvent.KEYCODE_DPAD_DOWN ? Navigation.DOWN : -1;
            if (direction < 0) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) focus.get(Navigation.next(position, direction, recents.size())).requestFocus();
            return true;
        });
    }

    private void restoreFocus() {
        for (View view : focus) if (focusToken.equals(view.getTag())) { view.requestFocus(); return; }
        focus.get(0).requestFocus();
    }

    private void load() {
        if (loading) { reloadRequested = true; return; }
        loading = true; reloadRequested = false;
        final int ticket = ++generation;
        final List<String> ids = new ArrayList<>(order);
        final String selected = Theme.named(config.get("theme")).photograph && !wallpaperSuppressed ? config.get("wallpaper") : "";
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
                loading = false;
                if (!visible || ticket != generation) {
                    AppCatalog.release(loaded); if (decoded != null) decoded.recycle();
                    if (visible) load(); return;
                }
                apps = loaded;
                for (Tile tile : tiles) for (AppCatalog.App app : apps) if (tile.packageId.equals(app.id)) tile.bind(app);
                if (wallpaperSuppressed) {
                    if (decoded != null) decoded.recycle();
                    background = null; backgroundKey = ""; wallpaper.setImageDrawable(null);
                } else if (!reuse) { background = decoded; backgroundKey = key; wallpaper.setImageBitmap(decoded); }
                if (reloadRequested) load();
            });
        });
    }

    private void launch(String id) {
        if (launchPending) return;
        // All PackageManager work stays off the input/render thread, including a fresh launch resolution.
        launchPending = true;
        io.execute(() -> {
            Intent intent = AppCatalog.launch(getPackageManager(), id);
            ui.post(() -> {
                launchPending = false;
                if (!visible) return;
                if (Ui.open(this, intent)) {
                    try { config.recordLaunch(id, true); }
                    catch (IOException e) { Ui.message(this, "App opened. Recent history could not be saved."); }
                } else {
                    Ui.dialog(this).setTitle(Favorites.name(id) + " is unavailable")
                        .setMessage("This app is missing, disabled or cannot be opened. Your apps and sign-in data have not been changed.")
                        .setPositiveButton("App settings", (d, which) -> Recovery.details(this, id)).setNegativeButton("Close", null).show();
                }
            });
        });
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

    private void preferences(boolean wall) { startActivity(new Intent(this, Preferences.class).putExtra("wallpaper", wall)); }

    @Override public boolean onKeyDown(int code, KeyEvent event) {
        if (code == KeyEvent.KEYCODE_MENU) {
            if (focusToken.startsWith("favorite:") || focusToken.startsWith("recent:")) options(focusToken.substring(focusToken.indexOf(':') + 1));
            else preferences(false);
            return true;
        }
        return super.onKeyDown(code, event);
    }

    @Override public void onBackPressed() { finish(); }
}
