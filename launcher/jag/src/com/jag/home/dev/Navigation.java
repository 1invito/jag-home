package com.jag.home.dev;

/** Favorite row, optional recent row, four dock controls. Empty rows never receive focus. */
final class Navigation {
    static final int LEFT = 0, RIGHT = 1, UP = 2, DOWN = 3;
    private Navigation() {}

    static int next(int at, int direction, int recents) {
        int recentCount = Math.max(0, Math.min(4, recents));
        if (at < 0 || at >= 10 + recentCount) return 0;
        int row = at < 6 ? 0 : at < 6 + recentCount ? 1 : 2;
        int start = row == 0 ? 0 : row == 1 ? 6 : 6 + recentCount;
        int size = row == 0 ? 6 : row == 1 ? recentCount : 4;
        int col = at - start;
        if (direction == LEFT) return start + Math.max(0, col - 1);
        if (direction == RIGHT) return start + Math.min(size - 1, col + 1);
        int target = direction == UP ? row - 1 : direction == DOWN ? row + 1 : row;
        if (recentCount == 0 && target == 1) target += direction == UP ? -1 : 1;
        if (target < 0 || target > 2 || target == row) return at;
        float x = row == 1 ? col / 5f : col / (float) (size - 1);
        int targetStart = target == 0 ? 0 : target == 1 ? 6 : 6 + recentCount;
        int targetSize = target == 0 ? 6 : target == 1 ? recentCount : 4;
        int next = Math.round(x * (target == 1 ? 5 : targetSize - 1));
        return targetStart + Math.min(targetSize - 1, next);
    }
}
