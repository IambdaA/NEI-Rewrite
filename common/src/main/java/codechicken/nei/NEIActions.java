package codechicken.nei;

import codechicken.nei.item.Stacks;
import codechicken.nei.item.ItemEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Things NEI does to the world on the player's behalf.
 *
 * In the creative inventory the stack goes onto the cursor, exactly like picking an item from a creative tab (the
 * creative screen is client driven, so no permissions are involved). Everywhere else it uses vanilla {@code /give},
 * so the server's normal permission rules apply: cheats must be enabled, or the player must be an operator.
 */
public final class NEIActions {

    private NEIActions() {}

    /** Runs a vanilla command as the player (without the leading slash). The server's permission rules apply. */
    public static boolean command(String text) {
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        player.connection.sendCommand(text);
        return true;
    }

    /** Gives the player {@code count} of the entry's item. Returns false if nothing could be done. */
    public static boolean give(ItemEntry entry, int count, Screen screen) {
        if (count < 1 || codechicken.nei.item.FluidDisplay.is(entry.stack)) return false; // fluid display items cannot be given

        if (screen instanceof CreativeModeInventoryScreen creative) {
            final ItemStack stack = Stacks.withCount(entry.stack, Math.min(count, entry.stack.getMaxStackSize()));
            creative.getMenu().setCarried(stack);
            return true;
        }

        if (codechicken.nei.client.CreativePlus.isScreen(screen)) { // NEI: item spawn slots 0-53 of the extended inventory
            return codechicken.nei.client.CreativePlus.give((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) screen, entry.stack, count);
        }

        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;

        // NEI's command.item: {0} player, {1} id, {2} amount, {3} damage, {4} components, {5} name
        if (!NEIConfig.commandSet(NEIConfig.COMMAND_ITEM)) return false;
        final String id = codechicken.nei.item.FluidDisplay.plainId(entry.registryId);
        NEIConfig.runCommand(NEIConfig.COMMAND_ITEM, player.getName().getString(), id, count, 0, components(entry.stack), id);
        return true;
    }

    /**
     * The NBT part of a give command ({@code {...}}) for a stack that is a variant (a potion, an enchanted book, a mob spawner
     * ...). Empty for a plain item. A name set by the list itself ({@code API.setName}) is left out.
     */
    static String components(ItemStack stack) {
        final net.minecraft.nbt.CompoundTag tag = stack.getTag();
        if (tag == null || tag.isEmpty()) return "";
        final net.minecraft.nbt.CompoundTag copy = tag.copy();
        if (copy.contains("display", 10)) {
            final net.minecraft.nbt.CompoundTag display = copy.getCompound("display");
            display.remove("Name");
            if (display.isEmpty()) copy.remove("display");
        }
        return copy.isEmpty() ? "" : copy.toString();
    }
}
