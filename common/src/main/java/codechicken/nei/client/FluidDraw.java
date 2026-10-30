package codechicken.nei.client;

import codechicken.nei.item.FluidDisplay;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/**
 * Draws a fluid display item. Split out of {@link FluidDisplay}, which is loaded on dedicated servers too and so must not
 * mention client classes.
 */
final class FluidDraw {

    private static final java.util.Set<Fluid> REPORTED = new java.util.HashSet<>();

    private FluidDraw() {}

    /** Draws the fluid's still sprite, tinted, in a 16 x 16 box, the way JEI draws a fluid ingredient. */
    static void draw(GuiGraphics g, ItemStack stack, int x, int y) {
        final Fluid fluid = FluidDisplay.fluidOf(stack);
        if (fluid == null) return;
        final ClientPlatform.FluidLook look = ClientPlatform.HOLDER.get().fluidLook(fluid);
        if (look == null || look.sprite() == null) {
            if (REPORTED.add(fluid)) codechicken.nei.NotEnoughItems.LOGGER.warn("NEI cannot draw fluid {}: no still sprite", fluid);
            return;
        }
        final int color = look.color();
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(x, y, 0, 16, 16, look.sprite(), (color >> 16 & 255) / 255f, (color >> 8 & 255) / 255f, (color & 255) / 255f,
                (color >>> 24) / 255f);
        RenderSystem.enableDepthTest();
    }
}
