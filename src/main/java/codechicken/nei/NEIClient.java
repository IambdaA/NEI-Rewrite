package codechicken.nei;

import codechicken.nei.api.NEIRegisterEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/** Client-only setup. Safe to touch client classes here; this never loads on a dedicated server. */
@Mod(value = NotEnoughItems.MODID, dist = Dist.CLIENT)
public final class NEIClient {

    public NEIClient(ModContainer container, IEventBus modBus) {
        // NEI's IConfigureNEI.loadConfig(): other mods register through codechicken.nei.api.API from this event
        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> {
            // the fluid display items (JEI bridge) get a subset of their own
            codechicken.nei.api.API.addSubset("Fluids", entry -> codechicken.nei.item.FluidDisplay.is(entry.stack));
            ModLoader.postEvent(new NEIRegisterEvent());
        }));
        container.registerConfig(ModConfig.Type.CLIENT, NEIConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
