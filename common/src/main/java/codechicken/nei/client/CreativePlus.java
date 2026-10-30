package codechicken.nei.client;

import java.util.ArrayList;
import java.util.List;


import codechicken.nei.NEIConfig;
import codechicken.nei.NotEnoughItems;
import codechicken.nei.net.PlusInventory;
import codechicken.nei.net.PlusPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.ItemStack;

/**
 * As in NEI: "creative+". The gamemode button cycles survival, creative, creative+. In creative+ the inventory key opens
 * the extended creative inventory (54 slots kept by the server, see {@link PlusInventory}) in place of the creative
 * screen, items clicked in the list go into its slots, and Ctrl + scroll in the world cycles the rows of the extended
 * inventory, the inventory and the hotbar. Needs NEI Rewrite on the server; without it everything stays vanilla.
 */
public final class CreativePlus {


    private CreativePlus() {}

    /** Whether the server has the mod's packets. */
    static boolean available() {
        final var connection = Minecraft.getInstance().getConnection();
        return connection != null && NotEnoughItems.platform().serverHas(PlusPackets.Open.TYPE);
    }

    /** Creative+ is on: set by the button, only counts in creative mode on a server that can do it. */
    static boolean active() {
        final var player = Minecraft.getInstance().player;
        return player != null && player.isCreative() && NEIConfig.CREATIVE_PLUS.getAsBoolean() && available();
    }

    static void set(boolean on) {
        NEIConfig.CREATIVE_PLUS.set(on);
        NEIConfig.SPEC.save();
    }

    static void open() {
        NotEnoughItems.platform().sendToServer(new PlusPackets.Open());
    }

    /** The server's extended inventory: a 6 row chest with NEI's name. */
    public static boolean isScreen(Screen screen) {
        return screen instanceof ContainerScreen chest && available()
                && chest.getTitle().getString().equals(PlusInventory.TITLE);
    }

    /**
     * NEI's {@code getItemSpawnSlots(0, 54)}: the stack goes on top of the same item in slots 0-53, then into empty
     * ones. Returns false if there was no room.
     */
    public static boolean give(AbstractContainerScreen<?> screen, ItemStack stack, int count) {
        final int max = stack.getMaxStackSize();
        final List<ItemStack> slots = new ArrayList<>();
        for (int i = 0; i < 54; i++) slots.add(screen.getMenu().getSlot(i).getItem().copy());
        final boolean[] changed = new boolean[54];

        int left = count;
        for (int pass = 0; pass < 2 && left > 0; pass++) {
            for (int i = 0; i < 54 && left > 0; i++) {
                final ItemStack at = slots.get(i);
                if (pass == 0 && !at.isEmpty() && ItemStack.isSameItemSameComponents(at, stack) && at.getCount() < max) {
                    final int add = Math.min(left, max - at.getCount());
                    at.grow(add);
                    left -= add;
                    changed[i] = true;
                } else if (pass == 1 && at.isEmpty()) {
                    final int add = Math.min(left, max);
                    slots.set(i, stack.copyWithCount(add));
                    left -= add;
                    changed[i] = true;
                }
            }
        }
        for (int i = 0; i < 54; i++) {
            if (changed[i]) NotEnoughItems.platform().sendToServer(new PlusPackets.Slot(i, slots.get(i)));
        }
        return left < count;
    }

    /** NEI's {@code checkCreativeInv}: the creative screen is replaced by the server's extended inventory. */
    public static boolean onOpening(Screen newScreen) {
        if (newScreen instanceof CreativeModeInventoryScreen && active()) {
            open();
            return true; // the loader cancels opening the vanilla screen
        }
        return false;
    }

    /** NEI's {@code checkCreativeInv}: the extended inventory closes when creative+ ends (the button, or another gamemode). */
    public static void onTick() {
        final Screen screen = Minecraft.getInstance().screen;
        if (isScreen(screen) && !active()) screen.onClose();
    }

    /** NEI's {@code processCreativeCycling}: Ctrl + scroll with no screen open (it used to be a hotbar change). */
    public static boolean onScroll(double dy) {
        final Minecraft mc = Minecraft.getInstance();
        if (!Screen.hasControlDown() || mc.screen != null || !active()) return false;
        if (dy == 0) return false;
        // NEI: the wheel up moved the hotbar selection back, which cycled the rows by -1
        NotEnoughItems.platform().sendToServer(new PlusPackets.Cycle(dy > 0 ? -1 : 1));
        return true; // the loader cancels the hotbar scroll
    }
}
