package codechicken.nei;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;

/**
 * Vanilla does not send recipes to clients, only recipe-book displays for unlocked recipes. NeoForge lets the server
 * opt in per recipe type; we opt in to every registered type so recipe/usage lookup also covers modded machines.
 *
 * This runs on the logical server, so the mod must be installed server-side for lookup to work on multiplayer
 * servers. Singleplayer and LAN hosts are fine.
 */
@EventBusSubscriber(modid = NotEnoughItems.MODID)
public final class RecipeSync {

    private RecipeSync() {}

    @SubscribeEvent
    static void onDatapackSync(OnDatapackSyncEvent event) {
        final RecipeType<?>[] types = BuiltInRegistries.RECIPE_TYPE.stream().toArray(RecipeType<?>[]::new);
        event.sendRecipes(types);
    }
}
