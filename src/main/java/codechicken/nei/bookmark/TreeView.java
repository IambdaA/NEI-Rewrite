package codechicken.nei.bookmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Everything the crafting tree window shows for one bookmark group, without any drawing: the maths, the boxes, their
 * places, the camera and the edits the window can make. Plain Java. Ported from the logic of GTNH NEI's
 * {@code GuiCraftingTree}.
 *
 * The caller saves the bookmarks after anything that returns true / changes the grid.
 */
public final class TreeView {

    public final BookmarkGrid grid;
    public final int groupId;
    public final TreeState state;
    public final TreeGraph graph = new TreeGraph();
    public final TreeLayout layout = new TreeLayout();
    public TreeMath math;

    private List<BookmarkGrid.Item> inventory = new ArrayList<>();

    public TreeView(BookmarkGrid grid, int groupId) {
        this.grid = grid;
        this.groupId = groupId;
        this.state = TreeState.of(grid.group(groupId));
    }

    public TreeCamera camera() {
        return state.camera;
    }

    /** False once the group has lost all its items (the window closes then). */
    public boolean groupExists() {
        return grid.groups.containsKey(groupId) && !grid.groupItems(groupId).isEmpty();
    }

    /** The player's inventory as plain items (amount = how many), used while "use inventory snapshot" is on. */
    public void setInventory(List<BookmarkGrid.Item> items) {
        inventory = new ArrayList<>(items);
    }

    /** NEI's {@code rebuildTree}. The viewport must be set on the camera before the first call. */
    public void rebuild(TreeSlot anchor) {
        final List<BookmarkGrid.Item> chainItems = grid.group(groupId).crafting ? grid.groupItems(groupId)
                : Collections.emptyList();

        final Set<String> collapsedRecipes = grid.group(groupId).collapsedRecipes;
        math = TreeMath.of(chainItems, state.useInventorySnapshot ? inventory : Collections.emptyList(), collapsedRecipes);
        layout.collapsedRecipes = collapsedRecipes;
        graph.rebuild(math);

        if (!state.initialized) {
            updateCollapsedItems();
        } else {
            layout.recalculate(graph, state.collapsedItems, anchor, state.camera);
        }

        if (!state.initialized) {
            state.initialized = true;
            layout.fitToView(graph, state.camera);
        }
    }

    public boolean isEmpty() {
        return math == null || !math.hasOutputs();
    }

    /** NEI's {@code updateCollapsedItems}: applies the collapse mode of the toolbar button. */
    public void updateCollapsedItems() {
        state.collapsedItems.clear();

        for (TreeSlot node : graph.childrenOf.keySet()) {
            if (graph.roots.contains(node)) continue;

            final boolean collapse = switch (state.collapseMode) {
                case EXPANDED -> false;
                case SMART -> !graph.childrenNeedCraftingCache.contains(node);
                case ALL -> true;
            };

            if (collapse) state.collapsedItems.add(node.ingrItem);
        }

        layout.recalculate(graph, state.collapsedItems, null, state.camera);
    }

    /** The arrow under a box: hides / shows what is below it. */
    public void toggleCollapsed(TreeSlot node) {
        if (!state.collapsedItems.remove(node.ingrItem)) state.collapsedItems.add(node.ingrItem);
        layout.recalculate(graph, state.collapsedItems, node, state.camera);
    }

    public boolean isCollapsed(TreeSlot node) {
        return state.collapsedItems.contains(node.ingrItem);
    }

    /** Ctrl + wheel over a root: how many times its recipe is made. {@code shift} is +-1 times the step. */
    public void shiftRoot(TreeSlot root, long shift) {
        final String recipeId = root.ingrItem.recipeId;
        if (recipeId == null) return;
        grid.shiftRecipeAmount(groupId, recipeId, shift);
        rebuild(null);
    }

    /**
     * The red cross on a box's badge: removes the recipe that makes it and every recipe below it nothing else needs.
     */
    public void removeRecipeOf(TreeSlot node) {
        if (node.prefItem == null) return;
        final String recipeId = node.prefItem.recipeId;

        grid.removeSlot(node.prefItem, false);

        if (!grid.existsRecipe(recipeId, groupId)) {
            for (String unused : graph.collectUnusedRecipes(node)) grid.removeRecipe(unused, groupId);
        }

        if (groupExists()) rebuild(null);
    }

    /**
     * NEI's {@code addRecipe}: the recipe picked in the recipe window becomes the one for {@code replace} (or just joins
     * the group when no box was chosen). The recipes only the old one needed are dropped.
     */
    public boolean addRecipe(BookmarkGrid.RecipeSpec spec, TreeSlot replace) {
        boolean changed = false;

        if (replace != null && replace.prefItem != null) {
            final Set<String> unused = graph.collectUnusedRecipes(replace);
            unused.remove(spec.recipeId());
            for (String id : unused) changed |= grid.removeRecipe(id, groupId);
        }

        if (!grid.existsRecipe(spec.recipeId(), groupId)) {
            grid.addRecipe(spec.recipeId(), spec.results(), spec.ingredients(), 1, groupId);
            changed = true;
        }

        if (changed && groupExists()) rebuild(null);
        return changed;
    }
}
