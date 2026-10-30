package codechicken.nei.neoforge;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/** The client-only half of {@link NeoPlatform}'s networking (kept apart so a dedicated server never loads it). */
final class NeoClientNet {

    private NeoClientNet() {}

    static void send(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    static boolean has(CustomPacketPayload.Type<?> type) {
        final var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(type.id());
    }
}
