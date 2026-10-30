package codechicken.nei.client;

import codechicken.nei.NotEnoughItems;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Texture and clipping calls in one place, so a renamed Minecraft call only needs fixing here.
 */
final class Tex {

    /** NEI's crafting tree sprite sheet (256 x 256), taken from the GTNH NEI resources. */
    static final ResourceLocation TREE = ResourceLocation.fromNamespaceAndPath(NotEnoughItems.MODID,
            "textures/gui/craftingtree.png");

    /** NEI's tab / toolbar sprites (256 x 256): bookmark icons, wrench, recipe tab backgrounds. */
    static final ResourceLocation SPRITES = sheet("nei_tabbed_sprites");
    /** The recipe window background (256 x 256, the window is its top left 184 x 174). */
    static final ResourceLocation RECIPE_BG = sheet("recipebg");
    /** NEI's icon sheet: a 512 x 512 file whose coordinates are in 256 x 256 units (draw it with texture size 256). */
    static final ResourceLocation ICONS = sheet("nei_sprites");
    /** The station column background (36 x 36). */
    static final ResourceLocation CATALYST = sheet("catalyst_tab");

    private Tex() {}

    private static ResourceLocation sheet(String name) {
        return ResourceLocation.fromNamespaceAndPath(NotEnoughItems.MODID, "textures/gui/" + name + ".png");
    }

    /** Draws the {@code w x h} part of any sheet that starts at {@code (u, v)}. */
    static void blit(GuiGraphics g, ResourceLocation tex, int texW, int texH, int x, int y, int u, int v, int w,
            int h) {
        Gfx.blit(g, tex, x, y, (float) u, (float) v, w, h, texW, texH);
    }

    /**
     * Nine-slice: the {@code sw x sh} region at {@code (u, v)} with border widths {@code l, t, r, b}, drawn at
     * {@code w x h}. Edges and middle are tiled (NEI's {@code DrawableBuilder#setSlices}).
     */
    static void nine(GuiGraphics g, ResourceLocation tex, int texW, int texH, int x, int y, int w, int h, int u,
            int v, int sw, int sh, int l, int t, int r, int b) {
        final int mw = sw - l - r;
        final int mh = sh - t - b;
        for (int dy = t; dy < h - b; dy += mh) {
            final int ph = Math.min(mh, h - b - dy);
            for (int dx = l; dx < w - r; dx += mw) {
                blit(g, tex, texW, texH, x + dx, y + dy, u + l, v + t, Math.min(mw, w - r - dx), ph);
            }
            blit(g, tex, texW, texH, x, y + dy, u, v + t, l, ph);
            blit(g, tex, texW, texH, x + w - r, y + dy, u + sw - r, v + t, r, ph);
        }
        for (int dx = l; dx < w - r; dx += mw) {
            final int pw = Math.min(mw, w - r - dx);
            blit(g, tex, texW, texH, x + dx, y, u + l, v, pw, t);
            blit(g, tex, texW, texH, x + dx, y + h - b, u + l, v + sh - b, pw, b);
        }
        blit(g, tex, texW, texH, x, y, u, v, l, t);
        blit(g, tex, texW, texH, x + w - r, y, u + sw - r, v, r, t);
        blit(g, tex, texW, texH, x, y + h - b, u, v + sh - b, l, b);
        blit(g, tex, texW, texH, x + w - r, y + h - b, u + sw - r, v + sh - b, r, b);
    }

    /** Draws the {@code w x h} part of the 256 x 256 sheet that starts at {@code (u, v)} with its corner at {@code (x, y)}. */
    static void blit(GuiGraphics g, int x, int y, int u, int v, int w, int h) {
        
        Gfx.blit(g, TREE, x, y, (float) u, (float) v, w, h, 256, 256);
    }

    /** Everything drawn until {@link #endClip} is cut off outside this rectangle (GUI coordinates). */
    static void beginClip(GuiGraphics g, int x, int y, int w, int h) {
        g.enableScissor(x, y, x + w, y + h);
    }

    static void endClip(GuiGraphics g) {
        g.disableScissor();
    }

    /**
     * The tree window's frame: NEI's nine-slice background (corners 9 / 9 / 22 / 9) from the sprite sheet. The middle
     * of the sprite is one flat colour, so it is filled instead of tiled; the four edges are tiled.
     */
    static void frame(GuiGraphics g, int x, int y, int w, int h) {
        final int left = 9;
        final int right = 9;
        final int top = 22;
        final int bottom = 9;
        final int size = 64;
        final int tileW = size - left - right;
        final int tileH = size - top - bottom;

        Gfx.fill(g, x + left, y + top, x + w - right, y + h - bottom, 0xFF101018);

        blit(g, x, y, 0, 0, left, top);
        blit(g, x + w - right, y, size - right, 0, right, top);
        blit(g, x, y + h - bottom, 0, size - bottom, left, bottom);
        blit(g, x + w - right, y + h - bottom, size - right, size - bottom, right, bottom);

        for (int dx = x + left; dx < x + w - right; dx += tileW) {
            final int len = Math.min(tileW, x + w - right - dx);
            blit(g, dx, y, left, 0, len, top);
            blit(g, dx, y + h - bottom, left, size - bottom, len, bottom);
        }
        for (int dy = y + top; dy < y + h - bottom; dy += tileH) {
            final int len = Math.min(tileH, y + h - bottom - dy);
            blit(g, x, dy, 0, top, left, len);
            blit(g, x + w - right, dy, size - right, top, right, len);
        }
    }
}
