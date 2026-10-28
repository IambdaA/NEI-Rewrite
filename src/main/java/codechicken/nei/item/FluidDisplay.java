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
 * place that keys by item works unchanged. They exist only on the client (see {@code client.FluidRegistration}): a server
 * that sent them would be refused by clients without the mod. They are never obtainable.
 */
public final class FluidDisplay {

    private static final Map<Item, Fluid> FLUIDS = new HashMap<>();
    private static final Map<Fluid, Item> ITEMS = new HashMap<>();

    private FluidDisplay() {}

    /** Records a display item; called while the client registers them. */
    public static void add(Item item, Fluid fluid) {
        FLUIDS.put(item, fluid);
        ITEMS.put(fluid, item);
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
            stack.set(DataComponents.CUSTOM_NAME,
                    fluid.getFluidType().getDescription().copy().withStyle(s -> s.withItalic(false)));
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
