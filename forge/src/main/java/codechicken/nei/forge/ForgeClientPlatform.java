package codechicken.nei.forge;

import com.mojang.blaze3d.platform.InputConstants;

import codechicken.nei.client.ClientPlatform;
import codechicken.nei.client.NEIKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.client.settings.KeyConflictContext;

public final class ForgeClientPlatform implements ClientPlatform {

    @Override
    public KeyMapping newKey(String name, boolean guiOnly, int glfwKey) {
        // a GUI-only key does not conflict with (and never steals) the game's own keys
        return new KeyMapping(name, guiOnly ? KeyConflictContext.GUI : KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, glfwKey, NEIKeys.CATEGORY);
    }

    @Override
    public FluidLook fluidLook(Fluid fluid) {
        final IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid);
        final ResourceLocation tex = ext.getStillTexture();
        if (tex == null) return null;
        final TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(tex);
        int color = ext.getTintColor();
        if (fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER) {
            color = 0xFF3F76E4; // vanilla's water asks the biome, and there is no biome here
        } else if ((color >>> 24) == 0) {
            color |= 0xFF000000;
        }
        return new FluidLook(sprite, color);
    }

    // Forge adds public getters for the container screen's frame (the fields are protected in 1.20.1)

    @Override
    public int guiLeft(AbstractContainerScreen<?> screen) {
        return screen.getGuiLeft();
    }

    @Override
    public int guiTop(AbstractContainerScreen<?> screen) {
        return screen.getGuiTop();
    }

    @Override
    public int guiWidth(AbstractContainerScreen<?> screen) {
        return screen.getXSize();
    }

    @Override
    public int guiHeight(AbstractContainerScreen<?> screen) {
        return screen.getYSize();
    }

    @Override
    public Slot hoveredSlot(AbstractContainerScreen<?> screen) {
        return screen.getSlotUnderMouse();
    }
}
