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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
    private static final int HEAD = 44;
    private static final int CELL_H = 62;
    private static final int GRID_X = 14;
    /** NEI's {@code GuiRecipeButton.BUTTON_WIDTH / BUTTON_HEIGHT}. */
    private static final int BTN = 12;
    private static final int NAV_W = 13; // NEI: the < > buttons of the title and page rows are 13 x 12
    private static final int NAV_H = 12;
    private static final int SEARCH_W = W - (5 + NAV_W) * 2 + 1 - 12 - 45; // NEI: the field between the magnifier and the page count


    private record Group(String title, ItemStack icon, List<RecipeView> views, List<ItemStack> stations) {}

    /** NEI's tab size: JEI style 24 x 24 ({@code GuiRecipeTabJEI}), creative style 28 x 31 ({@code GuiRecipeTabCreative}). */
    private static int tabW() {
        return NEIConfig.RECIPE_CREATIVE_TABS.getAsBoolean() ? 28 : 24;
    }

    private static int tabH() {
        return NEIConfig.RECIPE_CREATIVE_TABS.getAsBoolean() ? 31 : 24;
    }

    /** NEI's {@code categoriesPerPage}: as many tabs as fit over the window, overlapping it by 3. */
    private static int tabsPerRow() {
        return (W - 4) / tabW();
    }

    private static boolean tabsShown() {
        return NEIConfig.RECIPE_JEI_TABS.getAsBoolean();
    }

    private static final ResourceLocation CREATIVE_TABS = new ResourceLocation("minecraft", "textures/gui/container/creative_inventory/tabs.png");

    private record Nav(Item item, boolean uses, ItemStack focus) {}

    /**
     * The stack a lookup was asked for. Some mods (NTM's fluid icons) use ONE item for many things and tell them apart by a
     * data component; a lookup of such a stack must only find recipes with that very stack, not with the item's other kinds.
     */
    private static ItemStack pendingFocus = ItemStack.EMPTY;

    /** The stack the next lookup is about (set by {@link #focusNext}), or empty. */
    static ItemStack pendingFocus() {
        return pendingFocus;
    }
    private ItemStack focus = ItemStack.EMPTY;

    public static void focusNext(ItemStack stack) {
        pendingFocus = stack == null ? ItemStack.EMPTY : stack;
    }

    /**
     * Does {@code option} (a recipe's stack) have everything the focused stack has? The recipe's stack may carry more
     * (an amount, say) than the panel's entry, so equal NBT is too strict: every NBT entry of the focus must be
     * present and equal, extra ones on the recipe side are fine.
     */
    private static boolean covers(ItemStack option, ItemStack focus) {
        if (option.getItem() != focus.getItem()) return false;
        final String wanted = codechicken.nei.item.FluidDisplay.subtypeOf(focus);
        if (!wanted.isEmpty()) return wanted.equals(codechicken.nei.item.FluidDisplay.subtypeOf(option)); // JEI's identity
        // every NBT entry of the focus must be present and equal on the recipe's stack (extra ones there are fine)
        return !focus.hasTag() || net.minecraft.nbt.NbtUtils.compareNbt(focus.getTag(), option.getTag(), true);
    }

    /** Does a recipe have the focused stack (same item and components) in the slots a lookup looks at? */
    private static boolean hasFocus(RecipeView view, ItemStack focus, boolean uses) {
        for (SlotView s : view.slots()) {
            if (s.output() == uses) continue;
            for (ItemStack o : s.options()) if (covers(o, focus)) return true;
        }
        return false;
    }

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
    /** The slot of a grid recipe under the mouse (for the Accepts list), or null. */
    private SlotView hoveredSlot;
    private RecipeView hoveredView;
    private RecipeView hoveredHeart;
    /** NEI's {@code selectedResult}: while the mouse is on a heart, the result it would favorite (the wheel changes it). */
    private RecipeView choiceView;
    private RecipeView prevChoiceView;
    private String choice;
    private boolean hoveredPick;
    /** The crafting recipe whose "+" the mouse is on: its ingredients show as ghost items in the grid. */
    private RecipeView ghostView;

    // NEI's infinite scroll: every recipe of the type in one list, moved by this many pixels
    private int scrollPx;
    private int areaTop;
    private int areaBottom;
    // the Scroll mode of the machines column: how far it is scrolled, and where it was drawn
    private int catScroll;
    private int catX, catY, catW, catH, catContent;
    /** The scroll bar being dragged: 0 none, 1 the infinite list's, 2 the machines column's. */
    private int dragBar;
    // NEI's cycle freeze: each recipe has its own clock, which stops while the recipe is frozen
    private final Map<RecipeView, long[]> clocks = new java.util.IdentityHashMap<>();
    private long lastFrame;
    private long frameDt;
    private RecipeView frozenView;
    private RecipeView nextFrozenView;
    /** NEI's Handler Info Widget (Shift + D), shared by every recipe window as in NEI. */
    private static boolean infoShown;

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
            scrollPx = 0;
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
        // a fluid its mod draws as an item of its own (NTM - Continuum's droplet, the fluid in a component): that mod's recipes
        // name the droplet, not the fluid, so the lookup is the droplet's (for this fluid only) plus what names the fluid itself
        List<RecipeView> fluidViews = List.of();
        if (codechicken.nei.item.FluidDisplay.isFluidItem(item)) {
            final ItemStack standIn = codechicken.nei.item.FluidDisplay.representative(
                    codechicken.nei.item.FluidDisplay.fluidOf(new ItemStack(item)));
            if (!standIn.isEmpty()) {
                fluidViews = uses ? RecipeIndex.usesOf(item, !HotkeyHelp.isCtrlDown()) : RecipeIndex.recipesFor(item);
                item = standIn.getItem();
                pendingFocus = standIn;
            }
        }
        if (pushHistory && this.item != null) history.push(new Nav(this.item, this.uses, this.focus));
        this.item = item;
        this.uses = uses;
        this.focus = pendingFocus.getItem() == item ? pendingFocus : ItemStack.EMPTY;
        pendingFocus = ItemStack.EMPTY;

        // A machine's uses list the recipes it makes; Ctrl held leaves them out (NEI's skipCatalyst). Unlike NEI this does not
        // depend on JEI Style Recipe Catalysts: None only hides the machine column (the user's choice)
        final boolean catalystUses = !HotkeyHelp.isCtrlDown();
        List<RecipeView> views = uses ? RecipeIndex.usesOf(item, catalystUses) : RecipeIndex.recipesFor(item);
        // only for a stack that differs from its item's plain form: a component on the stack marks a kind of the item
        if (!focus.isEmpty() && focus.hasTag()) {
            final ItemStack wanted = focus;
            final int before = views.size();
            views = views.stream().filter(v -> hasFocus(v, wanted, uses)).toList();
            if (views.isEmpty() && before > 0) { // diagnostic for the next report
                codechicken.nei.NotEnoughItems.LOGGER.info("NEI lookup filter: no recipe of {} has the stack {}; first recipe's stacks:",
                        codechicken.nei.item.FluidDisplay.idOf(item), wanted.getTag());
                final var all = uses ? RecipeIndex.usesOf(item, catalystUses) : RecipeIndex.recipesFor(item);
                for (SlotView s : all.get(0).slots()) for (ItemStack o : s.options()) {
                    if (o.getItem() == item) codechicken.nei.NotEnoughItems.LOGGER.info("  {}", o.getTag());
                }
            }
        }
        if (!fluidViews.isEmpty()) {
            final List<RecipeView> merged = new ArrayList<>(views);
            final java.util.Set<RecipeView> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
            seen.addAll(views);
            for (RecipeView v : fluidViews) if (seen.add(v)) merged.add(v);
            views = merged;
        }
        // diagnostic: what a lookup found (to see why a lookup lists unrelated things)
        codechicken.nei.NotEnoughItems.LOGGER.info("NEI lookup {} of {}: {} recipes", uses ? "uses" : "recipes",
                codechicken.nei.item.FluidDisplay.idOf(item), views.size());
        for (int i = 0; i < Math.min(views.size(), 12); i++) {
            final StringBuilder sb = new StringBuilder();
            for (SlotView s : views.get(i).slots()) {
                sb.append(s.output() ? " out" : " in").append('[').append(s.options().size()).append(": ")
                        .append(codechicken.nei.item.FluidDisplay.idOf(s.options().get(0))).append(']');
            }
            codechicken.nei.NotEnoughItems.LOGGER.info("  {}:{}", views.get(i).title(), sb);
        }
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
        this.scrollPx = 0;
        this.catScroll = 0;
        clocks.clear();
    }

    private void layout(int sw, int sh) {
        // as in NEI's GuiRecipe#initGui: room for the tabs above (kept while they are hidden), at most Max Height high
        height = Math.max(130, Math.min(sh - 22 - 22 - tabH(), NEIConfig.RECIPE_MAX_HEIGHT.getAsInt()));
        left = (sw - W) / 2;
        top = Math.max(22 - 3 + tabH(), (sh - height) / 2);
        boxLeft = left;
        areaTop = top + HEAD - 3;
        areaBottom = top + height - 4;
    }

    private static boolean infinite() {
        return NEIConfig.RECIPE_INFINITE_SCROLL.getAsBoolean();
    }

    /** How far the infinite list can be scrolled. */
    private int maxScroll() {
        return Math.max(0, views().size() * cellH() - (areaBottom - areaTop - 3));
    }

    private boolean inArea(double mx, double my) {
        return mx >= left && mx < left + W && my >= areaTop && my < areaBottom;
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
        if (infinite()) return Math.max(1, views().size());
        return Math.max(1, (height - HEAD - 6) / cellH());
    }

    private void selectTab(int index) {
        if (groups.isEmpty()) return;
        tab = (index + groups.size()) % groups.size();
        tabOffset = tab / tabsPerRow() * tabsPerRow();
        page = 0;
        scrollPx = 0;
        catScroll = 0;
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

    /** True if the point is on the window or its tabs (or the Handler Info box). */
    public boolean contains(double mx, double my) {
        if (infoShown && NEIConfig.RECIPE_HANDLER_INFO.getAsBoolean() && Draw.in(mx, my, infoX, infoY, INFO_W, infoH)) return true;
        return Draw.in(mx, my, boxLeft - 4, top - tabH(), left + W + 8 - boxLeft + 4, height + tabH());
    }

    /** The stack a cycling slot shows at this point of its recipe's clock (one step a second, as before). */
    private static ItemStack current(SlotView slot, long clock) {
        final List<ItemStack> options = slot.options();
        return options.get((int) ((clock / 1000) % options.size()));
    }

    private static ItemStack current(SlotView slot) {
        return current(slot, System.currentTimeMillis());
    }

    /**
     * NEI's {@code isCycleFrozen}: a recipe stops cycling while Shift is held, or, with Stop cycling on hover, while the mouse
     * was on one of its items or buttons in the last frame. Each recipe keeps its own clock so a frozen one picks up where it
     * stopped.
     */
    private long clockOf(RecipeView view) {
        final long[] clock = clocks.computeIfAbsent(view, v -> new long[] { System.currentTimeMillis() });
        final boolean frozen = HotkeyHelp.isShiftDown()
                || NEIConfig.RECIPE_FREEZE_CYCLE.getAsBoolean() && view == frozenView;
        if (!frozen) clock[0] += frameDt;
        return clock[0];
    }

    public void render(GuiGraphics g, int mx, int my, int sw, int sh) {
        layout(sw, sh);
        final Font font = Minecraft.getInstance().font;

        slotHits.clear();
        heartHits.clear();
        plusHits.clear();
        hovered = ItemStack.EMPTY;
        hoveredSlot = null;
        hoveredView = null;
        hoveredHeart = null;
        hoveredPick = false;
        prevChoiceView = choiceView; // set while the mouse was on a heart in the last frame
        choiceView = null;
        ghostView = null;
        page = Math.max(0, Math.min(page, pageCount() - 1));
        final long now = System.currentTimeMillis();
        frameDt = lastFrame == 0 ? 0 : Math.max(0, Math.min(1000, now - lastFrame));
        lastFrame = now;
        frozenView = nextFrozenView;
        nextFrozenView = null;
        // a scroll bar held with the mouse follows it
        if (dragBar == 1) scrollPx = (int) ((long) (my - areaTop) * maxScroll() / Math.max(1, areaBottom - areaTop));
        if (dragBar == 2 && catH > 14) catScroll = (int) ((long) (my - catY - 7) * Math.max(0, catContent - (catH - 14)) / Math.max(1, catH - 14));
        scrollPx = Math.max(0, Math.min(scrollPx, maxScroll()));

        Tex.nine(g, Tex.RECIPE_BG, 256, 256, left - 4, top - 4, W + 8, height + 8, 0, 0, 184, 174, 9, 9, 9, 9);

        // tabs (NEI's JEI style or creative style): drawn after the window so the selected one joins it
        final int tw = tabW();
        final int th = tabH();
        final int perRow = tabsPerRow();
        final int ty = top - th + 3;
        for (int i = tabOffset; tabsShown() && i < Math.min(groups.size(), tabOffset + perRow); i++) {
            final int tx = left + 4 + (i - tabOffset) * tw;
            final ItemStack icon = groups.get(i).icon();
            if (NEIConfig.RECIPE_CREATIVE_TABS.getAsBoolean()) {
                // the creative inventory's tab sprites (26 x 32), centred in NEI's 28 x 31 tab; the icon sits 22 above its bottom
                // NEI's GuiRecipeTabCreative: the creative inventory's tabs.png, (28, 32) 28 x 32 selected, (28, 0) 28 x 30 not
                if (i == tab) Gfx.blit(g, CREATIVE_TABS, tx, ty, 28f, 32f, 28, 32, 256, 256);
                else Gfx.blit(g, CREATIVE_TABS, tx, ty, 28f, 0f, 28, 30, 256, 256);
                if (!icon.isEmpty()) Draw.stack(g, icon, tx + (tw - 16) / 2, ty + th - 22);
            } else {
                Tex.blit(g, Tex.SPRITES, 256, 256, tx, ty, i == tab ? 0 : 24, 16, 24, 24);
                if (!icon.isEmpty()) Draw.stack(g, icon, tx + 4, ty + 4);
            }
            if (Draw.in(mx, my, tx, ty, tw, th)) {
                // the owning mod's display name, as item tooltips show it (NTM, not the namespace "hbm")
                final String mod = icon.isEmpty() ? "Minecraft" : ItemTooltip.modName(icon);
                ItemTooltip.requestPlain(List.of(Component.literal(groups.get(i).title()),
                        Component.literal(mod).withStyle(ChatFormatting.BLUE)));
            }
        }
        if (tabsShown() && groups.size() > perRow) {
            final int ah = th - 4;
            Draw.button(g, left - 3, ty + 2, 8, ah, Draw.in(mx, my, left - 3, ty + 2, 8, ah));
            Draw.button(g, left + 4 + perRow * tw - 1, ty + 2, 8, ah, Draw.in(mx, my, left + 4 + perRow * tw - 1, ty + 2, 8, ah));
            Gfx.text(g, font, Component.literal("<"), left - 1, ty + ah / 2 - 2, 0xFFE0E0E0, false);
            Gfx.text(g, font, Component.literal(">"), left + 4 + perRow * tw + 1, ty + ah / 2 - 2, 0xFFE0E0E0, false);
        }

        // title row and page row (NEI: darkened bars with a < > button at each end)
        final Group group = group();
        final String title = group == null ? (uses ? "Uses" : "Recipes") : group.title();
        final int rightX = left + W - 5 - NAV_W + 1;
        for (int row = 0; row < 2; row++) {
            final int by = top + 3 + row * 14;
            final int barLeft = left + 5 + NAV_W + (row == 1 ? 12 : 0);
            Gfx.fill(g, barLeft, by, rightX - 1, by + NAV_H, 0x30000000);
            if (row == 1 && searchOn) {
                final String count = (page + 1) + "/" + pageCount();
                Gfx.text(g, font, Component.literal(count), left + 30 + SEARCH_W + 22 - font.width(count) / 2, by + 2,
                        0xFFFFFFFF, true);
            } else {
                final String text = row == 0 ? title : "Page " + (page + 1) + "/" + pageCount();
                Gfx.text(g, font, Component.literal(text), left + (W - font.width(text)) / 2, by + 2, 0xFFFFFFFF, true);
            }
            if (row == 1 || groups.size() > 1) {
                Draw.button(g, left + 5, by, NAV_W, NAV_H, Draw.in(mx, my, left + 5, by, NAV_W, NAV_H));
                Draw.button(g, rightX, by, NAV_W, NAV_H, Draw.in(mx, my, rightX, by, NAV_W, NAV_H));
                Gfx.text(g, font, Component.literal("<"), left + 9, by + 2, 0xFFE0E0E0, false);
                Gfx.text(g, font, Component.literal(">"), rightX + 4, by + 2, 0xFFE0E0E0, false);
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
            search.render(g, mx, my, 0f);
        }
        if (overSearch) {
            final Component filterTitle = Component.literal("Recipe Filter").withStyle(ChatFormatting.WHITE);
            // As elsewhere in NEI: the hotkeys show while Alt is held
            ItemTooltip.requestPlain(HotkeyHelp.helpDown()
                    ? List.of(filterTitle, syntax("Up / Down", "search history"), syntax("RMB", "clear the field"),
                            syntax("Esc", "leave the field"))
                    : ItemTooltip.hinted(filterTitle, syntax("<", "ingredients"), syntax(">", "results"),
                            syntax("^", "stations"), syntax("&", "after a marker: all must match")));
        }

        if (group != null) drawStations(g, group.stations(), mx, my);
        else boxLeft = left;

        // recipes
        if (group == null) {
            final String msg = ClientRecipes.all().isEmpty()
                    ? "No recipe data yet (join a world first)"
                    : "No recipes found";
            Gfx.text(g, font, Component.literal(msg), left + 8, top + HEAD + 6, 0xFF702020, false);
        } else {
            final List<RecipeView> shown = views();
            if (infinite()) {
                // NEI's infinite scroll: one list, cut to the recipe area; the mouse counts only inside it
                final int my2 = inArea(mx, my) ? my : -10000;
                Tex.beginClip(g, left, areaTop, W, areaBottom - areaTop);
                for (int r = 0; r < shown.size(); r++) {
                    final int cellTop = top + HEAD + r * cellH() - scrollPx;
                    if (cellTop + cellH() < areaTop || cellTop - 3 > areaBottom) continue;
                    drawRecipe(g, font, shown.get(r), cellTop, mx, my2, r > 0);
                }
                Tex.endClip(g);
                drawScrollBar(g, left + W - 3, areaTop, areaBottom - areaTop, scrollPx, maxScroll(), shown.size() * cellH());
            } else {
                final int start = page * perPage();
                for (int r = 0; r < perPage() && start + r < shown.size(); r++) {
                    drawRecipe(g, font, shown.get(start + r), top + HEAD + r * cellH(), mx, my, r > 0);
                }
            }
            if (shown.isEmpty()) {
                Gfx.text(g, font, Component.literal("No recipes match"), left + 8, top + HEAD + 6, 0xFF702020, false);
            }
        }

        if (System.currentTimeMillis() < statusUntil) {
            Gfx.text(g, font, Component.literal(status), left + 8, top + height - 14, statusColor, false);
        }

        if (ghostView != null && codechicken.nei.NEIConfig.RECIPE_GHOST.get() && screen instanceof AbstractContainerScreen<?> container) {
            final long clock = clockOf(ghostView);
            RecipeTransfer.drawGhosts(g, container, ghostView, s -> current(s, clock), false);
        }
        if (infoShown && NEIConfig.RECIPE_HANDLER_INFO.getAsBoolean() && group != null) drawHandlerInfo(g, font, group);

        HotkeyHelp.recipeResult = !hovered.isEmpty() && hoveredView != null
                && RecipeBookmarks.favoriteChoices(hoveredView).contains(codechicken.nei.item.FluidDisplay.idOf(hovered));
        if (!hovered.isEmpty()) {
            // a fluid display item has no tooltip of its own: the amount the recipe needs goes under its name
            final List<Component> amount = codechicken.nei.item.FluidDisplay.is(hovered) && hovered.getCount() > 1
                    ? List.of(Component.literal(hovered.getCount() + " mB").withStyle(net.minecraft.ChatFormatting.GRAY)) : List.of();
            ItemTooltip.request(hovered, HotkeyHelp.Where.RECIPE, amount);
            // NEI's HoveredRecipe RECIPE_GUI: an item outside the recipes (a machine) shows its favorite recipe
            if (hoveredView == null && NEIConfig.FAVORITE_TOOLTIP_GUI.getAsBoolean()) {
                final RecipeView favorite = RecipeBookmarks.favoriteView(hovered);
                if (favorite != null) ItemTooltip.requestRecipe(favorite);
            }
            if (hoveredView != null && NEIConfig.RECIPE_CYCLED_TOOLTIP.getAsBoolean()) {
                final SlotView cycling = hoveredSlot != null ? hoveredSlot : cyclingSlotOf(hoveredView, hovered);
                if (cycling != null && cycling.options().size() > 1) ItemTooltip.requestAccepts(acceptsList(cycling), hovered);
            }
        }
        else if (hoveredHeart != null) {
            final boolean wheel = codechicken.nei.NEIConfig.FAVORITE_WHEEL.getAsBoolean();
            final boolean several = wheel && RecipeBookmarks.favoriteChoices(hoveredHeart).size() > 1;
            // which result the heart is about (the wheel changes it): shown under "Favorite" when there is a choice
            final List<Component> state = new ArrayList<>();
            if (several && choice != null) {
                final ItemEntry chosen = codechicken.nei.ItemList.find(choice);
                if (chosen != null) {
                    state.add(Component.literal("Result: ").withStyle(net.minecraft.ChatFormatting.GRAY)
                            .append(chosen.stack.getHoverName()));
                    state.add(Component.literal("Scroll to choose the result").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
                }
            }
            ItemTooltip.requestHeart(several, state);
        }
        else if (hoveredPick) ItemTooltip.requestPlain(List.of(Component.literal("Use for recipe tree")));
    }

    private static final int BAR_W = 6; // the Scroll mode's bar beside the machines

    /**
     * NEI's station column ({@code GuiRecipeCatalyst}), left of the window, 16 px per item. JEI Style Recipe Catalysts: None
     * hides it, Grid wraps into more columns, Scroll keeps one column and adds a scroll bar on its left when it is too tall.
     */
    private void drawStations(GuiGraphics g, List<ItemStack> stations, int mx, int my) {
        final int mode = NEIConfig.RECIPE_CATALYSTS.getAsInt();
        catH = 0;
        if (stations.isEmpty() || mode == 0) {
            boxLeft = left;
            return;
        }
        final int available = height - 5; // NEI: setAvailableHeight(ySize - 5)
        final int perCol = Math.max(1, (available - 2 - 12) / 16);
        final int cols = mode == 1 ? (stations.size() + perCol - 1) / perCol : 1;
        final int rows = (stations.size() + cols - 1) / cols;
        final boolean scroll = mode == 2 && rows * 16 + 14 > available;
        final int bar = scroll ? BAR_W : 0;
        final int w = cols * 16 + 7 + 3 + bar;
        final int h = scroll ? available : rows * 16 + 14;
        final int x = left - w + 4;
        boxLeft = x;
        final int innerH = h - 14;
        catContent = rows * 16;
        catScroll = scroll ? Math.max(0, Math.min(catScroll, catContent - innerH)) : 0;
        catX = x;
        catY = top;
        catW = w;
        catH = scroll ? h : 0; // only a scrolling column takes the wheel

        Tex.nine(g, Tex.CATALYST, 36, 36, x - 4, top - 4, w + 8, h + 8, 0, 0, 36, 36, 10, 10, 10, 10);
        Draw.slot(g, x + 6 + bar, top + 6, cols * 16 + 2, innerH + 2);
        if (scroll) drawScrollBar(g, x + 6, top + 7, innerH, catScroll, catContent - innerH, catContent);

        if (scroll) Tex.beginClip(g, x + 7 + bar, top + 7, cols * 16, innerH);
        final boolean mouseIn = Draw.in(mx, my, x + 7 + bar, top + 7, cols * 16, innerH);
        for (int i = 0; i < stations.size(); i++) {
            final int ix = x + 7 + bar + cols * 16 - 16 - (i / rows) * 16;
            final int iy = top + 7 + (i % rows) * 16 - catScroll;
            if (iy + 16 <= top + 7 || iy >= top + 7 + innerH) continue;
            final ItemStack stack = stations.get(i);
            Draw.stack(g, stack, ix, iy);
            final Hit hit = new Hit(ix, Math.max(iy, top + 7), 16, Math.min(iy + 16, top + 7 + innerH) - Math.max(iy, top + 7), stack);
            slotHits.add(hit);
            if (mouseIn && hit.at(mx, my)) {
                Gfx.fill(g, ix, iy, ix + 16, iy + 16, 0x80FFFFFF);
                hovered = stack;
            }
        }
        if (scroll) Tex.endClip(g);
    }

    /** A thin scroll bar: the track, and a thumb as long as the visible part is of the whole. */
    private static void drawScrollBar(GuiGraphics g, int x, int y, int h, int scrolled, int max, int content) {
        if (max <= 0 || content <= 0) return;
        Gfx.fill(g, x, y, x + 3, y + h, 0x40000000);
        final int thumb = Math.max(8, h * h / Math.max(h, content));
        final int ty = y + (int) ((long) (h - thumb) * scrolled / max);
        Gfx.fill(g, x, ty, x + 3, ty + thumb, 0xFFA0A0A0);
        Gfx.fill(g, x, ty, x + 2, ty + thumb - 1, 0xFFE0E0E0);
    }

    // ---- NEI's Handler Info Widget (DebugHandlerWidget): Shift + D, read only here ----
    private static final int INFO_W = 150;
    private int infoX, infoY, infoH;

    private void drawHandlerInfo(GuiGraphics g, Font font, Group group) {
        final List<String[]> rows = new ArrayList<>();
        rows.add(new String[] { "Name", group.title() });
        final RecipeView first = group.views().isEmpty() ? null : group.views().get(0);
        final ItemStack icon = group.icon();
        rows.add(new String[] { "Mod Name", icon.isEmpty() ? "Minecraft" : ItemTooltip.modName(icon) });
        rows.add(new String[] { "Mod ID", icon.isEmpty() ? "minecraft" : codechicken.nei.item.FluidDisplay.ownerNamespace(icon) });
        rows.add(new String[] { "Order", String.valueOf(tab) });
        rows.add(new String[] { "Recipes", String.valueOf(group.views().size()) });
        rows.add(new String[] { "Height", String.valueOf(cellH()) });
        rows.add(new String[] { "Width", String.valueOf(first != null && first.drawn() != null ? Math.max(166, first.drawn().width()) : 166) });
        rows.add(new String[] { "Multi Widgets", String.valueOf(perPage() > 1) });
        rows.add(new String[] { "Show Favorites", String.valueOf(NEIConfig.FAVORITES_ENABLED.getAsBoolean()) });
        rows.add(new String[] { "Show Overlays", String.valueOf(screen instanceof AbstractContainerScreen<?>) });
        rows.add(new String[] { "Machines", String.valueOf(group.stations().size()) });

        infoX = left + W + 8;
        infoY = top;
        infoH = 6 + 12 + 4 + rows.size() * 11 + 4;
        Tex.nine(g, Tex.RECIPE_BG, 256, 256, infoX - 4, infoY - 4, INFO_W + 8, infoH + 8, 0, 0, 184, 174, 9, 9, 9, 9);
        Gfx.fill(g, infoX + 4, infoY + 6, infoX + INFO_W - 4, infoY + 18, 0x30000000);
        final String title = "Debug Recipe Handler";
        Gfx.text(g, font, Component.literal(title), infoX + (INFO_W - font.width(title)) / 2, infoY + 8, 0xFFFFFFFF, true);
        int y = infoY + 22;
        for (String[] row : rows) {
            Gfx.text(g, font, Component.literal(row[0]), infoX + 6, y, 0xFF888888, false);
            String value = row[1];
            while (value.length() > 1 && font.width(value) > INFO_W - 70) value = value.substring(0, value.length() - 1);
            if (!value.equals(row[1])) value = value.substring(0, Math.max(0, value.length() - 1)) + "\u2026";
            Gfx.text(g, font, Component.literal(value), infoX + 66, y, 0xFFFFFFFF, true);
            y += 11;
        }
    }

    /** The slot of a recipe that cycles through this stack (for a recipe that draws itself, where the slot is not known). */
    private static SlotView cyclingSlotOf(RecipeView view, ItemStack stack) {
        for (SlotView s : view.slots()) {
            if (s.options().size() > 1 && s.options().stream().anyMatch(o -> ItemStack.isSameItemSameTags(o, stack))) return s;
        }
        return null;
    }

    /** NEI's {@code AcceptsFollowingTooltipLineHandler}: the slot's alternatives once each, manual favorite items first. */
    private static List<ItemStack> acceptsList(SlotView slot) {
        final List<ItemStack> list = new ArrayList<>();
        final java.util.Set<String> seen = new java.util.HashSet<>();
        for (ItemStack o : slot.options()) {
            if (seen.add(codechicken.nei.item.FluidDisplay.idOf(o) + o.getTag())) list.add(o);
        }
        list.sort(java.util.Comparator.comparing((ItemStack o) -> !RecipeBookmarks.isFavoriteItem(codechicken.nei.item.FluidDisplay.idOf(o))));
        return list;
    }

    /**
     * The heart's state. While the mouse is on it the heart is about one result of the recipe (the first, or the one the
     * wheel chose: NEI's {@code selectedResult}); it shows whether that result's favorite is this recipe.
     */
    private boolean favoriteShown(RecipeView view, boolean overHeart) {
        if (!overHeart || !codechicken.nei.NEIConfig.FAVORITE_WHEEL.getAsBoolean()) return RecipeBookmarks.isFavorite(view);
        if (prevChoiceView != view || choice == null) choice = RecipeBookmarks.favoriteItem(view);
        choiceView = view;
        return choice != null && RecipeBookmarks.isFavoriteFor(view, choice);
    }

    /** NEI's {@code drawItemOverlay}: the chosen result is greyed with the heart on it. */
    private static void drawChoice(GuiGraphics g, int sx, int sy, int size, boolean on) {
        Gfx.fill(g, sx, sy, sx + size, sy + size, 0x66333333);
        heartIcon(g, sx + (size - 9) / 2, sy + (size - 10) / 2, on);
    }

    /** NEI's favorite icon ({@code GuiFavoriteButton.drawContent}): 9 x 10 at (10, 76) off, (19, 76) on, in a 12 x 12 button. */
    private static void heartIcon(GuiGraphics g, int bx, int by, boolean on) {
        Tex.blit(g, Tex.ICONS, 256, 256, bx + (BTN - 9 - 1) / 2, by + (BTN - 10) / 2, on ? 19 : 10, 76, 9, 10);
    }

    /**
     * NEI's "+" icon ({@code GuiOverlayButton.drawContent}), 9 x 10 on the icon sheet: the plain "+" (46, 76); with Shift
     * held the fill arrow (28, 76), or the red fill arrow (37, 76) when the recipe does not fit. A recipe that does not
     * fit is drawn at half strength (NEI: alpha 0.5 when the button is disabled).
     */
    private static void plusIcon(GuiGraphics g, int bx, int by, boolean fits) {
        plusIcon(g, bx, by, fits, HotkeyHelp.isShiftDown());
    }

    /** {@code fill}: the click would move items (NEI: canFillCraftingGrid and (no Shift needed or Shift held)). */
    private static void plusIcon(GuiGraphics g, int bx, int by, boolean fits, boolean fill) {
        final int u = fill ? (fits ? 28 : 37) : 46;
        final int ix = bx + (BTN - 9 - 1) / 2;
        final int iy = by + (BTN - 10) / 2;
        Tex.blit(g, Tex.ICONS, 256, 256, ix, iy, u, 76, 9, 10);
        if (!fits) Gfx.fill(g, ix, iy, ix + 9, iy + 10, 0x80262E35);
    }

    /** A recipe that draws itself (JEI layout): centered in the window, with NEI's heart and "+" buttons at the edge. */
    private void drawSelfDrawn(GuiGraphics g, Font font, RecipeView view, int cellTop, int mx, int my) {
        final RecipeView.Drawn d = view.drawn();
        final int x = Math.max(left + 2, left + (W - d.width()) / 2);
        d.draw(g, x, cellTop, mx, my);

        if (prevChoiceView == view && choice != null) {
            final ItemEntry chosen = codechicken.nei.ItemList.find(choice);
            if (chosen != null) d.highlightResult(g, chosen.stack, 0x66333333);
        }
        final ItemStack over = d.itemAt(mx, my);
        if (!over.isEmpty()) {
            hovered = over;
            nextFrozenView = view;
            // clicks are looked up in slotHits: one tiny area at the mouse stands for the slot the layout found
            slotHits.add(new Hit(mx, my, 1, 1, over));
        } else if (d.contains(mx, my)) {
            final List<Component> lines = d.tooltip(mx, my);
            if (!lines.isEmpty()) ItemTooltip.requestPlain(lines);
            else d.overlays(g, mx, my);
        }

        final int hx = left + W - 4 - BTN;
        final int hy = cellTop + 2;
        if (codechicken.nei.NEIConfig.FAVORITES_ENABLED.getAsBoolean()) { // NEI: no favorite button while the favorites are hidden
            final boolean overHeart = Draw.in(mx, my, hx, hy, BTN, BTN);
            final boolean favorite = favoriteShown(view, overHeart);
            Draw.button(g, hx, hy, BTN, BTN, overHeart);
            heartIcon(g, hx, hy, favorite);
            heartHits.add(new Heart(hx, hy, view));
            if (overHeart) hoveredHeart = view;
        }
        if (Draw.in(mx, my, hx, hy, BTN, BTN * 2 + 2)) nextFrozenView = view;
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

    private void drawRecipe(GuiGraphics g, Font font, RecipeView view, int cellTop, int mx, int my,
            boolean separator) {
        if (separator) Gfx.fill(g, left + 8, cellTop - 3, left + W - 8, cellTop - 2, 0xFF8B8B8B);
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

        final long clock = clockOf(view);
        for (int si = 0; si < view.slots().size(); si++) {
            final SlotView slot = view.slots().get(si);
            final int sx = left + GRID_X + slot.gx() * 18;
            final int sy = cellTop + 4 + slot.gy() * 18;
            final ItemStack stack = current(slot, clock);

            if (slot.output()) {
                Draw.slot(g, sx - 4, sy - 4, 26); // the result gets the big frame, as in NEI
                resultItem = stack.getItem();
            } else {
                Draw.slot(g, sx, sy, 18);
            }

            Draw.stack(g, stack, sx + 1, sy + 1);
            if (view == missingView && missing.contains(si) && System.currentTimeMillis() < statusUntil) {
                Gfx.fill(g, sx + 1, sy + 1, sx + 17, sy + 17, 0x70FF0000); // ingredient the player does not have
            }
            if (present != null && !slot.output() && (!present[si] || codechicken.nei.NEIConfig.PRESENCE_HIGHLIGHT_PRESENT.get())) {
                if (presenceMode == 2) {
                    Gfx.fill(g, sx + 1, sy + 1, sx + 17, sy + 17, present[si] ? 0x8000AA00 : 0x80AA0000);
                } else { // NEI's icons: a tick / a cross in the slot's bottom right corner (nei_tabbed_sprites, 8 x 8)
                    Tex.blit(g, Tex.SPRITES, 256, 256, sx + 9, sy + 9, present[si] ? 8 : 0, 40, 8, 8);
                }
            }
            if (slot.output() && stack.getCount() > 1) Gfx.decorations(g, font, stack, sx + 1, sy + 1);
            if (slot.output() && view == prevChoiceView && choice != null
                    && choice.equals(codechicken.nei.item.FluidDisplay.idOf(stack))) {
                drawChoice(g, sx + 1, sy + 1, 16, RecipeBookmarks.isFavoriteFor(view, choice));
            }

            final Hit hit = new Hit(sx, sy, 18, 18, stack);
            slotHits.add(hit);
            if (hit.at(mx, my)) {
                Gfx.fill(g, sx + 1, sy + 1, sx + 17, sy + 17, 0x80FFFFFF);
                hovered = stack;
                hoveredSlot = slot;
                nextFrozenView = view;
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
            Gfx.text(g, font, Component.literal(view.info()), left + GRID_X + 66, cellTop + 50, 0xFF404040, false);
        }

        if (resultItem != null) {
            final int hx = left + W - 8 - BTN - 4;
            final int hy = cellTop + 4 + 18 + 2;
            if (Draw.in(mx, my, hx, hy, BTN, BTN * 2 + 2)) nextFrozenView = view;
            // NEI's favorite button: sets this recipe as the one used for its result in recipe trees
            if (codechicken.nei.NEIConfig.FAVORITES_ENABLED.getAsBoolean()) {
                final boolean overHeart = Draw.in(mx, my, hx, hy, BTN, BTN);
                final boolean favorite = favoriteShown(view, overHeart);
                Draw.button(g, hx, hy, BTN, BTN, overHeart);
                heartIcon(g, hx, hy, favorite);
                heartHits.add(new Heart(hx, hy, view));
                if (overHeart) hoveredHeart = view;
            }
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
                // NEI: on a crafting grid the click only shows the recipe in the grid unless Shift is held or not required
                plusIcon(g, hx, py, fits, !grid ? HotkeyHelp.isShiftDown()
                        : !NEIConfig.RECIPE_SHIFT_OVERLAY.getAsBoolean() || HotkeyHelp.isShiftDown());
                plusHits.add(new Plus(hx, py, view));
                if (fits && grid && Draw.in(mx, my, hx, py, BTN, BTN)) ghostView = view;
            }
        }
    }

    // ---- NEI's RecipeTooltipLineHandler: a recipe drawn inside a tooltip (bookmarks, favorites) ----

    /** Width and height of {@link #drawCard}: NEI's widget (at least 166 wide) with 5 px padding and the 12 px title bar. */
    static int cardWidth(RecipeView view) {
        return (view.drawn() != null ? Math.max(166, view.drawn().width()) : 166) + 10;
    }

    static int cardHeight(RecipeView view) {
        return (view.drawn() != null ? view.drawn().height() + 8 : CELL_H) + 10 + 12;
    }

    /** Draws a recipe as NEI's recipe tooltip does: the recipe window's background, the type's name in a dark bar, the recipe. */
    static void drawCard(GuiGraphics g, RecipeView view, int x, int y) {
        final Font font = Minecraft.getInstance().font;
        final int w = cardWidth(view);
        final int h = cardHeight(view);
        Tex.nine(g, Tex.RECIPE_BG, 256, 256, x - 4, y - 4, w + 8, h + 8, 0, 0, 184, 174, 9, 9, 9, 9);
        Gfx.fill(g, x + 5, y + 5, x + w - 5, y + 17, 0x30000000);
        final String title = view.title();
        Gfx.text(g, font, Component.literal(title), x + 5 + (w - 10 - font.width(title)) / 2, y + 7, 0xFFFFFFFF, true);

        final int cellTop = y + 17;
        if (view.drawn() != null) {
            final RecipeView.Drawn d = view.drawn();
            d.draw(g, x + (w - d.width()) / 2, cellTop, -10000, -10000);
            return;
        }
        final int gx = x + GRID_X;
        if (view.crafting() != RecipeView.Crafting.NONE) {
            for (int c = 0; c < 9; c++) Draw.slot(g, gx + c % 3 * 18, cellTop + 4 + c / 3 * 18, 18);
        }
        for (SlotView slot : view.slots()) {
            final int sx = gx + slot.gx() * 18;
            final int sy = cellTop + 4 + slot.gy() * 18;
            final ItemStack stack = current(slot);
            if (slot.output()) Draw.slot(g, sx - 4, sy - 4, 26);
            else Draw.slot(g, sx, sy, 18);
            Draw.stack(g, stack, sx + 1, sy + 1);
            if (slot.output() && stack.getCount() > 1) Gfx.decorations(g, font, stack, sx + 1, sy + 1);
        }
        if (view.info().isEmpty()) {
            Draw.arrow(g, gx + 3 * 18 + 8, cellTop + 4 + 27);
        } else {
            final float cook = Draw.cookFraction();
            Draw.progressArrow(g, gx + 3 * 18 + 8, cellTop + 4 + 27, cook);
            boolean fuel = false;
            for (SlotView s : view.slots()) fuel |= !s.output() && !s.indexed();
            if (fuel) Draw.flame(g, gx + 18 + 1, cellTop + 4 + 20, cook);
            Gfx.text(g, font, Component.literal(view.info()), gx + 66, cellTop + 50, 0xFF404040, false);
        }
    }

    /** True while the item under the mouse is outside every recipe (the machines column): NEI's {@code getSlotAtPosition}. */
    boolean hoveredOutsideRecipe() {
        return !hovered.isEmpty() && hoveredView == null;
    }

    /** The mouse button was let go: a dragged scroll bar stops. */
    public void mouseReleased() {
        dragBar = 0;
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

        // tabs and their arrows (NEI: nothing while JEI Style Tabs is off)
        final int tw = tabW();
        final int th = tabH();
        final int perRow = tabsPerRow();
        final int ty = top - th + 3;
        if (tabsShown()) {
            if (groups.size() > perRow && Draw.in(mx, my, left - 3, ty + 2, 8, th - 4)) {
                selectTab((tabOffset / perRow - 1) * perRow);
                return;
            }
            if (groups.size() > perRow && Draw.in(mx, my, left + 4 + perRow * tw - 1, ty + 2, 8, th - 4)) {
                selectTab((tabOffset / perRow + 1) * perRow);
                return;
            }
            if (my >= ty && my < ty + th && mx >= left + 4 && mx < left + 4 + perRow * tw) {
                final int index = tabOffset + (int) ((mx - left - 4) / tw);
                if (index < groups.size()) selectTab(index);
                return;
            }
        }

        // the scroll bars: a click on the track moves the thumb there
        if (infinite() && maxScroll() > 0 && Draw.in(mx, my, left + W - 4, areaTop, 5, areaBottom - areaTop)) {
            scrollPx = (int) ((my - areaTop) * maxScroll() / Math.max(1, areaBottom - areaTop));
            dragBar = 1; // keeps following the mouse until the button is let go
            return;
        }
        if (catH > 0 && Draw.in(mx, my, catX + 5, catY + 7, BAR_W, catH - 14)) {
            catScroll = (int) ((my - catY - 7) * (catContent - (catH - 14)) / Math.max(1, catH - 14));
            dragBar = 2;
            return;
        }
        // NEI's infinite list: items and buttons outside the recipe area are cut off and take no clicks
        final boolean cut = infinite() && mx >= left && mx < left + W && !inArea(mx, my);

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
        for (Plus p : cut ? List.<Plus>of() : plusHits) {
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
                // As in NEI (GuiOverlayButton#overlayRecipe): with Require Shift a plain click only shows the recipe faded in the
                // grid until the screen closes; Shift + click (or any click without the option) fills the grid with as many
                // sets as possible, Ctrl with one set
                final boolean fill = !NEIConfig.RECIPE_SHIFT_OVERLAY.getAsBoolean() || HotkeyHelp.isShiftDown();
                if (button != 0) return;
                if (!fill) {
                    if (RecipeTransfer.canTransfer(container, p.view())) {
                        final long clock = clockOf(p.view());
                        RecipeTransfer.showOverlay(container, p.view(), s -> current(s, clock));
                        closeRequested = true;
                    }
                    return;
                }
                final RecipeTransfer.Result r = RecipeTransfer.transfer(container, p.view(),
                        HotkeyHelp.isCtrlDown() ? 1 : Integer.MAX_VALUE);
                missingView = p.view();
                missing = r.missing();
                if (r.error() != null) setStatus(r.error(), 0xFF702020);
                else if (!r.missing().isEmpty()) {
                    setStatus("Missing " + r.missing().size() + " ingredient(s)", 0xFF702020);
                    // As in NEI (the + always goes back to the inventory): once anything is in the grid the window closes;
                    // it only stays open, with the missing ingredients marked, when nothing could be placed
                    if (r.placed() > 0) closeRequested = true;
                } else {
                    setStatus("Ingredients placed", 0xFF206020);
                    closeRequested = true; // NEI closes the recipe window once the recipe is in the grid
                }
                return;
            }
        }

        // heart: favorite recipe
        for (Heart h : cut ? List.<Heart>of() : heartHits) {
            if (button == 0 && Draw.in(mx, my, h.x(), h.y(), BTN, BTN)) {
                RecipeBookmarks.toggleFavorite(h.view(), h.view() == choiceView ? choice : null);
                return;
            }
        }

        // items: left = recipes, right = uses
        if (button == 0 || button == 1) {
            for (Hit hit : slotHits) {
                if (hit.at(mx, my) && !(cut && hit.x() >= left)) {
                    show(hit.stack().getItem(), button == 1, true);
                    return;
                }
            }
        }
    }

    public void scrolled(double mx, double my, double scrollY) {
        for (Heart h : heartHits) {
            if (h.view() == choiceView && Draw.in(mx, my, h.x(), h.y(), BTN, BTN)) { // NEI: the wheel over the heart changes its result
                final List<String> choices = RecipeBookmarks.favoriteChoices(choiceView);
                if (choices.size() > 1) {
                    final int at = Math.max(0, choices.indexOf(choice));
                    choice = choices.get((choices.size() + at + (scrollY > 0 ? -1 : 1)) % choices.size());
                }
                return;
            }
        }
        final int dir = scrollY > 0 ? -1 : 1;
        // NEI's GuiRecipe#mouseScrolled, in its order: the tab bar, the machines column, the recipe list, Shift or the title
        // row (recipe type), then the recipes (with Scroll Pages) or the page row (pages)
        final int th = tabH();
        if (tabsShown() && my >= top - th + 3 && my < top + 3 && mx >= left + 4 && mx < left + 4 + tabsPerRow() * tabW()) {
            selectTab(Math.max(0, Math.min(tab + dir, groups.size() - 1)));
            return;
        }
        if (catH > 0 && Draw.in(mx, my, catX, catY, catW, catH)) {
            catScroll = Math.max(0, Math.min(catScroll + dir * 16, catContent - (catH - 14)));
            return;
        }
        if (infinite() && inArea(mx, my) && maxScroll() > 0) {
            scrollPx = Math.max(0, Math.min(scrollPx + dir * 18, maxScroll()));
            return;
        }
        if (HotkeyHelp.isShiftDown() || Draw.in(mx, my, left, top + 3, W, NAV_H)) {
            selectTab(tab + dir);
            return;
        }
        final boolean overPageRow = Draw.in(mx, my, left, top + 17, W, NAV_H) && !Draw.in(mx, my, left + 5 + NAV_W, top + 17, 12, 12)
                && !(searchOn && Draw.in(mx, my, left + 30, top + 16, SEARCH_W, 14));
        if (NEIConfig.RECIPE_SCROLL_PAGES.getAsBoolean() && inArea(mx, my) || overPageRow) changePage(dir);
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
        pendingFocus = nav.focus();
        show(nav.item(), nav.uses(), false);
        return true;
    }

    /** NEI's recipe keys: previous / next recipe page and machine (recipe type). Returns true if the key was used. */
    public boolean pageKey(InputConstants.Key key) {
        if (NEIConfig.RECIPE_HANDLER_INFO.getAsBoolean() && key.getValue() == org.lwjgl.glfw.GLFW.GLFW_KEY_D
                && HotkeyHelp.isShiftDown()) {
            infoShown = !infoShown; // NEI's DebugHandlerWidget: Shift + D
            return true;
        }
        if (NEIKeys.is(NEIKeys.PREV_RECIPE, key)) changePage(-1);
        else if (NEIKeys.is(NEIKeys.NEXT_RECIPE, key)) changePage(1);
        else if (NEIKeys.is(NEIKeys.PREV_MACHINE, key)) selectTab(tab - 1);
        else if (NEIKeys.is(NEIKeys.NEXT_MACHINE, key)) selectTab(tab + 1);
        else return false;
        return true;
    }

    /** NEI's {@code bookmark.favorite_item}: Shift + key over an ingredient that has alternatives favorites that item. */
    public boolean favoriteItemKey() {
        if (!codechicken.nei.NEIConfig.FAVORITES_ENABLED.getAsBoolean() || hoveredView == null || hovered.isEmpty()) return false;
        for (SlotView s : hoveredView.slots()) {
            if (s.options().size() > 1 && s.options().stream().anyMatch(o -> o.getItem() == hovered.getItem())) {
                RecipeBookmarks.toggleFavoriteItem(codechicken.nei.item.FluidDisplay.idOf(hovered));
                return true;
            }
        }
        // a result of the recipe: favorite this recipe for it (no mouse wheel needed; the item's own favorite wins in trees)
        final String id = codechicken.nei.item.FluidDisplay.idOf(hovered);
        if (RecipeBookmarks.favoriteChoices(hoveredView).contains(id)) {
            final boolean was = RecipeBookmarks.isFavoriteFor(hoveredView, id);
            RecipeBookmarks.toggleFavorite(hoveredView, id);
            setStatus(was ? "Favorite recipe removed" : "Favorite recipe for " + hovered.getHoverName().getString(), 0xFF206020);
            return true;
        }
        return false;
    }

}
