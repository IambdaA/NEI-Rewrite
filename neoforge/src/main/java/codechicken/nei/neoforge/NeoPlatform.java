package codechicken.nei.neoforge;

import java.nio.file.Path;

import codechicken.nei.Platform;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.network.PacketDistributor;

public final class NeoPlatform implements Platform {

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

    @Override
    public net.minecraft.world.level.material.Fluid fluidOf(Object ingredient) {
        return ingredient instanceof net.neoforged.neoforge.fluids.FluidStack stack ? stack.getFluid() : null;
    }

    @Override
    public java.util.List<net.minecraft.network.chat.Component> fluidTooltip(net.minecraft.world.level.material.Fluid fluid,
            boolean advanced) {
        // NeoForge 21.1 has no FluidTooltipEvent: mods describe their fluids for JEI, so JEI's fluid tooltip is asked
        if (!isModLoaded("jei")) return java.util.List.of();
        return codechicken.nei.jei.NEIJeiPlugin.fluidTooltip(fluid, advanced);
    }

    @Override
    public net.minecraft.network.chat.Component fluidName(net.minecraft.world.level.material.Fluid fluid) {
        return fluid.getFluidType().getDescription();
    }

    @Override
    public int fluidTemperature(net.minecraft.world.level.material.Fluid fluid) {
        return fluid.getFluidType().getTemperature();
    }

    @Override
    public boolean fluidGaseous(net.minecraft.world.level.material.Fluid fluid) {
        return fluid.getFluidType().isLighterThanAir();
    }

    @Override
    public long fluidAmount(Object ingredient) {
        return ingredient instanceof net.neoforged.neoforge.fluids.FluidStack stack ? stack.getAmount() : -1;
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        NeoClientNet.send(payload);
    }

    @Override
    public boolean serverHas(CustomPacketPayload.Type<?> type) {
        return NeoClientNet.has(type);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }

    @Override
    public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return player.connection.hasChannel(type.id());
    }
}
