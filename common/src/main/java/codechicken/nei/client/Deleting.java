package codechicken.nei.client;

import codechicken.nei.NotEnoughItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import codechicken.nei.net.CheatPackets;

/**
 * As in NEI ({@code NEIClientUtils.deleteSlotStack / decreaseSlotStack / deleteItemsOfType / deleteEverything},
 * {@code NEIController} delete mode): the delete button of the toolbar and the delete mode. The server does the deleting
 * (the stack on the cursor can also be dropped without it, as before), so this needs NEI Rewrite on the server.
 */
final class Deleting {

    private static boolean mode;
    private static Screen modeScreen;

    private Deleting() {}

    /** Whether the server has the mod's delete packets. */
    static boolean server() {
        final var connection = Minecraft.getInstance().getConnection();
        return connection != null && NotEnoughItems.platform().serverHas(CheatPackets.SetSlot.TYPE);
    }

    /** NEI's {@code canUseDeleteMode}: not in the vanilla creative inventory. */
    static boolean canUseMode(Screen screen) {
        return !(screen instanceof CreativeModeInventoryScreen);
    }

    /** The delete mode belongs to the screen it was switched on in (NEI resets it whenever a screen loads). */
    static boolean modeOn(Screen screen) {
        return mode && modeScreen == screen;
    }

    static void toggleMode(Screen screen) {
        if (!canUseMode(screen)) return;
        mode = !modeOn(screen);
        modeScreen = screen;
    }

    private static void say(String text) {
        final var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.literal(text));
    }

    /** NEI's {@code setSlotContents}: changes the slot here at once and tells the server. */
    private static void setSlot(AbstractContainerScreen<?> screen, int index, ItemStack stack) {
        final AbstractContainerMenu menu = screen.getMenu();
        if (!server()) {
            if (index == CheatPackets.CARRIED) { // the cursor stack needs no server
                menu.setCarried(stack);
            } else {
                say("Deleting items needs NEI Rewrite on the server.");
            }
            return;
        }
        if (index == CheatPackets.CARRIED) menu.setCarried(stack);
        else menu.getSlot(index).set(stack);
        NotEnoughItems.platform().sendToServer(new CheatPackets.SetSlot(index, stack));
    }

    static void deleteHeld(AbstractContainerScreen<?> screen) {
        setSlot(screen, CheatPackets.CARRIED, ItemStack.EMPTY);
    }

    static void decreaseHeld(AbstractContainerScreen<?> screen) {
        decrease(screen, CheatPackets.CARRIED);
    }

    static void deleteSlot(AbstractContainerScreen<?> screen, int index) {
        setSlot(screen, index, ItemStack.EMPTY);
    }

    /** NEI's {@code decreaseSlotStack}: one item less, the last one deletes the stack. */
    static void decrease(AbstractContainerScreen<?> screen, int index) {
        final ItemStack stack = index == CheatPackets.CARRIED ? screen.getMenu().getCarried() : screen.getMenu().getSlot(index).getItem();
        if (stack.isEmpty()) return;
        setSlot(screen, index, stack.getCount() <= 1 ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - 1));
    }

    /** NEI's {@code deleteItemsOfType}: every slot holding the same item. */
    static void deleteType(AbstractContainerScreen<?> screen, ItemStack type) {
        if (!canUseMode(screen)) return; // the vanilla creative screen's slots are not the server's
        final AbstractContainerMenu menu = screen.getMenu();
        for (int i = 0; i < menu.slots.size(); i++) {
            final ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, type)) setSlot(screen, i, ItemStack.EMPTY);
        }
    }

    /** NEI's {@code deleteEverything}: every slot of the open screen. */
    static void deleteEverything(AbstractContainerScreen<?> screen) {
        if (!canUseMode(screen)) return; // the vanilla creative screen's slots are not the server's
        if (!server()) {
            say("Deleting items needs NEI Rewrite on the server.");
            return;
        }
        final AbstractContainerMenu menu = screen.getMenu();
        for (int i = 0; i < menu.slots.size(); i++) menu.getSlot(i).set(ItemStack.EMPTY);
        NotEnoughItems.platform().sendToServer(new CheatPackets.DeleteAll());
    }

    /** NEI's delete button ({@code LayoutManager.delete.onButtonPress}). */
    static void button(AbstractContainerScreen<?> screen, boolean rightClick) {
        final ItemStack held = screen.getMenu().getCarried();
        final boolean shift = HotkeyHelp.isShiftDown();
        if (!held.isEmpty()) {
            if (shift) {
                final ItemStack type = held.copy();
                deleteHeld(screen);
                deleteType(screen, type);
            } else if (rightClick) {
                decreaseHeld(screen);
            } else {
                deleteHeld(screen);
            }
        } else if (shift) {
            deleteEverything(screen);
        } else {
            toggleMode(screen);
        }
    }

    /** NEI's {@code NEIController.handleSlotClick} in delete mode: Shift + left click deletes the item type, right click one item. */
    static void modeClick(AbstractContainerScreen<?> screen, Slot slot, int button) {
        final int index = screen.getMenu().slots.indexOf(slot);
        if (index < 0) return;
        if (HotkeyHelp.isShiftDown() && button == 0) {
            final ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) deleteType(screen, stack.copy());
        } else if (button == 1) {
            decrease(screen, index);
        } else {
            deleteSlot(screen, index);
        }
    }
}
