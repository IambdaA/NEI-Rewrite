package codechicken.nei.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import codechicken.nei.recipe.RecipeView;
import codechicken.nei.recipe.RecipeView.SlotView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Fills the open crafting grid from the player's inventory, like NEI's "+" button.
 *
 * It works by simulating the clicks a player would make (clear the grid with shift-click, then for each ingredient
 * pick up a stack, right-click one item into the grid slot, and put the rest back), so the server sees ordinary
 * container clicks and no custom networking is needed. The ingredients do not have to be in the recipe book.
 *
 * Supported menus: the crafting table and the 2x2 grid in the player inventory.
 */
final class RecipeTransfer {

    /**
     * @param placed  how many ingredient slots were filled
     * @param sets    how many sets of the ingredients went in
     * @param missing indexes (into {@link RecipeView#slots()}) of ingredients the player does not have
     * @param error   non-null if nothing was done, with the reason
     */
    record Result(int placed, int sets, Set<Integer> missing, String error) {}

    private record Grid(int firstSlot, int width, int height) {}

    private RecipeTransfer() {}

    private static Grid gridOf(AbstractContainerMenu menu) {
        // Both menus put the result in slot 0 and the grid directly after it.
        if (menu instanceof CraftingMenu) return new Grid(1, 3, 3);
        if (menu instanceof InventoryMenu) return new Grid(1, 2, 2);
        return null;
    }

    /** The grid cell (column, row) an ingredient goes into, or null if the recipe does not fit the grid. */
    private static int[] target(RecipeView view, SlotView slot, int ordinal, Grid grid) {
        if (view.crafting() == RecipeView.Crafting.SHAPELESS) {
            if (ordinal >= grid.width() * grid.height()) return null;
            return new int[] { ordinal % grid.width(), ordinal / grid.width() };
        }
        if (slot.gx() >= grid.width() || slot.gy() >= grid.height()) return null;
        return new int[] { slot.gx(), slot.gy() };
    }

    /** True if the screen has a crafting grid the "+" button (and the craftables panel) can use. */
    static boolean hasGrid(AbstractContainerScreen<?> screen) {
        return gridOf(screen.getMenu()) != null;
    }

    /**
     * NEI's {@code NEIClientUtils.presenceOverlay}: for each of the view's slots, whether the screen's slots hold an item for
     * it. Every ingredient takes one item out of the pool, so a recipe needing three plates needs three. Output slots and
     * slots the player cannot take from do not count; the result is null for screens without a crafting grid (an AE2
     * terminal's items are not in its slots, so "missing" would be wrong there).
     */
    static boolean[] presence(AbstractContainerScreen<?> screen, RecipeView view) {
        if (gridOf(screen.getMenu()) == null) return null;
        final Player player = Minecraft.getInstance().player;
        if (player == null) return null;

        final List<ItemStack> pool = new ArrayList<>();
        for (Slot s : screen.getMenu().slots) {
            final ItemStack st = s.getItem();
            if (!st.isEmpty() && s.mayPlace(st) && s.mayPickup(player)) pool.add(st.copy());
        }

        final boolean[] found = new boolean[view.slots().size()];
        for (int i = 0; i < found.length; i++) {
            final SlotView slot = view.slots().get(i);
            if (slot.output()) continue;
            for (ItemStack have : pool) {
                if (have.getCount() <= 0) continue;
                boolean match = false;
                for (ItemStack option : slot.options()) {
                    if (ItemStack.isSameItem(option, have)) {
                        match = true;
                        break;
                    }
                }
                if (match) {
                    have.shrink(1);
                    found[i] = true;
                    break;
                }
            }
        }
        return found;
    }

    /** True if the screen is a crafting grid and the recipe fits it. Cheap enough to call every frame. */
    static boolean canTransfer(AbstractContainerScreen<?> screen, RecipeView view) {
        if (view.crafting() == RecipeView.Crafting.NONE) return false;
        final Grid grid = gridOf(screen.getMenu());
        if (grid == null) return false;

        int ordinal = 0;
        for (SlotView slot : view.slots()) {
            if (slot.output()) continue;
            if (target(view, slot, ordinal++, grid) == null) return false;
        }
        return true;
    }

    /**
     * NEI's ghost items ({@code OverlayHandler.renderObjects}): what the "+" would put into the grid, drawn faded over the grid's
     * slots while the mouse is on the button. {@code pick} chooses the shown option of each ingredient.
     */
    static void drawGhosts(net.minecraft.client.gui.GuiGraphicsExtractor g, AbstractContainerScreen<?> screen, RecipeView view,
            java.util.function.Function<SlotView, ItemStack> pick) {
        final AbstractContainerMenu menu = screen.getMenu();
        final Grid grid = gridOf(menu);
        if (grid == null || !canTransfer(screen, view)) return;

        int ordinal = 0;
        for (SlotView slot : view.slots()) {
            if (slot.output()) continue;
            final int[] cell = target(view, slot, ordinal++, grid);
            final Slot dest = menu.slots.get(grid.firstSlot() + cell[1] * grid.width() + cell[0]);
            final int x = screen.leftPos + dest.x;
            final int y = screen.topPos + dest.y;
            Draw.stack(g, pick.apply(slot), x, y);
            g.fill(x, y, x + 16, y + 16, 0x908B8B8B); // the veil that makes it a ghost
        }
    }

    private static void click(AbstractContainerMenu menu, int slot, int button, ContainerInput input) {
        final Minecraft mc = Minecraft.getInstance();
        final Player player = mc.player;
        mc.gameMode.handleContainerInput(menu.containerId, slot, button, input, player);
    }

    static Result transfer(AbstractContainerScreen<?> screen, RecipeView view) {
        return transfer(screen, view, 1);
    }

    /** @param maxSets how many sets of the ingredients to place at most (Integer.MAX_VALUE: as many as possible) */
    static Result transfer(AbstractContainerScreen<?> screen, RecipeView view, int maxSets) {
        final AbstractContainerMenu menu = screen.getMenu();
        final Grid grid = gridOf(menu);
        final Minecraft mc = Minecraft.getInstance();
        if (grid == null || mc.gameMode == null || mc.player == null || !canTransfer(screen, view)) {
            return new Result(0, 0, Set.of(), "This recipe does not fit here");
        }
        if (!menu.getCarried().isEmpty()) {
            return new Result(0, 0, Set.of(), "Put down the item on your cursor first");
        }

        // 1. empty the grid
        if (!clearGrid(screen)) return new Result(0, 0, Set.of(), "Inventory is full, could not clear the crafting grid");

        // 2. plan: for each ingredient, the inventory slot to take one item from. With maxSets > 1 (NEI's maxTransfer,
        // Shift + click on the "+") whole sets are planned one after another while every ingredient is still available
        // and no grid cell would hold more than a stack.
        final List<Integer> sources = new ArrayList<>(); // menu slot indexes holding items we may take
        int[] remaining = new int[menu.slots.size()];
        for (int i = 0; i < menu.slots.size(); i++) {
            final Slot s = menu.slots.get(i);
            // main inventory and hotbar are container slots 0..35
            if (s.container == mc.player.getInventory() && s.getContainerSlot() < 36 && !s.getItem().isEmpty()) {
                sources.add(i);
                remaining[i] = s.getItem().getCount();
            }
        }

        Set<Integer> missing = new HashSet<>();
        final Map<Long, Integer> moves = new LinkedHashMap<>(); // (source << 32 | destination) -> how many items
        final int[] inCell = new int[menu.slots.size()];
        final net.minecraft.world.item.Item[] cellItem = new net.minecraft.world.item.Item[menu.slots.size()];

        int sets = 0;
        for (int set = 0; set < maxSets; set++) {
            final int[] left = remaining.clone();
            final Set<Integer> setMissing = new HashSet<>();
            final List<int[]> setMoves = new ArrayList<>(); // {source menu slot, destination menu slot}
            boolean fits = true;
            int ordinal = 0;

            for (int viewIndex = 0; viewIndex < view.slots().size(); viewIndex++) {
                final SlotView slot = view.slots().get(viewIndex);
                if (slot.output()) continue;

                final int[] cell = target(view, slot, ordinal++, grid);
                final int dest = grid.firstSlot() + cell[1] * grid.width() + cell[0];
                int best = -1;
                for (int src : sources) {
                    if (left[src] <= 0 || !matches(slot, menu.slots.get(src).getItem())) continue;
                    // a cell that already got an item (earlier set) only takes more of that item
                    if (cellItem[dest] != null && !menu.slots.get(src).getItem().is(cellItem[dest])) continue;
                    // prefer the smallest stack, so big stacks stay whole
                    if (best < 0 || left[src] < left[best]) best = src;
                }

                if (best < 0) {
                    setMissing.add(viewIndex);
                    continue;
                }
                final int limit = Math.min(menu.slots.get(best).getItem().getMaxStackSize(), menu.slots.get(dest).getMaxStackSize());
                if (inCell[dest] + 1 > limit) fits = false;
                left[best]--;
                setMoves.add(new int[] { best, dest });
            }

            if (set == 0) missing = setMissing; // the first set places what it can and reports the rest
            else if (!setMissing.isEmpty() || !fits) break;
            if (set == 0 && !fits) break; // cannot happen with a stack limit of 1+, kept for safety
            remaining = left;
            sets++;
            for (int[] m : setMoves) {
                inCell[m[1]]++;
                cellItem[m[1]] = menu.slots.get(m[0]).getItem().getItem();
                moves.merge(((long) m[0] << 32) | m[1], 1, Integer::sum);
            }
            if (!missing.isEmpty()) break; // an incomplete set is never repeated
        }

        // 3. do it: pick up the source stack, right-click the wanted number of items into the grid, put the rest back
        for (Map.Entry<Long, Integer> move : moves.entrySet()) {
            final int src = (int) (move.getKey() >> 32);
            final int dest = (int) (long) move.getKey();
            final int count = move.getValue();
            final boolean all = count == menu.slots.get(src).getItem().getCount();
            click(menu, src, 0, ContainerInput.PICKUP);
            if (all) {
                click(menu, dest, 0, ContainerInput.PICKUP); // the whole stack goes in with one click
            } else {
                for (int i = 0; i < count; i++) click(menu, dest, 1, ContainerInput.PICKUP);
            }
            if (!menu.getCarried().isEmpty()) click(menu, src, 0, ContainerInput.PICKUP);
        }

        int filled = 0;
        for (int i = 0; i < inCell.length; i++) if (inCell[i] > 0) filled++;
        return new Result(filled, sets, missing, null);
    }

    /**
     * Shift-clicks everything out of the crafting grid (the client simulates it, so the slots are up to date afterwards).
     *
     * @return false if something stayed in (no room in the inventory)
     */
    static boolean clearGrid(AbstractContainerScreen<?> screen) {
        final AbstractContainerMenu menu = screen.getMenu();
        final Grid grid = gridOf(menu);
        if (grid == null) return true;
        for (int i = 0; i < grid.width() * grid.height(); i++) {
            final int slotIndex = grid.firstSlot() + i;
            if (!menu.slots.get(slotIndex).getItem().isEmpty()) click(menu, slotIndex, 0, ContainerInput.QUICK_MOVE);
        }
        for (int i = 0; i < grid.width() * grid.height(); i++) {
            if (!menu.slots.get(grid.firstSlot() + i).getItem().isEmpty()) return false;
        }
        return true;
    }

    /** Shift-clicks the result slot: crafts as many sets as the grid holds. */
    static void takeResult(AbstractContainerScreen<?> screen) {
        click(screen.getMenu(), 0, 0, ContainerInput.QUICK_MOVE);
    }

    private static boolean matches(SlotView slot, ItemStack candidate) {
        for (ItemStack option : slot.options()) {
            if (candidate.is(option.getItem())) return true;
        }
        return false;
    }
}
