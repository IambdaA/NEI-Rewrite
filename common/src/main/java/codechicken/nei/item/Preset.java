package codechicken.nei.item;

import java.util.LinkedHashSet;
import java.util.Set;

/** One NEI preset ({@code PresetsList.Preset}): a named set of items with a mode. Items are registry ids. */
public final class Preset {

    /** HIDE: out of the item list. REMOVE: out of the list and their recipes. SUBSET: a custom subset. GROUP: a custom collapsible group. */
    public enum Mode {
        SUBSET, HIDE, REMOVE, GROUP
    }

    public String name = "";
    public boolean enabled = true;
    public Mode mode = Mode.HIDE;
    public final Set<String> items = new LinkedHashSet<>();

    public Preset copy() {
        final Preset p = new Preset();
        p.name = name;
        p.enabled = enabled;
        p.mode = mode;
        p.items.addAll(items);
        return p;
    }
}
