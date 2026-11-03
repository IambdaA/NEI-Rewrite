package codechicken.nei.fabric;

import codechicken.nei.net.NEIPacket;
import codechicken.nei.net.PacketType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;

/** The client-only half of {@link FabricPlatform}'s networking (kept apart so a dedicated server never loads it). */
final class FabricClientNet {

    private FabricClientNet() {}

    static void send(NEIPacket packet) {
        final FriendlyByteBuf buf = PacketByteBufs.create();
        packet.write(buf);
        ClientPlayNetworking.send(packet.type().id(), buf);
    }

    static boolean has(PacketType<?> type) {
        return ClientPlayNetworking.canSend(type.id());
    }
}
