package codechicken.nei.bookmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Where every box of the crafting tree sits. Plain Java. Ported from GTNH NEI's {@code CraftingTreeCanvas}
 * (the coordinate part): each recipe's boxes hang under their parent, children are spread {@link #X_SPACING} apart and
 * the parent is centred over them; several roots are set side by side {@link #ROOT_SPACING} apart. The subtrees are
 * packed as close as their outlines ("contours", one left / right pair per row) allow.
 */
public final class TreeLayout {

    public static final int SLOT_SIZE = 18;
    public static final int X_SPACING = 24;
    public static final int Y_SPACING = 14;
    public static final int ROOT_SPACING = 36;

    /** Size of the collapse arrow and the badge, as in NEI's texture. */
    public static final int ARROW_SIZE = 5;
    public static final float BADGE_SCALE = 0.6f;
    public static final int BADGE_SOURCE_SIZE = 16;

    public static final class Pt {

        public int x;
        public int y;

        Pt(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public final Map<TreeSlot, Pt> pointByNode = new HashMap<>();
    public final TreeMap<Integer, List<TreeSlot>> rows = new TreeMap<>();
    public int treeWidth;
    public int treeHeight;

    private TreeGraph graph;

    // ---- layout ----

    /**
     * @param anchor a box that should stay where it is on screen while the tree changes (the camera follows it), or null
     */
    public void recalculate(TreeGraph graph, Set<BookmarkGrid.Item> collapsedItems, TreeSlot anchor, TreeCamera camera) {
        this.graph = graph;

        final Map<TreeSlot, Integer> offsetFromParent = new HashMap<>();
        final Pt anchorOld = anchor != null ? resolveAnchor(anchor) : null;
        final int oldX = anchorOld == null ? 0 : anchorOld.x;
        final int oldY = anchorOld == null ? 0 : anchorOld.y;

        pointByNode.clear();
        rows.clear();
        treeWidth = 0;
        treeHeight = 0;

        for (TreeSlot node : graph.items) pointByNode.put(node, new Pt(0, 0));

        final List<int[]> contour = new ArrayList<>();

        for (TreeSlot root : graph.roots) {
            final List<int[]> layout = computeLayout(root, collapsedItems, offsetFromParent);
            final int shift = contour.isEmpty() ? 0 : requiredRootShift(contour, layout);

            mergeContour(contour, layout, shift);
            assignPositions(root, shift, 0, collapsedItems, offsetFromParent);
        }

        int minX = 0;
        for (Pt p : pointByNode.values()) minX = Math.min(minX, p.x);
        if (minX < 0) for (Pt p : pointByNode.values()) p.x -= minX;

        for (List<TreeSlot> row : rows.values()) {
            for (TreeSlot node : row) {
                final Pt p = pointByNode.get(node);
                treeWidth = Math.max(treeWidth, p.x + SLOT_SIZE);
                treeHeight = Math.max(treeHeight, p.y + SLOT_SIZE);
            }
        }

        if (anchorOld != null && camera != null) {
            final Pt now = resolveAnchor(anchor);
            if (now != null) {
                camera.scrollX += now.x - oldX;
                camera.scrollY += now.y - oldY;
            }
        }
    }

    private Pt resolveAnchor(TreeSlot anchor) {
        final Pt result = pointByNode.get(anchor);
        if (result != null) return result;

        for (Map.Entry<TreeSlot, Pt> entry : pointByNode.entrySet()) {
            if (entry.getKey().ingrItem == anchor.ingrItem) return entry.getValue();
        }
        return null;
    }

    private List<int[]> computeLayout(TreeSlot node, Set<BookmarkGrid.Item> collapsedItems,
            Map<TreeSlot, Integer> offsetFromParent) {
        List<TreeSlot> children = Collections.emptyList();
        if (!collapsedItems.contains(node.ingrItem)) children = graph.childrenOf.getOrDefault(node, Collections.emptyList());

        if (children.isEmpty()) {
            final List<int[]> rows = new ArrayList<>();
            rows.add(new int[] { 0, 0 });
            return rows;
        }

        final int[] offsets = new int[children.size()];
        final List<int[]> combined = new ArrayList<>();

        for (int i = 0; i < children.size(); i++) {
            final List<int[]> childRows = computeLayout(children.get(i), collapsedItems, offsetFromParent);
            final int shift = i == 0 ? 0 : requiredShift(combined, childRows, X_SPACING);

            offsets[i] = shift;
            mergeContour(combined, childRows, shift);
        }

        final int selfX = (offsets[0] + offsets[children.size() - 1]) / 2;

        for (int i = 0; i < children.size(); i++) offsetFromParent.put(children.get(i), offsets[i] - selfX);

        for (int[] row : combined) {
            row[0] -= selfX;
            row[1] -= selfX;
        }

        final List<int[]> rows = new ArrayList<>(combined.size() + 1);
        rows.add(new int[] { 0, 0 });
        rows.addAll(combined);
        return rows;
    }

    private static int requiredShift(List<int[]> existing, List<int[]> incoming, int spacing) {
        int shift = 0;
        final int rows = Math.min(existing.size(), incoming.size());

        for (int r = 0; r < rows; r++) shift = Math.max(shift, existing.get(r)[1] + spacing - incoming.get(r)[0]);
        return shift;
    }

    private static int requiredRootShift(List<int[]> existing, List<int[]> incoming) {
        int existingRight = Integer.MIN_VALUE;
        for (int[] row : existing) existingRight = Math.max(existingRight, row[1]);

        int incomingLeft = Integer.MAX_VALUE;
        for (int[] row : incoming) incomingLeft = Math.min(incomingLeft, row[0]);

        return Math.max(0, existingRight + ROOT_SPACING - incomingLeft);
    }

    private static void mergeContour(List<int[]> target, List<int[]> source, int shift) {
        for (int r = 0; r < source.size(); r++) {
            final int left = source.get(r)[0] + shift;
            final int right = source.get(r)[1] + shift;

            if (r < target.size()) {
                target.get(r)[0] = Math.min(target.get(r)[0], left);
                target.get(r)[1] = Math.max(target.get(r)[1], right);
            } else {
                target.add(new int[] { left, right });
            }
        }
    }

    private void assignPositions(TreeSlot node, int x, int y, Set<BookmarkGrid.Item> collapsedItems,
            Map<TreeSlot, Integer> offsetFromParent) {
        rows.computeIfAbsent(y, ignored -> new ArrayList<>()).add(node);
        pointByNode.put(node, new Pt(x, y));

        final int childY = y + SLOT_SIZE + Y_SPACING;

        if (!collapsedItems.contains(node.ingrItem)) {
            for (TreeSlot child : graph.childrenOf.getOrDefault(node, Collections.emptyList())) {
                assignPositions(child, x + offsetFromParent.get(child), childY, collapsedItems, offsetFromParent);
            }
        }
    }

    // ---- hit tests (world coordinates) ----

    /** The badge (recipe icon, top left corner of a box that has a recipe below it): x, y, w, h. */
    public static float[] badgeRect(float x, float y) {
        final float w = BADGE_SOURCE_SIZE * BADGE_SCALE;
        final float h = BADGE_SOURCE_SIZE * BADGE_SCALE;
        return new float[] { x - w / 2 + 1, y - h / 2 + 1, w, h };
    }

    /** The group's folded recipes: below one that is not a root of its own the arrow is hidden, as in NEI. */
    public Set<String> collapsedRecipes = Set.of();

    /** Whether the box has an arrow to collapse / expand what is below it. */
    public boolean hasCollapseArrow(TreeSlot node) {
        return graph != null && graph.childrenOf.containsKey(node) && node.prefItem != null
                && !(collapsedRecipes.contains(node.prefItem.recipeId) && !graph.roots.contains(node));
    }

    /** Top left of a box's collapse arrow. */
    public static int arrowX(Pt p) {
        return p.x + SLOT_SIZE / 2 - ARROW_SIZE - 1;
    }

    public static int arrowY(Pt p) {
        return p.y + SLOT_SIZE + 1;
    }

    public TreeSlot nodeAt(float wx, float wy, TreeCamera camera) {
        for (TreeSlot node : visible(camera)) {
            final Pt p = pointByNode.get(node);
            if (wx >= p.x && wx < p.x + SLOT_SIZE && wy >= p.y && wy < p.y + SLOT_SIZE) return node;
        }
        return null;
    }

    /** The box whose badge is under the point (boxes with a recipe below them only). */
    public TreeSlot badgeAt(float wx, float wy, TreeCamera camera) {
        for (TreeSlot node : visible(camera)) {
            if (node.prefItem == null || !graph.childrenOf.containsKey(node)) continue;
            final Pt p = pointByNode.get(node);
            final float[] b = badgeRect(p.x, p.y);
            if (wx >= b[0] && wx < b[0] + b[2] && wy >= b[1] && wy < b[1] + b[3]) return node;
        }
        return null;
    }

    public TreeSlot arrowAt(float wx, float wy, TreeCamera camera) {
        for (TreeSlot node : visible(camera)) {
            if (!hasCollapseArrow(node)) continue;
            final Pt p = pointByNode.get(node);
            final float ax = arrowX(p);
            final float ay = arrowY(p);
            if (wx >= ax && wx < ax + ARROW_SIZE && wy >= ay && wy < ay + ARROW_SIZE) return node;
        }
        return null;
    }

    /** The boxes on the rows that can be on screen, in drawing order. */
    public List<TreeSlot> visible(TreeCamera camera) {
        final List<TreeSlot> out = new ArrayList<>();
        final int min = camera.visibleRowMin();
        final int max = camera.visibleRowMax();
        if (min > max) return out;
        for (List<TreeSlot> row : rows.subMap(min, max).values()) out.addAll(row);
        return out;
    }

    public List<TreeSlot> all() {
        final List<TreeSlot> out = new ArrayList<>();
        for (List<TreeSlot> row : rows.values()) out.addAll(row);
        return out;
    }

    // ---- camera helpers that need the layout ----

    /** NEI's "Fit tree to view": zoom 1, tree centred if it fits, else the first root at the left / the top. */
    public void fitToView(TreeGraph graph, TreeCamera camera) {
        final float margin = 8f;
        final float firstResultCenterX = graph.roots.isEmpty() || pointByNode.get(graph.roots.get(0)) == null
                ? treeWidth / 2f
                : pointByNode.get(graph.roots.get(0)).x + SLOT_SIZE / 2f;

        camera.zoomLevel = 1.0f;
        camera.scrollX = treeWidth <= (camera.widgetW - margin) ? (treeWidth - camera.widgetW) / 2f
                : firstResultCenterX - camera.widgetW / 2f;
        camera.scrollY = treeHeight <= (camera.widgetH - margin) ? (treeHeight - camera.widgetH) / 2f : -margin;
    }

    public void scrollTo(TreeSlot node, TreeCamera camera) {
        if (node == null || !pointByNode.containsKey(node)) return;
        final Pt p = pointByNode.get(node);
        camera.centerOn(p.x + SLOT_SIZE / 2f, p.y + SLOT_SIZE / 2f);
    }
}
