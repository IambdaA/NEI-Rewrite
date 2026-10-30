package codechicken.nei.neoforge;

import codechicken.nei.NotEnoughItems;
import codechicken.nei.api.NEIRegisterEvent;
import codechicken.nei.client.ChatLinks;
import codechicken.nei.client.ClientHooks;
import codechicken.nei.client.ClientPlatform;
import codechicken.nei.client.FluidRegistration;
import codechicken.nei.client.NEIKeys;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Client-only entry point; never loads on a dedicated server. */
@Mod(value = NotEnoughItems.MODID, dist = Dist.CLIENT)
public final class NeoForgeClient {

    public NeoForgeClient(IEventBus modBus) {
        ClientPlatform.HOLDER.set(new NeoClientPlatform());

        modBus.addListener((FMLClientSetupEvent e) -> e.enqueueWork(() -> {
            ClientHooks.setup();
            // NEI's IConfigureNEI.loadConfig(): other mods register through codechicken.nei.api.API from this event
            ModLoader.postEvent(new NEIRegisterEvent());
        }));
        modBus.addListener((RegisterKeyMappingsEvent e) -> NEIKeys.all().forEach(e::register));
        modBus.addListener((RegisterEvent e) -> {
            if (e.getRegistryKey().equals(Registries.ITEM)) {
                FluidRegistration.create((id, item) -> e.register(Registries.ITEM, id, () -> item));
            }
        });

        final IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener((ClientTickEvent.Post e) -> ClientHooks.tick());
        bus.addListener((RegisterClientCommandsEvent e) -> e.getDispatcher().register(ChatLinks.<CommandSourceStack>command()));

        bus.addListener((ScreenEvent.Init.Post e) -> ClientHooks.screenInit(e.getScreen()));
        bus.addListener((ScreenEvent.Render.Pre e) -> {
            if (ClientHooks.renderPre(e.getScreen(), e.getGuiGraphics(), e.getMouseX(), e.getMouseY(), e.getPartialTick())) {
                e.setCanceled(true);
            }
        });
        bus.addListener((ScreenEvent.Render.Post e) ->
                ClientHooks.renderPost(e.getScreen(), e.getGuiGraphics(), e.getMouseX(), e.getMouseY(), e.getPartialTick()));
        bus.addListener((ScreenEvent.MouseButtonPressed.Pre e) -> {
            if (ClientHooks.mousePressed(e.getScreen(), e.getMouseX(), e.getMouseY(), e.getButton())) e.setCanceled(true);
        });
        bus.addListener((ScreenEvent.MouseButtonPressed.Post e) ->
                ClientHooks.mousePressedPost(e.getScreen(), e.getMouseX(), e.getMouseY(), e.getButton()));
        bus.addListener((ScreenEvent.MouseButtonReleased.Pre e) -> {
            if (ClientHooks.mouseReleased(e.getScreen(), e.getMouseX(), e.getMouseY(), e.getButton())) e.setCanceled(true);
        });
        bus.addListener((ScreenEvent.MouseScrolled.Pre e) -> {
            if (ClientHooks.mouseScrolled(e.getScreen(), e.getMouseX(), e.getMouseY(), e.getScrollDeltaY())) e.setCanceled(true);
        });
        bus.addListener((ScreenEvent.KeyPressed.Pre e) -> {
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
            if (Minecraft.getInstance().screen == null && ClientHooks.worldScroll(e.getScrollDeltaY())) e.setCanceled(true);
        });
        bus.addListener((RenderGuiEvent.Post e) -> ClientHooks.hud(e.getGuiGraphics()));
        bus.addListener((RenderLevelStageEvent e) -> {
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
                ClientHooks.renderWorld(e.getPoseStack(), e.getCamera().getPosition());
            }
        });
    }
}
