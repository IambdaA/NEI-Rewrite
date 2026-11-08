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
        final net.minecraft.world.item.ItemStackTemplate rest = stack.getItem().getCraftingRemainder(stack);
        return rest != null && rest.item().value() == stack.getItem();
    }

    @Override
    public net.minecraft.network.chat.Component fluidName(net.minecraft.world.level.material.Fluid fluid) {
        return fluid.getFluidType().getDescription();
    }

    @Override
    public java.util.List<net.minecraft.network.chat.Component> fluidTooltip(net.minecraft.world.level.material.Fluid fluid,
            boolean advanced) {
        final var mc = net.minecraft.client.Minecraft.getInstance();
        final java.util.List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
        final var stack = new net.neoforged.neoforge.fluids.FluidStack(fluid, 1000);
        lines.add(stack.getHoverName());
        net.neoforged.neoforge.event.EventHooks.onFluidTooltip(stack, mc.player, lines,
                advanced ? net.minecraft.world.item.TooltipFlag.ADVANCED : net.minecraft.world.item.TooltipFlag.NORMAL,
                net.minecraft.world.item.Item.TooltipContext.of(mc.level));
        return lines.subList(1, lines.size());
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
    public net.minecraft.world.level.material.Fluid fluidOf(Object ingredient) {
        return ingredient instanceof net.neoforged.neoforge.fluids.FluidStack stack ? stack.getFluid() : null;
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
        return player.connection.hasChannel(type);
    }
}
