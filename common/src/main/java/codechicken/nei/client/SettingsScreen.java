package codechicken.nei.client;

import codechicken.nei.Cfg;
import codechicken.nei.ItemList;
import codechicken.nei.NEIConfig;
import codechicken.nei.item.ColorMath;
import codechicken.nei.item.SortOrder;
import codechicken.nei.item.ZoomMath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * NEI's options screen ({@code config/GuiOptionList}, drawn with CodeChickenCore's {@code GuiScrollSlot}): a whole screen
 * over the dimmed world, not a window over the inventory.
 *
 * Layout as in NEI: the list is {@code min(width - 80, 320)} wide and starts 20 pixels from the top; its rows are 20
 * high with a 4 pixel gap and start 24 pixels in; the dirt bands above and below it end in 4 pixel fades to black; the
 * scrollbar is 6 pixels wide on a black track and is always drawn; the title is at y = 6; the Back button is 200 wide
 * (at most) at {@code height - 25}. An option row has its label at x = 10, y = 6 and a button on the right that is
 * {@code max(60, text width + 8)} wide; a number option has a text field in that place; a section is a button as wide
 * as the row. A click works when the mouse button is let go over the row it went down on. Escape closes every options
 * screen at once, Back goes up one.
 *
 * The options come from {@link NEIConfig#OPTIONS}, so a new option shows up here without touching this class.
 * Changes apply at once and are written to the config file a moment later.
 *
 * Differences from NEI: the Global button is drawn but does nothing (no per world overrides with W buttons; per world
 * settings are separate options here), an NEI option NEI Rewrite has no feature for is a greyed out row that does nothing,
 * no Panels Settings screen, a colour picker for colour options (NEI types the colour), and today's vanilla menu
 * background for the dirt bands. Left out on purpose: Item IDs, the Grid Rendering Cache options, Optimize computation for
 * GUI overlap and Item Untranslator.
 */
final class SettingsScreen extends Screen {

    private static final int AREA_Y = 20;
    private static final int ROW = 24; // 20 high plus the 4 pixel gap
    private static final int GLFW_KEY_ESCAPE = 256;
    private static final int GLFW_KEY_BACKSPACE = 259;

    /** A section button (section) or an option row (option). */
    private record Entry(String section, NEIConfig.Option option) {}

    /** One key of the item sorter: its place on the page eases towards its slot, or follows the mouse while dragged. */
    private static final class SortSlot {
        final String key;
        double y;
        double ya;

        SortSlot(String key, double y) {
            this.key = key;
            this.y = y;
            this.ya = y;
        }
    }

    private final Screen parent;
    private final String page; // null = the first screen, else a section key
    private final boolean sorter; // the item sorter page (NEI's GuiItemSorter)
    private final List<Entry> entries = new ArrayList<>();

    // GuiScrollPane's scroll state
    private float percent;
    private int scrollClickY = -1;
    private float scrollPercent;
    private int scrollMouseY;

    private int pressSlot = -1;
    private int pressButton = -1;
    private int mouseY;

    private final Map<NEIConfig.Option, EditBox> boxes = new HashMap<>();
    private NEIConfig.Option focused;
    private final ColorPickerPopup picker = new ColorPickerPopup();

    // the item sorter
    private List<String> sortKeys = new ArrayList<>();
    private final List<SortSlot> sortSlots = new ArrayList<>();
    private boolean dragging;
    private SortSlot dragged;
    private int mouseClickedY;
    private double dragStartY;

    /** Opens the first screen; {@code parent} is the screen it returns to. */
    SettingsScreen(Screen parent) {
        this(parent, null);
    }

    private SettingsScreen(Screen parent, String page) {
        super(Component.literal(page == null ? "NEI Options" : NEIConfig.SECTIONS.getOrDefault(page, page)));
        this.parent = parent;
        this.page = page;
        this.sorter = "inventory.itemsort".equals(page);
        if (sorter) {
            sortKeys = new ArrayList<>(SortOrder.parse(NEIConfig.ITEM_SORT.get()));
            for (int i = 0; i < sortKeys.size(); i++) sortSlots.add(new SortSlot(sortKeys.get(i), slotY(i)));
        } else {
            // NEI's OptionList: the options of this page in the order they were added, and a button for each page below it
            // at the place of its first option
            final java.util.Set<String> seen = new java.util.HashSet<>();
            final String prefix = page == null ? "" : page + ".";
            for (NEIConfig.Option o : NEIConfig.OPTIONS) {
                final String path = o.section(); // null: a row of the first screen (Report Issues)
                if (java.util.Objects.equals(path, page)) {
                    // a row with a value, a greyed out NEI row, or a button that does something (Dump, Report Issues)
                    if (o.value() != null || stub(o) || o.kind() == NEIConfig.Kind.ACTION) entries.add(new Entry(null, o));
                } else if (path != null && path.startsWith(prefix)) {
                    final String rest = path.substring(prefix.length());
                    final int dot = rest.indexOf('.');
                    final String child = prefix + (dot < 0 ? rest : rest.substring(0, dot));
                    if (seen.add(child)) entries.add(new Entry(child, null));
                }
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- geometry (GuiOptionList.OptionScrollSlot / GuiOptionPane.ScrollPane) ----

    private int areaW() {
        return Math.min(width - 80, 320);
    }

    private int areaX() {
        return (width - areaW()) / 2;
    }

    private int areaH() {
        return height - 50;
    }

    private int winY() {
        return AREA_Y + 4;
    }

    private int winH() {
        return areaH() - 8;
    }

    /** The slot window: the list leaves 20 pixels on the right, the sorter pane 24 on the left. */
    private int winX() {
        return sorter ? areaX() + 24 : areaX();
    }

    private int winW() {
        return sorter ? areaW() - 44 : areaW() - 20;
    }

    /** Where a row's content starts and how wide it is ({@code slotWidth}). */
    private int rowX() {
        return areaX() + 24;
    }

    private int rowW() {
        return areaW() - 44;
    }

    private int count() {
        return sorter ? sortSlots.size() : entries.size();
    }

    private int contentH() {
        return count() * ROW;
    }

    private static int slotY(int index) {
        return 2 + index * ROW;
    }

    private int scrolled() {
        return Math.max(0, (int) ((contentH() - winH()) * percent + 0.5));
    }

    private int rowTop(int index) {
        return winY() - scrolled() + index * ROW + 2;
    }

    private int barH() {
        final int h = (int) ((winH() / (double) Math.max(1, contentH())) * areaH());
        return h > areaH() ? areaH() : h < areaH() / 15 ? areaH() / 15 : h;
    }

    private int barX() {
        return areaX() + areaW() - 6;
    }

    private int barY() {
        return AREA_Y + (int) ((areaH() - barH()) * percent + 0.4999);
    }

    private void calcPercent() {
        final int empty = areaH() - barH();
        if (scrollClickY >= 0 && empty > 0) percent = (scrollMouseY - scrollClickY) / (float) empty + scrollPercent;
        percent = Math.max(0, Math.min(1, percent));
    }

    private void scroll(int i) {
        percent += i * areaH() / (float) (2 * Math.max(1, contentH()));
        calcPercent();
    }

    private boolean inWindow(double mx, double my) {
        return mx >= winX() && mx < winX() + winW() && my >= winY() && my < winY() + winH();
    }

    private int slotAt(double my) {
        final int rel = (int) my - winY() + scrolled();
        if (rel < 0) return -1;
        final int slot = rel / ROW;
        return slot < count() ? slot : -1;
    }

    private int backW() {
        return Math.min(200, width - 40);
    }

    private int backX() {
        return (width - backW()) / 2;
    }

    private int backY() {
        return height - 25;
    }

    // ---- option values ----

    @SuppressWarnings("unchecked")
    private static <T> T get(NEIConfig.Option o) {
        return ((Cfg.ConfigValue<T>) o.value()).get();
    }

    @SuppressWarnings("unchecked")
    private static <T> void set(NEIConfig.Option o, T value) {
        if (value.equals(get(o))) return;
        ((Cfg.ConfigValue<T>) o.value()).set(value);
        NEIConfig.changed();
        final Runnable after = NEIConfig.AFTER_CHANGE.get(o.value());
        if (after != null) after.run();
    }

    /** NEI's {@code Option#isEnabled}: a row whose condition does not hold is greyed out and does nothing. */
    private static boolean disabled(NEIConfig.Option o) {
        final java.util.function.BooleanSupplier cond = o.value() == null ? null : NEIConfig.DISABLED_WHEN.get(o.value());
        return cond != null && cond.getAsBoolean();
    }

    /** A row with a text field: a number (NEI's OptionIntegerField) or a text (OptionTextField). */
    private static boolean field(NEIConfig.Option o) {
        return o.kind() == NEIConfig.Kind.INT || o.kind() == NEIConfig.Kind.TEXT;
    }

    /** A greyed out row of an NEI option NEI Rewrite has no feature for. */
    private static boolean stub(NEIConfig.Option o) {
        return o.kind() == NEIConfig.Kind.STUB || o.kind() == NEIConfig.Kind.STUB_FIELD;
    }

    private static int parse(NEIConfig.Option o) {
        final Integer c = ColorMath.parse(String.valueOf((Object) get(o)));
        return c == null ? 0xFFFFFFFF : c;
    }

    private EditBox box(NEIConfig.Option o) {
        return boxes.computeIfAbsent(o, k -> {
            final EditBox b = new EditBox(Minecraft.getInstance().font, 0, 0, 60, 20, Component.empty());
            if (k.kind() == NEIConfig.Kind.TEXT) {
                b.setMaxLength(200);
                b.setValue(String.valueOf((Object) get(k)));
                b.setResponder(t -> set(k, t)); // NEI's OptionTextField: every change is kept
                return b;
            }
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

    /** NEI's OptionIntegerField: wide enough for the biggest value, at least 60, at most what the label leaves. */
    private int boxW(Font font, NEIConfig.Option o) {
        if (o.kind() == NEIConfig.Kind.TEXT) return Math.max(60, rowW() - font.width(o.label()) - 26);
        final int maxInput = font.width(String.valueOf(o.max())) + 32;
        return Math.max(60, Math.min(maxInput, rowW() - font.width(o.label())) - 16);
    }

    /** The width NEI's text field would have: a number field fits its biggest value, a text field what the label leaves. */
    private int stubBoxW(Font font, NEIConfig.Option o) {
        final int maxInput = o.max() > 0 ? font.width(String.valueOf(o.max())) + 32 : rowW();
        return Math.max(60, Math.min(maxInput, rowW() - font.width(o.label())) - 16);
    }

    /** A text field that cannot be used: the vanilla field's frame, NEI's default value in grey. */
    private void drawStubField(GuiGraphicsExtractor g, Font font, NEIConfig.Option o, int top) {
        final int w = stubBoxW(font, o);
        final int x = rowX() + rowW() - w;
        g.text(font, Component.literal(crop(font, o.label(), x - rowX() - 10)), rowX() + 10, top + 6, 0xFF808080, true);
        g.fill(x, top, x + w, top + 20, 0xFF555555);
        g.fill(x + 1, top + 1, x + w - 1, top + 19, 0xFF000000);
        g.text(font, Component.literal(crop(font, buttonText(o), w - 8)), x + 4, top + 6, 0xFF707070, false);
    }

    private void blur() {
        if (focused != null) {
            final EditBox b = boxes.get(focused);
            b.setFocused(false);
            b.setValue(String.valueOf((Object) get(focused))); // an unfinished or invalid text goes back to the value
            focused = null;
        }
    }

    // ---- button text and size (OptionButton) ----

    private String buttonText(NEIConfig.Option o) {
        return switch (o.kind()) {
            case BOOL -> {
                final String[] text = NEIConfig.BOOL_TEXT.get(o.value());
                final boolean on = this.<Boolean>get(o);
                yield text != null ? text[on ? 0 : 1] : on ? "Yes" : "No";
            }
            case CHOICE -> {
                final String[] names = NEIConfig.CHOICES.get(o.value());
                yield names[Math.max(0, Math.min(names.length - 1, this.<Integer>get(o)))];
            }
            case COLOR -> ColorMath.hex(parse(o));
            case MODE -> NEIConfig.MODE_NAMES[NEIConfig.cheatMode()];
            case ACTION, STUB, STUB_FIELD -> NEIConfig.STUB_TEXT.getOrDefault(o, "");
            case INT, SET, TEXT -> "";
        };
    }

    private int buttonW(Font font, NEIConfig.Option o) {
        if (o.label().isEmpty()) return rowW(); // an option without a prefix: the button takes the whole row (NEI)
        final int min = o.kind() == NEIConfig.Kind.COLOR ? 80 : 60;
        return Math.max(min, font.width(buttonText(o)) + 8);
    }

    private static String crop(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String s = text;
        while (!s.isEmpty() && font.width(s + "...") > maxWidth) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    // ---- a row of icon buttons (NEI's OptionStringSet: Utilities, Gamemodes) ----

    /** {@code getSlot().slotWidth() - (24 * options.size() - 4)}, in screen coordinates. */
    private int setX(int count) {
        return rowX() + rowW() - (24 * count - 4);
    }

    private List<Component> drawSet(GuiGraphicsExtractor g, Font font, NEIConfig.Option o, int top, int mx, int my,
            boolean live, List<Component> tip) {
        g.text(font, Component.literal(o.label()), rowX() + 10, top + 6, 0xFFFFFFFF, true);
        final String[][] items = NEIConfig.SET_ITEMS.get(o.value());
        final List<String> on = NEIConfig.entries((Cfg.StringValue) o.value());
        List<Component> result = tip;
        if (live && Draw.in(mx, my, rowX(), top, rowW(), 20)) result = null; // the row's own tip only over the label
        for (int k = 0; k < items.length; k++) {
            final int bx = setX(items.length) + k * 24;
            Draw.neiButton(g, bx, top, 20, 20, on.contains(items[k][0]) ? 1 : 0);
            setIcon(g, items[k][0], bx, top);
            if (live && Draw.in(mx, my, bx, top, 20, 20)) result = List.of(Component.literal(items[k][1]));
        }
        if (live && Draw.in(mx, my, rowX() + 4, top + 4, 50, 20)) { // NEI: the label's own tip
            result = new ArrayList<>();
            for (String l : ZoomMath.wrap(o.tip(), 260, font::width)) result.add(Component.literal(l));
        }
        return result;
    }

    /** The icons of OptionUtilities / OptionGamemodes: 12 x 12 pieces of NEI's icon sheet, or a few items. */
    private static void setIcon(GuiGraphicsExtractor g, String key, int x, int y) {
        int u = -1;
        int v = 0;
        switch (key) {
            case "time" -> { u = 120; v = 24; }
            case "rain" -> { u = 120; v = 12; }
            case "heal" -> { u = 168; v = 24; }
            case "delete" -> { u = 144; v = 12; }
            case "magnet" -> { u = 180; v = 24; }
            case "gamemode", "creative" -> { u = 132; v = 12; }
            case "creative+" -> { u = 156; v = 12; }
            case "adventure" -> { u = 168; v = 12; }
            case "enchant" -> Draw.stack(g, net.minecraft.world.item.Items.ENCHANTED_BOOK.getDefaultInstance(), x + 2, y + 2);
            case "potion" -> Draw.stack(g, net.minecraft.world.item.Items.POTION.getDefaultInstance(), x + 2, y + 2);
            case "item" -> Draw.stack(g, net.minecraft.world.item.Items.STONE.getDefaultInstance(), x + 2, y + 2);
            default -> { }
        }
        if (u >= 0) Tex.blit(g, Tex.ICONS, 256, 256, x + 4, y + 4, u, v, 12, 12);
    }

    /** OptionStringSet.clickButton: left click switches an entry on, right click switches it off. */
    private void clickSet(NEIConfig.Option o, int top, double mx, double my) {
        final String[][] items = NEIConfig.SET_ITEMS.get(o.value());
        final Cfg.StringValue value = (Cfg.StringValue) o.value();
        final List<String> on = new ArrayList<>(NEIConfig.entries(value));
        for (int k = 0; k < items.length; k++) {
            final int bx = setX(items.length) + k * 24;
            if (!Draw.in(mx, my, bx, top, 20, 20)) continue;
            final boolean has = on.contains(items[k][0]);
            if (pressButton == 0 && !has) on.add(items[k][0]);
            else if (pressButton == 1 && has) on.remove(items[k][0]);
            else return;
            value.set(String.join(", ", on));
            NEIConfig.changed();
            click();
            return;
        }
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ---- drawing ----

    /** GuiCCButton / OptionButton: NEI's button background, text 0xE0E0E0, 0xFFFFA0 under the mouse. */
    private static void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, String text, boolean hot) {
        Draw.neiButton(g, x, y, w, 20, hot ? 2 : 1);
        g.text(font, Component.literal(text), x + (w - font.width(text)) / 2, y + 6, hot ? 0xFFFFFFA0 : 0xFFE0E0E0, true);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float partial) {
        NEIConfig.flush();
        mouseY = my;
        final Font font = Minecraft.getInstance().font;
        final boolean live = !picker.isOpen();
        calcPercent();

        // drawDefaultBackground: the world, dimmed
        g.fillGradient(0, 0, width, height, 0xC0101010, 0xD0101010);

        // the scrollbar's track is the only thing with a background
        g.fill(barX(), AREA_Y, barX() + 6, AREA_Y + areaH(), 0xFF000000);

        List<Component> tip = null;
        if (sorter) {
            tip = drawSorter(g, font, mx, my, partial, live);
        } else {
            for (int i = 0; i < entries.size(); i++) {
                final int top = rowTop(i);
                if (top + 20 < AREA_Y || top > AREA_Y + areaH()) continue;
                final Entry e = entries.get(i);
                if (e.option() == null) {
                    final String label = NEIConfig.SECTIONS.get(e.section());
                    button(g, font, rowX(), top, rowW(), label, live && Draw.in(mx, my, rowX(), top, rowW(), 20));
                    continue;
                }
                final NEIConfig.Option o = e.option();
                if (live && inWindow(mx, my) && Draw.in(mx, my, rowX(), top, rowW(), 20)) {
                    tip = new ArrayList<>();
                    for (String l : ZoomMath.wrap(o.tip(), 260, font::width)) tip.add(Component.literal(l));
                }
                if (o.kind() == NEIConfig.Kind.STUB_FIELD) {
                    drawStubField(g, font, o, top);
                    continue;
                }
                if (o.kind() == NEIConfig.Kind.SET) {
                    tip = drawSet(g, font, o, top, mx, my, live && inWindow(mx, my), tip);
                    continue;
                }
                if (field(o)) {
                    g.text(font, Component.literal(o.label()), rowX() + 10, top + 6, 0xFFFFFFFF, true);
                    final EditBox b = box(o);
                    final int w = boxW(font, o);
                    b.setWidth(w);
                    b.setX(rowX() + rowW() - w);
                    b.setY(top);
                    b.extractRenderState(g, mx, my, 0f);
                    continue;
                }
                final int bw = buttonW(font, o);
                final int bx = rowX() + rowW() - bw;
                final boolean greyed = o.kind() == NEIConfig.Kind.STUB || disabled(o);
                if (!o.label().isEmpty()) {
                    g.text(font, Component.literal(crop(font, o.label(), bx - rowX() - 10)), rowX() + 10, top + 6,
                            greyed ? 0xFF808080 : 0xFFFFFFFF, true);
                }
                final boolean hot = live && Draw.in(mx, my, bx, top, bw, 20);
                if (o.kind() == NEIConfig.Kind.ACTION && NEIConfig.ACTION_MODES.containsKey(o)) {
                    // NEI's DataDumper mode button, 60 wide, 6 left of the Dump button
                    final int mbx = bx - 66;
                    final boolean modeHot = live && Draw.in(mx, my, mbx, top, 60, 20);
                    Draw.neiButton(g, mbx, top, 60, 20, modeHot ? 2 : 1);
                    final String modeText = NEIConfig.ACTION_MODE_NAMES.get(o)[NEIConfig.ACTION_MODES.get(o).getAsInt()];
                    g.text(font, Component.literal(modeText), mbx + (60 - font.width(modeText)) / 2, top + 6,
                            modeHot ? 0xFFFFFFA0 : 0xFFE0E0E0, true);
                }
                if (greyed) {
                    Draw.neiButton(g, bx, top, bw, 20, 0);
                    final String text = buttonText(o);
                    g.text(font, Component.literal(text), bx + (bw - font.width(text)) / 2, top + 6, 0xFF707070, true);
                } else if (o.kind() == NEIConfig.Kind.COLOR) {
                    final int argb = parse(o);
                    Draw.neiButton(g, bx, top, bw, 20, hot ? 2 : 1);
                    ColorPickerPopup.checker(g, bx + 3, top + 3, bw - 6, 14);
                    g.fill(bx + 3, top + 3, bx + bw - 3, top + 17, argb);
                    final String hex = buttonText(o);
                    g.text(font, Component.literal(hex), bx + (bw - font.width(hex)) / 2, top + 6,
                            ((argb >> 16 & 255) + (argb >> 8 & 255) + (argb & 255)) / 3 > 128 && (argb >>> 24) > 128
                                    ? 0xFF000000 : 0xFFFFFFFF,
                            false);
                } else {
                    button(g, font, bx, top, bw, buttonText(o), hot);
                }
            }
        }

        // OptionScrollPane.drawOverlay: dirt above and below, each ending in a 4 pixel fade to black
        Draw.dirt(g, 0, 0, width, AREA_Y);
        Draw.dirt(g, 0, AREA_Y + areaH(), width, height - AREA_Y - areaH());
        g.fillGradient(0, AREA_Y, width, AREA_Y + 4, 0xFF000000, 0x00000000);
        g.fillGradient(0, AREA_Y + areaH() - 4, width, AREA_Y + areaH(), 0x00000000, 0xFF000000);

        // GuiScrollPane.drawScrollbar (no guide line: scrollbarGuideAlignment is 0)
        final int bx = barX();
        final int by = barY();
        final int bh = barH();
        g.fill(bx, by, bx + 6, by + bh, 0xFF8B8B8B);
        g.fill(bx, by, bx + 5, by + bh - 1, 0xFFF0F0F0);
        g.fill(bx + 1, by + 1, bx + 6, by + bh, 0xFF555555);
        g.fill(bx + 1, by + 1, bx + 5, by + bh - 1, 0xFFC6C6C6);

        button(g, font, backX(), backY(), backW(), "Back", live && Draw.in(mx, my, backX(), backY(), backW(), 20));

        if (!sorter) { // GuiOptionList's worldButton: 60 x 16 at the top right. Per world overrides are not built.
            final int gx = width - 75;
            Draw.neiButton(g, gx, 2, 60, 16, 0);
            g.text(font, Component.literal("Global"), gx + (60 - font.width("Global")) / 2, 6, 0xFF707070, true);
            if (live && Draw.in(mx, my, gx, 2, 60, 16)) {
                tip = List.of(Component.literal("Options apply to all worlds"),
                        Component.literal("Not implemented in NEI Rewrite yet: per world overrides (the W buttons)."));
            }
        }

        final String title = getTitle().getString();
        g.text(font, Component.literal(title), width / 2 - font.width(title) / 2, 6, 0xFFFFFFFF, true);

        if (picker.isOpen()) {
            picker.render(g, font, mx, my, width, height);
        } else if (tip != null) {
            ItemTooltip.drawPlain(g, mx, my, width, height, tip);
        }
    }

    // ---- the item sorter (GuiItemSorter) ----

    private SortSlot sortAt(double mx, double my) {
        if (!inWindow(mx, my)) return null;
        final double rel = my - (winY() - scrolled());
        if (dragged != null && rel >= dragged.y && rel < dragged.y + 20) return dragged;
        for (SortSlot s : sortSlots) if (rel >= s.y && rel < s.y + 20) return s;
        return null;
    }

    private List<Component> drawSorter(GuiGraphicsExtractor g, Font font, int mx, int my, float partial, boolean live) {
        final SortSlot over = live ? sortAt(mx, my) : null;
        for (SortSlot s : sortSlots) if (s != dragged) drawSortSlot(g, font, s, over, partial);
        if (dragged != null) drawSortSlot(g, font, dragged, over, partial);
        if (over != null && !dragging && SortOrder.tip(over.key) != null) {
            final List<Component> tip = new ArrayList<>();
            for (String l : ZoomMath.wrap(SortOrder.tip(over.key), 260, font::width)) tip.add(Component.literal(l));
            return tip;
        }
        return null;
    }

    private void drawSortSlot(GuiGraphicsExtractor g, Font font, SortSlot s, SortSlot over, float partial) {
        final double y = s.ya + (s.y - s.ya) * partial;
        final int top = (int) Math.round(winY() - scrolled() + y);
        button(g, font, winX(), top, winW(), SortOrder.label(s.key), over == s);
    }

    private static double approachExp(double a, double b, double ratio, double cap) {
        double d = (b - a) * ratio;
        if (Math.abs(d) > cap) d = Math.signum(d) * cap;
        return a + d;
    }

    @Override
    public void tick() {
        if (!sorter) return;
        for (SortSlot s : sortSlots) {
            s.ya = s.y;
            if (dragging && s == dragged) s.y = dragStartY + mouseY - mouseClickedY;
            else s.y = approachExp(s.y, slotY(sortKeys.indexOf(s.key)), 0.4, 20);
        }
        if (dragging) {
            final int slot = (int) Math.max(0, Math.min(sortSlots.size() - 1, (dragged.y - (2 - 12)) / ROW));
            final int from = sortKeys.indexOf(dragged.key);
            if (slot != from) {
                sortKeys = new ArrayList<>(SortOrder.move(sortKeys, from, slot));
                NEIConfig.ITEM_SORT.set(SortOrder.save(sortKeys));
                NEIConfig.changed();
                ItemList.resort();
            }
        }
    }

    // ---- input ----

    private Screen root() {
        Screen p = parent;
        while (p instanceof SettingsScreen s) p = s.parent;
        return p;
    }

    private void back() {
        Minecraft.getInstance().gui.setScreen(parent);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(root());
    }

    @Override
    public void removed() {
        blur();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        final double mx = event.x();
        final double my = event.y();
        final int button = event.button();
        if (picker.isOpen()) {
            picker.click(mx, my, button);
            return true;
        }

        if (Draw.in(mx, my, backX(), backY(), backW(), 20)) { // GuiCCButton: acts when pressed
            click();
            back();
            return true;
        }

        final int barH = barH();
        if (button == 0 && barH < areaH() && mx >= barX() && mx <= barX() + 6 && my >= AREA_Y && my <= AREA_Y + areaH()) {
            final int empty = areaH() - barH;
            if (my < barY()) {
                percent = (float) ((my - AREA_Y) / empty);
                calcPercent();
            } else if (my > barY() + barH) {
                percent = (float) ((my - AREA_Y - barH + 1) / empty);
                calcPercent();
            } else {
                scrollClickY = (int) my;
                scrollPercent = percent;
                scrollMouseY = (int) my;
            }
            return true;
        }

        boolean onBox = false;
        if (inWindow(mx, my)) {
            final int slot = slotAt(my);
            if (sorter) {
                if (button == 0 && !dragging) {
                    final SortSlot s = sortAt(mx, my);
                    if (s != null) {
                        dragging = true;
                        dragged = s;
                        mouseClickedY = (int) my;
                        dragStartY = s.y;
                    }
                }
            } else if (slot >= 0) {
                pressSlot = slot;
                pressButton = button;
                final NEIConfig.Option o = entries.get(slot).option();
                if (o != null && field(o)) {
                    final Font font = Minecraft.getInstance().font;
                    final int w = boxW(font, o);
                    if (Draw.in(mx, my, rowX() + rowW() - w, rowTop(slot), w, 20)) {
                        onBox = true;
                        if (focused != o) blur();
                        focused = o;
                        final EditBox b = box(o);
                        b.setFocused(true);
                        b.mouseClicked(event, doubleClick);
                    }
                }
            }
        }
        if (!onBox) blur();
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        final double mx = event.x();
        final double my = event.y();
        final int button = event.button();
        picker.released();
        if (picker.isOpen()) return true;

        if (scrollClickY >= 0 && button == 0) {
            scrollClickY = -1;
        } else if (!sorter && inWindow(mx, my)) {
            final int slot = slotAt(my);
            if (slot >= 0 && slot == pressSlot && button == pressButton) activate(slot, mx, my);
        }
        if (button == 0) dragging = false;
        pressSlot = -1;
        pressButton = -1;
        return true;
    }

    /** A click that went down and up on the same row (GuiScrollSlot.slotClicked, then OptionButton.mouseClicked). */
    private void activate(int slot, double mx, double my) {
        final Entry e = entries.get(slot);
        final Font font = Minecraft.getInstance().font;
        final int top = rowTop(slot);
        if (e.option() == null) {
            if (Draw.in(mx, my, rowX(), top, rowW(), 20)) {
                click();
                final String key = e.section();
                if ("world.highlight_tips".equals(key)) {
                    Minecraft.getInstance().gui.setScreen(new HighlightTipsScreen(this)); // NEI's GuiHighlightTips
                } else if ("world.panels".equals(key)) {
                    Minecraft.getInstance().gui.setScreen(new PanelSettingsScreen(this)); // NEI's GuiPanelSettings
                } else if ("world.presets".equals(key)) {
                    final Screen container = root(); // the presets window opens over the inventory
                    Minecraft.getInstance().gui.setScreen(container);
                    NEIClientEvents.openPresets(container);
                } else {
                    Minecraft.getInstance().gui.setScreen(new SettingsScreen(this, key));
                }
            }
            return;
        }
        final NEIConfig.Option o = e.option();
        if (stub(o) || disabled(o)) return; // a greyed out row
        if (field(o)) return; // the text field took the press
        if (o.kind() == NEIConfig.Kind.SET) {
            clickSet(o, top, mx, my);
            return;
        }
        final int bw = buttonW(font, o);
        if (o.kind() == NEIConfig.Kind.ACTION && NEIConfig.ACTION_MODES.containsKey(o)
                && Draw.in(mx, my, rowX() + rowW() - bw - 66, top, 60, 20)) {
            final Cfg.IntValue mode = NEIConfig.ACTION_MODES.get(o);
            mode.set((mode.getAsInt() + 1) % NEIConfig.ACTION_MODE_NAMES.get(o).length);
            NEIConfig.changed();
            click();
            return;
        }
        if (!Draw.in(mx, my, rowX() + rowW() - bw, top, bw, 20)) return;
        switch (o.kind()) {
            case BOOL -> set(o, !this.<Boolean>get(o));
            case MODE -> NEIConfig.setCheatMode(NEIConfig.cheatMode() + 1);
            case CHOICE -> {
                set(o, (this.<Integer>get(o) + 1) % NEIConfig.CHOICES.get(o.value()).length);
                ItemList.refilter(); // the search modes change what the current search finds
            }
            case COLOR -> picker.open(NEIConfig.SECTIONS.getOrDefault(o.section(), "NEI Options") + ": " + o.label(),
                    parse(o), hex -> set(o, hex));
            case ACTION -> NEIConfig.ACTIONS.getOrDefault(o, () -> {}).run();
            default -> { }
        }
        click();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (scrollClickY >= 0) {
            final int diff = (int) event.y() - scrollClickY;
            final int empty = areaH() - barH();
            final int up = (int) (empty * scrollPercent + 0.5);
            final int down = empty - up;
            if (-diff > up) scrollMouseY = scrollClickY - up;
            else if (diff > down) scrollMouseY = scrollClickY + down;
            else scrollMouseY = (int) event.y();
            calcPercent();
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (picker.isOpen() || scrollY == 0) return true;
        scroll(scrollY > 0 ? -1 : 1); // the wheel scrolls whatever the mouse is over, as in NEI
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        final int key = event.key();
        if (picker.isOpen()) {
            if (key == GLFW_KEY_ESCAPE) picker.close();
            else if (picker.typingBox() != null) picker.typingBox().keyPressed(event);
            return true;
        }
        if (key == GLFW_KEY_ESCAPE) { // GuiOptionList closes every options screen, GuiOptionPane (the sorter) goes up one
            if (sorter) back();
            else onClose();
            return true;
        }
        if (sorter && key == GLFW_KEY_BACKSPACE) {
            back();
            return true;
        }
        if (focused != null) {
            boxes.get(focused).keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (picker.isOpen()) {
            if (picker.typingBox() != null) picker.typingBox().charTyped(event);
            return true;
        }
        if (focused != null) {
            boxes.get(focused).charTyped(event);
            return true;
        }
        return super.charTyped(event);
    }
}
