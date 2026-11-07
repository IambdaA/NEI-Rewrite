package codechicken.nei.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * The rectangles of an inventory screen that other mods draw into: the sidebar buttons of FTB Quests and FTB Library, a
 * recipe book, the potion effect boxes, the buttons other mods add beside the inventory. The item panels leave these slots
 * out and the bookmark panel is cut to the biggest rectangle that does not touch them.
 *
 * Three sources, merged once per frame by {@link #refresh}:
 * <ul>
 * <li>the exclusion areas that mods register with JEI (the way JEI's own overlay keeps away from them), read through
 * {@link #setProvider} from the JEI plugin;</li>
 * <li>the potion effect boxes ({@link EffectAreas}, which the option "Ignore potion effect overlap" switches off);</li>
 * <li>visible widgets of the screen that lie (partly) outside the inventory window, for mods that add buttons but register
 * no area.</li>
 * </ul>
 * Each area is {@code {x, y, w, h}} in screen coordinates.
 */
public final class GuiAreas {

    private GuiAreas() {}

    private static volatile Function<Screen, List<int[]>> provider = screen -> List.of();
    private static List<int[]> areas = List.of();
    private static boolean warned;

    /** Source of the areas that mods register with JEI; set when JEI's runtime is available. */
    public static void setProvider(Function<Screen, List<int[]>> source) {
        provider = source;
    }

    public static void clearProvider() {
        provider = screen -> List.of();
    }

    /** Collects this frame's areas. Called once per frame before the panels are laid out. */
    static void refresh(AbstractContainerScreen<?> screen) {
        final List<int[]> out = new ArrayList<>(EffectAreas.current());
        try {
            final boolean ignoreEffects = codechicken.nei.NEIConfig.IGNORE_POTION_OVERLAP.getAsBoolean();
            final List<int[]> effects = ignoreEffects ? EffectAreas.boxes() : List.of();
            for (int[] a : provider.apply(screen)) {
                // JEI draws the potion boxes itself; with the option on they are not an obstacle for NEI's panels either
                if (ignoreEffects && isEffectBox(effects, a)) continue;
                add(out, a);
            }
            widgets(screen, out);
        } catch (RuntimeException | LinkageError e) {
            // a faulty handler of some mod must not take the panels down with it
            if (!warned) {
                warned = true;
                codechicken.nei.NotEnoughItems.LOGGER.warn("NEI could not read the GUI areas of other mods: {}", e.toString());
            }
        }
        if (!same(areas, out)) {
            areas = out;
            fitKey = null; // the cached fit is for the old areas
        }
    }

    private static boolean same(List<int[]> a, List<int[]> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) if (!java.util.Arrays.equals(a.get(i), b.get(i))) return false;
        return true;
    }

    /** The areas found by the latest {@link #refresh}. */
    static List<int[]> current() {
        return areas;
    }

    private static boolean isEffectBox(List<int[]> boxes, int[] a) {
        for (int[] b : boxes) if (a[0] == b[0] && a[1] == b[1] && a[3] == b[3]) return true;
        return false;
    }

    private static void add(List<int[]> out, int[] a) {
        if (a[2] <= 0 || a[3] <= 0) return;
        for (int[] b : out) if (b[0] == a[0] && b[1] == a[1] && b[2] == a[2] && b[3] == a[3]) return;
        out.add(a);
    }

    /** Visible widgets that stick out of the inventory window; ones that cover half the screen are overlays, not buttons. */
    private static void widgets(AbstractContainerScreen<?> screen, List<int[]> out) {
        final int left = screen.leftPos;
        final int top = screen.topPos;
        final int right = left + screen.imageWidth;
        final int bottom = top + screen.imageHeight;
        for (GuiEventListener child : screen.children()) {
            if (!(child instanceof AbstractWidget w) || !w.visible) continue;
            final int x = w.getX();
            final int y = w.getY();
            final int width = w.getWidth();
            final int height = w.getHeight();
            if (width <= 0 || height <= 0) continue;
            if (x >= left && y >= top && x + width <= right && y + height <= bottom) continue; // the window's own widgets
            if ((long) width * height * 2 > (long) screen.width * screen.height) continue;
            add(out, new int[] { x, y, width, height });
        }
    }

    /** True if the point is inside one of the areas. */
    static boolean contains(double mx, double my) {
        for (int[] a : areas) {
            if (mx >= a[0] && mx < a[0] + a[2] && my >= a[1] && my < a[1] + a[3]) return true;
        }
        return false;
    }

    /**
     * The biggest rectangle inside {@code {left, top, right, bottom}} that touches none of the areas, as
     * {@code {left, top, right, bottom}}; {@code null} when nothing is left. Every free rectangle can be pushed against
     * the region's edges or the areas' edges, so the left and right sides are tried from those x values and, for each
     * such strip, the tallest gap between the areas that cross it is taken.
     */
    static int[] fit(int left, int top, int right, int bottom) {
        final int[] key = { left, top, right, bottom };
        if (java.util.Arrays.equals(key, fitKey)) return fitResult; // asked every frame, changes rarely
        fitResult = compute(left, top, right, bottom);
        fitKey = key;
        return fitResult;
    }

    private static int[] fitKey;
    private static int[] fitResult;

    private static int[] compute(int left, int top, int right, int bottom) {
        final List<int[]> near = new ArrayList<>();
        for (int[] a : areas) {
            if (a[0] + a[2] > left && a[0] < right && a[1] + a[3] > top && a[1] < bottom) near.add(a);
        }
        if (near.isEmpty()) return new int[] { left, top, right, bottom };
        near.sort(java.util.Comparator.comparingInt(a -> a[1]));

        final java.util.TreeSet<Integer> lefts = new java.util.TreeSet<>();
        final java.util.TreeSet<Integer> rights = new java.util.TreeSet<>();
        lefts.add(left);
        rights.add(right);
        for (int[] a : near) {
            if (a[0] + a[2] < right) lefts.add(Math.max(left, a[0] + a[2]));
            if (a[0] > left) rights.add(Math.min(right, a[0]));
        }

        long bestArea = 0;
        int[] best = null;
        for (int l : lefts) {
            for (int r : rights) {
                if (r <= l) continue;
                int cursor = top; // sweep down the strip over the areas that cross it
                for (int i = 0; i <= near.size(); i++) {
                    final int[] a = i < near.size() ? near.get(i) : null;
                    if (a != null && (a[0] >= r || a[0] + a[2] <= l)) continue;
                    final int gapEnd = a == null ? bottom : Math.min(bottom, a[1]);
                    final long area = (long) (r - l) * (gapEnd - cursor);
                    if (gapEnd > cursor && area > bestArea) {
                        bestArea = area;
                        best = new int[] { l, cursor, r, gapEnd };
                    }
                    if (a != null) cursor = Math.max(cursor, a[1] + a[3]);
                }
            }
        }
        return best;
    }
}
