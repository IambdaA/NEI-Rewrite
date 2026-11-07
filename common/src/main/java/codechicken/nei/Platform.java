package codechicken.nei;

import java.nio.file.Path;

import codechicken.nei.net.NEIPacket;
import codechicken.nei.net.PacketType;
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

    /** The loader's name of a fluid (its fluid type's description), or null when the loader has none. */
    default net.minecraft.network.chat.Component fluidName(net.minecraft.world.level.material.Fluid fluid) {
        return null;
    }

    /** A fluid's temperature in kelvin (NEI's Show Fluid Temperature); 300 when the loader does not know. */
    default int fluidTemperature(net.minecraft.world.level.material.Fluid fluid) {
        return 300;
    }

    /**
     * The lines other mods add to a fluid's tooltip through the loader's own hook (NeoForge {@code FluidTooltipEvent}, Fabric
     * {@code FluidVariantRendering#getTooltip}), without the name: NTM's traits, its F1 help, the id with advanced tooltips.
     */
    default java.util.List<net.minecraft.network.chat.Component> fluidTooltip(net.minecraft.world.level.material.Fluid fluid,
            boolean advanced) {
        return java.util.List.of();
    }

    /** Whether a fluid is a gas (NEI's Show Fluid State: lighter than air). */
    default boolean fluidGaseous(net.minecraft.world.level.material.Fluid fluid) {
        return false;
    }

    /** The fluid inside one of the loader's fluid ingredient objects (as JEI hands them out), or null when it is not one. */
    net.minecraft.world.level.material.Fluid fluidOf(Object ingredient);

    /** The amount in millibuckets of one of the loader's fluid ingredient objects, or -1 when it is not one. */
    long fluidAmount(Object ingredient);

    /** Sends a payload from the client to the server (client side only). */
    void sendToServer(NEIPacket payload);

    /** Whether the server we are connected to has one of the mod's channels (client side only). */
    boolean serverHas(PacketType<?> type);

    void sendToPlayer(ServerPlayer player, NEIPacket payload);

    /** Whether the player's client has the mod's channel (the server may only send payloads the client knows). */
    boolean canSend(ServerPlayer player, PacketType<?> type);
}
