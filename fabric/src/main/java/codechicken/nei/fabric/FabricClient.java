package codechicken.nei.fabric;

import codechicken.nei.NotEnoughItems;
import codechicken.nei.api.NEIPlugin;
import codechicken.nei.client.ChatLinks;
import codechicken.nei.client.ClientHooks;
import codechicken.nei.client.ClientPlatform;
import codechicken.nei.client.FluidRegistration;
import codechicken.nei.client.NEIKeys;
import codechicken.nei.net.NEINetwork;
import codechicken.nei.recipe.ClientRecipes;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.recipe.v1.sync.ClientRecipeSynchronizedEvent;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.crafting.RecipeMap;

/** Client-only entry point. */
public final class FabricClient implements ClientModInitializer {

    /**
     * Fabric's per-screen events may or may not be cleared when a screen is laid out again (window resize), so every
     * registration carries the number of the init it came from, and only the newest one of that screen does anything.
     * (Opening the inventory in creative mode swaps screens inside the survival screen's init(), so the survival screen's
     * AFTER_INIT arrives AFTER the creative screen's: the screen that counts is the one that is open, not the last initialised.)
     */
    private static final java.util.Map<Screen, Integer> GENERATION = new java.util.WeakHashMap<>();
    private static int counter;
    private static boolean loggedInit;
    private static boolean loggedRender;

    @Override
    public void onInitializeClient() {
        NotEnoughItems.LOGGER.info("NEI Rewrite (Fabric): client entrypoint running");
        ClientPlatform.HOLDER.set(new FabricClientPlatform());

        for (KeyMapping key : NEIKeys.all()) KeyMappingHelper.registerKeyMapping(key);
        KeyTable.fix();

        // the fluid display items (JEI bridge); this needs the item registry to still be open
        try {
            FluidRegistration.create((id, item) -> Registry.register(BuiltInRegistries.ITEM, id, item));
        } catch (RuntimeException e) {
            NotEnoughItems.LOGGER.warn("NEI Rewrite: could not add the fluid display items ({})", e.toString());
        }

        // registries are complete now: read the config, then let other mods register (NEI's IConfigureNEI.loadConfig())
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            ClientHooks.setup();
            NotEnoughItems.LOGGER.info("NEI Rewrite (Fabric): setup done, config read");
            for (NEIPlugin plugin : FabricLoader.getInstance().getEntrypoints(NotEnoughItems.MODID, NEIPlugin.class)) {
                try {
                    plugin.register();
                } catch (RuntimeException e) {
                    NotEnoughItems.LOGGER.error("NEI plugin {} failed", plugin.getClass().getName(), e);
                }
            }
        });

        for (NEINetwork.ToClient<?> packet : NEINetwork.TO_CLIENT) toClient(packet);

        // recipes the server synced (see FabricEntry)
        ClientRecipeSynchronizedEvent.EVENT.register((client, synchronizedRecipes) -> {
            ClientRecipes.set(RecipeMap.create(synchronizedRecipes.recipes()));
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientRecipes.clear());

        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientHooks.tick());
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(NotEnoughItems.MODID, "highlight_tips"),
                (graphics, tickCounter) -> ClientHooks.hud(graphics));
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> dispatcher.register(ChatLinks.<FabricClientCommandSource>command()));

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> screenInit(screen));
    }

    private static <T extends CustomPacketPayload> void toClient(NEINetwork.ToClient<T> p) {
        ClientPlayNetworking.registerGlobalReceiver(p.type(), (payload, context) -> p.handler().accept(payload));
    }

    private static void screenInit(Screen screen) {
        final int mine = ++counter;
        GENERATION.put(screen, mine);
        if (!loggedInit) {
            loggedInit = true;
            NotEnoughItems.LOGGER.info("NEI Rewrite (Fabric): first screen init ({}), hooks registered", screen.getClass().getName());
        }
        ClientHooks.screenInit(screen);

        ScreenEvents.afterExtract(screen).register((s, g, mx, my, dt) -> {
            if (!live(mine, s)) return;
            if (!loggedRender) {
                loggedRender = true;
                NotEnoughItems.LOGGER.info("NEI Rewrite (Fabric): first screen render hook ({})", s.getClass().getName());
            }
            ClientHooks.renderPost(s, g, mx, my, dt);
            // NeoForge's RenderFrameEvent.Post: a task queued now runs at the start of the next frame, when this one is complete
            net.minecraft.client.Minecraft.getInstance().execute(ClientHooks::frameDone);
        });
        ScreenMouseEvents.allowMouseClick(screen)
                .register((s, event) -> !live(mine, s) || !ClientHooks.mousePressed(s, event));
        ScreenMouseEvents.allowMouseRelease(screen)
                .register((s, event) -> !live(mine, s) || !ClientHooks.mouseReleased(s, event));
        ScreenMouseEvents.allowMouseScroll(screen)
                .register((s, mx, my, h, v) -> !live(mine, s) || !ClientHooks.mouseScrolled(s, mx, my, v));
        ScreenKeyboardEvents.allowKeyPress(screen)
                .register((s, event) -> !live(mine, s) || !ClientHooks.keyPressed(s, event));
        ScreenKeyboardEvents.allowKeyRelease(screen).register((s, event) -> {
            if (live(mine, s)) ClientHooks.keyReleased(s, event);
            return true;
        });
    }

    private static boolean live(int mine, Screen s) {
        final Integer latest = GENERATION.get(s);
        return latest != null && latest == mine && s == net.minecraft.client.Minecraft.getInstance().screen;
    }
}
