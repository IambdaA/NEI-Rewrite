package codechicken.nei.forge;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

import codechicken.nei.net.NEINetwork;
import codechicken.nei.net.NEIPacket;
import codechicken.nei.net.PacketType;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.ICustomPacket;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;

/**
 * The packets on Forge 1.20.1: one event channel per packet type (the packet's id is the channel name, as on Fabric). All
 * channels accept any remote version, and a missing channel as well, so a client or a server without the mod can still
 * connect (the mod only sends to a side that has the channel).
 */
final class ForgeNet {

    private static final String PROTOCOL = "1";
    private static final Map<ResourceLocation, EventNetworkChannel> CHANNELS = new HashMap<>();

    private ForgeNet() {}

    static EventNetworkChannel channel(PacketType<?> type) {
        return CHANNELS.computeIfAbsent(type.id(), id -> NetworkRegistry.newEventChannel(id, () -> PROTOCOL, v -> true, v -> true));
    }

    static void register() {
        for (NEINetwork.ToServer<?> packet : NEINetwork.TO_SERVER) toServer(packet);
        for (NEINetwork.ToClient<?> packet : NEINetwork.TO_CLIENT) toClient(packet);
    }

    private static <T extends NEIPacket> void toServer(NEINetwork.ToServer<T> p) {
        channel(p.type()).addListener((NetworkEvent.ClientCustomPayloadEvent event) -> {
            final NetworkEvent.Context context = event.getSource().get();
            context.setPacketHandled(true);
            final ServerPlayer player = context.getSender();
            if (player == null) return;
            final T packet = p.type().reader().apply(event.getPayload()); // read now: the buffer is released after this call
            context.enqueueWork(() -> p.handler().accept(player, packet));
        });
    }

    private static <T extends NEIPacket> void toClient(NEINetwork.ToClient<T> p) {
        channel(p.type()).addListener((NetworkEvent.ServerCustomPayloadEvent event) -> {
            final NetworkEvent.Context context = event.getSource().get();
            context.setPacketHandled(true);
            final T packet = p.type().reader().apply(event.getPayload());
            context.enqueueWork(() -> p.handler().accept(packet));
        });
    }

    /** The vanilla packet that carries the mod's packet in the given direction. */
    static Packet<?> build(NetworkDirection direction, NEIPacket packet) {
        final FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        packet.write(buf);
        final ICustomPacket<Packet<?>> custom = direction.buildPacket(Pair.of(buf, 0), packet.type().id());
        return custom.getThis();
    }
}
