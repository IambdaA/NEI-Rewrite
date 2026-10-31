package codechicken.nei.fabric;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The client-only half of {@link FabricPlatform}'s networking (kept apart so a dedicated server never loads it). */
final class FabricClientNet {

    private FabricClientNet() {}

    static void send(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    static boolean has(CustomPacketPayload.Type<?> type) {
        return ClientPlayNetworking.canSend(type);
    }
}
