package codechicken.nei.item;

import net.minecraft.world.item.ItemStack;

/** Small helpers for calls that 1.20.1 does not have. */
public final class Stacks {

    private Stacks() {}

    /** {@code ItemStack#copyWithCount} (added in 1.20.2). */
    public static ItemStack withCount(ItemStack stack, int count) {
        final ItemStack copy = stack.copy();
        copy.setCount(count);
        return copy;
    }
}
