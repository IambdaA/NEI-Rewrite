package codechicken.nei.fabric;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Vanilla keeps one key mapping per key (the table {@code KeyMapping.MAP}). NEI's hotkeys (R, U, A, ...) only act in screens
 * and NEI compares keys itself, but if one of them sits in that table it takes the key away from the game's own control (A =
 * walk left). NeoForge solves this with key conflict contexts; on Fabric {@link #fix} takes the GUI-only mappings out of the
 * table again and puts the mapping that was there before back.
 *
 * The table is found by its type (a static Map keyed by {@code InputConstants.Key}) so no field name is needed: the names differ
 * between development (Mojang names) and a normal game (intermediary names).
 */
public final class KeyTable {

    private static final List<KeyMapping> GUI_ONLY = new ArrayList<>();
    private static Map<InputConstants.Key, KeyMapping> table;
    private static boolean searched;

    private KeyTable() {}

    /** Remembers a hotkey that must stay out of the table. */
    static void register(KeyMapping mapping) {
        GUI_ONLY.add(mapping);
    }

    @SuppressWarnings("unchecked")
    private static Map<InputConstants.Key, KeyMapping> table() {
        if (!searched) {
            searched = true;
            for (Field field : KeyMapping.class.getDeclaredFields()) {
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers()) || !Map.class.isAssignableFrom(field.getType())) continue;
                if (!(field.getGenericType() instanceof ParameterizedType p)) continue;
                final Type[] args = p.getActualTypeArguments();
                if (args.length == 2 && args[0] == InputConstants.Key.class) {
                    try {
                        field.setAccessible(true);
                        table = (Map<InputConstants.Key, KeyMapping>) field.get(null);
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        table = null;
                    }
                    break;
                }
            }
        }
        return table;
    }

    public static void fix() {
        final Map<InputConstants.Key, KeyMapping> map = table();
        final var options = Minecraft.getInstance().options;
        if (map == null || options == null || GUI_ONLY.isEmpty()) return;

        for (KeyMapping gui : GUI_ONLY) {
            final String name = gui.saveString();
            final InputConstants.Key key = InputConstants.getKey(name);
            final KeyMapping current = map.get(key);
            if (current == null || !GUI_ONLY.contains(current)) continue;
            map.remove(key);
            for (KeyMapping other : options.keyMappings) {
                if (!GUI_ONLY.contains(other) && other.saveString().equals(name)) {
                    map.put(key, other);
                    break;
                }
            }
        }
    }
}
