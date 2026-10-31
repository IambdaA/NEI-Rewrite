package codechicken.nei.api;

import codechicken.nei.recipe.RecipeView;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Makes the "+" button of NEI's recipe window work in a mod's own screen (NEI's {@code IOverlayHandler}), without JEI.
 * Register one for your menu class with {@link API#registerRecipeTransferHandler}. A JEI transfer handler of the same
 * screen is used when none is registered.
 */
public interface RecipeTransferHandler {

    /** Whether the recipe can be placed into this menu right now. Called a few times a second; keep it cheap. */
    boolean canTransfer(AbstractContainerMenu menu, RecipeView view);

    /**
     * Places the recipe (moves the ingredients).
     *
     * @param max Shift + click: as many sets as possible
     * @return null when it worked, else a short message for the player
     */
    String transfer(AbstractContainerMenu menu, Player player, RecipeView view, boolean max);
}
