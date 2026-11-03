package codechicken.nei.net;

import codechicken.nei.NotEnoughItems;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/** NEI's {@code C2S.SEND_CHAT_ITEM_LINK}: a player shares bookmarks; the server announces it in chat. */
public record LinkToServer(String data) implements NEIPacket {

    public static final PacketType<LinkToServer> TYPE = new PacketType<>(
            new ResourceLocation(NotEnoughItems.MODID, "link_to_server"), b -> new LinkToServer(b.readUtf(NEINetwork.MAX_DATA)));

    @Override
    public PacketType<LinkToServer> type() {
        return TYPE;
    }

    @Override
    public void write(FriendlyByteBuf buf) {
        buf.writeUtf(data, NEINetwork.MAX_DATA);
    }
}
