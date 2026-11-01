package codechicken.nei.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import codechicken.nei.Favorites;
import codechicken.nei.ItemList;
import codechicken.nei.NEIConfig;
import codechicken.nei.item.ItemEntry;
import codechicken.nei.recipe.CraftCheck;
import codechicken.nei.recipe.CraftCheck.Need;
import codechicken.nei.recipe.RecipeIndex;
import codechicken.nei.recipe.RecipeView;
import codechicken.nei.recipe.RecipeView.SlotView;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * NEI's craftables panel ({@code ItemCraftablesPanel}): what the player can craft right now from the inventory, for
 * the crafting grid of the open screen. Favorite recipes come first, then the item list's order; one entry per result
 * item, as many as fit {@code craftablesRows} rows.
 *
 * shortcut: recalculated on the client thread every 600 ms (NEI's own delay), not on a worker; the roughly 1500 vanilla
 * crafting recipes take a few milliseconds. Upgrade to a worker if a modpack with tens of thousands of recipes lags.
 */
final class Craftables {

    private static final long MIN_DELAY = 600;

    private static List<ItemEntry> items = List.of();
    private static final Map<ItemEntry, Integer> AMOUNTS = new HashMap<>();
    private static long last;
    private static int lastColumns = -1;
    private static Object lastScreen;

    private Craftables() {}

    static List<ItemEntry> items() {
        return items;
    }

    static int amount(ItemEntry entry) {
        return AMOUNTS.getOrDefault(entry, 1);
    }

    /** Rows the panel needs for this many columns (0 = hidden). */
    static int rows(int columns) {
        if (items.isEmpty() || columns <= 0) return 0;
        return Math.min((items.size() + columns - 1) / columns, NEIConfig.CRAFTABLES_ROWS.get());
    }

    static void clear() {
        items = List.of();
        AMOUNTS.clear();
        lastScreen = null;
    }

    private static String id(ItemStack stack) {
        return codechicken.nei.item.FluidDisplay.idOf(stack);
    }

    static void update(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen, int columns) {
        if (!NEIConfig.CRAFTABLES_ENABLED.getAsBoolean() || !RecipeTransfer.hasGrid(screen)
                || Minecraft.getInstance().player == null || !ItemList.isLoaded()) {
            clear();
            return;
        }

        final long now = System.currentTimeMillis();
        if (screen == lastScreen && columns == lastColumns && now - last < MIN_DELAY) return;
        last = now;
        lastScreen = screen;
        lastColumns = columns;

        final Map<String, Integer> inventory = new HashMap<>();
        final Inventory inv = Minecraft.getInstance().player.getInventory();
        for (int i = 0; i < 36; i++) {
            final ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) inventory.merge(id(stack), stack.getCount(), Integer::sum);
        }

        final boolean favoritesOnly = NEIConfig.CRAFTABLES_FAVORITES_ONLY.getAsBoolean();
        final Map<String, RecipeView> found = new LinkedHashMap<>(); // one recipe per result item, the last wins as in NEI
        final Map<String, Integer> sizes = new HashMap<>();

        for (RecipeView view : RecipeIndex.craftingViews()) {
            final boolean favorite = Favorites.itemOf(RecipeBookmarks.key(view)) != null;
            if (favoritesOnly && !favorite) continue;
            if (!RecipeTransfer.canTransfer(screen, view)) continue;

            final List<Need> needs = new ArrayList<>();
            String result = null;
            int size = 0;
            for (SlotView slot : view.slots()) {
                if (slot.output()) {
                    // NEI sums the results of the same kind
                    final ItemStack out = slot.options().get(0);
                    if (result == null) result = id(out);
                    if (result.equals(id(out))) size += out.getCount();
                } else {
                    final List<String> options = new ArrayList<>();
                    for (ItemStack option : slot.options()) if (!options.contains(id(option))) options.add(id(option));
                    needs.add(new Need(options, Math.max(1, slot.options().get(0).getCount())));
                }
            }

            if (result != null && CraftCheck.craftable(needs, inventory)) {
                found.put(result, view);
                sizes.put(result, size);
            }
        }

        // favorites first, then the order of the item list
        final List<String> order = new ArrayList<>(found.keySet());
        order.sort(Comparator.<String>comparingInt(r -> Favorites.itemOf(RecipeBookmarks.key(found.get(r))) != null ? 0 : 1)
                .thenComparing(r -> r));

        final int max = Math.max(columns, 1) * NEIConfig.CRAFTABLES_ROWS.get();
        final List<ItemEntry> list = new ArrayList<>();
        AMOUNTS.clear();
        for (String r : order) {
            final ItemEntry entry = ItemList.find(r);
            if (entry == null) continue;
            list.add(entry);
            AMOUNTS.put(entry, sizes.get(r));
            if (list.size() >= max) break;
        }
        items = list;
    }
}
