package codechicken.nei.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import codechicken.nei.NotEnoughItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * Every NEI hotkey, rebindable under Options > Controls > NotEnoughItems. They only act while a screen is open
 * ({@link KeyConflictContext#GUI}), so they never clash with in-game keys.
 *
 * Escape (close the recipe window) and the arrow keys (change recipe page) are fixed.
 */
@EventBusSubscriber(modid = NotEnoughItems.MODID, value = Dist.CLIENT)
public final class NEIKeys {

    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
            Identifier.fromNamespaceAndPath(NotEnoughItems.MODID, "nei"));

    public static final KeyMapping RECIPE = key("recipe", GLFW.GLFW_KEY_R);
    public static final KeyMapping USAGE = key("usage", GLFW.GLFW_KEY_U);
    public static final KeyMapping BOOKMARK = key("bookmark", GLFW.GLFW_KEY_A);
    public static final KeyMapping TOGGLE_BOOKMARKS = key("toggle_bookmarks", GLFW.GLFW_KEY_B);
    public static final KeyMapping BACK = key("back", GLFW.GLFW_KEY_BACKSPACE);
    /** NEI's {@code bookmark.chat_link}: with Ctrl over a group's strip, a bookmark or an item, shares it in chat. */
    public static final KeyMapping CHAT_LINK = key("chat_link", GLFW.GLFW_KEY_L);
    /** NEI's {@code bookmark.pull_items}: over a group's strip or an item, takes the items from the open container. */
    public static final KeyMapping PULL_ITEMS = key("pull_items", GLFW.GLFW_KEY_V);
    /** NEI's {@code gui.craft_items}: with Shift over a bookmarked recipe, a crafting group or a recipe, autocrafts it (Ctrl: only what is missing). */
    public static final KeyMapping CRAFT_ITEMS = key("craft_items", GLFW.GLFW_KEY_C);
    /** NEI's {@code bookmark.open_crafting_tree}. */
    public static final KeyMapping OPEN_TREE = key("open_crafting_tree", GLFW.GLFW_KEY_T);

    /** NEI's {@code copy.name}, {@code copy.identifier} and {@code copy.oredict} (tags here); used with Ctrl held. */
    public static final KeyMapping COPY_NAME = key("copy_name", GLFW.GLFW_KEY_C);
    public static final KeyMapping COPY_ID = key("copy_id", GLFW.GLFW_KEY_X);
    public static final KeyMapping COPY_TAGS = key("copy_tags", GLFW.GLFW_KEY_D);

    /** NEI's item zoom keys: Shift + toggle switches it on / off, hold shows it while held, zoom in / out resize it. */
    public static final KeyMapping ZOOM_TOGGLE = key("itemzoom_toggle", GLFW.GLFW_KEY_Z);
    public static final KeyMapping ZOOM_HOLD = key("itemzoom_hold", GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyMapping ZOOM_IN = key("itemzoom_zoom_in", GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyMapping ZOOM_OUT = key("itemzoom_zoom_out", GLFW.GLFW_KEY_UNKNOWN);

    /** NEI's {@code world.highlight_tips} key: works in the world, not in screens. */
    public static final KeyMapping HUD_TOGGLE = new KeyMapping("key." + NotEnoughItems.MODID + ".highlight_tips",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_KP_0, CATEGORY);

    /** NEI's {@code gui.enchant} (over an item) and {@code gui.potion}. */
    public static final KeyMapping ENCHANT = key("enchant", GLFW.GLFW_KEY_X);
    public static final KeyMapping POTION = key("potion", GLFW.GLFW_KEY_P);

    /** NEI's {@code gui.hide}: hides all of NEI's panels until pressed again. */
    public static final KeyMapping HIDE = key("hide", GLFW.GLFW_KEY_O);
    /** NEI's {@code gui.overlay}: with Shift over an item, fills the crafting grid with its recipe. */
    public static final KeyMapping OVERLAY = key("overlay", GLFW.GLFW_KEY_S);
    /** NEI's {@code gui.getprevioussearch} / {@code gui.getnextsearch}: walk the search history while typing. */
    public static final KeyMapping PREV_SEARCH = key("prev_search", GLFW.GLFW_KEY_UP);
    public static final KeyMapping NEXT_SEARCH = key("next_search", GLFW.GLFW_KEY_DOWN);
    /** NEI's {@code recipe.prev_machine} etc.: change the recipe type and the recipe page in the recipe window. */
    public static final KeyMapping PREV_MACHINE = key("prev_machine", GLFW.GLFW_KEY_UP);
    public static final KeyMapping NEXT_MACHINE = key("next_machine", GLFW.GLFW_KEY_DOWN);
    public static final KeyMapping PREV_RECIPE = key("prev_recipe", GLFW.GLFW_KEY_LEFT);
    public static final KeyMapping NEXT_RECIPE = key("next_recipe", GLFW.GLFW_KEY_RIGHT);
    /** NEI's {@code bookmark.favorite} (item panel: saves the favorite recipe tree) and {@code bookmark.favorite_item}
     * (recipe window: favorite ingredient); both with Shift. */
    public static final KeyMapping FAVORITE = key("favorite", GLFW.GLFW_KEY_F);
    public static final KeyMapping FAVORITE_ITEM = key("favorite_item", GLFW.GLFW_KEY_F);
    /** NEI's {@code bookmark.remove_recipe}: Shift + key over a bookmark group removes the group. */
    public static final KeyMapping REMOVE_GROUP = key("remove_group", GLFW.GLFW_KEY_A);

    /** NEI's {@code gui.search}: focuses the search field (the panel must be showing). */
    public static final KeyMapping FOCUS_SEARCH = key("focus_search", GLFW.GLFW_KEY_F);

    /**
     * NEI's {@code world.*} keys (unbound by default, like NEI): the toolbar's time, rain, heal and gamemode buttons from the
     * keyboard, while no screen is open.
     */
    public static final KeyMapping WORLD_DAWN = world("world_dawn");
    public static final KeyMapping WORLD_NOON = world("world_noon");
    public static final KeyMapping WORLD_DUSK = world("world_dusk");
    public static final KeyMapping WORLD_MIDNIGHT = world("world_midnight");
    public static final KeyMapping WORLD_RAIN = world("world_rain");
    public static final KeyMapping WORLD_HEAL = world("world_heal");
    public static final KeyMapping WORLD_CREATIVE = world("world_creative");

    /** NEI's {@code world.moboverlay}: F7 by default (the only world key NEI binds). */
    public static final KeyMapping WORLD_MOBS = world("world_mobs", GLFW.GLFW_KEY_F7);

    private NEIKeys() {}

    private static KeyMapping world(String name) {
        return world(name, GLFW.GLFW_KEY_UNKNOWN);
    }

    private static KeyMapping world(String name, int glfwKey) {
        return new KeyMapping("key." + NotEnoughItems.MODID + "." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, glfwKey, CATEGORY);
    }

    /** True if this key is bound and is the one pressed; Shift / Ctrl held do not matter (NEI's keys work with them). */
    static boolean is(KeyMapping mapping, InputConstants.Key pressed) {
        return !mapping.isUnbound() && mapping.getKey().equals(pressed);
    }

    private static KeyMapping key(String name, int glfwKey) {
        return new KeyMapping("key." + NotEnoughItems.MODID + "." + name, KeyConflictContext.GUI,
                InputConstants.Type.KEYSYM, glfwKey, CATEGORY);
    }

    @SubscribeEvent
    static void onRegister(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(RECIPE);
        event.register(USAGE);
        event.register(BOOKMARK);
        event.register(TOGGLE_BOOKMARKS);
        event.register(BACK);
        event.register(OPEN_TREE);
        event.register(PULL_ITEMS);
        event.register(CHAT_LINK);
        event.register(CRAFT_ITEMS);
        event.register(COPY_NAME);
        event.register(COPY_ID);
        event.register(COPY_TAGS);
        event.register(ZOOM_TOGGLE);
        event.register(ZOOM_HOLD);
        event.register(ZOOM_IN);
        event.register(ZOOM_OUT);
        event.register(HUD_TOGGLE);
        event.register(ENCHANT);
        event.register(POTION);
        for (KeyMapping m : new KeyMapping[] { HIDE, OVERLAY, PREV_SEARCH, NEXT_SEARCH, PREV_MACHINE, NEXT_MACHINE,
                PREV_RECIPE, NEXT_RECIPE, FAVORITE, FAVORITE_ITEM, REMOVE_GROUP, FOCUS_SEARCH, WORLD_DAWN, WORLD_NOON,
                WORLD_DUSK, WORLD_MIDNIGHT, WORLD_RAIN, WORLD_HEAL, WORLD_CREATIVE, WORLD_MOBS }) {
            event.register(m);
        }
    }

    /** The key currently bound to a mapping, as text for display, e.g. "R". */
    static String name(KeyMapping mapping) {
        return mapping.getTranslatedKeyMessage().getString();
    }
}
