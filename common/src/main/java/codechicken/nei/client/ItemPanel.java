package codechicken.nei.client;

import codechicken.nei.item.Stacks;
import java.util.List;
import java.util.function.Supplier;

import codechicken.nei.Collapsible;
import codechicken.nei.NEIConfig;
import codechicken.nei.item.ItemEntry;
import codechicken.nei.item.ItemGroups;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * A paged grid of items with a page row, like NEI's side panels. The right-hand item list puts the page row above the
 * grid; the left-hand bookmark panel puts it below. Layout, paging and hit testing are plain arithmetic; only
 * {@link #render} touches the GUI API.
 */
public final class ItemPanel {

    /** NEI's {@code ItemsGrid.SLOT_SIZE}: 18, or what Item Panel Scale makes it (shared by every panel, the bookmarks too). */
    public static int SLOT = 18;

    /** NEI's {@code ItemsPanelGrid#updateScale}: {@code 2 + ceil(8 * scale / 100) * 2}. */
    static void updateScale() {
        SLOT = 2 + (int) Math.ceil(8f * codechicken.nei.NEIConfig.ITEM_PANEL_SCALE.getAsInt() / 100) * 2;
        BookmarkPanel.SLOT = SLOT;
    }
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
    /** Slots under the potion effect boxes (NEI's hideItemPanelSlot) are left out; the items continue in the next slot. */
    private boolean[] hidden = new boolean[0];
    private int[] rank = new int[0]; // slot -> how many shown slots come before it
    private int shownSlots;
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
        refreshMask();
        clampPage();
    }

    /** Called with every layout (every frame): the effects and other mods' widgets come and go. */
    private void refreshMask() {
        final int n = Math.max(0, columns * rows);
        if (hidden.length != n) {
            hidden = new boolean[n];
            rank = new int[n];
        }
        final List<int[]> areas = GuiAreas.current(); // other mods' widgets, JEI's exclusion areas, the potion boxes
        int count = 0;
        for (int s = 0; s < n; s++) {
            hidden[s] = EffectAreas.hits(areas, x + (s % columns) * SLOT, gridY + (s / columns) * SLOT, SLOT, SLOT);
            rank[s] = count;
            if (!hidden[s]) count++;
        }
        shownSlots = count;
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
        return Math.max(1, shownSlots);
    }

    public int pageCount() {
        return Math.max(1, (items().size() + perPage() - 1) / perPage());
    }

    private void clampPage() {
        page = Math.max(0, Math.min(page, pageCount() - 1));
    }

    /** True if the point is on the grid or the page row (and not on something another mod draws there). */
    public boolean contains(double mx, double my) {
        if (!isVisible()) return false;
        if (GuiAreas.contains(mx, my)) return false; // the click belongs to that mod's widget
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
        final int slot = row * columns + col;
        if (slot >= hidden.length || hidden[slot]) return -1;
        final int index = page * perPage() + rank[slot];
        return index < items().size() ? index : -1;
    }

    /** The entry under the mouse, or null. */
    public ItemEntry entryAt(double mx, double my) {
        if (!isVisible() || mx < x || my < gridY) return null;
        final int col = (int) ((mx - x) / SLOT);
        final int row = (int) ((my - gridY) / SLOT);
        if (col >= columns || row >= rows) return null;

        final int slot = row * columns + col;
        if (slot >= hidden.length || hidden[slot]) return null;
        final List<ItemEntry> items = items();
        final int index = page * perPage() + rank[slot];
        return index < items.size() ? items.get(index) : null;
    }

    public void render(GuiGraphics g, int mx, int my, int sw, int sh) {
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
            Gfx.text(g, font, Component.literal("<"), x + 4, navY + 3, 0xFFFFFFFF, false);
            Gfx.text(g, font, Component.literal(">"), x + gridW() - BTN + 5, navY + 3, 0xFFFFFFFF, false);

            final String label = (page + 1) + "/" + pageCount();
            Gfx.text(g, font, Component.literal(label), x + (gridW() - font.width(label)) / 2, navY + 3, 0xFFFFFFFF, true);
        }

        if (items.isEmpty()) {
            for (int i = 0; i < emptyHint.length; i++) {
                Gfx.text(g, font, Component.literal(emptyHint[i]), x + 2, gridY + 2 + i * 10, 0xFFA0A0A0, true);
            }
            return;
        }

        // NEI's order: highlight and group backgrounds, then group borders, then the items
        final int hoveredIndex = indexAt(mx, my);
        final int slots = hidden.length;
        final int[] slotGroup = new int[slots];
        java.util.Arrays.fill(slotGroup, -1);
        for (int i = 0; i < slots; i++) {
            if (!hidden[i] && start + rank[i] < items.size()) slotGroup[i] = groupAtIndex(start + rank[i]);
        }

        final int collapsedBg = NEIConfig.color(NEIConfig.COLLAPSIBLE_COLLAPSED_COLOR);
        final int expandedBg = NEIConfig.color(NEIConfig.COLLAPSIBLE_EXPANDED_COLOR);
        final boolean force = arranged != null && arranged.forceExpand;

        for (int i = 0; i < slots; i++) {
            if (hidden[i]) continue;
            final int index = start + rank[i];
            if (index >= items.size()) break;
            final int cx = x + (i % columns) * SLOT;
            final int cy = gridY + (i / columns) * SLOT;

            if (index == hoveredIndex) Gfx.fill(g, cx, cy, cx + SLOT, cy + SLOT, 0xEE555555);
            if (selected != null && selected.test(items.get(index))) Gfx.fill(g, cx, cy, cx + SLOT, cy + SLOT, 0x9955AA55);
            if (slotGroup[i] >= 0) {
                Gfx.fill(g, cx, cy, cx + SLOT, cy + SLOT, force || Collapsible.isExpanded(slotGroup[i]) ? expandedBg : collapsedBg);
            }
        }

        for (int i = 0; i < slots; i++) {
            if (slotGroup[i] < 0) continue;
            final int cx = x + (i % columns) * SLOT;
            final int cy = gridY + (i / columns) * SLOT;
            final int bg = force || Collapsible.isExpanded(slotGroup[i]) ? expandedBg : collapsedBg;
            final int border = (bg & 0xFFFFFF) | (Math.min(((bg >>> 24) + 102), 255) << 24); // NEI's darkerColor
            final int col = i % columns;

            if (col == 0 || slotGroup[i - 1] != slotGroup[i]) Gfx.fill(g, cx, cy, cx + 1, cy + SLOT, border);
            if (col == columns - 1 || i + 1 >= slotGroup.length || slotGroup[i + 1] != slotGroup[i]) {
                Gfx.fill(g, cx + SLOT - 1, cy, cx + SLOT, cy + SLOT, border);
            }
            if (i - columns < 0 || slotGroup[i - columns] != slotGroup[i]) Gfx.fill(g, cx, cy, cx + SLOT, cy + 1, border);
            if (i + columns >= slotGroup.length || slotGroup[i + columns] != slotGroup[i]) {
                Gfx.fill(g, cx, cy + SLOT - 1, cx + SLOT, cy + SLOT, border);
            }
        }

        for (int i = 0; i < slots; i++) {
            if (hidden[i]) continue;
            final int index = start + rank[i];
            if (index >= items.size()) break;

            final ItemEntry entry = items.get(index);
            final int cx = x + (i % columns) * SLOT;
            final int cy = gridY + (i / columns) * SLOT;

            Draw.beginSlotScale(g, cx, cy, SLOT);
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
                if (n > 1) Gfx.decorations(g, font, Stacks.withCount(entry.stack, n), cx + 1, cy + 1);
            }
            Draw.endSlotScale(g, SLOT);
        }

        if (groupButtonShown()) {
            final boolean over = Draw.in(mx, my, groupButtonX(), navY, BTN, BTN);
            Draw.button(g, groupButtonX(), navY, BTN, BTN, over);
            Gfx.text(g, font, Component.literal("G"), groupButtonX() + 4, navY + 3, 0xFFFFFFFF, false);
            if (over) ItemTooltip.requestPlain(List.of(Component.literal("Collapse/Expand All Collapsible Items")));
        }

        if (hovered != null) {
            final int group = groupAtIndex(hoveredIndex);
            HotkeyHelp.listGroupSize = group >= 0 && !force ? arranged.members.get(group).size() : 0;
            HotkeyHelp.listGroupExpanded = group >= 0 && Collapsible.isExpanded(group);
            final String name = group >= 0 && !force && !Collapsible.isExpanded(group) ? Collapsible.displayName(group) : "";
            // As in NEI: a collapsed group shows its own name instead of the first item's
            if (!name.isEmpty() && NEIConfig.COLLAPSIBLE_CUSTOM_NAME.getAsBoolean() && !HotkeyHelp.helpDown()) {
                ItemTooltip.requestPlain(List.of(Component.literal(name)));
            } else {
                ItemTooltip.request(hovered.stack, where);
                // NEI's HoveredRecipe ITEM_PANEL: an item with a favorite recipe shows it (Show Recipe Tooltips in Right Panel)
                if (codechicken.nei.NEIConfig.FAVORITE_TOOLTIP_PANEL.getAsBoolean()) {
                    final codechicken.nei.recipe.RecipeView favorite = RecipeBookmarks.favoriteView(hovered.stack);
                    if (favorite != null) ItemTooltip.requestRecipe(favorite);
                }
            }
        }
    }
}
