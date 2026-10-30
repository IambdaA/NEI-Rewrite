package codechicken.nei.client;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Every drawing call of the mod goes through here. Minecraft 1.21.1 draws the GUI with depth testing (later 26.x versions
 * draw strictly in call order), so a plain {@code fill} after an item would end up behind it. These helpers give the
 * painter's order NEI's panels were written for: rectangles and textures ignore depth, text is lifted above items, and
 * {@link #layer} starts a fresh depth buffer for a window that opens over other items.
 */
public final class Gfx {

    /** Above the item (z 150 .. 160) and its count (z 200) of any slot. */
    private static final int TEXT_Z = 250;

    private Gfx() {}

    /** A filled rectangle that is drawn over everything drawn before it (no depth test). */
    public static void fill(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        g.fill(RenderType.guiOverlay(), x1, y1, x2, y2, color);
    }

    public static void text(GuiGraphics g, Font font, Component text, int x, int y, int color, boolean shadow) {
        g.pose().pushPose();
        g.pose().translate(0f, 0f, TEXT_Z);
        g.drawString(font, text, x, y, color, shadow);
        g.pose().popPose();
    }

    public static void text(GuiGraphics g, Font font, String text, int x, int y, int color, boolean shadow) {
        g.pose().pushPose();
        g.pose().translate(0f, 0f, TEXT_Z);
        g.drawString(font, text, x, y, color, shadow);
        g.pose().popPose();
    }

    public static void item(GuiGraphics g, ItemStack stack, int x, int y) {
        g.renderItem(stack, x, y);
    }

    /** The stack count and the damage bar of a slot. */
    public static void decorations(GuiGraphics g, Font font, ItemStack stack, int x, int y) {
        g.renderItemDecorations(font, stack, x, y);
    }

    /** A part of a texture sheet, drawn over everything before it. */
    public static void blit(GuiGraphics g, ResourceLocation tex, int x, int y, float u, float v, int w, int h, int texW,
            int texH) {
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(tex, x, y, u, v, w, h, texW, texH);
        RenderSystem.enableDepthTest();
    }

    /** A GUI sprite (the {@code gui/sprites} atlas), drawn over everything before it. */
    public static void sprite(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h) {
        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blitSprite(sprite, x, y, w, h);
        RenderSystem.enableDepthTest();
    }

    /** Starts a new layer: what is drawn next cannot be hidden by items drawn so far (a window over the item panel). */
    public static void layer(GuiGraphics g) {
        g.flush();
        RenderSystem.clear(256, net.minecraft.client.Minecraft.ON_OSX); // GL_DEPTH_BUFFER_BIT
    }
}
