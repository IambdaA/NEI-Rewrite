package codechicken.nei.client;

import java.util.List;
import java.util.Map;

import codechicken.nei.Bookmarks;
import codechicken.nei.ItemList;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.ChainMath;
import codechicken.nei.bookmark.BookmarkLayout;
import codechicken.nei.item.ItemEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * The bookmark panel on the left, laid out like GTNH NEI's {@code BookmarkPanel}:
 *
 * <pre>
 * [<]  page 1/2  [>]       page row (top)
 * |  grid of bookmarks     the 7 px strip on the left of the grid shows the groups
 * |
 * [<]  1/2  [>]            "namespace" row (bottom): which page of bookmarks, hidden while there are none
 * </pre>
 *
 * Group strip, as in NEI: drag with the left button across rows to put them in a group (starting on a group's row
 * changes that group's extent), drag with the right button to take rows out of groups, click a group's strip to switch
 * it to a todo list (one item per row), Alt + click to collapse / expand it.
 *
 * Only drawing and input live here; the arithmetic is in {@link BookmarkLayout} and {@link BookmarkGrid}.
 * Shift + drag moves a group (by its strip) or an item / recipe (by its slot). Not ported: dropping an item into another
 * group of a todo list while sorting, the pull button.
 */
public final class BookmarkPanel {

    public static final int SLOT = 18;
    public static final int STRIP = 7; // NEI: BookmarkGrid.GROUP_PANEL_WIDTH
    private static final int BTN = 14;
    private static final long DRAG_MS = 250;

    // NEI colours (BookmarkGrid / ItemsGrid)
    private static final int GROUP_COLOR = 0xFF666666;
    private static final int SHADOW_COLOR = 0x66222222;
    private static final int HIGHLIGHT = 0xEE555555;
    // NEI's BookmarkGrid.GROUP_NONE_COLOR, GROUP_CHAIN_COLOR and RECIPE_COLOR: borders of collapsed groups / recipes
    private static final int BORDER_NONE = 0xFF666666;
    private static final int BORDER_CHAIN = 0x6645DA75;
    private static final int BORDER_RECIPE = 0x99A033A0;

    private final String[] emptyHint;

    private int x;
    private int top;
    private int bottom;
    private int right;

    // derived by prepare()
    private int columns;
    private int rows;
    private int gridY;
    private int nsY;
    private boolean nsVisible;

    // Shift + drag sorting (NEI's SortableGroup / SortableItem)
    private int sortGroup = -1;
    private BookmarkGrid.Item sortItem;
    /** Shift + press on a slot: becomes a sort once the mouse leaves the slot or waits; a plain release is a click. */
    private BookmarkGrid.Item downItem;
    private long downMs;
    private boolean sortMoved;
    private ItemEntry deferredClick;

    // group strip drag
    private boolean dragging;
    private int dragGroup;
    private int dragStartRow = -1;
    private int dragEndRow = -1;
    private long dragStartMs;

    public BookmarkPanel(String... emptyHint) {
        this.emptyHint = emptyHint;
    }

    /** Sets the area available (screen coordinates). */
    public void layout(int left, int right, int top, int bottom) {
        this.x = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;
        cancelDrag();
    }

    private int gridX() {
        return x + STRIP;
    }

    private void prepare() {
        columns = Math.max(0, (right - x - STRIP) / SLOT);
        gridY = top + BTN + 4;
        nsVisible = !Bookmarks.isEmpty();
        nsY = bottom - BTN;
        final int limit = nsVisible ? nsY - 4 : bottom;
        rows = Math.max(0, (limit - gridY) / SLOT);
    }

    public boolean isVisible() {
        prepare();
        return columns >= 2 && rows >= 1;
    }

    private int panelW() {
        return STRIP + columns * SLOT;
    }

    private static boolean installed(BookmarkGrid.Item item) {
        return ItemList.find(item.id) != null;
    }

    private boolean builtWithList;

    private BookmarkLayout lay() {
        // a layout made before the item list finished loading would hide every bookmark, so redo it once loaded
        final boolean loaded = ItemList.isLoaded();
        if (!loaded || !builtWithList) Bookmarks.grid().invalidateLayout();
        builtWithList = loaded;
        return Bookmarks.grid().layout(columns, BookmarkPanel::installed);
    }

    private int perPage() {
        return Math.max(1, columns * rows);
    }

    private int pageCount() {
        return Math.max(1, (lay().maxSlot + perPage() - 1) / perPage());
    }

    private int page() {
        final BookmarkGrid grid = Bookmarks.grid();
        grid.page = Math.max(0, Math.min(grid.page, pageCount() - 1));
        return grid.page;
    }

    public void changePage(int delta) {
        prepare();
        final BookmarkGrid grid = Bookmarks.grid();
        grid.page = Math.max(0, Math.min(page() + delta, pageCount() - 1));
    }

    public void resetPage() {
        Bookmarks.grid().page = 0;
    }

    // ---- geometry of the two button rows ----

    private int nsCenter() {
        return gridX() + columns * SLOT / 2;
    }

    private int nsLabelWidth() {
        return panelW() >= 70 ? 36 : 18; // NEI switches to a short label on narrow panels
    }

    private int nsPrevX() {
        return nsCenter() - nsLabelWidth() / 2 - 2 - BTN;
    }

    private int nsNextX() {
        return nsCenter() + nsLabelWidth() / 2 + 2;
    }

    private String nsLabel() {
        final String active = String.valueOf(Bookmarks.activeIndex() + 1);
        return nsLabelWidth() == 36 ? active + "/" + Bookmarks.namespaceCount() : active;
    }

    // ---- hit testing ----

    /** True if the point is anywhere on the panel (grid, strip or either button row). */
    public boolean contains(double mx, double my) {
        if (!isVisible()) return false;
        return mx >= x && mx < x + panelW() && my >= top && my < bottom;
    }

    // NEI's "P" button right of the namespace arrows: pulls all bookmarked items out of the open container (Shift: only
    // what is missing). Shown when the screen is a container with a handler and everything fits in the panel.
    private int pullX() {
        return nsNextX() + BTN + 2;
    }

    private boolean pullVisible() {
        return nsVisible && !Bookmarks.grid().isEmpty() && pullX() + BTN <= x + panelW()
                && ContainerPull.available(Minecraft.getInstance().screen);
    }

    private boolean overNamespaceNav(double mx, double my) {
        return nsVisible && Draw.in(mx, my, nsPrevX(), nsY, nsNextX() + BTN - nsPrevX(), BTN);
    }

    /** The bookmark under the mouse, or null. */
    public BookmarkGrid.Item itemAt(double mx, double my) {
        if (!isVisible() || mx < gridX() || my < gridY) return null;
        final int col = (int) ((mx - gridX()) / SLOT);
        final int row = (int) ((my - gridY) / SLOT);
        if (col >= columns || row >= rows) return null;

        final Integer index = lay().slotToItem.get(page() * perPage() + row * columns + col);
        return index == null ? null : Bookmarks.grid().layoutItems().get(index);
    }

    public ItemEntry entryAt(double mx, double my) {
        final BookmarkGrid.Item item = itemAt(mx, my);
        return item == null ? null : ItemList.find(item.id);
    }

    /** Row (on the current page) of the group strip under the mouse, or -1. */
    private int stripRow(double mx, double my) {
        if (mx < x || mx >= gridX() || my < gridY) return -1;
        final int row = (int) ((my - gridY) / SLOT);
        final int abs = page() * rows + row;
        return row < rows && abs < lay().rowCount() ? row : -1;
    }

    /** The group whose strip is under the mouse, or -1 (NEI's {@code getHoveredGroupId(true)}). */
    public int hoveredGroup(double mx, double my) {
        if (!isVisible()) return -1;
        final int row = stripRow(mx, my);
        if (row < 0) return -1;
        final int group = lay().groupOfRow(page() * rows + row);
        return group == BookmarkLayout.DEFAULT_GROUP ? -1 : group;
    }

    // ---- input ----

    /** Handles clicks on the page buttons. Returns true if the click was consumed. */
    public boolean mouseClicked(double mx, double my, int button) {
        if (!isVisible() || button != 0 || dragging) return false;

        if (Draw.in(mx, my, x, top, BTN, BTN)) {
            changePage(-1);
            return true;
        }
        if (Draw.in(mx, my, x + panelW() - BTN, top, BTN, BTN)) {
            changePage(1);
            return true;
        }
        if (nsVisible && Draw.in(mx, my, nsPrevX(), nsY, BTN, BTN)) {
            Bookmarks.prevNamespace();
            return true;
        }
        if (nsVisible && Draw.in(mx, my, nsNextX(), nsY, BTN, BTN)) {
            Bookmarks.nextNamespace();
            return true;
        }
        if (pullVisible() && Draw.in(mx, my, pullX(), nsY, BTN, BTN)) {
            ContainerPull.pullGroup(BookmarkLayout.DEFAULT_GROUP, HotkeyHelp.isShiftDown());
            return true;
        }
        return false;
    }

    /**
     * Mouse wheel. Over the page buttons it switches page; Ctrl + wheel over a bookmark changes its amount (NEI);
     * elsewhere on the panel it turns the grid page. Returns true if consumed.
     */
    public boolean scrolled(double mx, double my, double dy, boolean ctrl) {
        if (!isVisible()) return false;

        if (!dragging && overNamespaceNav(mx, my)) {
            if (dy > 0) Bookmarks.prevNamespace();
            else Bookmarks.nextNamespace();
            return true;
        }
        if (!contains(mx, my)) return false;

        if (!dragging && ctrl) {
            final int steps = dy > 0 ? 1 : -1;
            final BookmarkGrid.Item item = itemAt(mx, my);
            if (item != null) {
                // As in NEI: Alt makes one step the item's stack size
                final ItemEntry entry = ItemList.find(item.id);
                final long stack = HotkeyHelp.isAltDown() && entry != null ? QuantityBar.multiplier(entry.stack.getMaxStackSize()) : 1;
                Bookmarks.shiftItem(item, steps, stack);
                return true;
            }
            final int row = stripRow(mx, my);
            if (row >= 0) {
                final int group = lay().groupOfRow(page() * rows + row);
                if (group != BookmarkLayout.DEFAULT_GROUP) {
                    Bookmarks.shiftGroup(group, steps, HotkeyHelp.isAltDown() ? QuantityBar.multiplier(64) : 1);
                    return true;
                }
            }
        }
        changePage(dy > 0 ? -1 : 1);
        return true;
    }

    /**
     * Mouse button down. Starts a strip drag if the press is on a group strip (Shift is for other things in NEI, so it
     * is ignored here). Returns true if the press was on the strip.
     */
    public boolean pressed(double mx, double my, int button, boolean shift) {
        if (isVisible() && shift && button == 0) {
            // As in NEI, Shift + drag over a group's strip moves the group, over a slot moves the item (or recipe)
            final BookmarkGrid.Item over = itemAt(mx, my);
            if (over != null) {
                downItem = over;
                downMs = System.currentTimeMillis();
                return true;
            }
            final int stripRow = stripRow(mx, my);
            if (stripRow >= 0) {
                final int group = lay().groupOfRow(page() * rows + stripRow);
                if (group != BookmarkLayout.DEFAULT_GROUP) {
                    sortGroup = group;
                    return true;
                }
            }
        }
        if (!isVisible() || shift || (button != 0 && button != 1)) return false;
        final int row = stripRow(mx, my);
        if (row < 0) return false;

        final int abs = page() * rows + row;
        final BookmarkLayout lay = lay();
        if (lay.firstItemInRow(abs) < 0) return true;

        final int group = lay.groupOfRow(abs);
        dragGroup = button == 1 ? BookmarkLayout.DEFAULT_GROUP : (group == BookmarkLayout.DEFAULT_GROUP ? Integer.MIN_VALUE : group);
        dragStartRow = abs;
        dragEndRow = -1;
        dragStartMs = System.currentTimeMillis();
        dragging = true;
        return true;
    }

    /** Mouse button up. Finishes a strip drag. Returns true if a drag was in progress. */
    public boolean released(double mx, double my, int button, boolean alt) {
        if (sortGroup >= 0 || downItem != null) {
            // a Shift + click that never became a drag still does what a click does
            if (sortItem == null && sortGroup < 0 && downItem != null) deferredClick = ItemList.find(downItem.id);
            if (sortMoved) Bookmarks.save();
            cancelSort();
            return true;
        }
        if (!dragging) return false;
        dragging = false;

        if (dragEndRow != -1) {
            if (Bookmarks.grid().createGroup(columns, BookmarkPanel::installed, dragGroup, dragStartRow, dragEndRow)) {
                Bookmarks.save();
            }
        } else {
            final int group = lay().groupOfRow(dragStartRow);
            if (group != BookmarkLayout.DEFAULT_GROUP) {
                if (button == 0 && alt) Bookmarks.grid().toggleCollapsed(group);
                else if (button == 0) Bookmarks.grid().toggleTodo(group);
                else if (button == 1) Bookmarks.grid().toggleCrafting(group); // NEI: right click = crafting chain
                Bookmarks.save();
            }
        }
        dragStartRow = -1;
        dragEndRow = -1;
        return true;
    }

    /**
     * NEI's Alt + click on a bookmark: expands its group when collapsed, else folds / unfolds the recipe of the slot in a
     * crafting chain group. The click is always taken.
     */
    public boolean altClicked(double mx, double my) {
        final BookmarkGrid.Item slot = itemAt(mx, my);
        if (slot == null) return false;
        final BookmarkGrid grid = Bookmarks.grid();
        final BookmarkGrid.Group group = grid.group(slot.groupId);
        if (group.collapsed) {
            grid.toggleCollapsed(slot.groupId);
            Bookmarks.save();
        } else if (group.crafting && slot.recipeId != null) {
            grid.toggleCollapsedRecipe(slot.groupId, slot.recipeId);
            Bookmarks.save();
        }
        return true;
    }

    /** The click a Shift + press on a slot turned out to be (no drag happened), once. */
    public ItemEntry takeDeferredClick() {
        final ItemEntry entry = deferredClick;
        deferredClick = null;
        return entry;
    }

    private void cancelSort() {
        sortGroup = -1;
        sortItem = null;
        downItem = null;
        sortMoved = false;
    }

    /** Moves what is being dragged to where the mouse is (NEI does the same on every drag event). */
    private void updateSort(int mx, int my) {
        if (sortGroup >= 0) {
            final BookmarkLayout lay = lay();
            final int pageStart = page() * rows;
            if (my >= gridY && lay.rowCount() > 0) {
                final int over = Math.min(pageStart + (my - gridY) / SLOT, Math.min(pageStart + rows - 1, lay.rowCount() - 1));
                sortMoved |= Bookmarks.grid().moveGroup(columns, BookmarkPanel::installed, sortGroup, over);
            }
        } else if (downItem != null) {
            final BookmarkGrid.Item over = itemAt(mx, my);
            if (sortItem == null && (over != downItem || System.currentTimeMillis() - downMs > DRAG_MS)) {
                sortItem = Bookmarks.grid().real(downItem); // a collapsed recipe's stand-in slot drags the recipe
            }
            if (sortItem != null && Bookmarks.grid().crossGroup(sortItem)) {
                // in a todo list the row under the mouse decides, and the item may change group
                if (my >= gridY && (my - gridY) / SLOT < rows) {
                    final int row = page() * rows + (my - gridY) / SLOT;
                    sortMoved |= Bookmarks.grid().dragAcrossGroups(columns, BookmarkPanel::installed, sortItem, row,
                            ((my - gridY) % SLOT) / (double) SLOT);
                }
            } else if (sortItem != null && over != null) {
                sortMoved |= Bookmarks.grid().moveItem(sortItem, over);
            }
        }
    }

    private boolean sorting() {
        return sortGroup >= 0 || sortItem != null;
    }

    /** The dragged slots follow the mouse, the first one centred on it. */
    private void drawSorting(GuiGraphics g, int mx, int my, BookmarkLayout lay, int page) {
        final BookmarkGrid grid = Bookmarks.grid();
        final List<BookmarkGrid.Item> unit = sortGroup >= 0 ? grid.groupItems(sortGroup) : grid.sortUnit(sortItem);
        final List<BookmarkGrid.Item> laid = grid.layoutItems();
        int baseX = Integer.MIN_VALUE;
        int baseY = 0;

        for (Map.Entry<Integer, Integer> slot : new java.util.TreeMap<>(lay.slotToItem).entrySet()) {
            final int rel = slot.getKey() - page * perPage();
            final BookmarkGrid.Item item = laid.get(slot.getValue());
            if (rel < 0 || rel >= perPage() || !grid.inUnit(unit, item)) continue;
            final ItemEntry entry = ItemList.find(item.id);
            if (entry == null) continue;

            final int cx = gridX() + (rel % columns) * SLOT;
            final int cy = gridY + (rel / columns) * SLOT;
            if (baseX == Integer.MIN_VALUE) {
                baseX = cx;
                baseY = cy;
            }
            Draw.stack(g, entry.stack, mx - 8 + cx - baseX, my - 8 + cy - baseY);
        }
    }

    public void cancelDrag() {
        cancelSort();
        dragging = false;
        dragStartRow = -1;
        dragEndRow = -1;
    }

    private void updateDrag(int my) {
        if (!dragging) return;
        final BookmarkLayout lay = lay();
        final int pageStart = page() * rows;
        final int lastOnPage = Math.min(pageStart + rows - 1, lay.rowCount() - 1);
        final int over = Math.max(pageStart, Math.min(pageStart + (my - gridY) / SLOT, lastOnPage));

        if (dragEndRow != -1 || over != dragStartRow || System.currentTimeMillis() - dragStartMs > DRAG_MS) {
            dragEndRow = over;
        }
    }

    // ---- drawing ----

    private static void rect(GuiGraphics g, int rx, int ry, int w, int h, int color) {
        Gfx.fill(g, rx, ry, rx + w, ry + h, color);
    }

    public void render(GuiGraphics g, int mx, int my) {
        if (!isVisible()) return;
        final Font font = Minecraft.getInstance().font;
        final BookmarkGrid grid = Bookmarks.grid();
        updateSort(mx, my); // before the layout is read: a move makes a new one
        final BookmarkLayout lay = lay();
        final int page = page();
        updateDrag(my);

        // page row
        Draw.button(g, x, top, BTN, BTN, Draw.in(mx, my, x, top, BTN, BTN));
        Draw.button(g, x + panelW() - BTN, top, BTN, BTN, Draw.in(mx, my, x + panelW() - BTN, top, BTN, BTN));
        Gfx.text(g, font, Component.literal("<"), x + 4, top + 3, 0xFFFFFFFF, false);
        Gfx.text(g, font, Component.literal(">"), x + panelW() - BTN + 5, top + 3, 0xFFFFFFFF, false);
        final String label = (page + 1) + "/" + pageCount();
        Gfx.text(g, font, Component.literal(label), x + (panelW() - font.width(label)) / 2, top + 3, 0xFFFFFFFF, true);

        // namespace row
        if (nsVisible) {
            Draw.button(g, nsPrevX(), nsY, BTN, BTN, Draw.in(mx, my, nsPrevX(), nsY, BTN, BTN));
            Draw.button(g, nsNextX(), nsY, BTN, BTN, Draw.in(mx, my, nsNextX(), nsY, BTN, BTN));
            Gfx.text(g, font, Component.literal("<"), nsPrevX() + 4, nsY + 3, 0xFFFFFFFF, false);
            Gfx.text(g, font, Component.literal(">"), nsNextX() + 5, nsY + 3, 0xFFFFFFFF, false);
            final String ns = nsLabel();
            Gfx.text(g, font, Component.literal(ns), nsCenter() - font.width(ns) / 2, nsY + 3, 0xFFFFFFFF, true);
            if (pullVisible()) {
                final boolean over = Draw.in(mx, my, pullX(), nsY, BTN, BTN);
                Draw.button(g, pullX(), nsY, BTN, BTN, over);
                Gfx.text(g, font, Component.literal("P"), pullX() + 4, nsY + 3, 0xFFFFFFFF, false);
                if (over) ItemTooltip.requestPlain(List.of(Component.literal("Pull all bookmarked items from storage to inventory")));
            }
        }

        if (grid.isEmpty()) {
            for (int i = 0; i < emptyHint.length; i++) {
                Gfx.text(g, font, Component.literal(emptyHint[i]), gridX() + 2, gridY + 2 + i * 10, 0xFFA0A0A0, true);
            }
            return;
        }

        drawStrip(g, lay, page, mx, my);

        // items
        final BookmarkGrid.Item hoveredSlot = dragging || sorting() ? null : itemAt(mx, my);
        final ItemEntry hovered = hoveredSlot == null ? null : ItemList.find(hoveredSlot.id);
        final List<BookmarkGrid.Item> laid = grid.layoutItems();
        final int start = page * perPage();
        // highlight, then borders, then the items, as NEI's beforeDrawItems / drawItems
        final Ctx ctx = contextAt(grid, lay, laid, mx, my);
        drawBorders(g, grid, lay, laid, start, hoveredSlot, ctx);
        for (Map.Entry<Integer, Integer> slot : lay.slotToItem.entrySet()) {
            final int rel = slot.getKey() - start;
            if (rel < 0 || rel >= perPage()) continue;

            final BookmarkGrid.Item item = laid.get(slot.getValue());
            final ItemEntry entry = ItemList.find(item.id);
            if (entry == null) continue;

            final int cx = gridX() + (rel % columns) * SLOT;
            final int cy = gridY + (rel / columns) * SLOT;
            Draw.stack(g, entry.stack, cx + 1, cy + 1);
            drawAmounts(g, font, grid, lay, laid, slot.getKey(), item, cx, cy, ctx);
        }

        if (sorting()) drawSorting(g, mx, my, lay, page);

        if (hovered != null) {
            HotkeyHelp.slotType = hoveredSlot.type;
            HotkeyHelp.slotAltToggle = grid.group(hoveredSlot.groupId).collapsed || grid.group(hoveredSlot.groupId).crafting;
            HotkeyHelp.slotOnlyResult = true;
            for (BookmarkGrid.Item o : grid.items) {
                if (o != hoveredSlot && o.type == 1 && hoveredSlot.type == 1 && o.inRecipe(hoveredSlot.recipeId, hoveredSlot.groupId)) {
                    HotkeyHelp.slotOnlyResult = false;
                }
            }
            ItemTooltip.request(hovered.stack, HotkeyHelp.Where.BOOKMARK);
        } else if (!dragging && !sorting()) {
            // NEI: hovering the strip shows "Bookmarks Group" and, with Alt held, its hotkeys
            final int row = stripRow(mx, my);
            if (row >= 0) {
                final int stripGroup = lay.groupOfRow(page * rows + row);
                final boolean inGroup = stripGroup != BookmarkLayout.DEFAULT_GROUP;
                ItemTooltip.requestStrip(inGroup, inGroup && grid.group(stripGroup).crafting);
            }
        }
    }

    /** NEI's {@code calculateGroupBorders} + {@code drawBorder}; shortcut: 1px lines, NEI's are 0.75px wide. */
    private void drawBorders(GuiGraphics g, BookmarkGrid grid, BookmarkLayout lay, List<BookmarkGrid.Item> laid,
            int start, BookmarkGrid.Item hoveredSlot, Ctx ctx) {
        final Map<Integer, String> keys = grid.borderKeys(lay);

        for (Map.Entry<Integer, Integer> slot : lay.slotToItem.entrySet()) {
            final int abs = slot.getKey();
            final int rel = abs - start;
            if (rel < 0 || rel >= perPage()) continue;

            final BookmarkGrid.Item item = laid.get(slot.getValue());
            final int cx = gridX() + (rel % columns) * SLOT;
            final int cy = gridY + (rel / columns) * SLOT;
            // NEI's BookmarksGridSlot#beforeDraw: with Ctrl or Shift held the slots of the hovered recipe / row / group are
            // coloured by what they are (ingredient, result, remainder); otherwise only the hovered slot is lit
            final int shownColor = shownColor(grid, item, abs, ctx);
            if (shownColor != 0) Gfx.fill(g, cx, cy, cx + SLOT, cy + SLOT, shownColor);
            else if (item == hoveredSlot) Gfx.fill(g, cx, cy, cx + SLOT, cy + SLOT, HIGHLIGHT);

            final String key = keys.get(abs);
            if (key == null) continue;
            final BookmarkGrid.Group group = grid.group(item.groupId);
            final int color = !group.crafting ? BORDER_NONE : group.collapsed ? BORDER_CHAIN : BORDER_RECIPE;

            if (rel % columns == 0 || !key.equals(keys.get(abs - 1))) rect(g, cx, cy, 1, SLOT, color);
            if (rel % columns == columns - 1 || !key.equals(keys.get(abs + 1))) rect(g, cx + SLOT - 1, cy, 1, SLOT, color);
            if (!key.equals(keys.get(abs - columns))) rect(g, cx, cy, SLOT, 1, color);
            if (!key.equals(keys.get(abs + columns))) rect(g, cx, cy + SLOT - 1, SLOT, 1, color);
        }
    }

    /** Text at half size, as NEI draws amounts and markers over a slot ({@code NEIClientUtils#drawNEIOverlayText}). */
    private static void smallText(GuiGraphics g, Font font, String text, float x, float y, int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0f);
        g.pose().scale(0.5f, 0.5f, 1f);
        Gfx.text(g, font, Component.literal(text), 0, 0, 0xFF000000 | color, true);
        g.pose().popPose();
    }

    private static String readable(long n) {
        return n < 10_000 ? String.valueOf(n) : n < 1_000_000 ? n / 1000 + "K" : n / 1_000_000 + "M";
    }

    /**
     * The amount (bottom right) and, on the first result of a recipe, the "xN" recipe marker (top left), as in NEI's
     * {@code BookmarksGridSlot#drawStackSize} / {@code afterDraw}. In a crafting chain group the amount is the
     * calculated one.
     */
    private void drawAmounts(GuiGraphics g, Font font, BookmarkGrid grid, BookmarkLayout lay,
            List<BookmarkGrid.Item> laid, int absSlot, BookmarkGrid.Item item, int cx, int cy, Ctx ctx) {
        final ChainMath.Calc calc = grid.calc.get(item);
        final Shown shown = shownOf(grid, item, calc, absSlot, ctx);
        // NEI's drawStackSize: the amount the player set (Ctrl, or no chain), the amount still to gather (Shift) or the calculated one
        final long amount = shown == null || shown == Shown.REAL || calc == null ? item.amount
                : shown == Shown.SHIFT ? calc.shiftAmount : calc.amount();

        if (amount > 0 || shown == Shown.REAL && item.factor > 0) {
            final String text = readable(amount);
            smallText(g, font, text, cx + 17 - font.width(text) * 0.5f, cy + 17 - 4.5f, 0xFFFFFF);
        }

        if (item.type != 1 || item.recipeId == null) return;

        // first result of its recipe on a row
        final Integer prevIndex = lay.slotToItem.get(absSlot - 1);
        final BookmarkGrid.Item prev = prevIndex == null || absSlot % columns == 0 ? null : laid.get(prevIndex);
        final boolean todo = grid.group(item.groupId).todo;
        final boolean first = (absSlot % columns == 0 || !todo)
                && (prev == null || prev.type == 2 || !item.recipeId.equals(prev.recipeId));
        if (!first) return;

        final long real = calc != null ? calc.realMultiplier : item.factor <= 0 ? 0 : item.amount / item.factor;
        if (shown == Shown.REAL) {
            smallText(g, font, "x" + readable(real), cx + 1, cy + 1, 0xFFFFFF);
        } else if (shown == Shown.SHIFT) {
            smallText(g, font, "x" + readable(calc != null ? calc.multiplier : real), cx + 1, cy + 1, 0xFFFFFF);
        } else if (grid.isOutputRecipe(item.groupId, item.recipeId) || real > 1) {
            smallText(g, font, "x" + readable(real), cx + 1, cy + 1, 0xADADAD);
        }
    }

    // ---- NEI's ShownItemType: what Ctrl and Shift show while hovering ----

    private enum Shown { REAL, SHIFT, CALCULATED }

    /** NEI's {@code BookmarkMouseContext}: what is under the mouse. {@code slot} is -1 over an empty cell of a row. */
    private record Ctx(int groupId, int row, int slot, String recipeId, boolean ctrl, boolean shift) {}

    private Ctx contextAt(BookmarkGrid grid, BookmarkLayout lay, List<BookmarkGrid.Item> laid, double mx, double my) {
        if (dragging || sorting() || !isVisible() || mx < gridX() || my < gridY) return null;
        final int col = (int) ((mx - gridX()) / SLOT);
        final int row = (int) ((my - gridY) / SLOT);
        if (col >= columns || row >= rows) return null;
        final boolean ctrl = HotkeyHelp.isCtrlDown();
        final boolean shift = HotkeyHelp.isShiftDown();
        if (!ctrl && !shift) return null; // only these two change what is shown

        final int abs = page() * perPage() + row * columns + col;
        final Integer index = lay.slotToItem.get(abs);
        if (index != null) {
            final BookmarkGrid.Item item = laid.get(index);
            return new Ctx(item.groupId, abs / columns, abs, item.recipeId, ctrl, shift);
        }
        // an empty cell: the row speaks for its first item
        final int rowAbs = abs / columns;
        for (int c = 0; c < columns; c++) {
            final Integer first = lay.slotToItem.get(rowAbs * columns + c);
            if (first == null) continue;
            final BookmarkGrid.Item item = laid.get(first);
            final int groupId = lay.groupOfRow(rowAbs);
            return new Ctx(groupId, rowAbs, -1, grid.group(groupId).collapsed ? null : item.recipeId, ctrl, shift);
        }
        return null;
    }

    /** NEI's {@code getShownItemType} with {@code showShiftItem} / {@code showRealItem}. */
    private Shown shownOf(BookmarkGrid grid, BookmarkGrid.Item item, ChainMath.Calc calc, int abs, Ctx ctx) {
        if (ctx != null && item.groupId == ctx.groupId()) {
            final BookmarkGrid.Group group = grid.group(item.groupId);
            final int row = abs / columns;
            if (ctx.shift() && (abs == ctx.slot() || group.crafting
                    || item.type == 0 && group.todo && row == ctx.row()
                    || item.inRecipe(ctx.recipeId(), ctx.groupId()))) {
                return Shown.SHIFT;
            }
            if (ctx.ctrl() && (abs == ctx.slot() || group.collapsed
                    || ctx.recipeId() == null && group.todo && row == ctx.row()
                    || item.inRecipe(ctx.recipeId(), ctx.groupId())
                    || group.crafting && ctx.recipeId() != null && ctx.recipeId().equals(item.recipeId))) {
                return Shown.REAL;
            }
        }
        return calc != null ? Shown.CALCULATED : null;
    }

    /** The background of a slot while Ctrl / Shift show its amount, or 0. NEI's {@code beforeDraw} and {@code drawBackground}. */
    private int shownColor(BookmarkGrid grid, BookmarkGrid.Item item, int abs, Ctx ctx) {
        final ChainMath.Calc calc = grid.calc.get(item);
        final Shown shown = shownOf(grid, item, calc, abs, ctx);
        final BookmarkGrid.Group group = grid.group(item.groupId);
        int type = -1;
        if (shown == Shown.REAL) {
            type = realType(item, calc, group);
        } else if (shown == Shown.SHIFT) {
            if (calc != null) type = calc.shiftAmount > 0 ? calc.type : -1;
            else if (!group.crafting) type = realType(item, calc, group);
        }
        return switch (type) {
            case ChainMath.Calc.INGREDIENT -> 0x6645DA75;
            case ChainMath.Calc.RESULT -> 0x9966CCFF;
            case ChainMath.Calc.REMAINDER -> 0x55A033A0;
            default -> 0;
        };
    }

    /** NEI's {@code realType}: what the slot is when the amounts are the ones the player set. */
    private static int realType(BookmarkGrid.Item item, ChainMath.Calc calc, BookmarkGrid.Group group) {
        if (group.crafting && calc != null) return calc.type == ChainMath.Calc.INGREDIENT ? ChainMath.Calc.INGREDIENT : ChainMath.Calc.RESULT;
        return item.type == 2 || group.crafting && item.recipeId == null ? ChainMath.Calc.INGREDIENT : ChainMath.Calc.RESULT;
    }

    /** NEI's {@code BookmarkGrid#draw} strip part, ported line by line. */
    private void drawStrip(GuiGraphics g, BookmarkLayout lay, int page, int mx, int my) {
        final int rowShift = page * rows;
        final int totalRows = lay.rowCount();
        if (totalRows == 0 || !lay.rowToGroup.containsKey(rowShift)) return;

        Map<Integer, Integer> groups = lay.rowToGroup;
        if (dragging && dragEndRow != -1) {
            groups = BookmarkLayout.prepareGroups(groups, dragGroup, dragStartRow, dragEndRow);
        } else if (!dragging) {
            final int focused = stripRow(mx, my);
            if (focused != -1) rect(g, gridX() - STRIP, gridY + focused * SLOT, STRIP, SLOT, HIGHLIGHT);
        }

        final int lastRow = Math.min(rows - 1, totalRows - 1 - rowShift);
        final int previousPageGroup = groups.getOrDefault(rowShift - 1, BookmarkLayout.DEFAULT_GROUP);
        final int nextPageGroup = groups.getOrDefault(rowShift + rows, BookmarkLayout.DEFAULT_GROUP);
        int previous = BookmarkLayout.DEFAULT_GROUP;
        int groupStart = -2;

        if (previousPageGroup == groups.get(rowShift)) {
            previous = previousPageGroup;
            groupStart = -1;
        }

        for (int row = 0; row < rows && groups.containsKey(rowShift + row); row++) {
            final int group = groups.get(rowShift + row);

            if (groupStart != -2 && previous != group) {
                if (previous != BookmarkLayout.DEFAULT_GROUP) drawGroup(g, groupStart, row - 1, previous);
                else drawShadow(g, groupStart, row - 1);
                groupStart = -2;
            }
            if (groupStart == -2) groupStart = row;
            previous = group;
        }

        if (groupStart != -2) {
            final int end = nextPageGroup != BookmarkLayout.DEFAULT_GROUP
                    && groups.containsKey(rowShift + rows - 1) && nextPageGroup == groups.get(rowShift + rows - 1)
                            ? rows
                            : lastRow;
            if (previous != BookmarkLayout.DEFAULT_GROUP) drawGroup(g, groupStart, end, previous);
            else drawShadow(g, groupStart, end);
        }
    }

    /** NEI's {@code BookmarkGrid#drawGroup}: the strip is green for a crafting chain group (GROUP_CHAIN_COLOR), grey otherwise. */
    private void drawGroup(GuiGraphics g, int rowStart, int rowEnd, int groupId) {
        final int color = Bookmarks.grid().group(groupId).crafting ? BORDER_CHAIN : GROUP_COLOR;
        final int half = STRIP / 2;
        final int pad = SLOT / 4;
        final int left = gridX() - half - 1;
        int height = (Math.min(rowEnd, rows - 1) - Math.max(0, rowStart) + 1) * SLOT;
        int y = gridY + Math.max(0, rowStart) * SLOT;

        if (rowStart >= 0) {
            rect(g, left, gridY + rowStart * SLOT + pad, half, 1, color);
            y += pad + 1;
            height -= pad + 1;
        }
        if (rowEnd < rows) {
            rect(g, left, gridY + (rowEnd + 1) * SLOT - pad, half, 1, color);
            height -= pad;
        }
        rect(g, left, y, 1, height, color);
    }

    private void drawShadow(GuiGraphics g, int rowStart, int rowEnd) {
        final int y = gridY + Math.max(0, rowStart) * SLOT;
        final int height = (Math.min(rowEnd, rows - 1) - Math.max(0, rowStart) + 1) * SLOT;
        rect(g, gridX() - STRIP / 2 - 1, y + 2, 1, height - 4, SHADOW_COLOR);
    }
}
