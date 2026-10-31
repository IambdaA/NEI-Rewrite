package codechicken.nei.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Small drawing helpers that imitate the vanilla container look using only rectangle fills. */
final class Draw {

    private Draw() {}

    /** Draws an item stack, or a fluid display item as its fluid. Every place that draws stacks goes through this. */
    static void stack(GuiGraphicsExtractor g, net.minecraft.world.item.ItemStack stack, int x, int y) {
        if (codechicken.nei.item.FluidDisplay.is(stack)) FluidDraw.draw(g, stack, x, y);
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

    /** NEI's dark slate bevelled button (colours sampled from NEI 1.7.10): black edge, light top and left, dark bottom and right. */
    static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hover) {
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, hover ? 0xFF35404A : 0xFF262E35);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0xFF40474D);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, 0xFF4F5962);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, 0xFF161D23);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 2, 0xFF161D23);
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

    /** Text at half size, as NEI draws amounts and markers over a slot ({@code NEIClientUtils#drawNEIOverlayText}). */
    static void smallText(GuiGraphicsExtractor g, Font font, String text, float x, float y, int color) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().scale(0.5f, 0.5f);
        g.text(font, Component.literal(text), 0, 0, 0xFF000000 | color, true);
        g.pose().popMatrix();
    }
}
