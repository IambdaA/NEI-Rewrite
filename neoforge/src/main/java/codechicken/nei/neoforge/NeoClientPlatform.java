package codechicken.nei.neoforge;

import com.mojang.blaze3d.platform.InputConstants;

import codechicken.nei.client.ClientPlatform;
import codechicken.nei.client.NEIKeys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

public final class NeoClientPlatform implements ClientPlatform {

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
        if ((color >>> 24) == 0) color |= 0xFF000000;
        return new FluidLook(sprite, color);
    }
}
