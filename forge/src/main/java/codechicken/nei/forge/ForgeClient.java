package codechicken.nei.forge;

import codechicken.nei.api.NEIRegisterEvent;
import codechicken.nei.client.ChatLinks;
import codechicken.nei.client.ClientHooks;
import codechicken.nei.client.ClientPlatform;
import codechicken.nei.client.FluidRegistration;
import codechicken.nei.client.NEIKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoader;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.registries.RegisterEvent;

/** The client half of the entry point; only {@link ForgeEntry} calls it, and only on the client. */
final class ForgeClient {

    private ForgeClient() {}

    static void init(IEventBus modBus) {
        ClientPlatform.HOLDER.set(new ForgeClientPlatform());

        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> {
            ClientHooks.setup();
            // NEI's IConfigureNEI.loadConfig(): other mods register through codechicken.nei.api.API from this event
            ModLoader.get().postEvent(new NEIRegisterEvent());
        }));
        modBus.addListener((RegisterKeyMappingsEvent e) -> NEIKeys.all().forEach(e::register));
        modBus.addListener((RegisterEvent e) -> {
            if (e.getRegistryKey().equals(Registries.ITEM)) {
                FluidRegistration.create((id, item) -> e.register(Registries.ITEM, id, () -> item));
            }
        });

        final IEventBus bus = MinecraftForge.EVENT_BUS;
        bus.addListener((TickEvent.ClientTickEvent e) -> {
            if (e.phase == TickEvent.Phase.END) ClientHooks.tick();
        });
        bus.addListener((RegisterClientCommandsEvent e) -> e.getDispatcher().register(ChatLinks.<CommandSourceStack>command()));

        bus.addListener((ScreenEvent.Init.Post e) -> ClientHooks.screenInit(e.getScreen()));
        bus.addListener((ScreenEvent.Render.Pre e) -> {
            if (ClientHooks.renderPre(e.getScreen(), e.getGuiGraphics(), e.getMouseX(), e.getMouseY(), e.getPartialTick())) {
                e.setCanceled(true);
            }
        });
        bus.addListener((ScreenEvent.Render.Post e) ->
                ClientHooks.renderPost(e.getScreen(), e.getGuiGraphics(), e.getMouseX(), e.getMouseY(), e.getPartialTick()));
        // the input listeners run before JEI's (and every other mod's): R / U / a click over an item reach NEI first
        bus.addListener(EventPriority.HIGHEST, (ScreenEvent.MouseButtonPressed.Pre e) -> {
            if (ClientHooks.mousePressed(e.getScreen(), e.getMouseX(), e.getMouseY(), e.getButton())) e.setCanceled(true);
        });
        bus.addListener((ScreenEvent.MouseButtonPressed.Post e) ->
                ClientHooks.mousePressedPost(e.getScreen(), e.getMouseX(), e.getMouseY(), e.getButton()));
        bus.addListener((ScreenEvent.MouseButtonReleased.Pre e) -> {
            if (ClientHooks.mouseReleased(e.getScreen(), e.getMouseX(), e.getMouseY(), e.getButton())) e.setCanceled(true);
        });
        bus.addListener(EventPriority.HIGHEST, (ScreenEvent.MouseScrolled.Pre e) -> {
            if (ClientHooks.mouseScrolled(e.getScreen(), e.getMouseX(), e.getMouseY(), e.getScrollDelta())) e.setCanceled(true);
        });
        bus.addListener(EventPriority.HIGHEST, (ScreenEvent.KeyPressed.Pre e) -> {
            if (ClientHooks.keyPressed(e.getScreen(), e.getKeyCode(), e.getScanCode(), e.getModifiers())) e.setCanceled(true);
        });
        bus.addListener((ScreenEvent.KeyReleased.Pre e) ->
                ClientHooks.keyReleased(e.getScreen(), e.getKeyCode(), e.getScanCode(), e.getModifiers()));
        bus.addListener((ScreenEvent.CharacterTyped.Pre e) -> {
            if (ClientHooks.charTyped(e.getScreen(), e.getCodePoint(), e.getModifiers())) e.setCanceled(true);
        });
        bus.addListener((ScreenEvent.Opening e) -> {
            if (ClientHooks.screenOpening(e.getNewScreen())) e.setCanceled(true);
        });
        bus.addListener((InputEvent.MouseScrollingEvent e) -> {
            if (Minecraft.getInstance().screen == null && ClientHooks.worldScroll(e.getScrollDelta())) e.setCanceled(true);
        });
        bus.addListener((RenderGuiEvent.Post e) -> ClientHooks.hud(e.getGuiGraphics()));
        bus.addListener((RenderLevelStageEvent e) -> {
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                ClientHooks.renderWorld(e.getPoseStack(), e.getCamera().getPosition());
            }
        });
    }
}
