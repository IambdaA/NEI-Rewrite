package codechicken.nei.item;

import java.util.List;
import java.util.Map;

/**
 * The {@code /give} commands behind the enchantment and potion windows. Plain Java, no game classes.
 *
 * Both use the item component syntax of recent Minecraft versions ({@code enchantments={"minecraft:sharpness":5}},
 * {@code potion_contents={custom_effects:[...]}}); if a later version changes it, only this class needs to change.
 */
public final class GiveCommands {

    public record Effect(String id, int amplifier, int seconds) {}

    private GiveCommands() {}

    /** One enchanted item; levels of 0 are left out. */
    public static String enchanted(String itemId, Map<String, Integer> levels) {
        final StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : levels.entrySet()) {
            if (e.getValue() <= 0) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append('"').append(e.getKey()).append("\":").append(e.getValue());
        }
        return "give @s " + itemId + (sb.length() == 0 ? "" : "[enchantments={" + sb + "}]");
    }

    /** One potion (or splash / lingering potion, tipped arrow: {@code itemId}) with the given effects. */
    public static String potion(String itemId, List<Effect> effects) {
        final StringBuilder sb = new StringBuilder();
        for (Effect e : effects) {
            if (sb.length() > 0) sb.append(',');
            sb.append("{id:\"").append(e.id()).append("\",amplifier:").append(e.amplifier()).append(",duration:")
                    .append(e.seconds() * 20).append('}');
        }
        return "give @s " + itemId + (sb.length() == 0 ? "" : "[potion_contents={custom_effects:[" + sb + "]}]");
    }
}
