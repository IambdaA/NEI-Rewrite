package codechicken.nei.bookmark;

import java.util.Collections;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * What the crafting tree window remembers about one bookmark group between openings: where the camera was, which boxes
 * are collapsed, and the toolbar toggles. Plain Java. Ported from GTNH NEI's {@code CraftingTreeState} (NEI also saves
 * the toolbar toggles in the world's data; here they last until the game is closed).
 */
public final class TreeState {

    /** The toolbar's collapse button cycles through these. */
    public enum CollapseMode {
        /** Everything open. */
        EXPANDED,
        /** Boxes that have nothing left to craft below them are closed. */
        SMART,
        /** Everything below the roots is closed. */
        ALL
    }

    private static final Map<BookmarkGrid.Group, TreeState> GROUP_STATES = new WeakHashMap<>();

    public CollapseMode collapseMode = CollapseMode.EXPANDED;
    public boolean useInventorySnapshot;

    /** The stats panel under the tree and which of its sections show (NEI: {@code visibleStatsSections}). */
    public boolean statsVisible = true;
    public final Set<TreeStats.Section> statsSections = EnumSet.allOf(TreeStats.Section.class);

    public boolean initialized;
    public final TreeCamera camera = new TreeCamera();
    /** Boxes (by their bookmark item) whose children are hidden. */
    public final Set<BookmarkGrid.Item> collapsedItems = Collections.newSetFromMap(new IdentityHashMap<>());

    public void toggleUseInventorySnapshot() {
        useInventorySnapshot = !useInventorySnapshot;
    }

    public void toggleStatsSection(TreeStats.Section section) {
        if (!statsSections.remove(section)) statsSections.add(section);
    }

    public void cycleCollapseMode() {
        final CollapseMode[] modes = CollapseMode.values();
        collapseMode = modes[(collapseMode.ordinal() + 1) % modes.length];
    }

    public static TreeState of(BookmarkGrid.Group group) {
        return GROUP_STATES.computeIfAbsent(group, g -> new TreeState());
    }
}
