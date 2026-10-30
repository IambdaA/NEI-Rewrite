package codechicken.nei.bookmark;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What to take out of a container for a bookmark group ({@code BookmarkPanel#pullBookmarkItems} in NEI). Plain Java.
 *
 * Without recipes in the group every item is wanted in full. With recipes the chain is worked out again with the
 * inventory (only when {@code inventory} is given, NEI's Shift) and the container's items added as plain items: what the
 * chain then uses up of the container's items is what gets pulled. Items are matched by id.
 */
public final class PullMath {

    private PullMath() {}

    private static boolean plain(BookmarkGrid.Item i) {
        return i.recipeId == null || i.type == 0;
    }

    /** @return item id to the amount to pull, in group order */
    public static Map<String, Long> compute(List<BookmarkGrid.Item> items, Set<String> collapsed,
            Map<String, Long> inventory, Map<String, Long> storage) {
        final Map<String, Long> pull = new LinkedHashMap<>();

        if (ChainMath.of(items, collapsed).outputRecipes.isEmpty()) {
            for (BookmarkGrid.Item i : items) {
                final long n = i.amount - inventory.getOrDefault(i.id, 0L);
                if (plain(i) && n > 0) pull.merge(i.id, n, Long::sum);
            }
            return pull;
        }

        final List<BookmarkGrid.Item> list = new ArrayList<>();
        for (BookmarkGrid.Item i : items) {
            if (!plain(i)) continue;
            final long left = i.amount - inventory.getOrDefault(i.id, 0L) - storage.getOrDefault(i.id, 0L);
            if (left <= 0) continue;
            final BookmarkGrid.Item copy = new BookmarkGrid.Item(i.id, (int) Math.min(left, Integer.MAX_VALUE), i.groupId);
            copy.options = i.options;
            list.add(copy);
        }
        for (Map.Entry<String, Long> e : inventory.entrySet()) list.add(stock(e, -1));
        final List<BookmarkGrid.Item> stored = new ArrayList<>();
        for (Map.Entry<String, Long> e : storage.entrySet()) stored.add(stock(e, -2));
        list.addAll(stored);
        for (BookmarkGrid.Item i : items) if (!plain(i)) list.add(i);

        final ChainMath math = ChainMath.of(list, Set.of());
        for (BookmarkGrid.Item s : stored) {
            final ChainMath.Calc c = math.calc.get(s);
            if (c != null && c.shiftAmount > 0) pull.merge(s.id, c.shiftAmount, Long::sum);
        }
        return pull;
    }

    private static BookmarkGrid.Item stock(Map.Entry<String, Long> e, int group) {
        return new BookmarkGrid.Item(e.getKey(), (int) Math.min(e.getValue(), Integer.MAX_VALUE), group);
    }
}
