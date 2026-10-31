package codechicken.nei.client;

import java.lang.reflect.Method;

import codechicken.nei.NotEnoughItems;
import net.minecraft.client.Minecraft;

/**
 * Whether the player may run operator commands ({@code /time}, {@code /weather}, {@code /gamemode}, {@code /effect}).
 *
 * Minecraft changed its permission API after 1.21, so both forms are tried by reflection: {@code Entity#hasPermissions(int)} and the newer
 * {@code permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)}. If neither exists the answer is "yes", so the
 * toolbar stays usable (the server still refuses commands it does not allow).
 */
final class Perms {

    private static boolean cached = true;
    private static long checkedAt;
    private static boolean logged;

    private Perms() {}

    static boolean canUseCommands() {
        final long now = System.currentTimeMillis();
        if (now - checkedAt > 1000) {
            checkedAt = now;
            cached = query();
        }
        return cached;
    }

    private static boolean query() {
        final var player = Minecraft.getInstance().player;
        if (player == null) return false;

        try {
            final Method m = player.getClass().getMethod("hasPermissions", int.class);
            return log("hasPermissions(int)", (Boolean) m.invoke(player, 2));
        } catch (NoSuchMethodException e) {
            // not this version, try the next form
        } catch (ReflectiveOperationException | RuntimeException e) {
            return true;
        }

        try {
            final Object set = player.getClass().getMethod("permissions").invoke(player);
            final Class<?> setClass = Class.forName("net.minecraft.server.permissions.PermissionSet");
            final Class<?> permission = Class.forName("net.minecraft.server.permissions.Permission");
            final Object level = Class.forName("net.minecraft.server.permissions.Permissions")
                    .getField("COMMANDS_GAMEMASTER").get(null);
            return log("permissions()", (Boolean) setClass.getMethod("hasPermission", permission).invoke(set, level));
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!logged) {
                logged = true;
                NotEnoughItems.LOGGER.warn("Could not read the player's permission level, showing the toolbar anyway: {}",
                        e.toString());
            }
            return true;
        }
    }

    private static boolean log(String how, boolean result) {
        if (!logged) {
            logged = true;
            NotEnoughItems.LOGGER.debug("NEI reads command permission with {}", how);
        }
        return result;
    }
}
