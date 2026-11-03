package codechicken.nei.client;

import net.minecraft.client.Minecraft;

/**
 * Whether the player may run operator commands ({@code /time}, {@code /weather}, {@code /gamemode}, {@code /effect}):
 * game master level (2), as the server knows it. Checked at most once a second.
 */
final class Perms {

    private static boolean cached = true;
    private static long checkedAt;

    private Perms() {}

    static boolean canUseCommands() {
        final long now = System.currentTimeMillis();
        if (now - checkedAt > 1000) {
            checkedAt = now;
            final var player = Minecraft.getInstance().player;
            cached = player != null && player.hasPermissions(2);
        }
        return cached;
    }
}
