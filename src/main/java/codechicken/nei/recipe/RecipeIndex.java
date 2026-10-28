package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import codechicken.nei.NotEnoughItems;
import codechicken.nei.Presets;
import codechicken.nei.item.RecipeRemoval;
import codechicken.nei.recipe.RecipeView.SlotView;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/**
 * Lookup tables from an item to the recipes that make it and the recipes that use it. Built lazily on the client
 * thread (resolving tags needs the world), and rebuilt after the server sends new recipes.
 */
public final class RecipeIndex {

    private record Index(Map<Item, List<RecipeView>> outputs, Map<Item, List<RecipeView>> inputs, int total,
            List<RecipeView> crafting) {}

    private static volatile Index index;

    private RecipeIndex() {}

    public static void invalidate() {
        index = null;
    }

    public static List<RecipeView> recipesFor(Item item) {
        return get().outputs().getOrDefault(item, List.of());
    }

    public static List<RecipeView> usesOf(Item item) {
        return get().inputs().getOrDefault(item, List.of());
    }

    /** Every crafting-table recipe (shaped or shapeless), for the craftables panel. */
    public static List<RecipeView> craftingViews() {
        return get().crafting();
    }

    public static int totalViews() {
        return get().total();
    }

    private static Index get() {
        Index i = index;
        if (i == null) index = i = build();
        return i;
    }

    /** NEI's "Remove" presets: see {@link RecipeRemoval#keep}. */
    private static boolean keepRecipe(RecipeView view, Set<String> removed) {
        final List<List<String>> in = new ArrayList<>();
        final List<List<String>> out = new ArrayList<>();
        for (SlotView slot : view.slots()) {
            final List<String> ids = new ArrayList<>();
            for (ItemStack option : slot.options()) ids.add(BuiltInRegistries.ITEM.getKey(option.getItem()).toString());
            (slot.output() ? out : in).add(ids);
        }
        return RecipeRemoval.keep(in, out, removed);
    }

    /** A view with outputs only: a modded machine's display whose inputs the converter could not read. */
    private static boolean resultOnly(RecipeView view) {
        for (SlotView slot : view.slots()) if (!slot.output()) return false;
        return true;
    }

    private static Index build() {
        final Map<Item, List<RecipeView>> outputs = new HashMap<>();
        final Map<Item, List<RecipeView>> inputs = new HashMap<>();
        final List<RecipeView> crafting = new ArrayList<>();
        int total = 0;

        final var level = Minecraft.getInstance().level;
        if (level == null) return new Index(outputs, inputs, 0, crafting);

        final Set<String> removedIds = Presets.removedIds();
        final ContextMap context = SlotDisplayContext.fromLevel(level);

        for (RecipeHolder<?> holder : ClientRecipes.all()) {
            final List<RecipeDisplay> displays;
            try {
                displays = holder.value().display();
            } catch (RuntimeException e) {
                continue;
            }

            for (RecipeDisplay display : displays) {
                try {
                    final RecipeView view = RecipeConverter.convert(display, context);
                    if (view == null) continue;
                    if (!removedIds.isEmpty() && !keepRecipe(view, removedIds)) continue;
                    total++;
                    if (view.crafting() != RecipeView.Crafting.NONE) crafting.add(view);

                    for (SlotView slot : view.slots()) {
                        final Map<Item, List<RecipeView>> target = slot.output() ? outputs : inputs;
                        if (!slot.output() && !slot.indexed()) continue;

                        for (ItemStack option : slot.options()) {
                            final List<RecipeView> list = target.computeIfAbsent(option.getItem(), k -> new ArrayList<>());
                            // the same recipe is added once per matching slot; consecutive duplicates are the same view
                            if (list.isEmpty() || list.get(list.size() - 1) != view) list.add(view);
                        }
                    }
                } catch (RuntimeException e) {
                    NotEnoughItems.LOGGER.debug("Skipping unreadable recipe display: {}", e.toString());
                }
            }
        }

        // Recipes from other sources (JEI bridge). Where a source exists, vanilla displays of modded machines that only
        // know their result (RecipeConverter cannot read their inputs) are dropped: the source has the full recipe.
        final List<RecipeView> external = RecipeSources.all();
        if (!external.isEmpty()) {
            if (RecipeSources.anyComplete()) {
                for (Map<Item, List<RecipeView>> map : List.of(outputs, inputs)) {
                    for (List<RecipeView> views : map.values()) views.removeIf(RecipeIndex::resultOnly);
                }
            }
            for (RecipeView view : external) {
                if (!removedIds.isEmpty() && !keepRecipe(view, removedIds)) continue;
                total++;
                for (SlotView slot : view.slots()) {
                    if (!slot.output() && !slot.indexed()) continue;
                    final Map<Item, List<RecipeView>> target = slot.output() ? outputs : inputs;
                    for (ItemStack option : slot.options()) {
                        final List<RecipeView> list = target.computeIfAbsent(option.getItem(), k -> new ArrayList<>());
                        if (list.isEmpty() || list.get(list.size() - 1) != view) list.add(view);
                    }
                }
                // As in NEI, the "uses" of a machine are the recipes it can make
                final List<ItemStack> allStations = new ArrayList<>(view.stations());
                allStations.addAll(codechicken.nei.api.API.catalystsFor(view.title()));
                for (ItemStack station : allStations) {
                    final List<RecipeView> list = inputs.computeIfAbsent(station.getItem(), k -> new ArrayList<>());
                    if (list.isEmpty() || list.get(list.size() - 1) != view) list.add(view);
                }
            }
        }

        NotEnoughItems.LOGGER.info("NEI indexed {} recipe displays; fluids with recipes: {}, with uses: {}", total,
                outputs.keySet().stream().filter(codechicken.nei.item.FluidDisplay::isFluidItem).count(),
                inputs.keySet().stream().filter(codechicken.nei.item.FluidDisplay::isFluidItem).count());
        return new Index(outputs, inputs, total, crafting);
    }
}
