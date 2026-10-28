package codechicken.nei.client;

import java.util.ArrayList;
import java.util.List;

import codechicken.nei.NEIConfig;
import codechicken.nei.NotEnoughItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * As in NEI ({@code WorldOverlayRenderer}): the mob spawn overlay (F7). In the 33 x 33 x 33 blocks around the player an X
 * is drawn on every block where a monster could spawn: red where it can spawn at any time of day, yellow where only at
 * night (the sky light is 8 or more). A spot counts when the spawn placement of a zombie is fine there, the block light is
 * within the dimension's limit, nothing stands in the way and there is no liquid.
 *
 * Drawn with the game's gizmos, which a client tick may emit (they stay for that tick). The cells are worked out every
 * ten ticks, the lines are handed over again every tick.
 */
@EventBusSubscriber(modid = NotEnoughItems.MODID, value = Dist.CLIENT)
final class MobOverlay {

    /** NEI's vertical reach (33 blocks); the horizontal reach is the "Mob spawn overlay range" option (NEI: 16). */
    private static final int RADIUS = 16;
    private static final int REBUILD_TICKS = 10;
    /** Longer than a tick (50 ms) so the lines stay on screen between ticks. */
    private static final int PERSIST_MS = 80;
    private static final int RED = 0xFFFF0000;
    private static final int YELLOW = 0xFFFFFF00;

    /** One X: a cell's corner and the level height of its top, and which colour. */
    private record Cell(double x, double y, double z, boolean night) {}

    /** The zombie, looked up by id: 26.2 has no {@code EntityType.ZOMBIE} constant. */
    private static final Identifier ZOMBIE_ID = Identifier.fromNamespaceAndPath("minecraft", "zombie");

    private static boolean on;
    private static final List<Cell> CELLS = new ArrayList<>();
    private static long builtAt = -1000L;

    private MobOverlay() {}

    private static void say(boolean enabled) {
        final var player = Minecraft.getInstance().player;
        if (player == null) return;
        // NEI's nei.chat.moboverlay.0 / .1: red "Off", dark green "On"
        player.sendSystemMessage(Component.literal("Mob Spawning Overlay: ")
                .append(Component.literal(enabled ? "On" : "Off").withStyle(style -> style.withColor(enabled ? 0x00AA00 : 0xFF5555))));
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        final Minecraft mc = Minecraft.getInstance();
        final ClientLevel level = mc.level;
        if (level == null || mc.player == null) { // NEI resets the overlays with the world
            on = false;
            CELLS.clear();
            return;
        }

        while (NEIKeys.WORLD_MOBS.consumeClick()) {
            on = !on;
            CELLS.clear();
            builtAt = -1000L;
            say(on);
        }
        if (!on) return;

        try {
            final long time = level.getGameTime();
            if (time - builtAt >= REBUILD_TICKS || time < builtAt) {
                builtAt = time;
                build(level, mc.player.blockPosition());
            }
            // The tick event fires after vanilla closed its per-tick gizmo scope; outside a scope the gizmos go to a
            // no-op collector, so open the client's per-tick scope again here.
            try (var scope = mc.collectPerTickGizmos()) {
                for (Cell c : CELLS) {
                    final int color = c.night() ? YELLOW : RED;
                    Gizmos.line(new Vec3(c.x(), c.y(), c.z()), new Vec3(c.x() + 1, c.y(), c.z() + 1), color).persistForMillis(PERSIST_MS);
                    Gizmos.line(new Vec3(c.x() + 1, c.y(), c.z()), new Vec3(c.x(), c.y(), c.z() + 1), color).persistForMillis(PERSIST_MS);
                }
            }
        } catch (RuntimeException e) {
            NotEnoughItems.LOGGER.warn("Mob spawn overlay failed, switching it off", e);
            mc.player.sendSystemMessage(Component.literal("Mob spawn overlay failed: " + e));
            on = false;
            CELLS.clear();
        }
    }

    /** NEI's {@code buildMobSpawnOverlay}: the cells around {@code center}, column by column up to the surface. */
    private static void build(ClientLevel level, BlockPos center) {
        CELLS.clear();
        final int minY = level.getMinY();
        final int maxY = level.getMaxY();
        final int y0 = Math.max(minY, Math.min(center.getY(), maxY - RADIUS) - RADIUS);
        final int blockLimit = level.dimensionType().monsterSpawnBlockLightLimit();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        final int reach = NEIConfig.MOB_RANGE.get();
        for (int x = center.getX() - reach; x <= center.getX() + reach; x++) {
            for (int z = center.getZ() - reach; z <= center.getZ() + reach; z++) {
                if (!level.hasChunkAt(pos.set(x, center.getY(), z))) continue;
                if (level.getBiome(pos.set(x, center.getY(), z)).value().getMobSettings().getMobs(MobCategory.MONSTER).isEmpty()) continue;

                final int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z); // first free block above the surface
                for (int y = y0; y <= Math.min(y0 + 2 * RADIUS, maxY - 1) && y <= top; y++) {
                    pos.set(x, y, z);
                    final int mode = spawnMode(level, pos, blockLimit);
                    if (mode == 0) continue;
                    CELLS.add(new Cell(x, y + yOffset(level, pos), z, mode == 1));
                }
            }
        }
    }

    /** NEI's {@code getOverlayYOffset}: a snow layer lifts the X onto its top. */
    private static double yOffset(ClientLevel level, BlockPos pos) {
        final BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof SnowLayerBlock) return state.getValue(SnowLayerBlock.LAYERS) / 8.0 + 0.02;
        return 0.02;
    }

    /** NEI's {@code getSpawnMode}: 0 no spawn, 1 only at night, 2 at any time. */
    private static int spawnMode(ClientLevel level, BlockPos pos, int blockLimit) {
        if (level.getBrightness(LightLayer.BLOCK, pos) > blockLimit) return 0;
        final EntityType<?> zombie = BuiltInRegistries.ENTITY_TYPE.getValue(ZOMBIE_ID);
        if (!SpawnPlacements.getPlacementType(zombie).isSpawnPositionOk(level, pos, zombie)) return 0;

        final AABB box = new AABB(pos.getX() + 0.2, pos.getY() + 0.01, pos.getZ() + 0.2, pos.getX() + 0.8, pos.getY() + 1.8,
                pos.getZ() + 0.8);
        if (!level.noCollision(box) || level.containsAnyLiquid(box)) return 0;

        return level.getBrightness(LightLayer.SKY, pos) >= 8 ? 1 : 2;
    }
}
