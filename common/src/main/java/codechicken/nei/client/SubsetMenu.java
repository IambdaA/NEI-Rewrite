package codechicken.nei.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import codechicken.nei.NEIActions;
import codechicken.nei.NEIConfig;
import codechicken.nei.Subsets;
import codechicken.nei.item.ItemEntry;
import codechicken.nei.search.SearchParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * NEI's "Item Subsets" widget: a bar at the top centre that opens cascading menus of {@link Subsets}. Click the bar to
 * open it, hover a row to open the menu below it, left click shows a subset, right click hides it, double left click
 * shows only that subset, double click on the bar shows everything. It closes half a second after the mouse leaves it.
 *
 * As in NEI a menu lists the items of its subset as icon rows below the rows of the subsets inside it: a click hides or
 * shows that item, Ctrl + click gives it (cheat mode) and Shift + click searches its name (see {@link #searchByClick()}).
 */
final class SubsetMenu {

    private static final int BAR_W = 150;
    private static final int BAR_H = 16;
    private static final int ROW = 18;
    private static final long DOUBLE_CLICK_MS = 500;

    private record Panel(int x, int y, int w, int h, Subsets.Tag owner, int offset) {

        boolean at(double mx, double my) {
            return Draw.in(mx, my, x, y, w, h);
        }

        int rows() {
            return owner.children.size() + owner.own.size();
        }

        int indexAt(double my) {
            final int index = offset + (int) ((my - y) / ROW);
            return index >= 0 && index < rows() ? index : -1;
        }

        /** The subset of a row, or null for an icon row (or no row). */
        Subsets.Tag rowAt(double my) {
            final int index = indexAt(my);
            return index >= 0 && index < owner.children.size() ? owner.children.get(index) : null;
        }

        /** The item of an icon row, or null. */
        ItemEntry itemAt(double my) {
            final int index = indexAt(my) - owner.children.size();
            return index >= 0 && index < owner.own.size() ? owner.own.get(index) : null;
        }
    }

    /** NEI's {@code enableSearchBySubsets}: Shift + click searches only while the subsets search mode is "Prefix". */
    static boolean searchByClick() {
        return NEIConfig.SEARCH_SUBSET_MODE.getAsInt() == SearchParser.PREFIX;
    }

    private static boolean open;
    private static long lastInside;
    private static long lastClick;
    private static Subsets.Tag lastClickTag;
    /** chain.get(i) is the tag whose children are shown in panel i + 1 (panel 0 shows the root's). */
    private static final List<Subsets.Tag> CHAIN = new ArrayList<>();
    private static final Map<Subsets.Tag, Integer> SCROLL = new HashMap<>();
    private static List<Panel> panels = List.of();
    private static int barX;
    private static int barY = 2;
    private static int barW = BAR_W;
    private static int barH = BAR_H;
    private static boolean up; // the bar sits at the bottom (NEI's subsets.widgetPosition off): menus open upwards

    private SubsetMenu() {}

    static void close() {
        open = false;
        CHAIN.clear();
        panels = List.of();
    }

    private static boolean overBar(double mx, double my) {
        return Draw.in(mx, my, barX, barY, barW, barH);
    }

    static boolean contains(double mx, double my) {
        if (overBar(mx, my)) return true;
        if (open) for (Panel p : panels) if (p.at(mx, my)) return true;
        return false;
    }

    /**
     * @param top true: the wide "Item Subsets" bar at the top centre; false: a square "%" button at (sx, sy), left of the
     *            search field, whose menus open upwards (NEI's {@code inventory.subsets.widgetPosition})
     */
    static void render(GuiGraphicsExtractor g, int mx, int my, int sw, int sh, boolean top, int sx, int sy, int size) {
        final Font font = Minecraft.getInstance().font;
        final long now = System.currentTimeMillis();
        up = !top;
        barW = top ? BAR_W : size;
        barH = top ? BAR_H : size;
        barX = top ? (sw - BAR_W) / 2 : sx;
        barY = top ? 2 : sy;

        final boolean overBar = overBar(mx, my);
        Draw.button(g, barX, barY, barW, barH, overBar || open);
        final String label = top ? "Item Subsets" : "%";
        g.text(font, Component.literal(label), barX + (barW - font.width(label)) / 2, barY + (barH - 8) / 2,
                0xFFE0E0E0, true);
        if (!open) return;

        // panels, left to right: each one sits beside the row of the previous panel that opened it
        final List<Panel> list = new ArrayList<>();
        Subsets.Tag owner = Subsets.root();
        int x = barX;
        int y = barY + barH;
        int depth = 0;
        Subsets.Tag hovered = null;
        ItemEntry hoveredItem = null;

        while (owner != null) {
            int width = 0;
            for (Subsets.Tag t : owner.children) width = Math.max(width, font.width(t.name) + 8);
            width = Math.max(width, 30);
            final int totalRows = owner.children.size() + owner.own.size();
            final boolean first = depth == 0 && up;
            final int space = first ? barY - 2 : sh - 2 - y;
            final int visibleRows = Math.max(1, Math.min(totalRows, space / ROW));
            final int offset = Math.max(0, Math.min(SCROLL.getOrDefault(owner, 0), totalRows - visibleRows));
            SCROLL.put(owner, offset);

            int px = x;
            if (depth > 0) {
                final Panel prev = list.get(depth - 1);
                px = prev.x() + prev.w();
                if (px + width > sw - 2) px = prev.x() - width; // no room on the right: open to the left
            }
            final Panel panel = new Panel(px, first ? barY - visibleRows * ROW : y, width, visibleRows * ROW, owner, offset);
            list.add(panel);

            // hover: opens the menu of the row under the mouse and closes the ones deeper than this panel
            Subsets.Tag next = null;
            if (panel.at(mx, my)) {
                final Subsets.Tag t = panel.rowAt(my);
                if (t != null) {
                    hovered = t;
                    while (CHAIN.size() > depth) CHAIN.remove(CHAIN.size() - 1);
                    if (!t.children.isEmpty()) CHAIN.add(t);
                } else if (panel.itemAt(my) != null) {
                    hoveredItem = panel.itemAt(my);
                    while (CHAIN.size() > depth) CHAIN.remove(CHAIN.size() - 1);
                }
            }
            if (CHAIN.size() > depth) {
                next = CHAIN.get(depth);
                final int row = owner.children.indexOf(next) - offset;
                y = panel.y() + Math.max(0, row) * ROW;
                x = panel.x();
            }
            owner = next;
            depth++;
        }
        panels = list;

        for (Panel p : list) {
            for (int i = 0; i < p.h() / ROW; i++) {
                final int ry = p.y() + i * ROW;
                final boolean over = Draw.in(mx, my, p.x(), ry, p.w(), ROW);
                final int index = p.offset() + i;
                if (index >= p.owner().children.size()) { // an icon row: the item, dark when hidden
                    final ItemEntry item = p.owner().own.get(index - p.owner().children.size());
                    if (Subsets.isHidden(item)) {
                        g.fill(p.x(), ry, p.x() + p.w(), ry + ROW, 0xFF000000);
                        g.fill(p.x() + 1, ry + 1, p.x() + p.w() - 1, ry + ROW - 1, 0xFF161D23);
                    } else {
                        Draw.button(g, p.x(), ry, p.w(), ROW, over);
                    }
                    Draw.stack(g, item.stack, p.x() + p.w() / 2 - 8, ry + 1);
                    continue;
                }
                final Subsets.Tag t = p.owner().children.get(index);
                final int state = Subsets.state(t);
                if (state == 0) {
                    g.fill(p.x(), ry, p.x() + p.w(), ry + ROW, 0xFF000000);
                    g.fill(p.x() + 1, ry + 1, p.x() + p.w() - 1, ry + ROW - 1, 0xFF161D23);
                } else {
                    Draw.button(g, p.x(), ry, p.w(), ROW, over || CHAIN.contains(t));
                }
                final int color = over ? 0xFFFFFFA0 : state == 2 ? 0xFFE0E0E0 : 0xFFA0A0A0;
                g.text(font, Component.literal(t.name), p.x() + 4, ry + (ROW - 8) / 2, color, true);
                if (!t.children.isEmpty()) {
                    g.text(font, Component.literal(">"), p.x() + p.w() - 8, ry + (ROW - 8) / 2, 0xFF909090, false);
                }
            }
        }

        if (overBar || hovered != null || hoveredItem != null || contains(mx, my)) lastInside = now;
        else if (now - lastInside > DOUBLE_CLICK_MS) close();

        if (hoveredItem != null) ItemTooltip.request(hoveredItem.stack, HotkeyHelp.Where.SUBSET);
        else if (hovered != null) ItemTooltip.requestSubset(hovered.name);
    }

    /** Returns true if the click was on the widget. A click anywhere else closes the menu and is not consumed. */
    static boolean click(double mx, double my, int button) {
        final long now = System.currentTimeMillis();

        if (overBar(mx, my)) {
            if (button == 0) {
                if (now - lastClick < DOUBLE_CLICK_MS) Subsets.unhideAll();
                else {
                    open = true;
                    lastInside = now;
                }
                lastClick = now;
                lastClickTag = null;
            }
            return true;
        }

        if (open) {
            for (Panel p : panels) {
                if (!p.at(mx, my)) continue;
                final Subsets.Tag t = p.rowAt(my);
                if (t == null && p.itemAt(my) != null) {
                    clickItem(p.itemAt(my), button);
                    return true;
                }
                if (t != null && button == 0 && HotkeyHelp.isShiftDown() && searchByClick()) {
                    // As in NEI: Shift + click searches the subset ("%" prefix, the tag's path without spaces)
                    NEIClientEvents.searchFor("%" + Subsets.pathOf(t).replace(" ", ""));
                    return true;
                }
                if (t != null && (button == 0 || button == 1)) {
                    if (button == 1) Subsets.setHidden(t, true);
                    else if (t == lastClickTag && now - lastClick < DOUBLE_CLICK_MS) Subsets.showOnly(t);
                    else Subsets.setHidden(t, false);
                    lastClick = now;
                    lastClickTag = t;
                }
                return true;
            }
            close();
        }
        return false;
    }

    /** NEI's SubsetSlot#slotClicked for an item row. */
    private static void clickItem(ItemEntry item, int button) {
        if (button != 0 && button != 1) return;
        final var screen = Minecraft.getInstance().screen;
        if (HotkeyHelp.isCtrlDown() && NEIConfig.CHEAT_ENABLED.getAsBoolean() && screen != null) {
            NEIActions.give(item, button == 1 ? 1 : QuantityBar.multiplier(item.stack.getMaxStackSize()), screen);
        } else if (HotkeyHelp.isShiftDown() && searchByClick()) {
            NEIClientEvents.searchFor(SearchParser.escapedText(item.stack.getHoverName().getString()));
        } else {
            Subsets.setHidden(item, button == 1);
        }
    }

    static boolean scrolled(double mx, double my, double dy) {
        if (!open) return false;
        for (Panel p : panels) {
            if (p.at(mx, my)) {
                if (p.rows() > p.h() / ROW) {
                    SCROLL.put(p.owner(), p.offset() + (dy > 0 ? -1 : 1));
                }
                return true;
            }
        }
        return false;
    }
}
