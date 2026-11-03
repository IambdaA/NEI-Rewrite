package codechicken.nei.forge;

import codechicken.nei.net.NEIPacket;
import codechicken.nei.net.PacketType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraftforge.network.NetworkDirection;

/** The client-only half of {@link ForgePlatform}'s networking (kept apart so a dedicated server never loads it). */
final class ForgeClientNet {

    private ForgeClientNet() {}

    static void send(NEIPacket packet) {
        final ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) connection.send(ForgeNet.build(NetworkDirection.PLAY_TO_SERVER, packet));
    }

    static boolean has(PacketType<?> type) {
        final ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection != null && ForgeNet.channel(type).isRemotePresent(connection.getConnection());
    }
}
