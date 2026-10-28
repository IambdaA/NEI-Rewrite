package codechicken.nei.api;

import net.neoforged.bus.api.Event;
import net.neoforged.fml.event.IModBusEvent;

/**
 * Fired on the mod event bus, on the client, once the registries are filled (during client setup). It is NEI's
 * {@code IConfigureNEI.loadConfig()}: a mod subscribes on its mod bus and calls {@link API} from the listener.
 *
 * <pre>{@code
 * modBus.addListener((NEIRegisterEvent e) -> {
 *     API.hideItem(new ItemStack(MyItems.SECRET.get()));
 *     API.registerRecipeSource("mymod", () -> MyRecipes.views());
 * });
 * }</pre>
 */
public final class NEIRegisterEvent extends Event implements IModBusEvent {}
