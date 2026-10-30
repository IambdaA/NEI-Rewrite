package codechicken.nei.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;

/**
 * As in NEI ({@code ItemMobSpawner.addInformation}, {@code SpawnerRenderer}): the mob of a spawner of the item list shows in
 * the tooltip in red (monsters) or aqua (everything else), and inside the spawner's icon.
 *
 * Differences: NEI draws the mob itself, turning, inside the cage. Here the mob's spawn egg is drawn small in the middle of the
 * icon (drawing a live entity inside an item icon needs the entity render pipeline, which was not available to check). The
 * mob is found from the tooltip's name line, so no block data has to be read.
 */
final class SpawnerLook {

    /** Display name to mob; built on first use. */
    private static Map<String, EntityType<?>> byName;
    /** The mob of each spawner stack of the list (by instance: the list keeps its stacks). */
    private static final Map<ItemStack, Optional<EntityType<?>>> MOBS = Collections.synchronizedMap(new WeakHashMap<>());

    private SpawnerLook() {}

    private static synchronized Map<String, EntityType<?>> names() {
        if (byName == null) {
            final Map<String, EntityType<?>> map = new HashMap<>();
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) map.putIfAbsent(type.getDescription().getString(), type);
            byName = map;
        }
        return byName;
    }

    /** The mob a spawner of the list makes, or null for other items and spawners without one. */
    static EntityType<?> mobOf(ItemStack stack) {
        if (!stack.is(Items.SPAWNER)) return null;
        return MOBS.computeIfAbsent(stack, s -> {
            try {
                for (Component line : s.getTooltipLines(Item.TooltipContext.EMPTY, Minecraft.getInstance().player, TooltipFlag.NORMAL)) {
                    final EntityType<?> type = names().get(line.getString());
                    if (type != null) return Optional.of(type);
                }
            } catch (RuntimeException e) {
                // no mob then
            }
            return Optional.empty();
        }).orElse(null);
    }

    /** NEI's tooltip line: the mob's name in dark red for monsters, dark aqua for the rest. */
    static List<Component> recolored(ItemStack stack, List<Component> lines) {
        final EntityType<?> mob = mobOf(stack);
        if (mob == null) return lines;
        final String name = mob.getDescription().getString();
        final int color = mob.getCategory() == MobCategory.MONSTER ? 0xAA0000 : 0x00AAAA;
        final List<Component> result = new ArrayList<>(lines.size());
        boolean done = false;
        for (Component line : lines) {
            if (!done && line.getString().equals(name)) {
                result.add(Component.literal(name).withStyle(s -> s.withColor(color)));
                done = true;
            } else {
                result.add(line);
            }
        }
        return result;
    }

    /** The mob's spawn egg, drawn small over the middle of the spawner icon at {@code (x, y)}. */
    static void badge(GuiGraphics g, ItemStack stack, int x, int y) {
        final EntityType<?> mob = mobOf(stack);
        if (mob == null) return;
        final ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(mob);
        final ResourceLocation eggId = ResourceLocation.fromNamespaceAndPath(key.getNamespace(), key.getPath() + "_spawn_egg");
        if (!BuiltInRegistries.ITEM.containsKey(eggId)) return; // not every mob has an egg
        final ItemStack egg = new ItemStack(BuiltInRegistries.ITEM.get(eggId));
        g.pose().pushPose();
        g.pose().translate(x + 4, y + 4, 0f);
        g.pose().scale(0.5f, 0.5f, 1f);
        Gfx.item(g, egg, 0, 0);
        g.pose().popPose();
    }
}
