package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import codechicken.nei.recipe.RecipeView.SlotView;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

/**
 * Turns a vanilla recipe into a {@link RecipeView}.
 *
 * Only the five vanilla shapes (crafting shaped / shapeless, smelting-style cooking, stonecutting, smithing) are read;
 * a modded machine's own recipe type is left to the JEI bridge or a mod's {@code API.registerRecipeSource}.
 */
final class RecipeConverter {

    private static final int OUT_X = 5;
    private static final int OUT_Y = 1;
    private static final int MAX_OPTIONS = 100;

    private RecipeConverter() {}

    /** Returns null if the recipe is not one of the readable shapes or has no displayable result. */
    static RecipeView convert(Recipe<?> recipe, RegistryAccess access) {
        final List<SlotView> slots = new ArrayList<>();
        String info = "";
        String fixedTitle = null;
        RecipeView.Crafting crafting = RecipeView.Crafting.NONE;

        if (recipe instanceof ShapedRecipe shaped) {
            fixedTitle = "Shaped Crafting";
            crafting = RecipeView.Crafting.SHAPED;
            final int w = Math.max(1, shaped.getWidth());
            final List<Ingredient> in = shaped.getIngredients();
            for (int i = 0; i < in.size(); i++) add(slots, i % w, i / w, in.get(i), false, true);
        } else if (recipe instanceof ShapelessRecipe shapeless) {
            fixedTitle = "Shapeless Crafting";
            crafting = RecipeView.Crafting.SHAPELESS;
            final List<Ingredient> in = shapeless.getIngredients();
            for (int i = 0; i < Math.min(9, in.size()); i++) add(slots, i % 3, i / 3, in.get(i), false, true);
        } else if (recipe instanceof AbstractCookingRecipe cooking) {
            if (!cooking.getIngredients().isEmpty()) add(slots, 1, 0, cooking.getIngredients().get(0), false, true);
            if (!(cooking instanceof CampfireCookingRecipe)) {
                final List<ItemStack> fuels = new ArrayList<>();
                for (var fuel : AbstractFurnaceBlockEntity.getFuel().keySet()) {
                    fuels.add(new ItemStack(fuel));
                    if (fuels.size() >= MAX_OPTIONS) break;
                }
                if (!fuels.isEmpty()) slots.add(new SlotView(1, 2, fuels, false, false)); // fuels are not "uses" of a recipe
            }
            info = String.format(Locale.ROOT, "%.1fs, %.2f xp", cooking.getCookingTime() / 20f, cooking.getExperience());
        } else if (recipe instanceof StonecutterRecipe cutter) {
            fixedTitle = "Stonecutting";
            if (!cutter.getIngredients().isEmpty()) add(slots, 1, 1, cutter.getIngredients().get(0), false, true);
        } else if (recipe instanceof SmithingRecipe smithing) {
            // 1.20.1 hides the three slots; the recipe only answers whether a stack fits (the slots are the matching items)
            fixedTitle = "Smithing";
            addMatching(slots, 0, 1, smithing::isTemplateIngredient);
            addMatching(slots, 1, 1, smithing::isBaseIngredient);
            addMatching(slots, 2, 1, smithing::isAdditionIngredient);
        } else {
            return null;
        }

        final ItemStack result = recipe.getResultItem(access);
        if (result == null || result.isEmpty()) return null;
        slots.add(new SlotView(OUT_X, OUT_Y, List.of(result.copy()), true, true));

        final ItemStack station = recipe.getToastSymbol();
        // Cooking recipes cover smelting, blasting, smoking and campfires, so they are named by their station.
        final String stationName = station.isEmpty() ? null : station.getHoverName().getString();
        final String title = fixedTitle != null ? fixedTitle : stationName != null ? stationName : "Recipe";

        return new RecipeView(title, station, List.copyOf(slots), info, crafting);
    }

    private static void addMatching(List<SlotView> slots, int gx, int gy, Predicate<ItemStack> fits) {
        final List<ItemStack> options = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            final ItemStack stack = new ItemStack(item);
            if (fits.test(stack)) options.add(stack);
            if (options.size() >= MAX_OPTIONS) break;
        }
        if (!options.isEmpty()) slots.add(new SlotView(gx, gy, options, false, true));
    }

    private static boolean add(List<SlotView> slots, int gx, int gy, Ingredient ingredient, boolean output, boolean indexed) {
        if (ingredient == null || ingredient.isEmpty()) return false;
        final List<ItemStack> options = new ArrayList<>();
        for (ItemStack stack : ingredient.getItems()) {
            if (!stack.isEmpty()) options.add(stack);
            if (options.size() >= MAX_OPTIONS) break;
        }
        if (options.isEmpty()) return false;
        slots.add(new SlotView(gx, gy, options, output, indexed));
        return true;
    }
}
