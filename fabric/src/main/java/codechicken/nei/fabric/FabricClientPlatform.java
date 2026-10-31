package codechicken.nei.fabric;

import com.mojang.blaze3d.platform.InputConstants;

import codechicken.nei.client.ClientPlatform;
import codechicken.nei.client.NEIKeys;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public final class FabricClientPlatform implements ClientPlatform {

    @Override
    public KeyMapping.Category category(Identifier id) {
        return KeyMapping.Category.register(id);
    }

    @Override
    public KeyMapping newKey(String name, boolean guiOnly, int glfwKey) {
        final KeyMapping mapping = new KeyMapping(name, InputConstants.Type.KEYSYM, glfwKey, NEIKeys.CATEGORY);
        if (guiOnly) KeyTable.register(mapping); // kept out of vanilla's one-mapping-per-key table
        return mapping;
    }

    @Override
    public InputConstants.Key boundKey(KeyMapping mapping) {
        return KeyMappingHelper.getBoundKeyOf(mapping);
    }

    @Override
    public int fluidColor(Fluid fluid, FluidModel model) {
        try {
            if (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER) return 0xFF3F76E4; // the biome decides in game
            return FluidVariantRendering.getColor(FluidVariant.of(fluid)) | 0xFF000000;
        } catch (RuntimeException e) {
            return 0xFFFFFFFF; // a handler that needs a world: draw the sprite untinted
        }
    }
}
