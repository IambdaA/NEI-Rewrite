package codechicken.nei.bookmark;

/**
 * One box of the crafting tree window: an item, how much of it the tree needs, and which recipe makes it.
 * Plain Java. Ported from GTNH NEI's {@code ItemTreeSlot}; container items and fluid cells are left out.
 *
 * <pre>
 * ingrItem   the bookmark item this box stands for: an output recipe's result (a root) or a recipe's ingredient
 * prefItem   the result of the recipe that makes it, or null if nothing in the chain makes it (a leaf to gather)
 * </pre>
 */
public final class TreeSlot {

    public final BookmarkGrid.Item ingrItem;
    public final BookmarkGrid.Item prefItem;

    /** What the parent boxes already asked this recipe for before, and what this box asks now. */
    public final long inheritedRequestedAmount;
    public final long currentRequestedAmount;
    public final long requestedAmount;

    public final long containerAmount;
    /** How much of {@link #requestedAmount} is still missing (what the player does not already have). */
    public final long producedAmount;
    public final long leftAmount;

    /** How many times the recipe that makes this item has to run. */
    public final long multiplier;

    /** The item to draw: what the chain makes, or else the ingredient itself. */
    public final String itemId;

    TreeSlot(ChainMath.Node ingr, long inheritedRequestedAmount, long currentRequestedAmount, long producedAmount,
            long containerAmount, ChainMath.Node pref) {
        this.ingrItem = ingr.src;
        this.prefItem = pref == null ? null : pref.src;
        this.inheritedRequestedAmount = inheritedRequestedAmount;
        this.currentRequestedAmount = currentRequestedAmount;
        this.requestedAmount = inheritedRequestedAmount + currentRequestedAmount;
        this.containerAmount = containerAmount;
        this.producedAmount = producedAmount;
        this.leftAmount = producedAmount + containerAmount;

        final ChainMath.Node source = pref != null ? pref : ingr;
        this.multiplier = source.multiplierFromAmount(this.requestedAmount);
        this.itemId = source.id;
    }

    /** The recipe that makes this item, or null. */
    public String recipeId() {
        return prefItem != null ? prefItem.recipeId : null;
    }
}
