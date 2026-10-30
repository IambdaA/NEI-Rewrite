package codechicken.nei.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import codechicken.nei.ItemList;
import codechicken.nei.item.ItemEntry;
import codechicken.nei.recipe.RecipeSources;
import codechicken.nei.recipe.RecipeView;
import codechicken.nei.search.SearchParser;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * NEI's mod API ({@code codechicken.nei.api.API}) for NeoForge. A mod listens to {@link NEIRegisterEvent} on its mod event
 * bus and calls these methods from there. Client side only: never touch this class on a dedicated server.
 *
 * Not carried over from NEI: GUI handlers and overlays (except the recipe "+": {@link #registerRecipeTransferHandler}),
 * key binds and options, layout styles, sort options, stack stringify handlers, fluid renderers, mode handlers.
 * Differences: NEI's {@code registerRecipeHandler} / {@code registerUsageHandler} are one {@link #registerRecipeSource}
 * here (a recipe is found by its inputs and outputs, so one list serves both), and {@code addItemListEntry} adds a stack
 * instead of replacing the item's default entries.
 */
public final class API {

    private record Entry<T>(ItemStack stack, T value) {}

    /** A custom subset: a dotted path such as {@code Blocks.MobSpawners} and what belongs in it. */
    public record Subset(String name, ItemFilter filter) {}

    private static final List<ItemStack> HIDDEN = new CopyOnWriteArrayList<>();
    private static final List<ItemFilter> HIDDEN_RULES = new CopyOnWriteArrayList<>();
    private static final List<Entry<String>> NAMES = new CopyOnWriteArrayList<>();
    private static final List<Entry<List<String>>> ALIASES = new CopyOnWriteArrayList<>();
    private static final List<ItemStack> EXTRA = new CopyOnWriteArrayList<>();
    private static final List<Subset> SUBSETS = new CopyOnWriteArrayList<>();
    private static final List<Entry<String>> CATALYSTS = new CopyOnWriteArrayList<>();

    private API() {}

    /**
     * Adds recipes. {@code source} is called on the client thread whenever the recipe index builds (join a world, recipes
     * synced), so it can read the level; return the same list shape each time. The id names the source for logs and for
     * replacing it later.
     */
    public static void registerRecipeSource(String id, Supplier<List<RecipeView>> source) {
        RecipeSources.set(id, source, false);
    }

    /** Hides an item (same item and components) from the item panel; recipes still show it. */
    public static void hideItem(ItemStack item) {
        if (item.isEmpty()) return;
        HIDDEN.add(item.copy());
        if (ItemList.isLoaded()) ItemList.refilter(); // before the first load the list filters on its own
    }

    /** Hides every item matching a search expression, e.g. {@code "@mymod"} or {@code "$c:ingots"}. */
    public static void hideItem(String rule) {
        final ItemFilter filter = new SearchParser().parsePlain(rule);
        if (filter == null) return;
        HIDDEN_RULES.add(filter);
        if (ItemList.isLoaded()) ItemList.refilter(); // before the first load the list filters on its own
    }

    /** Replaces the name shown for an item (item panel, search). A null name removes the override. */
    public static void setOverrideName(ItemStack item, String name) {
        NAMES.removeIf(e -> ItemStack.isSameItemSameComponents(e.stack(), item));
        if (name != null) NAMES.add(new Entry<>(item.copy(), name));
        ItemList.refresh();
    }

    /** Extra names an item is found by in the search field. Each is a translation key or plain text. */
    public static void setAliases(ItemStack item, String... aliases) {
        ALIASES.removeIf(e -> ItemStack.isSameItemSameComponents(e.stack(), item));
        final List<String> list = new ArrayList<>();
        for (String a : aliases) if (a != null && !a.isBlank()) list.add(a.trim());
        if (!list.isEmpty()) ALIASES.add(new Entry<>(item.copy(), list));
        ItemList.refresh();
    }

    /** Adds a stack (an item with data) to the item panel. */
    public static void addItemListEntry(ItemStack item) {
        if (item.isEmpty()) return;
        EXTRA.add(item.copy());
        ItemList.refresh();
    }

    /** Adds an extra stack of an item to the item panel. */
    public static void addItemVariant(Item item, ItemStack variant) {
        if (variant.getItem() == item) addItemListEntry(variant);
    }

    /**
     * NEI's {@code addRecipeCatalyst}: shows {@code machine} in the station column of every recipe type with this title
     * (the tab's name, e.g. {@code "Shaped Crafting"}; not case sensitive), and lists those recipes under the machine's
     * "uses". Recipes you add with {@link #registerRecipeSource} can carry their own stations instead.
     */
    public static void addCatalyst(String recipeTitle, ItemStack machine) {
        if (machine.isEmpty() || recipeTitle == null || recipeTitle.isBlank()) return;
        CATALYSTS.add(new Entry<>(machine.copy(), recipeTitle.trim().toLowerCase(Locale.ROOT)));
        RecipeSources.invalidate();
    }

    /** NEI's {@code IOverlayHandler}: makes the recipe window's "+" place recipes into a menu of your own. */
    public static void registerRecipeTransferHandler(Class<? extends net.minecraft.world.inventory.AbstractContainerMenu> menu,
            RecipeTransferHandler handler) {
        codechicken.nei.recipe.CraftingBridge.register(menu, handler);
    }

    /** NEI's {@code registerBookmarkContainerHandler}: lets the bookmark panel pull items out of this screen's container. */
    public static void registerBookmarkContainerHandler(
            Class<? extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>> screen,
            IBookmarkContainerHandler handler) {
        codechicken.nei.client.ContainerPull.register(screen, handler);
    }

    /**
     * Adds a subset to the Subsets menu.
     *
     * @param name dotted path, e.g. {@code Blocks.MobSpawners}; not case sensitive; missing levels are created
     */
    public static void addSubset(String name, ItemFilter filter) {
        SUBSETS.add(new Subset(name, filter));
        ItemList.refresh();
    }

    public static void addSubset(String name, Iterable<ItemStack> items) {
        final List<ItemStack> list = new ArrayList<>();
        for (ItemStack s : items) list.add(s.copy());
        addSubset(name, e -> {
            for (ItemStack s : list) if (ItemStack.isSameItemSameComponents(s, e.stack)) return true;
            return false;
        });
    }

    // ---- used by NEI itself ----

    /** The machines {@link #addCatalyst} added for a recipe type, by its title. */
    public static List<ItemStack> catalystsFor(String title) {
        final String key = title == null ? "" : title.trim().toLowerCase(Locale.ROOT);
        final List<ItemStack> out = new ArrayList<>();
        for (Entry<String> e : CATALYSTS) if (e.value().equals(key)) out.add(e.stack());
        return out;
    }


    public static boolean hides(ItemEntry e) {
        for (ItemStack s : HIDDEN) if (ItemStack.isSameItemSameComponents(s, e.stack)) return true;
        for (ItemFilter f : HIDDEN_RULES) if (f.matches(e)) return true;
        return false;
    }

    /** Applies a name override to a panel stack (in place) and returns it. */
    public static ItemStack renamed(ItemStack stack) {
        for (Entry<String> e : NAMES) {
            if (ItemStack.isSameItemSameComponents(e.stack(), stack)) {
                stack.set(DataComponents.CUSTOM_NAME, Component.literal(e.value()).withStyle(s -> s.withItalic(false)));
                break;
            }
        }
        return stack;
    }

    /** Lower-case aliases of a stack, translated where the text is a translation key. */
    public static List<String> aliasesOf(ItemStack stack) {
        for (Entry<List<String>> e : ALIASES) {
            if (ItemStack.isSameItemSameComponents(e.stack(), stack)) {
                return e.value().stream().map(a -> Component.translatable(a).getString().toLowerCase(Locale.ROOT)).toList();
            }
        }
        return List.of();
    }

    public static List<ItemStack> extraEntries() {
        return EXTRA;
    }

    public static List<Subset> subsets() {
        return SUBSETS;
    }
}
