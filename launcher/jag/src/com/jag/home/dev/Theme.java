package com.jag.home.dev;

/** Immutable color/behavior tokens, shared by every theme's single rendering path. */
final class Theme {
    final String name;
    final int top, bottom, light, accent, surface, dock;
    final boolean minimal, photograph;

    private Theme(String name, int top, int bottom, int light, int accent, int surface, int dock,
                  boolean minimal, boolean photograph) {
        this.name = name; this.top = top; this.bottom = bottom; this.light = light;
        this.accent = accent; this.surface = surface; this.dock = dock;
        this.minimal = minimal; this.photograph = photograph;
    }

    private static final Theme CINEMATIC = new Theme("Cinematic", 0xFF091426, 0xFF040812,
        0xFF345A7B, 0xFFD3E8FF, 0xF019293D, 0xD9111D2E, false, true);
    private static final Theme MIDNIGHT = new Theme("Midnight", 0xFF0C1630, 0xFF030711,
        0xFF203668, 0xFF86AEFF, 0xFF142039, 0xF00C1427, false, false);
    private static final Theme GRAPHITE = new Theme("Graphite", 0xFF252A32, 0xFF090B10,
        0xFF4A515C, 0xFFF1F2F4, 0xFF2A303A, 0xF01B2028, false, false);
    private static final Theme MINIMAL = new Theme("Ultra Minimal", 0xFF050608, 0xFF050608,
        0xFF050608, 0xFFE3E7ED, 0xFF101216, 0xFF101216, true, false);

    static Theme named(String name) {
        if ("Midnight".equals(name)) return MIDNIGHT;
        if ("Graphite".equals(name)) return GRAPHITE;
        if ("Ultra Minimal".equals(name)) return MINIMAL;
        return CINEMATIC;
    }
}
