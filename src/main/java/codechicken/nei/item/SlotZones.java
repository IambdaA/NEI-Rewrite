package codechicken.nei.item;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntPredicate;

/**
 * NEI's slot zones ({@code FastTransferManager#generateSlotMap}): slots that sit next to each other on screen (at most
 * 18 pixels apart both ways) are one zone, e.g. a chest's 27 slots, the player's 27 inventory slots, the hotbar.
 * Plain Java, no game classes.
 */
public final class SlotZones {

    private static final int THRESHOLD = 18;

    public record Pos(int x, int y) {}

    private SlotZones() {}

    /**
     * The zones that hold at least one slot accepting the item. Each zone lists its slot indexes top to bottom, left to
     * right; zones are in the order of their lowest slot index.
     */
    public static List<List<Integer>> zones(List<Pos> slots, IntPredicate accepts) {
        final int n = slots.size();
        final boolean[] seen = new boolean[n];
        final List<List<Integer>> out = new ArrayList<>();

        for (int start = 0; start < n; start++) {
            if (seen[start] || !accepts.test(start)) continue;

            final List<Integer> zone = new ArrayList<>();
            final ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(start);
            seen[start] = true;
            while (!queue.isEmpty()) {
                final int cur = queue.poll();
                zone.add(cur);
                for (int i = 0; i < n; i++) {
                    if (seen[i]) continue;
                    if (Math.abs(slots.get(cur).x() - slots.get(i).x()) <= THRESHOLD
                            && Math.abs(slots.get(cur).y() - slots.get(i).y()) <= THRESHOLD) {
                        seen[i] = true;
                        queue.add(i);
                    }
                }
            }
            zone.sort(Comparator.<Integer>comparingInt(i -> slots.get(i).y()).thenComparingInt(i -> slots.get(i).x()));
            out.add(zone);
        }
        return out;
    }

    /** Index of the zone holding the slot, or -1. */
    public static int zoneOf(List<List<Integer>> zones, int slot) {
        for (int z = 0; z < zones.size(); z++) if (zones.get(z).contains(slot)) return z;
        return -1;
    }
}
