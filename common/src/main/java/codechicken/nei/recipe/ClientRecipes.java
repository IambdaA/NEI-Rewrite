package codechicken.nei.recipe;

import java.util.Collection;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * The recipes this client knows. In 1.21.1 the server sends every recipe to every client, so they are read from the
 * connection's recipe manager; a change (a reload, another world) is noticed by comparing the manager's collection with
 * the one seen last, and makes {@link RecipeIndex} rebuild.
 */
public final class ClientRecipes {

    private static List<RecipeHolder<?>> all = List.of();
    private static Collection<RecipeHolder<?>> seen;
    private static int seenSize = -1;

    private ClientRecipes() {}

    /** Checks whether the recipes changed since the last call (cheap) and tells the index if so. */
    public static synchronized void refresh() {
        final var connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            if (seen != null) {
                seen = null;
                seenSize = -1;
                all = List.of();
                RecipeIndex.invalidate();
            }
            return;
        }
        final Collection<RecipeHolder<?>> now = connection.getRecipeManager().getRecipes();
        if (now != seen || now.size() != seenSize) {
            seen = now;
            seenSize = now.size();
            all = List.copyOf(now);
            RecipeIndex.invalidate();
        }
    }

    public static synchronized List<RecipeHolder<?>> all() {
        refresh();
        return all;
    }
}
