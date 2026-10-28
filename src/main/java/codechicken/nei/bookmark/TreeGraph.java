package codechicken.nei.bookmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The boxes of the crafting tree and how they hang together. Plain Java. Ported from GTNH NEI's
 * {@code CraftingTreeGraph}.
 */
public final class TreeGraph {

    public final List<TreeSlot> roots = new ArrayList<>();
    public final List<TreeSlot> items = new ArrayList<>();
    public final Map<TreeSlot, TreeSlot> parentOf = new HashMap<>();
    public final Map<TreeSlot, List<TreeSlot>> childrenOf = new HashMap<>();

    /** Boxes with something still missing somewhere below them. */
    public final Set<TreeSlot> subtreeMissingCache = new HashSet<>();
    /** Boxes with at least one child that still has to be gathered or made. */
    public final Set<TreeSlot> childrenNeedCraftingCache = new HashSet<>();

    public void rebuild(TreeMath math) {
        subtreeMissingCache.clear();
        childrenNeedCraftingCache.clear();

        roots.clear();
        roots.addAll(math.rootSlots);
        items.clear();
        items.addAll(math.items);
        parentOf.clear();
        parentOf.putAll(math.parentOf);
        childrenOf.clear();
        childrenOf.putAll(math.childrenOf);

        for (TreeSlot root : roots) computeSubtreeMissing(root);

        for (Map.Entry<TreeSlot, List<TreeSlot>> entry : childrenOf.entrySet()) {
            if (entry.getValue().stream().anyMatch(child -> child.leftAmount != 0)) {
                childrenNeedCraftingCache.add(entry.getKey());
            }
        }
    }

    /**
     * The recipes that would no longer be used if the recipe that makes {@code node} went away: that recipe (when no
     * other box uses it) and every recipe below it that only it needed.
     */
    public Set<String> collectUnusedRecipes(TreeSlot node) {
        final Map<String, Set<String>> children = new HashMap<>();
        final Map<String, Set<String>> consumers = new HashMap<>();
        int rootCount = 0;

        for (TreeSlot item : items) {
            if (item.prefItem != null) {
                children.computeIfAbsent(item.ingrItem.recipeId, k -> new HashSet<>()).add(item.prefItem.recipeId);
                consumers.computeIfAbsent(item.prefItem.recipeId, k -> new HashSet<>()).add(item.ingrItem.recipeId);

                if (item.ingrItem.recipeId.equals(node.prefItem.recipeId)) rootCount++;
            }
        }

        final Set<String> unused = new HashSet<>();
        final String rootRecipeId = node.prefItem.recipeId;

        if (rootCount <= 1) {
            unused.add(rootRecipeId);
            collectSubtree(rootRecipeId, children, consumers, unused);
        }

        return unused;
    }

    private void collectSubtree(String recipeId, Map<String, Set<String>> children, Map<String, Set<String>> consumers,
            Set<String> unused) {
        for (String child : children.getOrDefault(recipeId, Collections.emptySet())) {
            if (consumers.get(child).size() == 1 && unused.add(child)) collectSubtree(child, children, consumers, unused);
        }
    }

    private boolean computeSubtreeMissing(TreeSlot node) {
        boolean hasMissing = !childrenOf.containsKey(node);

        for (TreeSlot child : childrenOf.getOrDefault(node, Collections.emptyList())) {
            hasMissing |= child.leftAmount != 0 && computeSubtreeMissing(child);
        }

        if (hasMissing) subtreeMissingCache.add(node);
        return hasMissing;
    }
}
