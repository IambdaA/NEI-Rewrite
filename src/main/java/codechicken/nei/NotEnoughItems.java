package codechicken.nei;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.fml.common.Mod;

/**
 * Common entry point, loaded on both client and dedicated server. The server half is what makes recipe lookup work
 * on multiplayer: it tells the server to send recipes to clients (see {@link RecipeSync}).
 *
 * Client-only setup lives in {@link NEIClient}.
 */
@Mod(NotEnoughItems.MODID)
public final class NotEnoughItems {

    public static final String MODID = "notenoughitems";
    public static final Logger LOGGER = LogUtils.getLogger();

    public NotEnoughItems() {}
}
