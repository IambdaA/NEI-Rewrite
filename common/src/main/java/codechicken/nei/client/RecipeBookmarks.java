package codechicken.nei.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import codechicken.nei.Bookmarks;
import codechicken.nei.Favorites;
import codechicken.nei.ItemList;
import codechicken.nei.bookmark.BookmarkGrid.RecipeItem;
import codechicken.nei.bookmark.BookmarkGrid.RecipeSpec;
import codechicken.nei.item.ItemEntry;
import codechicken.nei.recipe.RecipeIndex;
import codechicken.nei.recipe.RecipeView;
import codechicken.nei.recipe.RecipeView.SlotView;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * Turns a {@link RecipeView} into what the bookmarks store (NEI's {@code Recipe} to bookmark items), and builds recipe
 * trees from favorites (NEI's {@code GuiFavoriteButton#getRecipesTree}).
 */
final class RecipeBookmarks {

    /** NEI: {@code inventory.favorites.depth}, default 9. */
    private static final int TREE_DEPTH = 9;

    private RecipeBookmarks() {}

    private static String id(ItemStack stack) {
        return codechicken.nei.item.FluidDisplay.idOf(stack.getItem());
    }

    /** A stable id for the recipe: its type plus what it makes and uses. */
    static String key(RecipeView view) {
        final StringBuilder sb = new StringBuilder(view.title().replace(' ', '_')).append('@');
        for (SlotView s : view.slots()) {
            sb.append(s.output() ? 'o' : 'i').append(s.gx()).append(',').append(s.gy()).append('=')
                    .append(id(s.options().get(0))).append('x').append(s.options().get(0).getCount()).append(';');
        }
        return Integer.toHexString(sb.toString().hashCode()) + "_" + Integer.toHexString(sb.length());
    }

    /** The ids of everything a slot accepts, without repeats. */
    private static List<String> optionIds(SlotView slot) {
        final Set<String> ids = new LinkedHashSet<>();
        for (ItemStack s : slot.options()) if (!s.isEmpty()) ids.add(id(s));
        return new ArrayList<>(ids);
    }

    private static int countOf(SlotView slot, String id) {
        for (ItemStack s : slot.options()) if (!s.isEmpty() && id(s).equals(id)) return Math.max(1, s.getCount());
        return 1;
    }

    /** Slots that count as ingredients: not results, and not the slots that are not indexed (fuel). */
    private static boolean isIngredient(SlotView slot) {
        return !slot.output() && slot.indexed() && !slot.options().isEmpty();
    }

    /**
     * The results of a recipe, one entry per distinct item with its counts added up.
     *
     * @param only null for every result; otherwise just the result with this item, or the first one if none matches
     *             (NEI: {@code Recipe#getResult(stack)})
     */
    static List<RecipeItem> results(RecipeView view, ItemStack only) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        for (SlotView s : view.slots()) {
            if (!s.output() || s.options().isEmpty() || s.options().get(0).isEmpty()) continue;
            counts.merge(id(s.options().get(0)), Math.max(1, s.options().get(0).getCount()), Integer::sum);
        }
        if (only != null && counts.size() > 1) {
            final String keep = !only.isEmpty() && counts.containsKey(id(only)) ? id(only)
                    : counts.keySet().iterator().next();
            final int count = counts.get(keep);
            counts.clear();
            counts.put(keep, count);
        }
        final List<RecipeItem> out = new ArrayList<>();
        counts.forEach((k, v) -> out.add(new RecipeItem(k, v)));
        return out;
    }

    /**
     * The ingredients of a recipe: one entry per distinct item, its count the sum over every slot that accepts it
     * (six iron ingot slots are six iron ingots).
     *
     * @param chosen the item picked for a slot that accepts several; slots not in the map use their first
     */
    static List<RecipeItem> ingredients(RecipeView view, Map<SlotView, String> chosen) {
        final Map<String, List<String>> options = new LinkedHashMap<>();
        final List<SlotView> slots = new ArrayList<>();

        for (SlotView s : view.slots()) {
            if (!isIngredient(s)) continue;
            final List<String> ids = optionIds(s);
            if (ids.isEmpty()) continue;
            final String pick = chosen != null && chosen.containsKey(s) && ids.contains(chosen.get(s)) ? chosen.get(s)
                    : ids.get(0);
            slots.add(s);
            options.putIfAbsent(pick, ids);
        }

        final List<RecipeItem> out = new ArrayList<>();
        for (Map.Entry<String, List<String>> e : options.entrySet()) {
            int sum = 0;
            for (SlotView s : slots) if (optionIds(s).contains(e.getKey())) sum += countOf(s, e.getKey());
            out.add(new RecipeItem(e.getKey(), sum, e.getValue()));
        }
        return out;
    }

    static RecipeSpec spec(RecipeView view, Map<SlotView, String> chosen, ItemStack onlyResult) {
        return new RecipeSpec(key(view), results(view, onlyResult), ingredients(view, chosen));
    }

    /** SHIFT + bookmark key over a recipe: adds it to the bookmarks, or removes it if it is there. */
    static boolean toggle(RecipeView view, ItemStack hovered, boolean withCount) {
        final RecipeSpec spec = spec(view, null, hovered == null ? ItemStack.EMPTY : hovered);
        return Bookmarks.toggleRecipe(spec.recipeId(), spec.results(), spec.ingredients(), withCount);
    }

    // ---- favorites ----

    /** The item the heart on this recipe is about: its favorite item, or else its first result. */
    static String favoriteItem(RecipeView view) {
        final String fav = Favorites.itemOf(key(view));
        if (fav != null) return fav;
        final List<RecipeItem> results = results(view, null);
        return results.isEmpty() ? null : results.get(0).id();
    }

    /** NEI's {@code GuiFavoriteButton}: the distinct results the heart can favorite, in recipe order. */
    static List<String> favoriteChoices(RecipeView view) {
        final List<String> ids = new ArrayList<>();
        for (RecipeItem r : results(view, null)) ids.add(r.id());
        return ids;
    }

    /**
     * The favorite recipe key to use for an item: its own favorite (NEI), else, if the option is on and exactly one favorited
     * recipe has the item among its results, that one (the heart stores a recipe under its first result only, so a recipe
     * favorited for "ethylene" would otherwise be invisible to an ingredient that is its fourth result). With several
     * candidates nothing is guessed.
     */
    static String favoriteKeyFor(String itemId) {
        final String own = Favorites.recipeOf(itemId);
        if (own != null || !codechicken.nei.NEIConfig.FAVORITE_ANY_RESULT.getAsBoolean()) return own;
        final ItemEntry entry = ItemList.find(itemId);
        if (entry == null) return null;
        String found = null;
        for (RecipeView v : RecipeIndex.recipesFor(entry.stack.getItem())) {
            final String k = key(v);
            if (Favorites.itemOf(k) == null || k.equals(found)) continue;
            if (found != null) return null; // two favorited recipes make it: not guessed (Shift + F on its slot in the recipe picks one)
            found = k;
        }
        return found;
    }

    /** Whether this recipe is the favorite of that item. */
    static boolean isFavoriteFor(RecipeView view, String itemId) {
        return key(view).equals(Favorites.recipeOf(itemId));
    }

    /** NEI's {@code GuiFavoriteButton#toggleFavorite} with a chosen result (the mouse wheel over the heart picks it). */
    static void toggleFavorite(RecipeView view, String itemId) {
        if (itemId == null) {
            toggleFavorite(view);
            return;
        }
        Favorites.set(itemId, isFavoriteFor(view, itemId) ? null : key(view));
    }

    static boolean isFavorite(RecipeView view) {
        return Favorites.itemOf(key(view)) != null;
    }

    /** NEI's {@code GuiFavoriteButton#toggleFavorite}. */
    static void toggleFavorite(RecipeView view) {
        final String item = favoriteItem(view);
        if (item == null) return;
        Favorites.set(item, isFavorite(view) ? null : key(view));
    }

    /** Key of a manual favorite item (NEI's FAVORITE_ITEM recipe id): the item is preferred in trees, no recipe. */
    static final String FAV_ITEM = "favorite_item:";

    /** NEI's {@code FavoriteRecipes#toggleFavoriteItem}. */
    static void toggleFavoriteItem(String itemId) {
        final String current = Favorites.recipeOf(itemId);
        Favorites.set(itemId, current != null && current.startsWith(FAV_ITEM) ? null : FAV_ITEM + itemId);
    }

    /** NEI's {@code bookmark.favorite} over an item: saves the tree of its favorite recipe. */
    static void saveFavoriteTree(String itemId) {
        final String key = favoriteKeyFor(itemId);
        final RecipeView view = key == null ? null : resolve(itemId, key);
        if (view != null) saveTree(view);
    }

    /** The recipe with this key that makes this item, or null. */
    static RecipeView resolve(String itemId, String key) {
        final ItemEntry entry = ItemList.find(itemId);
        if (entry == null) return null;
        for (RecipeView v : RecipeIndex.recipesFor(entry.stack.getItem())) if (key(v).equals(key)) return v;
        return null;
    }

    /**
     * NEI's {@code GuiFavoriteButton#getRecipesTree}: the recipe plus, level by level, the favorite recipe of each of
     * its ingredients, down to {@value #TREE_DEPTH} levels.
     */
    static List<RecipeSpec> tree(RecipeView main) {
        final Map<SlotView, String> chosen = new IdentityHashMap<>();
        final Set<String> items = new HashSet<>();
        final List<RecipeView> result = new ArrayList<>();
        List<RecipeView> loop = new ArrayList<>();
        loop.add(main);
        result.add(main);
        int depth = TREE_DEPTH;

        while (!loop.isEmpty() && depth-- >= 0) {
            final List<RecipeView> next = new ArrayList<>();

            for (RecipeView view : loop) {
                for (SlotView slot : view.slots()) {
                    if (!isIngredient(slot)) continue;
                    final List<String> perms = optionIds(slot);
                    if (perms.isEmpty()) continue;

                    String stack = perms.stream().filter(items::contains).findFirst().orElse(null);

                    if (stack == null) {
                        stack = perms.get(0);
                        String recipeKey = favoriteKeyFor(stack);
                        if (recipeKey == null) {
                            for (String perm : perms) {
                                if ((recipeKey = favoriteKeyFor(perm)) != null) {
                                    stack = perm;
                                    break;
                                }
                            }
                        }

                        final RecipeView recipe = recipeKey == null ? null : resolve(stack, recipeKey);
                        if (recipe == null) {
                            // a favorite item has no recipe but is still the one to use
                            if (recipeKey == null || !recipeKey.startsWith(FAV_ITEM)) stack = null;
                        } else {
                            items.add(stack);
                            if (depth >= 0) {
                                result.add(recipe);
                                next.add(recipe);
                            }
                        }
                    }

                    if (stack != null) chosen.put(slot, stack);
                }
            }
            loop = next;
        }

        final List<RecipeSpec> specs = new ArrayList<>();
        for (RecipeView v : result) specs.add(spec(v, chosen, null));
        return specs;
    }

    /** Shift + bookmark key on the heart: saves the recipe tree as a new crafting chain group. Returns its id, or -1. */
    static int saveTree(RecipeView view) {
        return Bookmarks.addRecipeTree(tree(view));
    }
}
