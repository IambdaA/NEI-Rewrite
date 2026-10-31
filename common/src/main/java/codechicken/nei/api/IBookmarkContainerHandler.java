package codechicken.nei.api;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * NEI's {@code IBookmarkContainerHandler}: tells the bookmark panel how to take items out of a container screen. Register
 * one for your screen class with {@link API#registerBookmarkContainerHandler}.
 */
public interface IBookmarkContainerHandler {

    /** The items stored in the container: by default every slot but the player's 36 inventory slots (the last ones). */
    default List<ItemStack> getStorageStacks(AbstractContainerScreen<?> screen) {
        final List<Slot> slots = screen.getMenu().slots;
        final List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < slots.size() - 36; i++) {
            if (slots.get(i).hasItem()) stacks.add(slots.get(i).getItem().copy());
        }
        return stacks;
    }

    /** Moves these stacks (item and wanted count) from the container to the player's inventory. */
    void pullBookmarkItemsFromContainer(AbstractContainerScreen<?> screen, List<ItemStack> wanted);
}
