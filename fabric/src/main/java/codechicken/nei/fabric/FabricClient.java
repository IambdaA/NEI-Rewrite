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
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.KeyMapping;

/** Client-only entry point. */
public final class FabricClient implements ClientModInitializer {

    /**
     * Fabric's per-screen events may or may not be cleared when a screen is laid out again (window resize), so every
     * registration carries the number of the init it came from, and only the newest one does anything.
     */
    private static int generation;
    private static Screen current;

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

        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientHooks.tick());
        HudRenderCallback.EVENT.register((graphics, delta) -> ClientHooks.hud(graphics));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (context.matrixStack() != null) {
                ClientHooks.renderWorld(context.matrixStack(), context.camera().getPosition());
            }
        });
        ClientCommandRegistrationCallback.EVENT.register(
                (dispatcher, registryAccess) -> dispatcher.register(ChatLinks.<FabricClientCommandSource>command()));

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> screenInit(screen));
    }

    private static <T extends CustomPacketPayload> void toClient(NEINetwork.ToClient<T> p) {
        ClientPlayNetworking.registerGlobalReceiver(p.type(), (payload, context) -> p.handler().accept(payload));
    }

    private static void screenInit(Screen screen) {
        final int mine = ++generation;
        current = screen;
        ClientHooks.screenInit(screen);

        ScreenEvents.afterRender(screen).register((s, g, mx, my, dt) -> {
            if (live(mine, s)) ClientHooks.renderPost(s, g, mx, my, dt);
        });
        ScreenMouseEvents.allowMouseClick(screen)
                .register((s, mx, my, button) -> !live(mine, s) || !ClientHooks.mousePressed(s, mx, my, button));
        ScreenMouseEvents.afterMouseClick(screen).register((s, mx, my, button) -> {
            if (live(mine, s)) ClientHooks.mousePressedPost(s, mx, my, button);
        });
        ScreenMouseEvents.allowMouseRelease(screen)
                .register((s, mx, my, button) -> !live(mine, s) || !ClientHooks.mouseReleased(s, mx, my, button));
        ScreenMouseEvents.allowMouseScroll(screen)
                .register((s, mx, my, h, v) -> !live(mine, s) || !ClientHooks.mouseScrolled(s, mx, my, v));
        ScreenKeyboardEvents.allowKeyPress(screen)
                .register((s, key, scan, mods) -> !live(mine, s) || !ClientHooks.keyPressed(s, key, scan, mods));
        ScreenKeyboardEvents.allowKeyRelease(screen).register((s, key, scan, mods) -> {
            if (live(mine, s)) ClientHooks.keyReleased(s, key, scan, mods);
            return true;
        });
    }

    private static boolean live(int mine, Screen s) {
        return mine == generation && s == current;
    }
}
