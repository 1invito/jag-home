package com.jag.home.dev;

/** Physical pixel layout with density-scaled, overscan-safe margins. */
final class LayoutPolicy {
    final float unit;
    final int edge, gap, cardWidth, cardHeight, rowWidth, top, recentTop, recentWidth, recentHeight, dockHeight, dockBottom;

    LayoutPolicy(int width, int height, float density, String size, boolean recent) {
        unit = Math.min(density, Math.min(width / 960f, height / 540f));
        edge = Math.round(48 * unit);
        gap = Math.max(4, Math.round(13 * unit));
        float scale = "Small".equals(size) ? 0.87f : "Large".equals(size) ? 1 : 0.95f;
        cardWidth = Math.max(16, (int) ((width - 2 * edge - gap * 5) / 6f * scale));
        cardHeight = cardWidth * 9 / 16 + Math.round(30 * unit);
        rowWidth = cardWidth * 6 + gap * 5;
        top = Math.round(height * (recent ? 0.397f : 0.47f)) - cardHeight / 2;
        recentWidth = Math.min(cardWidth, Math.round(118 * unit));
        recentHeight = recentWidth * 9 / 16 + Math.round(24 * unit);
        recentTop = top + cardHeight + Math.round(35 * unit);
        dockHeight = Math.round(50 * unit);
        dockBottom = Math.round(37 * unit);
    }
}
