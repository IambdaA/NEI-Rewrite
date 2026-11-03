package codechicken.nei.net;

import java.util.function.Function;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** The channel id of a packet and how to read it. */
public record PacketType<T extends NEIPacket>(ResourceLocation id, Function<FriendlyByteBuf, T> reader) {}
