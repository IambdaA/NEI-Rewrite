package codechicken.nei.net;

import net.minecraft.network.FriendlyByteBuf;

/** A packet of the mod. 1.20.1 has no payload codecs, so each packet writes itself and its {@link PacketType} reads it back. */
public interface NEIPacket {

    PacketType<?> type();

    void write(FriendlyByteBuf buf);
}
