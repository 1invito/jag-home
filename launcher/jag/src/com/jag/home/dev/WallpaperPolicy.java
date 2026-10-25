package com.jag.home.dev;

import java.util.Locale;

final class WallpaperPolicy {
    static final int MAX_WIDTH = 1920, MAX_HEIGHT = 1080;
    static final long MAX_BYTES = 32L * 1024 * 1024;
    private WallpaperPolicy() {}

    static boolean supported(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp");
    }

    static int sample(int width, int height) {
        int sample = 1;
        while ((long) width > (long) MAX_WIDTH * sample || (long) height > (long) MAX_HEIGHT * sample) sample *= 2;
        return sample;
    }

    static boolean decodable(int width, int height, String mime) {
        return width > 0 && height > 0 && width <= 65536 && height <= 65536
            && ("image/jpeg".equals(mime) || "image/png".equals(mime) || "image/webp".equals(mime));
    }
}
