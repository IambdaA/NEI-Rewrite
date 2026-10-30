package codechicken.nei.item;

import codechicken.nei.NotEnoughItems;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * One searchable entry of the item list.
 *
 * Cheap fields (name, mod, id) are computed once at load time so that searching can run on worker threads without
 * touching game state. Tooltip lines and tags are computed lazily.
 */
public final class ItemEntry {

    public final ItemStack stack;
    /** Lower-case display name. */
    public final String name;
    /** Namespace of the item, e.g. {@code minecraft}. */
    public final String modId;
    /** Lower-case mod display name, e.g. {@code minecraft}. */
    public final String modName;
    /** Mod display name as the mod spells it, e.g. {@code Minecraft}. */
    public final String modDisplay;
    /** Full registry id, e.g. {@code minecraft:iron_ingot}. */
    public final String registryId;

    /** Position in the registry (NEI's "Item ID" sort). */
    public final int numericId;
    /** Position in the natural listing, before sorting (NEI's "Default" sort); set once while the list loads. */
    public int order;
    /** Extra lower-case names from {@code API.setAliases}; searching by name also matches these. */
    public final List<String> aliases;

    private volatile String tooltip;
    private volatile List<String> tags;

    public ItemEntry(ItemStack stack) {
        this.stack = stack;
        this.aliases = codechicken.nei.api.API.aliasesOf(stack);
        final int registered = BuiltInRegistries.ITEM.getId(stack.getItem());
        this.numericId = registered >= 0 ? registered : Integer.MAX_VALUE / 2 + Math.floorMod(FluidDisplay.idOf(stack.getItem()).hashCode(), 100000);
        this.name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);

        this.modId = FluidDisplay.ownerNamespace(stack); // a fluid display item belongs to the fluid's mod
        this.registryId = FluidDisplay.idOf(stack.getItem());
        this.modDisplay = NotEnoughItems.platform().modName(this.modId);
        this.modName = this.modDisplay.toLowerCase(Locale.ROOT);
    }

    /** All tooltip lines joined by newlines, lower-case. Computed on first use. */
    public String tooltip() {
        String t = this.tooltip;
        if (t == null) {
            final StringBuilder sb = new StringBuilder();
            try {
                for (var line : this.stack
                        .getTooltipLines(Item.TooltipContext.EMPTY, Minecraft.getInstance().player, TooltipFlag.NORMAL)) {
                    sb.append(line.getString()).append('\n');
                }
            } catch (RuntimeException ignored) {
                // A broken third-party tooltip must never break searching.
            }
            this.tooltip = t = sb.toString().toLowerCase(Locale.ROOT);
        }
        return t;
    }

    /** Item tag ids, e.g. {@code c:ingots/iron}. Computed on first use. */
    public List<String> tags() {
        List<String> t = this.tags;
        if (t == null) {
            this.tags = t = this.stack.getItemHolder().tags().map(tag -> tag.location().toString()).toList();
        }
        return t;
    }
}
