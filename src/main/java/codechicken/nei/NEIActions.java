package codechicken.nei;

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
            final ItemStack stack = entry.stack.copyWithCount(Math.min(count, entry.stack.getMaxStackSize()));
            creative.getMenu().setCarried(stack);
            return true;
        }

        if (codechicken.nei.client.CreativePlus.isScreen(screen)) { // NEI: item spawn slots 0-53 of the extended inventory
            return codechicken.nei.client.CreativePlus.give((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) screen, entry.stack, count);
        }

        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;

        // the command is sent without the leading slash
        player.connection.sendCommand("give @s " + entry.registryId + components(entry.stack) + " " + count);
        return true;
    }

    /**
     * The component part of a give command ({@code [key=value,...]}) for a stack that is a variant (a potion, an enchanted
     * book, a mob spawner ...). Empty for a plain item or if the stack cannot be written. A name set by the list itself
     * ({@code API.setName}) is left out.
     */
    static String components(ItemStack stack) {
        try {
            if (stack.getComponentsPatch().isEmpty()) return "";
            final var level = Minecraft.getInstance().level;
            if (level == null) return "";
            final var ops = level.registryAccess().createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
            final net.minecraft.nbt.Tag written = ItemStack.CODEC.encodeStart(ops, stack.copyWithCount(1)).result().orElse(null);
            if (!(written instanceof net.minecraft.nbt.CompoundTag root) || !(root.get("components") instanceof net.minecraft.nbt.CompoundTag parts)) {
                return "";
            }
            final StringBuilder sb = new StringBuilder();
            for (String key : parts.keySet()) {
                if (key.equals("minecraft:custom_name")) continue;
                if (sb.length() > 0) sb.append(',');
                if (key.startsWith("!")) sb.append(key); // a removed component has no value
                else sb.append(key).append('=').append(parts.get(key));
            }
            return sb.length() == 0 ? "" : "[" + sb + "]";
        } catch (RuntimeException e) {
            NotEnoughItems.LOGGER.warn("Could not write the item's components for /give: {}", e.toString());
            return "";
        }
    }
}
