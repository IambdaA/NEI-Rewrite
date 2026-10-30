package codechicken.nei.bookmark;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The chain maths, run once more while recording which box needs which. Plain Java.
 *
 * Ported from GTNH NEI's {@code CraftingTreeMath}: it walks the chain exactly like {@link ChainMath} but also builds
 * the tree. A box is made for every item the walk visits; a box for an ingredient made by another recipe of the chain
 * gets the ingredients of that recipe as children, even when the recipe has nothing left to make (those boxes show 0).
 *
 * Unlike the bookmark panel the tree starts with nothing in the player's pocket: the plain items of the group are
 * ignored. {@code inventory} stands for the snapshot of the player's inventory ("Recompute tree using player
 * inventory") and may be empty.
 */
public final class TreeMath extends ChainMath {

    public final List<TreeSlot> rootSlots = new ArrayList<>();
    public final List<TreeSlot> items = new ArrayList<>();
    public final Map<TreeSlot, TreeSlot> parentOf = new HashMap<>();
    public final Map<TreeSlot, List<TreeSlot>> childrenOf = new HashMap<>();

    private final Deque<TreeSlot> parentStack = new ArrayDeque<>();
    private TreeSlot lastItem;

    private TreeMath() {}

    /**
     * @param groupItems the items of one crafting chain group, in list order
     * @param inventory  what the player has: plain items (type 0) whose amount is how many
     */
    public static TreeMath of(List<BookmarkGrid.Item> groupItems, List<BookmarkGrid.Item> inventory) {
        return of(groupItems, inventory, Set.of());
    }

    /** @param collapsed the group's folded recipes: each is a root of the tree of its own */
    public static TreeMath of(List<BookmarkGrid.Item> groupItems, List<BookmarkGrid.Item> inventory, Set<String> collapsed) {
        final TreeMath math = new TreeMath();
        math.build(groupItems, collapsed);
        math.initialItems.clear();
        for (BookmarkGrid.Item item : inventory) math.initialItems.add(new Node(item, item.amount));
        math.refresh();
        return math;
    }

    /** True when the group has at least one recipe to show. */
    public boolean hasOutputs() {
        return !outputMultipliers.isEmpty();
    }

    @Override
    protected void refresh() {
        rootSlots.clear();
        items.clear();
        parentOf.clear();
        childrenOf.clear();
        parentStack.clear();
        lastItem = null;
        super.refresh();
    }

    @Override
    protected void calculationStepTrigger(Node ingr, Node pref, long requested, long containerAmount,
            long inventoryAmount, long ingrAmount, long produced) {

        if (ingr.type == 1) {
            // an output recipe's result: a root of the tree
            final long total = required.get(pref);
            final TreeSlot slot = new TreeSlot(ingr, Math.max(0, total - requested), requested, total, 0, pref);
            rootSlots.add(slot);
            items.add(slot);
            lastItem = slot;
        } else {
            final TreeSlot slot = new TreeSlot(ingr, 0, requested, produced, containerAmount, pref);
            items.add(slot);
            addChild(slot);
            lastItem = slot;
        }
    }

    private void addChild(TreeSlot slot) {
        final TreeSlot parent = parentStack.peek();
        parentOf.put(slot, parent);
        childrenOf.computeIfAbsent(parent, k -> new ArrayList<>()).add(slot);
    }

    @Override
    protected void prepareIngredients(String recipeId, long multiplier, List<String> visited) {
        parentStack.push(lastItem);
        super.prepareIngredients(recipeId, multiplier, visited);
        lastItem = parentStack.pop();
    }

    @Override
    protected void calculateSuitableRecipe(Node ingr, long ingrMultiplier, List<String> visited) {
        final Node pref = preferred.get(ingr);
        super.calculateSuitableRecipe(ingr, ingrMultiplier, visited);

        // the recipe had nothing left to make: still show what it is made of (all zero)
        if (pref != null && !isForeignBoxBoundary(ingr) && !visited.contains(pref.recipe)
                && !childrenOf.containsKey(lastItem)) {
            visited.add(pref.recipe);
            prepareIngredientsEmpty(pref.recipe, visited);
            visited.remove(pref.recipe);
        }
    }

    private void prepareIngredientsEmpty(String recipeId, List<String> visited) {
        parentStack.push(lastItem);

        for (Node item : ingredientsByRecipe.getOrDefault(recipeId, Collections.emptyList())) {
            if (!item.emptyFactor()) calculateEmpty(item, visited);
        }

        lastItem = parentStack.pop();
    }

    private void calculateEmpty(Node ingr, List<String> visited) {
        final Node pref = preferred.get(ingr);
        final TreeSlot slot = new TreeSlot(ingr, 0, 0, 0, 0, pref);

        items.add(slot);
        addChild(slot);
        lastItem = slot;

        if (pref != null && !isForeignBoxBoundary(ingr) && !visited.contains(pref.recipe)) {
            visited.add(pref.recipe);
            prepareIngredientsEmpty(pref.recipe, visited);
            visited.remove(pref.recipe);
        }
    }
}
