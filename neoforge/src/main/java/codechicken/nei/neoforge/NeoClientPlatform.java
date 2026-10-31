package codechicken.nei.neoforge;

import com.mojang.blaze3d.platform.InputConstants;

import codechicken.nei.client.ClientPlatform;
import codechicken.nei.client.NEIKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.fluids.FluidStack;

public final class NeoClientPlatform implements ClientPlatform {

    @Override
    public KeyMapping.Category category(Identifier id) {
        return new KeyMapping.Category(id); // registered in NeoForgeClient (RegisterKeyMappingsEvent)
    }

    @Override
    public KeyMapping newKey(String name, boolean guiOnly, int glfwKey) {
        // a GUI-only key does not conflict with (and never steals) the game's own keys
        return new KeyMapping(name, guiOnly ? KeyConflictContext.GUI : KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, glfwKey, NEIKeys.CATEGORY);
    }

    @Override
    public InputConstants.Key boundKey(KeyMapping mapping) {
        return mapping.getKey();
    }

    @Override
    public int fluidColor(Fluid fluid, FluidModel model) {
        final FluidTintSource tint = model.fluidTintSource();
        return tint == null ? 0xFFFFFFFF : tint.colorAsStack(new FluidStack(fluid, 1000));
    }
}
