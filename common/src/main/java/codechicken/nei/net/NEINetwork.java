package codechicken.nei.net;

import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import codechicken.nei.NotEnoughItems;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * The mod's network traffic: sharing bookmarks in chat (NEI's chat item link) and the extended creative inventory. All payloads are optional, so a
 * client or a server without the mod can still connect; the server half only matters on servers that have the mod.
 *
 * Common code: it never touches client classes (the client handler lives in {@code client.ChatLinks}). The packets are
 * listed here; each loader module registers them with its own network API.
 */
public final class NEINetwork {

    /** Longest payload text (a client to server packet may hold 32767 bytes). */
    public static final int MAX_DATA = 30000;

    /** A packet the client sends and the server handles. */
    public record ToServer<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, BiConsumer<ServerPlayer, T> handler) {}

    /** A packet the server sends and the client handles (on the client thread). */
    public record ToClient<T extends CustomPacketPayload>(CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec, Consumer<T> handler) {}

    public static final List<ToServer<?>> TO_SERVER = List.of(
            new ToServer<>(LinkToServer.TYPE, LinkToServer.CODEC, (sender, payload) -> share(sender, payload.data())),
            new ToServer<>(PlusPackets.Open.TYPE, PlusPackets.Open.CODEC, (sender, payload) -> PlusInventory.open(sender)),
            new ToServer<>(PlusPackets.Slot.TYPE, PlusPackets.Slot.CODEC,
                    (sender, payload) -> PlusInventory.setSlot(sender, payload.slot(), payload.stack())),
            new ToServer<>(PlusPackets.Cycle.TYPE, PlusPackets.Cycle.CODEC,
                    (sender, payload) -> PlusInventory.cycle(sender, payload.steps())),
            new ToServer<>(CheatPackets.SetSlot.TYPE, CheatPackets.SetSlot.CODEC,
                    (sender, payload) -> CheatServer.setSlot(sender, payload.slot(), payload.stack())),
            new ToServer<>(CheatPackets.DeleteAll.TYPE, CheatPackets.DeleteAll.CODEC,
                    (sender, payload) -> CheatServer.deleteAll(sender)),
            new ToServer<>(CheatPackets.Magnet.TYPE, CheatPackets.Magnet.CODEC,
                    (sender, payload) -> CheatServer.setMagnet(sender, payload.on())));

    public static final List<ToClient<?>> TO_CLIENT = List.of(
            new ToClient<>(LinkToClient.TYPE, LinkToClient.CODEC,
                    payload -> codechicken.nei.client.ChatLinks.cache(payload.id(), payload.data())));

    private NEINetwork() {}

    /** NEI's {@code NEIServerUtils.sendChatItemLink}: every player with the mod gets the data, everyone gets the message. */
    private static void share(ServerPlayer sender, String data) {
        final var server = sender.level().getServer();
        if (server == null) return;
        final String id = UUID.randomUUID().toString().substring(0, 8);

        String label = "Bookmarks";
        for (String line : data.split("\n", 3)) {
            if (line.startsWith("#label ")) label = line.substring(7).trim();
        }
        if (label.length() > 48) label = label.substring(0, 48);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (NotEnoughItems.platform().canSend(player, LinkToClient.TYPE)) {
                NotEnoughItems.platform().sendToPlayer(player, new LinkToClient(id, data));
            }
        }

        final MutableComponent link = Component.literal("[" + label + "]").withStyle(style -> style
                .withColor(0x55FFFF)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/nei_bookmark " + id))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Click to add to your bookmarks (needs NEI Rewrite)"))));
        server.getPlayerList().broadcastSystemMessage(Component.literal("<" + sender.getName().getString() + "> ")
                .append(Component.literal("Click to add bookmark: ").withStyle(net.minecraft.ChatFormatting.GOLD)).append(link), false);
    }
}
