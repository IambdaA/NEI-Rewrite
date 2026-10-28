package codechicken.nei.net;

import codechicken.nei.NotEnoughItems;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** NEI's {@code C2S.SEND_CHAT_ITEM_LINK}: a player shares bookmarks; the server announces it in chat. */
public record LinkToServer(String data) implements CustomPacketPayload {

    public static final Type<LinkToServer> TYPE = new Type<>(Identifier.fromNamespaceAndPath(NotEnoughItems.MODID, "link_to_server"));
    public static final StreamCodec<ByteBuf, LinkToServer> CODEC = ByteBufCodecs.stringUtf8(NEINetwork.MAX_DATA)
            .map(LinkToServer::new, LinkToServer::data);

    @Override
    public Type<LinkToServer> type() {
        return TYPE;
    }
}
