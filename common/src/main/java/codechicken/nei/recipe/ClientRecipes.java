package codechicken.nei.recipe;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;

/**
 * The recipes this client knows. The server sends every recipe to every client, so they are read from the
 * connection's recipe manager. On 1.20.1 {@code RecipeManager.getRecipes()} builds a new set on every call, so a change
 * cannot be seen by comparing collections: it is noticed when the connection (and with it the manager) changes, or when
 * the loader reports that the server sent the recipes again ({@link #recipesUpdated()}, e.g. after /reload). Either
 * makes {@link RecipeIndex} rebuild.
 */
public final class ClientRecipes {

    private static List<Recipe<?>> all = List.of();
    private static RecipeManager seen;
    private static volatile boolean updated;

    private ClientRecipes() {}

    /** Checks whether the recipes changed since the last call (cheap) and tells the index if so. */
    public static synchronized void refresh() {
        final var connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            if (seen != null) {
                seen = null;
                all = List.of();
                RecipeIndex.invalidate();
            }
            return;
        }
        final RecipeManager now = connection.getRecipeManager();
        if (now != seen || updated) {
            updated = false;
            seen = now;
            all = List.copyOf(now.getRecipes());
            RecipeIndex.invalidate();
        }
    }

    /** The server sent this client its recipes (again): read them on the next {@link #refresh()}. */
    public static void recipesUpdated() {
        updated = true;
    }

    public static synchronized List<Recipe<?>> all() {
        refresh();
        return all;
    }
}
