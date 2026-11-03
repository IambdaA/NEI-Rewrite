package codechicken.nei.net;

import codechicken.nei.NotEnoughItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** NEI's {@code S2C.CACHE_BOOKMARK_DATA}: what a shared link holds, kept by every client until its chat link is clicked. */
public record LinkToClient(String id, String data) implements NEIPacket {

    public static final PacketType<LinkToClient> TYPE = new PacketType<>(new ResourceLocation(NotEnoughItems.MODID, "link_to_client"),
            b -> new LinkToClient(b.readUtf(32), b.readUtf(NEINetwork.MAX_DATA)));

    @Override
    public PacketType<LinkToClient> type() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(id, 32);
        buf.writeUtf(data, NEINetwork.MAX_DATA);
    }
}
