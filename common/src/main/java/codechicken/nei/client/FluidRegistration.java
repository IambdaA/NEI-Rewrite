package codechicken.nei.client;

import java.util.function.BiConsumer;

import codechicken.nei.NotEnoughItems;
import codechicken.nei.item.FluidDisplay;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * Creates one display item per source fluid, on the client only. A dedicated server must not have them: it sends its item
 * registry to joining clients, and a client without this mod would be refused over the unknown ids. The loader module
 * calls {@link #create} while the item registry fills, which comes after the fluid registry, so every mod's fluids exist
 * then, and registers what is passed to {@code sink}.
 */
public final class FluidRegistration {

    private FluidRegistration() {}

    public static void create(BiConsumer<ResourceLocation, Item> sink) {
        for (Fluid fluid : BuiltInRegistries.FLUID) {
            if (fluid == Fluids.EMPTY || !fluid.isSource(fluid.defaultFluidState())) continue;
            final ResourceLocation key = BuiltInRegistries.FLUID.getKey(fluid);
            final ResourceLocation id = ResourceLocation.fromNamespaceAndPath(NotEnoughItems.MODID,
                    "fluid/" + key.getNamespace() + "/" + key.getPath());
            final Item item = new Item(new Item.Properties().stacksTo(1));
            sink.accept(id, item);
            FluidDisplay.add(item, fluid, id.toString());
        }
    }
}
