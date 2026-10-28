package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import codechicken.nei.recipe.RecipeView.SlotView;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;

/**
 * Turns a vanilla {@link RecipeDisplay} into a {@link RecipeView}.
 *
 * Only the five vanilla display shapes know their inputs. Any other display (modded machines) still shows its result
 * and station, via the methods every display has, but no ingredients.
 */
final class RecipeConverter {

    private static final int OUT_X = 5;
    private static final int OUT_Y = 1;
    private static final int MAX_OPTIONS = 100;

    private RecipeConverter() {}

    /** Returns null if the recipe has no displayable result. */
    static RecipeView convert(RecipeDisplay display, ContextMap context) {
        final List<SlotView> slots = new ArrayList<>();
        String info = "";
        String fixedTitle = null;
        RecipeView.Crafting crafting = RecipeView.Crafting.NONE;

        if (display instanceof ShapedCraftingRecipeDisplay shaped) {
            fixedTitle = "Shaped Crafting";
            crafting = RecipeView.Crafting.SHAPED;
            final int w = Math.max(1, shaped.width());
            for (int i = 0; i < shaped.ingredients().size(); i++) {
                add(slots, i % w, i / w, shaped.ingredients().get(i), context, false, true);
            }
        } else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
            fixedTitle = "Shapeless Crafting";
            crafting = RecipeView.Crafting.SHAPELESS;
            for (int i = 0; i < Math.min(9, shapeless.ingredients().size()); i++) {
                add(slots, i % 3, i / 3, shapeless.ingredients().get(i), context, false, true);
            }
        } else if (display instanceof FurnaceRecipeDisplay furnace) {
            add(slots, 1, 0, furnace.ingredient(), context, false, true);
            add(slots, 1, 2, furnace.fuel(), context, false, false);
            info = String.format(Locale.ROOT, "%.1fs, %.2f xp", furnace.duration() / 20f, furnace.experience());
        } else if (display instanceof StonecutterRecipeDisplay cutter) {
            fixedTitle = "Stonecutting";
            add(slots, 1, 1, cutter.input(), context, false, true);
        } else if (display instanceof SmithingRecipeDisplay smithing) {
            fixedTitle = "Smithing";
            add(slots, 0, 1, smithing.template(), context, false, true);
            add(slots, 1, 1, smithing.base(), context, false, true);
            add(slots, 2, 1, smithing.addition(), context, false, true);
        }

        if (!add(slots, OUT_X, OUT_Y, display.result(), context, true, true)) return null;

        final List<ItemStack> stations = resolve(display.craftingStation(), context);
        final ItemStack station = stations.isEmpty() ? ItemStack.EMPTY : stations.get(0);
        // Furnace-style displays cover smelting, blasting, smoking and campfires, so they are named by their station.
        final String stationName = station.isEmpty() ? null : station.getHoverName().getString();
        final String title = fixedTitle != null ? fixedTitle : stationName != null ? stationName : "Recipe";

        return new RecipeView(title, station, List.copyOf(slots), info, crafting);
    }

    private static boolean add(List<SlotView> slots, int gx, int gy, SlotDisplay display, ContextMap context,
            boolean output, boolean indexed) {
        final List<ItemStack> options = resolve(display, context);
        if (options.isEmpty()) return false;
        slots.add(new SlotView(gx, gy, options, output, indexed));
        return true;
    }

    private static List<ItemStack> resolve(SlotDisplay display, ContextMap context) {
        final List<ItemStack> raw = display.resolveForStacks(context);
        final List<ItemStack> out = new ArrayList<>(Math.min(raw.size(), MAX_OPTIONS));
        for (ItemStack stack : raw) {
            if (!stack.isEmpty()) out.add(stack);
            if (out.size() >= MAX_OPTIONS) break;
        }
        return out;
    }
}
