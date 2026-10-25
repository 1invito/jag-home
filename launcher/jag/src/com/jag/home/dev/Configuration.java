package com.jag.home.dev;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

/** Small private settings file. A failed write never changes the in-memory values. */
final class Configuration {
    static final String[][] CHOICES = {
        {"theme", "Theme", "Cinematic", "Cinematic", "Midnight", "Graphite", "Ultra Minimal"},
        {"dim", "Background dimming", "20", "0", "20", "40", "60", "80"},
        {"accent", "Accent color", "Theme", "Theme", "Ice", "Blue", "Teal", "Violet", "White"},
        {"tiles", "Tile sizing", "Medium", "Small", "Medium", "Large"},
        {"labels", "Application labels", "Shown", "Shown", "Hidden"},
        {"clock", "Clock and date", "Shown", "Shown", "Hidden"},
        {"time", "Clock format", "24-hour", "24-hour", "12-hour"},
        {"animation", "Focus animation", "On", "On", "Off"},
        {"reduce.motion", "Reduce Motion", "Off", "Off", "On"},
        {"recent.enabled", "Recently Opened", "On", "On", "Off"}
    };
    private final File file;
    private Properties values = new Properties();

    Configuration(File file) {
        this.file = file;
        try (FileInputStream in = new FileInputStream(file)) {
            values.load(in);
        } catch (IOException | IllegalArgumentException e) {
            values.clear();
        }
    }

    String get(String key) {
        for (String[] choice : CHOICES) {
            if (choice[0].equals(key)) {
                String saved = values.getProperty(key, choice[2]);
                for (int i = 3; i < choice.length; i++) if (saved.equals(choice[i])) return saved;
                return choice[2];
            }
        }
        return values.getProperty(key, "");
    }

    void set(String key, String value) throws IOException {
        Properties changed = new Properties();
        changed.putAll(values);
        changed.setProperty(key, value);
        commit(changed);
    }

    void cycle(String key, int delta) throws IOException {
        for (String[] c : CHOICES) {
            if (!c[0].equals(key)) continue;
            int i = Arrays.asList(c).subList(3, c.length).indexOf(get(key));
            set(key, c[3 + (i + delta + c.length - 3) % (c.length - 3)]);
            return;
        }
    }

    void resetAppearance() throws IOException {
        Properties changed = new Properties();
        changed.setProperty("order", get("order"));
        changed.setProperty("recent", get("recent"));
        changed.setProperty("recent.enabled", get("recent.enabled"));
        commit(changed);
    }

    void recordLaunch(String id, boolean success) throws IOException {
        if (success && "On".equals(get("recent.enabled"))) set("recent", RecentHistory.launched(get("recent"), id));
    }

    /** Explicit stable import: appearance/order only, never history or arbitrary private keys. */
    void importAppearance(Properties incoming, String wallpaperPath) throws IOException {
        Properties changed = new Properties(); changed.putAll(values);
        for (String[] choice : CHOICES) {
            if (choice[0].equals("recent.enabled")) continue;
            String value = incoming.getProperty(choice[0]);
            for (int i = 3; i < choice.length; i++) if (choice[i].equals(value)) changed.setProperty(choice[0], value);
        }
        if (incoming.containsKey("order")) changed.setProperty("order", Favorites.move(incoming.getProperty("order"), -1, -1));
        if (wallpaperPath != null) changed.setProperty("wallpaper", wallpaperPath);
        commit(changed);
    }

    boolean animateFocus(boolean lowMemory, boolean hardwareAccelerated) {
        return "On".equals(get("animation")) && "Off".equals(get("reduce.motion"))
            && !"Ultra Minimal".equals(get("theme")) && !lowMemory && hardwareAccelerated;
    }

    int accent() {
        switch (get("accent")) {
            case "Blue": return 0xFF8EB6F8;
            case "Teal": return 0xFF83D5CE;
            case "Violet": return 0xFFB9A8EB;
            case "White": return 0xFFF4F6FA;
            case "Theme": return Theme.named(get("theme")).accent;
            default: return 0xFFC5DCFF;
        }
    }

    private void commit(Properties changed) throws IOException {
        File tmp = new File(file.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            changed.store(out, "JAG Home private local settings");
            out.getFD().sync();
        }
        if (!tmp.renameTo(file)) throw new IOException("Could not save settings");
        values = changed;
    }
}
