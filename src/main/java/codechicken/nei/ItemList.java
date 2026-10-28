package codechicken.nei;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import codechicken.nei.api.API;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.item.ItemEntry;
import codechicken.nei.item.SortOrder;
import codechicken.nei.search.SearchParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackLinkedSet;
import net.minecraft.world.item.Items;

/**
 * Client side list of every item, plus the currently filtered view.
 *
 * Like the original, list fields are replaced atomically and their contents are never modified, so the render thread
 * can read them while a worker thread is searching.
 */
public final class ItemList {

    private static volatile List<ItemEntry> all = List.of();
    private static volatile List<ItemEntry> filtered = List.of();
    private static volatile boolean loaded;
    private static volatile java.util.Map<String, ItemEntry> byId = java.util.Map.of();

    private static volatile String lastText = "";
    private static final AtomicInteger GENERATION = new AtomicInteger();
    private static final SearchParser PARSER = new SearchParser();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        final Thread t = new Thread(r, "NEI-search");
        t.setDaemon(true);
        return t;
    });

    private ItemList() {}

    public static boolean isLoaded() {
        return loaded;
    }

    public static List<ItemEntry> getFiltered() {
        return filtered;
    }

    /** The entry for a registry id such as {@code minecraft:iron_ingot}, or null if unknown. */
    /** The full list, before searching and hiding. */
    public static List<ItemEntry> all() {
        return all;
    }

    public static ItemEntry find(String registryId) {
        return byId.get(registryId);
    }

    public static int totalCount() {
        return all.size();
    }

    /**
     * Rebuilds the full list from the item registry. Must run on the client thread (display names need the language
     * manager).
     *
     * Variants (enchanted books, potions, ...) come from the creative search tab; items it does not list fall back to
     * their default stack.
     */
    public static void load() {
        final List<ItemEntry> list = new ArrayList<>();
        final Set<Item> seenItems = new HashSet<>();
        final Set<ItemStack> seenStacks = ItemStackLinkedSet.createTypeAndComponentsSet();

        // Variants first (enchanted books, potions, ...), straight from the creative search tab.
        for (ItemStack stack : creativeStacks()) {
            if (stack.isEmpty() || stack.getItem() == Items.AIR || !seenStacks.add(stack)) continue;
            add(list, stack.copy());
            seenItems.add(stack.getItem());
        }

        // Stacks mods added through the API (NEI's addItemListEntry / addItemVariant).
        for (ItemStack stack : API.extraEntries()) {
            if (stack.isEmpty() || !seenStacks.add(stack)) continue;
            add(list, stack.copy());
            seenItems.add(stack.getItem());
        }

        // NEI's mob spawners: one for every mob.
        final var level = Minecraft.getInstance().level;
        if (level != null) {
            for (ItemStack stack : codechicken.nei.item.MobSpawners.stacks(level.registryAccess())) {
                if (seenStacks.add(stack)) add(list, stack.copy());
            }
        }

        // Anything the creative tabs do not list still gets its default stack.
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR || seenItems.contains(item)) continue;
            final ItemStack stack = codechicken.nei.item.FluidDisplay.named(item.getDefaultInstance());
            if (!stack.isEmpty()) add(list, stack);
        }

        for (int i = 0; i < list.size(); i++) list.get(i).order = i;
        list.sort(comparator());

        all = List.copyOf(list);
        final java.util.Map<String, ItemEntry> ids = new java.util.HashMap<>();
        for (ItemEntry e : all) ids.putIfAbsent(e.registryId, e);
        byId = java.util.Map.copyOf(ids);
        loaded = true;
        Subsets.rebuild(all);
        PARSER.clearCache();
        updateFilter(lastText);
    }

    /** NEI's {@code ItemSorter}: the sort keys in the saved order, each deciding when the ones before it tie. */
    private static Comparator<ItemEntry> comparator() {
        Comparator<ItemEntry> result = null;
        for (String key : SortOrder.parse(NEIConfig.ITEM_SORT.get())) {
            final Comparator<ItemEntry> next = switch (key) {
                case "minecraft" -> Comparator.comparing((ItemEntry e) -> !e.modId.equals("minecraft"));
                case "mod" -> Comparator.comparing((ItemEntry e) -> e.modId);
                case "id" -> Comparator.comparingInt((ItemEntry e) -> e.numericId);
                case "default" -> Comparator.comparingInt((ItemEntry e) -> e.order);
                default -> Comparator.comparing((ItemEntry e) -> e.name);
            };
            result = result == null ? next : result.thenComparing(next);
        }
        return result.thenComparingInt(e -> e.order); // List.sort is stable; this also keeps the order total
    }

    /** Sorts the loaded list again after the sort order changed. */
    public static void resort() {
        if (!loaded) return;
        final List<ItemEntry> list = new ArrayList<>(all);
        list.sort(comparator());
        all = List.copyOf(list);
        refilter();
    }

    private static void add(List<ItemEntry> list, ItemStack stack) {
        try {
            list.add(new ItemEntry(API.renamed(stack)));
        } catch (RuntimeException e) {
            NotEnoughItems.LOGGER.warn("Skipping item {}: {}", stack.getItem(), e.toString());
        }
    }

    /** Every stack the creative search tab shows, operator items included. Empty if there is no world yet. */
    private static Collection<ItemStack> creativeStacks() {
        final var level = Minecraft.getInstance().level;
        if (level == null) return List.of();
        try {
            CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
            return CreativeModeTabs.searchTab().getSearchTabDisplayItems();
        } catch (RuntimeException e) {
            NotEnoughItems.LOGGER.warn("Could not read creative tab contents, item variants will be missing: {}", e.toString());
            return List.of();
        }
    }

    /** Rebuilds the list after the API changed what it holds. Does nothing before the first load. */
    public static void refresh() {
        if (loaded) load();
    }

    /** Re-filters in the background. A newer call supersedes an older one that has not finished yet. */
    public static void updateFilter(String text) {
        lastText = text;
        final int generation = GENERATION.incrementAndGet();
        final ItemFilter filter = PARSER.parse(text);
        final List<ItemEntry> source = all;

        CompletableFuture.runAsync(() -> {
            final List<ItemEntry> result = source.parallelStream().filter(e -> !Subsets.isHidden(e) && !Presets.hides(e) && !API.hides(e) && safeMatch(filter, e)).toList();
            if (generation == GENERATION.get()) filtered = result;
        }, WORKER).exceptionally(t -> {
            NotEnoughItems.LOGGER.error("Item search failed", t);
            return null;
        });
    }

    /** Filters again with the last search text (after a subset was hidden or shown). */
    public static void refilter() {
        updateFilter(lastText);
    }

    private static boolean safeMatch(ItemFilter filter, ItemEntry entry) {
        try {
            return filter.matches(entry);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
