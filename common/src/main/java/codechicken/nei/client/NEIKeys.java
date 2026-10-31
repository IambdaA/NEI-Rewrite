package codechicken.nei.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import codechicken.nei.NotEnoughItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Every NEI hotkey, rebindable under Options > Controls > NotEnoughItems. They only act while a screen is open
 * , so they never clash with in-game keys.
 *
 * Escape (close the recipe window) and the arrow keys (change recipe page) are fixed.
 */
public final class NEIKeys {

    public static final KeyMapping.Category CATEGORY = ClientPlatform.HOLDER.get()
            .category(Identifier.fromNamespaceAndPath(NotEnoughItems.MODID, "nei"));

    private static final List<KeyMapping> ALL = new ArrayList<>();
    /** The keys that only act while a screen is open (the loader must not let them take the game's own keys). */
    private static final Set<KeyMapping> GUI_ONLY = Collections.newSetFromMap(new IdentityHashMap<>());

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

    /** Not in NEI: over an item or fluid, types its name into the search field of an open Applied Energistics terminal. */
    public static final KeyMapping TERMINAL_NAME = key("terminal_name", GLFW.GLFW_KEY_T);

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
    public static final KeyMapping HUD_TOGGLE = make("highlight_tips", false, GLFW.GLFW_KEY_KP_0);

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
        return make(name, false, glfwKey);
    }

    /** True if this key is bound and is the one pressed; Shift / Ctrl held do not matter (NEI's keys work with them). */
    static boolean is(KeyMapping mapping, InputConstants.Key pressed) {
        return !mapping.isUnbound() && ClientPlatform.HOLDER.get().boundKey(mapping).equals(pressed);
    }

    private static KeyMapping key(String name, int glfwKey) {
        return make(name, true, glfwKey);
    }

    private static KeyMapping make(String name, boolean guiOnly, int glfwKey) {
        final KeyMapping mapping = ClientPlatform.HOLDER.get().newKey("key." + NotEnoughItems.MODID + "." + name, guiOnly, glfwKey);
        ALL.add(mapping);
        if (guiOnly) GUI_ONLY.add(mapping);
        return mapping;
    }

    /** Every hotkey, for the loader to register. */
    public static List<KeyMapping> all() {
        return List.copyOf(ALL);
    }

    /** Whether the key only acts while a screen is open. */
    public static boolean isGuiOnly(KeyMapping mapping) {
        return GUI_ONLY.contains(mapping);
    }

    /** The key currently bound to a mapping, as text for display, e.g. "R". */
    static String name(KeyMapping mapping) {
        return mapping.getTranslatedKeyMessage().getString();
    }
}
