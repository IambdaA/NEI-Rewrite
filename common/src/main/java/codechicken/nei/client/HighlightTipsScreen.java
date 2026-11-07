package codechicken.nei.client;

import codechicken.nei.NEIConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * NEI's {@code GuiHighlightTips}, the screen behind World > Highlight Tooltips: one 80 x 20 button in the middle (Shown /
 * Hidden) and, while shown, a sample box (a block of redstone) that is dragged to where the tooltip should be. The position
 * is kept as 0 - 10000 across the free area, snapping to 25 %, 50 % and 75 %, as in NEI. Escape and Backspace go back.
 */
final class HighlightTipsScreen extends Screen {

    private static final int KEY_ESCAPE = 256;
    private static final int KEY_BACKSPACE = 259;

    private final Screen parent;
    private boolean dragging;
    private int downX;
    private int downY;

    HighlightTipsScreen(Screen parent) {
        super(Component.literal("Highlight Tooltips"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private static boolean shown() {
        return NEIConfig.HUD_ENABLED.getAsBoolean();
    }

    private int buttonX() {
        return (width - 80) / 2;
    }

    private int buttonY() {
        return (height - 20) / 2;
    }

    private String sampleName() {
        return Items.REDSTONE_BLOCK.getDefaultInstance().getHoverName().getString();
    }

    private int sampleW() {
        return HighlightTips.boxWidth(Minecraft.getInstance().font, sampleName(), "Sample");
    }

    /** NEI's {@code getDrag}: the mouse movement since the press as a change of the 0 - 10000 position. */
    private int[] drag(int mx, int my) {
        final int freeX = Math.max(1, width - 1 - sampleW());
        final int freeY = Math.max(1, height - 1 - HighlightTips.BOX_H);
        final int px = NEIConfig.HUD_X.getAsInt();
        final int py = NEIConfig.HUD_Y.getAsInt();
        final int dx = Math.max(-px, Math.min(10000 - px, (mx - downX) * 10000 / freeX));
        final int dy = Math.max(-py, Math.min(10000 - py, (my - downY) * 10000 / freeY));
        return new int[] { dx, dy };
    }

    private int[] renderPos(int mx, int my) {
        int x = NEIConfig.HUD_X.getAsInt();
        int y = NEIConfig.HUD_Y.getAsInt();
        if (dragging) {
            final int[] d = drag(mx, my);
            x += d[0];
            y += d[1];
        }
        for (int i = 25; i < 100; i += 25) { // snapping
            if (x / 100 == i) x = i * 100;
            if (y / 100 == i) y = i * 100;
        }
        return new int[] { x, y };
    }

    private int[] box(int mx, int my) {
        final int[] pos = renderPos(mx, my);
        return new int[] { (width - 1 - sampleW()) * pos[0] / 10000, (height - 1 - HighlightTips.BOX_H) * pos[1] / 10000,
                sampleW(), HighlightTips.BOX_H };
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        g.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);
        final Font font = Minecraft.getInstance().font;
        final boolean hot = Draw.in(mx, my, buttonX(), buttonY(), 80, 20);
        Draw.neiButton(g, buttonX(), buttonY(), 80, 20, hot ? 2 : 1);
        final String text = shown() ? "Shown" : "Hidden";
        g.text(font, Component.literal(text), buttonX() + (80 - font.width(text)) / 2, buttonY() + 6,
                hot ? 0xFFFFFFA0 : 0xFFE0E0E0, true);
        if (shown()) {
            final int[] b = box(mx, my);
            final ItemStack stack = Items.REDSTONE_BLOCK.getDefaultInstance();
            HighlightTips.drawBox(g, font, stack, sampleName(), "Sample", b[0], b[1]);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        final int mx = (int) event.x();
        final int my = (int) event.y();
        if (event.button() == 0 && Draw.in(mx, my, buttonX(), buttonY(), 80, 20)) { // GuiCCButton acts when pressed
            NEIConfig.HUD_ENABLED.set(!shown());
            NEIConfig.changed();
            return true;
        }
        if (event.button() == 0 && shown()) {
            final int[] b = box(mx, my);
            if (Draw.in(mx, my, b[0], b[1], b[2], b[3])) {
                dragging = true;
                downX = mx;
                downY = my;
            }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0 && dragging) {
            final int[] pos = renderPos((int) event.x(), (int) event.y());
            NEIConfig.HUD_X.set(pos[0]);
            NEIConfig.HUD_Y.set(pos[1]);
            NEIConfig.changed();
            dragging = false;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == KEY_ESCAPE || event.key() == KEY_BACKSPACE) {
            Minecraft.getInstance().gui.setScreen(parent);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public void removed() {
        NEIConfig.flush();
    }
}
