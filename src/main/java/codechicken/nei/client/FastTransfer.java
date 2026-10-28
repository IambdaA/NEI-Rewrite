package codechicken.nei.client;

import java.util.ArrayList;
import java.util.List;

import codechicken.nei.NEIConfig;
import codechicken.nei.item.SlotZones;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * NEI's mouse wheel transfer ({@code FastTransferManager#transferItem} and {@code #retrieveItem}): over a slot with
 * items, wheel down moves one item to the place a Shift + click would send it, wheel up pulls one item of that kind into
 * the slot from the other zones (chest, inventory, hotbar). The wheel directions swap with the "invert" option.
 *
 * Moving one item is the same three clicks NEI uses (pick up the stack, right click one onto the target, put the rest
 * back), so the server sees ordinary clicks. The destination of a Shift + click is found by letting the client menu
 * play it and putting everything back afterwards, as NEI does.
 *
 * Also here (stage 38): NEI's Shift + click mass transfer (Shift + click an empty slot while holding a stack: every stack of
 * that kind in the zone the item came from moves into the zone you clicked) and the throw-all (Shift + click outside the
 * window, or Shift + the drop key over a slot: every stack of that kind in that zone is thrown). NEI does these in its
 * slot click hook; here the mouse press and key press events do the clicking themselves and cancel the vanilla click.
 */
final class FastTransfer {

    private FastTransfer() {}

    /** @return true if the wheel was used (also when nothing could be moved), so the event is not passed on */
    static boolean wheel(AbstractContainerScreen<?> screen, double dy) {
        if (!NEIConfig.WHEEL_TRANSFER.getAsBoolean() || screen instanceof CreativeModeInventoryScreen) return false;
        final Slot slot = screen.getSlotUnderMouse();
        final Minecraft mc = Minecraft.getInstance();
        if (slot == null || !slot.hasItem() || mc.player == null || mc.gameMode == null) return false;

        final AbstractContainerMenu menu = screen.getMenu();
        if (!menu.getCarried().isEmpty()) return false; // shortcut: NEI does not check, but the clicks below need an empty cursor

        final boolean out = (dy < 0) != NEIConfig.WHEEL_TRANSFER_INVERT.getAsBoolean(); // default: wheel down moves out
        final int index = menu.slots.indexOf(slot);
        if (index < 0) return false;
        if (out) transferItem(menu, index, mc.player);
        else retrieveItem(menu, index, mc.player);
        return true;
    }

    // ---- Shift + click mass transfer and throw-all (NEI's NEIController#handleSlotClick / lastKeyTyped) ----

    /** NEI's {@code pickedUpFromSlot}: the slot the cursor stack was last picked up from. */
    private static int pickedUp = -1;
    private static ItemStack heldBefore;

    /** NEI's {@code beforeSlotClick}: remembers the cursor stack before a click goes to the menu. */
    static void before(AbstractContainerMenu menu) {
        heldBefore = menu.getCarried().copy();
    }

    /** NEI's {@code afterSlotClick}: if the cursor stack changed, the click was a pick up (or a put down) at that slot. */
    static void after(AbstractContainerMenu menu, int slotIndex) {
        if (heldBefore == null) return;
        final ItemStack now = menu.getCarried();
        if (heldBefore.isEmpty() != now.isEmpty() || (!now.isEmpty() && !ItemStack.isSameItemSameComponents(heldBefore, now))) {
            pickedUp = slotIndex;
        }
        heldBefore = null;
    }

    /**
     * NEI's Shift handling in {@code handleSlotClick} (middle button excluded, creative screens left alone):
     * Shift + click on an empty slot with a stack on the cursor puts the stack down and mass transfers; Shift + click
     * outside the window (left button) throws everything of that kind.
     *
     * @return true if the click was handled (the vanilla click must not run)
     */
    static boolean shiftClick(AbstractContainerScreen<?> screen, double mx, double my, int button) {
        if (button == 2 || screen instanceof CreativeModeInventoryScreen) return false;
        final Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return false;
        final AbstractContainerMenu menu = screen.getMenu();
        final ItemStack held = menu.getCarried().copy();
        if (held.isEmpty()) return false;

        final Slot slot = screen.getSlotUnderMouse();
        if (slot != null) {
            if (slot.hasItem()) return false;
            final int index = menu.slots.indexOf(slot);
            if (index < 0) return false;
            final int from = pickedUp;
            click(menu, index, button);
            if (slot.mayPlace(held)) massTransfer(menu, from, index, held);
            heldBefore = held;
            after(menu, index); // NEI's afterSlotClick ran after all of this
            return true;
        }

        final boolean outside = mx < screen.getGuiLeft() || my < screen.getGuiTop()
                || mx >= screen.getGuiLeft() + screen.getXSize() || my >= screen.getGuiTop() + screen.getYSize();
        if (outside && button == 0) {
            throwAll(menu, pickedUp);
            return true;
        }
        return false;
    }

    /** NEI's {@code lastKeyTyped}: Shift + the drop key over a slot throws every stack of its kind in its zone. */
    static boolean shiftDrop(AbstractContainerScreen<?> screen) {
        if (screen instanceof CreativeModeInventoryScreen) return false;
        final Slot slot = screen.getSlotUnderMouse();
        final Minecraft mc = Minecraft.getInstance();
        if (slot == null || !slot.hasItem() || mc.player == null || mc.gameMode == null) return false;
        final AbstractContainerMenu menu = screen.getMenu();
        if (!menu.getCarried().isEmpty()) return false; // shortcut: NEI clicks anyway, which swaps the stacks
        final int index = menu.slots.indexOf(slot);
        if (index < 0) return false;
        click(menu, index, 0);
        throwAll(menu, index);
        click(menu, index, 0);
        return true;
    }

    /** NEI's {@code performMassTransfer}. */
    private static void massTransfer(AbstractContainerMenu menu, int fromSlot, int toSlot, ItemStack held) {
        final List<List<Integer>> zones = zones(menu, held);
        final int fromZone = SlotZones.zoneOf(zones, fromSlot);
        final int toZone = SlotZones.zoneOf(zones, toSlot);
        if (fromZone == -1 || toZone == -1 || fromZone == toZone) return;

        final ItemStack now = menu.getCarried();
        if (!now.isEmpty() && !same(held, now)) return;
        if (!fillZone(menu, zones.get(toZone))) return;

        for (int from : zones.get(fromZone)) {
            if (!same(held, menu.slots.get(from).getItem())) continue;
            click(menu, from, 0);
            if (!fillZone(menu, zones.get(toZone))) {
                click(menu, from, 0); // no room left: put it back
                return;
            }
        }
    }

    /** NEI's {@code fillZoneWithHeldItem}: stacks of the same kind first, then empty slots. True when the cursor is empty. */
    private static boolean fillZone(AbstractContainerMenu menu, List<Integer> zone) {
        for (int i : zone) {
            final ItemStack held = menu.getCarried();
            if (held.isEmpty()) break;
            if (same(menu.slots.get(i).getItem(), held)) click(menu, i, 0);
        }
        for (int i : zone) {
            if (menu.getCarried().isEmpty()) break;
            if (!menu.slots.get(i).hasItem()) click(menu, i, 0);
        }
        return menu.getCarried().isEmpty();
    }

    /** NEI's {@code throwAll}: throws the cursor stack, then every stack of its kind in the zone it was picked up from. */
    private static void throwAll(AbstractContainerMenu menu, int pickedFrom) {
        final ItemStack held = menu.getCarried().copy();
        if (held.isEmpty()) return;
        click(menu, -999, 0); // outside the window: drops the cursor stack

        final List<List<Integer>> zones = zones(menu, held);
        final int zone = SlotZones.zoneOf(zones, pickedFrom);
        if (zone == -1) return; // cannot tell where it came from
        for (int i : zones.get(zone)) {
            if (!same(held, menu.slots.get(i).getItem())) continue;
            click(menu, i, 0);
            click(menu, -999, 0);
        }
    }

    // ---- zones ----

    private static List<List<Integer>> zones(AbstractContainerMenu menu, ItemStack stack) {
        final List<SlotZones.Pos> positions = new ArrayList<>();
        for (Slot s : menu.slots) positions.add(new SlotZones.Pos(s.x, s.y));
        final ItemStack one = stack.copyWithCount(1);
        return SlotZones.zones(positions, i -> menu.slots.get(i).mayPlace(one));
    }

    // ---- clicks ----

    static void click(AbstractContainerMenu menu, int slot, int button) {
        click(menu, slot, button, ContainerInput.PICKUP);
    }

    static void click(AbstractContainerMenu menu, int slot, int button, ContainerInput input) {
        final Minecraft mc = Minecraft.getInstance();
        mc.gameMode.handleContainerInput(menu.containerId, slot, button, input, mc.player);
    }

    /**
     * NEI's {@code transferItems}: moves {@code count} items of the slot to where a Shift + click sends them. A whole
     * stack is one Shift + click (NEI moves them one by one, with the same result and far fewer packets).
     */
    static void transferItems(AbstractContainerMenu menu, int from, int count, Player player) {
        if (count >= menu.slots.get(from).getItem().getCount()) {
            click(menu, from, 0, ContainerInput.QUICK_MOVE);
            return;
        }
        for (int i = 0; i < count && menu.slots.get(from).hasItem(); i++) transferItem(menu, from, player);
    }

    /**
     * NEI's {@code dropHeldItem}: puts the item on the cursor into a free inventory slot so the cursor is empty.
     * Returns that slot (to pick the item up again afterwards), or -1.
     */
    static int dropHeld(AbstractContainerMenu menu) {
        final ItemStack held = menu.getCarried();
        if (held.isEmpty()) return -1;
        for (int i = Math.max(0, menu.slots.size() - 36); i < menu.slots.size(); i++) {
            final Slot slot = menu.slots.get(i);
            if (slot.hasItem() || !slot.mayPlace(held)) continue;
            click(menu, i, 0);
            if (menu.getCarried().isEmpty()) return i;
        }
        return -1;
    }

    private static void moveOneItem(AbstractContainerMenu menu, int from, int to) {
        click(menu, from, 0); // pick up the stack
        click(menu, to, 1); // put one down
        click(menu, from, 0); // put the rest back
    }

    private static void moveOutputSet(AbstractContainerMenu menu, int from, int to) {
        if (!menu.getCarried().isEmpty()) return;
        click(menu, from, 0);
        if (menu.getCarried().isEmpty()) return; // the container moved it by itself
        click(menu, to, 0);
    }

    // ---- NEI's transferItem / retrieveItem ----

    private static void transferItem(AbstractContainerMenu menu, int from, Player player) {
        final int to = findShiftClickDestination(menu, from, player);
        if (to == -1) return;
        final Slot source = menu.slots.get(from);
        if (source.mayPlace(source.getItem())) moveOneItem(menu, from, to);
        else moveOutputSet(menu, from, to); // slots you cannot put things in: the whole result moves
    }

    private static void retrieveItem(AbstractContainerMenu menu, int to, Player player) {
        final Slot target = menu.slots.get(to);
        final ItemStack stack = target.getItem();
        if (stack.isEmpty() || stack.getCount() >= target.getMaxStackSize() || stack.getCount() >= stack.getMaxStackSize()) return;

        final List<List<Integer>> zones = zones(menu, stack);
        final int destZone = SlotZones.zoneOf(zones, to);
        if (destZone == -1) return; // a slot that does not accept the item

        int firstZone = -1;
        final int firstSlot = findShiftClickDestination(menu, to, player);
        if (firstSlot != -1) {
            firstZone = SlotZones.zoneOf(zones, firstSlot);
            if (firstZone != -1 && retrieveFromZone(menu, zones.get(firstZone), to)) return;
        }
        for (int z = 0; z < zones.size(); z++) {
            if (z == destZone || z == firstZone) continue;
            if (retrieveFromZone(menu, zones.get(z), to)) return;
        }
        retrieveFromZone(menu, zones.get(destZone), to);
    }

    private static boolean retrieveFromZone(AbstractContainerMenu menu, List<Integer> zone, int to) {
        final ItemStack wanted = menu.slots.get(to).getItem();
        // first pass: stacks that are not full, so full stacks stay whole
        for (int i : zone) {
            if (i == to) continue;
            final Slot slot = menu.slots.get(i);
            final ItemStack other = slot.getItem();
            if (same(wanted, other) && other.getCount() != slot.getMaxStackSize() && other.getCount() != other.getMaxStackSize()) {
                moveOneItem(menu, i, to);
                return true;
            }
        }
        for (int i : zone) {
            if (i != to && same(wanted, menu.slots.get(i).getItem())) {
                moveOneItem(menu, i, to);
                return true;
            }
        }
        return false;
    }

    private static boolean same(ItemStack a, ItemStack b) {
        return !a.isEmpty() && !b.isEmpty() && ItemStack.isSameItemSameComponents(a, b);
    }

    /** The slot one item of the source slot ends up in after a Shift + click, or -1. NEI's findShiftClickDestinationSlot. */
    private static int findShiftClickDestination(AbstractContainerMenu menu, int from, Player player) {
        final Slot source = menu.slots.get(from);
        final ItemStack stack = source.getItem();
        if (stack.isEmpty()) return -1;

        final List<ItemStack> saved = snapshot(menu);
        try {
            source.set(stack.copyWithCount(1));
            final List<ItemStack> before = snapshot(menu);
            menu.clicked(from, 0, ContainerInput.QUICK_MOVE, player);
            final List<ItemStack> after = snapshot(menu);

            for (int i = 0; i < before.size(); i++) {
                if (i == from) continue;
                final ItemStack b = before.get(i);
                final ItemStack a = after.get(i);
                if (a.isEmpty() || ItemStack.matches(a, b)) continue;
                if (b.isEmpty() ? same(stack, a) : same(stack, a) && a.getCount() > b.getCount()) return i;
            }
            return -1;
        } finally {
            for (int i = 0; i < saved.size(); i++) menu.slots.get(i).set(saved.get(i));
            menu.setCarried(ItemStack.EMPTY);
        }
    }

    private static List<ItemStack> snapshot(AbstractContainerMenu menu) {
        final List<ItemStack> list = new ArrayList<>();
        for (Slot s : menu.slots) list.add(s.getItem().copy());
        return list;
    }
}
