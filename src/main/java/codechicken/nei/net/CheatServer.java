package codechicken.nei.net;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import codechicken.nei.NotEnoughItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * The server half of NEI's delete mode, "delete all" and magnet ({@code NEISPH.setInventorySlot}, {@code NEIServerUtils.deleteAllItems},
 * {@code ServerHandler.updateMagneticPlayer}). Allowed for creative players and operators, as the toolbar is.
 * Common code: it never touches client classes.
 */
@EventBusSubscriber(modid = NotEnoughItems.MODID)
public final class CheatServer {

    /** Players with the magnet on. NEI saves this per player; here it is the client's setting, sent again on every join. */
    private static final Set<UUID> MAGNET = new HashSet<>();

    // NEI's magnet constants
    private static final double DISTANCE_XZ = 16;
    private static final double DISTANCE_Y = 8;
    private static final double MAX_SPEED_XZ = 0.5;
    private static final double MAX_SPEED_Y = 0.5;
    private static final double SPEED_XZ = 0.05;
    private static final double SPEED_Y = 0.07;

    private CheatServer() {}

    /** Creative players and operators (game master level), as JEI's cheat mode checks it. */
    static boolean allowed(ServerPlayer player) {
        return player.isCreative() || player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    /** NEI's {@code setInventorySlot}: only removing items is accepted here (giving has its own routes). */
    static void setSlot(ServerPlayer player, int slot, ItemStack stack) {
        if (!allowed(player)) return;
        final AbstractContainerMenu menu = player.containerMenu;
        final ItemStack old = slot == CheatPackets.CARRIED ? menu.getCarried()
                : slot >= 0 && slot < menu.slots.size() ? menu.getSlot(slot).getItem() : null;
        if (old == null) return;
        final boolean deleting = stack.isEmpty() || ItemStack.isSameItemSameComponents(stack, old) && stack.getCount() < old.getCount();
        if (!deleting) return;

        if (slot == CheatPackets.CARRIED) menu.setCarried(stack.copy());
        else menu.getSlot(slot).set(stack.copy());
        menu.broadcastChanges();
    }

    /** NEI's {@code deleteAllItems}: every slot of the open container. */
    static void deleteAll(ServerPlayer player) {
        if (!allowed(player)) return;
        final AbstractContainerMenu menu = player.containerMenu;
        for (int i = 0; i < menu.slots.size(); i++) menu.getSlot(i).set(ItemStack.EMPTY);
        menu.broadcastChanges();
    }

    static void setMagnet(ServerPlayer player, boolean on) {
        if (on && allowed(player)) MAGNET.add(player.getUUID());
        else MAGNET.remove(player.getUUID());
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        MAGNET.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        MAGNET.clear();
    }

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        if (MAGNET.isEmpty()) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (MAGNET.contains(player.getUUID()) && !player.isDeadOrDying()) pull(player);
        }
    }

    /** NEI's {@code updateMagneticPlayer}: items within 16 x 8 x 16 that fit in the inventory drift towards the player. */
    private static void pull(ServerPlayer player) {
        final List<ItemEntity> items = player.level().getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(DISTANCE_XZ, DISTANCE_Y, DISTANCE_XZ));
        for (ItemEntity item : items) {
            if (item.hasPickUpDelay()) continue;
            if (!fits(player.getInventory(), item.getItem())) continue;

            double dx = player.getX() - item.getX();
            double dy = player.getEyeY() - item.getY();
            double dz = player.getZ() - item.getZ();
            final double absxz = Math.sqrt(dx * dx + dz * dz);
            final double absy = Math.abs(dy);
            if (absxz > DISTANCE_XZ) continue;
            if (absxz < 1) item.playerTouch(player);

            if (absxz > 1) {
                dx /= absxz;
                dz /= absxz;
            }
            if (absy > 1) dy /= absy;

            final Vec3 motion = item.getDeltaMovement();
            double vx = motion.x + SPEED_XZ * dx;
            double vy = motion.y + SPEED_Y * dy;
            double vz = motion.z + SPEED_XZ * dz;

            final double ratioXz = Math.sqrt(vx * vx + vz * vz) / MAX_SPEED_XZ;
            if (ratioXz > 1) {
                vx /= ratioXz;
                vz /= ratioXz;
            }
            final double ratioY = Math.abs(vy) / MAX_SPEED_Y;
            if (ratioY > 1) vy /= ratioY;

            item.setDeltaMovement(vx, vy, vz);
            item.hurtMarked = true;
        }
    }

    /** NEI's {@code canItemFitInInventory}. */
    private static boolean fits(Inventory inventory, ItemStack stack) {
        return inventory.getFreeSlot() >= 0 || inventory.getSlotWithRemainingSpace(stack) >= 0;
    }
}
