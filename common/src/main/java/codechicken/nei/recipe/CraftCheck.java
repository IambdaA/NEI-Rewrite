package codechicken.nei.recipe;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The "can I craft this from my inventory" test of NEI's craftables panel ({@code existsIngredients}), on plain ids. */
public final class CraftCheck {

    /** One ingredient slot: any of {@code options}, {@code count} of it. */
    public record Need(List<String> options, int count) {}

    private CraftCheck() {}

    /** True if every ingredient can be taken from the inventory, each item used up as it is taken. */
    public static boolean craftable(List<Need> needs, Map<String, Integer> inventory) {
        if (needs.isEmpty()) return false;

        final Map<String, Integer> left = new HashMap<>(inventory);
        for (Need need : needs) {
            String used = null;
            for (String option : need.options()) {
                if (left.getOrDefault(option, 0) >= need.count()) {
                    used = option;
                    break;
                }
            }
            if (used == null) return false;
            left.merge(used, -need.count(), Integer::sum);
        }
        return true;
    }
}
