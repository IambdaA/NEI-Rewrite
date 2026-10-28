package codechicken.nei.client;

import codechicken.nei.NotEnoughItems;
import codechicken.nei.item.FluidDisplay;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * Registers one display item per source fluid, on the client only. A dedicated server must not have them: it sends its item
 * registry to joining clients, and a client without this mod would be refused over the unknown ids. They are registered while
 * the item registry fills, which comes after the fluid registry, so every mod's fluids exist then.
 */
@EventBusSubscriber(modid = NotEnoughItems.MODID, value = Dist.CLIENT)
final class FluidRegistration {

    private FluidRegistration() {}

    @SubscribeEvent
    static void onRegister(RegisterEvent event) {
        event.register(Registries.ITEM, helper -> {
            for (Fluid fluid : BuiltInRegistries.FLUID) {
                if (fluid == Fluids.EMPTY || !fluid.isSource(fluid.defaultFluidState())) continue;
                final Identifier key = BuiltInRegistries.FLUID.getKey(fluid);
                final Identifier id = Identifier.fromNamespaceAndPath(NotEnoughItems.MODID,
                        "fluid/" + key.getNamespace() + "/" + key.getPath());
                // the fluid type registry is not filled yet here (fluid.getFluidType() crashes): names are set on the stacks
                final Item item = new Item(new Item.Properties().stacksTo(1).setId(ResourceKey.create(Registries.ITEM, id)));
                helper.register(id, item);
                FluidDisplay.add(item, fluid);
            }
        });
    }
}
