package codechicken.nei.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import codechicken.nei.NotEnoughItems;
import codechicken.nei.recipe.RecipeSources;
import codechicken.nei.recipe.RecipeView;
import codechicken.nei.recipe.RecipeView.SlotView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * The villager trade tab of the recipe window: "Villager Trade" recipes (one or two payment items, the result) for the
 * items of villager trades.
 *
 * NEI itself has no such tab (modpacks add it with an addon), and this Minecraft version has no trade list the client can
 * read before a villager is opened (trades are rolled per villager). So the tab learns: every trade of a villager you open
 * is added to the recipe lookups (R / U on an emerald, a book ...) for the rest of the session. The profession (the title
 * of the trading screen) is part of the tab's name.
 */
final class VillagerTrades {

    private static final String SOURCE = "villager_trades";
    /** Trade key to its recipe, in the order they were learned. */
    private static final Map<String, RecipeView> LEARNED = new LinkedHashMap<>();

    private VillagerTrades() {}
    public static void onTick() {
        if (!(Minecraft.getInstance().gui.screen() instanceof MerchantScreen screen)) return;
        final String profession = screen.getTitle().getString();

        boolean added = false;
        for (MerchantOffer offer : screen.getMenu().getOffers()) {
            final ItemStack a = offer.getBaseCostA();
            final ItemStack b = offer.getCostB();
            final ItemStack result = offer.getResult();
            if (a.isEmpty() || result.isEmpty()) continue;

            final String key = profession + "|" + keyOf(a) + "|" + keyOf(b) + "|" + keyOf(result);
            if (LEARNED.containsKey(key)) continue;

            final List<SlotView> slots = new ArrayList<>();
            slots.add(new SlotView(1, 1, List.of(a.copy()), false, true));
            if (!b.isEmpty()) slots.add(new SlotView(2, 1, List.of(b.copy()), false, true));
            slots.add(new SlotView(5, 1, List.of(result.copy()), true, true));
            final String title = profession.isEmpty() ? "Villager Trade" : "Villager Trade (" + profession + ")";
            LEARNED.put(key, new RecipeView(title, new ItemStack(Items.EMERALD), List.copyOf(slots), "", RecipeView.Crafting.NONE));
            added = true;
        }

        if (added) {
            final List<RecipeView> snapshot = List.copyOf(LEARNED.values());
            RecipeSources.set(SOURCE, () -> snapshot); // makes the recipe index rebuild
        }
    }

    private static String keyOf(ItemStack stack) {
        if (stack.isEmpty()) return "-";
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + "x" + stack.getCount() + "#" + stack.getComponentsPatch().hashCode();
    }
}
