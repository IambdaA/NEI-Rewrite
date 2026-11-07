package codechicken.nei.client;

import java.util.List;

import codechicken.nei.ItemList;
import codechicken.nei.NEIConfig;
import codechicken.nei.item.ItemEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * NEI's {@code GuiPanelSettings}, the screen behind World > Panels Settings: the inventory in the middle, and on each side a
 * frame that stands for the bookmarks (left) and the item panel (right). Dragging a frame's middle moves it, dragging one of
 * its four handles moves that edge. The result is kept as NEI keeps it: each edge's distance from its side of the free area
 * in hundred-thousandths of that area ({@code world.panels.bookmarks.left} ... in NEI). Escape and Backspace go back.
 */
final class PanelSettingsScreen extends Screen {

    private static final int KEY_ESCAPE = 256;
    private static final int KEY_BACKSPACE = 259;
    private static final int X_SIZE = 176;
    private static final int Y_SIZE = 198;
    private static final ResourceLocation INVENTORY = ResourceLocation.withDefaultNamespace("textures/gui/container/inventory.png");

    private final Screen parent;
    private final Placeholder bookmarks = new Placeholder(NEIConfig.PANEL_BOOKMARKS);
    private final Placeholder items = new Placeholder(NEIConfig.PANEL_ITEMS);

    PanelSettingsScreen(Screen parent) {
        super(Component.literal("Panels Settings"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }

    private int guiLeft() {
        return (width - X_SIZE) / 2;
    }

    /** NEI's PanelPlaceholder: one panel's frame with its four resize handles. */
    private static final class Placeholder {

        static final int PANEL = 16;

        final NEIConfig.PanelEdges edges;
        int mx0, my0, mw, mh; // the free area (NEI's margin)
        int x, y, w, h;
        String dragDir;
        int downX, downY;

        Placeholder(NEIConfig.PanelEdges edges) {
            this.edges = edges;
        }

        void resize(int ax, int ay, int aw, int ah, int mouseX, int mouseY) {
            mx0 = ax;
            my0 = ay;
            mw = Math.max(1, aw);
            mh = Math.max(1, ah);
            final int[] p = paddings(mouseX, mouseY);
            x = mx0 + p[0];
            y = my0 + p[1];
            w = mw - p[2] - p[0];
            h = mh - p[3] - p[1];
        }

        /** NEI's {@code getPaddings}, with the drag in progress applied. */
        int[] paddings(int mouseX, int mouseY) {
            final int min = ItemPanel.SLOT;
            int left = (int) Math.ceil(mw * edges.left().getAsInt() / 100000.0);
            int top = (int) Math.ceil(mh * edges.top().getAsInt() / 100000.0);
            int right = (int) Math.ceil(mw * edges.right().getAsInt() / 100000.0);
            int bottom = (int) Math.ceil(mh * edges.bottom().getAsInt() / 100000.0);

            if (dragDir != null) {
                final int dx = mouseX - downX;
                final int dy = mouseY - downY;
                switch (dragDir) {
                    case "left" -> left = Math.min(Math.max(0, mw - right - min), Math.max(0, left + dx));
                    case "right" -> right = Math.min(Math.max(0, mw - left - min), Math.max(0, right - dx));
                    case "top" -> top = Math.min(Math.max(0, mh - bottom - min), Math.max(0, top + dy));
                    case "bottom" -> bottom = Math.min(Math.max(0, mh - top - min), Math.max(0, bottom - dy));
                    default -> { // move
                        final int width = mw - right - left;
                        final int height = mh - top - bottom;
                        left = Math.min(Math.max(0, mw - width), Math.max(0, left + dx));
                        top = Math.min(Math.max(0, mh - height), Math.max(0, top + dy));
                        right = mw - left - width;
                        bottom = mh - top - height;
                    }
                }
            }

            final int deltaHeight = Math.min(0, mh - top - bottom - min) / 2;
            left = Math.min(left, Math.max(0, mw - right - min));
            right = Math.min(right, Math.max(0, mw - left - min));
            top = Math.min(mh - min, Math.max(0, top + deltaHeight));
            bottom = Math.min(mh - top - min, Math.max(0, bottom - deltaHeight));
            return new int[] { left, top, right, bottom };
        }

        private boolean in(int px, int py, int bx, int by, int bw, int bh) {
            return px >= bx && py >= by && px < bx + bw && py < by + bh;
        }

        int[] leftButton() {
            return new int[] { x, y + h / 2 - PANEL, PANEL, PANEL * 2 };
        }

        int[] rightButton() {
            return new int[] { x + w - PANEL, y + h / 2 - PANEL, PANEL, PANEL * 2 };
        }

        int[] topButton() {
            return new int[] { x + w / 2 - PANEL, y, PANEL * 2, PANEL };
        }

        int[] bottomButton() {
            return new int[] { x + w / 2 - PANEL, y + h - PANEL, PANEL * 2, PANEL };
        }

        private void handle(GuiGraphics g, int[] b, int mouseX, int mouseY) {
            Gfx.fill(g, b[0], b[1], b[0] + b[2], b[1] + b[3], 0xBBAAAAAA);
            Gfx.fill(g, b[0] + 2, b[1] + 2, b[0] + b[2] - 2, b[1] + b[3] - 2,
                    in(mouseX, mouseY, b[0], b[1], b[2], b[3]) ? 0xFF000000 : 0xBB000000);
        }

        void draw(GuiGraphics g, int mouseX, int mouseY) {
            Gfx.fill(g, x, y, x + w, y + PANEL, 0xBBAAAAAA); // top bar
            Gfx.fill(g, x, y + h - PANEL, x + w, y + h, 0xBBAAAAAA); // bottom bar
            Gfx.fill(g, x, y, x + 2, y + h, 0xBBAAAAAA);
            Gfx.fill(g, x + w - 2, y, x + w, y + h, 0xBBAAAAAA);
            Gfx.fill(g, x, y, x + w, y + 2, 0xBBAAAAAA);
            Gfx.fill(g, x, y + h - 2, x + w, y + h, 0xBBAAAAAA);
            drawItems(g);
            handle(g, leftButton(), mouseX, mouseY);
            handle(g, rightButton(), mouseX, mouseY);
            handle(g, topButton(), mouseX, mouseY);
            handle(g, bottomButton(), mouseX, mouseY);
        }

        /** NEI fills the frame with the item list's items, repeated. */
        private void drawItems(GuiGraphics g) {
            final int slot = ItemPanel.SLOT;
            final int columns = (w - 2) / slot;
            final int rows = (h - (PANEL + 2) * 2 - 2) / slot;
            final int padLeft = (w % slot) / 2;
            final List<ItemEntry> list = ItemList.getFiltered();
            if (list.isEmpty() || columns <= 0 || rows <= 0) return;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < columns; c++) {
                    final int sx = x + padLeft + slot * c;
                    final int sy = y + PANEL + 2 + slot * r;
                    Draw.beginSlotScale(g, sx, sy, slot);
                    Draw.stack(g, list.get((r * columns + c) % list.size()).stack, sx + 1, sy + 1);
                    Draw.endSlotScale(g, slot);
                }
            }
        }

        boolean press(int px, int py) {
            if (!in(px, py, x, y, w, h)) return false;
            downX = px;
            downY = py;
            if (in(px, py, leftButton())) dragDir = "left";
            else if (in(px, py, rightButton())) dragDir = "right";
            else if (in(px, py, topButton())) dragDir = "top";
            else if (in(px, py, bottomButton())) dragDir = "bottom";
            else dragDir = "move";
            return true;
        }

        private boolean in(int px, int py, int[] b) {
            return in(px, py, b[0], b[1], b[2], b[3]);
        }

        /** NEI's {@code mouseMovedOrUp}: the new edges are written as parts of the free area. */
        void release(int px, int py) {
            if (dragDir == null) return;
            final int[] p = paddings(px, py);
            edges.left().set(p[0] * 100000 / mw);
            edges.top().set(p[1] * 100000 / mh);
            edges.right().set(p[2] * 100000 / mw);
            edges.bottom().set(p[3] * 100000 / mh);
            NEIConfig.changed();
            dragDir = null;
        }
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        Gfx.fillGradient(g, 0, 0, width, height, 0xC0101010, 0xD0101010);
        final int guiLeft = guiLeft();
        final int guiTop = (height - Y_SIZE) / 2;
        Tex.blit(g, INVENTORY, 256, 256, guiLeft, guiTop + (Y_SIZE - 166) / 2, 0, 0, 176, 166);

        // NEI: the bookmarks get the space left of the inventory, the item panel the space right of it
        bookmarks.resize(2, 2, guiLeft - 4, height - 4, mx, my);
        items.resize(guiLeft + X_SIZE + 2, 2, width - (guiLeft + X_SIZE + 4), height - 4, mx, my);
        bookmarks.draw(g, mx, my);
        items.draw(g, mx, my);
    }

    @Override
    public boolean mouseClicked(double ex, double ey, int eb) {
        if (eb == 0) {
            final int mx = (int) ex;
            final int my = (int) ey;
            if (!bookmarks.press(mx, my)) items.press(mx, my);
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double ex, double ey, int eb) {
        if (eb == 0) {
            bookmarks.release((int) ex, (int) ey);
            items.release((int) ex, (int) ey);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int ek, int es, int em) {
        if (ek == KEY_ESCAPE || ek == KEY_BACKSPACE) {
            Minecraft.getInstance().setScreen(parent);
            return true;
        }
        return super.keyPressed(ek, es, em);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void removed() {
        NEIConfig.flush();
    }
}
