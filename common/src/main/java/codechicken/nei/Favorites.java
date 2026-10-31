package codechicken.nei;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


/**
 * Favorite recipes, as in GTNH NEI's {@code FavoriteRecipes} (manual favorites only): one recipe per item, set with the
 * heart button next to a recipe. A recipe tree ({@code Shift + bookmark key} on the heart) uses the favorite recipe of
 * every ingredient to decide which recipes make that ingredient.
 *
 * NEI also generates favorites for items that have exactly one recipe, but that option is off by default
 * ({@code inventory.favorites.generateSingleRecipeFavorites}), so it is not ported.
 *
 * File ({@code config/notenoughitems-favorites.txt}): one {@code itemId recipeKey} per line. The recipe key is the
 * one the bookmarks use ({@code RecipeBookmarks#key}).
 */
public final class Favorites {

    private static final Map<String, String> BY_ITEM = new LinkedHashMap<>();
    private static boolean loaded;
    private static Path loadedFile;

    private Favorites() {}

    private static Path file() {
        return HistoryFile.configFile("favorites", NEIConfig.FAVORITES_WORLD.getAsBoolean());
    }

    private static synchronized void ensureLoaded() {
        final Path file = file();
        if (loaded && file.equals(loadedFile)) return; // another world, or the option switched
        loaded = true;
        loadedFile = file;
        BY_ITEM.clear();
        boolean seeded = false;
        try {
            Path f = file;
            if (!Files.exists(f)) { // a new world starts with a copy of the global ones
                f = HistoryFile.plainFile("favorites");
                seeded = !f.equals(file) && Files.exists(f);
            }
            if (Files.exists(f)) {
                for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
                    final String[] parts = line.trim().split("\\s+");
                    if (parts.length == 2 && !parts[0].startsWith("#")) BY_ITEM.put(parts[0], parts[1]);
                }
            }
        } catch (IOException e) {
            NotEnoughItems.LOGGER.warn("Could not read favorites: {}", e.toString());
        }
        if (seeded) save();
    }

    private static void save() {
        final List<String> lines = new ArrayList<>();
        for (Map.Entry<String, String> e : BY_ITEM.entrySet()) lines.add(e.getKey() + " " + e.getValue());
        try {
            Files.write(file(), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            NotEnoughItems.LOGGER.warn("Could not save favorites: {}", e.toString());
        }
    }

    /** The favorite recipe key of an item, or null. */
    public static synchronized String recipeOf(String itemId) {
        ensureLoaded();
        return BY_ITEM.get(itemId);
    }

    /** The item a recipe is the favorite of, or null. */
    public static synchronized String itemOf(String recipeKey) {
        ensureLoaded();
        for (Map.Entry<String, String> e : BY_ITEM.entrySet()) if (e.getValue().equals(recipeKey)) return e.getKey();
        return null;
    }

    /** NEI's {@code FavoriteRecipes#setFavorite}: replaces the item's and the recipe's old favorite; null removes. */
    public static synchronized void set(String itemId, String recipeKey) {
        ensureLoaded();
        BY_ITEM.remove(itemId);
        if (recipeKey != null) {
            BY_ITEM.values().removeIf(recipeKey::equals);
            BY_ITEM.put(itemId, recipeKey);
        }
        save();
    }
}
