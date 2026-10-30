package codechicken.nei.item;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/**
 * NEI's fluid display items ({@code ItemFluidDisplay}): every fluid gets an item of its own so fluids can sit in the item
 * panel, be searched, and be looked up like items (recipes that make or use them). GTNH NEI uses one item with the fluid in
 * its damage value; here every source fluid gets its own item (ids {@code notenoughitems:fluid/<mod>/<fluid>}), so every
 * place that keys by item works unchanged. They exist only on the client (see {@code client.FluidRegistration}; each loader module registers them): a server
 * that sent them would be refused by clients without the mod. They are never obtainable.
 */
public final class FluidDisplay {

    private static final Map<Item, Fluid> FLUIDS = new HashMap<>();
    private static final Map<Fluid, Item> ITEMS = new HashMap<>();
    /** The id each display item was registered under: kept here so nothing depends on the item registry still holding it. */
    private static final Map<Item, String> IDS = new HashMap<>();

    private FluidDisplay() {}

    /** Records a display item; called while the client registers them. */
    public static void add(Item item, Fluid fluid, String id) {
        FLUIDS.put(item, fluid);
        ITEMS.put(fluid, item);
        IDS.put(item, id);
    }

    /** Every display item made so far. */
    public static java.util.Collection<Item> items() {
        return java.util.List.copyOf(FLUIDS.keySet());
    }

    /**
     * The id of an item as text, e.g. {@code minecraft:stone}. A fluid display item answers with the id it was made with
     * even if the item registry has dropped it (a registry sync with a server that does not know it), where the registry
     * itself would say {@code minecraft:air}.
     */
    public static String idOf(Item item) {
        final String own = IDS.get(item);
        return own != null ? own : BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /** How many display items the item registry still holds (a diagnostic: it should be all of them). */
    public static int stillRegistered() {
        int n = 0;
        for (Item item : FLUIDS.keySet()) {
            if (BuiltInRegistries.ITEM.getKey(item).toString().equals(IDS.get(item))) n++;
        }
        return n;
    }

    public static boolean isFluidItem(Item item) {
        return FLUIDS.containsKey(item);
    }

    public static boolean is(ItemStack stack) {
        return FLUIDS.containsKey(stack.getItem());
    }

    public static Fluid fluidOf(ItemStack stack) {
        return FLUIDS.get(stack.getItem());
    }

    /** The display stack of a fluid, or empty if it has none (flowing or unregistered). */
    public static ItemStack stackOf(Fluid fluid) {
        Item item = ITEMS.get(fluid);
        // a recipe may name the flowing variant; the display item belongs to the source
        if (item == null && fluid instanceof net.minecraft.world.level.material.FlowingFluid flowing) {
            item = ITEMS.get(flowing.getSource());
        }
        return item == null ? ItemStack.EMPTY : named(new ItemStack(item));
    }

    /** Gives a fluid display stack the fluid's name (not italic, as a custom name would be); other stacks come back as they are. */
    public static ItemStack named(ItemStack stack) {
        final Fluid fluid = fluidOf(stack);
        if (fluid != null) {
            // the fluid's block carries its name ("Water", "Lava", a mod's own); a fluid without a block shows its id
            final var block = fluid.defaultFluidState().createLegacyBlock().getBlock();
            final net.minecraft.network.chat.Component name = block == net.minecraft.world.level.block.Blocks.AIR
                    ? net.minecraft.network.chat.Component.literal(BuiltInRegistries.FLUID.getKey(fluid).getPath())
                    : block.getName();
            stack.set(DataComponents.CUSTOM_NAME, name.copy().withStyle(s -> s.withItalic(false)));
        }
        return stack;
    }

    /** The mod that owns the stack: the fluid's mod for a display item, else the item's. */
    public static String ownerNamespace(ItemStack stack) {
        final Fluid fluid = fluidOf(stack);
        return (fluid != null ? BuiltInRegistries.FLUID.getKey(fluid) : BuiltInRegistries.ITEM.getKey(stack.getItem()))
                .getNamespace();
    }
}
