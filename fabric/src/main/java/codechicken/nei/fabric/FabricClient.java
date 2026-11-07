package codechicken.nei.fabric;

import codechicken.nei.NotEnoughItems;
import codechicken.nei.api.NEIPlugin;
import codechicken.nei.client.ChatLinks;
import codechicken.nei.client.ClientHooks;
import codechicken.nei.client.ClientPlatform;
import codechicken.nei.client.FluidRegistration;
import codechicken.nei.client.NEIKeys;
import codechicken.nei.net.NEINetwork;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import codechicken.nei.net.NEIPacket;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.KeyMapping;

/** Client-only entry point. */
public final class FabricClient implements ClientModInitializer {

    /**
     * Fabric's per-screen events may or may not be cleared when a screen is laid out again (window resize), so every
     * registration carries the number of the init of ITS screen it came from, and only the newest one does anything.
     * The count is per screen: opening the inventory in creative mode makes the survival screen open the creative one inside
     * its own init(), so the survival screen's init event arrives after the creative screen's (a global counter declared the
     * creative screen dead: no NEI panel there).
     */
    private static int keyTicks;
    private static final ResourceLocation EARLY = new ResourceLocation("notenoughitems", "early");
    private static final java.util.Set<Screen> SKIPPED = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    private static final java.util.Map<Screen, Integer> INITS = new java.util.WeakHashMap<>();

    @Override
    public void onInitializeClient() {
        ClientPlatform.HOLDER.set(new FabricClientPlatform());

        for (KeyMapping key : NEIKeys.all()) KeyBindingHelper.registerKeyBinding(key);
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
            for (NEIPlugin plugin : FabricLoader.getInstance().getEntrypoints(NotEnoughItems.MODID, NEIPlugin.class)) {
                try {
                    plugin.register();
                } catch (RuntimeException e) {
                    NotEnoughItems.LOGGER.error("NEI plugin {} failed", plugin.getClass().getName(), e);
                }
            }
        });

        for (NEINetwork.ToClient<?> packet : NEINetwork.TO_CLIENT) toClient(packet);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientHooks.tick();
            if (++keyTicks % 20 == 0) KeyTable.fix(); // whatever rebuilt vanilla's key table (JEI, a rebinding) is put right again
        });
        HudRenderCallback.EVENT.register((graphics, delta) -> ClientHooks.hud(graphics));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (context.matrixStack() != null) {
                ClientHooks.renderWorld(context.matrixStack(), context.camera().getPosition());
            }
        });
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> {
                    dispatcher.register(ChatLinks.<FabricClientCommandSource>command());
                    dispatcher.register(codechicken.nei.client.ReleaseNotice.<FabricClientCommandSource>command());
                });

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> screenInit(screen));
    }

    private static <T extends NEIPacket> void toClient(NEINetwork.ToClient<T> p) {
        ClientPlayNetworking.registerGlobalReceiver(p.type().id(), (client, handler, buf, responseSender) -> {
            final T packet = p.type().reader().apply(buf); // read now: the buffer is released after this call
            client.execute(() -> p.handler().accept(packet));
        });
    }

    private static void screenInit(Screen screen) {
        final int mine = INITS.merge(screen, 1, Integer::sum);
        ClientHooks.screenInit(screen);

        ScreenEvents.afterRender(screen).register((s, g, mx, my, dt) -> {
            if (live(mine, s)) ClientHooks.renderPost(s, g, mx, my, dt);
            else if (s == net.minecraft.client.Minecraft.getInstance().screen && SKIPPED.add(s)) {
                NotEnoughItems.LOGGER.info("NEI: the open screen {} is not live (init {} of {})", s.getClass().getName(), mine,
                        INITS.getOrDefault(s, 0));
            }
        });
        // JEI listens in the default phase and (it loads first) is called before a listener registered after it: NEI's input
        // listeners go in an earlier phase, so R / U / a click over an item reach NEI before JEI's own recipe keys
        final var click = ScreenMouseEvents.allowMouseClick(screen);
        click.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
        click.register(EARLY, (s, mx, my, button) -> !live(mine, s) || !ClientHooks.mousePressed(s, mx, my, button));
        ScreenMouseEvents.afterMouseClick(screen).register((s, mx, my, button) -> {
            if (live(mine, s)) ClientHooks.mousePressedPost(s, mx, my, button);
        });
        ScreenMouseEvents.allowMouseRelease(screen)
                .register((s, mx, my, button) -> !live(mine, s) || !ClientHooks.mouseReleased(s, mx, my, button));
        final var scroll = ScreenMouseEvents.allowMouseScroll(screen);
        scroll.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
        scroll.register(EARLY, (s, mx, my, h, v) -> !live(mine, s) || !ClientHooks.mouseScrolled(s, mx, my, v));
        final var press = ScreenKeyboardEvents.allowKeyPress(screen);
        press.addPhaseOrdering(EARLY, Event.DEFAULT_PHASE);
        press.register(EARLY, (s, key, scan, mods) -> !live(mine, s) || !ClientHooks.keyPressed(s, key, scan, mods));
        ScreenKeyboardEvents.allowKeyRelease(screen).register((s, key, scan, mods) -> {
            if (live(mine, s)) ClientHooks.keyReleased(s, key, scan, mods);
            return true;
        });
    }

    private static boolean live(int mine, Screen s) {
        return s == net.minecraft.client.Minecraft.getInstance().screen && INITS.getOrDefault(s, 0) == mine;
    }
}
