package codechicken.nei.fabric;

import java.nio.file.Path;

import codechicken.nei.Platform;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.material.Fluid;

public final class FabricPlatform implements Platform {

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir();
    }

    @Override
    public String modName(String namespace) {
        return FabricLoader.getInstance().getModContainer(namespace).map(c -> c.getMetadata().getName()).orElse(namespace);
    }

    @Override
    public boolean isModLoaded(String id) {
        return FabricLoader.getInstance().isModLoaded(id);
    }

    @Override
    public Component fluidName(Fluid fluid) {
        return FluidVariantAttributes.getName(FluidVariant.of(fluid));
    }

    /** JEI's Fabric fluid ingredient ({@code IJeiFluidIngredient}); read by reflection so the glue does not need JEI to compile. */
    private static java.lang.reflect.Method amountGetter;

    /** JEI's Fabric fluid ingredient counts in droplets (81 per millibucket). */
    @Override
    public long fluidAmount(Object ingredient) {
        try {
            if (jeiFluidClass == null) jeiFluidClass = Class.forName("mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient");
            if (!jeiFluidClass.isInstance(ingredient)) return -1;
            if (amountGetter == null) amountGetter = jeiFluidClass.getMethod("getAmount");
            return Math.max(1, ((Number) amountGetter.invoke(ingredient)).longValue() / 81);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    private static java.lang.reflect.Method variantGetter;
    private static Class<?> jeiFluidClass;

    @Override
    public Fluid fluidOf(Object ingredient) {
        try {
            if (jeiFluidClass == null) jeiFluidClass = Class.forName("mezz.jei.api.fabric.ingredients.fluids.IJeiFluidIngredient");
            if (!jeiFluidClass.isInstance(ingredient)) return null;
            if (variantGetter == null) variantGetter = jeiFluidClass.getMethod("getFluidVariant");
            return ((FluidVariant) variantGetter.invoke(ingredient)).getFluid();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        FabricClientNet.send(payload);
    }

    @Override
    public boolean serverHas(CustomPacketPayload.Type<?> type) {
        return FabricClientNet.has(type);
    }

    @Override
    public void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    @Override
    public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) {
        return ServerPlayNetworking.canSend(player, type);
    }
}
