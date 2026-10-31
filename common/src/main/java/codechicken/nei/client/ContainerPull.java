package codechicken.nei.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import codechicken.nei.Bookmarks;
import codechicken.nei.ItemList;
import codechicken.nei.api.IBookmarkContainerHandler;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.PullMath;
import codechicken.nei.item.ItemEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * NEI's "pull bookmarked items": takes what a bookmark group needs out of the open container (chest, barrel, ender chest,
 * shulker box; other screens through {@link codechicken.nei.api.API#registerBookmarkContainerHandler}) into the
 * inventory. {@link PullMath} works out the amounts, this class does the clicks.
 */
public final class ContainerPull {

    private static final Map<Class<?>, IBookmarkContainerHandler> HANDLERS = new HashMap<>();

    /** NEI's {@code DefaultBookmarkContainerHandler}. */
    private static final IBookmarkContainerHandler DEFAULT = (screen, wanted) -> {
        final AbstractContainerMenu menu = screen.getMenu();
        final var player = Minecraft.getInstance().player;
        for (ItemStack want : wanted) {
            int need = want.getCount();
            for (int i = 0; i < menu.slots.size() - 36 && need > 0; i++) {
                final ItemStack have = menu.slots.get(i).getItem();
                if (have.isEmpty() || !have.is(want.getItem())) continue;
                final int move = Math.min(need, have.getCount());
                FastTransfer.transferItems(menu, i, move, player);
                need -= move;
            }
        }
    };

    static {
        register(ContainerScreen.class, DEFAULT);
        register(ShulkerBoxScreen.class, DEFAULT);
    }

    private ContainerPull() {}

    public static void register(Class<?> screen, IBookmarkContainerHandler handler) {
        HANDLERS.put(screen, handler);
    }

    /** Whether the screen is a container that can be pulled from (NEI compares the exact class too). */
    static boolean available(Screen screen) {
        return screen != null && HANDLERS.containsKey(screen.getClass());
    }

    private static String id(ItemStack stack) {
        return codechicken.nei.item.FluidDisplay.idOf(stack);
    }

    private static Map<String, Long> count(List<ItemStack> stacks) {
        final Map<String, Long> map = new LinkedHashMap<>();
        for (ItemStack s : stacks) if (!s.isEmpty()) map.merge(id(s), (long) s.getCount(), Long::sum);
        return map;
    }

    /** A bookmark group's items (NEI's {@code pullBookmarkItems(groupId, shift)}). */
    static boolean pullGroup(int groupId, boolean shift) {
        final BookmarkGrid grid = Bookmarks.grid();
        final BookmarkGrid.Group group = grid.group(groupId);
        return pull(grid.groupItems(groupId), group.crafting ? group.collapsedRecipes : Set.of(), shift);
    }

    /** One item, as if bookmarked on its own (NEI's {@code pullRecipeItems}). */
    static boolean pullStack(String itemId, int amount, boolean shift) {
        return pull(List.of(new BookmarkGrid.Item(itemId, amount, 0)), Set.of(), shift);
    }

    /**
     * @param shift NEI's Shift: the inventory counts, so only what is missing is pulled
     * @return false when nothing could be pulled (no container, nothing wanted, no free slot)
     */
    static boolean pull(List<BookmarkGrid.Item> items, Set<String> collapsed, boolean shift) {
        final Screen current = Minecraft.getInstance().screen;
        if (!available(current) || !(current instanceof AbstractContainerScreen<?> screen)) return false;
        final IBookmarkContainerHandler handler = HANDLERS.get(current.getClass());
        final AbstractContainerMenu menu = screen.getMenu();
        if (menu.slots.size() < 36) return false;

        final List<ItemStack> inventory = new ArrayList<>();
        for (int i = menu.slots.size() - 36; i < menu.slots.size(); i++) inventory.add(menu.slots.get(i).getItem());

        final Map<String, Long> need = PullMath.compute(items, collapsed, shift ? count(inventory) : Map.of(),
                count(handler.getStorageStacks(screen)));
        if (need.isEmpty()) return false;

        // as NEI: free slots are empty ones and not full stacks of a wanted item; they bound what can be pulled
        int free = 0;
        for (ItemStack s : inventory) {
            if (s.isEmpty() || s.getCount() < s.getMaxStackSize() && need.containsKey(id(s))) free++;
        }
        if (free == 0) return false;

        final List<ItemStack> wanted = new ArrayList<>();
        for (Map.Entry<String, Long> e : need.entrySet()) {
            final ItemEntry entry = ItemList.find(e.getKey());
            if (entry == null) continue;
            final long count = Math.min(e.getValue(), (long) free * entry.stack.getMaxStackSize());
            if (count > 0) wanted.add(entry.stack.copyWithCount((int) count));
        }
        if (wanted.isEmpty()) return false;

        final int restore = FastTransfer.dropHeld(menu);
        if (menu.getCarried().isEmpty()) {
            handler.pullBookmarkItemsFromContainer(screen, wanted);
            if (restore != -1) FastTransfer.click(menu, restore, 0); // pick the old cursor item up again
        }
        return true;
    }
}
