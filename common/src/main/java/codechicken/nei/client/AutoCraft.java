package codechicken.nei.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import codechicken.nei.NEIConfig;
import codechicken.nei.NotEnoughItems;
import codechicken.nei.bookmark.AutoCraftPlan;
import codechicken.nei.bookmark.AutoCraftPlan.Step;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.BookmarkGrid.RecipeItem;
import codechicken.nei.recipe.RecipeView;
import codechicken.nei.recipe.RecipeView.SlotView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * As in NEI: autocrafting. With a crafting grid open (crafting table or the inventory), Shift + the craft key over a
 * bookmarked recipe, a crafting chain group or a recipe of the recipe window crafts the recipes of the chain, bottom up
 * ({@link AutoCraftPlan}): fill the grid, wait for the result, Shift + click it, wait for the items to arrive.
 *
 * Runs on the client tick, because the server tells the client what the grid makes: each step waits for it. NEI does the
 * same on a worker thread. It stops when the screen changes or something is missing, and says why in chat.
 */
final class AutoCraft {

    private enum Phase {
        FILL, WAIT_RESULT, WAIT_ITEMS
    }

    private static final int RESULT_TIMEOUT = 40; // ticks
    private static final int ITEMS_TIMEOUT = 40;

    private static Deque<Step> queue = new ArrayDeque<>();
    private static AbstractContainerScreen<?> screen;
    private static Step step;
    private static RecipeView view;
    private static String resultName;
    private static long left;
    private static int sets;
    private static Phase phase;
    private static int ticks;
    private static int stable;
    private static long itemsBefore;
    private static long itemsTarget;
    private static boolean crafted;

    private AutoCraft() {}

    static boolean enabled() {
        return NEIConfig.AUTOCRAFT.getAsBoolean();
    }

    static boolean running() {
        return screen != null;
    }

    private static void say(String text) {
        final var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.literal(text));
    }

    /** One recipe of the recipe window as the items a bookmark would store (NEI: {@code RecipeChainMath.of(recipe, 1)}). */
    static List<BookmarkGrid.Item> itemsOf(RecipeView view) {
        final String key = RecipeBookmarks.key(view);
        final List<BookmarkGrid.Item> items = new ArrayList<>();
        for (RecipeItem r : RecipeBookmarks.results(view, null)) {
            final BookmarkGrid.Item item = new BookmarkGrid.Item(r.id(), r.count(), 0);
            item.type = 1;
            item.recipeId = key;
            item.factor = r.count();
            items.add(item);
        }
        for (RecipeItem r : RecipeBookmarks.ingredients(view, Map.of())) {
            final BookmarkGrid.Item item = new BookmarkGrid.Item(r.id(), r.count(), 0);
            item.type = 2;
            item.recipeId = key;
            item.factor = r.count();
            item.options = r.options();
            items.add(item);
        }
        return items;
    }

    /** The items of a plain (not chained) bookmarked recipe: what the player set is already in the amounts. */
    static List<BookmarkGrid.Item> itemsOf(BookmarkGrid grid, BookmarkGrid.Item slot) {
        final List<BookmarkGrid.Item> items = new ArrayList<>();
        for (BookmarkGrid.Item i : grid.items) if (i.inRecipe(slot.recipeId, slot.groupId)) items.add(i);
        return items;
    }

    /**
     * @param missingOnly NEI's Shift + Ctrl: only make what the inventory lacks of the wanted items
     * @param focus       only this recipe and what it needs, or null for everything in {@code items}
     * @return true if the key was used (even if it only said why nothing happens)
     */
    static boolean start(List<BookmarkGrid.Item> items, boolean missingOnly, String focus) {
        if (!enabled()) return false;
        final var current = Minecraft.getInstance().screen;
        if (!(current instanceof AbstractContainerScreen<?> s) || !RecipeTransfer.hasGrid(s)) {
            say("Autocrafting needs a crafting grid: open a crafting table or your inventory");
            return true;
        }

        final List<Step> plan = AutoCraftPlan.plan(items, inventory(s.getMenu()), missingOnly, focus);
        if (plan.isEmpty()) {
            say("Nothing to craft");
            return true;
        }

        stop(); // NEI: pressing it again restarts
        queue = new ArrayDeque<>(plan);
        screen = s;
        crafted = false;
        return true;
    }

    private static void stop() {
        screen = null;
        step = null;
        view = null;
        queue = new ArrayDeque<>();
    }

    private static void fail(String reason) {
        say("Autocrafting stopped: " + reason);
        stop();
    }

    private static void finish() {
        final boolean any = crafted;
        stop();
        if (any) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private static String id(ItemStack stack) {
        return codechicken.nei.item.FluidDisplay.idOf(stack.getItem());
    }

    /** What the player carries (main inventory and hotbar) by item id; the grid and the result do not count. */
    private static Map<String, Long> inventory(AbstractContainerMenu menu) {
        final var player = Minecraft.getInstance().player;
        final Map<String, Long> counts = new HashMap<>();
        for (Slot s : menu.slots) {
            if (s.container == player.getInventory() && s.getContainerSlot() < 36 && !s.getItem().isEmpty()) {
                counts.merge(id(s.getItem()), (long) s.getItem().getCount(), Long::sum);
            }
        }
        return counts;
    }

    /** True if the stack is one of the results of the recipe being crafted. */
    private static boolean isResult(ItemStack stack) {
        for (SlotView slot : view.slots()) {
            if (!slot.output()) continue;
            for (ItemStack option : slot.options()) if (stack.is(option.getItem())) return true;
        }
        return false;
    }

    private static SlotView firstResult() {
        for (SlotView slot : view.slots()) if (slot.output() && !slot.options().isEmpty()) return slot;
        return null;
    }

    private static boolean next() {
        step = queue.poll();
        if (step == null) {
            finish();
            return false;
        }
        view = RecipeBookmarks.resolve(step.resultId(), step.recipeId());
        final SlotView result = view == null ? null : firstResult();
        if (result == null) {
            fail("a recipe of the chain is not available any more");
            return false;
        }
        resultName = result.options().get(0).getHoverName().getString();
        if (!RecipeTransfer.canTransfer(screen, view)) {
            fail(resultName + " does not fit this crafting grid");
            return false;
        }
        left = step.runs();
        phase = Phase.FILL;
        return true;
    }

    public static void onTick() {
        if (screen == null) return;
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != screen) { // NEI: any other screen stops it
            stop();
            return;
        }

        try {
            if (step == null && !next()) return;
            final AbstractContainerMenu menu = screen.getMenu();

            switch (phase) {
            case FILL -> {
                if (!menu.getCarried().isEmpty()) {
                    fail("put down the item on your cursor first");
                    return;
                }
                final RecipeTransfer.Result r = RecipeTransfer.transfer(screen, view, (int) Math.min(left, 64));
                if (r.error() != null) {
                    fail(r.error());
                } else if (!r.missing().isEmpty() || r.sets() == 0) {
                    RecipeTransfer.clearGrid(screen);
                    fail("missing ingredients for " + resultName);
                } else {
                    sets = r.sets();
                    phase = Phase.WAIT_RESULT;
                    ticks = 0;
                    stable = 0;
                }
            }
            case WAIT_RESULT -> {
                // the server computes what the grid makes; a half filled grid can show another recipe for a moment
                final ItemStack out = menu.getSlot(0).getItem();
                if (!out.isEmpty() && isResult(out)) {
                    if (++stable >= 2) {
                        final var made = firstResult().options().get(0);
                        itemsBefore = inventory(menu).getOrDefault(id(made), 0L);
                        itemsTarget = itemsBefore + (long) sets * Math.max(1, made.getCount());
                        RecipeTransfer.takeResult(screen);
                        phase = Phase.WAIT_ITEMS;
                        ticks = 0;
                    }
                } else {
                    stable = 0;
                    if (++ticks > RESULT_TIMEOUT) {
                        RecipeTransfer.clearGrid(screen);
                        fail("the crafting grid does not make " + resultName);
                    }
                }
            }
            case WAIT_ITEMS -> {
                // the client guesses one craft of the Shift + click; the server's answer brings the real amount
                final String made = id(firstResult().options().get(0));
                if (inventory(menu).getOrDefault(made, 0L) >= itemsTarget) {
                    crafted = true;
                    left -= sets;
                    RecipeTransfer.clearGrid(screen);
                    if (left > 0) {
                        phase = Phase.FILL;
                    } else {
                        step = null;
                    }
                } else if (++ticks > ITEMS_TIMEOUT) {
                    RecipeTransfer.clearGrid(screen);
                    fail("could not take " + resultName + " (is the inventory full?)");
                }
            }
            }
        } catch (RuntimeException e) {
            NotEnoughItems.LOGGER.warn("Autocrafting failed: {}", e.toString());
            fail(String.valueOf(e.getMessage()));
        }
    }
}
