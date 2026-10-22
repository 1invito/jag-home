package tv.dumbtv.home;

/** Small host-side checks for the pixel path, without a device or test framework. */
public final class FocusColorTest {
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }

    public static void main(String[] args) {
        int[] transparent = new int[100];
        check(FocusColor.representative(transparent) == FocusColor.FALLBACK, "transparent fallback");

        int[] icon = new int[100];
        for (int i = 0; i < icon.length; i++) icon[i] = i < 75 ? 0xFFFFFFFF : 0xFFFF3020;
        int color = FocusColor.representative(icon);
        int red = color >> 16 & 255, green = color >> 8 & 255, blue = color & 255;
        check(red > green * 2 && red > blue * 2, "colored logo beats white backdrop");
        check(red < 200, "bright art darkened");

        int[] blueIcon = new int[100];
        for (int i = 0; i < blueIcon.length; i++) blueIcon[i] = 0xFF2050E0;
        color = FocusColor.representative(blueIcon);
        check((color & 255) > (color >> 16 & 255), "blue stays blue");
        System.out.println("FocusColorTest passed");
    }
}
