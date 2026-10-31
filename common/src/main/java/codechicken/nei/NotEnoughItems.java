package codechicken.nei;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

/**
 * Shared constants and the loader seam. The mod entry points live in the loader modules ({@code NeoForgeEntry},
 * {@code FabricEntry}); they call {@link #init} first.
 */
public final class NotEnoughItems {

    public static final String MODID = "notenoughitems";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static Platform platform;

    private NotEnoughItems() {}

    public static void init(Platform p) {
        platform = p;
    }

    public static Platform platform() {
        if (platform == null) throw new IllegalStateException("NEI Rewrite: the loader module did not set the platform");
        return platform;
    }
}
