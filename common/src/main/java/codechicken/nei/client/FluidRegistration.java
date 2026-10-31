package codechicken.nei.client;

import codechicken.nei.NotEnoughItems;
import codechicken.nei.item.FluidDisplay;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.function.BiConsumer;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Registers one display item per source fluid, on the client only. A dedicated server must not have them: it sends its item
 * registry to joining clients, and a client without this mod would be refused over the unknown ids. They are registered while
 * the item registry fills, which comes after the fluid registry, so every mod's fluids exist then.
 */
public final class FluidRegistration {

    private FluidRegistration() {}

    /**
     * Creates the display items; the loader module calls this while the item registry fills and registers what is passed
     * to {@code sink} (the item already carries its id, as 26.2 requires).
     */
    public static void create(BiConsumer<Identifier, Item> sink) {
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            if (fluid == Fluids.EMPTY || !fluid.isSource(fluid.defaultFluidState())) continue;
            final Identifier key = BuiltInRegistries.FLUID.getKey(fluid);
            final Identifier id = Identifier.fromNamespaceAndPath(NotEnoughItems.MODID,
                    "fluid/" + key.getNamespace() + "/" + key.getPath());
            // the fluid type registry is not filled yet here: names are set on the stacks
            final Item item = new Item(new Item.Properties().stacksTo(1)
                    .setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, id)));
            sink.accept(id, item);
            FluidDisplay.add(item, fluid, id.toString());
        }
    }
}
