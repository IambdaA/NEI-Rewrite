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
import net.fabricmc.fabric.api.recipe.v1.sync.RecipeSynchronization;
import net.minecraft.core.registries.BuiltInRegistries;
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

        // Vanilla does not send recipes to clients. Fabric API lets the server opt in per recipe serializer: do it for
        // every serializer so recipe / usage lookup also covers modded machines (the mod must be on the server for this).
        syncRecipes();
        ServerLifecycleEvents.SERVER_STARTING.register(server -> syncRecipes()); // serializers of mods that registered late
    }

    private static final java.util.Set<Object> SYNCED = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    private static void syncRecipes() {
        for (var serializer : BuiltInRegistries.RECIPE_SERIALIZER) {
            if (!SYNCED.add(serializer)) continue;
            try {
                RecipeSynchronization.synchronizeRecipeSerializer(serializer);
            } catch (RuntimeException e) {
                NotEnoughItems.LOGGER.warn("NEI Rewrite: cannot sync recipe serializer {}: {}", serializer, e.toString());
            }
        }
    }

    private static <T extends CustomPacketPayload> void toClient(NEINetwork.ToClient<T> p) {
        PayloadTypeRegistry.clientboundPlay().register(p.type(), p.codec());
    }

    private static <T extends CustomPacketPayload> void toServer(NEINetwork.ToServer<T> p) {
        PayloadTypeRegistry.serverboundPlay().register(p.type(), p.codec());
        ServerPlayNetworking.registerGlobalReceiver(p.type(), (payload, context) -> p.handler().accept(context.player(), payload));
    }
}
