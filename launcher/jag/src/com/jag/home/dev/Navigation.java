package com.jag.home.dev;

/** D-pad graph used by the home screen; positions 0–5 apps, 6–8 actions. */
final class Navigation {
    static final int LEFT = 0, RIGHT = 1, UP = 2, DOWN = 3;
    private Navigation() {}

    static int next(int at, int direction) {
        if (at < 0 || at > 8) return 0;
        if (at < 6) {
            if (direction == LEFT) return Math.max(0, at - 1);
            if (direction == RIGHT) return Math.min(5, at + 1);
            if (direction == DOWN) return 6 + at / 2;
        } else {
            if (direction == LEFT) return Math.max(6, at - 1);
            if (direction == RIGHT) return Math.min(8, at + 1);
            if (direction == UP) return (at - 6) * 2;
        }
        return at;
    }
}
