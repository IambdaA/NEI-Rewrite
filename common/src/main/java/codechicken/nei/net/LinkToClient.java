package codechicken.nei.net;

import codechicken.nei.NotEnoughItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** NEI's {@code S2C.CACHE_BOOKMARK_DATA}: what a shared link holds, kept by every client until its chat link is clicked. */
public record LinkToClient(String id, String data) implements CustomPacketPayload {

    public static final Type<LinkToClient> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(NotEnoughItems.MODID, "link_to_client"));
    public static final StreamCodec<ByteBuf, LinkToClient> CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), LinkToClient::id,
            ByteBufCodecs.stringUtf8(NEINetwork.MAX_DATA), LinkToClient::data,
            LinkToClient::new);

    @Override
    public Type<LinkToClient> type() {
        return TYPE;
    }
}
