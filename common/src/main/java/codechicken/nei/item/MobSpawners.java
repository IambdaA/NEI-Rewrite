package codechicken.nei.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import codechicken.nei.NotEnoughItems;

/**
 * As in NEI ({@code ItemMobSpawner.loadSpawners / getSubItems}): one spawner for every mob in the item list. NEI skips
 * what is not a living mob and the ender dragon and giant; here that is what has no mob category (items, boats, markers ...)
 * and the same two.
 *
 * The stack is a normal spawner with the mob in its block entity data (what the vanilla spawner saves, so its tooltip
 * shows the mob's name). Placing it makes a spawner for that mob; NEI sets the mob after placing, the data does that here.
 */
public final class MobSpawners {

    private static final Set<String> SKIPPED = Set.of("minecraft:ender_dragon", "minecraft:giant");

    private MobSpawners() {}

    /** The block entity data of a spawner for {@code entityId}: {@code SpawnData} and a one entry {@code SpawnPotentials}. */
    static CompoundTag data(String entityId) {
        final CompoundTag entity = new CompoundTag();
        entity.putString("id", entityId);
        final CompoundTag spawnData = new CompoundTag();
        spawnData.put("entity", entity.copy());

        final CompoundTag potential = new CompoundTag();
        potential.putInt("weight", 1);
        final CompoundTag potentialData = new CompoundTag();
        potentialData.put("entity", entity.copy());
        potential.put("data", potentialData);
        final ListTag potentials = new ListTag();
        potentials.add(potential);

        final CompoundTag data = new CompoundTag();
        data.putString("id", "minecraft:mob_spawner");
        data.put("SpawnData", spawnData);
        data.put("SpawnPotentials", potentials);
        return data;
    }

    /** A spawner item that carries the mob in its block entity data ({@code BlockEntityTag}). */
    static ItemStack item(String entityId) {
        final CompoundTag data = data(entityId);
        data.remove("id");
        final ItemStack stack = new ItemStack(Items.SPAWNER);
        stack.getOrCreateTag().put("BlockEntityTag", data);
        return stack;
    }

    /** A spawner for every mob. Empty if the stacks cannot be built (the plain spawner stays in the list). */
    public static List<ItemStack> stacks() {
        final List<ItemStack> list = new ArrayList<>();
        try {
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                final String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
                if (type.getCategory() == MobCategory.MISC || !type.canSummon() || SKIPPED.contains(id)) continue;
                list.add(item(id));
            }
        } catch (RuntimeException e) {
            NotEnoughItems.LOGGER.warn("Could not build the mob spawner items: {}", e.toString());
        }
        return list;
    }
}
