package codechicken.nei.item;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Where and how big NEI's item zoom draws ({@code ItemZoom}). All sizes are in GUI units. Plain Java, no game classes.
 *
 * The zoomed item is drawn in the free area beside the container window: {@code scale} is a multiple of the 16 pixel
 * slot size, and the "zoom" setting (0 to 1000) is the part of that area it may fill, in thousandths.
 */
public final class ZoomMath {

    public static final int MIN_ZOOM = 0;
    public static final int MAX_ZOOM = 1000;
    public static final int SLOT_SIZE = 16;
    private static final int MIN_SCALE = 2;

    public static final class Placement {

        /** The item is drawn {@code scale} times its normal size; its top left corner is {@code (x, y)}. */
        public int scale;
        public float x;
        public float y;
        /** Width of the free area on one side of the window. */
        public int availableWidth;
        public boolean onRight;

        public float size() {
            return scale * SLOT_SIZE;
        }
    }

    private ZoomMath() {}

    private static float pointSize(int screenWidth, int xSize) {
        return (screenWidth - (xSize + screenWidth) / 2) / (float) MAX_ZOOM;
    }

    private static int zoomAmount(float pointSize, int setting) {
        return Math.max(Math.round(SLOT_SIZE * MIN_SCALE / pointSize), setting);
    }

    private static int scaleFor(float pointSize, int setting) {
        return Math.round(zoomAmount(pointSize, setting) * pointSize / SLOT_SIZE);
    }

    /** NEI's {@code ItemZoom#resize}. Returns null when the free area is too narrow to show anything (under two slots). */
    public static Placement place(int screenWidth, int screenHeight, int xSize, int setting, int mouseX,
            boolean preferLeft) {
        final int avail = (screenWidth - xSize) / 2;
        final float point = pointSize(screenWidth, xSize);
        if (avail < SLOT_SIZE * 2 || point <= 0) return null;

        final Placement p = new Placement();
        p.scale = scaleFor(point, setting);
        p.y = (screenHeight - p.size()) / 2f;
        p.x = (avail - p.size()) / 2f;
        p.availableWidth = avail;

        final boolean leftFree = avail < mouseX;
        final boolean rightFree = mouseX < screenWidth - avail;
        p.onRight = leftFree && rightFree ? !preferLeft : !leftFree;
        if (p.onRight) p.x += screenWidth - avail;
        return p;
    }

    /** The "zoom" setting after one step bigger (the item grows by one slot size), as NEI's {@code increaseZoom}. */
    public static int zoomIn(int screenWidth, int xSize, int setting) {
        final float point = pointSize(screenWidth, xSize);
        if (point <= 0) return setting;
        final float scale = scaleFor(point, setting);
        return Math.min(MAX_ZOOM, Math.round((scale + 1) / (point / SLOT_SIZE)));
    }

    /** One step smaller, as NEI's {@code decreaseZoom}; never below two slot sizes. */
    public static int zoomOut(int screenWidth, int xSize, int setting) {
        final float point = pointSize(screenWidth, xSize);
        if (point <= 0) return setting;
        final float scale = scaleFor(point, setting);
        return Math.max(MIN_ZOOM, Math.round(Math.max(MIN_SCALE, scale - 1) / (point / SLOT_SIZE)));
    }

    /** NEI's {@code NEIClientUtils#cropText}: the text cut to fit {@code maxWidth}. */
    public static String crop(String text, int maxWidth, ToIntFunction<String> width) {
        String out = text;
        while (out.length() > 1 && width.applyAsInt(out) > maxWidth) out = out.substring(0, out.length() - 1);
        return out;
    }

    /** Word wrap to {@code maxWidth}; a word wider than the line stays on a line of its own. */
    public static List<String> wrap(String text, int maxWidth, ToIntFunction<String> width) {
        final List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            final String joined = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && width.applyAsInt(joined) > maxWidth) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(joined);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }
}
