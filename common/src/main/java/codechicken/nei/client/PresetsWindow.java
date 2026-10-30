package codechicken.nei.client;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import codechicken.nei.Collapsible;
import codechicken.nei.ItemList;
import codechicken.nei.NEIConfig;
import codechicken.nei.Presets;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.item.ItemEntry;
import codechicken.nei.item.Preset;
import codechicken.nei.search.SearchParser;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * NEI's presets editor ({@code GuiPresetList} and {@code GuiPresetSettings}) as a window over the inventory.
 *
 * List: every preset with an enabled tick, its mode letter and name, and a delete cross; click a name to edit it,
 * Shift + click moves it up. Editor: name, mode, enabled, the items of the preset (click to remove) beside a searchable
 * list of all items (left click adds, right click removes, Ctrl = the whole mod, Shift = everything shown), and the "F"
 * box that hides items other presets already use.
 *
 * Differences from NEI: no drag selection or drag sorting (Shift and Ctrl clicks do the bulk work), items are registry
 * ids so Shift does not mean "the same item" as it does in NEI.
 */
final class PresetsWindow implements ModalWindow {

    private static final int ROW = 24;
    private static final int BTN = 20;

    private final Screen screen;
    private boolean list = true;
    private boolean close;
    private int scroll;
    private int sw;
    private int sh;

    // editor
    private Preset edit;
    private int editIndex;
    private final EditBox name;
    private final EditBox search;
    private boolean hideUsed = true;
    private final SearchParser parser = new SearchParser();
    private List<ItemEntry> shown = List.of();
    private String shownQuery;
    private boolean shownHideUsed;
    private int shownGeneration = -1;
    private int generation;
    private final ItemPanel mine = new ItemPanel(this::editItems, false, "No items yet");
    private final ItemPanel all = new ItemPanel(this::shownItems, false, "No items").withSelected(
            e -> edit != null && edit.items.contains(e.registryId));
    private List<ItemEntry> editEntries = List.of();
    private int editEntriesGeneration = -1;

    // geometry
    private int wx;
    private int wy;
    private int ww;
    private int wh;
    private int colLeftX;
    private int colRightX;
    private int colW;
    private int contentTop;

    PresetsWindow(Screen screen) {
        this.screen = screen;
        final Font font = Minecraft.getInstance().font;
        name = new EditBox(font, 0, 0, 100, 18, Component.empty());
        name.setMaxLength(40);
        name.setResponder(t -> {
            if (edit != null) edit.name = t;
        });
        search = new EditBox(font, 0, 0, 100, 18, Component.empty());
        search.setMaxLength(100);
        search.setHint(Component.translatable("nei.search.hint"));
        SearchHighlight.apply(search);
        search.setResponder(t -> {
            generation++;
            all.resetPage();
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

    /** NEI's PresetSearchField history: the text is remembered whenever the field gains or loses focus. */
    private static final codechicken.nei.item.TextHistory HISTORY = new codechicken.nei.item.TextHistory();

    private void focusSearch(boolean on) {
        if (search.isFocused() != on) HISTORY.add(search.getValue());
        search.setFocused(on);
    }

    /** Up / Down in the search field walk its history (gui.getprevioussearch / gui.getnextsearch). */
    @Override
    public boolean historyKey(boolean older) {
        if (list || !search.isFocused()) return false;
        final String text = search.getValue();
        (older ? HISTORY.previous(text) : HISTORY.next(text)).ifPresent(search::setValue);
        return true;
    }

    @Override
    public EditBox typingBox() {
        if (list) return null;
        return name.isFocused() ? name : search.isFocused() ? search : null;
    }

    /** Escape: leave the box, else the editor, else the window. */
    @Override
    public void escape() {
        if (typingBox() != null) {
            name.setFocused(false);
            focusSearch(false);
        } else if (!list) {
            list = true;
        } else {
            close = true;
        }
    }

    // ---- geometry ----

    private void layout(int sw, int sh) {
        this.sw = sw;
        this.sh = sh;
        ww = list ? Math.min(300, sw - 12) : Math.min(440, sw - 12);
        wx = (sw - ww) / 2;
        wy = 14;
        wh = sh - 28;
        contentTop = wy + 22;
        colW = (ww - 18) / 2;
        colLeftX = wx + 6;
        colRightX = wx + ww - 6 - colW;

        if (!list) {
            name.setX(colLeftX);
            name.setY(contentTop + 10);
            name.setWidth(colW);
            search.setX(colRightX);
            search.setY(contentTop);
            search.setWidth(colW);
            mine.layout(colLeftX, colLeftX + colW, contentTop + 104, wy + wh - 30);
            all.layout(colRightX, colRightX + colW, contentTop + 36, wy + wh - 30);
        }
    }

    boolean contains(double mx, double my) {
        return Draw.in(mx, my, wx, wy, ww, wh);
    }

    private int listTop() {
        return wy + 46;
    }

    private int listBottom() {
        return wy + wh - 30;
    }

    private int visibleRows() {
        return Math.max(1, (listBottom() - listTop()) / ROW);
    }

    // ---- list data ----

    private List<ItemEntry> editItems() {
        if (editEntriesGeneration != generation) {
            final List<ItemEntry> out = new ArrayList<>();
            for (String id : edit.items) {
                final ItemEntry e = ItemList.find(id);
                if (e != null) out.add(e);
            }
            editEntries = out;
            editEntriesGeneration = generation;
        }
        return editEntries;
    }

    /** The right grid: the search result, without items other presets or collapsible groups already take (the "F" box). */
    private List<ItemEntry> shownItems() {
        final String query = search.getValue();
        if (shownGeneration != generation || !query.equals(shownQuery) || hideUsed != shownHideUsed) {
            final ItemFilter filter = parser.parse(query);
            final Set<String> used = new HashSet<>();
            if (hideUsed) {
                for (Preset p : Presets.list()) if (!(editIndex >= 0 && Presets.list().get(editIndex) == p)) used.addAll(p.items);
            }
            final List<ItemEntry> out = new ArrayList<>();
            for (ItemEntry e : ItemList.all()) {
                if (edit.items.contains(e.registryId)) {
                    if (matches(filter, e)) out.add(e);
                    continue;
                }
                if (hideUsed && (used.contains(e.registryId) || Collapsible.groupIndex(e) >= 0)) continue;
                if (matches(filter, e)) out.add(e);
            }
            shown = out;
            shownQuery = query;
            shownHideUsed = hideUsed;
            shownGeneration = generation;
        }
        return shown;
    }

    private static boolean matches(ItemFilter filter, ItemEntry e) {
        try {
            return filter.matches(e);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void openEditor(int index) {
        editIndex = index;
        edit = index < 0 ? new Preset() : Presets.list().get(index).copy();
        name.setValue(edit.name);
        search.setValue("");
        name.setFocused(false);
        focusSearch(false);
        hideUsed = true;
        generation++;
        mine.resetPage();
        all.resetPage();
        list = false;
    }

    private boolean canSave() {
        return !edit.name.isEmpty() && !edit.items.isEmpty();
    }

    private static String modeKey(Preset.Mode m) {
        return switch (m) {
            case HIDE -> "Hide";
            case SUBSET -> "Subset";
            case REMOVE -> "Remove";
            case GROUP -> "Group";
        };
    }

    private static String modeTip(Preset.Mode m) {
        return switch (m) {
            case HIDE -> "Hide items in panel";
            case SUBSET -> "Create custom subsets";
            case REMOVE -> "Hide items in panel and their recipes";
            case GROUP -> "Create custom collapsible items group";
        };
    }

    private static Component modeChar(Preset.Mode m) {
        return switch (m) {
            case HIDE -> Component.literal("H").withStyle(ChatFormatting.GOLD);
            case SUBSET -> Component.literal("S").withStyle(ChatFormatting.DARK_PURPLE);
            case REMOVE -> Component.literal("R").withStyle(ChatFormatting.RED);
            case GROUP -> Component.literal("G").withStyle(ChatFormatting.AQUA);
        };
    }

    // ---- drawing ----

    private void checkbox(GuiGraphics g, Font font, int x, int y, boolean checked, String label, boolean over) {
        Draw.slot(g, x, y, 10);
        if (checked) Gfx.fill(g, x + 2, y + 2, x + 8, y + 8, 0xFF2A7A2A);
        Gfx.text(g, font, Component.literal(label), x + 14, y + 1, over ? 0xFF000000 : 0xFF404040, false);
    }

    private void button(GuiGraphics g, Font font, int x, int y, int w, String label, boolean over, boolean enabled) {
        Draw.button(g, x, y, w, BTN, over && enabled);
        Gfx.text(g, font, Component.literal(label), x + (w - font.width(label)) / 2, y + 6, enabled ? 0xFFFFFFFF : 0xFF777777, true);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, int sw, int sh) {
        layout(sw, sh);
        final Font font = Minecraft.getInstance().font;
        Gfx.fill(g, 0, 0, sw, sh, 0x90000000);
        Draw.panel(g, wx, wy, ww, wh);

        final String title = list ? "Presets List" : editIndex < 0 ? "Create Preset" : "Update Preset";
        Gfx.text(g, font, Component.literal(title), wx + (ww - font.width(title)) / 2, wy + 7, 0xFF404040, false);

        if (list) renderList(g, font, mx, my);
        else renderEditor(g, font, mx, my);
    }

    private void renderList(GuiGraphics g, Font font, int mx, int my) {
        final List<Preset> presets = Presets.list();
        final int by = wy + 20;
        button(g, font, wx + 6, by, 100, "New Preset", Draw.in(mx, my, wx + 6, by, 100, BTN), true);
        button(g, font, wx + 110, by, 100, "Toggle Presets", Draw.in(mx, my, wx + 110, by, 100, BTN), true);

        final int rows = visibleRows();
        scroll = Math.max(0, Math.min(scroll, Math.max(0, presets.size() - rows)));
        if (presets.isEmpty()) Gfx.text(g, font, Component.literal("No presets yet"), wx + 8, listTop() + 4, 0xFF606060, false);

        for (int i = 0; i < rows && scroll + i < presets.size(); i++) {
            final Preset p = presets.get(scroll + i);
            final int y = listTop() + i * ROW;
            final boolean overOn = Draw.in(mx, my, wx + 6, y, BTN, BTN);
            final boolean overName = Draw.in(mx, my, wx + 32, y, ww - 64, BTN);
            final boolean overDel = Draw.in(mx, my, wx + ww - 6 - BTN, y, BTN, BTN);

            Draw.button(g, wx + 6, y, BTN, BTN, overOn || p.enabled);
            Gfx.text(g, font, Component.literal("✔"), wx + 13, y + 6, p.enabled ? 0xFFFFFFA0 : 0xFF707070, false);
            Draw.button(g, wx + 32, y, ww - 64, BTN, overName);
            Gfx.text(g, font, Component.empty().append(modeChar(p.mode)).append(Component.literal(" " + p.name)), wx + 38, y + 6,
                    overName ? 0xFFFFFFA0 : 0xFFE0E0E0, false);
            Draw.button(g, wx + ww - 6 - BTN, y, BTN, BTN, overDel);
            Gfx.text(g, font, Component.literal("✕"), wx + ww - 6 - BTN + 7, y + 6, overDel ? 0xFFFFFFA0 : 0xFFE0E0E0, false);

            if (overOn) ItemTooltip.requestPlain(List.of(Component.literal("Enabled")));
            if (overName) ItemTooltip.requestPlain(List.of(Component.literal(modeKey(p.mode) + ": " + p.items.size() + " items"),
                    Component.literal("Shift + click: move up")));
        }

        final int cy = wy + wh - 26;
        button(g, font, wx + (ww - 100) / 2, cy, 100, "Close", Draw.in(mx, my, wx + (ww - 100) / 2, cy, 100, BTN), true);
    }

    private void renderEditor(GuiGraphics g, Font font, int mx, int my) {
        Gfx.text(g, font, Component.literal("Preset Name"), colLeftX, contentTop, 0xFF404040, false);
        name.render(g, mx, my, 0f);

        Gfx.text(g, font, Component.literal("Mode"), colLeftX, contentTop + 34, 0xFF404040, false);
        final Preset.Mode[] modes = { Preset.Mode.HIDE, Preset.Mode.SUBSET, Preset.Mode.REMOVE, Preset.Mode.GROUP };
        for (int i = 0; i < 4; i++) {
            final int x = colLeftX + (i % 2) * (colW / 2);
            final int y = contentTop + 46 + (i / 2) * 14;
            final boolean over = Draw.in(mx, my, x, y, colW / 2, 12);
            checkbox(g, font, x, y, edit.mode == modes[i], modeKey(modes[i]), over);
            if (over) ItemTooltip.requestPlain(List.of(Component.literal(modeTip(modes[i]))));
        }
        checkbox(g, font, colLeftX, contentTop + 76, edit.enabled, "Enabled", Draw.in(mx, my, colLeftX, contentTop + 76, colW, 12));
        Gfx.text(g, font, Component.literal("Items (" + edit.items.size() + ")"), colLeftX, contentTop + 92, 0xFF404040, false);

        mine.render(g, mx, my, sw, sh);

        search.render(g, mx, my, 0f);
        final boolean overF = Draw.in(mx, my, colRightX, contentTop + 22, 40, 12);
        checkbox(g, font, colRightX, contentTop + 22, hideUsed, "F", overF);
        if (overF) ItemTooltip.requestPlain(List.of(Component.literal("Hide items from another active presets")));
        all.render(g, mx, my, sw, sh);

        final int by = wy + wh - 26;
        final int bx = wx + ww / 2;
        button(g, font, bx - 100, by, 98, "Back", Draw.in(mx, my, bx - 100, by, 98, BTN), true);
        button(g, font, bx + 2, by, 98, "Save", Draw.in(mx, my, bx + 2, by, 98, BTN), canSave());
    }

    // ---- input ----

    @Override
    public void click(double mx, double my, int button, boolean ctrl, boolean shift) {
        layout(sw, sh);
        if (!contains(mx, my)) {
            if (list) close = true; // a click outside closes the list; the editor keeps unsaved work
            return;
        }
        if (list) clickList(mx, my, button, shift);
        else clickEditor(mx, my, button, ctrl, shift);
    }

    private void clickList(double mx, double my, int button, boolean shift) {
        if (button != 0) return;
        final List<Preset> presets = Presets.list();
        final int by = wy + 20;
        if (Draw.in(mx, my, wx + 6, by, 100, BTN)) {
            openEditor(-1);
            return;
        }
        if (Draw.in(mx, my, wx + 110, by, 100, BTN)) {
            final boolean enable = presets.stream().noneMatch(p -> p.enabled);
            for (Preset p : presets) p.enabled = enable;
            Presets.save();
            return;
        }
        if (Draw.in(mx, my, wx + (ww - 100) / 2, wy + wh - 26, 100, BTN)) {
            close = true;
            return;
        }
        for (int i = 0; i < visibleRows() && scroll + i < presets.size(); i++) {
            final int index = scroll + i;
            final int y = listTop() + i * ROW;
            if (Draw.in(mx, my, wx + 6, y, BTN, BTN)) {
                presets.get(index).enabled = !presets.get(index).enabled;
                Presets.save();
            } else if (Draw.in(mx, my, wx + 32, y, ww - 64, BTN)) {
                if (shift && index > 0) {
                    presets.add(index - 1, presets.remove(index));
                    Presets.save();
                } else {
                    openEditor(index);
                }
            } else if (Draw.in(mx, my, wx + ww - 6 - BTN, y, BTN, BTN)) {
                presets.remove(index);
                Presets.save();
            }
            return;
        }
    }

    private void clickEditor(double mx, double my, int button, boolean ctrl, boolean shift) {
        // text boxes
        final boolean overName = Draw.in(mx, my, name.getX(), name.getY(), name.getWidth(), name.getHeight());
        final boolean overSearch = Draw.in(mx, my, search.getX(), search.getY(), search.getWidth(), search.getHeight());
        name.setFocused(overName);
        focusSearch(overSearch);
        if (overSearch && button == 1) search.setValue(""); // right click clears, as in the search field
        if (overName || overSearch) return;

        final int by = wy + wh - 26;
        final int bx = wx + ww / 2;
        if (Draw.in(mx, my, bx - 100, by, 98, BTN)) {
            list = true;
            return;
        }
        if (Draw.in(mx, my, bx + 2, by, 98, BTN)) {
            if (canSave()) {
                if (editIndex < 0) Presets.list().add(edit);
                else Presets.list().set(editIndex, edit);
                Presets.save();
                list = true;
            }
            return;
        }

        if (button == 0) {
            final Preset.Mode[] modes = { Preset.Mode.HIDE, Preset.Mode.SUBSET, Preset.Mode.REMOVE, Preset.Mode.GROUP };
            for (int i = 0; i < 4; i++) {
                final int x = colLeftX + (i % 2) * (colW / 2);
                final int y = contentTop + 46 + (i / 2) * 14;
                if (Draw.in(mx, my, x, y, colW / 2, 12)) edit.mode = modes[i];
            }
            if (Draw.in(mx, my, colLeftX, contentTop + 76, colW, 12)) edit.enabled = !edit.enabled;
            if (Draw.in(mx, my, colRightX, contentTop + 22, 40, 12)) {
                hideUsed = !hideUsed;
                all.resetPage();
            }
        }

        if (mine.contains(mx, my)) {
            if (mine.mouseClicked(mx, my, button)) return;
            final ItemEntry e = mine.entryAt(mx, my);
            if (e != null) {
                edit.items.remove(e.registryId);
                generation++;
            }
            return;
        }

        if (all.contains(mx, my)) {
            if (all.mouseClicked(mx, my, button)) return;
            final ItemEntry e = all.entryAt(mx, my);
            if (e == null || (button != 0 && button != 1)) return;
            final boolean add = button == 0;
            final List<ItemEntry> targets = new ArrayList<>();
            if (shift) {
                targets.addAll(shownItems()); // everything the search shows
            } else if (ctrl) {
                for (ItemEntry other : ItemList.all()) if (other.modId.equals(e.modId)) targets.add(other); // the whole mod
            } else {
                targets.add(e);
            }
            for (ItemEntry t : targets) {
                if (add) edit.items.add(t.registryId);
                else edit.items.remove(t.registryId);
            }
            generation++;
        }
    }

    @Override
    public void scrolled(double mx, double my, double dy) {
        layout(sw, sh);
        if (list) {
            scroll = Math.max(0, scroll + (dy > 0 ? -1 : 1));
        } else if (mine.contains(mx, my)) {
            mine.changePage(dy > 0 ? -1 : 1);
        } else if (all.contains(mx, my)) {
            all.changePage(dy > 0 ? -1 : 1);
        }
    }
}
