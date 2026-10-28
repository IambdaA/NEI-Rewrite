package codechicken.nei.bookmark;

/**
 * Pan and zoom of the crafting tree. Plain Java, no game classes. Ported from GTNH NEI's tree {@code Camera}.
 *
 * World coordinates are the tree's own pixels (a box is 18 x 18). {@code scrollX / scrollY} is the world point at the
 * top left of the viewport.
 */
public final class TreeCamera {

    private static final float ZOOM_MIN = 0.1f;
    private static final float ZOOM_MAX = 8.0f;
    private static final double ZOOM_STEP_BASE = 1.4;
    private static final float SCROLL_STEP_DIVISOR = 5f;
    private static final int CULL_MARGIN = 32;
    private static final int CULL_ROW_MARGIN = 16;
    private static final float INITIAL_SCROLL = -8f;

    public int widgetX;
    public int widgetY;
    public int widgetW;
    public int widgetH;

    public float scrollX = INITIAL_SCROLL;
    public float scrollY = INITIAL_SCROLL;
    public float zoomLevel = 1.0f;

    private double lastDragX;
    private double lastDragY;
    private boolean dragging;

    public void setViewport(int x, int y, int w, int h) {
        widgetX = x;
        widgetY = y;
        widgetW = w;
        widgetH = h;
    }

    public boolean isInWidget(double x, double y) {
        return x >= widgetX && y >= widgetY && x < widgetX + widgetW && y < widgetY + widgetH;
    }

    public void startDrag(double mouseX, double mouseY) {
        dragging = true;
        lastDragX = mouseX;
        lastDragY = mouseY;
    }

    public void drag(double mouseX, double mouseY) {
        if (!dragging) return;
        scrollX -= (mouseX - lastDragX) / zoomLevel;
        scrollY -= (mouseY - lastDragY) / zoomLevel;
        lastDragX = mouseX;
        lastDragY = mouseY;
    }

    public void endDrag() {
        dragging = false;
    }

    public boolean isDragging() {
        return dragging;
    }

    /**
     * Wheel: plain = up / down, Shift = left / right, Ctrl = zoom around the mouse (NEI's option
     * "Tree Zoom without Ctrl" makes plain zoom, see {@code zoomWithoutModifier}).
     */
    public void scroll(double mouseX, double mouseY, int direction, boolean shiftKey, boolean zoomKey) {
        if (shiftKey) {
            scrollX -= direction * widgetW / (SCROLL_STEP_DIVISOR * zoomLevel);
        } else if (zoomKey) {
            final double localX = mouseX - widgetX;
            final double localY = mouseY - widgetY;
            final float oldZoom = zoomLevel;

            zoomLevel = clamp((float) (zoomLevel * Math.pow(ZOOM_STEP_BASE, direction)), ZOOM_MIN, ZOOM_MAX);
            scrollX += (float) (localX / oldZoom - localX / zoomLevel);
            scrollY += (float) (localY / oldZoom - localY / zoomLevel);
        } else {
            scrollY -= direction * widgetH / (SCROLL_STEP_DIVISOR * zoomLevel);
        }
    }

    public void centerOn(float worldX, float worldY) {
        scrollX = worldX - (widgetW / zoomLevel) / 2f;
        scrollY = worldY - (widgetH / zoomLevel) / 2f;
    }

    public void clampToBounds(int treeWidth, int treeHeight) {
        scrollX = clamp(scrollX, -widgetW / zoomLevel + 4, treeWidth - 4);
        scrollY = clamp(scrollY, -widgetH / zoomLevel + 4, treeHeight - 4);
    }

    /** First and last tree row (world y) that can be on screen. */
    public int visibleRowMin() {
        final float zScrollY = scrollY * zoomLevel;
        return (int) ((zScrollY - CULL_MARGIN) / zoomLevel) - CULL_ROW_MARGIN;
    }

    public int visibleRowMax() {
        final float zScrollY = scrollY * zoomLevel;
        return (int) ((zScrollY + CULL_MARGIN) / zoomLevel) + (int) (widgetH / zoomLevel) + CULL_ROW_MARGIN;
    }

    public float worldX(double mouseX) {
        return (float) ((mouseX - widgetX + scrollX * zoomLevel) / zoomLevel);
    }

    public float worldY(double mouseY) {
        return (float) ((mouseY - widgetY + scrollY * zoomLevel) / zoomLevel);
    }

    public static float clamp(float value, float min, float max) {
        return value < min ? min : Math.min(value, max);
    }
}
