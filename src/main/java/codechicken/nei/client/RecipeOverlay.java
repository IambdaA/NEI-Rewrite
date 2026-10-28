package codechicken.nei.client;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import com.mojang.blaze3d.platform.InputConstants;

import codechicken.nei.Bookmarks;
import codechicken.nei.NEIConfig;
import codechicken.nei.api.API;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.item.ItemEntry;
import codechicken.nei.item.TextHistory;
import codechicken.nei.search.SearchParser;
import codechicken.nei.recipe.ClientRecipes;
import codechicken.nei.recipe.RecipeIndex;
import codechicken.nei.recipe.RecipeQuery;
import codechicken.nei.recipe.RecipeQuery.Where;
import codechicken.nei.recipe.RecipeView;
import codechicken.nei.recipe.RecipeView.SlotView;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * NEI's recipe window, drawn on top of the container screen so the side panels stay visible and usable.
 *
 * Layout as in NEI: recipe types are tabs above the window, the stations that can craft the type sit in a column on its
 * left, then a title row, a {@code < Page n/m >} row and as many recipes as fit (window up to 370 high). Click an item to look it up (left: recipes, right: uses); the heart
 * bookmarks the recipe's result.
 *
 * This class owns no input handling of its own: {@link NEIClientEvents} forwards clicks, scrolls and keys to it.
 */
public final class RecipeOverlay {

    private static final int W = 176;
    private static final int TAB = 24; // NEI's JEI style tab: 24 x 24, 7 per row, overlapping the window by 3
    private static final int TABS = (W - 4) / TAB;
    private static final int HEAD = 44;
    private static final int MAX_HEIGHT = 370; // NEI: inventory.guirecipe.maxHeight
    private static final int CELL_H = 62;
    private static final int GRID_X = 14;
    /** NEI's {@code GuiRecipeButton.BUTTON_WIDTH / BUTTON_HEIGHT}. */
    private static final int BTN = 12;
    private static final int NAV_W = 13; // NEI: the < > buttons of the title and page rows are 13 x 12
    private static final int NAV_H = 12;
    private static final int SEARCH_W = W - (5 + NAV_W) * 2 + 1 - 12 - 45; // NEI: the field between the magnifier and the page count


    private record Group(String title, ItemStack icon, List<RecipeView> views, List<ItemStack> stations) {}

    private record Nav(Item item, boolean uses) {}

    private record Hit(int x, int y, int w, int h, ItemStack stack) {

        boolean at(double mx, double my) {
            return Draw.in(mx, my, x, y, w, h);
        }
    }

    private record Heart(int x, int y, RecipeView view) {}

    private record Plus(int x, int y, RecipeView view) {}

    private final Screen screen;
    private final Deque<Nav> history = new ArrayDeque<>();
    private Item item;
    private boolean uses;
    private List<Group> groups = List.of();
    private int tab;
    private int tabOffset;
    private int page;

    // NEI's recipe search: the magnifier button opens a field that keeps only the recipes with a matching item
    private final EditBox search;
    private boolean searchOn;
    private String query = "";
    private static final TextHistory RECIPE_HISTORY = new TextHistory(); // shared by every recipe window, like NEI's
    private final SearchParser parser = new SearchParser();
    private final Map<Item, ItemEntry> entries = new HashMap<>();
    private Group filteredGroup;
    private String filteredQuery;
    private List<RecipeView> filtered = List.of();

    // geometry and hit areas, refreshed every frame
    private int left;
    private int top;
    private int height;
    private int boxLeft;
    private final List<Hit> slotHits = new ArrayList<>();
    private final List<Heart> heartHits = new ArrayList<>();
    private final List<Plus> plusHits = new ArrayList<>();

    // result of the last '+' transfer: which ingredients were missing, and a message, both shown for a few seconds
    private RecipeView missingView;
    private Set<Integer> missing = Set.of();
    private String status = "";
    private int statusColor;
    private long statusUntil;
    private ItemStack hovered = ItemStack.EMPTY;
    private RecipeView hoveredView;
    private RecipeView hoveredHeart;
    private boolean hoveredPick;
    /** The crafting recipe whose "+" the mouse is on: its ingredients show as ghost items in the grid. */
    private RecipeView ghostView;

    /** Set while the window was opened from the crafting tree: the "+" then picks the recipe for the tree. */
    private Consumer<RecipeView> picker;
    private boolean closeRequested;

    public RecipeOverlay(Screen screen, Item item, boolean uses) {
        this.screen = screen;
        search = new EditBox(Minecraft.getInstance().font, 0, 0, SEARCH_W, 14, Component.empty());
        search.setMaxLength(100);
        search.setResponder(text -> {
            query = text;
            page = 0;
        });
        layout(screen.width, screen.height);
        show(item, uses, false);
    }

    public Screen screen() {
        return screen;
    }

    /** The item under the mouse as of the last frame (slot or tab icon), or empty. */
    public ItemStack hovered() {
        return hovered;
    }

    /** The recipe under the mouse as of the last frame, or null (NEI's "focused recipe"). */
    public RecipeView hoveredRecipe() {
        return hoveredView;
    }

    /** The recipe whose favorite (heart) button is under the mouse, or null. */
    public RecipeView hoveredHeart() {
        return hoveredHeart;
    }

    /**
     * Opened from the crafting tree (NEI replaces the "+" buttons with {@code TreeOverlayButton}): the "+" next to a
     * recipe hands it to {@code picker} and closes the window. Null for the normal window.
     */
    void setPicker(Consumer<RecipeView> picker) {
        this.picker = picker;
    }

    /** True once a recipe was picked for the tree: the caller closes the window. */
    boolean wantsClose() {
        return closeRequested;
    }

    public void show(Item item, boolean uses, boolean pushHistory) {
        if (pushHistory && this.item != null) history.push(new Nav(this.item, this.uses));
        this.item = item;
        this.uses = uses;

        final List<RecipeView> views = uses ? RecipeIndex.usesOf(item) : RecipeIndex.recipesFor(item);
        final Map<String, List<RecipeView>> byTitle = new LinkedHashMap<>();
        for (RecipeView v : views) byTitle.computeIfAbsent(v.title(), k -> new ArrayList<>()).add(v);

        final List<Group> list = new ArrayList<>();
        for (var e : byTitle.entrySet()) {
            final RecipeView first = e.getValue().get(0);
            ItemStack icon = first.station();
            if (icon.isEmpty()) {
                for (SlotView s : first.slots()) {
                    if (s.output()) {
                        icon = s.options().get(0);
                        break;
                    }
                }
            }
            // NEI's station column: every block that can craft this type of recipe
            final List<ItemStack> stations = new ArrayList<>();
            for (RecipeView v : e.getValue()) {
                final List<ItemStack> mine = new ArrayList<>(v.stations());
                if (!v.station().isEmpty()) mine.add(0, v.station());
                mine.addAll(codechicken.nei.api.API.catalystsFor(v.title()));
                for (ItemStack st : mine) {
                    if (stations.stream().noneMatch(s -> s.getItem() == st.getItem())) stations.add(st);
                }
            }
            list.add(new Group(e.getKey(), icon, List.copyOf(e.getValue()), List.copyOf(stations)));
        }

        this.groups = List.copyOf(list);
        searchOn = false;
        search.setValue("");
        search.setFocused(false);
        this.tab = 0;
        this.tabOffset = 0;
        this.page = 0;
    }

    private void layout(int sw, int sh) {
        // as in NEI's GuiRecipe#initGui: room for the tabs above, at most 370 high
        height = Math.max(130, Math.min(sh - 22 - 22 - TAB, MAX_HEIGHT));
        left = (sw - W) / 2;
        top = Math.max(22 - 3 + TAB, (sh - height) / 2);
        boxLeft = left;
    }

    /** Height of one recipe cell: NEI's 62, or what the tallest self-drawn (JEI) recipe of this type needs. */
    private int cellH() {
        final Group g = group();
        if (g == null) return CELL_H;
        if (g != cellGroup) {
            cellGroup = g;
            int drawn = 0;
            for (RecipeView v : g.views()) if (v.drawn() != null) drawn = Math.max(drawn, v.drawn().height());
            cellHeight = drawn == 0 ? CELL_H : drawn + 8;
        }
        return cellHeight;
    }

    private Group cellGroup;
    private int cellHeight = CELL_H;

    private int perPage() {
        return Math.max(1, (height - HEAD - 6) / cellH());
    }

    private void selectTab(int index) {
        if (groups.isEmpty()) return;
        tab = (index + groups.size()) % groups.size();
        tabOffset = tab / TABS * TABS;
        page = 0;
    }

    private Group group() {
        return groups.isEmpty() ? null : groups.get(Math.max(0, Math.min(tab, groups.size() - 1)));
    }

    /** The recipes of the current type that pass the search (all of them while the search is closed or empty). */
    private List<RecipeView> views() {
        final Group g = group();
        if (g == null) return List.of();
        if (!searchOn || query.isBlank()) return g.views();
        if (g != filteredGroup || !query.equals(filteredQuery)) {
            filteredGroup = g;
            filteredQuery = query;
            filtered = filter(g.views(), query);
        }
        return filtered;
    }

    /** NEI's recipe filter: {@code <} ingredients, {@code >} results, {@code ^} stations, {@code &} after it = all must match; see {@link RecipeQuery}. */
    private List<RecipeView> filter(List<RecipeView> views, String q) {
        final List<RecipeQuery.Clause> clauses = RecipeQuery.parse(q);
        final List<ItemFilter> filters = new ArrayList<>();
        for (RecipeQuery.Clause c : clauses) filters.add(parser.parse(c.text()));
        final List<RecipeView> out = new ArrayList<>();
        for (RecipeView v : views) {
            boolean ok = true;
            for (int i = 0; ok && i < clauses.size(); i++) ok = matches(v, clauses.get(i), filters.get(i));
            if (ok) out.add(v);
        }
        return out;
    }

    private boolean matches(RecipeView view, RecipeQuery.Clause clause, ItemFilter f) {
        final List<List<ItemStack>> stacks = new ArrayList<>();
        final Where where = clause.where();
        for (SlotView s : view.slots()) {
            final boolean wanted = switch (where) {
                case ANY -> true;
                case INPUT -> !s.output();
                case OUTPUT -> s.output();
                case OTHER -> false;
            };
            if (wanted) stacks.add(s.options());
        }
        if (where == Where.ANY || where == Where.OTHER) { // the stations, as in the station column
            final List<ItemStack> st = new ArrayList<>(view.stations());
            if (!view.station().isEmpty() && st.isEmpty()) st.add(view.station());
            for (ItemStack s : st) stacks.add(List.of(s));
        }
        return RecipeQuery.test(clause.all(), stacks, option -> {
            final ItemEntry entry = entries.computeIfAbsent(option.getItem(), i -> new ItemEntry(option));
            try {
                return !API.hides(entry) && f.matches(entry);
            } catch (RuntimeException e) {
                return false; // a filter that cannot judge this item does not match it
            }
        });
    }

    /** One tooltip line: the marker in gold, what it does in gray. */
    private static Component syntax(String marker, String text) {
        return Component.literal(marker).withStyle(ChatFormatting.GOLD)
                .append(Component.literal("  " + text).withStyle(ChatFormatting.GRAY));
    }

    public boolean searchFocused() {
        return searchOn && search.isFocused();
    }

    public EditBox searchBox() {
        return search;
    }

    public void blurSearch() {
        leaveSearch();
    }

    /** As in NEI's RecipeSearchField: the text goes into the history when the field loses focus. */
    private void leaveSearch() {
        if (search.isFocused()) RECIPE_HISTORY.add(search.getValue());
        search.setFocused(false);
    }

    /** Up / Down in the field walk the recipe search history (NEI's gui.prev_search / next_search). */
    public void walkHistory(boolean older) {
        final String text = search.getValue();
        (older ? RECIPE_HISTORY.previous(text) : RECIPE_HISTORY.next(text)).ifPresent(search::setValue);
    }

    private int pageCount() {
        return Math.max(1, (views().size() + perPage() - 1) / perPage());
    }

    private void changePage(int delta) {
        page = Math.max(0, Math.min(page + delta, pageCount() - 1));
    }

    /** True if the point is on the window or its tabs. */
    public boolean contains(double mx, double my) {
        return Draw.in(mx, my, boxLeft - 4, top - TAB, left + W + 8 - boxLeft + 4, height + TAB);
    }

    private static ItemStack current(SlotView slot) {
        final List<ItemStack> options = slot.options();
        return options.get((int) ((System.currentTimeMillis() / 1000) % options.size()));
    }

    public void render(GuiGraphicsExtractor g, int mx, int my, int sw, int sh) {
        layout(sw, sh);
        final Font font = Minecraft.getInstance().font;

        slotHits.clear();
        heartHits.clear();
        plusHits.clear();
        hovered = ItemStack.EMPTY;
        hoveredView = null;
        hoveredHeart = null;
        hoveredPick = false;
        ghostView = null;
        page = Math.max(0, Math.min(page, pageCount() - 1));

        Tex.nine(g, Tex.RECIPE_BG, 256, 256, left - 4, top - 4, W + 8, height + 8, 0, 0, 184, 174, 9, 9, 9, 9);

        // tabs (NEI's JEI style): drawn after the window so the selected one joins it
        final int ty = top - TAB + 3;
        for (int i = tabOffset; i < Math.min(groups.size(), tabOffset + TABS); i++) {
            final int tx = left + 4 + (i - tabOffset) * TAB;
            Tex.blit(g, Tex.SPRITES, 256, 256, tx, ty, i == tab ? 0 : TAB, 16, TAB, TAB);
            final ItemStack icon = groups.get(i).icon();
            if (!icon.isEmpty()) Draw.stack(g, icon, tx + 4, ty + 4);
            if (Draw.in(mx, my, tx, ty, TAB, TAB)) {
                // the owning mod's display name, as item tooltips show it (NTM, not the namespace "hbm")
                final String mod = icon.isEmpty() ? "Minecraft" : ItemTooltip.modName(icon);
                ItemTooltip.requestPlain(List.of(Component.literal(groups.get(i).title()),
                        Component.literal(mod).withStyle(ChatFormatting.BLUE)));
            }
        }
        if (groups.size() > TABS) {
            Draw.button(g, left - 3, ty + 2, 8, TAB - 4, Draw.in(mx, my, left - 3, ty + 2, 8, TAB - 4));
            Draw.button(g, left + 4 + TABS * TAB - 1, ty + 2, 8, TAB - 4,
                    Draw.in(mx, my, left + 4 + TABS * TAB - 1, ty + 2, 8, TAB - 4));
            g.text(font, Component.literal("<"), left - 1, ty + 8, 0xFFE0E0E0, false);
            g.text(font, Component.literal(">"), left + 4 + TABS * TAB + 1, ty + 8, 0xFFE0E0E0, false);
        }

        // title row and page row (NEI: darkened bars with a < > button at each end)
        final Group group = group();
        final String title = group == null ? (uses ? "Uses" : "Recipes") : group.title();
        final int rightX = left + W - 5 - NAV_W + 1;
        for (int row = 0; row < 2; row++) {
            final int by = top + 3 + row * 14;
            final int barLeft = left + 5 + NAV_W + (row == 1 ? 12 : 0);
            g.fill(barLeft, by, rightX - 1, by + NAV_H, 0x30000000);
            if (row == 1 && searchOn) {
                final String count = (page + 1) + "/" + pageCount();
                g.text(font, Component.literal(count), left + 30 + SEARCH_W + 22 - font.width(count) / 2, by + 2,
                        0xFFFFFFFF, true);
            } else {
                final String text = row == 0 ? title : "Page " + (page + 1) + "/" + pageCount();
                g.text(font, Component.literal(text), left + (W - font.width(text)) / 2, by + 2, 0xFFFFFFFF, true);
            }
            if (row == 1 || groups.size() > 1) {
                Draw.button(g, left + 5, by, NAV_W, NAV_H, Draw.in(mx, my, left + 5, by, NAV_W, NAV_H));
                Draw.button(g, rightX, by, NAV_W, NAV_H, Draw.in(mx, my, rightX, by, NAV_W, NAV_H));
                g.text(font, Component.literal("<"), left + 9, by + 2, 0xFFE0E0E0, false);
                g.text(font, Component.literal(">"), rightX + 4, by + 2, 0xFFE0E0E0, false);
            }
        }

        // the magnifier opens / closes the recipe search
        final boolean overSearch = Draw.in(mx, my, left + 5 + NAV_W, top + 17, 12, 12);
        Draw.button(g, left + 5 + NAV_W, top + 17, 12, 12, overSearch || searchOn);
        Tex.blit(g, Tex.ICONS, 256, 256, left + 5 + NAV_W + 1, top + 18, 0, 76, 10, 10);
        if (searchOn) {
            search.setX(left + 30);
            search.setY(top + 16);
            search.setWidth(SEARCH_W);
            // NEI's colours: red when the text matches no recipe of this type
            final boolean none = !query.isBlank() && views().isEmpty();
            search.setTextColor(none ? (search.isFocused() ? 0xFFCC3300 : 0xFF993300)
                    : (search.isFocused() ? 0xFFE0E0E0 : 0xFF909090));
            search.extractRenderState(g, mx, my, 0f);
        }
        if (overSearch) {
            final Component filterTitle = Component.literal("Recipe Filter").withStyle(ChatFormatting.WHITE);
            // As elsewhere in NEI: the hotkeys show while Alt is held
            ItemTooltip.requestPlain(HotkeyHelp.isAltDown()
                    ? List.of(filterTitle, syntax("Up / Down", "search history"), syntax("RMB", "clear the field"),
                            syntax("Esc", "leave the field"))
                    : List.of(filterTitle, syntax("<", "ingredients"), syntax(">", "results"), syntax("^", "stations"),
                            syntax("&", "after a marker: all must match"), ItemTooltip.hintLine()));
        }

        if (group != null) drawStations(g, group.stations(), mx, my);

        // recipes
        if (group == null) {
            final String msg = ClientRecipes.all().isEmpty()
                    ? "No recipe data from the server (NEI must be installed on it)"
                    : "No recipes found";
            g.text(font, Component.literal(msg), left + 8, top + HEAD + 6, 0xFF702020, false);
        } else {
            final List<RecipeView> shown = views();
            final int start = page * perPage();
            for (int r = 0; r < perPage() && start + r < shown.size(); r++) {
                drawRecipe(g, font, shown.get(start + r), top + HEAD + r * cellH(), mx, my, r > 0);
            }
            if (shown.isEmpty()) {
                g.text(font, Component.literal("No recipes match"), left + 8, top + HEAD + 6, 0xFF702020, false);
            }
        }

        if (System.currentTimeMillis() < statusUntil) {
            g.text(font, Component.literal(status), left + 8, top + height - 14, statusColor, false);
        }

        if (ghostView != null && codechicken.nei.NEIConfig.RECIPE_GHOST.get() && screen instanceof AbstractContainerScreen<?> container) {
            RecipeTransfer.drawGhosts(g, container, ghostView, RecipeOverlay::current);
        }

        if (!hovered.isEmpty()) ItemTooltip.request(hovered, HotkeyHelp.Where.RECIPE);
        else if (hoveredHeart != null) ItemTooltip.requestHeart();
        else if (hoveredPick) ItemTooltip.requestPlain(List.of(Component.literal("Use for recipe tree")));
    }

    /** NEI's station column ({@code GuiRecipeCatalyst}): left of the window, 16 px per item, wraps into more columns. */
    private void drawStations(GuiGraphicsExtractor g, List<ItemStack> stations, int mx, int my) {
        if (stations.isEmpty()) {
            boxLeft = left;
            return;
        }
        final int perCol = Math.max(1, (height - 5 - 2 - 12) / 16);
        final int cols = (stations.size() + perCol - 1) / perCol;
        final int rows = (stations.size() + cols - 1) / cols;
        final int w = cols * 16 + 7 + 3;
        final int h = rows * 16 + 14;
        final int x = left - w + 4;
        boxLeft = x;

        Tex.nine(g, Tex.CATALYST, 36, 36, x - 4, top - 4, w + 8, h + 8, 0, 0, 36, 36, 10, 10, 10, 10);
        Draw.slot(g, x + 6, top + 6, cols * 16 + 2, rows * 16 + 2);

        for (int i = 0; i < stations.size(); i++) {
            final int ix = x + 7 + cols * 16 - 16 - (i / rows) * 16;
            final int iy = top + 7 + (i % rows) * 16;
            final ItemStack stack = stations.get(i);
            Draw.stack(g, stack, ix, iy);
            final Hit hit = new Hit(ix, iy, 16, 16, stack);
            slotHits.add(hit);
            if (hit.at(mx, my)) {
                g.fill(ix, iy, ix + 16, iy + 16, 0x80FFFFFF);
                hovered = stack;
            }
        }
    }

    /** NEI's favorite icon ({@code GuiFavoriteButton.drawContent}): 9 x 10 at (10, 76) off, (19, 76) on, in a 12 x 12 button. */
    private static void heartIcon(GuiGraphicsExtractor g, int bx, int by, boolean on) {
        Tex.blit(g, Tex.ICONS, 256, 256, bx + (BTN - 9 - 1) / 2, by + (BTN - 10) / 2, on ? 19 : 10, 76, 9, 10);
    }

    /**
     * NEI's "+" icon ({@code GuiOverlayButton.drawContent}), 9 x 10 on the icon sheet: the plain "+" (46, 76); with Shift
     * held the fill arrow (28, 76), or the red fill arrow (37, 76) when the recipe does not fit. A recipe that does not
     * fit is drawn at half strength (NEI: alpha 0.5 when the button is disabled).
     */
    private static void plusIcon(GuiGraphicsExtractor g, int bx, int by, boolean fits) {
        final int u = HotkeyHelp.isShiftDown() ? (fits ? 28 : 37) : 46;
        final int ix = bx + (BTN - 9 - 1) / 2;
        final int iy = by + (BTN - 10) / 2;
        Tex.blit(g, Tex.ICONS, 256, 256, ix, iy, u, 76, 9, 10);
        if (!fits) g.fill(ix, iy, ix + 9, iy + 10, 0x80262E35);
    }

    /** A recipe that draws itself (JEI layout): centered in the window, with NEI's heart and "+" buttons at the edge. */
    private void drawSelfDrawn(GuiGraphicsExtractor g, Font font, RecipeView view, int cellTop, int mx, int my) {
        final RecipeView.Drawn d = view.drawn();
        final int x = Math.max(left + 2, left + (W - d.width()) / 2);
        d.draw(g, x, cellTop, mx, my);

        final ItemStack over = d.itemAt(mx, my);
        if (!over.isEmpty()) {
            hovered = over;
            // clicks are looked up in slotHits: one tiny area at the mouse stands for the slot the layout found
            slotHits.add(new Hit(mx, my, 1, 1, over));
        } else if (d.contains(mx, my)) {
            final List<Component> lines = d.tooltip(mx, my);
            if (!lines.isEmpty()) ItemTooltip.requestPlain(lines);
            else d.overlays(g, mx, my);
        }

        final int hx = left + W - 4 - BTN;
        final int hy = cellTop + 2;
        final boolean favorite = RecipeBookmarks.isFavorite(view);
        final boolean overHeart = Draw.in(mx, my, hx, hy, BTN, BTN);
        Draw.button(g, hx, hy, BTN, BTN, overHeart);
        heartIcon(g, hx, hy, favorite);
        heartHits.add(new Heart(hx, hy, view));
        if (overHeart) hoveredHeart = view;
        if (picker != null) {
            final int py = hy + BTN + 2;
            final boolean overPick = Draw.in(mx, my, hx, py, BTN, BTN);
            Draw.button(g, hx, py, BTN, BTN, overPick);
            plusIcon(g, hx, py, true);
            plusHits.add(new Plus(hx, py, view));
            if (overPick) hoveredPick = true;
        } else if (screen instanceof AbstractContainerScreen<?> container) {
            // JEI's recipe transfer: shown when the open screen has a handler for this recipe's category
            final RecipeView.Drawn.Transfer state = codechicken.nei.recipe.CraftingBridge.hasCustom(container.getMenu())
                    ? codechicken.nei.recipe.CraftingBridge.state(container.getMenu(), view)
                    : d.transferState(container.getMenu());
            if (state != RecipeView.Drawn.Transfer.NONE) {
                final int py = hy + BTN + 2;
                Draw.button(g, hx, py, BTN, BTN, Draw.in(mx, my, hx, py, BTN, BTN));
                plusIcon(g, hx, py, state == RecipeView.Drawn.Transfer.OK);
                plusHits.add(new Plus(hx, py, view));
            }
        }
    }

    private void drawRecipe(GuiGraphicsExtractor g, Font font, RecipeView view, int cellTop, int mx, int my,
            boolean separator) {
        if (separator) g.fill(left + 8, cellTop - 3, left + W - 8, cellTop - 2, 0xFF8B8B8B);
        if (Draw.in(mx, my, left + 8, cellTop - 3, W - 16, cellH())) hoveredView = view;

        if (view.drawn() != null) {
            drawSelfDrawn(g, font, view, cellTop, mx, my);
            return;
        }

        Item resultItem = null;

        // NEI draws the whole crafting grid, empty cells included
        if (view.crafting() != RecipeView.Crafting.NONE) {
            for (int c = 0; c < 9; c++) Draw.slot(g, left + GRID_X + c % 3 * 18, cellTop + 4 + c / 3 * 18, 18);
        }

        // NEI's item presence overlay: while the mouse is on the recipe's "+", the ingredients show whether you have them
        boolean[] present = null;
        final int presenceMode = codechicken.nei.NEIConfig.PRESENCE_OVERLAY.getAsInt();
        if (presenceMode > 0 && picker == null && screen instanceof AbstractContainerScreen<?> presenceScreen
                && view.crafting() != RecipeView.Crafting.NONE
                && Draw.in(mx, my, left + W - 8 - BTN - 4, cellTop + 4 + 18 + 2 + BTN + 2, BTN, BTN)) {
            present = RecipeTransfer.presence(presenceScreen, view);
        }

        for (int si = 0; si < view.slots().size(); si++) {
            final SlotView slot = view.slots().get(si);
            final int sx = left + GRID_X + slot.gx() * 18;
            final int sy = cellTop + 4 + slot.gy() * 18;
            final ItemStack stack = current(slot);

            if (slot.output()) {
                Draw.slot(g, sx - 4, sy - 4, 26); // the result gets the big frame, as in NEI
                resultItem = stack.getItem();
            } else {
                Draw.slot(g, sx, sy, 18);
            }

            Draw.stack(g, stack, sx + 1, sy + 1);
            if (view == missingView && missing.contains(si) && System.currentTimeMillis() < statusUntil) {
                g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x70FF0000); // ingredient the player does not have
            }
            if (present != null && !slot.output() && (!present[si] || codechicken.nei.NEIConfig.PRESENCE_HIGHLIGHT_PRESENT.get())) {
                if (presenceMode == 2) {
                    g.fill(sx + 1, sy + 1, sx + 17, sy + 17, present[si] ? 0x8000AA00 : 0x80AA0000);
                } else { // NEI's icons: a tick / a cross in the slot's bottom right corner (nei_tabbed_sprites, 8 x 8)
                    Tex.blit(g, Tex.SPRITES, 256, 256, sx + 9, sy + 9, present[si] ? 8 : 0, 40, 8, 8);
                }
            }
            if (slot.output() && stack.getCount() > 1) g.itemDecorations(font, stack, sx + 1, sy + 1);

            final Hit hit = new Hit(sx, sy, 18, 18, stack);
            slotHits.add(hit);
            if (hit.at(mx, my)) {
                g.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0x80FFFFFF);
                hovered = stack;
            }
        }

        if (view.info().isEmpty()) {
            Draw.arrow(g, left + GRID_X + 3 * 18 + 8, cellTop + 4 + 27);
        } else { // a furnace type recipe (the only ones with a time line): NEI animates the arrow and the flame
            final float cook = Draw.cookFraction();
            Draw.progressArrow(g, left + GRID_X + 3 * 18 + 8, cellTop + 4 + 27, cook);
            boolean fuel = false;
            for (SlotView s : view.slots()) fuel |= !s.output() && !s.indexed();
            if (fuel) Draw.flame(g, left + GRID_X + 18 + 1, cellTop + 4 + 20, cook);
        }

        if (!view.info().isEmpty()) {
            g.text(font, Component.literal(view.info()), left + GRID_X + 66, cellTop + 50, 0xFF404040, false);
        }

        if (resultItem != null) {
            final int hx = left + W - 8 - BTN - 4;
            final int hy = cellTop + 4 + 18 + 2;
            // NEI's favorite button: sets this recipe as the one used for its result in recipe trees
            final boolean favorite = RecipeBookmarks.isFavorite(view);
            final boolean overHeart = Draw.in(mx, my, hx, hy, BTN, BTN);
            Draw.button(g, hx, hy, BTN, BTN, overHeart);
            heartIcon(g, hx, hy, favorite);
            heartHits.add(new Heart(hx, hy, view));
            if (overHeart) hoveredHeart = view;
            if (picker != null) {
                // from the crafting tree: every recipe can be picked (NEI only where it has a "+" button)
                final int py = hy + BTN + 2;
                final boolean overPick = Draw.in(mx, my, hx, py, BTN, BTN);
                Draw.button(g, hx, py, BTN, BTN, overPick);
                plusIcon(g, hx, py, true);
                plusHits.add(new Plus(hx, py, view));
                if (overPick) hoveredPick = true;
            } else if (screen instanceof AbstractContainerScreen<?> container
                    && (view.crafting() != RecipeView.Crafting.NONE
                            || codechicken.nei.recipe.CraftingBridge.hasCustom(container.getMenu()))) {
                // shown for every crafting recipe; greyed out when it does not fit this screen's grid (e.g. a 3x3
                // recipe in the 2x2 inventory grid), and clicking it then says so
                // a modded crafting screen (AE2 terminal ...) is asked through JEI's transfer handlers
                final boolean grid = RecipeTransfer.hasGrid(container) && !codechicken.nei.recipe.CraftingBridge.hasCustom(container.getMenu());
                final boolean fits = grid ? RecipeTransfer.canTransfer(container, view)
                        : codechicken.nei.recipe.CraftingBridge.state(container.getMenu(), view) == RecipeView.Drawn.Transfer.OK;
                final int py = hy + BTN + 2;
                Draw.button(g, hx, py, BTN, BTN, Draw.in(mx, my, hx, py, BTN, BTN));
                plusIcon(g, hx, py, fits);
                plusHits.add(new Plus(hx, py, view));
                if (fits && grid && Draw.in(mx, my, hx, py, BTN, BTN)) ghostView = view;
            }
        }
    }

    /** Handles a click anywhere on the window or tabs. Always consumes it. */
    public void mouseClicked(double mx, double my, int button) {
        // recipe search: the magnifier opens it, a click on the field focuses it, a click elsewhere leaves it
        if (searchOn) {
            final boolean overField = Draw.in(mx, my, left + 30, top + 16, SEARCH_W, 14);
            if (overField) search.setFocused(true);
            else leaveSearch();
            if (overField) {
                if (button == 1) search.setValue(""); // right click clears, as in NEI
                return;
            }
        }
        if (button == 0 && Draw.in(mx, my, left + 5 + NAV_W, top + 17, 12, 12)) {
            searchOn = !searchOn;
            if (searchOn) search.setFocused(true);
            else {
                leaveSearch();
                search.setValue("");
            }
            return;
        }

        // tabs and their arrows
        final int ty = top - TAB + 3;
        if (groups.size() > TABS && Draw.in(mx, my, left - 3, ty + 2, 8, TAB - 4)) {
            selectTab((tabOffset / TABS - 1) * TABS);
            return;
        }
        if (groups.size() > TABS && Draw.in(mx, my, left + 4 + TABS * TAB - 1, ty + 2, 8, TAB - 4)) {
            selectTab((tabOffset / TABS + 1) * TABS);
            return;
        }
        if (my >= ty && my < ty + TAB && mx >= left + 4 && mx < left + 4 + TABS * TAB) {
            final int index = tabOffset + (int) ((mx - left - 4) / TAB);
            if (index < groups.size()) selectTab(index);
            return;
        }

        // title row (recipe type) and page row buttons
        final int rightX = left + W - 5 - NAV_W + 1;
        if (groups.size() > 1) {
            if (Draw.in(mx, my, left + 5, top + 3, NAV_W, NAV_H)) {
                selectTab(tab - 1);
                return;
            }
            if (Draw.in(mx, my, rightX, top + 3, NAV_W, NAV_H)) {
                selectTab(tab + 1);
                return;
            }
        }
        if (Draw.in(mx, my, left + 5, top + 17, NAV_W, NAV_H)) {
            changePage(-1);
            return;
        }
        if (Draw.in(mx, my, rightX, top + 17, NAV_W, NAV_H)) {
            changePage(1);
            return;
        }

        // plus: move the ingredients from the inventory into the crafting grid
        for (Plus p : plusHits) {
            if (picker != null && Draw.in(mx, my, p.x(), p.y(), BTN, BTN)) {
                if (button == 0) {
                    picker.accept(p.view());
                    closeRequested = true;
                }
                return;
            }
            if (Draw.in(mx, my, p.x(), p.y(), BTN, BTN) && screen instanceof AbstractContainerScreen<?> container) {
                final boolean custom = codechicken.nei.recipe.CraftingBridge.hasCustom(container.getMenu());
                if (!custom && p.view().drawn() != null) { // a JEI recipe: the transfer handler of its mod does it
                    final String error = p.view().drawn().transfer(container.getMenu(), net.minecraft.client.Minecraft.getInstance().player,
                            button == 0 && HotkeyHelp.isShiftDown());
                    if (error != null) {
                        setStatus(error, 0xFF702020);
                    } else {
                        setStatus("Ingredients placed", 0xFF206020);
                        closeRequested = true;
                    }
                    return;
                }
                if (custom || !RecipeTransfer.hasGrid(container)) { // a modded crafting screen: the mod's own or JEI's handler does the moving
                    final String error = codechicken.nei.recipe.CraftingBridge.transfer(container.getMenu(),
                            net.minecraft.client.Minecraft.getInstance().player, p.view(), button == 0 && HotkeyHelp.isShiftDown());
                    if (error != null) {
                        setStatus(error, 0xFF702020);
                    } else {
                        setStatus("Ingredients placed", 0xFF206020);
                        closeRequested = true;
                    }
                    return;
                }
                // As in NEI: Shift + click places as many sets as possible (maxTransfer)
                final RecipeTransfer.Result r = RecipeTransfer.transfer(container, p.view(),
                        button == 0 && HotkeyHelp.isShiftDown() ? Integer.MAX_VALUE : 1);
                missingView = p.view();
                missing = r.missing();
                if (r.error() != null) setStatus(r.error(), 0xFF702020);
                else if (!r.missing().isEmpty()) setStatus("Missing " + r.missing().size() + " ingredient(s)", 0xFF702020);
                else {
                    setStatus("Ingredients placed", 0xFF206020);
                    closeRequested = true; // NEI closes the recipe window once the recipe is in the grid
                }
                return;
            }
        }

        // heart: favorite recipe
        for (Heart h : heartHits) {
            if (button == 0 && Draw.in(mx, my, h.x(), h.y(), BTN, BTN)) {
                RecipeBookmarks.toggleFavorite(h.view());
                return;
            }
        }

        // items: left = recipes, right = uses
        if (button == 0 || button == 1) {
            for (Hit hit : slotHits) {
                if (hit.at(mx, my)) {
                    show(hit.stack().getItem(), button == 1, true);
                    return;
                }
            }
        }
    }

    public void scrolled(double mx, double my, double scrollY) {
        final int dir = scrollY > 0 ? -1 : 1;
        if (my < top + 3) {
            selectTab(Math.max(0, Math.min(tab + dir, groups.size() - 1))); // over the tab bar: next / previous type
        } else {
            changePage(dir);
        }
    }

    private void setStatus(String text, int color) {
        status = text;
        statusColor = color;
        statusUntil = System.currentTimeMillis() + 4000;
    }

    /** Goes back to the previously viewed item. Returns true if there was one. */
    public boolean back() {
        if (history.isEmpty()) return false;
        final Nav nav = history.pop();
        show(nav.item(), nav.uses(), false);
        return true;
    }

    /** NEI's recipe keys: previous / next recipe page and machine (recipe type). Returns true if the key was used. */
    public boolean pageKey(InputConstants.Key key) {
        if (NEIKeys.is(NEIKeys.PREV_RECIPE, key)) changePage(-1);
        else if (NEIKeys.is(NEIKeys.NEXT_RECIPE, key)) changePage(1);
        else if (NEIKeys.is(NEIKeys.PREV_MACHINE, key)) selectTab(tab - 1);
        else if (NEIKeys.is(NEIKeys.NEXT_MACHINE, key)) selectTab(tab + 1);
        else return false;
        return true;
    }

    /** NEI's {@code bookmark.favorite_item}: Shift + key over an ingredient that has alternatives favorites that item. */
    public boolean favoriteItemKey() {
        if (hoveredView == null || hovered.isEmpty()) return false;
        for (SlotView s : hoveredView.slots()) {
            if (s.options().size() > 1 && s.options().stream().anyMatch(o -> o.getItem() == hovered.getItem())) {
                RecipeBookmarks.toggleFavoriteItem(BuiltInRegistries.ITEM.getKey(hovered.getItem()).toString());
                return true;
            }
        }
        return false;
    }

}
