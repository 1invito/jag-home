package com.jag.home.dev;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Real host checks for the exact models used by the activity; no Android stubs. */
public final class ModelTest {
    private static int checks;
    private static void check(boolean pass, String message) {
        checks++;
        if (!pass) throw new AssertionError(message);
    }

    private static void favorites() {
        List<String> defaults = Favorites.order("");
        check(defaults.equals(Arrays.asList(Favorites.PACKAGES)), "owner-specified order");
        String vpn = Favorites.PACKAGES[5];
        List<String> repaired = Favorites.order(vpn + ",unknown," + vpn + "," + Favorites.PACKAGES[0]);
        check(repaired.size() == 6 && new HashSet<>(repaired).size() == 6, "repair duplicates and missing IDs");
        check(repaired.get(0).equals(vpn), "preserve valid custom order");
        check(Favorites.order(Favorites.move("", 5, 0)).get(0).equals(vpn), "move last to first");
        check(Favorites.order(Favorites.move("", -1, 7)).equals(defaults), "invalid move preserves order");
        check(Favorites.availability(false, false).equals("Not installed"), "missing app remains visible");
        check(Favorites.availability(true, false).equals("Unavailable"), "disabled or restricted app");
        check(Favorites.availability(true, true).isEmpty(), "launchable app");
    }

    private static void navigation() {
        for (int recent = 0; recent <= 4; recent++) {
            int total = 10 + recent;
            check(Navigation.next(0, Navigation.LEFT, recent) == 0, "favorite left boundary");
            check(Navigation.next(5, Navigation.RIGHT, recent) == 5, "favorite right boundary");
            check(Navigation.next(total - 1, Navigation.DOWN, recent) == total - 1, "dock lower boundary");
            check(Navigation.next(-1, Navigation.UP, recent) == 0, "recover missing focus token");
            check(Navigation.next(5, Navigation.DOWN, recent) == (recent == 0 ? 9 : 5 + recent), "primary to next visible row");
            for (int start = 0; start < total; start++) {
                Set<Integer> visited = new HashSet<>();
                ArrayDeque<Integer> queue = new ArrayDeque<>(); queue.add(start);
                while (!queue.isEmpty()) {
                    int at = queue.remove();
                    if (!visited.add(at)) continue;
                    for (int direction = 0; direction < 4; direction++) {
                        int next = Navigation.next(at, direction, recent);
                        check(next >= 0 && next < total, "no invisible/missing control receives focus");
                        if (!visited.contains(next)) queue.add(next);
                    }
                }
                check(visited.size() == total, "every control reachable with " + recent + " recents");
            }
        }
    }

    private static void recentAndThemes(File root) throws Exception {
        Configuration config = new Configuration(new File(root, "v2.properties"));
        check(config.get("theme").equals("Cinematic"), "safe new-key theme default");
        check(config.get("recent.enabled").equals("On") && RecentHistory.read(config.get("recent")).isEmpty(), "enabled and hidden when empty");
        config.recordLaunch(Favorites.PACKAGES[0], false);
        check(config.get("recent").isEmpty(), "failed launches never enter history");
        for (String id : Favorites.PACKAGES) config.recordLaunch(id, true);
        List<String> recent = RecentHistory.read(config.get("recent"));
        check(recent.equals(Arrays.asList(Favorites.PACKAGES[5], Favorites.PACKAGES[4], Favorites.PACKAGES[3], Favorites.PACKAGES[2])), "four unique successes, newest first");
        config.recordLaunch(Favorites.PACKAGES[3], true);
        recent = RecentHistory.read(config.get("recent"));
        check(recent.get(0).equals(Favorites.PACKAGES[3]) && recent.size() == 4, "relaunch moves existing entry to front");
        String saved = config.get("recent");
        config.recordLaunch("unrelated.app", true);
        check(config.get("recent").equals(saved), "only the six favorites can be tracked");
        config.set("recent.enabled", "Off");
        config.recordLaunch(Favorites.PACKAGES[0], true);
        check(config.get("recent").equals(saved), "disabled history does not record");
        config.set("order", Favorites.move("", 5, 0));
        for (String name : new String[] {"Cinematic", "Midnight", "Graphite", "Ultra Minimal"}) {
            config.set("theme", name);
            Configuration restored = new Configuration(new File(root, "v2.properties"));
            check(restored.get("theme").equals(name), "theme persists: " + name);
            check(Theme.named(name).name.equals(name), "distinct immutable tokens");
        }
        check(!config.animateFocus(false, true), "Ultra Minimal is static regardless of animation preference");
        config.set("theme", "Cinematic");
        check(config.animateFocus(false, true), "normal hardware focus animation available");
        check(!config.animateFocus(true, true), "memory pressure degrades to static focus");
        check(!config.animateFocus(false, false), "software rendering degrades to static focus");
        config.set("reduce.motion", "On");
        check(!config.animateFocus(false, true), "reduce motion always wins");
        config.resetAppearance();
        check(config.get("recent").equals(saved), "restore visual defaults preserves history");
        check(config.get("recent.enabled").equals("Off"), "visual reset preserves tracking opt-out");
        check(Favorites.order(config.get("order")).get(0).equals(Favorites.PACKAGES[5]), "visual reset preserves favorite order");
        config.set("recent", "");
        check(config.get("recent").isEmpty() && !config.get("order").isEmpty(), "explicit history clear preserves favorites");
        check(RecentHistory.read("bad," + Favorites.PACKAGES[0] + "," + Favorites.PACKAGES[0]).size() == 1, "corrupt history repairs unknown and duplicate IDs");
        check(Theme.named("unknown").name.equals("Cinematic"), "invalid theme fallback");
        // Exact v1 schema: no new keys and a custom order, no destructive migration/rewrite.
        File legacy = new File(root, "v1.properties");
        String old = "accent=Ice\nclock=Hidden\ntime=12-hour\ndim=40\nlabels=Hidden\nanimation=Off\ntiles=Small\nwallpaper=/saved/picture.jpg\norder=" + Favorites.move("", 4, 0) + "\n";
        Files.write(legacy.toPath(), old.getBytes(StandardCharsets.UTF_8));
        Configuration migrated = new Configuration(legacy);
        check(migrated.get("accent").equals("Ice") && migrated.get("clock").equals("Hidden"), "read v1 appearance unchanged");
        check(migrated.get("theme").equals("Cinematic") && migrated.get("recent.enabled").equals("On"), "new defaults do not require migration");
        migrated.set("theme", "Graphite");
        Configuration updated = new Configuration(legacy);
        check(updated.get("wallpaper").equals("/saved/picture.jpg") && updated.get("time").equals("12-hour"), "editing new settings retains v1 fields");
        check(Favorites.order(updated.get("order")).get(0).equals(Favorites.PACKAGES[4]), "v1 order survives ordinary update");
    }

    private static void layout() {
        for (int[] screen : new int[][] {{1920,1080}, {1280,720}, {3840,2160}, {960,540}}) {
            for (float density : new float[] {1, 1.5f, 2, 3}) {
                for (String size : new String[] {"Small", "Medium", "Large"}) {
                    for (boolean recent : new boolean[] {false, true}) {
                        LayoutPolicy l = new LayoutPolicy(screen[0], screen[1], density, size, recent);
                        check(l.rowWidth + 2 * l.edge <= screen[0], "six cards stay inside overscan margins");
                        check(l.top > l.edge && l.cardWidth > 0, "primary row below header");
                        check(l.top + l.cardHeight < screen[1] - l.dockBottom - l.dockHeight, "primary does not overlap dock");
                        if (recent) check(l.recentTop + l.recentHeight < screen[1] - l.dockBottom - l.dockHeight, "recent row does not overlap dock");
                    }
                }
            }
        }
    }

    private static void settings(File root) throws Exception {
        File file = new File(root, "settings.properties");
        Configuration config = new Configuration(file);
        check(config.get("tiles").equals("Medium"), "first-run defaults");
        config.cycle("dim", -1);
        config.set("animation", "Off");
        config.set("wallpaper", "/local/picture.jpg");
        config.set("order", Favorites.move("", 4, 1));
        Configuration restored = new Configuration(file);
        check(restored.get("dim").equals("0"), "dimming persists");
        check(restored.get("animation").equals("Off"), "animation setting persists");
        check(restored.get("wallpaper").equals("/local/picture.jpg"), "wallpaper selection persists");
        check(Favorites.order(restored.get("order")).get(1).equals(Favorites.PACKAGES[4]), "order persists");
        restored.cycle("dim", -1);
        check(restored.get("dim").equals("80"), "settings wrap backward");
        restored.set("accent", "invalid");
        check(restored.get("accent").equals("Theme"), "invalid enum uses safe default");
        restored.resetAppearance();
        Configuration reset = new Configuration(file);
        check(reset.get("animation").equals("On") && reset.get("wallpaper").isEmpty(), "restore appearance");
        check(Favorites.order(reset.get("order")).get(1).equals(Favorites.PACKAGES[4]), "appearance reset keeps order");
        Configuration failed = new Configuration(new File(root, "absent/settings"));
        try {
            failed.set("tiles", "Large");
            throw new AssertionError("write failure must surface");
        } catch (java.io.IOException expected) {
            check(failed.get("tiles").equals("Medium"), "write failure keeps previous values");
        }
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write("dim=\\uZZZZ".getBytes(StandardCharsets.UTF_8));
        }
        check(new Configuration(file).get("dim").equals("20"), "corrupt settings recover defaults");
    }

    private static void wallpaper() {
        for (String extension : new String[] {"JPG", "jpeg", "png", "WEBP"}) {
            check(WallpaperPolicy.supported("picture." + extension), "supported wallpaper format");
        }
        check(!WallpaperPolicy.supported("video.mp4"), "reject videos");
        check(!WallpaperPolicy.decodable(0, 1080, "image/jpeg"), "corrupt dimensions fallback");
        check(!WallpaperPolicy.decodable(1920, 1080, "image/gif"), "no animated image format");
        check(WallpaperPolicy.sample(3840, 2160) == 2, "4K decode bounded to 1080p");
        check(WallpaperPolicy.sample(3841, 2160) == 4, "odd dimensions cannot round above decode limit");
        check(WallpaperPolicy.sample(1920, 1080) == 1, "avoid upscaling small images");
        for (int[] size : new int[][] {{8192, 8192}, {800, 4000}, {65536, 65536}, {1, 1}}) {
            int sample = WallpaperPolicy.sample(size[0], size[1]);
            check(size[0] / sample <= 1920 && size[1] / sample <= 1080, "decode budget for aspect ratios");
        }
    }

    private static void memory() {
        Set<String> protectedPackages = new HashSet<>(Arrays.asList(Favorites.PACKAGES));
        protectedPackages.addAll(Arrays.asList("com.jag.home.dev", "tv.dumbtv.home", "com.android.systemui",
            "com.amazon.tv.launcher", "com.amazon.tv.settings", "com.android.bluetooth", "dnsfilter.android"));
        protectedPackages.add("optional.background.app");
        protectedPackages.add("");
        protectedPackages.add(null);
        for (int api : new int[] {23, 25, 28, 30, 34, 99}) {
            for (String id : protectedPackages) {
                check(!MemoryPolicy.canAutomaticallyStop(id, api, true), "visible process is not permission to kill");
                check(!MemoryPolicy.canAutomaticallyStop(id, api, false), "unknown processes fail closed");
            }
            check(MemoryPolicy.explanation(api).contains("No apps were stopped"), "unsupported API is explicit");
        }
    }

    public static void main(String[] args) throws Exception {
        File root = Files.createTempDirectory("jag-model-test").toFile();
        try {
            favorites(); navigation(); settings(root); recentAndThemes(root); layout(); wallpaper(); memory();
            System.out.println("JAG models: " + checks + " checks passed");
        } finally {
            File[] files = root.listFiles();
            if (files != null) for (File file : files) Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(root.toPath());
        }
    }
}
