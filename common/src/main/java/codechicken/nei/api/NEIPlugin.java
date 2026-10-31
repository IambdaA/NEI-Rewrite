package codechicken.nei.api;

/**
 * NEI's {@code IConfigureNEI.loadConfig()} for Fabric: a mod declares an entrypoint named {@code notenoughitems} in its
 * {@code fabric.mod.json} that implements this, and calls {@link API} from {@link #register}. It runs on the client once
 * the registries are filled. (On NeoForge a mod listens for {@code NEIRegisterEvent} on its mod bus instead.)
 */
public interface NEIPlugin {

    void register();
}
