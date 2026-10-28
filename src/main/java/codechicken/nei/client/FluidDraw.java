package codechicken.nei.client;

import codechicken.nei.item.FluidDisplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.client.fluid.FluidTintSource;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Draws a fluid display item. Split out of {@link FluidDisplay}, which is loaded on dedicated servers too and so must not
 * mention client classes.
 */
final class FluidDraw {

    private FluidDraw() {}

    /** Draws the fluid's still sprite, tinted, in a 16 x 16 box, the way JEI draws a fluid ingredient. */
    static void draw(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        final Fluid fluid = FluidDisplay.fluidOf(stack);
        if (fluid == null) return;
        final FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet()
                .get(fluid.defaultFluidState());
        final TextureAtlasSprite sprite = model.stillMaterial().sprite();
        if (sprite == null || sprite.atlasLocation().equals(MissingTextureAtlasSprite.getLocation())) return;
        final FluidTintSource tint = model.fluidTintSource();
        final int color = tint == null ? 0xFFFFFFFF : tint.colorAsStack(new FluidStack(fluid, 1000));
        final SpriteContents contents = sprite.contents();
        g.enableScissor(x, y, x + 16, y + 16);
        g.blitTiledSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, 16, 16, 0, 0, contents.width(), contents.height(),
                contents.width(), contents.height(), color);
        g.disableScissor();
    }
}
