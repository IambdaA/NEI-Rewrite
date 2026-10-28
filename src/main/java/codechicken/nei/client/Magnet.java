package codechicken.nei.client;

import codechicken.nei.NEIConfig;
import codechicken.nei.NotEnoughItems;
import codechicken.nei.net.CheatPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * As in NEI (magnet button, {@code toggleMagnetMode}): the toolbar toggle. The server pulls the dropped items towards the
 * player ({@code net.CheatServer}); the setting is the client's and is sent again whenever a world is joined.
 */
@EventBusSubscriber(modid = NotEnoughItems.MODID, value = Dist.CLIENT)
final class Magnet {

    private static LocalPlayer lastPlayer;

    private Magnet() {}

    static boolean on() {
        return NEIConfig.MAGNET.getAsBoolean();
    }

    static boolean available() {
        final var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(CheatPackets.Magnet.TYPE);
    }

    static void toggle() {
        final var player = Minecraft.getInstance().player;
        if (!available()) { // NEI toggles only with its server counterpart
            if (player != null) player.sendSystemMessage(Component.literal("The magnet needs NEI Rewrite on the server."));
            return;
        }
        final boolean now = !on();
        NEIConfig.MAGNET.set(now);
        NEIConfig.SPEC.save();
        ClientPacketDistributor.sendToServer(new CheatPackets.Magnet(now));
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        final LocalPlayer player = Minecraft.getInstance().player;
        if (player == lastPlayer) return;
        lastPlayer = player;
        if (player != null && on() && available()) ClientPacketDistributor.sendToServer(new CheatPackets.Magnet(true));
    }
}
