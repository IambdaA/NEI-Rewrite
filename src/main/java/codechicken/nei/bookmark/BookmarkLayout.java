package codechicken.nei.bookmark;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;

/**
 * Where each bookmark goes on the grid. Plain arithmetic, no game classes.
 *
 * Ported from GTNH NEI's {@code BookmarkGridGenerator} (the parts that do not involve recipes):
 * <ul>
 * <li>a group always starts on a new row, and the item after a group starts on a new row too;</li>
 * <li>a group in "todo list" mode puts each of its items on a row of its own;</li>
 * <li>a collapsed group shows only what fits on its first row.</li>
 * </ul>
 * Also holds {@link #prepareGroups}, NEI's {@code GroupingItem#prepareGroups}: what the row to group map looks like
 * while the player drags across the group strip.
 */
public final class BookmarkLayout {

    public static final int DEFAULT_GROUP = 0;

    public final int columns;
    /** Absolute slot index to item index (index into the group array given to {@link #generate}). */
    public final Map<Integer, Integer> slotToItem = new HashMap<>();
    public final Map<Integer, Integer> itemToSlot = new HashMap<>();
    /** Absolute row to group id. */
    public final Map<Integer, Integer> rowToGroup = new HashMap<>();
    /** One past the highest used slot, 0 when empty. */
    public int maxSlot;

    private BookmarkLayout(int columns) {
        this.columns = columns;
    }

    public int rowCount() {
        return (maxSlot + columns - 1) / columns;
    }

    public int groupOfRow(int row) {
        return rowToGroup.getOrDefault(row, DEFAULT_GROUP);
    }

    /** The first item on a row (from the left), or -1. */
    public int firstItemInRow(int row) {
        for (int c = 0; c < columns; c++) {
            final Integer item = slotToItem.get(row * columns + c);
            if (item != null) return item;
        }
        return -1;
    }

    /** The last item on a row (from the right), or -1. */
    public int lastItemInRow(int row) {
        for (int c = columns - 1; c >= 0; c--) {
            final Integer item = slotToItem.get(row * columns + c);
            if (item != null) return item;
        }
        return -1;
    }

    /**
     * @param itemGroups group id of each visible item, in list order (items of one group must be next to each other)
     * @param todo       true if the group is in todo list mode
     * @param collapsed  true if the group is collapsed
     */
    public static BookmarkLayout generate(int columns, int[] itemGroups, IntPredicate todo, IntPredicate collapsed) {
        return generate(columns, itemGroups, new int[itemGroups.length], new String[itemGroups.length], todo, collapsed);
    }

    /**
     * @param types   0 = plain item, 1 = recipe result, 2 = recipe ingredient (see {@link BookmarkGrid.Item})
     * @param recipes recipe id of each item, or null
     */
    public static BookmarkLayout generate(int columns, int[] itemGroups, int[] types, String[] recipes, IntPredicate todo,
            IntPredicate collapsed) {
        return generate(columns, itemGroups, types, recipes, todo, collapsed, g -> false);
    }

    /**
     * @param crafting a crafting chain group in the normal (not todo) view shows only its results and plain items, all
     *                 of them, not just one row like a collapsed group
     */
    public static BookmarkLayout generate(int columns, int[] itemGroups, int[] types, String[] recipes, IntPredicate todo,
            IntPredicate collapsed, IntPredicate crafting) {
        final BookmarkLayout out = new BookmarkLayout(Math.max(1, columns));
        final int n = itemGroups.length;
        int abs = -1;
        int prev = -1; // index of the previously placed item
        int i = 0;

        while (i < n) {
            final int g = itemGroups[i];

            if (collapsed.test(g)) {
                // a collapsed group shows its results and plain items (not ingredients), at most one row of them
                int shown = 0;
                for (int j = i; j < n && itemGroups[j] == g; j++) {
                    if (types[j] == 2) continue;
                    final int next = nextSlot(abs + 1, prev, j, itemGroups, types, recipes, false, out.columns);
                    if (shown > 0 && next % out.columns == 0) break;
                    shown++;
                    prev = j;
                    abs = next;
                    out.slotToItem.put(abs, j);
                    out.itemToSlot.put(j, abs);
                }
                while (i < n && itemGroups[i] == g) i++;
            } else if (crafting.test(g) && !todo.test(g)) {
                for (int j = i; j < n && itemGroups[j] == g; j++) {
                    if (types[j] == 2) continue;
                    abs = nextSlot(abs + 1, prev, j, itemGroups, types, recipes, false, out.columns);
                    prev = j;
                    out.slotToItem.put(abs, j);
                    out.itemToSlot.put(j, abs);
                }
                while (i < n && itemGroups[i] == g) i++;
            } else {
                abs = nextSlot(abs + 1, prev, i, itemGroups, types, recipes, todo.test(g), out.columns);
                prev = i;
                out.slotToItem.put(abs, i);
                out.itemToSlot.put(i, abs);
                i++;
            }
        }

        out.maxSlot = out.slotToItem.isEmpty() ? 0 : out.slotToItem.keySet().stream().mapToInt(x -> x).max().getAsInt() + 1;
        out.generateGroups(itemGroups);
        return out;
    }

    /** NEI's {@code BookmarkGridGenerator#nextSlotIndex} without invalid slots. */
    private static int nextSlot(int index, int prev, int cur, int[] groups, int[] types, String[] recipes, boolean todo,
            int columns) {
        index = Math.max(0, index);
        final boolean sameGroup = prev >= 0 && groups[prev] == groups[cur];
        final boolean sameRecipe = sameGroup && recipes[cur] != null && recipes[cur].equals(recipes[prev]);

        for (int guard = 0; guard < 4 * columns + 4; guard++) {
            final boolean firstColumn = index % columns == 0;
            if (!firstColumn && !sameGroup) {
                index++; // a new group starts on a new row
            } else if (!todo) {
                return index;
            } else if (firstColumn && (recipes[cur] == null || !sameGroup || types[cur] != 2 || !sameRecipe)) {
                // todo list, first column: a plain item, a recipe result, or an ingredient that starts a row
                return index;
            } else if (!firstColumn && types[cur] != 0 && types[prev] != 0 && sameRecipe) {
                return index; // ingredients continue the row of their recipe's result
            } else {
                index++;
            }
        }
        return index;
    }

    private void generateGroups(int[] itemGroups) {
        int previous = DEFAULT_GROUP;
        for (int slot = 0; slot < maxSlot; slot++) {
            final int row = slot / columns;
            if (rowToGroup.getOrDefault(row, DEFAULT_GROUP) != DEFAULT_GROUP) continue;

            int group = DEFAULT_GROUP;
            final Integer item = slotToItem.get(slot);
            if (item != null) group = itemGroups[item];
            rowToGroup.put(row, group);

            if (group != DEFAULT_GROUP) {
                final boolean same = previous == group;
                previous = group;
                if (same) {
                    for (int r = row - 1; r >= 0 && rowToGroup.getOrDefault(r, DEFAULT_GROUP) == DEFAULT_GROUP; r--) {
                        rowToGroup.put(r, group);
                    }
                }
            }
        }
    }

    /**
     * The row to group map as it will be once the drag is released (NEI's {@code GroupingItem#prepareGroups}).
     *
     * @param groupId    {@link Integer#MIN_VALUE} when dragging from an ungrouped row (a new group), the id of the
     *                   group dragged from, or {@link #DEFAULT_GROUP} for a right drag (take rows out of groups)
     * @param startRow   absolute row where the drag began
     * @param endRow     absolute row the mouse is over now
     * @return a new map; negative values mean "a new group copied from group -value" (a group that was split in two)
     */
    public static Map<Integer, Integer> prepareGroups(Map<Integer, Integer> rows, int groupId, int startRow, int endRow) {
        final int top = Math.min(startRow, endRow);
        final int bottom = Math.max(startRow, endRow);
        final Map<Integer, Integer> groups = new HashMap<>(rows);

        if (groupId != DEFAULT_GROUP) {
            if (startRow > endRow) {
                int r = endRow - 1;
                while (groups.containsKey(r) && groups.get(r) == groupId) groups.put(r--, DEFAULT_GROUP);
            } else if (startRow < endRow) {
                int r = endRow + 1;
                while (groups.containsKey(r) && groups.get(r) == groupId) groups.put(r++, DEFAULT_GROUP);
            }
        } else if (groups.containsKey(top - 1) && groups.containsKey(bottom + 1) && groups.containsKey(bottom)
                && groups.get(top - 1).equals(groups.get(bottom + 1))) {
            final int gid = groups.get(bottom);
            int r = bottom + 1;
            while (groups.containsKey(r) && groups.get(r) == gid) {
                groups.put(r, -groups.get(r));
                r++;
            }
        }

        for (int r = top; r <= bottom && groups.containsKey(r); r++) groups.put(r, groupId);
        return groups;
    }
}
