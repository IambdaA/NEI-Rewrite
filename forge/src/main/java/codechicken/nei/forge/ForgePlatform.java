package codechicken.nei.forge;

import java.nio.file.Path;

import codechicken.nei.Platform;
import codechicken.nei.net.NEIPacket;
import codechicken.nei.net.PacketType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkDirection;

public final class ForgePlatform implements Platform {

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get();
    }

    @Override
    public String modName(String namespace) {
        return ModList.get().getModContainerById(namespace).map(c -> c.getModInfo().getDisplayName()).orElse(namespace);
    }

    @Override
    public boolean isModLoaded(String id) {
        return ModList.get().isLoaded(id);
    }

    @Override
    public boolean staysInGrid(net.minecraft.world.item.ItemStack stack) {
        if (!stack.hasCraftingRemainingItem()) return false;
        final net.minecraft.world.item.ItemStack rest = stack.getCraftingRemainingItem();
        return !rest.isEmpty() && rest.is(stack.getItem());
    }

    /** JEI's Forge fluid ingredient is Forge's own {@link FluidStack}, counted in millibuckets. */
    @Override
    public Fluid fluidOf(Object ingredient) {
        return ingredient instanceof FluidStack stack ? stack.getFluid() : null;
    }

    /** Forge 1.20.1 has no fluid tooltip event: mods describe their fluids for JEI, so JEI's fluid tooltip is asked. */
    @Override
    public java.util.List<net.minecraft.network.chat.Component> fluidTooltip(Fluid fluid, boolean advanced) {
        if (!isModLoaded("jei")) return java.util.List.of();
        return codechicken.nei.jei.NEIJeiPlugin.fluidTooltip(fluid, advanced);
    }

    @Override
    public net.minecraft.network.chat.Component fluidName(Fluid fluid) {
        return fluid.getFluidType().getDescription();
    }

    @Override
    public int fluidTemperature(Fluid fluid) {
        return fluid.getFluidType().getTemperature();
    }

    @Override
    public boolean fluidGaseous(Fluid fluid) {
        return fluid.getFluidType().isLighterThanAir();
    }

    @Override
    public long fluidAmount(Object ingredient) {
        return ingredient instanceof FluidStack stack ? stack.getAmount() : -1;
    }

    @Override
    public void sendToServer(NEIPacket payload) {
        ForgeClientNet.send(payload);
    }

    @Override
    public boolean serverHas(PacketType<?> type) {
        return ForgeClientNet.has(type);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, NEIPacket payload) {
        player.connection.send(ForgeNet.build(NetworkDirection.PLAY_TO_CLIENT, payload));
    }

    @Override
    public boolean canSend(ServerPlayer player, PacketType<?> type) {
        return ForgeNet.channel(type).isRemotePresent(player.connection.connection);
    }
}
