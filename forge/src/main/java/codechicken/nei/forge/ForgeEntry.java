package codechicken.nei.forge;

import codechicken.nei.NotEnoughItems;
import codechicken.nei.net.CheatServer;
import codechicken.nei.net.PlusInventory;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;

/** The mod's entry point (client and dedicated server): the platform, the optional packets and the server-side events. */
@Mod(NotEnoughItems.MODID)
public final class ForgeEntry {

    public ForgeEntry() {
        final IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        NotEnoughItems.init(new ForgePlatform());
        ForgeNet.register();

        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) CheatServer.onLogout(player);
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            CheatServer.onStopped();
            PlusInventory.onServerStopped();
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) CheatServer.onTick(e.getServer());
        });

        // the client half is a class of its own so a dedicated server never loads it
        if (FMLEnvironment.dist == Dist.CLIENT) ForgeClient.init(modBus);
    }
}
