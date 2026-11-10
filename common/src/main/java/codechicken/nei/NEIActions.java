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
        if (count < 1) return false;
        if (codechicken.nei.item.FluidDisplay.is(entry.stack)) { // a fluid: its bucket, as JEI's cheat mode does
            final ItemStack bucket = bucketOf(entry.stack);
            if (bucket.isEmpty()) return false;
            final String id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(bucket.getItem()).toString();
            return giveStack(bucket, id, Math.min(count, bucket.getMaxStackSize()), screen);
        }
        return giveStack(entry.stack, entry.registryId, count, screen);
    }

    /**
     * Not in NEI: gives what a crafting chain group still lacks of its ingredients (the Missing Items of its Shift tooltip) and
     * what its machine or furnace steps make, which autocrafting cannot, so the group can be autocrafted. A tool's number is
     * durability, so it comes as enough tools to cover it; a fluid comes as filled buckets (a fluid without a bucket is left
     * out). Everything goes through the give command, also on the creative screen, where a give only puts one stack on the
     * cursor. Returns how many different items were given.
     */
    public static int giveMissing(codechicken.nei.bookmark.BookmarkGrid grid, int groupId) {
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !NEIConfig.commandSet(NEIConfig.COMMAND_ITEM)) return 0;
        final codechicken.nei.client.BookmarkPanel.ChainTotals totals = codechicken.nei.client.BookmarkPanel.chainTotals(grid, groupId, true);
        final java.util.Map<String, long[]> wanted = new java.util.LinkedHashMap<>(totals.missing());
        codechicken.nei.client.BookmarkPanel.machineMade(grid, groupId, totals).forEach(wanted::putIfAbsent);
        int given = 0;
        for (java.util.Map.Entry<String, long[]> e : wanted.entrySet()) {
            final ItemEntry entry = ItemList.find(e.getKey());
            if (entry == null) continue;
            ItemStack stack = entry.stack;
            String id = entry.registryId;
            long count = e.getValue()[0];
            if (codechicken.nei.item.FluidDisplay.is(stack)) {
                stack = bucketOf(stack);
                if (stack.isEmpty()) continue;
                id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                count = (count + 999) / 1000; // millibuckets
            } else if (totals.tools().contains(e.getKey()) && stack.getMaxDamage() > 0) {
                count = (count + stack.getMaxDamage() - 1) / stack.getMaxDamage();
            }
            if (count <= 0) continue;
            final String plain = codechicken.nei.item.FluidDisplay.plainId(id);
            final String parts = components(stack);
            final long perCommand = 100L * stack.getMaxStackSize(); // /give refuses more than 100 stacks at once
            for (long left = count; left > 0; left -= perCommand) {
                NEIConfig.runCommand(NEIConfig.COMMAND_ITEM, player.getName().getString(), plain, Math.min(left, perCommand), 0, parts, plain);
            }
            given++;
        }
        return given;
    }

    /** The filled bucket of a fluid display stack, or empty if the fluid has none (gases, NTM's fluids, flowing variants). */
    public static ItemStack bucketOf(ItemStack fluidDisplay) {
        final net.minecraft.world.level.material.Fluid fluid = codechicken.nei.item.FluidDisplay.fluidOf(fluidDisplay);
        if (fluid == null) return ItemStack.EMPTY;
        final net.minecraft.world.item.Item bucket = fluid.getBucket();
        return bucket == null || bucket == net.minecraft.world.item.Items.AIR ? ItemStack.EMPTY : new ItemStack(bucket);
    }

    private static boolean giveStack(ItemStack given, String registryId, int count, Screen screen) {
        if (screen instanceof CreativeModeInventoryScreen creative) {
            final ItemStack stack = given.copyWithCount(Math.min(count, given.getMaxStackSize()));
            creative.getMenu().setCarried(stack);
            return true;
        }

        if (codechicken.nei.client.CreativePlus.isScreen(screen)) { // NEI: item spawn slots 0-53 of the extended inventory
            return codechicken.nei.client.CreativePlus.give((net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) screen, given, count);
        }

        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;

        // NEI's command.item: {0} player, {1} id, {2} amount, {3} damage, {4} components, {5} name
        if (!NEIConfig.commandSet(NEIConfig.COMMAND_ITEM)) return false;
        final String id = codechicken.nei.item.FluidDisplay.plainId(registryId);
        NEIConfig.runCommand(NEIConfig.COMMAND_ITEM, player.getName().getString(), id, count, 0, components(given), id);
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
            for (String key : parts.getAllKeys()) {
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
