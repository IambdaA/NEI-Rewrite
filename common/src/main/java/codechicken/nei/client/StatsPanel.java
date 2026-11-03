package codechicken.nei.client;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

import codechicken.nei.bookmark.ReadableNumber;
import codechicken.nei.bookmark.TreeStats;
import codechicken.nei.bookmark.TreeView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * NEI's stats panel under the crafting tree ({@code StatsPanel}): labelled blocks of slots, the layout copied from
 * {@code layoutSections}. The numbers come from {@link TreeStats}. It scrolls sideways with the wheel when it is wider
 * than the window.
 *
 * shortcut: no draggable scrollbar thumb (NEI has one), the wheel scrolls; upgrade if the panel often overflows.
 */
final class StatsPanel {

    private static final int SLOT = 18;
    private static final int INLINE_GAP = 2;
    private static final int BLOCK_GAP = 2;
    private static final int COLUMN_GAP = 10;
    private static final int PAD_BLOCK = 4;
    private static final int PAD_INLINE = 1;
    private static final int HOVER = 0x66999999;
    private static final int SCROLL_STEP = 20;

    /** What the panel needs from the window. */
    interface Lookup {

        ItemStack stack(String itemId);

        /** The station icon of a recipe or recipe type, or empty. */
        ItemStack station(String itemId, String recipeId);

        ItemStack handlerStation(String handlerName);
    }

    /** One slot: an item entry or a handler. */
    record Cell(int x, int y, TreeStats.Entry entry, TreeStats.Handler handler) {

        /** What the tree highlights when this cell is clicked: "i:" item or "h:" handler. */
        String key() {
            return entry != null ? "i:" + entry.itemId() : "h:" + handler.name();
        }
    }

    private record Label(String text, int x, int y) {}

    private final List<Cell> cells = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private List<TreeStats.Block> blocks = List.of();

    // what the blocks were built from, so they are rebuilt only when it changes
    private Object lastMath;
    private Set<TreeStats.Section> lastSections;
    private boolean lastSnapshot;
    private int lastW = -1;
    private int lastH = -1;

    private int x;
    private int y;
    private int w;
    private int h;
    private int contentW;
    private int shift;
    private int scroll;

    /** Panel height, 0 when there is nothing to show. */
    int height() {
        return h;
    }

    /** The part of the panel that has content, as {x, y, width, height} in GUI pixels (for the tree image), or null. */
    int[] contentArea() {
        if (h <= 0) return null;
        final int left = Math.max(x, x + shift - PAD_BLOCK);
        final int right = Math.min(x + w, x + shift + contentW + PAD_BLOCK);
        return new int[] { left, y, right - left, h };
    }

    boolean contains(double mx, double my) {
        return h > 0 && Draw.in(mx, my, x, y, w, h);
    }

    /**
     * Rebuilds when the tree, the sections or the window changed. {@code wx, wy, ww, wh} is the whole area of the tree
     * window; the panel sits at its bottom.
     */
    void update(TreeView view, boolean visible, int wx, int wy, int ww, int wh, BiFunction<String, String, String> handlerOf) {
        final Set<TreeStats.Section> sections = view.state.statsSections.isEmpty() ? EnumSet.noneOf(TreeStats.Section.class)
                : EnumSet.copyOf(view.state.statsSections);
        if (!visible || view.isEmpty()) {
            blocks = List.of();
            cells.clear();
            labels.clear();
            h = 0;
            lastMath = null;
            return;
        }

        if (view.math == lastMath && sections.equals(lastSections) && view.state.useInventorySnapshot == lastSnapshot
                && ww == lastW && wh == lastH) {
            return;
        }
        lastMath = view.math;
        lastSections = sections;
        lastSnapshot = view.state.useInventorySnapshot;
        lastW = ww;
        lastH = wh;

        blocks = TreeStats.of(view.math, view.graph, lastSnapshot, sections, handlerOf);
        layout(wx, wy, ww, wh);
    }

    /** NEI's {@code layoutSections}. */
    private void layout(int wx, int wy, int ww, int wh) {
        cells.clear();
        labels.clear();
        if (blocks.isEmpty()) {
            h = 0;
            return;
        }

        final Font font = Minecraft.getInstance().font;
        final int lineH = font.lineHeight;
        final int count = blocks.size();
        final int[] sizes = new int[count];
        final int[] labelW = new int[count];
        final int[] columns = new int[count];
        final int maxRowWidth = Math.max(SLOT, ww);
        final int maxRows = wh / ((lineH + 4) + SLOT * 2 + BLOCK_GAP) > 3 ? 2 : 1;

        for (int i = 0; i < count; i++) {
            sizes[i] = blocks.get(i).size();
            labelW[i] = font.width(blocks.get(i).label() + ":");
            columns[i] = sizes[i] == 0 ? 0 : (sizes[i] + maxRows - 1) / maxRows;
        }

        int compact = COLUMN_GAP * Math.max(0, count - 1);
        for (int i = 0; i < count; i++) compact += blockWidth(columns[i], labelW[i]);

        // spare room lets the blocks grow wider (fewer rows), one column at a time, round robin
        if (compact <= maxRowWidth) {
            int budget = (maxRowWidth - compact) / (SLOT + INLINE_GAP);
            while (budget > 0) {
                boolean grew = false;
                for (int i = 0; i < count && budget > 0; i++) {
                    if (columns[i] < sizes[i]) {
                        columns[i]++;
                        budget--;
                        grew = true;
                    }
                }
                if (!grew) break;
            }
        }

        int cursor = 0;
        int tallest = 0;
        for (int i = 0; i < count; i++) {
            tallest = Math.max(tallest, place(blocks.get(i), columns[i], cursor, lineH));
            cursor += blockWidth(columns[i], labelW[i]) + COLUMN_GAP;
        }
        contentW = Math.max(0, cursor - COLUMN_GAP);

        final int maxVisible = maxRows * SLOT + Math.max(0, maxRows - 1) * BLOCK_GAP + lineH + 4;
        w = ww;
        x = wx;
        h = Math.min(tallest, maxVisible) + PAD_BLOCK * 2;
        y = wy + wh - h;
        shift = contentW < w ? (w - contentW) / 2 : 0;
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private static int blockWidth(int columns, int labelWidth) {
        return Math.max(columns * SLOT + Math.max(0, columns - 1) * INLINE_GAP, labelWidth);
    }

    /** Puts a label and its slots at {@code x}; returns the height used. */
    private int place(TreeStats.Block block, int columns, int bx, int lineH) {
        labels.add(new Label(block.label() + ":", bx, 0));

        final int gridY = lineH + 4;
        final int rowWidth = columns * SLOT + Math.max(0, columns - 1) * INLINE_GAP;
        int cursor = 0;
        int row = 0;
        boolean any = false;

        final List<Cell> placed = new ArrayList<>();
        for (int i = 0; i < block.size(); i++) {
            if (cursor > 0) {
                if (cursor + INLINE_GAP + SLOT > rowWidth) {
                    cursor = 0;
                    row++;
                } else {
                    cursor += INLINE_GAP;
                }
            } else if (cursor + SLOT > rowWidth) {
                cursor = 0;
                row++;
            }

            final int cx = bx + cursor;
            final int cy = gridY + row * (SLOT + BLOCK_GAP);
            if (i < block.entries().size()) placed.add(new Cell(cx, cy, block.entries().get(i), null));
            else placed.add(new Cell(cx, cy, null, block.handlers().get(i - block.entries().size())));

            cursor += SLOT;
            any = true;
        }
        cells.addAll(placed);

        final int rows = any ? row + 1 : 0;
        return gridY + rows * SLOT + Math.max(0, rows - 1) * BLOCK_GAP;
    }

    // ---- input ----

    private int viewWidth() {
        return w - PAD_INLINE * 2;
    }

    private int maxScroll() {
        return Math.max(0, contentW - viewWidth());
    }

    /** The wheel over the panel scrolls it sideways. Returns true if the panel took it. */
    boolean scrolled(double mx, double my, double dy) {
        if (!contains(mx, my)) return false;
        scroll = Math.max(0, Math.min(scroll - (dy > 0 ? 1 : -1) * SCROLL_STEP, maxScroll()));
        return true;
    }

    private int originX() {
        return x + PAD_INLINE + shift - scroll;
    }

    private int originY() {
        return y + PAD_BLOCK;
    }

    Cell cellAt(double mx, double my) {
        if (!contains(mx, my)) return null;
        for (Cell c : cells) {
            if (Draw.in(mx, my, originX() + c.x(), originY() + c.y(), SLOT, SLOT)) return c;
        }
        return null;
    }

    // ---- drawing ----

    /** @param highlighted the {@link Cell#key()} the tree is highlighting, or null */
    void draw(GuiGraphics g, Font font, int mx, int my, boolean canHover, String highlighted, Lookup lookup) {
        if (h <= 0) return;

        // NEI's background strip: a 2 pixel edge on top, then the flat fill
        for (int dx = 0; dx < w; dx += 46) {
            final int pw = Math.min(46, w - dx);
            Tex.blit(g, x + dx, y, 26, 84, pw, 2);
            for (int dy = 2; dy < h; dy += 14) Tex.blit(g, x + dx, y + dy, 26, 86, pw, Math.min(14, h - dy));
        }

        Tex.beginClip(g, x, y, w, h);
        final int ox = originX();
        final int oy = originY();

        for (Label l : labels) Gfx.text(g, font, Component.literal(l.text()), ox + l.x(), oy + l.y(), 0xFFFFFFFF, true);

        for (Cell c : cells) {
            final int cx = ox + c.x();
            final int cy = oy + c.y();
            final boolean lit = c.key().equals(highlighted);

            if (c.entry() != null) drawEntry(g, font, c.entry(), cx, cy, lit, lookup);
            else drawHandler(g, font, c.handler(), cx, cy, lit, lookup);

            if (canHover && Draw.in(mx, my, cx, cy, SLOT, SLOT) && Draw.in(mx, my, x, y, w, h)) {
                Gfx.fill(g, cx + 1, cy + 1, cx + SLOT - 1, cy + SLOT - 1, HOVER);
            }
        }
        Tex.endClip(g);

        if (maxScroll() > 0) { // a plain thumb along the bottom edge
            final int thumb = Math.max(8, viewWidth() * viewWidth() / contentW);
            final int tx = x + PAD_INLINE + (viewWidth() - thumb) * scroll / maxScroll();
            Gfx.fill(g, x + PAD_INLINE, y + h - 3, x + w - PAD_INLINE, y + h - 1, 0x66000000);
            Gfx.fill(g, tx, y + h - 3, tx + thumb, y + h - 1, 0xFFAAAAAA);
        }
    }

    private static void drawEntry(GuiGraphics g, Font font, TreeStats.Entry e, int cx, int cy, boolean lit,
            Lookup lookup) {
        Tex.blit(g, cx, cy, lit ? 164 : 68 + 24 * e.kind().ordinal(), 26, SLOT, SLOT);

        final ItemStack stack = lookup.stack(e.itemId());
        if (!stack.isEmpty()) Draw.stack(g, stack, cx + 1, cy + 1);
        amount(g, font, e.amount(), cx, cy);

        if (e.recipeId() != null) { // the recipe type's badge, as on the tree's boxes
            final ItemStack station = lookup.station(e.itemId(), e.recipeId());
            g.pose().pushPose();
            g.pose().translate(cx - 3.6f, cy - 3.6f, 0f);
            g.pose().scale(0.6f, 0.6f, 1f);
            if (station.isEmpty()) Tex.blit(g, 0, 0, 67, 48, 16, 16);
            else Draw.stack(g, station, 0, 0);
            g.pose().popPose();
        }
    }

    private static void drawHandler(GuiGraphics g, Font font, TreeStats.Handler hd, int cx, int cy,
            boolean lit, Lookup lookup) {
        Tex.blit(g, cx, cy, lit ? 164 : 68, 26, SLOT, SLOT);

        final ItemStack station = lookup.handlerStation(hd.name());
        if (station.isEmpty()) Tex.blit(g, cx + 1, cy + 1, 67, 48, 16, 16);
        else Draw.stack(g, station, cx + 1, cy + 1);

        Draw.smallText(g, font, "x" + ReadableNumber.wide(hd.iterations()), cx + 2, cy + 2, 0xFFFFFF);
        amount(g, font, hd.recipes(), cx, cy);
    }

    /** Bottom right of the item area, as on the tree's boxes. */
    private static void amount(GuiGraphics g, Font font, long amount, int cx, int cy) {
        final String text = amount < 10_000 ? String.valueOf(amount) : ReadableNumber.wide(amount);
        Draw.smallText(g, font, text, (float) Math.ceil(cx + 1 + 16 - font.width(text) * 0.5),
                (float) Math.ceil(cy + 1 + 16 - 4.5), 0xFFFFFF);
    }
}
