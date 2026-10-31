package codechicken.nei.recipe;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Lets NEI's own crafting recipes (vanilla crafting, read from the synced recipes) be placed by a mod's crafting screen
 * (an AE2 crafting terminal, a backpack's crafting upgrade ...) through JEI's recipe transfer handlers. The JEI bridge
 * ({@code jei.NEIJeiPlugin}) registers the provider while JEI runs; without JEI there is none and only the vanilla
 * crafting table and the 2 x 2 inventory grid work ({@code client.RecipeTransfer}).
 */
public final class CraftingBridge {

    /** Finds the JEI crafting recipe that matches an NEI crafting view and asks the screen's JEI handler. */
    public interface Provider {

        /** NONE: no handler for this menu / no matching JEI recipe; OK: can be placed now; BLOCKED: handler says no. */
        RecipeView.Drawn.Transfer state(AbstractContainerMenu menu, RecipeView view);

        /** Places the recipe; returns null on success, else a message for the player. */
        String transfer(AbstractContainerMenu menu, Player player, RecipeView view, boolean max);
    }

    private static volatile Provider provider;
    /** Handlers mods registered through {@code API.registerRecipeTransferHandler}, by menu class. */
    private static final java.util.Map<Class<? extends AbstractContainerMenu>, codechicken.nei.api.RecipeTransferHandler> CUSTOM =
            new java.util.concurrent.ConcurrentHashMap<>();

    private CraftingBridge() {}

    public static void register(Class<? extends AbstractContainerMenu> menu, codechicken.nei.api.RecipeTransferHandler handler) {
        CUSTOM.put(menu, handler);
    }

    private static codechicken.nei.api.RecipeTransferHandler custom(AbstractContainerMenu menu) {
        for (var e : CUSTOM.entrySet()) if (e.getKey().isInstance(menu)) return e.getValue();
        return null;
    }

    /** True when a mod registered its own handler for this menu. */
    public static boolean hasCustom(AbstractContainerMenu menu) {
        return custom(menu) != null;
    }

    public static void set(Provider p) {
        provider = p;
    }

    public static void clear(Provider p) {
        if (provider == p) provider = null;
    }

    public static RecipeView.Drawn.Transfer state(AbstractContainerMenu menu, RecipeView view) {
        final var c = custom(menu);
        if (c != null) {
            try {
                return c.canTransfer(menu, view) ? RecipeView.Drawn.Transfer.OK : RecipeView.Drawn.Transfer.BLOCKED;
            } catch (RuntimeException e) {
                return RecipeView.Drawn.Transfer.BLOCKED; // a broken handler of a mod must not break the window
            }
        }
        final Provider p = provider;
        return p == null ? RecipeView.Drawn.Transfer.NONE : p.state(menu, view);
    }

    public static String transfer(AbstractContainerMenu menu, Player player, RecipeView view, boolean max) {
        final var c = custom(menu);
        if (c != null) {
            try {
                return c.transfer(menu, player, view, max);
            } catch (RuntimeException e) {
                return "Could not place the recipe";
            }
        }
        final Provider p = provider;
        return p == null ? "No recipe transfer for this screen (needs JEI, or a mod that registers one)" : p.transfer(menu, player, view, max);
    }
}
