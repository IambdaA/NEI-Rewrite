package codechicken.nei.client;

import com.mojang.blaze3d.vertex.PoseStack;

import codechicken.nei.NEIConfig;
import codechicken.nei.NotEnoughItems;
import codechicken.nei.api.API;
import codechicken.nei.item.FluidDisplay;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.phys.Vec3;

/**
 * The one door from the mod loaders into the client code. Each loader module listens for its own events (NeoForge's event
 * bus, Fabric API callbacks, a few mixins) and calls these methods; nothing in here knows which loader it is.
 * Methods that return {@code boolean} return true to cancel the event.
 */
public final class ClientHooks {

    private ClientHooks() {}

    /** Reads the config and registers what NEI itself adds to the API; the loader then lets other mods register. */
    public static void setup() {
        NEIConfig.SPEC.load(NotEnoughItems.platform().configDir().resolve("notenoughitems-client.json"));
        // the fluid display items (JEI bridge) get a subset of their own
        API.addSubset("Fluids", entry -> FluidDisplay.is(entry.stack));
    }

    /** End of a client tick. */
    public static void tick() {
        Toolbar.onTick();
        TerminalSearchSync.onTick();
        AutoCraft.onTick();
        Magnet.onTick();
        VillagerTrades.onTick();
        MobOverlay.onTick();
        CreativePlus.onTick();
        HighlightTips.onTick();
    }

    public static void screenInit(Screen screen) {
        NEIClientEvents.onInit(ScreenEv.init(screen));
    }

    /** Before a screen draws; true skips drawing it. */
    public static boolean renderPre(Screen screen, GuiGraphics g, int mx, int my, float partialTick) {
        final ScreenEv e = ScreenEv.render(screen, g, mx, my, partialTick);
        NEIClientEvents.onRenderPre(e);
        return e.isCanceled();
    }

    /** After a screen drew: NEI's panels, windows and tooltips go on top. */
    public static void renderPost(Screen screen, GuiGraphics g, int mx, int my, float partialTick) {
        final ScreenEv e = ScreenEv.render(screen, g, mx, my, partialTick);
        NEIClientEvents.onRender(e);
        HighlightTips.onScreenFrame(e);
        TreeShot.onFrame(); // the picture is finished now
    }

    public static boolean mousePressed(Screen screen, double mx, double my, int button) {
        final ScreenEv e = ScreenEv.mouse(screen, mx, my, button);
        NEIClientEvents.onMousePressed(e);
        return e.isCanceled();
    }

    public static void mousePressedPost(Screen screen, double mx, double my, int button) {
        NEIClientEvents.onMousePressedPost(ScreenEv.mouse(screen, mx, my, button));
    }

    public static boolean mouseReleased(Screen screen, double mx, double my, int button) {
        final ScreenEv e = ScreenEv.mouse(screen, mx, my, button);
        NEIClientEvents.onMouseReleased(e);
        return e.isCanceled();
    }

    public static boolean mouseScrolled(Screen screen, double mx, double my, double scrollY) {
        final ScreenEv e = ScreenEv.scroll(screen, mx, my, scrollY);
        NEIClientEvents.onMouseScrolled(e);
        return e.isCanceled();
    }

    public static boolean keyPressed(Screen screen, int key, int scan, int modifiers) {
        final ScreenEv e = ScreenEv.key(screen, key, scan, modifiers);
        NEIClientEvents.onKeyPressed(e);
        return e.isCanceled();
    }

    public static void keyReleased(Screen screen, int key, int scan, int modifiers) {
        NEIClientEvents.onKeyReleased(ScreenEv.key(screen, key, scan, modifiers));
    }

    public static boolean charTyped(Screen screen, int codePoint, int modifiers) {
        final ScreenEv e = ScreenEv.chr(screen, codePoint, modifiers);
        NEIClientEvents.onCharTyped(e);
        return e.isCanceled();
    }

    /** A screen is about to open; true cancels it (the creative inventory is replaced by the extended one). */
    public static boolean screenOpening(Screen next) {
        return CreativePlus.onOpening(next);
    }

    /** The mouse wheel turned with no screen open; true cancels it. */
    public static boolean worldScroll(double dy) {
        return CreativePlus.onScroll(dy);
    }

    /** The in-game HUD was drawn. */
    public static void hud(GuiGraphics g) {
        HighlightTips.onHud(g);
    }

    /** The level was drawn (translucent part done); {@code pose} is rotated by the camera but not moved. */
    public static void renderWorld(PoseStack pose, Vec3 camera) {
        MobOverlay.renderWorld(pose, camera);
    }
}
