package codechicken.nei.neoforge;

import codechicken.nei.NotEnoughItems;
import codechicken.nei.net.CheatServer;
import codechicken.nei.net.NEINetwork;
import codechicken.nei.net.PlusInventory;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Common entry point (client and dedicated server): the platform, the optional packets and the server-side events. */
@Mod(NotEnoughItems.MODID)
public final class NeoForgeEntry {

    public NeoForgeEntry(IEventBus modBus) {
        NotEnoughItems.init(new NeoPlatform());
        modBus.addListener(NeoForgeEntry::registerPayloads);

        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer player) CheatServer.onLogout(player);
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            CheatServer.onStopped();
            PlusInventory.onServerStopped();
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> CheatServer.onTick(e.getServer()));

        // Vanilla does not send recipes to clients. NeoForge lets the server opt in per recipe type; opt in to every
        // registered type so recipe / usage lookup also covers modded machines. (The mod must be on the server for this.)
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent e) ->
                e.sendRecipes(BuiltInRegistries.RECIPE_TYPE.stream().toArray(RecipeType<?>[]::new)));
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // optional: a client or a server without the mod can still connect
        final PayloadRegistrar registrar = event.registrar("1").optional();
        for (NEINetwork.ToServer<?> packet : NEINetwork.TO_SERVER) toServer(registrar, packet);
        for (NEINetwork.ToClient<?> packet : NEINetwork.TO_CLIENT) toClient(registrar, packet);
    }

    private static <T extends CustomPacketPayload> void toServer(PayloadRegistrar registrar, NEINetwork.ToServer<T> p) {
        registrar.playToServer(p.type(), p.codec(), (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) p.handler().accept(player, payload);
        });
    }

    private static <T extends CustomPacketPayload> void toClient(PayloadRegistrar registrar, NEINetwork.ToClient<T> p) {
        registrar.playToClient(p.type(), p.codec(), (payload, context) -> p.handler().accept(payload));
    }
}
