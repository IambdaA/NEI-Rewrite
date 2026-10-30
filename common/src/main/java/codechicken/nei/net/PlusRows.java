package codechicken.nei.net;

import java.util.ArrayList;
import java.util.List;

/**
 * NEI's {@code NEIServerUtils.cycleCreativeInv}: the 6 rows of the extended inventory, the 3 upper rows of the player's
 * inventory and the hotbar form a ring of 10 rows (top down); cycling moves every row {@code steps} places down it.
 *
 * Pure Java so it can be tested without Minecraft.
 */
public final class PlusRows {

    private PlusRows() {}

    /**
     * @param storage   54 slots, 6 rows of 9, top row first
     * @param inventory 36 slots in Minecraft's order: 0-8 hotbar, 9-35 the three rows above it, top row first
     */
    public static <T> void cycle(List<T> storage, List<T> inventory, int steps) {
        final List<List<T>> rows = new ArrayList<>();
        for (int r = 0; r < 6; r++) rows.add(new ArrayList<>(storage.subList(r * 9, r * 9 + 9)));
        for (int r = 0; r < 3; r++) rows.add(new ArrayList<>(inventory.subList(9 + r * 9, 18 + r * 9)));
        rows.add(new ArrayList<>(inventory.subList(0, 9)));

        for (int r = 0; r < 10; r++) {
            final List<T> row = rows.get(r);
            final int to = Math.floorMod(r + steps, 10);
            for (int c = 0; c < 9; c++) {
                if (to < 6) storage.set(to * 9 + c, row.get(c));
                else if (to < 9) inventory.set(9 + (to - 6) * 9 + c, row.get(c));
                else inventory.set(c, row.get(c));
            }
        }
    }
}
