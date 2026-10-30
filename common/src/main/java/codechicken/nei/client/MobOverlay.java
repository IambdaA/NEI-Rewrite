package codechicken.nei.client;

import java.util.ArrayList;
import java.util.List;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import codechicken.nei.NEIConfig;
import codechicken.nei.NotEnoughItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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

/**
 * As in NEI ({@code WorldOverlayRenderer}): the mob spawn overlay (F7). In the 33 x 33 x 33 blocks around the player an X
 * is drawn on every block where a monster could spawn: red where it can spawn at any time of day, yellow where only at
 * night (the sky light is 8 or more). A spot counts when the spawn placement of a zombie is fine there, the block light is
 * within the dimension's limit, nothing stands in the way and there is no liquid.
 *
 * Drawn as lines in the world by {@link #renderWorld}, which the loader calls after the translucent part of the level is
 * drawn. The cells are worked out every ten ticks.
 */
final class MobOverlay {

    /** NEI's vertical reach (33 blocks); the horizontal reach is the "Mob spawn overlay range" option (NEI: 16). */
    private static final int RADIUS = 16;
    private static final int REBUILD_TICKS = 10;
    private static final int RED = 0xFFFF0000;
    private static final int YELLOW = 0xFFFFFF00;

    /** One X: a cell's corner and the level height of its top, and which colour. */
    private record Cell(double x, double y, double z, boolean night) {}

    /** The zombie, looked up by id. */
    private static final ResourceLocation ZOMBIE_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "zombie");

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

    public static void onTick() {
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
        } catch (RuntimeException e) {
            NotEnoughItems.LOGGER.warn("Mob spawn overlay failed, switching it off", e);
            mc.player.sendSystemMessage(Component.literal("Mob spawn overlay failed: " + e));
            on = false;
            CELLS.clear();
        }
    }

    /**
     * Draws the X marks. {@code pose} is the level's pose (rotated by the camera, not moved); {@code camera} the camera's
     * position in the world.
     */
    static void renderWorld(PoseStack pose, Vec3 camera) {
        if (!on || CELLS.isEmpty()) return;
        final var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        final VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        final PoseStack.Pose last = pose.last();
        final Matrix4f matrix = last.pose();
        for (Cell c : CELLS) {
            final int color = c.night() ? YELLOW : RED;
            line(lines, last, matrix, c.x() - camera.x, c.y() - camera.y, c.z() - camera.z, c.x() + 1 - camera.x,
                    c.y() - camera.y, c.z() + 1 - camera.z, color);
            line(lines, last, matrix, c.x() + 1 - camera.x, c.y() - camera.y, c.z() - camera.z, c.x() - camera.x,
                    c.y() - camera.y, c.z() + 1 - camera.z, color);
        }
        buffers.endBatch(RenderType.lines());
    }

    private static void line(VertexConsumer out, PoseStack.Pose pose, Matrix4f matrix, double x1, double y1, double z1,
            double x2, double y2, double z2, int color) {
        final float dx = (float) (x2 - x1);
        final float dy = (float) (y2 - y1);
        final float dz = (float) (z2 - z1);
        final float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        final float nx = dx / len;
        final float ny = dy / len;
        final float nz = dz / len;
        out.addVertex(matrix, (float) x1, (float) y1, (float) z1).setColor(color).setNormal(pose, nx, ny, nz);
        out.addVertex(matrix, (float) x2, (float) y2, (float) z2).setColor(color).setNormal(pose, nx, ny, nz);
    }

    /** NEI's {@code buildMobSpawnOverlay}: the cells around {@code center}, column by column up to the surface. */
    private static void build(ClientLevel level, BlockPos center) {
        CELLS.clear();
        final int minY = level.getMinBuildHeight();
        final int maxY = level.getMaxBuildHeight() - 1;
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
        final EntityType<?> zombie = BuiltInRegistries.ENTITY_TYPE.get(ZOMBIE_ID);
        if (!SpawnPlacements.getPlacementType(zombie).isSpawnPositionOk(level, pos, zombie)) return 0;

        final AABB box = new AABB(pos.getX() + 0.2, pos.getY() + 0.01, pos.getZ() + 0.2, pos.getX() + 0.8, pos.getY() + 1.8,
                pos.getZ() + 0.8);
        if (!level.noCollision(box) || level.containsAnyLiquid(box)) return 0;

        return level.getBrightness(LightLayer.SKY, pos) >= 8 ? 1 : 2;
    }
}
