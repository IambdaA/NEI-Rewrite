package codechicken.nei.item;

/** Colour conversions for the settings colour picker. ARGB ints, hue 0 - 360, the rest 0 - 1. No game classes. */
public final class ColorMath {

    private ColorMath() {}

    /** @return {hue, saturation, value, alpha} */
    public static float[] toHsv(int argb) {
        final float a = (argb >>> 24) / 255f;
        final float r = ((argb >> 16) & 255) / 255f;
        final float g = ((argb >> 8) & 255) / 255f;
        final float b = (argb & 255) / 255f;
        final float max = Math.max(r, Math.max(g, b));
        final float min = Math.min(r, Math.min(g, b));
        final float d = max - min;
        float h = 0;
        if (d > 0) {
            if (max == r) h = ((g - b) / d) % 6;
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h *= 60;
            if (h < 0) h += 360;
        }
        return new float[] { h, max == 0 ? 0 : d / max, max, a };
    }

    public static int fromHsv(float h, float s, float v, float a) {
        final float hh = ((h % 360) + 360) % 360 / 60;
        final float c = v * s;
        final float x = c * (1 - Math.abs(hh % 2 - 1));
        final float m = v - c;
        float r;
        float g;
        float b;
        switch ((int) hh) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return (Math.round(a * 255) << 24) | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8)
                | Math.round((b + m) * 255);
    }

    public static String hex(int argb) {
        return String.format("#%08X", argb);
    }

    /** {@code #AARRGGBB} or {@code #RRGGBB} (opaque), with or without the #; null if it is not a colour. */
    public static Integer parse(String text) {
        String t = text.trim();
        if (t.startsWith("#")) t = t.substring(1);
        if (!t.matches("[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8}")) return null;
        final long v = Long.parseLong(t, 16);
        return (int) (t.length() == 6 ? 0xFF000000L | v : v);
    }
}
