package codechicken.nei.fabric;

import codechicken.nei.NotEnoughItems;
import codechicken.nei.net.CheatServer;
import codechicken.nei.net.NEINetwork;
import codechicken.nei.net.PlusInventory;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Common entry point (client and dedicated server): the platform, the packets and the server-side events. */
public final class FabricEntry implements ModInitializer {

    @Override
    public void onInitialize() {
        NotEnoughItems.init(new FabricPlatform());

        for (NEINetwork.ToServer<?> packet : NEINetwork.TO_SERVER) toServer(packet);
        for (NEINetwork.ToClient<?> packet : NEINetwork.TO_CLIENT) toClient(packet);

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> CheatServer.onLogout(handler.getPlayer()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            CheatServer.onStopped();
            PlusInventory.onServerStopped();
        });
        ServerTickEvents.END_SERVER_TICK.register(CheatServer::onTick);
    }

    private static <T extends CustomPacketPayload> void toClient(NEINetwork.ToClient<T> p) {
        PayloadTypeRegistry.playS2C().register(p.type(), p.codec());
    }

    private static <T extends CustomPacketPayload> void toServer(NEINetwork.ToServer<T> p) {
        PayloadTypeRegistry.playC2S().register(p.type(), p.codec());
        ServerPlayNetworking.registerGlobalReceiver(p.type(), (payload, context) -> p.handler().accept(context.player(), payload));
    }
}
