package codechicken.nei.api;

import java.util.List;

import codechicken.nei.item.ItemEntry;

/** Decides whether an item list entry should be shown. Must be thread safe. */
@FunctionalInterface
public interface ItemFilter {

    boolean matches(ItemEntry entry);

    ItemFilter EVERYTHING = e -> true;

    static ItemFilter not(ItemFilter f) {
        return e -> !f.matches(e);
    }

    static ItemFilter all(List<ItemFilter> filters) {
        return e -> {
            for (ItemFilter f : filters) if (!f.matches(e)) return false;
            return true;
        };
    }

    static ItemFilter any(List<ItemFilter> filters) {
        return e -> {
            for (ItemFilter f : filters) if (f.matches(e)) return true;
            return false;
        };
    }
}
