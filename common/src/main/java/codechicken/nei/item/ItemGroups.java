package codechicken.nei.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntPredicate;
import java.util.function.ToIntFunction;

/** The grouping step of NEI's collapsible items, on plain lists so it can be tested without the game. */
public final class ItemGroups {

    private ItemGroups() {}

    /** The item list after grouping: what is shown, and which shown slot belongs to which group. */
    public static final class Arranged<T> {

        public final List<T> shown = new ArrayList<>();
        /** Shown position to group index. */
        public final Map<Integer, Integer> groupOf = new HashMap<>();
        /** Group index to all of its items (also those not shown). */
        public final Map<Integer, List<T>> members = new LinkedHashMap<>();
        /** Only one group is in the list and nothing else: it is shown open and cannot be collapsed. */
        public boolean forceExpand;
    }

    /**
     * NEI's {@code ItemsPanelGrid#refresh}: a collapsed group shows its first item, an expanded one all of its items
     * next to each other.
     *
     * @param groupOf group index of an item, or -1
     */
    public static <T> Arranged<T> arrange(List<T> items, ToIntFunction<T> groupOf, IntPredicate expanded) {
        final Arranged<T> out = new Arranged<>();
        final Set<Integer> emitted = new HashSet<>();
        boolean outside = false;

        for (T item : items) {
            final int g = groupOf.applyAsInt(item);
            if (g < 0) outside = true;
            else out.members.computeIfAbsent(g, k -> new ArrayList<>()).add(item);
        }

        // opens by itself when nothing but one group is listed
        if (!outside && out.members.size() == 1) {
            final int g = out.members.keySet().iterator().next();
            out.shown.addAll(items);
            for (int i = 0; i < items.size(); i++) out.groupOf.put(i, g);
            out.forceExpand = true;
            return out;
        }

        for (T item : items) {
            final int g = groupOf.applyAsInt(item);
            if (g < 0) {
                out.shown.add(item);
            } else if (emitted.add(g)) {
                final List<T> all = out.members.get(g);
                if (expanded.test(g)) {
                    for (T member : all) {
                        out.groupOf.put(out.shown.size(), g);
                        out.shown.add(member);
                    }
                } else {
                    out.groupOf.put(out.shown.size(), g);
                    out.shown.add(item);
                }
            }
        }
        return out;
    }
}
