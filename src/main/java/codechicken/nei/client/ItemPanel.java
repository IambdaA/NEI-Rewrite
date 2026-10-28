package codechicken.nei.client;

import java.util.List;
import java.util.function.Supplier;

import codechicken.nei.Collapsible;
import codechicken.nei.NEIConfig;
import codechicken.nei.item.ItemEntry;
import codechicken.nei.item.ItemGroups;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * A paged grid of items with a page row, like NEI's side panels. The right-hand item list puts the page row above the
 * grid; the left-hand bookmark panel puts it below. Layout, paging and hit testing are plain arithmetic; only
 * {@link #render} touches the GUI API.
 */
public final class ItemPanel {

    public static final int SLOT = 18;
    private static final int BTN = 14;

    private final Supplier<List<ItemEntry>> source;
    private final boolean navBottom;
    private final String[] emptyHint;
    private final HotkeyHelp.Where where;
    private java.util.function.ToIntFunction<ItemEntry> amounts;
    private java.util.function.Predicate<ItemEntry> selected;
    private boolean collapsible;
    private List<ItemEntry> arrangedFrom;
    private int arrangedVersion = -1;
    private ItemGroups.Arranged<ItemEntry> arranged;

    private int x;
    private int gridY;
    private int navY;
    private int columns;
    private int rows;
    private int page;
    private boolean nav = true;

    /** The item list groups similar items into one slot (NEI's collapsible items). */
    public ItemPanel withCollapsible() {
        collapsible = true;
        return this;
    }

    /** What is shown: the source, or the source with its collapsible groups folded. */
    private List<ItemEntry> items() {
        final List<ItemEntry> raw = source.get();
        if (!collapsible || Collapsible.isEmpty() || raw.isEmpty()) {
            arranged = null;
            return raw;
        }
        if (arranged == null || arrangedFrom != raw || arrangedVersion != Collapsible.version()) {
            arrangedFrom = raw;
            arrangedVersion = Collapsible.version();
            arranged = ItemGroups.arrange(raw, Collapsible::groupIndex, Collapsible::isExpanded);
        }
        return arranged.shown;
    }

    /** Group index of the shown item at this index, or -1; a group of one item does not count (NEI draws it plain). */
    private int groupAtIndex(int index) {
        items();
        if (arranged == null) return -1;
        final Integer g = arranged.groupOf.get(index);
        return g != null && arranged.members.get(g).size() > 1 ? g : -1;
    }

    private boolean groupButtonShown() {
        return collapsible && nav && !Collapsible.isEmpty() && !items().isEmpty();
    }

    private int groupButtonX() {
        return x + gridW() - 2 * BTN - 2;
    }

    /** No page row (NEI's history panel is just a grid; extra items are cut off). */
    public ItemPanel withoutNav() {
        nav = false;
        return this;
    }

    /**
     * @param source    what to show; called often, so it must be cheap
     * @param navBottom true to put the page row under the grid instead of above it
     * @param emptyHint lines of text shown while the source is empty
     */
    public ItemPanel(Supplier<List<ItemEntry>> source, boolean navBottom, String... emptyHint) {
        this.source = source;
        this.navBottom = navBottom;
        this.emptyHint = emptyHint;
        this.where = navBottom ? HotkeyHelp.Where.BOOKMARK : HotkeyHelp.Where.LIST;
    }

    /** Draws a green background behind the items for which this is true (NEI's preset editor marks its items so). */
    public ItemPanel withSelected(java.util.function.Predicate<ItemEntry> selected) {
        this.selected = selected;
        return this;
    }

    /** Shows an amount on each item (only when above 1), e.g. bookmark quantities. */
    public ItemPanel withAmounts(java.util.function.ToIntFunction<ItemEntry> amounts) {
        this.amounts = amounts;
        return this;
    }

    /**
     * Sets the area available (screen coordinates). The page row takes {@code BTN + 4} pixels at the top (or bottom,
     * for a bottom panel) of that area, so callers leave room for it.
     */
    public void layout(int left, int right, int top, int bottom) {
        this.x = left;
        this.columns = Math.max(0, (right - left) / SLOT);
        if (navBottom) {
            this.navY = bottom - BTN;
            this.gridY = top;
            this.rows = Math.max(0, (navY - 4 - top) / SLOT);
        } else {
            this.navY = top;
            this.gridY = nav ? top + BTN + 4 : top;
            this.rows = Math.max(0, (bottom - gridY) / SLOT);
        }
        clampPage();
    }

    private int gridW() {
        return columns * SLOT;
    }

    public boolean isVisible() {
        return columns >= 3 && rows >= 1;
    }

    public void resetPage() {
        page = 0;
    }

    public void changePage(int delta) {
        page += delta;
        clampPage();
    }

    private int perPage() {
        return Math.max(1, columns * rows);
    }

    public int pageCount() {
        return Math.max(1, (items().size() + perPage() - 1) / perPage());
    }

    private void clampPage() {
        page = Math.max(0, Math.min(page, pageCount() - 1));
    }

    /** True if the point is on the grid or the page row. */
    public boolean contains(double mx, double my) {
        if (!isVisible()) return false;
        final int top = nav ? Math.min(gridY, navY) : gridY;
        final int bottom = nav ? Math.max(gridY + rows * SLOT, navY + BTN) : gridY + rows * SLOT;
        return mx >= x && mx < x + gridW() && my >= top && my < bottom;
    }

    /** Handles clicks on the page buttons. Returns true if the click was consumed. */
    public boolean mouseClicked(double mx, double my, int button) {
        if (!nav || !isVisible()) return false;
        // NEI's "G" button: left toggles every group, right closes them all
        if (groupButtonShown() && Draw.in(mx, my, groupButtonX(), navY, BTN, BTN) && (button == 0 || button == 1)) {
            Collapsible.toggleAll(button == 1 ? Boolean.FALSE : null);
            return true;
        }
        if (button != 0) return false;
        if (Draw.in(mx, my, x, navY, BTN, BTN)) {
            changePage(-1);
            return true;
        }
        if (Draw.in(mx, my, x + gridW() - BTN, navY, BTN, BTN)) {
            changePage(1);
            return true;
        }
        return false;
    }

    /** NEI's Alt + click on a grouped item: opens or closes its group. Returns true if it did. */
    public boolean altClicked(double mx, double my) {
        final int index = indexAt(mx, my);
        if (index < 0 || arranged == null || arranged.forceExpand) return false;
        final int group = groupAtIndex(index);
        if (group < 0) return false;
        Collapsible.setExpanded(group, !Collapsible.isExpanded(group));
        return true;
    }

    private int indexAt(double mx, double my) {
        if (!isVisible() || mx < x || my < gridY) return -1;
        final int col = (int) ((mx - x) / SLOT);
        final int row = (int) ((my - gridY) / SLOT);
        if (col >= columns || row >= rows) return -1;
        final int index = page * perPage() + row * columns + col;
        return index < items().size() ? index : -1;
    }

    /** The entry under the mouse, or null. */
    public ItemEntry entryAt(double mx, double my) {
        if (!isVisible() || mx < x || my < gridY) return null;
        final int col = (int) ((mx - x) / SLOT);
        final int row = (int) ((my - gridY) / SLOT);
        if (col >= columns || row >= rows) return null;

        final List<ItemEntry> items = items();
        final int index = page * perPage() + row * columns + col;
        return index < items.size() ? items.get(index) : null;
    }

    public void render(GuiGraphicsExtractor g, int mx, int my, int sw, int sh) {
        if (!isVisible()) return;
        clampPage();

        final Font font = Minecraft.getInstance().font;
        final List<ItemEntry> items = items();
        final int start = page * perPage();
        final ItemEntry hovered = entryAt(mx, my);

        if (nav) {
            // page row: [<]   page/total   [>]
            final boolean hoverPrev = Draw.in(mx, my, x, navY, BTN, BTN);
            final boolean hoverNext = Draw.in(mx, my, x + gridW() - BTN, navY, BTN, BTN);
            Draw.button(g, x, navY, BTN, BTN, hoverPrev);
            Draw.button(g, x + gridW() - BTN, navY, BTN, BTN, hoverNext);
            g.text(font, Component.literal("<"), x + 4, navY + 3, 0xFFFFFFFF, false);
            g.text(font, Component.literal(">"), x + gridW() - BTN + 5, navY + 3, 0xFFFFFFFF, false);

            final String label = (page + 1) + "/" + pageCount();
            g.text(font, Component.literal(label), x + (gridW() - font.width(label)) / 2, navY + 3, 0xFFFFFFFF, true);
        }

        if (items.isEmpty()) {
            for (int i = 0; i < emptyHint.length; i++) {
                g.text(font, Component.literal(emptyHint[i]), x + 2, gridY + 2 + i * 10, 0xFFA0A0A0, true);
            }
            return;
        }

        // NEI's order: highlight and group backgrounds, then group borders, then the items
        final int hoveredIndex = indexAt(mx, my);
        final int[] slotGroup = new int[perPage()];
        java.util.Arrays.fill(slotGroup, -1);
        for (int i = 0; i < perPage() && start + i < items.size(); i++) slotGroup[i] = groupAtIndex(start + i);

        final int collapsedBg = NEIConfig.color(NEIConfig.COLLAPSIBLE_COLLAPSED_COLOR);
        final int expandedBg = NEIConfig.color(NEIConfig.COLLAPSIBLE_EXPANDED_COLOR);
        final boolean force = arranged != null && arranged.forceExpand;

        for (int i = 0; i < perPage(); i++) {
            final int index = start + i;
            if (index >= items.size()) break;
            final int cx = x + (i % columns) * SLOT;
            final int cy = gridY + (i / columns) * SLOT;

            if (index == hoveredIndex) g.fill(cx, cy, cx + SLOT, cy + SLOT, 0xEE555555);
            if (selected != null && selected.test(items.get(index))) g.fill(cx, cy, cx + SLOT, cy + SLOT, 0x9955AA55);
            if (slotGroup[i] >= 0) {
                g.fill(cx, cy, cx + SLOT, cy + SLOT, force || Collapsible.isExpanded(slotGroup[i]) ? expandedBg : collapsedBg);
            }
        }

        for (int i = 0; i < perPage(); i++) {
            if (slotGroup[i] < 0) continue;
            final int cx = x + (i % columns) * SLOT;
            final int cy = gridY + (i / columns) * SLOT;
            final int bg = force || Collapsible.isExpanded(slotGroup[i]) ? expandedBg : collapsedBg;
            final int border = (bg & 0xFFFFFF) | (Math.min(((bg >>> 24) + 102), 255) << 24); // NEI's darkerColor
            final int col = i % columns;

            if (col == 0 || slotGroup[i - 1] != slotGroup[i]) g.fill(cx, cy, cx + 1, cy + SLOT, border);
            if (col == columns - 1 || i + 1 >= slotGroup.length || slotGroup[i + 1] != slotGroup[i]) {
                g.fill(cx + SLOT - 1, cy, cx + SLOT, cy + SLOT, border);
            }
            if (i - columns < 0 || slotGroup[i - columns] != slotGroup[i]) g.fill(cx, cy, cx + SLOT, cy + 1, border);
            if (i + columns >= slotGroup.length || slotGroup[i + columns] != slotGroup[i]) {
                g.fill(cx, cy + SLOT - 1, cx + SLOT, cy + SLOT, border);
            }
        }

        for (int i = 0; i < perPage(); i++) {
            final int index = start + i;
            if (index >= items.size()) break;

            final ItemEntry entry = items.get(index);
            final int cx = x + (i % columns) * SLOT;
            final int cy = gridY + (i / columns) * SLOT;

            if (slotGroup[i] >= 0 && !force && !Collapsible.isExpanded(slotGroup[i])) {
                // a collapsed group: its last item peeks out from behind the first
                final List<ItemEntry> all = arranged.members.get(slotGroup[i]);
                Draw.stack(g, all.get(all.size() - 1).stack, cx + 2, cy);
                Draw.stack(g, entry.stack, cx - 1, cy + 3);
            } else {
                Draw.stack(g, entry.stack, cx + 1, cy + 1);
            }

            if (amounts != null) {
                final int n = amounts.applyAsInt(entry);
                if (n > 1) g.itemDecorations(font, entry.stack.copyWithCount(n), cx + 1, cy + 1);
            }
        }

        if (groupButtonShown()) {
            final boolean over = Draw.in(mx, my, groupButtonX(), navY, BTN, BTN);
            Draw.button(g, groupButtonX(), navY, BTN, BTN, over);
            g.text(font, Component.literal("G"), groupButtonX() + 4, navY + 3, 0xFFFFFFFF, false);
            if (over) ItemTooltip.requestPlain(List.of(Component.literal("Collapse/Expand All Collapsible Items")));
        }

        if (hovered != null) {
            final int group = groupAtIndex(hoveredIndex);
            HotkeyHelp.listGroupSize = group >= 0 && !force ? arranged.members.get(group).size() : 0;
            HotkeyHelp.listGroupExpanded = group >= 0 && Collapsible.isExpanded(group);
            final String name = group >= 0 && !force && !Collapsible.isExpanded(group) ? Collapsible.displayName(group) : "";
            // As in NEI: a collapsed group shows its own name instead of the first item's
            if (!name.isEmpty() && NEIConfig.COLLAPSIBLE_CUSTOM_NAME.getAsBoolean() && !HotkeyHelp.isAltDown()) {
                ItemTooltip.requestPlain(List.of(Component.literal(name)));
            } else {
                ItemTooltip.request(hovered.stack, where);
            }
        }
    }
}
