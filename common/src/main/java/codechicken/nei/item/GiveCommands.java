package codechicken.nei.item;

import java.util.List;
import java.util.Map;

/**
 * The {@code /give} commands behind the enchantment and potion windows. Plain Java, no game classes.
 *
 * 1.20.1 syntax: enchantments in {@code {Enchantments:[{id:"minecraft:sharpness",lvl:5s}]}} (books: {@code StoredEnchantments}), potion
 * effects in {@code {CustomPotionEffects:[{Id:1,Amplifier:0,Duration:600}]}} with the effect's numeric id.
 */
public final class GiveCommands {

    public record Effect(int id, int amplifier, int seconds) {}

    private GiveCommands() {}

    /** One enchanted item; levels of 0 are left out. */
    public static String enchanted(String itemId, Map<String, Integer> levels) {
        final StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : levels.entrySet()) {
            if (e.getValue() <= 0) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append("{id:\"").append(e.getKey()).append("\",lvl:").append(e.getValue()).append("s}");
        }
        final String tag = itemId.equals("minecraft:enchanted_book") ? "StoredEnchantments" : "Enchantments";
        return "give @s " + itemId + (sb.length() == 0 ? "" : "{" + tag + ":[" + sb + "]}");
    }

    /** One potion (or splash / lingering potion, tipped arrow: {@code itemId}) with the given effects. */
    public static String potion(String itemId, List<Effect> effects) {
        final StringBuilder sb = new StringBuilder();
        for (Effect e : effects) {
            if (sb.length() > 0) sb.append(',');
            sb.append("{Id:").append(e.id()).append(",Amplifier:").append(e.amplifier()).append("b,Duration:")
                    .append(e.seconds() * 20).append('}');
        }
        return "give @s " + itemId + (sb.length() == 0 ? "" : "{CustomPotionEffects:[" + sb + "]}");
    }
}
