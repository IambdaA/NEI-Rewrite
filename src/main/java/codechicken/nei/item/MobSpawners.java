package codechicken.nei.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.mojang.serialization.DynamicOps;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;

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

    /** The item as the codec reads it: {@code {id, count, components:{"minecraft:block_entity_data": ...}}}. */
    static CompoundTag item(String entityId) {
        final CompoundTag components = new CompoundTag();
        components.put("minecraft:block_entity_data", data(entityId));
        final CompoundTag root = new CompoundTag();
        root.putString("id", "minecraft:spawner");
        root.putInt("count", 1);
        root.put("components", components);
        return root;
    }

    /** A spawner for every mob. Empty if the stacks cannot be built (the plain spawner stays in the list). */
    public static List<ItemStack> stacks(HolderLookup.Provider registries) {
        final List<ItemStack> list = new ArrayList<>();
        try {
            final DynamicOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
            for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
                final String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
                if (type.getCategory() == MobCategory.MISC || !type.canSummon() || SKIPPED.contains(id)) continue;
                ItemStack.CODEC.parse(ops, item(id)).result().ifPresent(list::add);
            }
        } catch (RuntimeException e) {
            NotEnoughItems.LOGGER.warn("Could not build the mob spawner items: {}", e.toString());
        }
        return list;
    }
}
