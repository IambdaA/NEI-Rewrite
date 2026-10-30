package codechicken.nei;

import java.nio.file.Path;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/**
 * The few things that differ between the mod loaders. Everything else in the mod is plain Minecraft code. Each loader
 * module implements this and hands it to {@link NotEnoughItems#init} when the mod starts; the client-only half is
 * {@link codechicken.nei.client.ClientPlatform}.
 */
public interface Platform {

    /** The loader's config folder. */
    Path configDir();

    /** The display name of the mod that owns a namespace (the namespace itself when no mod is found). */
    String modName(String namespace);

    boolean isModLoaded(String id);

    /** Sends a payload from the client to the server (client side only). */
    void sendToServer(CustomPacketPayload payload);

    /** Whether the server we are connected to has one of the mod's channels (client side only). */
    boolean serverHas(CustomPacketPayload.Type<?> type);

    void sendToPlayer(ServerPlayer player, CustomPacketPayload payload);

    /** Whether the player's client has the mod's channel (the server may only send payloads the client knows). */
    boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type);
}
