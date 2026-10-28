package codechicken.nei.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import codechicken.nei.ItemList;
import codechicken.nei.NEIConfig;
import codechicken.nei.item.ColorMath;
import codechicken.nei.item.SortOrder;
import codechicken.nei.item.ZoomMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * NEI's options screen as a window over the inventory: a column of section buttons and option rows (label on the left,
 * the value on the right), a Back button, and for colours a picker (saturation / value square, hue bar, alpha bar, hex
 * field) instead of typing a number.
 *
 * The options come from {@link NEIConfig#OPTIONS}, so a new option shows up here without touching this class. Changes
 * apply at once and are written to the config file a moment later.
 *
 * Differences from NEI: only the options this port has; no "Utilities" and "Gamemodes" rows; the background is a plain
 * dark fill.
 */
final class SettingsWindow implements ModalWindow {

    private static final int ROW = 26;
    private static final int HEADER = 28;
    private static final int FOOTER = 34;
    private static final int CONTROL_W = 100;

    private enum Drag {
        NONE, SV, HUE, ALPHA
    }

    private final Screen screen;
    private final Runnable defaultScreen;
    private boolean close;
    private String page; // null = first screen, else a section key
    private int scroll;
    /** Row of the sorting page being dragged, or -1. */
    private int dragSort = -1;
    private int sw;
    private int sh;

    private final Map<NEIConfig.Option, EditBox> boxes = new HashMap<>();
    private NEIConfig.Option focused;

    // colour picker
    private NEIConfig.Option picking;
    private int pickOld;
    private float[] hsv = new float[4];
    private Drag drag = Drag.NONE;
    private final EditBox hexBox = new EditBox(Minecraft.getInstance().font, 0, 0, 80, 18, Component.empty());

    /** @param defaultScreen opens NeoForge's own settings screen (a fallback button in the footer) */
    SettingsWindow(Screen screen, Runnable defaultScreen) {
        this.screen = screen;
        this.defaultScreen = defaultScreen;
        hexBox.setMaxLength(9);
        hexBox.setResponder(t -> {
            final Integer c = ColorMath.parse(t);
            if (c != null && picking != null && hexBox.isFocused()) hsv = ColorMath.toHsv(c);
        });
    }

    @Override
    public Screen screen() {
        return screen;
    }

    @Override
    public boolean wantsClose() {
        return close;
    }

    @Override
    public EditBox typingBox() {
        if (picking != null) return hexBox.isFocused() ? hexBox : null;
        return focused != null ? boxes.get(focused) : null;
    }

    @Override
    public void escape() {
        if (typingBox() != null) {
            blur();
        } else if (picking != null) {
            picking = null;
        } else if (page != null) {
            page = null;
            scroll = 0;
        } else {
            close = true;
        }
    }

    private void blur() {
        if (picking != null) {
            hexBox.setFocused(false);
        } else if (focused != null) {
            boxes.get(focused).setFocused(false);
            boxes.get(focused).setValue(String.valueOf(((ModConfigSpec.ConfigValue<?>) focused.value()).get()));
            focused = null;
        }
    }

    // ---- entries ----

    /** A section button (section), an option row (option) or a sort key row of the sorting page (sort). */
    private record Entry(String section, NEIConfig.Option option, String sort) {}

    private List<Entry> entries() {
        final List<Entry> list = new ArrayList<>();
        if ("sorting".equals(page)) { // NEI's item sorter: the keys in order, drag one to move it
            for (String key : SortOrder.parse(NEIConfig.ITEM_SORT.get())) list.add(new Entry(null, null, key));
            return list;
        }
        if (page == null) for (String key : NEIConfig.SECTIONS.keySet()) list.add(new Entry(key, null, null));
        for (NEIConfig.Option o : NEIConfig.OPTIONS) {
            if (java.util.Objects.equals(o.section(), page)) list.add(new Entry(null, o, null));
        }
        return list;
    }

    private int colW() {
        return Math.min(430, sw - 40);
    }

    private int colX() {
        return (sw - colW()) / 2;
    }

    private int rowY(int index) {
        return HEADER + 8 + index * ROW - scroll;
    }

    private int contentH(int count) {
        return count * ROW + 8;
    }

    private int viewH() {
        return sh - HEADER - FOOTER;
    }

    private int controlX() {
        return colX() + colW() - CONTROL_W - 14;
    }

    // ---- option values ----

    @SuppressWarnings("unchecked")
    private static <T> T get(NEIConfig.Option o) {
        return ((ModConfigSpec.ConfigValue<T>) o.value()).get();
    }

    @SuppressWarnings("unchecked")
    private static <T> void set(NEIConfig.Option o, T value) {
        if (value.equals(get(o))) return;
        ((ModConfigSpec.ConfigValue<T>) o.value()).set(value);
        NEIConfig.changed();
    }

    private EditBox box(NEIConfig.Option o) {
        return boxes.computeIfAbsent(o, k -> {
            final EditBox b = new EditBox(Minecraft.getInstance().font, 0, 0, CONTROL_W, 18, Component.empty());
            b.setMaxLength(10);
            b.setValue(String.valueOf((Object) get(k)));
            b.setResponder(t -> {
                if (t.isEmpty() || t.length() > 9 || !t.chars().allMatch(Character::isDigit)) return;
                final int v = Integer.parseInt(t);
                if (v >= k.min() && v <= k.max()) set(k, v);
            });
            return b;
        });
    }

    // ---- drawing ----

    private static void checker(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        for (int cy = 0; cy < h; cy += 4) {
            for (int cx = 0; cx < w; cx += 4) {
                g.fill(x + cx, y + cy, Math.min(x + cx + 4, x + w), Math.min(y + cy + 4, y + h),
                        ((cx / 4 + cy / 4) & 1) == 0 ? 0xFF9A9A9A : 0xFF6A6A6A);
            }
        }
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mx, int my, int sw, int sh) {
        this.sw = sw;
        this.sh = sh;
        final Font font = Minecraft.getInstance().font;
        final List<Entry> entries = entries();

        scroll = Math.max(0, Math.min(scroll, Math.max(0, contentH(entries.size()) - viewH())));
        g.fill(0, 0, sw, sh, 0xE0101010);

        if (dragSort >= 0) { // the dragged key follows the mouse; the list is sorted again at each move
            final int to = Math.max(0, Math.min(entries.size() - 1, (my - rowY(0)) / ROW));
            if (to != dragSort) {
                NEIConfig.ITEM_SORT.set(SortOrder.save(SortOrder.move(SortOrder.parse(NEIConfig.ITEM_SORT.get()), dragSort, to)));
                NEIConfig.changed();
                dragSort = to;
                ItemList.resort();
            }
        }

        // rows first; the header and footer bars cover whatever scrolls under them
        NEIConfig.Option hoverTip = null;
        String sortTip = null;
        for (int i = 0; i < entries.size(); i++) {
            final int y = rowY(i);
            if (y + ROW < HEADER || y > sh - FOOTER) continue;
            final Entry e = entries.get(i);
            final boolean overRow = picking == null && my >= Math.max(y, HEADER) && my < Math.min(y + ROW, sh - FOOTER)
                    && mx >= colX() && mx < colX() + colW();

            if (e.sort() != null) {
                final String label = SortOrder.label(e.sort());
                Draw.button(g, colX(), y, colW(), 20, overRow || dragSort == i);
                g.text(font, Component.literal(label), colX() + (colW() - font.width(label)) / 2, y + 6,
                        overRow || dragSort == i ? 0xFFFFFFA0 : 0xFFE0E0E0, true);
                if (overRow && dragSort < 0) sortTip = SortOrder.tip(e.sort());
                continue;
            }

            if (e.option() == null) {
                final String label = NEIConfig.SECTIONS.get(e.section());
                Draw.button(g, colX(), y, colW(), 20, overRow);
                g.text(font, Component.literal(label), colX() + (colW() - font.width(label)) / 2, y + 6,
                        overRow ? 0xFFFFFFA0 : 0xFFE0E0E0, true);
                continue;
            }

            final NEIConfig.Option o = e.option();
            if (overRow) hoverTip = o;
            g.text(font, Component.literal(o.label()), colX() + 14, y + 6, overRow ? 0xFFFFFFA0 : 0xFFE0E0E0, true);
            final boolean overControl = overRow && mx >= controlX() && mx < controlX() + CONTROL_W;
            switch (o.kind()) {
                case BOOL -> {
                    final boolean on = get(o);
                    Draw.button(g, controlX(), y, CONTROL_W, 20, overControl);
                    g.text(font, Component.literal(on ? "Yes" : "No"), controlX() + (CONTROL_W - font.width(on ? "Yes" : "No")) / 2,
                            y + 6, on ? 0xFFE0E0E0 : 0xFFA0A0A0, true);
                }
                case CHOICE -> {
                    final String[] names = NEIConfig.CHOICES.get(o.value());
                    final int v = Math.max(0, Math.min(names.length - 1, this.<Integer>get(o)));
                    Draw.button(g, controlX(), y, CONTROL_W, 20, overControl);
                    g.text(font, Component.literal(names[v]), controlX() + (CONTROL_W - font.width(names[v])) / 2, y + 6,
                            0xFFE0E0E0, true);
                }
                case INT -> {
                    final EditBox b = box(o);
                    b.setX(controlX());
                    b.setY(y + 1);
                    b.extractRenderState(g, mx, my, 0f);
                }
                case COLOR -> {
                    final int argb = parse(o);
                    Draw.button(g, controlX(), y, CONTROL_W, 20, overControl);
                    checker(g, controlX() + 3, y + 3, CONTROL_W - 6, 14);
                    g.fill(controlX() + 3, y + 3, controlX() + CONTROL_W - 3, y + 17, argb);
                    final String hex = ColorMath.hex(argb);
                    g.text(font, Component.literal(hex), controlX() + (CONTROL_W - font.width(hex)) / 2, y + 6,
                            ((argb >> 16 & 255) + (argb >> 8 & 255) + (argb & 255)) / 3 > 128 && (argb >>> 24) > 128 ? 0xFF000000
                                    : 0xFFFFFFFF, false);
                }
            }
        }

        // header and footer bars
        g.fill(0, 0, sw, HEADER, 0xFF1B1B1B);
        g.fill(0, HEADER - 1, sw, HEADER, 0xFF000000);
        g.fill(0, sh - FOOTER, sw, sh, 0xFF1B1B1B);
        g.fill(0, sh - FOOTER, sw, sh - FOOTER + 1, 0xFF000000);
        final String title = page == null ? "NEI Settings" : NEIConfig.SECTIONS.get(page);
        g.text(font, Component.literal(title), (sw - font.width(title)) / 2, 10, 0xFFFFFFFF, true);

        final int by = sh - FOOTER + 7;
        final int bx = (sw - 200) / 2;
        Draw.button(g, bx, by, 200, 20, picking == null && Draw.in(mx, my, bx, by, 200, 20));
        final String back = page == null ? "Back" : "Back to the main settings";
        g.text(font, Component.literal(back), bx + (200 - font.width(back)) / 2, by + 6, 0xFFE0E0E0, true);
        if (defaultScreen != null && sw >= 620) {
            Draw.button(g, sw - 150, by, 140, 20, picking == null && Draw.in(mx, my, sw - 150, by, 140, 20));
            g.text(font, Component.literal("NeoForge screen"), sw - 150 + (140 - font.width("NeoForge screen")) / 2, by + 6,
                    0xFFE0E0E0, true);
        }

        // scroll bar
        final int total = contentH(entries.size());
        if (total > viewH()) {
            final int barH = Math.max(20, viewH() * viewH() / total);
            final int barY = HEADER + (viewH() - barH) * scroll / (total - viewH());
            g.fill(colX() + colW() + 4, barY, colX() + colW() + 8, barY + barH, 0xFFE0E0E0);
        }

        if (hoverTip != null && picking == null && my >= HEADER && my < sh - FOOTER) {
            final List<Component> lines = new ArrayList<>();
            for (String l : ZoomMath.wrap(hoverTip.tip(), 260, font::width)) lines.add(Component.literal(l));
            ItemTooltip.requestPlain(lines);
        }

        if (sortTip != null && my >= HEADER && my < sh - FOOTER) ItemTooltip.requestPlain(List.of(Component.literal(sortTip)));

        if (picking != null) renderPicker(g, font, mx, my);
    }

    private static int parse(NEIConfig.Option o) {
        final Integer c = ColorMath.parse(String.valueOf((Object) get(o)));
        return c == null ? 0xFFFFFFFF : c;
    }

    // ---- colour picker ----

    private static final int PICK_W = 280;
    private static final int PICK_H = 200;
    private static final int SV = 128;
    private static final int CELL = 4;

    private int px() {
        return (sw - PICK_W) / 2;
    }

    private int py() {
        return (sh - PICK_H) / 2;
    }

    private int current() {
        return ColorMath.fromHsv(hsv[0], hsv[1], hsv[2], hsv[3]);
    }

    private void updateDrag(int mx, int my) {
        final float fx = Math.max(0, Math.min(1, (mx - (px() + 12)) / (float) (SV - 1)));
        final float fy = Math.max(0, Math.min(1, (my - (py() + 28)) / (float) (SV - 1)));
        switch (drag) {
            case SV -> {
                hsv[1] = fx;
                hsv[2] = 1 - fy;
            }
            case HUE -> hsv[0] = fy * 360f;
            case ALPHA -> hsv[3] = fx;
            default -> { }
        }
        if (drag != Drag.NONE) hexBox.setValue(ColorMath.hex(current()));
    }

    private void renderPicker(GuiGraphicsExtractor g, Font font, int mx, int my) {
        if (drag != Drag.NONE) updateDrag(mx, my);
        final int x = px();
        final int y = py();
        g.fill(0, 0, sw, sh, 0xA0000000);
        Draw.panel(g, x, y, PICK_W, PICK_H);
        g.text(font, Component.literal(NEIConfig.SECTIONS.getOrDefault(picking.section(), "") + ": " + picking.label()), x + 12, y + 10,
                0xFF404040, false);

        final int cells = SV / CELL;
        for (int cy = 0; cy < cells; cy++) {
            for (int cx = 0; cx < cells; cx++) {
                g.fill(x + 12 + cx * CELL, y + 28 + cy * CELL, x + 12 + (cx + 1) * CELL, y + 28 + (cy + 1) * CELL,
                        ColorMath.fromHsv(hsv[0], cx / (float) (cells - 1), 1 - cy / (float) (cells - 1), 1));
            }
        }
        // hue bar
        for (int cy = 0; cy < cells; cy++) {
            g.fill(x + 150, y + 28 + cy * CELL, x + 164, y + 28 + (cy + 1) * CELL, ColorMath.fromHsv(cy / (float) (cells - 1) * 360f, 1, 1, 1));
        }
        // alpha bar
        checker(g, x + 12, y + 164, SV, 12);
        for (int cx = 0; cx < cells; cx++) {
            g.fill(x + 12 + cx * CELL, y + 164, x + 12 + (cx + 1) * CELL, y + 176,
                    ColorMath.fromHsv(hsv[0], hsv[1], hsv[2], cx / (float) (cells - 1)));
        }

        // markers
        final int sx = x + 12 + Math.round(hsv[1] * (SV - 1));
        final int sy = y + 28 + Math.round((1 - hsv[2]) * (SV - 1));
        g.fill(sx - 3, sy - 3, sx + 4, sy - 2, 0xFFFFFFFF);
        g.fill(sx - 3, sy + 3, sx + 4, sy + 4, 0xFFFFFFFF);
        g.fill(sx - 3, sy - 3, sx - 2, sy + 4, 0xFFFFFFFF);
        g.fill(sx + 3, sy - 3, sx + 4, sy + 4, 0xFFFFFFFF);
        final int hy = y + 28 + Math.round(hsv[0] / 360f * (SV - 1));
        g.fill(x + 148, hy, x + 166, hy + 1, 0xFFFFFFFF);
        final int ax = x + 12 + Math.round(hsv[3] * (SV - 1));
        g.fill(ax, y + 162, ax + 1, y + 178, 0xFFFFFFFF);

        // old / new swatches
        g.text(font, Component.literal("Old"), x + 180, y + 28, 0xFF404040, false);
        checker(g, x + 180, y + 38, 40, 24);
        g.fill(x + 180, y + 38, x + 220, y + 62, pickOld);
        g.text(font, Component.literal("New"), x + 230, y + 28, 0xFF404040, false);
        checker(g, x + 230, y + 38, 40, 24);
        g.fill(x + 230, y + 38, x + 270, y + 62, current());

        g.text(font, Component.literal("Hex #AARRGGBB"), x + 180, y + 74, 0xFF404040, false);
        hexBox.setX(x + 180);
        hexBox.setY(y + 86);
        hexBox.setWidth(90);
        if (!hexBox.isFocused() && drag == Drag.NONE && !hexBox.getValue().equalsIgnoreCase(ColorMath.hex(current()))) {
            hexBox.setValue(ColorMath.hex(current()));
        }
        hexBox.extractRenderState(g, mx, my, 0f);

        final int by = y + PICK_H - 30;
        Draw.button(g, x + 180, by - 4, 44, 20, Draw.in(mx, my, x + 180, by - 4, 44, 20));
        g.text(font, Component.literal("OK"), x + 180 + (44 - font.width("OK")) / 2, by + 2, 0xFFE0E0E0, true);
        Draw.button(g, x + 228, by - 4, 44, 20, Draw.in(mx, my, x + 228, by - 4, 44, 20));
        g.text(font, Component.literal("Cancel"), x + 228 + (44 - font.width("Cancel")) / 2, by + 2, 0xFFE0E0E0, true);
    }

    // ---- input ----

    @Override
    public void released() {
        drag = Drag.NONE;
        dragSort = -1;
    }

    @Override
    public void click(double mx, double my, int button, boolean ctrl, boolean shift) {
        if (picking != null) {
            clickPicker(mx, my, button);
            return;
        }
        if (button != 0) return;

        final int by = sh - FOOTER + 7;
        if (Draw.in(mx, my, (sw - 200) / 2, by, 200, 20)) {
            blur();
            escape();
            return;
        }
        if (defaultScreen != null && sw >= 620 && Draw.in(mx, my, sw - 150, by, 140, 20)) {
            defaultScreen.run();
            return;
        }

        final List<Entry> entries = entries();
        boolean hitBox = false;
        if (my >= HEADER && my < sh - FOOTER) {
            for (int i = 0; i < entries.size(); i++) {
                final int y = rowY(i);
                if (my < y || my >= y + ROW) continue;
                final Entry e = entries.get(i);
                if (e.sort() != null) {
                    if (mx >= colX() && mx < colX() + colW()) dragSort = i;
                    return;
                }
                if (e.option() == null) {
                    if (mx >= colX() && mx < colX() + colW()) {
                        blur();
                        page = e.section();
                        scroll = 0;
                        return;
                    }
                    continue;
                }
                final NEIConfig.Option o = e.option();
                if (mx < controlX() || mx >= controlX() + CONTROL_W) continue;
                switch (o.kind()) {
                    case BOOL -> {
                        final boolean on = get(o);
                        set(o, !on);
                    }
                    case CHOICE -> {
                        final int count = NEIConfig.CHOICES.get(o.value()).length;
                        set(o, (this.<Integer>get(o) + 1) % count);
                        codechicken.nei.ItemList.refilter(); // the search modes change what the current search finds
                    }
                    case INT -> {
                        if (focused != o) blur();
                        focused = o;
                        box(o).setFocused(true);
                        hitBox = true;
                    }
                    case COLOR -> {
                        blur();
                        picking = o;
                        pickOld = parse(o);
                        hsv = ColorMath.toHsv(pickOld);
                        hexBox.setValue(ColorMath.hex(pickOld));
                        hexBox.setFocused(false);
                    }
                }
                break;
            }
        }
        if (!hitBox) blur();
    }

    private void clickPicker(double mx, double my, int button) {
        final int x = px();
        final int y = py();
        if (button != 0) return;
        final int by = y + PICK_H - 34;
        if (Draw.in(mx, my, x + 180, by, 44, 20)) {
            set(picking, ColorMath.hex(current()));
            picking = null;
            return;
        }
        if (Draw.in(mx, my, x + 228, by, 44, 20)) {
            picking = null;
            return;
        }
        if (Draw.in(mx, my, x + 12, y + 28, SV, SV)) drag = Drag.SV;
        else if (Draw.in(mx, my, x + 150, y + 28, 14, SV)) drag = Drag.HUE;
        else if (Draw.in(mx, my, x + 12, y + 164, SV, 12)) drag = Drag.ALPHA;
        hexBox.setFocused(Draw.in(mx, my, hexBox.getX(), hexBox.getY(), hexBox.getWidth(), hexBox.getHeight()));
        if (drag != Drag.NONE) updateDrag((int) mx, (int) my);
    }

    @Override
    public void scrolled(double mx, double my, double dy) {
        if (picking != null) return;
        scroll = Math.max(0, scroll - (int) Math.signum(dy) * ROW);
    }
}
