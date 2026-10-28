package codechicken.nei.net;

import java.util.UUID;

import codechicken.nei.NotEnoughItems;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The mod's network traffic: sharing bookmarks in chat (NEI's chat item link) and the extended creative inventory. All payloads are optional, so a
 * client or a server without the mod can still connect; the server half only matters on servers that have the mod.
 *
 * Common code: it never touches client classes (the client handler lives in {@code client.ChatLinks}).
 */
@EventBusSubscriber(modid = NotEnoughItems.MODID)
public final class NEINetwork {

    /** Longest payload text (about 500 items). */
    public static final int MAX_DATA = 60000;

    private NEINetwork() {}

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        final var registrar = event.registrar("1").optional();
        registrar.playToServer(LinkToServer.TYPE, LinkToServer.CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sender) share(sender, payload.data());
        });
        registrar.playToServer(PlusPackets.Open.TYPE, PlusPackets.Open.CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sender) PlusInventory.open(sender);
        });
        registrar.playToServer(PlusPackets.Slot.TYPE, PlusPackets.Slot.CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sender) PlusInventory.setSlot(sender, payload.slot(), payload.stack());
        });
        registrar.playToServer(PlusPackets.Cycle.TYPE, PlusPackets.Cycle.CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sender) PlusInventory.cycle(sender, payload.steps());
        });
        registrar.playToServer(CheatPackets.SetSlot.TYPE, CheatPackets.SetSlot.CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sender) CheatServer.setSlot(sender, payload.slot(), payload.stack());
        });
        registrar.playToServer(CheatPackets.DeleteAll.TYPE, CheatPackets.DeleteAll.CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sender) CheatServer.deleteAll(sender);
        });
        registrar.playToServer(CheatPackets.Magnet.TYPE, CheatPackets.Magnet.CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sender) CheatServer.setMagnet(sender, payload.on());
        });
        registrar.playToClient(LinkToClient.TYPE, LinkToClient.CODEC,
                (payload, ctx) -> codechicken.nei.client.ChatLinks.cache(payload.id(), payload.data()));
    }

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
            if (player.connection.hasChannel(LinkToClient.TYPE)) PacketDistributor.sendToPlayer(player, new LinkToClient(id, data));
        }

        final MutableComponent link = Component.literal("[" + label + "]").withStyle(style -> style
                .withColor(0x55FFFF)
                .withClickEvent(new ClickEvent.RunCommand("/nei_bookmark " + id))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to add to your bookmarks (needs NEI Rewrite)"))));
        server.getPlayerList().broadcastSystemMessage(Component.literal("<" + sender.getName().getString() + "> ")
                .append(Component.literal("Click to add bookmark: ").withStyle(net.minecraft.ChatFormatting.GOLD)).append(link), false);
    }
}
