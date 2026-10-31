package codechicken.nei.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import codechicken.nei.Bookmarks;
import codechicken.nei.ItemList;
import codechicken.nei.NEIConfig;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.ReadableNumber;
import codechicken.nei.bookmark.TreeCamera;
import codechicken.nei.bookmark.TreeGraph;
import codechicken.nei.bookmark.TreeLayout;
import codechicken.nei.bookmark.TreeSlot;
import codechicken.nei.bookmark.TreeState;
import codechicken.nei.bookmark.TreeStats;
import codechicken.nei.bookmark.TreeView;
import codechicken.nei.item.ItemEntry;
import codechicken.nei.recipe.RecipeView;
import codechicken.nei.search.SearchParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * NEI's Crafting Tree window ({@code GuiCraftingTree}): a full-screen picture of a crafting chain group, every recipe
 * a box with the boxes of its ingredients hanging below it.
 *
 * <ul>
 * <li>The wheel scrolls, Shift + wheel scrolls sideways, Ctrl + wheel zooms; dragging the empty background pans.</li>
 * <li>Ctrl + wheel over a root box changes how many times its recipe is made (Alt: one step is a stack).</li>
 * <li>Click a box: its recipes (left) or uses (right). The "+" in that recipe window makes the recipe the one for the
 * box (it replaces the recipes the old one needed).</li>
 * <li>The little recipe icon at a box's top left corner removes that recipe (red cross while hovered); the arrow below
 * a box hides / shows everything under it.</li>
 * <li>Toolbar: use the player's inventory for the maths, fit the tree to the window, collapse / expand.</li>
 * </ul>
 *
 * The maths and layout live in {@link TreeView} (plain Java, tested on their own); this class only draws and turns
 * input into calls on it. Like {@link RecipeOverlay} it owns no event hooks: {@link NEIClientEvents} forwards to it.
 *
 * Not ported yet: Alt + click to collapse a recipe, the recipe preview in the badge tooltip, container items and fluids.
 */
public final class CraftingTreeOverlay {

    /** Shows the recipe window for an item, in "pick a recipe for the tree" mode. */
    interface Opener {

        void open(Item item, boolean uses);
    }

    // NEI's GuiCraftingTree
    private static final int MARGIN = 4;
    private static final int PADDING = 9;
    private static final int PADDING_TOP = 22;

    // NEI's CraftingTreeToolbar: 16 x 16 buttons, 20 apart
    private static final int BTN = 16;
    private static final int BTN_SPACING = 20;
    private static final int BTN_INV = 0;
    private static final int BTN_FIT = 1;
    private static final int BTN_COLLAPSE = 2;
    private static final int BTN_STATS = 3;
    private static final int BTN_SECTIONS = 4;
    private static final int BTN_LINK = 5;
    private static final int BTN_SHOT = 6;
    private static final int BTN_COUNT = 7;

    // NEI's CraftingTreeCanvas
    private static final int LINE_COLOR = 0xFF888888;
    private static final int HOVER_COLOR = 0x66888888;
    private static final float BADGE_SCALE = TreeLayout.BADGE_SCALE;

    // slot sprites on the sheet, 24 apart
    private static final int SLOT_DEFAULT = 0;
    private static final int SLOT_MISSING = 1;
    private static final int SLOT_CRAFTING = 2;
    private static final int SLOT_DISABLED = 3;

    private final Screen screen;
    private final TreeView view;
    private final Opener opener;

    private final Map<String, ItemStack> stacks = new HashMap<>();
    private final Map<String, RecipeView> recipes = new HashMap<>();

    private TreeSlot hoveredNode;
    private TreeSlot hoveredBadge;
    /** NEI's {@code replaceRecipeNode}: the box the next recipe picked in the recipe window takes over. */
    private TreeSlot replaceNode;
    private int lastW = -1;
    private int lastH = -1;

    // NEI's tree search: matching boxes stay normal, the others turn grey, Enter / Shift + Enter step through the matches
    private static final int SLOT_HIGHLIGHT = 4;
    private final EditBox search;
    private final SearchParser parser = new SearchParser();
    private ItemFilter filter;
    private final Set<TreeSlot> matches = new LinkedHashSet<>();
    private TreeSlot highlight;

    // NEI's stats panel under the tree. Clicking one of its slots highlights the matching boxes like a search does.
    private final StatsPanel stats = new StatsPanel();
    /** Set while "Save tree as image" is taking its pictures: the window then draws nothing but the tree. */
    private TreeShot shot;
    private StatsPanel.Cell hoveredCell;
    private String statsKey;
    private Predicate<TreeSlot> statsMatch;
    private final Map<String, ItemStack> handlerStations = new HashMap<>();
    private boolean menuOpen;
    private int menuX;
    private int menuY;
    private int menuW;

    CraftingTreeOverlay(Screen screen, BookmarkGrid grid, int groupId, Opener opener) {
        this.screen = screen;
        this.opener = opener;
        this.view = new TreeView(grid, groupId);
        search = new EditBox(Minecraft.getInstance().font, 0, 0, 140, 14, Component.empty());
        search.setMaxLength(256);
        search.setResponder(this::searchChanged);

        layout(screen.width, screen.height);
        refreshInventory();
        final boolean firstOpen = !view.state.initialized;
        view.rebuild(null);
        updateStats();
        if (firstOpen) view.layout.fitToView(view.graph, view.camera()); // fit into the room the panel leaves
    }

    Screen screen() {
        return screen;
    }

    /** False once the group is gone (the window closes then). */
    boolean alive() {
        return view.groupExists();
    }

    /** The item of the box under the mouse as of the last frame, or empty. */
    ItemStack hoveredStack() {
        if (hoveredCell != null && hoveredCell.entry() != null) return stackOf(hoveredCell.entry().itemId());
        return hoveredNode == null ? ItemStack.EMPTY : stackOf(hoveredNode.itemId);
    }

    // ---- search ----

    boolean searchFocused() {
        return search.isFocused();
    }

    EditBox searchBox() {
        return search;
    }

    private void searchChanged(String text) {
        statsKey = null; // typing replaces a stats highlight (NEI)
        statsMatch = null;
        highlight = null;
        filter = text.isEmpty() ? null : parser.parse(text);
        refreshMatches();
        searchNext(true);
    }

    /** Boxes change when the tree is edited, so the matches are worked out again every frame (a tree is small). */
    private void refreshMatches() {
        matches.clear();
        if (statsMatch != null) {
            for (TreeSlot node : view.graph.items) if (statsMatch.test(node)) matches.add(node);
        } else if (filter != null) {
            for (TreeSlot node : view.graph.items) {
                final ItemEntry entry = ItemList.find(node.itemId);
                try {
                    if (entry != null && filter.matches(entry)) matches.add(node);
                } catch (RuntimeException e) {
                    // a filter that cannot judge this item does not match it
                }
            }
        }
        if (!matches.contains(highlight)) highlight = null;
    }

    private boolean searching() {
        return filter != null || statsMatch != null;
    }

    /** NEI's {@code handleWidgetClick}: a stats slot highlights its boxes in the tree (Shift goes backwards). */
    private void statsClicked(StatsPanel.Cell cell, int button) {
        if (button == 1) {
            if (cell.key().equals(statsKey)) { // right click on the lit slot clears it
                statsKey = null;
                statsMatch = null;
                highlight = null;
                refreshMatches();
            }
            return;
        }
        if (button != 0) return;

        if (!cell.key().equals(statsKey)) {
            if (!search.getValue().isEmpty()) search.setValue(""); // clears any stats target through the responder
            statsKey = cell.key();
            if (cell.entry() != null) {
                final String id = cell.entry().itemId();
                statsMatch = node -> node.itemId.equals(id);
            } else {
                final String name = cell.handler().name();
                statsMatch = node -> node.prefItem != null && name.equals(handlerName(node.itemId, node.prefItem.recipeId));
            }
            highlight = null;
            refreshMatches();
        }
        searchNext(!HotkeyHelp.isShiftDown());
    }

    // ---- stats panel ----

    private RecipeView recipeFor(String itemId, String recipeId) {
        if (!recipes.containsKey(recipeId)) recipes.put(recipeId, RecipeBookmarks.resolve(itemId, recipeId));
        return recipes.get(recipeId);
    }

    /** NEI's handler name: here the recipe type's title. Remembers the station icon for the handler slots. */
    private String handlerName(String itemId, String recipeId) {
        final RecipeView recipe = recipeFor(itemId, recipeId);
        if (recipe == null) return "Recipe";
        handlerStations.putIfAbsent(recipe.title(), recipe.station());
        return recipe.title();
    }

    private final StatsPanel.Lookup lookup = new StatsPanel.Lookup() {

        @Override
        public ItemStack stack(String itemId) {
            return stackOf(itemId);
        }

        @Override
        public ItemStack station(String itemId, String recipeId) {
            final RecipeView recipe = recipeFor(itemId, recipeId);
            return recipe == null ? ItemStack.EMPTY : recipe.station();
        }

        @Override
        public ItemStack handlerStation(String handlerName) {
            return handlerStations.getOrDefault(handlerName, ItemStack.EMPTY);
        }
    };

    /** Works out the panel and gives the tree the room it leaves. Runs every frame; the panel rebuilds only on change. */
    private void updateStats() {
        final int wx = MARGIN + PADDING;
        final int wy = MARGIN + PADDING_TOP;
        final int ww = lastW - MARGIN * 2 - PADDING * 2;
        final int wh = lastH - MARGIN * 2 - PADDING_TOP - PADDING;
        // while the tree is captured the panel is only shown for its own last picture, and the viewport stays as it was
        stats.update(view, view.state.statsVisible && (shot == null || shot.statsTile()), wx, wy, ww, wh, this::handlerName);
        if (shot == null) view.camera().setViewport(wx, wy, ww, wh - stats.height());
    }

    /** The index after / before {@code index} in a ring of {@code size} (-1 = nothing highlighted yet). */
    static int step(int index, int size, boolean forward) {
        return forward ? (index + 1) % size : index <= 0 ? size - 1 : index - 1;
    }

    /** NEI's {@code searchGoTo}: highlights the next / previous match, opens the boxes above it and centres it. */
    void searchNext(boolean forward) {
        if (matches.isEmpty()) return;
        final List<TreeSlot> list = new ArrayList<>(matches);
        final TreeSlot target = list.get(step(list.indexOf(highlight), list.size(), forward));

        for (TreeSlot up = view.graph.parentOf.get(target); up != null; up = view.graph.parentOf.get(up)) {
            view.state.collapsedItems.remove(up.ingrItem);
        }
        view.layout.recalculate(view.graph, view.state.collapsedItems, null, view.state.camera);

        highlight = target;
        final TreeLayout.Pt p = view.layout.pointByNode.get(target);
        if (p != null) view.camera().centerOn(p.x + TreeLayout.SLOT_SIZE / 2f, p.y + TreeLayout.SLOT_SIZE / 2f);
    }

    // ---- layout and data ----

    private void layout(int sw, int sh) {
        if (sw == lastW && sh == lastH) return;
        lastW = sw;
        lastH = sh;
        updateStats();
    }

    private ItemStack stackOf(String id) {
        return stacks.computeIfAbsent(id, key -> {
            final ItemEntry entry = ItemList.find(key);
            return entry == null ? ItemStack.EMPTY : entry.stack;
        });
    }

    /** The recipe a box's recipe id stands for (cached), or null. */
    private RecipeView recipeOf(TreeSlot node) {
        return node.prefItem == null ? null : recipeFor(node.itemId, node.prefItem.recipeId);
    }

    /**
     * NEI's inventory snapshot: everything in the open container's slots the player can take, except crafting results,
     * counted per item. Taken again before every rebuild.
     */
    private void refreshInventory() {
        final List<BookmarkGrid.Item> items = new ArrayList<>();

        if (view.state.useInventorySnapshot && screen instanceof AbstractContainerScreen<?> container) {
            final Player player = Minecraft.getInstance().player;
            final Map<String, Integer> counts = new LinkedHashMap<>();

            for (Slot slot : container.getMenu().slots) {
                final ItemStack stack = slot.getItem();
                if (stack.isEmpty() || slot instanceof ResultSlot || player != null && !slot.mayPickup(player)) continue;
                counts.merge(codechicken.nei.item.FluidDisplay.idOf(stack), stack.getCount(), Integer::sum);
            }

            counts.forEach((id, count) -> items.add(new BookmarkGrid.Item(id, count, BookmarkGrid.DEFAULT_GROUP_ID)));
        }

        view.setInventory(items);
    }

    // ---- drawing ----

    void render(GuiGraphicsExtractor g, int mx, int my, int sw, int sh, boolean blocked) {
        layout(sw, sh);
        final Font font = Minecraft.getInstance().font;
        final TreeCamera camera = view.camera();

        if (shot != null && TreeShot.active() != shot) shot = null; // finished or cancelled
        if (shot != null) {
            updateStats();
            hoveredNode = null;
            hoveredBadge = null;
            shot.applyCamera();
            g.fill(0, 0, sw, sh, 0xFF101010);
            if (shot.statsTile()) stats.draw(g, font, -1, -1, false, statsKey, lookup);
            else drawTree(g, font, -1, -1, true, false);
            return;
        }

        hoveredNode = null;
        hoveredBadge = null;

        g.fill(0, 0, sw, sh, 0xC8101010); // NEI's default screen background
        Tex.frame(g, MARGIN, MARGIN, sw - MARGIN * 2, sh - MARGIN * 2);

        refreshMatches();
        updateStats();
        final boolean empty = view.isEmpty();
        final boolean canHover = !blocked && !camera.isDragging() && camera.isInWidget(mx, my);

        if (empty) {
            final String text = "No crafting chain to display";
            g.text(font, Component.literal(text), sw / 2 - font.width(text) / 2,
                    MARGIN + PADDING_TOP + (sh - MARGIN * 2 - PADDING_TOP) / 2, 0xFFAAAAAA, true);
        } else {
            if (camera.isDragging()) camera.drag(mx, my);
            drawTree(g, font, mx, my, blocked, canHover);
        }

        final String title = "Crafting Tree";
        g.text(font, Component.literal(title), sw / 2 - font.width(title) / 2, MARGIN + PADDING_TOP / 2 - 9 / 2 + 1,
                0xFFFFFFFF, true);

        hoveredCell = blocked ? null : stats.cellAt(mx, my);
        stats.draw(g, font, mx, my, !blocked, statsKey, lookup);
        drawToolbar(g, mx, my, blocked);
        drawMenu(g, font, mx, my);
        search.setWidth(Math.min(140, (sw - MARGIN * 2 - PADDING * 2) / 3));
        search.setX(sw - MARGIN - search.getWidth() - PADDING);
        search.setY(MARGIN + 5);
        search.extractRenderState(g, mx, my, 0f);
        requestTooltip(mx, my, blocked);
    }

    private void drawTree(GuiGraphicsExtractor g, Font font, int mx, int my, boolean blocked, boolean canHover) {
        final TreeCamera camera = view.camera();
        final TreeLayout layout = view.layout;
        final TreeGraph graph = view.graph;
        final float zoom = camera.zoomLevel;

        if (shot == null) camera.clampToBounds(layout.treeWidth, layout.treeHeight);

        final float wx = camera.worldX(mx);
        final float wy = camera.worldY(my);
        if (canHover) {
            hoveredBadge = layout.badgeAt(wx, wy, camera);
            if (hoveredBadge == null) hoveredNode = layout.nodeAt(wx, wy, camera);
        }
        final TreeSlot arrowHover = blocked || camera.isDragging() || !camera.isInWidget(mx, my) ? null
                : layout.arrowAt(wx, wy, camera);

        final float zScrollX = camera.scrollX * zoom;
        final int cropXMin = (int) ((zScrollX - 32) / zoom);
        final int cropXMax = (int) ((zScrollX + 32) / zoom) + (int) (camera.widgetW / zoom);

        Tex.beginClip(g, camera.widgetX, camera.widgetY, camera.widgetW, camera.widgetH);
        g.pose().pushMatrix();
        g.pose().translate(camera.widgetX - zScrollX, camera.widgetY - camera.scrollY * zoom);
        g.pose().scale(zoom, zoom);

        for (TreeSlot node : layout.visible(camera)) {
            final TreeLayout.Pt p = layout.pointByNode.get(node);

            drawParentLine(g, layout, graph, node, p);
            if (p.x < cropXMin || p.x > cropXMax) continue;

            drawSlot(g, font, node, p, node == hoveredNode);
            drawCollapseIcon(g, layout, graph, node, p, node == arrowHover);

            if (node == hoveredNode) g.fill(p.x + 1, p.y + 1, p.x + TreeLayout.SLOT_SIZE - 1,
                    p.y + TreeLayout.SLOT_SIZE - 1, HOVER_COLOR);

            if (node.prefItem != null && graph.childrenOf.containsKey(node)) {
                drawBadge(g, node, p);
                if (node == hoveredBadge) drawDeleteButton(g, p);
            }
        }

        g.pose().popMatrix();
        Tex.endClip(g);

        drawScrollbars(g, camera, layout);
    }

    private int slotKind(TreeSlot node) {
        if (searching()) return node == highlight ? SLOT_HIGHLIGHT : matches.contains(node) ? SLOT_DEFAULT : SLOT_DISABLED;
        if (!view.state.useInventorySnapshot) return SLOT_DEFAULT;
        final TreeGraph graph = view.graph;

        if (node.leftAmount == 0) {
            final TreeSlot parent = graph.parentOf.get(node);
            if (parent != null && parent.leftAmount == 0 || node.requestedAmount == 0) return SLOT_DISABLED;
            return SLOT_DEFAULT;
        }
        if (!graph.childrenOf.containsKey(node)) return SLOT_MISSING;
        if (view.isCollapsed(node)) return graph.subtreeMissingCache.contains(node) ? SLOT_MISSING : SLOT_CRAFTING;
        if (!graph.childrenNeedCraftingCache.contains(node)) return SLOT_CRAFTING;
        return SLOT_DEFAULT;
    }

    private void drawSlot(GuiGraphicsExtractor g, Font font, TreeSlot node, TreeLayout.Pt p, boolean hovered) {
        Tex.blit(g, p.x, p.y, 68 + 24 * slotKind(node), 2, TreeLayout.SLOT_SIZE, TreeLayout.SLOT_SIZE);

        final ItemStack stack = stackOf(node.itemId);
        if (!stack.isEmpty()) Draw.stack(g, stack, p.x + 1, p.y + 1);

        if (node.requestedAmount > 0) {
            final boolean resolved = node.leftAmount == 0;
            // As in NEI: Ctrl over a collapsed recipe's root shows what that recipe alone asks for
            final boolean own = hovered && !resolved && node.prefItem != null && HotkeyHelp.isCtrlDown()
                    && view.graph.roots.contains(node)
                    && view.grid.group(view.groupId).collapsedRecipes.contains(node.prefItem.recipeId);
            final long shownAmount = own ? node.currentRequestedAmount : node.requestedAmount;
            final String text = codechicken.nei.item.FluidDisplay.isFluidId(node.itemId)
                    ? codechicken.nei.item.FluidDisplay.amountLabel(shownAmount) : amountText(shownAmount);
            // bottom right of the 16 x 16 item area, as NEI's drawNEIOverlayText
            Draw.smallText(g, font, text, (float) Math.ceil(p.x + 1 + 16 - font.width(text) * 0.5),
                    (float) Math.ceil(p.y + 1 + 16 - 4.5), resolved ? 0x55FF55 : 0xFFFFFF);
        }
    }

    private static String amountText(long amount) {
        return amount < 10_000 ? String.valueOf(amount) : ReadableNumber.wide(amount);
    }

    private void drawCollapseIcon(GuiGraphicsExtractor g, TreeLayout layout, TreeGraph graph, TreeSlot node,
            TreeLayout.Pt p, boolean hover) {
        if (!layout.hasCollapseArrow(node)) return;

        // NEI's names, taken literally: the sheet has the "-" sprite as "collapsed" and the "+" as "expanded"
        final int v = view.isCollapsed(node) ? 88 : 95;
        Tex.blit(g, TreeLayout.arrowX(p), TreeLayout.arrowY(p), hover ? 11 : 4, v, TreeLayout.ARROW_SIZE,
                TreeLayout.ARROW_SIZE);
    }

    private void drawParentLine(GuiGraphicsExtractor g, TreeLayout layout, TreeGraph graph, TreeSlot node,
            TreeLayout.Pt p) {
        final TreeSlot parent = graph.parentOf.get(node);
        if (parent == null) return;
        final TreeLayout.Pt pp = layout.pointByNode.get(parent);
        if (pp == null) return;

        final int x0 = p.x + TreeLayout.SLOT_SIZE / 2;
        final int y0 = p.y - 1;
        final int x1 = pp.x + TreeLayout.SLOT_SIZE / 2;
        final int y1 = pp.y + TreeLayout.SLOT_SIZE;

        if (x0 != x1) {
            final int midY = (y0 + y1) / 2;
            rect(g, x0, Math.min(y0, midY), 1, Math.abs(midY - y0) + 1);
            rect(g, Math.min(x0, x1), midY, Math.abs(x1 - x0) + 1, 1);
            rect(g, x1, Math.min(midY, y1), 1, Math.abs(y1 - midY) + 1);
        } else {
            rect(g, x0, Math.min(y0, y1), 1, Math.abs(y1 - y0) + 1);
        }
    }

    private static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, LINE_COLOR);
    }

    /** The small recipe icon at a box's top left corner: the recipe type's station, or NEI's hammer. */
    private void drawBadge(GuiGraphicsExtractor g, TreeSlot node, TreeLayout.Pt p) {
        final float[] badge = TreeLayout.badgeRect(p.x, p.y);
        final RecipeView recipe = recipeOf(node);
        final ItemStack station = recipe == null ? ItemStack.EMPTY : recipe.station();

        g.pose().pushMatrix();
        g.pose().translate(badge[0], badge[1]);
        g.pose().scale(BADGE_SCALE, BADGE_SCALE);
        if (station.isEmpty()) Tex.blit(g, 0, 0, 67, 48, 16, 16);
        else Draw.stack(g, station, 0, 0);
        g.pose().popMatrix();
    }

    private void drawDeleteButton(GuiGraphicsExtractor g, TreeLayout.Pt p) {
        final float[] badge = TreeLayout.badgeRect(p.x, p.y);

        g.pose().pushMatrix();
        g.pose().translate(badge[0], badge[1]);
        g.pose().scale(BADGE_SCALE, BADGE_SCALE);
        Tex.blit(g, 0, 0, 85, 48, 16, 16);
        g.pose().popMatrix();
    }

    private void drawScrollbars(GuiGraphicsExtractor g, TreeCamera camera, TreeLayout layout) {
        if (layout.treeWidth <= 0 || layout.treeHeight <= 0) return;

        final float scrollXPct = TreeCamera.clamp(camera.scrollX / layout.treeWidth, 0.0f, 1.0f);
        final float scrollYPct = TreeCamera.clamp(camera.scrollY / layout.treeHeight, 0.0f, 1.0f);

        Tex.blit(g, camera.widgetX + camera.widgetW - 3, (int) (camera.widgetY + scrollYPct * (camera.widgetH - 24)), 0,
                88, 2, 24);
        Tex.blit(g, (int) (camera.widgetX + scrollXPct * (camera.widgetW - 24)), camera.widgetY + camera.widgetH - 3, 0,
                84, 24, 2);
    }

    // ---- toolbar ----

    private int buttonX(int index) {
        return MARGIN + PADDING + BTN_SPACING * index;
    }

    private int buttonY() {
        return MARGIN + 4;
    }

    private int buttonAt(double mx, double my) {
        for (int i = 0; i < BTN_COUNT; i++) {
            if (Draw.in(mx, my, buttonX(i), buttonY(), BTN, BTN)) return i;
        }
        return -1;
    }

    /** NEI draws a toggled-on button in its "disabled" look. */
    private boolean buttonOn(int index) {
        return switch (index) {
            case BTN_INV -> view.state.useInventorySnapshot;
            case BTN_COLLAPSE -> view.state.collapseMode != TreeState.CollapseMode.EXPANDED;
            case BTN_STATS -> view.state.statsVisible;
            default -> false;
        };
    }

    private int iconU(int index) {
        return switch (index) {
            case BTN_INV -> 56;
            case BTN_FIT -> 38;
            // NEI shows the compact icon while some sections are switched off
            case BTN_STATS -> view.state.statsSections.size() == TreeStats.Section.values().length ? 2 : 20;
            case BTN_SECTIONS -> 164;
            case BTN_LINK -> 74;
            case BTN_SHOT -> 146;
            default -> switch (view.state.collapseMode) {
                case EXPANDED -> 128;
                case SMART -> 110;
                case ALL -> 92;
            };
        };
    }

    private String buttonTooltip(int index) {
        return switch (index) {
            case BTN_INV -> view.state.useInventorySnapshot ? "Using inventory snapshot - click to clear"
                    : "Recompute tree using player inventory (snapshot)";
            case BTN_FIT -> "Fit tree to view";
            case BTN_STATS -> view.state.statsVisible ? "Hide stats panel" : "Show stats panel";
            case BTN_SECTIONS -> "Choose visible stats sections";
            case BTN_LINK -> "Send Bookmark Group to Chat";
            case BTN_SHOT -> "Save tree as image";
            default -> switch (view.state.collapseMode) {
                case EXPANDED -> "Collapse items with nothing left to craft";
                case SMART -> "Collapse all nested items";
                case ALL -> "Expand all nested items";
            };
        };
    }

    private void drawToolbar(GuiGraphicsExtractor g, int mx, int my, boolean blocked) {
        final int over = blocked ? -1 : buttonAt(mx, my);

        for (int i = 0; i < BTN_COUNT; i++) {
            final int x = buttonX(i);
            final int y = buttonY();

            // vanilla's button look: dark when "on", blue-grey when hovered
            final int body = buttonOn(i) ? 0xFF4A4A4A : i == over ? 0xFF7381B5 : 0xFF6F6F6F;
            g.fill(x, y, x + BTN, y + BTN, 0xFF000000);
            g.fill(x + 1, y + 1, x + BTN - 1, y + BTN - 1, body);
            if (!buttonOn(i)) {
                g.fill(x + 1, y + 1, x + BTN - 1, y + 2, 0xFFA0A0A0);
                g.fill(x + 1, y + BTN - 2, x + BTN - 1, y + BTN - 1, 0xFF3C3C3C);
            }

            // the 16 x 16 icon is drawn at half size, centred
            g.pose().pushMatrix();
            g.pose().translate(x + (BTN - 8) / 2f, y + (BTN - 8) / 2f);
            g.pose().scale(0.5f, 0.5f);
            Tex.blit(g, 0, 0, iconU(i), 66, 16, 16);
            g.pose().popMatrix();
        }
    }

    // ---- tooltips ----

    private static Component gray(String text) {
        return Component.literal(text).withStyle(s -> s.withColor(0xAAAAAA));
    }

    private void requestTooltip(int mx, int my, boolean blocked) {
        if (blocked) return;

        final int button = buttonAt(mx, my);
        if (button >= 0) {
            ItemTooltip.requestPlain(List.of(Component.literal(buttonTooltip(button))));
        } else if (menuOpen && Draw.in(mx, my, menuX, menuY, menuW, MENU_TOP + TreeStats.Section.values().length * MENU_ROW)) {
            // the menu has no tooltip
        } else if (hoveredCell != null) {
            statsTooltip(hoveredCell);
        } else if (hoveredBadge != null) {
            // NEI shows a small picture of the recipe here; for now just its type
            final RecipeView recipe = recipeOf(hoveredBadge);
            ItemTooltip.requestPlain(List.of(Component.literal(recipe == null ? "Recipe" : recipe.title())));
        } else if (hoveredNode != null) {
            final boolean root = view.graph.roots.contains(hoveredNode);
            ItemTooltip.request(stackOf(hoveredNode.itemId), root ? HotkeyHelp.Where.TREE_ROOT : HotkeyHelp.Where.TREE,
                    stateLines(hoveredNode));
        }
    }

    /** NEI's stats slot tooltips: a handler's counts, a crafting entry's recipe type, or the item's own tooltip. */
    private void statsTooltip(StatsPanel.Cell cell) {
        if (cell.handler() != null) {
            ItemTooltip.requestPlain(List.of(Component.literal(cell.handler().name()),
                    gray("Unique Recipes: " + cell.handler().recipes()),
                    gray("Iterations: " + cell.handler().iterations())));
            return;
        }
        final ItemStack stack = stackOf(cell.entry().itemId());
        if (stack.isEmpty()) return;
        if (cell.entry().recipeId() != null) {
            ItemTooltip.requestPlain(List.of(stack.getHoverName(), gray(handlerName(cell.entry().itemId(), cell.entry().recipeId()))));
        } else {
            ItemTooltip.request(stack, HotkeyHelp.Where.TREE, List.of());
        }
    }

    /** NEI's {@code GuiCraftingTree#handleItemTooltip}: the lines right under the item's name, top to bottom. */
    private List<Component> stateLines(TreeSlot node) {
        final List<Component> lines = new ArrayList<>();
        final TreeGraph graph = view.graph;
        final boolean hasChildren = graph.childrenOf.containsKey(node);

        if (node.prefItem != null && hasChildren) {
            final long surplus = (long) node.prefItem.factor * node.multiplier - node.requestedAmount;
            if (surplus > 0) lines.add(gray("Extra produced: " + amountText(surplus)));
        }

        if (view.state.useInventorySnapshot && node.requestedAmount > 0) {
            if (node.leftAmount > 0 && hasChildren && !graph.childrenNeedCraftingCache.contains(node)) {
                lines.add(gray("Can be crafted"));
            }

            if (node.leftAmount == 0) {
                final ItemStack stack = stackOf(node.itemId);
                lines.add(gray("In inventory: " + (stack.isEmpty() ? node.itemId : stack.getHoverName().getString())));
            } else if (node.leftAmount != node.requestedAmount) {
                lines.add(gray("Still needed: " + amountText(node.leftAmount)));
            }
        }

        return lines;
    }

    // ---- input ----

    /** A mouse press anywhere while the window is open. The caller always consumes it. */
    void mouseClicked(double mx, double my, int button) {
        // NEI: a click on the field focuses it (right click clears it), a click elsewhere leaves it
        final boolean overField = Draw.in(mx, my, search.getX(), search.getY(), search.getWidth(), search.getHeight());
        search.setFocused(overField);
        if (overField) {
            if (button == 1) search.setValue("");
            return;
        }
        if (menuClicked(mx, my)) return;

        final StatsPanel.Cell cell = stats.cellAt(mx, my);
        if (cell != null) {
            statsClicked(cell, button);
            return;
        }
        if (stats.contains(mx, my)) return; // the gaps of the panel do nothing

        if (button == 0) {
            final int b = buttonAt(mx, my);
            if (b >= 0) {
                toolbarAction(b);
                return;
            }
        }

        final TreeCamera camera = view.camera();
        if (view.isEmpty() || !camera.isInWidget(mx, my)) return;

        final TreeLayout layout = view.layout;
        final float wx = camera.worldX(mx);
        final float wy = camera.worldY(my);
        final TreeSlot badge = layout.badgeAt(wx, wy, camera);
        final TreeSlot node = badge != null ? null : layout.nodeAt(wx, wy, camera);

        if (node != null) {
            // As in NEI, a left click on a box with a recipe below it marks the box the next picked recipe replaces.
            // (NEI leaves an old mark standing after other clicks; here they clear it.)
            replaceNode = button == 0 && view.graph.childrenOf.containsKey(node) ? node : null;

            if (button == 0 || button == 1) {
                final ItemStack stack = stackOf(node.itemId);
                if (!stack.isEmpty()) opener.open(stack.getItem(), button == 1);
            }
            return;
        }

        if (button != 0) return;

        if (badge != null) {
            refreshInventory();
            view.removeRecipeOf(badge);
            Bookmarks.save();
            return;
        }

        final TreeSlot arrow = layout.arrowAt(wx, wy, camera);
        if (arrow != null) {
            view.toggleCollapsed(arrow);
            return;
        }

        camera.startDrag(mx, my);
    }

    void mouseReleased() {
        view.camera().endDrag();
    }

    private void toolbarAction(int button) {
        switch (button) {
            case BTN_INV -> {
                view.state.toggleUseInventorySnapshot();
                refreshInventory();
                view.rebuild(null);
            }
            case BTN_FIT -> view.layout.fitToView(view.graph, view.camera());
            case BTN_COLLAPSE -> {
                view.state.cycleCollapseMode();
                view.updateCollapsedItems();
                view.layout.fitToView(view.graph, view.camera());
            }
            case BTN_STATS -> view.state.statsVisible = !view.state.statsVisible;
            case BTN_LINK -> ChatLinks.sendGroup(view.groupId);
            case BTN_SHOT -> {
                if (!view.isEmpty()) shot = TreeShot.begin(view.camera(), view.layout, stats.contentArea());
            }
            case BTN_SECTIONS -> {
                menuOpen = !menuOpen;
                menuX = buttonX(BTN_SECTIONS);
                menuY = buttonY() + BTN + 2;
                menuW = MENU_MIN_W;
                final Font font = Minecraft.getInstance().font;
                for (TreeStats.Section s : TreeStats.Section.values()) {
                    menuW = Math.max(menuW, font.width(sectionLabel(s)) + MENU_PAD * 2);
                }
            }
            default -> {}
        }
    }

    // NEI's StatsSectionsMenu
    private static final int MENU_ROW = 16;
    private static final int MENU_TOP = 4;
    private static final int MENU_PAD = 10;
    private static final int MENU_MIN_W = 70;

    private static String sectionLabel(TreeStats.Section section) {
        return switch (section) {
            case INGREDIENTS_NEEDED -> "Ingredients Needed";
            case INGREDIENTS_AVAILABLE -> "Ingredients Available";
            case CRAFTING_NEEDED -> "Crafting Needed";
            case CRAFTING_AVAILABLE -> "Crafting Available";
            case RESULTS -> "Results";
            case REMAINDERS -> "Remainders";
            case HANDLERS -> "Handlers";
        };
    }

    private void drawMenu(GuiGraphicsExtractor g, Font font, int mx, int my) {
        if (!menuOpen) return;
        final TreeStats.Section[] sections = TreeStats.Section.values();

        for (int i = 0; i < sections.length; i++) {
            final int ry = menuY + MENU_TOP + i * MENU_ROW;
            final boolean over = Draw.in(mx, my, menuX, ry, menuW, MENU_ROW);
            final boolean on = view.state.statsSections.contains(sections[i]);

            if (on) {
                Draw.button(g, menuX, ry, menuW, MENU_ROW, over);
            } else { // a section that is switched off looks dark, like a hidden subset
                g.fill(menuX, ry, menuX + menuW, ry + MENU_ROW, 0xFF000000);
                g.fill(menuX + 1, ry + 1, menuX + menuW - 1, ry + MENU_ROW - 1, 0xFF161D23);
            }
            final String label = sectionLabel(sections[i]);
            g.text(font, Component.literal(label), menuX + (menuW - font.width(label)) / 2, ry + (MENU_ROW - 8) / 2,
                    over ? 0xFFFFFFA0 : on ? 0xFFE0E0E0 : 0xFFA0A0A0, true);
        }
    }

    /** True if the click was on the open menu (a click anywhere else closes it and goes on). */
    private boolean menuClicked(double mx, double my) {
        if (!menuOpen) return false;
        final TreeStats.Section[] sections = TreeStats.Section.values();
        final double rel = my - menuY - MENU_TOP;

        if (Draw.in(mx, my, menuX, menuY, menuW, MENU_TOP + sections.length * MENU_ROW)) {
            final int index = (int) (rel / MENU_ROW);
            if (rel >= 0 && index < sections.length) view.state.toggleStatsSection(sections[index]);
            return true;
        }
        if (buttonAt(mx, my) != BTN_SECTIONS) menuOpen = false;
        return false;
    }

    /** The wheel. Outside the tree area it does nothing (the window swallows it). */
    void scrolled(double mx, double my, double dy, boolean ctrl, boolean shift, boolean alt) {
        if (stats.scrolled(mx, my, dy)) return;

        final TreeCamera camera = view.camera();
        if (view.isEmpty() || !camera.isInWidget(mx, my)) return;

        final int direction = dy > 0 ? 1 : -1;

        if (ctrl) {
            final TreeSlot node = view.layout.nodeAt(camera.worldX(mx), camera.worldY(my), camera);
            if (node != null && view.graph.roots.contains(node) && node.ingrItem.recipeId != null) {
                // Alt makes one step the item's stack size (NEI: getScrollMultiplier)
                final long step = alt ? Math.max(1, stackOf(node.itemId).getMaxStackSize()) : 1;
                refreshInventory();
                view.shiftRoot(node, step * direction);
                Bookmarks.save();
                return;
            }
        }

        camera.scroll(mx, my, direction, shift, ctrl);
    }

    /**
     * The "+" in the recipe window opened from here (NEI's {@code TreeOverlayButton} / {@code addRecipe}): the recipe
     * joins the group, and takes over from the recipes the replaced box needed.
     */
    void addRecipe(RecipeView recipe) {
        final TreeSlot replace = replaceNode;
        replaceNode = null;

        refreshInventory();
        if (view.addRecipe(RecipeBookmarks.spec(recipe, null, null), replace)) Bookmarks.save();
    }
}
