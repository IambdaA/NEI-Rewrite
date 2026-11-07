package codechicken.nei.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Small drawing helpers that imitate the vanilla container look using only rectangle fills. */
final class Draw {

    private Draw() {}

    /** Draws an item stack, or a fluid display item as its fluid. Every place that draws stacks goes through this. */
    static void stack(GuiGraphicsExtractor g, net.minecraft.world.item.ItemStack stack, int x, int y) {
        if (codechicken.nei.item.FluidDisplay.is(stack)) {
            // a fluid its mod draws as an item of its own (NTM - Continuum's droplet icon) looks like that item; others get
            // NEI's fluid texture
            final net.minecraft.world.item.ItemStack standIn = codechicken.nei.item.FluidDisplay.representative(
                    codechicken.nei.item.FluidDisplay.fluidOf(stack));
            if (standIn.isEmpty()) FluidDraw.draw(g, stack, x, y);
            else g.item(standIn, x, y);
        }
        else {
            g.item(stack, x, y);
            if (stack.is(net.minecraft.world.item.Items.SPAWNER)) SpawnerLook.badge(g, stack, x, y); // NEI's mob inside the spawner
        }
    }

    static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** The grey raised window background used by vanilla containers. */
    static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
        g.fill(x + 1, y + 1, x + w - 2, y + 3, 0xFFFFFFFF); // top highlight
        g.fill(x + 1, y + 1, x + 3, y + h - 2, 0xFFFFFFFF); // left highlight
        g.fill(x + 2, y + h - 3, x + w - 1, y + h - 1, 0xFF555555); // bottom shadow
        g.fill(x + w - 3, y + 2, x + w - 1, y + h - 1, 0xFF555555); // right shadow
    }

    /** An 18x18 inset slot. Items are drawn at (x + 1, y + 1). */
    static void slot(GuiGraphicsExtractor g, int x, int y, int size) {
        slot(g, x, y, size, size);
    }

    static void slot(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF8B8B8B);
        g.fill(x, y, x + w - 1, y + 1, 0xFF373737);
        g.fill(x, y, x + 1, y + h - 1, 0xFF373737);
        g.fill(x + 1, y + h - 1, x + w, y + h, 0xFFFFFFFF);
        g.fill(x + w - 1, y + 1, x + w, y + h, 0xFFFFFFFF);
    }

    /** The look of the buttons: {@code NEIConfig.UI_STYLE} 0 NTNH-Approximate, 1 GTNH-Modernity, 2 Vanilla. */
    private static int style() {
        return codechicken.nei.NEIConfig.UI_STYLE.get();
    }

    /** A bevelled button: an edge, light top and left, dark bottom and right, and the face colour in between. */
    private static void bevel(GuiGraphicsExtractor g, int x, int y, int w, int h, int edge, int face, int top, int left,
            int shade) {
        g.fill(x, y, x + w, y + h, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, top);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, left);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, shade);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, shade);
    }

    /**
     * The slate button of the NTNH pack, approximated with plain fills (no texture is included): a black edge that turns
     * white under the mouse, a slate face, a lighter line on the top and left and a darker one on the bottom and right.
     * Disabled (a switched-off option) is the same pattern darker.
     */
    private static void ntnhButton(GuiGraphicsExtractor g, int x, int y, int w, int h, int state) {
        if (state == 0) bevel(g, x, y, w, h, 0xFF000000, 0xFF1C2228, 0xFF272D33, 0xFF2F363C, 0xFF11161B);
        else bevel(g, x, y, w, h, state == 2 ? 0xFFFFFFFF : 0xFF000000, 0xFF262E35, 0xFF40474D, 0xFF4F5962, 0xFF161D23);
    }

    /**
     * Modernity-GTNH's dark button, sampled from a screenshot of the multiplayer screen (colours exact for the normal state):
     * a 1 pixel edge, a one pixel light line on top, left and right, a light line again above the two dark bottom rows.
     * Mouse over and disabled were not in the screenshot; they are the same pattern a little lighter / darker.
     */
    private static void gtnhButton(GuiGraphicsExtractor g, int x, int y, int w, int h, int state) {
        final int edge = 0xFF0A090E;
        final int face = state == 0 ? 0xFF1B1C25 : state == 2 ? 0xFF2D3040 : 0xFF242631;
        final int line = state == 0 ? 0xFF22232D : state == 2 ? 0xFF3A3D50 : 0xFF2D2F3C;
        final int shade = state == 0 ? 0xFF101117 : 0xFF14151D;
        g.fill(x, y, x + w, y + h, edge);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, line); // top
        g.fill(x + 1, y + 1, x + 2, y + h - 4, line); // left
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 4, line); // right
        g.fill(x + 1, y + h - 4, x + w - 1, y + h - 3, line); // above the shade
        g.fill(x + 1, y + h - 3, x + w - 1, y + h - 1, shade);
    }

    /** The classic grey button. */
    private static void vanillaButton(GuiGraphicsExtractor g, int x, int y, int w, int h, int state) {
        if (state == 0) bevel(g, x, y, w, h, 0xFF000000, 0xFF4A4A4A, 0xFF5A5A5A, 0xFF5A5A5A, 0xFF2E2E2E);
        else bevel(g, x, y, w, h, 0xFF000000, state == 2 ? 0xFF7D87B5 : 0xFF6F6F6F, 0xFFA8A8A8, 0xFFA8A8A8, 0xFF3E3E3E);
    }

    /**
     * The button of NEI's options screen, toolbar and panels (what {@code LayoutManager.drawButtonBackground} draws from the
     * resource pack's widgets texture) in the look chosen by the UI Style option. {@code state} is NEI's: 0 disabled (a
     * switched-off option), 1 normal, 2 mouse over.
     */
    static void neiButton(GuiGraphicsExtractor g, int x, int y, int w, int h, int state) {
        switch (style()) {
            case 1 -> gtnhButton(g, x, y, w, h, state);
            case 2 -> vanillaButton(g, x, y, w, h, state);
            default -> ntnhButton(g, x, y, w, h, state);
        }
    }

    static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hover) {
        neiButton(g, x, y, w, h, hover ? 2 : 1);
    }

    private static final net.minecraft.resources.Identifier DIRT = net.minecraft.resources.Identifier
            .fromNamespaceAndPath("minecraft", "textures/gui/menu_background.png");

    /** NEI's {@code OptionScrollPane.drawOverlayTex}: the vanilla menu background tiled over the rectangle. */
    static void dirt(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        for (int dy = 0; dy < h; dy += 32) {
            final int th = Math.min(32, h - dy);
            for (int dx = 0; dx < w; dx += 32) {
                final int tw = Math.min(32, w - dx);
                g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, DIRT, x + dx, y + dy, 0f, 0f, tw, th, 32,
                        32);
            }
        }
    }

    /** A right-pointing grey arrow. {@code x} is its left end, {@code cy} its vertical centre. */
    static void arrow(GuiGraphicsExtractor g, int x, int cy) {
        g.fill(x, cy - 2, x + 16, cy + 2, 0xFF8B8B8B);
        for (int i = 0; i < 6; i++) {
            g.fill(x + 16 + i, cy - 5 + i, x + 17 + i, cy + 5 - i, 0xFF8B8B8B);
        }
    }

    /** NEI's furnace cycle: {@code TemplateRecipeHandler.cycleticks % 48}, as a 0-1 fraction (one tick is 50 ms). */
    static float cookFraction() {
        return (System.currentTimeMillis() / 50 % 48) / 48f;
    }

    /**
     * NEI's furnace arrow ({@code drawProgressBar(74, 23, 176, 14, 24, 16, 48, 0)}): the empty arrow fills from left to
     * right. {@code x} is its left end, {@code cy} its vertical centre; {@code fraction} is 0-1.
     */
    static void progressArrow(GuiGraphicsExtractor g, int x, int cy, float fraction) {
        final int filled = (int) (fraction * 22);
        for (int i = 0; i < 22; i++) {
            final int top = i < 16 ? cy - 2 : cy - 5 + (i - 16);
            final int bottom = i < 16 ? cy + 2 : cy + 5 - (i - 16);
            g.fill(x + i, top, x + i + 1, bottom, i < filled ? 0xFFFFFFFF : 0xFF8B8B8B);
        }
    }

    /** Minecraft's own furnace flame (the sprite the vanilla furnace screen draws), 14 x 14. */
    private static final net.minecraft.resources.Identifier FLAME = net.minecraft.resources.Identifier
            .fromNamespaceAndPath("minecraft", "container/furnace/lit_progress");

    /**
     * NEI's furnace flame ({@code drawProgressBar(51, 25, 176, 0, 14, 14, 48, 7)}): the flame burns down from the top over the
     * cycle; what has burnt away stays as a faint trace. {@code x, y} is its top left corner; {@code fraction} is 0-1.
     */
    static void flame(GuiGraphicsExtractor g, int x, int y, float fraction) {
        g.blitSprite(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, FLAME, x, y, 14, 14);
        final int burnt = (int) (fraction * 14);
        if (burnt > 0) g.fill(x, y, x + 14, y + burnt, 0xC0C6C6C6); // the window's grey, nearly opaque
    }

    /**
     * NEI's {@code panelFactor}: with Item Panel Scale the slot is not 18 px, so what is drawn for an 18 px slot at
     * {@code (x, y)} (item at +1, amounts, markers) is scaled to fit. Pair with {@link #endSlotScale}.
     */
    static void beginSlotScale(GuiGraphicsExtractor g, int x, int y, int slot) {
        if (slot == 18) return;
        final float f = (slot - 2) / 16f;
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(f, f);
        g.pose().translate(-x, -y);
    }

    static void endSlotScale(GuiGraphicsExtractor g, int slot) {
        if (slot != 18) g.pose().popMatrix();
    }

    /** Text at half size, as NEI draws amounts and markers over a slot ({@code NEIClientUtils#drawNEIOverlayText}). */
    static void smallText(GuiGraphicsExtractor g, Font font, String text, float x, float y, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(0.5f, 0.5f);
        g.text(font, Component.literal(text), 0, 0, 0xFF000000 | color, true);
        g.pose().popMatrix();
    }
}
