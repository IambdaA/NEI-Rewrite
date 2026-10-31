package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import codechicken.nei.NotEnoughItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;

/** Holds the recipes the server synced to this client (each loader module hands them over: NeoForge {@code RecipesReceivedEvent}, Fabric {@code ClientRecipeSynchronizedEvent}). Cleared on logout. */
public final class ClientRecipes {

    private static volatile List<RecipeHolder<?>> all = List.of();

    private ClientRecipes() {}

    public static List<RecipeHolder<?>> all() {
        return all;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public static void set(net.minecraft.world.item.crafting.RecipeMap map) {
        final List<RecipeHolder<?>> list = new ArrayList<>();
        // raw types because the element type is a wildcard
        for (RecipeType<?> type : BuiltInRegistries.RECIPE_TYPE) {
            final Collection<RecipeHolder<?>> recipes = (Collection) map.byType((RecipeType) type);
            list.addAll(recipes);
        }
        all = List.copyOf(list);
        RecipeIndex.invalidate();
        NotEnoughItems.LOGGER.info("NEI received {} recipes from the server", all.size());
    }
    public static void clear() {
        all = List.of();
        RecipeIndex.invalidate();
    }
}
