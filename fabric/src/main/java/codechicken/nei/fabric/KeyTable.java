package codechicken.nei.fabric;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;

import codechicken.nei.NotEnoughItems;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * Vanilla keeps one key mapping per key (a static table in {@code KeyMapping}). NEI's hotkeys (R, U, A, ...) only act in
 * screens and NEI compares keys itself, but if one of them sits in that table it takes the key away from the game's own
 * control (A = walk left). NeoForge solves this with key conflict contexts; on Fabric {@link #fix} takes the GUI-only
 * mappings out of the table again and puts the mapping that was there before back.
 *
 * The table is found by its type (a static Map keyed by {@code InputConstants.Key}) so no field name is needed. If the
 * table looks different in this version nothing is changed (the keys then conflict, but nothing breaks).
 */
public final class KeyTable {

    private static final List<KeyMapping> GUI_ONLY = new ArrayList<>();
    private static Map<Object, Object> table;
    private static boolean searched;

    private KeyTable() {}

    /** Remembers a hotkey that must stay out of the table. */
    static void register(KeyMapping mapping) {
        GUI_ONLY.add(mapping);
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> table() {
        if (!searched) {
            searched = true;
            for (Field field : KeyMapping.class.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || !Map.class.isAssignableFrom(field.getType())) continue;
                if (!(field.getGenericType() instanceof ParameterizedType p)) continue;
                final Type[] args = p.getActualTypeArguments();
                if (args.length == 2 && args[0] == InputConstants.Key.class) {
                    try {
                        field.setAccessible(true);
                        table = (Map<Object, Object>) field.get(null); // KeyMapping, or a list of them, per key
                    } catch (ReflectiveOperationException | RuntimeException e) {
                        table = null;
                    }
                    break;
                }
            }
            if (table == null) NotEnoughItems.LOGGER.info("NEI Rewrite: key table not found, GUI-only hotkeys may share keys with the game");
        }
        return table;
    }

    public static void fix() {
        try {
            final Map<Object, Object> map = table();
            final var options = Minecraft.getInstance().options;
            if (map == null || options == null || GUI_ONLY.isEmpty()) return;

            for (KeyMapping gui : GUI_ONLY) {
                final InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(gui);
                final Object current = map.get(key);
                if (current instanceof KeyMapping single) {
                    if (!GUI_ONLY.contains(single)) continue;
                    map.remove(key);
                    for (KeyMapping other : options.keyMappings) {
                        if (!GUI_ONLY.contains(other) && KeyMappingHelper.getBoundKeyOf(other).equals(key)) {
                            map.put(key, other);
                            break;
                        }
                    }
                } else if (current instanceof java.util.List<?> list) {
                    final java.util.List<Object> kept = new ArrayList<>();
                    for (Object o : list) if (!(o instanceof KeyMapping m && GUI_ONLY.contains(m))) kept.add(o);
                    if (kept.size() == list.size()) continue;
                    if (kept.isEmpty()) map.remove(key);
                    else map.put(key, kept);
                }
            }
        } catch (RuntimeException | LinkageError e) {
            NotEnoughItems.LOGGER.warn("NEI Rewrite: could not fix the key table ({})", e.toString());
        }
    }
}
