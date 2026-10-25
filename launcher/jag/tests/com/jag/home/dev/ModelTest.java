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
        check(Navigation.next(0, Navigation.LEFT) == 0, "left boundary");
        check(Navigation.next(5, Navigation.RIGHT) == 5, "right boundary");
        check(Navigation.next(8, Navigation.DOWN) == 8, "bottom boundary");
        check(Navigation.next(3, Navigation.UP) == 3, "top boundary");
        check(Navigation.next(-1, Navigation.DOWN) == 0, "recover invalid restored focus");
        check(Navigation.next(5, Navigation.DOWN) == 8, "last app to settings");
        check(Navigation.next(8, Navigation.UP) == 4, "settings to app row");
        // Every control must be reachable from every other control without escaping the graph.
        for (int start = 0; start < 9; start++) {
            Set<Integer> visited = new HashSet<>();
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                int at = queue.remove();
                if (!visited.add(at)) continue;
                for (int direction = 0; direction < 4; direction++) {
                    int next = Navigation.next(at, direction);
                    check(next >= 0 && next <= 8, "focus never escapes graph");
                    if (!visited.contains(next)) queue.add(next);
                }
            }
            check(visited.size() == 9, "no focus trap starting at " + start);
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
        check(restored.get("accent").equals("Ice"), "invalid enum uses safe default");
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
            favorites(); navigation(); settings(root); wallpaper(); memory();
            System.out.println("JAG models: " + checks + " checks passed");
        } finally {
            File[] files = root.listFiles();
            if (files != null) for (File file : files) Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(root.toPath());
        }
    }
}
