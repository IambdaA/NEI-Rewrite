package codechicken.nei.fabric;

import com.mojang.blaze3d.platform.InputConstants;

import codechicken.nei.client.ClientPlatform;
import codechicken.nei.client.NEIKeys;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

public final class FabricClientPlatform implements ClientPlatform {

    @Override
    public KeyMapping newKey(String name, boolean guiOnly, int glfwKey) {
        final KeyMapping mapping = new KeyMapping(name, InputConstants.Type.KEYSYM, glfwKey, NEIKeys.CATEGORY);
        if (guiOnly) KeyTable.register(mapping); // kept out of vanilla's one-mapping-per-key table
        return mapping;
    }

    @Override
    public FluidLook fluidLook(Fluid fluid) {
        final FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(fluid);
        if (handler == null) return null;
        final FluidState state = fluid.defaultFluidState();
        final TextureAtlasSprite[] sprites = handler.getFluidSprites(null, null, state);
        if (sprites == null || sprites.length == 0 || sprites[0] == null) return null;

        int color = 0xFFFFFFFF;
        if (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER) {
            color = 0xFF3F76E4; // vanilla's water handler asks the biome, and there is no biome here
        } else {
            try {
                color = 0xFF000000 | handler.getFluidColor(null, null, state);
            } catch (RuntimeException e) {
                // a handler that needs a world: draw the sprite untinted
            }
        }
        return new FluidLook(sprites[0], color);
    }
}
