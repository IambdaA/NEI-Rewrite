package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import codechicken.nei.NotEnoughItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;

/** Holds the recipes the server synced to this client (see {@code RecipeSync}). Cleared on logout. */
@EventBusSubscriber(modid = NotEnoughItems.MODID, value = Dist.CLIENT)
public final class ClientRecipes {

    private static volatile List<RecipeHolder<?>> all = List.of();

    private ClientRecipes() {}

    public static List<RecipeHolder<?>> all() {
        return all;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SubscribeEvent
    static void onRecipes(RecipesReceivedEvent event) {
        final List<RecipeHolder<?>> list = new ArrayList<>();
        // raw types because the element type is a wildcard
        for (RecipeType<?> type : BuiltInRegistries.RECIPE_TYPE) {
            final Collection<RecipeHolder<?>> recipes = (Collection) event.getRecipeMap().byType((RecipeType) type);
            list.addAll(recipes);
        }
        all = List.copyOf(list);
        RecipeIndex.invalidate();
        NotEnoughItems.LOGGER.info("NEI received {} recipes from the server", all.size());
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        all = List.of();
        RecipeIndex.invalidate();
    }
}
