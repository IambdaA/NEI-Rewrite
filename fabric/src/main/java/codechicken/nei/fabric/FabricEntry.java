package codechicken.nei.fabric;

import codechicken.nei.NotEnoughItems;
import codechicken.nei.net.CheatServer;
import codechicken.nei.net.NEINetwork;
import codechicken.nei.net.NEIPacket;
import codechicken.nei.net.PlusInventory;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Common entry point (client and dedicated server): the platform, the packets the server receives and the server-side events. */
public final class FabricEntry implements ModInitializer {

    @Override
    public void onInitialize() {
        NotEnoughItems.init(new FabricPlatform());

        // every Fabric channel is optional: a client without the mod simply never gets (or sends) these
        for (NEINetwork.ToServer<?> packet : NEINetwork.TO_SERVER) toServer(packet);

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CheatServer.onLogout(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            CheatServer.onStopped();
            PlusInventory.onServerStopped();
        });
        ServerTickEvents.END_SERVER_TICK.register(CheatServer::onTick);
    }

    private static <T extends NEIPacket> void toServer(NEINetwork.ToServer<T> p) {
        ServerPlayNetworking.registerGlobalReceiver(p.type().id(), (server, player, handler, buf, responseSender) -> {
            final T packet = p.type().reader().apply(buf); // read now: the buffer is released after this call
            server.execute(() -> p.handler().accept(player, packet));
        });
    }
}
