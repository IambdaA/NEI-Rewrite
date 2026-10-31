package codechicken.nei.item;

import java.util.ArrayList;
import java.util.List;

/**
 * NEI's item panel sorting ({@code ItemSorter}): an ordered list of sort keys, each deciding between two items when the
 * ones before it tie. The user reorders the list; it is saved as comma separated keys.
 *
 * Not carried over: {@code nei.itemsort.damage}, there are no damage values in this Minecraft version.
 */
public final class SortOrder {

    /** Minecraft items first, then by mod, item id, natural order and display name: NEI's default order. */
    public static final List<String> KEYS = List.of("minecraft", "mod", "id", "default", "name");

    public static final String DEFAULT = String.join(",", KEYS);

    private SortOrder() {}

    /** NEI's {@code ItemSorter.fromSaveString}: known keys in the given order; unknown ones dropped, missing ones appended. */
    public static List<String> parse(String saved) {
        final List<String> order = new ArrayList<>();
        if (saved != null) {
            for (String part : saved.split(",")) {
                final String key = part.trim();
                if (KEYS.contains(key) && !order.contains(key)) order.add(key);
            }
        }
        for (String key : KEYS) if (!order.contains(key)) order.add(key);
        return order;
    }

    public static String save(List<String> order) {
        return String.join(",", order);
    }

    /** The order with the key at {@code from} moved to position {@code to} (both clamped to the list). */
    public static List<String> move(List<String> order, int from, int to) {
        final List<String> out = new ArrayList<>(order);
        if (out.isEmpty()) return out;
        from = Math.max(0, Math.min(from, out.size() - 1));
        to = Math.max(0, Math.min(to, out.size() - 1));
        out.add(to, out.remove(from));
        return out;
    }

    public static String label(String key) {
        return switch (key) {
            case "minecraft" -> "Minecraft";
            case "mod" -> "Mod";
            case "id" -> "Item ID";
            case "default" -> "Default";
            default -> "Display Name";
        };
    }

    /** NEI's {@code nei.itemsort.<key>.tip}; null where NEI has none. */
    public static String tip(String key) {
        return switch (key) {
            case "minecraft" -> "Sorts items from vanilla minecraft first";
            case "mod" -> "Groups items in the same mod, sorted by mod name";
            case "default" -> "Leave the natural ordering of each item unchanged";
            default -> null;
        };
    }
}
