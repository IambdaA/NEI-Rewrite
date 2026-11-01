package codechicken.nei.fabric;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import codechicken.nei.NotEnoughItems;
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

    private static boolean reported;
    private static boolean notFoundReported;

    /** A mapping the table must not keep for a key that the game also uses: NEI's own, and JEI's (GUI-only too). */
    private static boolean guiOnly(KeyMapping m) {
        return GUI_ONLY.contains(m) || m.getName().startsWith("key.jei.");
    }

    public static void fix() {
        try {
            final Map<InputConstants.Key, KeyMapping> map = table();
            final var options = Minecraft.getInstance().options;
            if (map == null) {
                if (!notFoundReported) {
                    notFoundReported = true;
                    NotEnoughItems.LOGGER.warn("NEI Rewrite: the key table was not found, NEI's hotkeys may take keys from the game (A = walk left)");
                }
                return;
            }
            if (options == null || GUI_ONLY.isEmpty()) return;

            int changed = 0;
            for (var entry : new ArrayList<>(map.entrySet())) {
                final KeyMapping current = entry.getValue();
                if (!guiOnly(current)) continue;
                // the mapping of the game (or another mod) bound to that key takes it back; vanilla's own first
                KeyMapping back = null;
                for (KeyMapping other : options.keyMappings) {
                    if (guiOnly(other) || !InputConstants.getKey(other.saveString()).equals(entry.getKey())) continue;
                    if (back == null || other.getCategory().startsWith("key.categories.")) back = other;
                    if (back == other && other.getCategory().startsWith("key.categories.")) break;
                }
                if (back != null) map.put(entry.getKey(), back);
                else map.remove(entry.getKey());
                changed++;
            }
            if (changed > 0 && !reported) {
                reported = true;
                NotEnoughItems.LOGGER.info("NEI Rewrite: gave {} key(s) back to the game's own controls", changed);
            }
        } catch (RuntimeException | LinkageError e) {
            if (!notFoundReported) {
                notFoundReported = true;
                NotEnoughItems.LOGGER.warn("NEI Rewrite: could not fix the key table ({})", e.toString());
            }
        }
    }
}
