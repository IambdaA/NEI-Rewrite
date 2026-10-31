package codechicken.nei.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.mojang.blaze3d.platform.InputConstants;

import codechicken.nei.Bookmarks;
import codechicken.nei.Favorites;
import codechicken.nei.recipe.RecipeView;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.ItemList;
import codechicken.nei.NEIActions;
import codechicken.nei.NEIConfig;
import codechicken.nei.NotEnoughItems;
import codechicken.nei.Subsets;
import codechicken.nei.item.ItemEntry;
import codechicken.nei.HistoryFile;
import codechicken.nei.item.TextHistory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Hooks NEI's UI into container screens (including the creative inventory):
 * item list on the right, bookmarks on the left, recipe window in the middle.
 *
 * Mouse: left click an item = its recipes, right click = its uses, shift + click = give it (needs cheats / creative).
 * Keys over an item: R recipes, U uses, A bookmark, B show/hide the bookmark panel, Backspace back (in the recipe
 * window), Escape closes the recipe window. T over a crafting chain group's strip opens the crafting tree window
 * (Shift + T over a recipe's heart saves the recipe tree and opens it); Escape or the inventory key leaves it.
 *
 */
public final class NEIClientEvents {

    private static final int GLFW_KEY_ESCAPE = 256;

    private static final ItemPanel ITEMS = new ItemPanel(ItemList::getFiltered, false).withCollapsible();
    private static final BookmarkPanel BOOKMARKS = new BookmarkPanel("Hover an item,", "press the", "bookmark key", "Alt = help");

    /** NEI's item history: the items whose recipes or uses were looked up, newest first (max 50). */
    private static final List<ItemEntry> HISTORY_LIST = new ArrayList<>();
    private static String historyKey;
    private static final ItemPanel HISTORY = new ItemPanel(() -> HISTORY_LIST, false).withoutNav();

    /** NEI's bottom bar: wrench (config) and book (bookmark panel on / off), 20 x 20 with 2 pixels between. */
    private static final int BAR = 20;

    private static boolean bookmarksVisible = true;
    private static RecipeOverlay overlay;
    private static CraftingTreeOverlay tree;
    private static ModalWindow modalWin;
    private static EditBox searchBox;

    /** What is in the item search, or null while the search field does not exist (no panel on this screen). */
    static String searchText() {
        return searchBox == null ? null : searchBox.getValue();
    }

    /** Puts text into the item search (NEI's {@code searchField.setText}). */
    static void searchFor(String text) {
        if (searchBox != null) searchBox.setValue(text);
    }
    /** Set when a key focused the search field: its character must not be typed into it. */
    private static boolean skipChar;
    private static int layoutKey;
    private static int historyTop;
    // NEI's craftables panel: above the history panel, same dashed line and a grid of up to craftablesRows rows
    private static final ItemPanel CRAFTABLES = new ItemPanel(Craftables::items, false).withoutNav()
            .withAmounts(Craftables::amount);
    private static int craftablesTop;
    // NEI's item quantity field: the bottom 20 pixels of the item list's area
    private static final QuantityBar QUANTITY = new QuantityBar();
    private static boolean quantityShown;
    private static int panelLeft;
    private static int panelRight;
    private static String lastQuery = "";
    private static int lastMouseX;
    private static int lastMouseY;

    private NEIClientEvents() {}

    /** NEI's {@code gui.hide}: while set, NEI draws and handles nothing except the key that brings it back. */
    // shortcut: not saved between sessions (NEI keeps inventory.hidden in its config), upgrade if that is missed
    private static boolean hidden;
    private static final TextHistory SEARCH_HISTORY = new TextHistory();
    private static boolean searchWasFocused;

    private static boolean activeScreen(Screen screen) {
        return NEIConfig.PANEL_ENABLED.getAsBoolean() && screen instanceof AbstractContainerScreen<?>;
    }

    private static boolean active(Screen screen) {
        return !hidden && activeScreen(screen);
    }

    /** The open recipe window, if it belongs to this screen. Drops it if the player moved to another screen. */
    private static RecipeOverlay overlayFor(Screen screen) {
        if (overlay != null && overlay.screen() != screen) overlay = null;
        return overlay;
    }

    /** The open presets window, if it belongs to this screen. */
    private static ModalWindow presetsFor(Screen screen) {
        if (modalWin != null && (modalWin.screen() != screen || modalWin.wantsClose())) modalWin = null;
        return modalWin;
    }

    /** The open crafting tree window, if it belongs to this screen. Drops it if the player moved or the group is gone. */
    private static CraftingTreeOverlay treeFor(Screen screen) {
        if (tree != null && (tree.screen() != screen || !tree.alive())) {
            tree = null;
            overlay = null;
        }
        return tree;
    }

    private static void openTree(Screen screen, int groupId) {
        overlay = null;
        tree = new CraftingTreeOverlay(screen, Bookmarks.grid(), groupId, (item, uses) -> openRecipes(screen, item, uses));
    }

    /** NEI's {@code ItemHistoryPanel#addItem}: newest first, no repeats, at most 50. */
    private static void addHistory(Item item) {
        loadHistory(); // so a first lookup does not overwrite the saved list
        final ItemEntry entry = ItemList.find(codechicken.nei.item.FluidDisplay.idOf(item));
        if (entry == null) return;
        HISTORY_LIST.removeIf(e -> e.stack.getItem() == item);
        HISTORY_LIST.add(0, entry);
        if (HISTORY_LIST.size() > 50) HISTORY_LIST.remove(HISTORY_LIST.size() - 1);
        if (NEIConfig.HISTORY_SAVE.getAsBoolean()) {
            HistoryFile.write(HISTORY_LIST.stream().map(e -> e.registryId).toList());
        }
    }

    /** Reads the saved history once the item list exists (a saved item that no longer exists is dropped). */
    private static void loadHistory() {
        if (!NEIConfig.HISTORY_SAVE.getAsBoolean()) return;
        // compared on every call instead of listening for the login: a different world (or the option being switched) reloads
        final String key = HistoryFile.key();
        if (key.equals(historyKey)) return;
        historyKey = key;
        HISTORY_LIST.clear();
        for (String id : HistoryFile.read()) {
            final ItemEntry e = ItemList.find(id.trim());
            if (e != null && HISTORY_LIST.size() < 50 && !HISTORY_LIST.contains(e)) HISTORY_LIST.add(e);
        }
    }

    private static void openRecipes(Screen screen, Item item, boolean uses) {
        addHistory(item);
        final RecipeOverlay open = overlayFor(screen);
        if (open != null) open.show(item, uses, true);
        else overlay = new RecipeOverlay(screen, item, uses);

        // opened from the crafting tree, the recipe window's "+" picks the recipe for the tree
        final CraftingTreeOverlay treeWindow = treeFor(screen);
        overlay.setPicker(treeWindow != null ? treeWindow::addRecipe : null);
    }
    public static void onInit(ScreenEv event) {
        if (!active(event.getScreen())) return;
        // The layout itself happens every frame in onRender: opening the inventory in creative mode swaps screens inside
        // init(), so this event also arrives for the discarded survival screen, whose values (guiLeft 0, width 176) are
        // wrong. Only reset state here.
        HotkeyHelp.reset();
        ItemZoom.reset();
        QUANTITY.blur();
        if (!ItemList.isLoaded()) ItemList.load();
        loadHistory();
        layoutKey = 0;
        SubsetMenu.close();
        if (searchBox != null) searchBox.setFocused(false);
    }

    /**
     * Lays the panels out from the screen's current size. Runs every frame (it is cheap), so a screen whose values
     * were not final at init time (the creative inventory) still ends up right.
     */
    private static void layout(AbstractContainerScreen<?> screen) {
        final int guiLeft = screen.leftPos;
        final int right = guiLeft + screen.imageWidth + 6;
        final int cols = Math.max(0, (screen.width - 4 - right) / ItemPanel.SLOT);

        // history: dash line + up to two rows, under the item list (NEI: 8 + 18 * rows)
        final int rows = HISTORY_LIST.isEmpty() || cols == 0 || !NEIConfig.HISTORY_ENABLED.getAsBoolean() ? 0
                : Math.min(NEIConfig.HISTORY_ROWS.getAsInt(), (HISTORY_LIST.size() + cols - 1) / cols);
        final int h = rows == 0 ? 0 : 8 + 18 * rows;
        historyTop = screen.height - 6 - h;
        panelLeft = right;
        panelRight = screen.width - 4;

        Craftables.update(screen, cols);
        final int craftRows = Craftables.rows(cols);
        craftablesTop = historyTop - (craftRows == 0 ? 0 : 8 + 18 * craftRows);

        // item list: page row on top, grid below it, the quantity field at the bottom
        final int listBottom = craftablesTop - (rows + craftRows == 0 ? 0 : 2);
        final int quantityH = QuantityBar.enabled() && cols >= 3 ? QuantityBar.H : 0;
        ITEMS.layout(right, panelRight, 6, listBottom - quantityH);
        quantityShown = quantityH > 0 && ITEMS.isVisible();
        QUANTITY.layout(right, listBottom - QuantityBar.H, cols * ItemPanel.SLOT);
        HISTORY.layout(right, panelRight, historyTop + 4, historyTop + 4 + 18 * rows);
        CRAFTABLES.layout(right, panelRight, craftablesTop + 4, craftablesTop + 4 + 18 * craftRows);

        // bookmarks: page row on top, grid fills the left edge, page ("namespace") row at the bottom; the wrench and
        // book buttons take the bottom left corner
        final int toolbar = Toolbar.bottom(screen.width, screen.imageWidth);
        final int key = Objects.hash(guiLeft, screen.width, screen.height, toolbar);
        if (key != layoutKey) {
            layoutKey = key;
            BOOKMARKS.layout(4, guiLeft - 6, toolbar == 0 ? 6 : toolbar + 4, screen.height - BAR - 6);
        }

        // NEI's search field: centered at the bottom, as wide as the window (at most 176) minus 4
        final int fullW = Math.min(screen.imageWidth, 176) - 4;
        final int fullX = (screen.width - fullW) / 2;
        final int by = screen.height - BAR - 2;
        // subsets.widgetPosition off: the subsets button takes the left end of the search field
        final int sub = subsetsAtSearch(screen) ? BAR : 0;
        final int bx = fullX + sub;
        final int bw = fullW - sub;
        if (searchBox == null) {
            searchBox = new EditBox(Minecraft.getInstance().font, bx, by, bw, BAR, Component.empty());
            SearchHighlight.apply(searchBox);
            searchBox.setMaxLength(100);
            searchBox.setHint(Component.translatable("nei.search.hint"));
            searchBox.setValue(lastQuery);
            searchBox.setResponder(text -> {
                lastQuery = text;
                ItemList.updateFilter(text);
                ITEMS.resetPage();
            });
        } else {
            searchBox.setX(bx);
            searchBox.setY(by);
            searchBox.setWidth(bw);
        }
    }

    /** NEI shows the subset bar when there is room above the window. */
    private static boolean subsetsShown(AbstractContainerScreen<?> screen) {
        return NEIConfig.SUBSETS_ENABLED.getAsBoolean() && (screen.topPos > 20 || subsetsAtSearch(screen))
                && !Subsets.root().children.isEmpty();
    }

    /** NEI's {@code subsetWidgetOnTop} off: the button sits beside the search field and needs no room above the window. */
    private static boolean subsetsAtSearch(AbstractContainerScreen<?> screen) {
        return NEIConfig.SUBSETS_ENABLED.getAsBoolean() && !NEIConfig.SUBSETS_ON_TOP.getAsBoolean()
                && !Subsets.root().children.isEmpty();
    }

    private static boolean overWrench(double mx, double my, int sh) {
        return Draw.in(mx, my, 2, sh - BAR - 2, BAR, BAR);
    }

    private static boolean overBook(double mx, double my, int sh) {
        return Draw.in(mx, my, 2 + BAR + 2, sh - BAR - 2, BAR, BAR);
    }

    /** The presets button (NEI has it in its options screen; here it sits on the bottom bar as a "P"). */
    private static boolean overPresets(double mx, double my, int sh) {
        return Draw.in(mx, my, 2 + 2 * (BAR + 2), sh - BAR - 2, BAR, BAR);
    }

    /** NEI's wrench and book buttons (icons from its sprite sheet). */
    private static void drawBar(net.minecraft.client.gui.GuiGraphicsExtractor g, int mx, int my, int sh) {
        final int y = sh - BAR - 2;
        final boolean wrench = overWrench(mx, my, sh);
        final boolean book = overBook(mx, my, sh);
        Draw.button(g, 2, y, BAR, BAR, wrench);
        Draw.button(g, 2 + BAR + 2, y, BAR, BAR, book);
        Tex.blit(g, Tex.SPRITES, 256, 256, 4, y + 2, 32, 0, 16, 16);
        Tex.blit(g, Tex.SPRITES, 256, 256, 2 + BAR + 4, y + 2, bookmarksVisible ? 16 : 0, 0, 16, 16);

        final boolean presets = overPresets(mx, my, sh);
        Draw.button(g, 2 + 2 * (BAR + 2), y, BAR, BAR, presets);
        g.text(Minecraft.getInstance().font, Component.literal("P"), 2 + 2 * (BAR + 2) + 7, y + 6, 0xFFFFFFFF, true);

        if (presets) {
            ItemTooltip.requestPlain(List.of(Component.literal("Presets List")));
        } else if (wrench) {
            ItemTooltip.requestPlain(List.of(Component.literal("NEI Config"),
                    Component.literal(NEIConfig.CHEAT_ENABLED.getAsBoolean() ? "Cheat Mode" : "Recipe Mode")));
        } else if (book) {
            ItemTooltip.requestPlain(List.of(Component.literal("Toggle visibility of the Bookmark Panel")));
        }
    }


    /** Opens a screen (26.2 moved {@code setScreen} from Minecraft to its Gui; 26.1.2 still has it on Minecraft). */
    private static void setScreen(Screen next) {
        Minecraft.getInstance().setScreen(next);
    }

    /** The tree window covers the whole screen: skip drawing the container (and its slot tooltips) underneath. */
    public static void onRenderPre(ScreenEv event) {
        // Cancelling Render.Pre skips drawing the screen itself.
        final Screen screen = event.getScreen();
        if (active(screen) && treeFor(screen) != null) event.setCanceled(true);
    }
    public static void onRender(ScreenEv event) {
        final Screen screen = event.getScreen();
        if (!active(screen)) return;

        final var graphics = event.getGuiGraphics();
        final int mx = (int) event.getMouseX();
        final int my = (int) event.getMouseY();
        layout((AbstractContainerScreen<?>) screen);
        // NEI remembers the search text whenever the box gains or loses focus
        if (searchBox != null && searchBox.isFocused() != searchWasFocused) {
            searchWasFocused = searchBox.isFocused();
            SEARCH_HISTORY.add(searchBox.getValue());
        }
        lastMouseX = mx;
        lastMouseY = my;
        ItemTooltip.begin();

        final RecipeOverlay open = overlayFor(screen);
        final CraftingTreeOverlay treeWindow = treeFor(screen);

        NEIConfig.flush();
        if (treeWindow != null) {
            ItemZoom.reset();
            // the tree window replaces the panels; a recipe window opened from it goes on top
            treeWindow.render(graphics, mx, my, screen.width, screen.height, open != null && open.contains(mx, my));
            if (open != null) {
                graphics.fill(0, 0, screen.width, screen.height, 0x90000000);
                open.render(graphics, mx, my, screen.width, screen.height);
            }
            ItemTooltip.flush(graphics, mx, my, screen.width, screen.height);
            return;
        }

        final ModalWindow modalWindow = presetsFor(screen);
        if (modalWindow != null) {
            ItemZoom.reset();
            modalWindow.render(graphics, mx, my, screen.width, screen.height);
            ItemTooltip.flush(graphics, mx, my, screen.width, screen.height);
            return;
        }

        if (open != null) graphics.fill(0, 0, screen.width, screen.height, 0x90000000); // dim the inventory

        ITEMS.render(graphics, mx, my, screen.width, screen.height);
        if (quantityShown) QUANTITY.render(graphics, mx, my);
        if (!Craftables.items().isEmpty() && CRAFTABLES.isVisible()) {
            final int color = NEIConfig.color(NEIConfig.CRAFTABLES_COLOR);
            for (int dx = panelLeft; dx < panelRight; dx += 3) {
                graphics.fill(dx, craftablesTop, Math.min(dx + 2, panelRight), craftablesTop + 1, color);
            }
            CRAFTABLES.render(graphics, mx, my, screen.width, screen.height);
        }
        if (!HISTORY_LIST.isEmpty() && HISTORY.isVisible() && NEIConfig.HISTORY_ENABLED.getAsBoolean()) {
            // NEI draws a dashed line (nei:textures/dash.png, colour inventory.history.color) above the history grid
            final int historyColor = NEIConfig.color(NEIConfig.HISTORY_COLOR);
            for (int dx = panelLeft; dx < panelRight; dx += 3) {
                graphics.fill(dx, historyTop, Math.min(dx + 2, panelRight), historyTop + 1, historyColor);
            }
            HISTORY.render(graphics, mx, my, screen.width, screen.height);
        }
        if (bookmarksVisible) BOOKMARKS.render(graphics, mx, my);
        Toolbar.render(graphics, mx, my, (AbstractContainerScreen<?>) screen);
        drawBar(graphics, mx, my, screen.height);
        if (subsetsShown((AbstractContainerScreen<?>) screen)) {
            final var container = (AbstractContainerScreen<?>) screen;
            final int fullW = Math.min(container.imageWidth, 176) - 4;
            SubsetMenu.render(graphics, mx, my, screen.width, screen.height, !subsetsAtSearch(container),
                    (screen.width - fullW) / 2, screen.height - BAR - 2, BAR);
        }
        else SubsetMenu.close();
        searchBox.extractRenderState(graphics, mx, my, event.getPartialTick());

        if (open != null) {
            open.render(graphics, mx, my, screen.width, screen.height);
        } else if (HotkeyHelp.isAltDown() && screen instanceof AbstractContainerScreen<?> container) {
            // hotkey help for items in the container's own slots (panel items draw theirs themselves)
            final var slot = container.hoveredSlot;
            if (slot != null && ITEMS.entryAt(mx, my) == null && !slot.getItem().isEmpty()) {
                ItemTooltip.request(slot.getItem(), HotkeyHelp.Where.SLOT);
            }
        }

        // As in NEI, the zoomed item is drawn over everything but the tooltip
        final var container = (AbstractContainerScreen<?>) screen;
        ItemZoom.update(container, NEIConfig.ZOOM_NEI_ONLY.getAsBoolean() ? panelStackAt(mx, my) : hoveredStack(screen), mx,
                bookmarksVisible && BOOKMARKS.isVisible() ? Bookmarks.grid().size() : 0,
                ITEMS.isVisible() ? ItemList.getFiltered().size() : 0);
        ItemZoom.render(graphics, container);

        // the tooltip goes last so nothing paints over it
        ItemTooltip.flush(graphics, mx, my, screen.width, screen.height);
    }

    private static ItemStack panelStackAt(double mx, double my) {
        final ItemEntry entry = panelEntryAt(mx, my);
        return entry == null ? ItemStack.EMPTY : entry.stack;
    }

    /** The panel item under the point (either panel), or null. */
    private static ItemEntry panelEntryAt(double mx, double my) {
        if (ITEMS.contains(mx, my)) return ITEMS.entryAt(mx, my);
        if (HISTORY.contains(mx, my)) return HISTORY.entryAt(mx, my);
        if (CRAFTABLES.contains(mx, my)) return CRAFTABLES.entryAt(mx, my);
        if (bookmarksVisible && BOOKMARKS.contains(mx, my)) return BOOKMARKS.entryAt(mx, my);
        return null;
    }

    /**
     * Left / right click on a panel item. As in NEI ({@code PanelWidget#mouseUp}): while cheating, a plain click gives
     * the item and Shift + click opens recipes / uses; otherwise (cheats off, or the recipe window is open) a click
     * opens recipes (left) / uses (right).
     */
    private static void clickEntry(Screen screen, ItemEntry entry, int button, boolean shift) {
        if (entry == null || (button != 0 && button != 1)) return;
        if (!shift && overlayFor(screen) == null && NEIConfig.CHEAT_ENABLED.getAsBoolean()
                && !codechicken.nei.item.FluidDisplay.is(entry.stack)) {
            // NEI: a left click gives the quantity field's amount (a full stack when it is empty), a right click gives one
            NEIActions.give(entry, button == 1 ? 1 : QuantityBar.multiplier(entry.stack.getMaxStackSize()), screen);
        } else {
            RecipeOverlay.focusNext(entry.stack);
            openRecipes(screen, entry.stack.getItem(), button == 1);
        }
    }
    public static void onMousePressed(ScreenEv event) {
        final Screen screen = event.getScreen();
        if (!active(screen)) return;

        final double mx = event.getMouseX();
        final double my = event.getMouseY();
        final int button = event.getButton();
        final var mouse = event.getMouseButtonEvent();

        final CraftingTreeOverlay treeWindow = treeFor(screen);
        if (treeWindow != null) {
            event.setCanceled(true); // nothing reaches the inventory behind the tree
            final RecipeOverlay recipeWindow = overlayFor(screen);
            if (recipeWindow == null) {
                treeWindow.mouseClicked(mx, my, button);
            } else if (recipeWindow.contains(mx, my)) {
                recipeWindow.mouseClicked(mx, my, button);
                if (recipeWindow.wantsClose()) overlay = null; // a recipe was picked for the tree
            } else {
                overlay = null; // click outside the recipe window closes it
            }
            return;
        }

        final ModalWindow modalWindow = presetsFor(screen);
        if (modalWindow != null) {
            event.setCanceled(true); // the presets window is modal
            modalWindow.click(mx, my, button, HotkeyHelp.isCtrlDown(), HotkeyHelp.isShiftDown());
            return;
        }

        if (searchBox != null) {
            if (searchBox.isMouseOver(mx, my)) {
                QUANTITY.blur();
                if (button == 1) {
                    searchBox.setValue(""); // right click clears, as in NEI
                } else {
                    searchBox.mouseClicked(mouse, false);
                }
                searchBox.setFocused(true);
                event.setCanceled(true);
                return;
            }
            searchBox.setFocused(false);
        }

        if (quantityShown ? QUANTITY.click(mx, my, button, HotkeyHelp.isCtrlDown(), HotkeyHelp.isShiftDown())
                : blurQuantity()) {
            if (QUANTITY.focused() && searchBox != null) searchBox.setFocused(false);
            event.setCanceled(true);
            return;
        }

        final RecipeOverlay open = overlayFor(screen);

        // The recipe window is modal over the container, but the side panels stay usable.
        if (open != null && open.contains(mx, my)) {
            open.mouseClicked(mx, my, button);
            if (open.wantsClose()) overlay = null; // the "+" put the recipe in the grid
            event.setCanceled(true);
            return;
        }

        final boolean shift = mouse.hasShiftDown();

        if (subsetsShown((AbstractContainerScreen<?>) screen) && SubsetMenu.click(mx, my, button)) {
            event.setCanceled(true);
            return;
        }

        if (Toolbar.click(mx, my, button, (AbstractContainerScreen<?>) screen)) {
            event.setCanceled(true);
            return;
        }

        // NEI's delete mode: a click on a slot deletes instead of picking up
        if (Deleting.modeOn(screen)) {
            final var slot = ((AbstractContainerScreen<?>) screen).hoveredSlot;
            if (slot != null) {
                Deleting.modeClick((AbstractContainerScreen<?>) screen, slot, button);
                event.setCanceled(true);
                return;
            }
        }

        if (button == 0 && overPresets(mx, my, screen.height)) {
            event.setCanceled(true);
            QUANTITY.blur();
            if (searchBox != null) searchBox.setFocused(false);
            overlay = null;
            modalWin = new PresetsWindow(screen);
            return;
        }

        if (button == 0 && (overWrench(mx, my, screen.height) || overBook(mx, my, screen.height))) {
            event.setCanceled(true);
            if (overBook(mx, my, screen.height)) bookmarksVisible = !bookmarksVisible;
            else {
                QUANTITY.blur();
                if (searchBox != null) searchBox.setFocused(false);
                overlay = null;
                modalWin = new SettingsWindow(screen, null);
            }
            return;
        }

        if (CRAFTABLES.contains(mx, my)) {
            event.setCanceled(true);
            clickEntry(screen, CRAFTABLES.entryAt(mx, my), button, shift);
            return;
        }

        if (HISTORY.contains(mx, my)) {
            event.setCanceled(true);
            clickEntry(screen, HISTORY.entryAt(mx, my), button, shift);
            return;
        }

        if (bookmarksVisible && BOOKMARKS.contains(mx, my)) {
            event.setCanceled(true);
            if (BOOKMARKS.pressed(mx, my, button, shift)) return; // group strip: drag to group, click to change view
            if (BOOKMARKS.mouseClicked(mx, my, button)) return; // page buttons
            if (button == 0 && HotkeyHelp.isAltDown() && BOOKMARKS.altClicked(mx, my)) return;
            clickEntry(screen, BOOKMARKS.entryAt(mx, my), button, shift);
            return;
        }

        if (ITEMS.contains(mx, my)) {
            event.setCanceled(true);
            if (ITEMS.mouseClicked(mx, my, button)) return;
            if (button == 0 && HotkeyHelp.isAltDown() && ITEMS.altClicked(mx, my)) return;
            clickEntry(screen, ITEMS.entryAt(mx, my), button, shift);
            return;
        }

        if (open != null) {
            // click outside everything: close the recipe window instead of poking the inventory behind it
            overlay = null;
            event.setCanceled(true);
            return;
        }

        // NEI's Shift + click mass transfer and throw-all (stage 38); anything else goes on to vanilla
        final var container = (AbstractContainerScreen<?>) screen;
        if (shift && FastTransfer.shiftClick(container, mx, my, button)) {
            event.setCanceled(true);
            return;
        }
        FastTransfer.before(container.getMenu());
    }

    /** NEI's afterSlotClick: notes where the cursor stack was picked up (see FastTransfer). */
    public static void onMousePressedPost(ScreenEv event) {
        if (!active(event.getScreen())) return;
        final var container = (AbstractContainerScreen<?>) event.getScreen();
        final var slot = container.hoveredSlot;
        FastTransfer.after(container.getMenu(), slot == null ? -999 : container.getMenu().slots.indexOf(slot));
    }

    private static boolean blurQuantity() {
        QUANTITY.blur();
        return false;
    }
    public static void onMouseReleased(ScreenEv event) {
        if (!active(event.getScreen())) return;
        final CraftingTreeOverlay treeWindow = treeFor(event.getScreen());
        if (treeWindow != null) {
            treeWindow.mouseReleased();
            event.setCanceled(true);
            return;
        }
        final ModalWindow modalOpen = presetsFor(event.getScreen());
        if (modalOpen != null) {
            modalOpen.released();
            event.setCanceled(true);
            return;
        }
        if (BOOKMARKS.released(event.getMouseX(), event.getMouseY(), event.getButton(), HotkeyHelp.isAltDown())) {
            event.setCanceled(true);
            // a Shift + click on a bookmark that did not turn into a sorting drag is a click after all
            clickEntry(event.getScreen(), BOOKMARKS.takeDeferredClick(), 0, true);
        }
    }
    public static void onMouseScrolled(ScreenEv event) {
        final Screen screen = event.getScreen();
        if (!active(screen)) return;

        final double mx = event.getMouseX();
        final double my = event.getMouseY();
        final double dy = event.getScrollDeltaY();

        final CraftingTreeOverlay treeWindow = treeFor(screen);
        if (treeWindow != null) {
            event.setCanceled(true);
            final RecipeOverlay recipeWindow = overlayFor(screen);
            if (recipeWindow != null && recipeWindow.contains(mx, my)) {
                recipeWindow.scrolled(mx, my, dy);
            } else {
                treeWindow.scrolled(mx, my, dy, HotkeyHelp.isCtrlDown(), HotkeyHelp.isShiftDown(),
                        HotkeyHelp.isAltDown());
            }
            return;
        }

        final ModalWindow modalWindow = presetsFor(screen);
        if (modalWindow != null) {
            event.setCanceled(true);
            modalWindow.scrolled(mx, my, dy);
            return;
        }

        final RecipeOverlay open = overlayFor(screen);
        if (open != null && open.contains(mx, my)) {
            open.scrolled(mx, my, dy);
            event.setCanceled(true);
            return;
        }

        if (SubsetMenu.scrolled(mx, my, dy)) {
            event.setCanceled(true);
            return;
        }

        if (quantityShown && QUANTITY.scrolled(mx, my, dy, HotkeyHelp.isCtrlDown(), HotkeyHelp.isShiftDown())) {
            event.setCanceled(true);
            return;
        }

        // As in NEI, the wheel over a slot of the container moves one item (see FastTransfer); the panels come first
        if (!ITEMS.contains(mx, my) && !HISTORY.contains(mx, my) && !CRAFTABLES.contains(mx, my)
                && !(bookmarksVisible && BOOKMARKS.contains(mx, my)) && open == null
                && FastTransfer.wheel((AbstractContainerScreen<?>) screen, dy)) {
            event.setCanceled(true);
            return;
        }

        // bookmarks: wheel over the page buttons switches page, Ctrl + wheel changes an amount, else turns the grid page
        if (bookmarksVisible && BOOKMARKS.scrolled(mx, my, dy, HotkeyHelp.isCtrlDown())) {
            event.setCanceled(true);
            return;
        }
        if (ITEMS.contains(mx, my)) {
            ITEMS.changePage(dy > 0 ? -1 : 1);
            event.setCanceled(true);
        }
    }
    public static void onKeyPressed(ScreenEv event) {
        final Screen screen = event.getScreen();
        if (!activeScreen(screen)) return;

        final var key = event.getKeyEvent();
        HotkeyHelp.track(key.key(), true);
        skipChar = false; // the character of the key that focused the search arrives before the next key press

        final InputConstants.Key pressed = InputConstants.getKey(key);

        if (hidden) {
            if (NEIKeys.is(NEIKeys.HIDE, pressed) && !(screen.getFocused() instanceof EditBox)) {
                hidden = false;
                event.setCanceled(true);
            }
            return;
        }
        final CraftingTreeOverlay treeWindow = treeFor(screen);

        final ModalWindow modalWindow = presetsFor(screen);
        if (modalWindow != null) {
            event.setCanceled(true);
            if (key.key() == GLFW_KEY_ESCAPE) modalWindow.escape();
            else if (NEIKeys.is(NEIKeys.PREV_SEARCH, pressed) && modalWindow.historyKey(true)) return;
            else if (NEIKeys.is(NEIKeys.NEXT_SEARCH, pressed) && modalWindow.historyKey(false)) return;
            else if (modalWindow.typingBox() != null) modalWindow.typingBox().keyPressed(key);
            return;
        }

        // typing in the recipe window's search field
        final RecipeOverlay typing = overlayFor(screen);
        if (typing != null && typing.searchFocused()) {
            event.setCanceled(true);
            if (key.key() == GLFW_KEY_ESCAPE) typing.blurSearch();
            else if (NEIKeys.is(NEIKeys.PREV_SEARCH, pressed)) typing.walkHistory(true);
            else if (NEIKeys.is(NEIKeys.NEXT_SEARCH, pressed)) typing.walkHistory(false);
            else typing.searchBox().keyPressed(key);
            return;
        }

        if (treeWindow != null) {
            event.setCanceled(true); // the inventory behind the tree gets no keys (Q, number keys, E ...)
            // As in NEI, Escape and the inventory key leave the tree (or first the recipe window opened from it)
            // (while the tree's search field has focus the inventory key is just a letter)
            if (key.key() == GLFW_KEY_ESCAPE || !treeWindow.searchFocused()
                    && NEIKeys.is(Minecraft.getInstance().options.keyInventory, pressed)) {
                if (overlayFor(screen) != null) overlay = null;
                else tree = null;
                return;
            }
            if (treeWindow.searchFocused()) {
                if (key.key() == 257 || key.key() == 335) treeWindow.searchNext(!HotkeyHelp.isShiftDown()); // Enter, keypad Enter
                else treeWindow.searchBox().keyPressed(key);
                return;
            }
        } else {
            if (QUANTITY.focused()) {
                if (key.key() == GLFW_KEY_ESCAPE) {
                    QUANTITY.blur();
                    return; // let Escape close the screen
                }
                QUANTITY.key(key.key());
                event.setCanceled(true); // typing must not reach the inventory (E closes it, number keys move items)
                return;
            }

            if (searchBox != null && searchBox.isFocused()) {
                if (key.key() == GLFW_KEY_ESCAPE) {
                    searchBox.setFocused(false);
                    return; // let Escape close the screen
                }
                // As in NEI: Up / Down walk the search history
                final boolean older = NEIKeys.is(NEIKeys.PREV_SEARCH, pressed);
                if (older || NEIKeys.is(NEIKeys.NEXT_SEARCH, pressed)) {
                    final String text = searchBox.getValue();
                    (older ? SEARCH_HISTORY.previous(text) : SEARCH_HISTORY.next(text)).ifPresent(searchBox::setValue);
                    event.setCanceled(true);
                    return;
                }
                searchBox.keyPressed(key);
                event.setCanceled(true); // otherwise the inventory key would close the screen while typing
                return;
            }

            // Any other text box (e.g. the creative inventory's own search) keeps its keys.
            if (screen.getFocused() instanceof EditBox) return;
        }

        // NEI's gui.search: the key (without modifiers; Shift + F is the favorite key) focuses the search field
        if (treeWindow == null && searchBox != null && active(screen) && !HotkeyHelp.isShiftDown()
                && !HotkeyHelp.isCtrlDown() && NEIKeys.is(NEIKeys.FOCUS_SEARCH, pressed)) {
            searchBox.setFocused(true);
            skipChar = true; // the "f" that follows must not be typed into the field
            event.setCanceled(true);
            return;
        }

        // NEI's Shift + drop key over a slot: throw every stack of that kind in the zone (see FastTransfer)
        if (treeWindow == null && HotkeyHelp.isShiftDown() && NEIKeys.is(Minecraft.getInstance().options.keyDrop, pressed)
                && FastTransfer.shiftDrop((AbstractContainerScreen<?>) screen)) {
            event.setCanceled(true);
            return;
        }

        // NEI's gui.hide
        if (treeWindow == null && NEIKeys.is(NEIKeys.HIDE, pressed)) {
            hidden = true;
            overlay = null;
            modalWin = null;
            SubsetMenu.close();
            event.setCanceled(true);
            return;
        }

        final RecipeOverlay open = overlayFor(screen);
        if (open != null) {
            if (key.key() == GLFW_KEY_ESCAPE) {
                overlay = null;
                event.setCanceled(true);
                return;
            }
            if (NEIKeys.is(NEIKeys.BACK, pressed) && open.back()) {
                event.setCanceled(true);
                return;
            }
            if (open.pageKey(pressed)) {
                event.setCanceled(true);
                return;
            }
        }

        // the item zoom keys; Shift + the toggle key, like the other Shift keys, must not be stopped by the modifier
        if (treeWindow == null) {
            if (ItemZoom.toggleKey(pressed, HotkeyHelp.isShiftDown())) {
                ItemZoom.toggle();
                event.setCanceled(true);
                return;
            }
            if (ItemZoom.key(pressed)) {
                event.setCanceled(true);
                return;
            }
        }

        // NEI's enchantment modifier (over an item) and potion creator windows
        // (Ctrl + X is the copy id key)
        if (treeWindow == null && !HotkeyHelp.isCtrlDown() && !NEIKeys.ENCHANT.isUnbound()
                && NEIKeys.is(NEIKeys.ENCHANT, pressed)) {
            final ItemStack target = hoveredStack(screen);
            if (!target.isEmpty()) {
                modalWin = new CreatorWindow(screen, CreatorWindow.Kind.ENCHANT, target);
                event.setCanceled(true);
                return;
            }
        }
        if (treeWindow == null && !NEIKeys.POTION.isUnbound() && NEIKeys.is(NEIKeys.POTION, pressed)) {
            modalWin = new CreatorWindow(screen, CreatorWindow.Kind.POTION, ItemStack.EMPTY);
            event.setCanceled(true);
            return;
        }

        if (treeWindow == null && NEIKeys.is(NEIKeys.TOGGLE_BOOKMARKS, pressed)) {
            bookmarksVisible = !bookmarksVisible;
            event.setCanceled(true);
            return;
        }

        // NEI's favorite keys (both need Shift): in the recipe window an ingredient with alternatives becomes the
        // favorite one; over an item panel entry the item's favorite recipe tree is saved as a bookmark group
        if (HotkeyHelp.isShiftDown() && NEIKeys.is(NEIKeys.FAVORITE_ITEM, pressed) && overlayFor(screen) != null
                && overlayFor(screen).favoriteItemKey()) {
            event.setCanceled(true);
            return;
        }
        if (HotkeyHelp.isShiftDown() && treeWindow == null && NEIKeys.is(NEIKeys.FAVORITE, pressed)
                && (ITEMS.contains(lastMouseX, lastMouseY) || HISTORY.contains(lastMouseX, lastMouseY))) {
            final ItemStack over = panelStackAt(lastMouseX, lastMouseY);
            if (!over.isEmpty()) RecipeBookmarks.saveFavoriteTree(codechicken.nei.item.FluidDisplay.idOf(over));
            event.setCanceled(true);
            return;
        }

        // NEI's bookmark.remove_recipe: Shift + key over a group's strip removes the whole group
        if (HotkeyHelp.isShiftDown() && treeWindow == null && bookmarksVisible && NEIKeys.is(NEIKeys.REMOVE_GROUP, pressed)
                && BOOKMARKS.contains(lastMouseX, lastMouseY) && BOOKMARKS.itemAt(lastMouseX, lastMouseY) == null) {
            final int groupId = BOOKMARKS.hoveredGroup(lastMouseX, lastMouseY);
            if (groupId >= 0) {
                Bookmarks.removeGroup(groupId);
                event.setCanceled(true);
                return;
            }
        }

        // NEI's gui.overlay with Shift: fill the crafting grid with the recipe of the item under the mouse; with Ctrl
        // as well, as many sets as the bookmark slot's multiplier (the ghost-item overlay without Shift is not ported)
        if (HotkeyHelp.isShiftDown() && treeWindow == null && NEIKeys.is(NEIKeys.OVERLAY, pressed)
                && screen instanceof AbstractContainerScreen<?> container) {
            final ItemStack over = hoveredStack(screen);
            if (!over.isEmpty()) {
                final String id = codechicken.nei.item.FluidDisplay.idOf(over);
                final BookmarkGrid.Item slot = bookmarksVisible ? BOOKMARKS.itemAt(lastMouseX, lastMouseY) : null;
                final String recipeKey = slot != null && slot.recipeId != null ? slot.recipeId : RecipeBookmarks.favoriteKeyFor(id);
                final RecipeView view = recipeKey == null ? null : RecipeBookmarks.resolve(id, recipeKey);
                if (view != null) {
                    // As in NEI: multiplier 0 (Shift alone) is 64 sets at most; Ctrl + Shift uses the multiplier of the
                    // bookmark slot under the mouse (at most 64), or 1 when the mouse is not over one
                    int sets = 64;
                    if (HotkeyHelp.isCtrlDown()) {
                        final var calc = slot == null ? null : Bookmarks.grid().calc.get(slot);
                        final long multiplier = slot == null ? 1 : calc != null ? calc.multiplier
                                : slot.factor <= 0 ? 0 : slot.amount / slot.factor;
                        sets = (int) Math.min(64, multiplier);
                    }
                    RecipeTransfer.transfer(container, view, Math.max(1, sets));
                }
                event.setCanceled(true);
                return;
            }
        }

        // NEI's bookmark.chat_link (Ctrl + key): shares the group under the mouse, a bookmark (its whole recipe) or an item
        // of the panels in chat. Needs the mod on the server.
        if (treeWindow == null && HotkeyHelp.isCtrlDown() && !NEIKeys.CHAT_LINK.isUnbound()
                && NEIKeys.is(NEIKeys.CHAT_LINK, pressed)) {
            if (bookmarksVisible && BOOKMARKS.contains(lastMouseX, lastMouseY)) {
                final var slot = BOOKMARKS.itemAt(lastMouseX, lastMouseY);
                final int groupId = slot == null ? BOOKMARKS.hoveredGroup(lastMouseX, lastMouseY) : -1;
                if (slot != null) ChatLinks.sendSlot(slot);
                else if (groupId >= 0) ChatLinks.sendGroup(groupId);
                if (slot != null || groupId >= 0) {
                    event.setCanceled(true);
                    return;
                }
            }
            // NEI's Ctrl + Shift + key: the whole recipe under the mouse (the recipe window's, or an item's favorite)
            final RecipeOverlay linkWindow = overlayFor(screen);
            final ItemEntry linkEntry = panelEntryAt(lastMouseX, lastMouseY);
            if (HotkeyHelp.isShiftDown()) {
                RecipeView linked = linkWindow != null ? linkWindow.hoveredRecipe() : null;
                if (linked == null && linkEntry != null) {
                    final String favorite = RecipeBookmarks.favoriteKeyFor(linkEntry.registryId);
                    linked = favorite == null ? null : RecipeBookmarks.resolve(linkEntry.registryId, favorite);
                }
                if (linked != null) {
                    ChatLinks.sendRecipe(linked);
                    event.setCanceled(true);
                    return;
                }
            } else if (linkWindow != null && !linkWindow.hovered().isEmpty()) { // an item of the recipe window
                final ItemStack shown = linkWindow.hovered();
                ChatLinks.sendItem(codechicken.nei.item.FluidDisplay.idOf(shown), Math.max(1, shown.getCount()));
                event.setCanceled(true);
                return;
            }
            final ItemEntry entry = linkEntry;
            if (entry != null) {
                ChatLinks.sendItem(entry.registryId, QuantityBar.multiplier(entry.stack.getMaxStackSize()));
                event.setCanceled(true);
                return;
            }
        }

        // NEI's bookmark.pull_items: over a group's strip pulls the group's items out of the open container, over an item
        // pulls that item (Shift: only what the inventory lacks). Needs a container screen with a handler.
        if (treeWindow == null && !NEIKeys.PULL_ITEMS.isUnbound() && NEIKeys.is(NEIKeys.PULL_ITEMS, pressed)
                && ContainerPull.available(screen)) {
            final boolean shift = HotkeyHelp.isShiftDown();
            final var slot = bookmarksVisible && BOOKMARKS.contains(lastMouseX, lastMouseY)
                    ? BOOKMARKS.itemAt(lastMouseX, lastMouseY) : null;
            final ItemEntry bookmarked = slot != null ? ItemList.find(slot.id) : null;
            final ItemStack stack = bookmarked != null ? bookmarked.stack : hoveredStack(screen);
            if (!stack.isEmpty()) {
                final int amount = slot != null && slot.amount > 0 ? slot.amount : QuantityBar.multiplier(stack.getMaxStackSize());
                ContainerPull.pullStack(codechicken.nei.item.FluidDisplay.idOf(stack), amount, shift);
                event.setCanceled(true);
                return;
            }
            final int groupId = bookmarksVisible ? BOOKMARKS.hoveredGroup(lastMouseX, lastMouseY) : -1;
            if (groupId >= 0) {
                ContainerPull.pullGroup(groupId, shift);
                event.setCanceled(true);
                return;
            }
        }

        // NEI's gui.craft_items (Shift +): autocraft the bookmarked recipe, crafting group or recipe under the mouse.
        // Ctrl as well: only what the inventory lacks. A recipe in a crafting group crafts what it needs from the group.
        if (treeWindow == null && AutoCraft.enabled() && !NEIKeys.CRAFT_ITEMS.isUnbound()
                && NEIKeys.is(NEIKeys.CRAFT_ITEMS, pressed) && HotkeyHelp.isShiftDown()) {
            final boolean missingOnly = HotkeyHelp.isCtrlDown();
            final BookmarkGrid grid = Bookmarks.grid();
            final BookmarkGrid.Item slot = bookmarksVisible && BOOKMARKS.contains(lastMouseX, lastMouseY)
                    ? BOOKMARKS.itemAt(lastMouseX, lastMouseY) : null;
            final int groupId = slot == null && bookmarksVisible ? BOOKMARKS.hoveredGroup(lastMouseX, lastMouseY) : -1;
            final RecipeOverlay window = overlayFor(screen);
            boolean used = false;
            if (slot != null && slot.recipeId != null && slot.type != 2) {
                used = grid.group(slot.groupId).crafting
                        ? AutoCraft.start(grid.groupItems(slot.groupId), missingOnly, slot.recipeId)
                        : AutoCraft.start(AutoCraft.itemsOf(grid, slot), missingOnly, null);
            } else if (groupId >= 0 && grid.group(groupId).crafting) {
                used = AutoCraft.start(grid.groupItems(groupId), missingOnly, null);
            } else if (slot == null && window != null && window.hoveredRecipe() != null) {
                used = AutoCraft.start(AutoCraft.itemsOf(window.hoveredRecipe()), missingOnly, null);
            }
            if (used) {
                event.setCanceled(true);
                return;
            }
        }

        // Shift / Ctrl + key are separate actions in NEI, so the modifier must not stop the key from matching
        // (NeoForge's isActiveAndMatches refuses a key whose binding has no modifier while Shift or Ctrl is held)
        if (!NEIKeys.BOOKMARK.isUnbound() && NEIKeys.is(NEIKeys.BOOKMARK, pressed)) {
            // As in NEI: over a bookmark slot the key removes that slot (a recipe's result takes its recipe along)
            if (treeWindow == null && bookmarksVisible && BOOKMARKS.contains(lastMouseX, lastMouseY)) {
                final BookmarkGrid.Item slot = BOOKMARKS.itemAt(lastMouseX, lastMouseY);
                if (slot != null) {
                    Bookmarks.removeSlot(slot, HotkeyHelp.isShiftDown());
                    event.setCanceled(true);
                    return;
                }
            }

            // SHIFT over the favorite (heart) button saves the recipe tree as a crafting chain group (NEI)
            final RecipeOverlay heartTarget = overlayFor(screen);
            if (HotkeyHelp.isShiftDown() && heartTarget != null && heartTarget.hoveredHeart() != null) {
                RecipeBookmarks.saveTree(heartTarget.hoveredHeart());
                event.setCanceled(true);
                return;
            }

            // SHIFT over a recipe bookmarks the whole recipe (CTRL + SHIFT also keeps the counts)
            final RecipeOverlay recipeWindow = overlayFor(screen);
            if (HotkeyHelp.isShiftDown() && recipeWindow != null && recipeWindow.hoveredRecipe() != null) {
                // NEI's default (bookmarkRecipeWithCount = true) keeps the counts with or without Ctrl
                RecipeBookmarks.toggle(recipeWindow.hoveredRecipe(), recipeWindow.hovered(), true);
                event.setCanceled(true);
                return;
            }

            final ItemStack target = hoveredStack(screen);
            if (!target.isEmpty()) {
                // As in NEI: inside the recipe window the item remembers its recipe; Ctrl keeps a count of 1
                final String recipeId = recipeWindow != null && recipeWindow.hoveredRecipe() != null
                        ? RecipeBookmarks.key(recipeWindow.hoveredRecipe())
                        : null;
                Bookmarks.toggle(codechicken.nei.item.FluidDisplay.idOf(target), recipeId, HotkeyHelp.isCtrlDown());
                event.setCanceled(true);
            }
            return;
        }

        // As in NEI, Shift + the tree key over a recipe's heart saves the recipe tree and opens it; the key alone over a
        // crafting chain group's strip opens that group's tree. Like the bookmark key it works with modifiers held.
        if (treeWindow == null && !NEIKeys.OPEN_TREE.isUnbound() && NEIKeys.is(NEIKeys.OPEN_TREE, pressed)) {
            final RecipeOverlay heartWindow = overlayFor(screen);
            if (HotkeyHelp.isShiftDown() && heartWindow != null && heartWindow.hoveredHeart() != null) {
                final int groupId = RecipeBookmarks.saveTree(heartWindow.hoveredHeart());
                if (groupId >= 0) openTree(screen, groupId);
                event.setCanceled(true);
                return;
            }
            if (bookmarksVisible && BOOKMARKS.contains(lastMouseX, lastMouseY)) {
                final int groupId = BOOKMARKS.hoveredGroup(lastMouseX, lastMouseY);
                if (groupId >= 0 && Bookmarks.grid().group(groupId).crafting) {
                    openTree(screen, groupId);
                    event.setCanceled(true);
                    return;
                }
            }
        }

        // As in NEI: Ctrl + a copy key over an item copies its name, id or tags. Like the bookmark key, the modifier
        // must not stop the key from matching.
        if (HotkeyHelp.isCtrlDown()) {
            final String text = copyText(pressed, hoveredStack(screen));
            if (text != null) {
                Minecraft.getInstance().keyboardHandler.setClipboard(text);
                event.setCanceled(true);
                return;
            }
        }

        // Not in NEI: T over an item or fluid types its name into an open AE2 terminal's search field (NEI's own search
        // follows through the terminal sync). Only in a terminal; anywhere else the key does nothing.
        if (NEIKeys.is(NEIKeys.TERMINAL_NAME, pressed) && !HotkeyHelp.isShiftDown() && TerminalSearchSync.isTerminal(screen)) {
            final ItemStack named = hoveredStack(screen);
            if (!named.isEmpty() && TerminalSearchSync.type(screen, named.getHoverName().getString())) {
                event.setCanceled(true);
                return;
            }
        }

        final boolean recipe = NEIKeys.is(NEIKeys.RECIPE, pressed);
        final boolean usage = NEIKeys.is(NEIKeys.USAGE, pressed);
        if (recipe || usage) {
            final Item target = hoveredItem(screen);
            if (target != null) {
                RecipeOverlay.focusNext(hoveredStack(screen));
                openRecipes(screen, target, usage);
                event.setCanceled(true);
            }
        }
    }
    public static void onKeyReleased(ScreenEv event) {
        if (!activeScreen(event.getScreen())) return;
        HotkeyHelp.track(event.getKeyEvent().key(), false);
        ItemZoom.released(InputConstants.getKey(event.getKeyEvent()));
    }

    /** The text a Ctrl + copy key puts on the clipboard for this stack, or null if the key is not a copy key. */
    private static String copyText(InputConstants.Key pressed, ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (NEIKeys.is(NEIKeys.COPY_NAME, pressed)) {
            final String name = stack.getHoverName().getString();
            // shortcut: NEI also escapes search syntax by pattern mode; only quotes here, upgrade if pasted names misparse
            return name.contains(" ") ? '"' + name.replace("\"", "\\\"") + '"' : name;
        }
        if (NEIKeys.is(NEIKeys.COPY_ID, pressed)) {
            return codechicken.nei.item.FluidDisplay.idOf(stack);
        }
        if (NEIKeys.is(NEIKeys.COPY_TAGS, pressed)) {
            return BuiltInRegistries.ITEM.wrapAsHolder(stack.getItem()).tags().map(t -> t.location().toString()).sorted()
                    .collect(java.util.stream.Collectors.joining(","));
        }
        return null;
    }

    /** The item under the mouse: the recipe window, a panel entry, or a slot of the container screen. */
    private static Item hoveredItem(Screen screen) {
        final ItemStack stack = hoveredStack(screen);
        return stack.isEmpty() ? null : stack.getItem();
    }

    private static ItemStack hoveredStack(Screen screen) {
        final RecipeOverlay open = overlayFor(screen);
        if (open != null && !open.hovered().isEmpty()) return open.hovered();

        // the tree window covers the panels and the container: only its own boxes count
        final CraftingTreeOverlay treeWindow = treeFor(screen);
        if (treeWindow != null) {
            return open != null && open.contains(lastMouseX, lastMouseY) ? ItemStack.EMPTY : treeWindow.hoveredStack();
        }

        final ItemEntry panelEntry = panelEntryAt(lastMouseX, lastMouseY);
        if (panelEntry != null) return panelEntry.stack;

        if (open == null && screen instanceof AbstractContainerScreen<?> container) {
            final var slot = container.hoveredSlot;
            if (slot != null) return slot.getItem();
        }
        return ItemStack.EMPTY;
    }
    public static void onCharTyped(ScreenEv event) {
        if (!active(event.getScreen())) return;
        if (skipChar) {
            skipChar = false;
            event.setCanceled(true);
            return;
        }
        final ModalWindow presetsTyping = presetsFor(event.getScreen());
        if (presetsTyping != null) {
            if (presetsTyping.typingBox() != null) presetsTyping.typingBox().charTyped(event.getCharacterEvent());
            event.setCanceled(true);
            return;
        }
        final RecipeOverlay typing = overlayFor(event.getScreen());
        if (typing != null && typing.searchFocused()) {
            typing.searchBox().charTyped(event.getCharacterEvent());
            event.setCanceled(true);
            return;
        }
        final CraftingTreeOverlay treeTyping = treeFor(event.getScreen());
        if (treeTyping != null) {
            if (treeTyping.searchFocused()) treeTyping.searchBox().charTyped(event.getCharacterEvent());
            event.setCanceled(true); // typing goes nowhere else while the tree is open
            return;
        }
        if (QUANTITY.focused()) {
            final int codepoint = event.getCharacterEvent().codepoint();
            if (codepoint < 0x10000) QUANTITY.typed((char) codepoint);
            event.setCanceled(true);
            return;
        }
        if (searchBox == null || !searchBox.isFocused()) return;

        searchBox.charTyped(event.getCharacterEvent());
        event.setCanceled(true);
    }
}
