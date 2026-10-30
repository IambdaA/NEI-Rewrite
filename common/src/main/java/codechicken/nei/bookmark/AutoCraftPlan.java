package codechicken.nei.bookmark;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What autocrafting makes, in what order (NEI's {@code AutoCraftingManager} on top of {@code RecipeChainMath}). Plain Java.
 *
 * The chain of the items is worked out with the player's inventory as stock, so what the inventory already covers is not
 * crafted again; the recipes then run bottom up, each as often as the chain says.
 */
public final class AutoCraftPlan {

    /** Run {@code recipeId} {@code runs} times; {@code resultId} is the item it makes (to find the recipe again). */
    public record Step(String recipeId, String resultId, long runs) {}

    private AutoCraftPlan() {}

    /**
     * @param items       the items of one bookmark group (or just one recipe's)
     * @param inventory   item id to how many the player carries
     * @param missingOnly NEI's Shift + Ctrl ("Craft Missing Items"): what the inventory has of the wanted items counts, so
     *                    only the rest is made. Otherwise (Shift only, "Craft Items") the wanted amount is made in full.
     * @param focus       only this recipe and the ones it needs, or null for the whole group
     */
    public static List<Step> plan(List<BookmarkGrid.Item> items, Map<String, Long> inventory, boolean missingOnly,
            String focus) {
        List<BookmarkGrid.Item> chain = items;
        if (focus != null) {
            final Set<String> needed = ChainMath.of(items).dependencies(focus);
            chain = new ArrayList<>();
            for (BookmarkGrid.Item i : items) if (i.recipeId == null || i.type == 0 || needed.contains(i.recipeId)) chain.add(i);
        }

        // the wanted items: results of the recipes nothing else in the chain needs
        final Set<String> wanted = new HashSet<>();
        if (!missingOnly) {
            final Set<String> outputs = ChainMath.of(chain).outputRecipes;
            for (BookmarkGrid.Item i : chain) if (i.type == 1 && outputs.contains(i.recipeId)) wanted.add(i.id);
        }

        final List<BookmarkGrid.Item> all = new ArrayList<>(chain);
        for (Map.Entry<String, Long> e : inventory.entrySet()) {
            if (e.getValue() <= 0 || wanted.contains(e.getKey())) continue;
            all.add(new BookmarkGrid.Item(e.getKey(), (int) Math.min(e.getValue(), Integer.MAX_VALUE), -1));
        }

        final ChainMath math = ChainMath.of(all);
        final List<Step> steps = new ArrayList<>();
        for (String recipe : math.craftOrder()) {
            final long runs = math.runs(recipe);
            if (runs <= 0) continue;
            String result = null;
            for (BookmarkGrid.Item i : chain) {
                if (i.type == 1 && recipe.equals(i.recipeId)) {
                    result = i.id;
                    break;
                }
            }
            if (result != null) steps.add(new Step(recipe, result, runs));
        }
        return steps;
    }
}
