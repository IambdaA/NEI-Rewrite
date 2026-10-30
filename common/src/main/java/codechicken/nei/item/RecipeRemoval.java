package codechicken.nei.item;

import java.util.List;
import java.util.Set;

/** NEI's {@code PresetRecipeFilter}: which recipes disappear when their items are removed by a "Remove" preset. */
public final class RecipeRemoval {

    private RecipeRemoval() {}

    /**
     * A recipe is dropped when one of its ingredient slots can only hold removed items, or when everything it makes
     * is removed. A recipe that makes nothing stays unless an ingredient is removed.
     *
     * @param inputs  the registry ids each ingredient slot can show
     * @param outputs the registry ids each result slot can show
     */
    public static boolean keep(List<List<String>> inputs, List<List<String>> outputs, Set<String> removed) {
        for (List<String> slot : inputs) {
            if (!slot.isEmpty() && removed.containsAll(slot)) return false;
        }
        if (outputs.isEmpty()) return true;
        for (List<String> slot : outputs) {
            if (!removed.containsAll(slot)) return true;
        }
        return false;
    }
}
