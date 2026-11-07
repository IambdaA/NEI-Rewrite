package codechicken.nei;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;


/**
 * Client config, shown by the wrench button as NeoForge's settings screen with one sub screen per NEI options group.
 * Colours are written as {@code #AARRGGBB}. Generated layout: keep names in step with {@code lang/en_us.json}.
 */
public final class NEIConfig {

    private static final Cfg.Builder BUILDER = new Cfg.Builder();
    private static final Pattern COLOR = Pattern.compile("#[0-9A-Fa-f]{8}");

    public enum Kind {
        BOOL, INT, COLOR,
        /** NEI's cheat mode button: Recipe / Utility / Cheat Mode ({@link NEIConfig#cheatMode()}); its value is {@link NEIConfig#CHEAT_ENABLED}. */
        MODE,
        /** NEI's OptionStringSet: a row of icon buttons that switch the entries of a comma separated string on and off ({@link #SET_ITEMS}). */
        SET,
        /** A whole number 0 .. n - 1 shown as a button that cycles through named choices ({@link #CHOICES}). */
        CHOICE,
        /** A text field for a string ({@link Cfg.StringValue}): NEI's OptionTextField. */
        TEXT,
        /** A button that does something when clicked ({@link #ACTIONS}); it says {@link #STUB_TEXT} (NEI's Dump buttons). */
        ACTION,
        /** An NEI option NEI Rewrite has no feature for: a greyed out button that says {@link #STUB_TEXT} and does nothing. */
        STUB,
        /** The same as a greyed out text field (a number when the option's max is above 0). */
        STUB_FIELD
    }

    /** What the greyed out button or field of a {@link Kind#STUB} / {@link Kind#STUB_FIELD} option says (NEI's default). */
    public static final Map<Option, String> STUB_TEXT = new java.util.HashMap<>();

    /** What the button of a {@link Kind#ACTION} row does. */
    public static final Map<Option, Runnable> ACTIONS = new java.util.HashMap<>();

    /** An {@link Kind#ACTION} row's mode button (NEI's DataDumper mode: CSV / NBT / Json ...): its value and the names. */
    public static final Map<Option, Cfg.IntValue> ACTION_MODES = new java.util.HashMap<>();
    public static final Map<Option, String[]> ACTION_MODE_NAMES = new java.util.HashMap<>();

    /** The names of the choices of a {@link Kind#CHOICE} option, by its value. */
    public static final Map<Cfg.ConfigValue<?>, String[]> CHOICES = new java.util.HashMap<>();

    /** What happens after the settings screen changed an option (NEI's {@code OptionToggleButton#onClick} overrides). */
    public static final Map<Cfg.ConfigValue<?>, Runnable> AFTER_CHANGE = new java.util.HashMap<>();

    /** Options the settings screen greys out while the condition holds (NEI's {@code Option#isEnabled}). */
    public static final Map<Cfg.ConfigValue<?>, java.util.function.BooleanSupplier> DISABLED_WHEN = new java.util.HashMap<>();

    /** The entries of a {@link Kind#SET} option as {@code {key, tooltip name}}, in button order. */
    public static final Map<Cfg.ConfigValue<?>, String[][]> SET_ITEMS = new java.util.HashMap<>();

    /** NEI's three modes (inventory.cheatmode 0, 1, 2). */
    public static final String[] MODE_NAMES = { "Recipe Mode", "Utility Mode", "Cheat Mode" };

    /** The button texts of a {@link Kind#BOOL} option as {@code {on, off}}; without an entry the button says Yes / No. */
    public static final Map<Cfg.ConfigValue<?>, String[]> BOOL_TEXT = new java.util.HashMap<>();

    /**
     * One option as the settings screen shows it. {@code section} is the page it is on, as a dotted path in NEI's own tree
     * ({@code inventory.bookmarks}); a record without a value only makes the page appear in its parent's list. An empty
     * {@code label} gives a button over the whole row (NEI's options without a prefix).
     */
    public record Option(String section, String label, String tip, Cfg.ConfigValue<?> value, Kind kind, int min,
            int max) {}

    /** Section key to its name, in screen order. */
    public static final Map<String, String> SECTIONS = new LinkedHashMap<>();
    public static final List<Option> OPTIONS = new ArrayList<>();

    public static final Cfg.BooleanValue PANEL_ENABLED = BUILDER
            .comment("Show the item panel, bookmarks and the other NEI panels next to container screens.")
            .define("panelEnabled", true);

    public static final Cfg.BooleanValue CHEAT_ENABLED = BUILDER
            .comment("A plain click on a list item gives it and Shift + click opens recipes (as NEI in cheat mode). Needs creative mode, cheats or operator permission.")
            .define("cheatEnabled", true);

    public static final Cfg.BooleanValue TOOLBAR_IN_SURVIVAL = BUILDER
            .comment("Show the toolbar (time, weather, heal ...) in survival mode too. It is always shown in creative mode. Needs cheats or operator permission.")
            .define("toolbarInSurvival", false);

    public static final Cfg.BooleanValue AUTOCRAFT = BUILDER
            .comment("Autocrafting: with a crafting grid open, Shift + the craft key over a bookmarked recipe, crafting chain group or a recipe of the recipe window crafts it.")
            .define("autocrafting", true);

    public static final Cfg.BooleanValue IGNORE_POTION_OVERLAP = BUILDER
            .comment("Ignore overlap with the potion effect boxes next to the inventory: the item panels no longer leave out the slots under them (NEI: inventory.bookmarks.ignorePotionOverlap).")
            .define("ignorePotionOverlap", false);

    /** NEI's "creative+" mode: the creative inventory key opens the extended inventory. Set by the gamemode button. */
    /** NEI's magnet mode (toolbar button): dropped items drift to you. Needs NEI Rewrite on the server. */
    public static final Cfg.BooleanValue MAGNET = BUILDER
            .comment("Magnet mode (set by the toolbar button): dropped items near you drift to you. Needs NEI Rewrite on the server.")
            .define("magnet", false);

    public static final Cfg.BooleanValue CREATIVE_PLUS = BUILDER
            .comment("Creative+ mode (set by the gamemode button): in creative mode the inventory key opens the extended creative inventory. Needs NEI Rewrite on the server.")
            .define("creativePlus", false);

    public static final Cfg.StringValue ITEM_SORT = BUILDER
            .comment("The order the item list is sorted in: comma separated keys (minecraft, mod, id, default, name), each deciding when the ones before it tie. Changed in the Item Panel Sorting settings.")
            .define("itemSort", codechicken.nei.item.SortOrder.DEFAULT);

    public static final Cfg.StringValue UTILITIES = BUILDER
            .comment("NEI's inventory.utilities: the toolbar features Utility Mode shows (time, rain, heal, delete, magnet, gamemode, enchant, potion, item), comma separated. Changed in Inventory > Utilities.")
            .define("utilities", "delete, magnet");

    public static final Cfg.StringValue GAMEMODES = BUILDER
            .comment("NEI's inventory.gamemodes: the gamemodes the gamemode button cycles through besides survival (creative, creative+, adventure), comma separated. Changed in Inventory > Gamemodes.")
            .define("gamemodes", "creative, creative+, adventure");

    public static final Cfg.BooleanValue SHOW_HOTKEYS = BUILDER
            .comment("NEI's inventory.showHotkeys: holding Alt over an item shows the list of its hotkeys.")
            .define("showHotkeys", true);

    public static final Cfg.BooleanValue HOTKEYS_HELP_TEXT = BUILDER
            .comment("NEI's inventory.hotkeysHelpText: item tooltips end with \"Hold ALT for hotkeys\".")
            .define("hotkeysHelpText", true);

    /** 0 NTNH-Approximate, 1 GTNH-Modernity, 2 Vanilla ({@link #CHOICES}). NEI takes the look from the resource pack's button texture. */
    public static final Cfg.IntValue UI_STYLE = BUILDER
            .comment("Look of the buttons: 0 NTNH-Approximate (slate buttons that approximate the look of the NTNH pack), 1 GTNH-Modernity (the dark buttons of Modernity-GTNH), 2 Vanilla (classic grey).")
            .defineInRange("uiStyle", 0, 0, 2);

    // ---- Collapsible Items ----
    public static final Cfg.BooleanValue BOOKMARKS_WORLD = BUILDER
            .comment("Keep separate bookmarks for each world / server (a new world starts with the global bookmarks).")
            .define("bookmarksWorldSpecific", false);

    public static final Cfg.BooleanValue FAVORITES_WORLD = BUILDER
            .comment("Keep separate favorite recipes for each world / server (a new world starts with the global ones).")
            .define("favoritesWorldSpecific", false);

    static {
        BUILDER.comment("Groups similar items (spawn eggs, music discs ...) into one slot of the item list. Alt + click a group opens it; the G button opens all.").push("collapsible");
    }

    public static final Cfg.BooleanValue COLLAPSIBLE_ENABLED = BUILDER
            .comment("Group similar items into one slot of the item list.")
            .define("collapsibleItems", true);

    public static final Cfg.BooleanValue COLLAPSIBLE_CUSTOM_NAME = BUILDER
            .comment("Show the group's own name instead of the first item's name while a group is collapsed.")
            .define("collapsibleItemsCustomName", true);

    public static final Cfg.StringValue COLLAPSIBLE_EXPANDED_COLOR = BUILDER
            .comment("Background colour of an open group, as #AARRGGBB (alpha, red, green, blue in hex).")
            .define("collapsibleExpandedColor", "#335555EE", o -> o instanceof String s && COLOR.matcher(s).matches());

    public static final Cfg.StringValue COLLAPSIBLE_COLLAPSED_COLOR = BUILDER
            .comment("Background colour of a closed group, as #AARRGGBB (alpha, red, green, blue in hex).")
            .define("collapsibleCollapsedColor", "#335555EE", o -> o instanceof String s && COLOR.matcher(s).matches());

    static {
        BUILDER.pop();
    }

    // ---- Craftables Panel ----
    static {
        BUILDER.comment("What you can craft from your inventory, shown above the history panel.").push("craftables");
    }

    public static final Cfg.BooleanValue CRAFTABLES_ENABLED = BUILDER
            .comment("Show what you can craft from your inventory (crafting table and inventory grid recipes).")
            .define("craftablesPanel", false);

    /** 0 Off, 1 Icon, 2 Highlight. NEI's {@code inventory.guirecipe.itemPresenceOverlay} (NEI's default is 1). */
    public static final Cfg.IntValue PRESENCE_OVERLAY = BUILDER
            .comment("Ingredients on the \"+\" button's recipe while the mouse is on it: 0 Off, 1 Icon (a cross / tick in the corner of the slot), 2 Highlight (red / green slot). NEI's own default is Icon.")
            .defineInRange("itemPresenceOverlay", 2, 0, 2);

    public static final Cfg.BooleanValue RECIPE_GHOST = BUILDER
            .comment("While the mouse is on a crafting recipe's \"+\", draw the ingredients faded over the open screen's crafting grid. The recipe window covers that grid, so this is off by default.")
            .define("recipeGhostItems", false);

    public static final Cfg.BooleanValue FLUID_MB_ONLY = BUILDER
            .comment("Show fluid amounts always in millibuckets (2500mB) instead of buckets from 1000 on (2.5B). Off by default.")
            .define("fluidAmountsInMillibuckets", false);

    public static final Cfg.BooleanValue FAVORITE_ANY_RESULT = BUILDER
            .comment("A favorite recipe also counts for its other results: a recipe tree finds a favorite recipe of an ingredient even if the recipe was favorited under another result (an oil refinery recipe makes several fluids). NEI only knows the first result.")
            .define("favoriteAnyResult", false);

    public static final Cfg.BooleanValue FAVORITE_WHEEL = BUILDER
            .comment("NEI's way of choosing the result a favorite is for: the mouse wheel over the heart changes it. Off by default (see favoriteAnyResult).")
            .define("favoriteWheel", false);

    public static final Cfg.BooleanValue PRESENCE_HIGHLIGHT_PRESENT = BUILDER
            .comment("Also mark the ingredients you have (green / tick), not only the missing ones.")
            .define("slotHighlightPresent", true);

    public static final Cfg.IntValue MOB_RANGE = BUILDER
            .comment("How far around you the mob spawn overlay (F7) reaches, in blocks. NEI uses 16.")
            .defineInRange("mobOverlayRange", 16, 8, 32);

    public static final Cfg.IntValue CRAFTABLES_ROWS = BUILDER
            .comment("How many rows the craftables panel may use.")
            .defineInRange("craftablesRows", 2, 1, 5);

    public static final Cfg.BooleanValue CRAFTABLES_FAVORITES_ONLY = BUILDER
            .comment("Only show recipes you marked as favorite (the heart).")
            .define("craftablesFavoritesOnly", false);

    public static final Cfg.StringValue CRAFTABLES_COLOR = BUILDER
            .comment("Colour of the dashed line above the panel, as #AARRGGBB.")
            .define("craftablesColor", "#EE555555", o -> o instanceof String s && COLOR.matcher(s).matches());

    static {
        BUILDER.pop();
    }

    // ---- History Panel ----
    static {
        BUILDER.comment("The items whose recipes or uses you looked up, under the item list.").push("history");
    }

    public static final Cfg.BooleanValue HISTORY_ENABLED = BUILDER
            .comment("Show the history panel under the item list.")
            .define("historyEnabled", true);

    public static final Cfg.StringValue HISTORY_COLOR = BUILDER
            .comment("Colour of the dashed line above the panel, as #AARRGGBB.")
            .define("historyColor", "#EE555555", o -> o instanceof String s && COLOR.matcher(s).matches());

    public static final Cfg.IntValue HISTORY_ROWS = BUILDER
            .comment("How many rows the history panel may use.")
            .defineInRange("historyRows", 2, 1, 5);

    public static final Cfg.BooleanValue HISTORY_SAVE = BUILDER
            .comment("Keep the history between sessions (see the world specific option).")
            .define("historySave", false);

    public static final Cfg.BooleanValue HISTORY_WORLD = BUILDER
            .comment("Keep a separate saved history for each world / server.")
            .define("historyWorldSpecific", true);

    static {
        BUILDER.pop();
    }

    // ---- Subsets Widget ----
    static {
        BUILDER.comment("The Subsets dropdown above the inventory.").push("subsets");
    }

    public static final Cfg.BooleanValue SUBSETS_ENABLED = BUILDER
            .comment("Show the Subsets dropdown above the inventory.")
            .define("subsetsEnabled", true);

    public static final Cfg.BooleanValue SUBSETS_ON_TOP = BUILDER
            .comment("On: the Subsets bar at the top centre. Off: a button left of the search field.")
            .define("subsetsOnTop", true);

    static {
        BUILDER.pop();
    }

    // ---- Search ----
    static {
        BUILDER.comment("How the item search reads the text (NEI's inventory.search options).").push("search");
    }

    /** 0 Plain, 1 Extended (? and * wildcards, r/regex/), 2 Regex, 3 Extended+ (expressions). */
    public static final Cfg.IntValue SEARCH_PATTERN_MODE = BUILDER
            .comment("Search mode: 0 Plain, 1 Extended (? one character, * anything, r/regex/), 2 Regex, 3 Extended+ (expressions with | - { } quotes and /regex/).")
            .defineInRange("patternMode", 2, 0, 3);

    /** 0 AND, 1 Space, 2 AND-X. */
    public static final Cfg.IntValue SEARCH_SPACE_MODE = BUILDER
            .comment("Space works like: 0 AND (every word must match), 1 Space (a space is part of the text), 2 AND-X (every word must match within the same kind of text).")
            .defineInRange("spaceMode", 0, 0, 2);

    public static final Cfg.IntValue SEARCH_MOD_MODE = BUILDER
            .comment("Search mode for mod names (prefix @): 0 Always, 1 Prefix, 2 Never.")
            .defineInRange("modNameSearchMode", 1, 0, 2);

    public static final Cfg.IntValue SEARCH_TOOLTIP_MODE = BUILDER
            .comment("Search mode for tooltips (prefix #): 0 Always, 1 Prefix, 2 Never.")
            .defineInRange("tooltipSearchMode", 1, 0, 2);

    public static final Cfg.IntValue SEARCH_ID_MODE = BUILDER
            .comment("Search mode for resource ids (prefix &): 0 Always, 1 Prefix, 2 Never.")
            .defineInRange("identifierSearchMode", 1, 0, 2);

    public static final Cfg.IntValue SEARCH_TAG_MODE = BUILDER
            .comment("Search mode for item tags (prefix $): 0 Always, 1 Prefix, 2 Never.")
            .defineInRange("tagSearchMode", 1, 0, 2);

    public static final Cfg.IntValue SEARCH_SUBSET_MODE = BUILDER
            .comment("Search mode for item subsets (prefix %): 0 Always, 1 Prefix, 2 Never. With Prefix, Shift + click in the Subsets menu searches.")
            .defineInRange("subsetsSearchMode", 1, 0, 2);

    public static final Cfg.BooleanValue SEARCH_QUOTE_DROP = BUILDER
            .comment("Put quotes around an item name with spaces when it is sent to the search (Shift + click on an item of the Subsets menu).")
            .define("quoteDropItemName", true);

    public static final Cfg.BooleanValue SYNC_TERMINAL_SEARCH = BUILDER
            .comment("Keep the item search and the search field of an Applied Energistics terminal the same (AE2's \"sync with JEI\", done with NEI's search).")
            .define("syncTerminalSearch", true);

    static {
        BUILDER.pop();
    }

    // ---- Item Quantity Widget ----
    static {
        BUILDER.comment("The - / + field under the item list.").push("quantity");
    }

    public static final Cfg.BooleanValue QUANTITY_SHOW = BUILDER
            .comment("Show the quantity field under the item list: how many items a click on a list item gives.")
            .define("showItemQuantity", true);

    public static final Cfg.IntValue QUANTITY = BUILDER
            .comment("How many items a click on a list item gives (0 = one stack). The field under the item list changes this too.")
            .defineInRange("itemQuantity", 0, 0, 999999999);

    static {
        BUILDER.pop();
    }

    // ---- Item Zoom Panel ----
    static {
        BUILDER.comment("Shows the hovered item enlarged beside the window.").push("itemzoom");
    }

    public static final Cfg.BooleanValue ZOOM_ENABLED = BUILDER
            .comment("Show the hovered item enlarged beside the window. Shift + the toggle key switches it.")
            .define("itemZoomEnabled", true);

    public static final Cfg.BooleanValue ZOOM_NEI_ONLY = BUILDER
            .comment("Zoom only items of the item list, history and bookmark panels.")
            .define("itemZoomNeiOnly", false);

    public static final Cfg.BooleanValue ZOOM_ONLY_SOLID = BUILDER
            .comment("Zoom only blocks that are solid.")
            .define("itemZoomOnlySolid", false);

    public static final Cfg.IntValue ZOOM_SIDE = BUILDER
            .comment("Where to show the item when both sides are free: 0 = the side whose panel has fewer items, 1 = left, 2 = right.")
            .defineInRange("itemZoomPreferredSide", 0, 0, 2);

    public static final Cfg.BooleanValue ZOOM_HELP = BUILDER
            .comment("Show the toggle key below the zoomed item.")
            .define("itemZoomHelpText", true);

    public static final Cfg.IntValue ZOOM_AMOUNT = BUILDER
            .comment("How much of the free side area the item fills, in thousandths. The zoom in / out keys change this too.")
            .defineInRange("itemZoomAmount", 500, 0, 1000);

    public static final Cfg.BooleanValue ZOOM_SHOW_NAME = BUILDER
            .comment("Show the item's name below the zoomed item.")
            .define("itemZoomShowName", true);

    public static final Cfg.StringValue ZOOM_NAME_COLOR = BUILDER
            .comment("Colour of the item name, as #AARRGGBB.")
            .define("itemZoomNameColor", "#FFFFFFFF", o -> o instanceof String s && COLOR.matcher(s).matches());

    static {
        BUILDER.pop();
    }

    // ---- Mouse Wheel Transfer ----
    static {
        BUILDER.comment("The wheel over a slot of a container moves single items.").push("transfer");
    }

    public static final Cfg.BooleanValue WHEEL_TRANSFER = BUILDER
            .comment("Wheel over an item slot: wheel down moves one item out (as Shift + click would), wheel up pulls one in.")
            .define("mouseWheelTransfer", true);

    public static final Cfg.BooleanValue WHEEL_TRANSFER_INVERT = BUILDER
            .comment("Swap the two wheel directions.")
            .define("mouseWheelTransferInvert", false);

    static {
        BUILDER.pop();
    }

    // ---- Highlight Tooltips ----
    static {
        BUILDER.comment("A box with the name of the block you look at.").push("highlight");
    }

    public static final Cfg.BooleanValue HUD_ENABLED = BUILDER
            .comment("Show the name of the block you look at, with its item, in a box on the screen. A key (numpad 0) switches it.")
            .define("highlightTips", false);

    public static final Cfg.IntValue HUD_X = BUILDER
            .comment("0 = left edge, 10000 = right edge.")
            .defineInRange("highlightTipsX", 5000, 0, 10000);

    public static final Cfg.IntValue HUD_Y = BUILDER
            .comment("0 = top edge, 10000 = bottom edge.")
            .defineInRange("highlightTipsY", 100, 0, 10000);

    static {
        BUILDER.pop();
    }

    // ---- NEI options that were greyed out rows before: each has a feature now; the defaults are NEI's ----
    public static final Cfg.BooleanValue BOOKMARKS_ENABLED = BUILDER
            .comment("NEI's inventory.bookmarks.enabled: show the bookmarks panel (the book button and the bookmark.hide key switch it).")
            .define("bookmarksEnabled", true);

    public static final Cfg.BooleanValue FAVORITES_ENABLED = BUILDER
            .comment("NEI's inventory.favorites.enabled: the favorites (the heart buttons of the recipe window and the recipe trees' use of them).")
            .define("favoritesEnabled", true);

    public static final Cfg.BooleanValue TREE_ZOOM_NO_CTRL = BUILDER
            .comment("NEI's inventory.bookmarks.craftingTreeZoomWithoutModifier: zoom the crafting tree with the mouse wheel without holding Ctrl.")
            .define("craftingTreeZoomWithoutModifier", false);

    public static final Cfg.BooleanValue SEARCH_CENTERED = BUILDER
            .comment("NEI's inventory.search.widgetPosition: true = the search field at the bottom centre of the screen, false = under the item list, left of the quantity field.")
            .define("searchWidgetCentered", true);

    public static final Cfg.BooleanValue SEARCH_HIDE_UNTIL = BUILDER
            .comment("NEI's inventory.search.hideUntilSearching: the item list stays empty until something is typed into the search field.")
            .define("hideItemsUntilSearching", false);

    /** 0 No, 1 Yes, 2 Extended ({@link #CHOICES}). */
    public static final Cfg.IntValue SEARCH_AUTOFOCUS = BUILDER
            .comment("NEI's inventory.search.widgetAutofocus: focus the search field when the inventory opens, until the mouse moves unless typing started first. 0 No, 1 Yes, 2 Extended (the inventory key does not type).")
            .defineInRange("searchWidgetAutofocus", 0, 0, 2);

    public static final Cfg.BooleanValue SEARCH_OLD_FORMAT = BUILDER
            .comment("NEI's inventory.search.format: true = the old format (a space is part of the text, % searches mod names and @ subsets, names always search mods), false = the extended format. Changing it in the settings also resets the search modes as NEI does.")
            .define("searchOldFormat", false);

    public static final Cfg.BooleanValue FAVORITE_GENERATE = BUILDER
            .comment("NEI's inventory.favorites.generateSingleRecipeFavorites: an item that exactly one recipe makes counts as having that recipe as favorite (recipe trees use it when you favorited none).")
            .define("generateSingleRecipeFavorites", false);

    public static final Cfg.IntValue FAVORITE_DEPTH = BUILDER
            .comment("NEI's inventory.favorites.depth: how many levels of sub-recipes a recipe tree made from a favorite (Shift + the bookmark key on the heart) goes down.")
            .defineInRange("favoritesDepth", 9, 0, 100);

    public static final Cfg.BooleanValue RELEASE_NOTICE = BUILDER
            .comment("After joining a world, a chat message asks to report issues of this release on GitHub (its link turns it off).")
            .define("releaseNotice", true);

    // ---- Commands (NEI's command.*): what the toolbar sends; a template that does not start with / turns its button off ----
    public static final Cfg.StringValue COMMAND_GAMEMODE = BUILDER
            .comment("NEI's command.creative: {0} = survival, creative or adventure, {1} = player.")
            .define("commandGamemode", "/gamemode {0} {1}");

    public static final Cfg.StringValue COMMAND_ITEM = BUILDER
            .comment("NEI's command.item: {0} = player, {1} = id, {2} = amount, {3} = damage (always 0), {4} = the item's components as [key=value,...], {5} = id again (NEI: name).")
            .define("commandItem", "/give {0} {1}{4} {2}");

    public static final Cfg.StringValue COMMAND_TIME = BUILDER
            .comment("NEI's command.time: {0} = the world time to set (the next dawn, noon, dusk or midnight).")
            .define("commandTime", "/time set {0}");

    public static final Cfg.StringValue COMMAND_RAIN = BUILDER
            .comment("NEI's command.rain: {0} = 1 to turn rain on, 0 to turn it off, {1} = rain or clear.")
            .define("commandRain", "/weather {1}");

    public static final Cfg.StringValue COMMAND_HEAL = BUILDER
            .comment("NEI's command.heal: {0} = player. Several commands may be separated by ; (here: health, then food).")
            .define("commandHeal", "/effect give {0} minecraft:instant_health 1 4; /effect give {0} minecraft:saturation 1 20");

    /** NEI's {@code sendCommand}: fills {@code {0}, {1} ...} into the template and runs each ;-separated command (none if empty). */
    public static void runCommand(Cfg.StringValue template, Object... args) {
        final String text = template.get();
        if (text == null || text.isBlank()) return;
        for (String part : text.split(";")) { // split the template first: an argument (item components) may hold a ;
            String cmd = part.trim();
            for (int i = 0; i < args.length; i++) cmd = cmd.replace("{" + i + "}", String.valueOf(args[i]));
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            if (!cmd.isEmpty()) NEIActions.command(cmd);
        }
    }

    /** NEI's {@code canPerformAction} without a server mod: the command template must start with {@code /}. */
    public static boolean commandSet(Cfg.StringValue template) {
        final String text = template.get();
        return text != null && text.trim().startsWith("/");
    }

    /** NEI's tools.dump.itempanel mode: 0 CSV, 1 NBT, 2 Json. */
    public static final Cfg.IntValue DUMP_ITEMPANEL_MODE = BUILDER
            .comment("NEI's Item Panel dump format (Tools > Data Dumps): 0 CSV, 1 NBT, 2 Json.")
            .defineInRange("dumpItemPanelMode", 0, 0, 2);

    /** NEI's tools.dump.handlers mode: 0 CSV, 1 Json. */
    public static final Cfg.IntValue DUMP_HANDLERS_MODE = BUILDER
            .comment("NEI's NEI Handlers dump format (Tools > Data Dumps): 0 CSV, 1 Json.")
            .defineInRange("dumpHandlersMode", 0, 0, 1);

    // ---- panel scale, fluid tooltips, overlays, panel positions ----
    public static final Cfg.IntValue ITEM_PANEL_SCALE = BUILDER
            .comment("NEI's inventory.itempanelScale: the size of the slots of the item, bookmark, history and craftables panels, in percent (100 = 18 px).")
            .defineInRange("itemPanelScale", 100, 1, 1000);

    public static final Cfg.BooleanValue FLUID_TEMPERATURE = BUILDER
            .comment("NEI's inventory.tooltip.showFluidTemperature: a fluid's tooltip shows its temperature.")
            .define("showFluidTemperature", true);

    public static final Cfg.BooleanValue FLUID_STATE = BUILDER
            .comment("NEI's inventory.tooltip.showFluidState: a fluid's tooltip shows whether it is a liquid or a gas.")
            .define("showFluidState", true);

    public static final Cfg.BooleanValue OVERLAYS_LOCK = BUILDER
            .comment("NEI's world.overlays.lock: the mob spawn overlay stays on when you change dimension or world.")
            .define("lockOverlays", true);

    /** One panel's four edges (NEI's world.panels.*): each edge's distance from its side, in hundred-thousandths of the free area. */
    public record PanelEdges(Cfg.IntValue left, Cfg.IntValue top, Cfg.IntValue right, Cfg.IntValue bottom) {

        /** NEI's {@code calculateBounds}: the paddings in pixels for a free area of this size. */
        public int[] pixels(int w, int h) {
            return new int[] { (int) Math.ceil(w * left.getAsInt() / 100000.0), (int) Math.ceil(h * top.getAsInt() / 100000.0),
                    (int) Math.ceil(w * right.getAsInt() / 100000.0), (int) Math.ceil(h * bottom.getAsInt() / 100000.0) };
        }
    }

    private static Cfg.IntValue edge(String panel, String side) {
        return BUILDER.comment("NEI's world.panels." + panel + "." + side + ": set in World > Panels Settings (0 - 100000).")
                .defineInRange("panels." + panel + "." + side, 0, 0, 100000);
    }

    public static final PanelEdges PANEL_BOOKMARKS = new PanelEdges(edge("bookmarks", "left"), edge("bookmarks", "top"),
            edge("bookmarks", "right"), edge("bookmarks", "bottom"));
    public static final PanelEdges PANEL_ITEMS = new PanelEdges(edge("items", "left"), edge("items", "top"),
            edge("items", "right"), edge("items", "bottom"));

    // ---- Bookmarks Panel and Favorites (NEI's inventory.bookmarks.* / inventory.favorites.*) ----
    public static final Cfg.BooleanValue BOOKMARKS_ANIMATION = BUILDER
            .comment("NEI's inventory.bookmarks.animation: a new bookmark pops up from the middle of its slot (REI style).")
            .define("bookmarksAnimation", true);

    /** 0 None, 1 In Horizontal View, 2 In Vertical View, 3 Always ({@link #CHOICES}). */
    public static final Cfg.IntValue BOOKMARK_RECIPE_TOOLTIPS = BUILDER
            .comment("NEI's inventory.bookmarks.recipeTooltipsMode: a bookmarked recipe's result shows the recipe in its tooltip. 0 None, 1 in normal groups, 2 in todo list groups, 3 always.")
            .defineInRange("recipeTooltipsMode", 1, 0, 3);

    /** 0 None, 1 Text, 2 Background ({@link #CHOICES}). */
    public static final Cfg.IntValue BOOKMARK_MARKER = BUILDER
            .comment("NEI's inventory.bookmarks.showRecipeMarkerMode: how a bookmark that remembers its recipe is marked. 0 None, 1 Text (an R in the corner), 2 Background (light blue).")
            .defineInRange("showRecipeMarkerMode", 0, 0, 2);

    public static final Cfg.IntValue BOOKMARK_VERTICAL_ROWS = BUILDER
            .comment("NEI's inventory.bookmarks.maxVerticalTooltipRows: in a crafting chain group's tooltip, a list with one item per row and its name may have this many rows (0: always the grid).")
            .defineInRange("maxVerticalTooltipRows", 12, 0, 50);

    public static final Cfg.StringValue BOOKMARK_MARKER_COLOR = BUILDER
            .comment("NEI's inventory.bookmarks.recipeMarkerColor: colour of the R marker and the xN of a bookmarked recipe, as #AARRGGBB.")
            .define("recipeMarkerColor", "#FFADADAD", o -> o instanceof String s && COLOR.matcher(s).matches());

    public static final Cfg.BooleanValue BOOKMARK_HANDLER_ICON = BUILDER
            .comment("NEI's inventory.bookmarks.showRecipeHandlerIcon: a bookmarked recipe's result carries the small icon of its recipe type.")
            .define("showRecipeHandlerIcon", false);

    public static final Cfg.BooleanValue BOOKMARK_ITEMS_WITH_RECIPE = BUILDER
            .comment("NEI's inventory.bookmarks.bookmarkItemsWithRecipe: an item bookmarked from the recipe window remembers the recipe it was in.")
            .define("bookmarkItemsWithRecipe", true);

    public static final Cfg.BooleanValue BOOKMARK_RECIPE_WITH_COUNT = BUILDER
            .comment("NEI's inventory.bookmarks.bookmarkRecipeWithCount: Shift + the bookmark key over a recipe keeps its amounts (as Ctrl + Shift does).")
            .define("bookmarkRecipeWithCount", false);

    public static final Cfg.BooleanValue FAVORITE_TOOLTIP_PANEL = BUILDER
            .comment("NEI's inventory.favorites.showRecipeTooltipInPanel: an item of the item list with a favorite recipe shows that recipe in its tooltip.")
            .define("showRecipeTooltipInPanel", true);

    public static final Cfg.BooleanValue FAVORITE_TOOLTIP_GUI = BUILDER
            .comment("NEI's inventory.favorites.showRecipeTooltipInGui: in the recipe window, a machine with a favorite recipe shows that recipe in its tooltip.")
            .define("showRecipeTooltipInGui", false);

    // ---- Recipes GUI (NEI's inventory.guirecipe.*) ----
    public static final Cfg.BooleanValue RECIPE_JEI_TABS = BUILDER
            .comment("NEI's inventory.guirecipe.jeiStyleTabs: show the recipe type tabs above the recipe window.")
            .define("jeiStyleTabs", true);

    /** 0 None, 1 Grid, 2 Scroll ({@link #CHOICES}). */
    public static final Cfg.IntValue RECIPE_CATALYSTS = BUILDER
            .comment("NEI's inventory.guirecipe.jeiStyleRecipeCatalyst: the machines column left of the recipe window. 0 None, 1 Grid (more columns when needed), 2 Scroll (one column with a scroll bar).")
            .defineInRange("jeiStyleRecipeCatalyst", 1, 0, 2);

    public static final Cfg.BooleanValue RECIPE_CYCLED_TOOLTIP = BUILDER
            .comment("NEI's inventory.guirecipe.cycledIngredientsTooltip: the tooltip of an ingredient with alternatives lists them (\"Accepts following\").")
            .define("cycledIngredientsTooltip", true);

    public static final Cfg.BooleanValue RECIPE_CREATIVE_TABS = BUILDER
            .comment("NEI's inventory.guirecipe.creativeTabStyle: true = the recipe type tabs look like the creative inventory's tabs, false = JEI style tabs.")
            .define("creativeTabStyle", false);

    public static final Cfg.BooleanValue RECIPE_INFINITE_SCROLL = BUILDER
            .comment("NEI's inventory.guirecipe.infiniteScroll: all recipes of a type in one list that the wheel and a scroll bar move, instead of pages.")
            .define("infiniteScroll", false);

    public static final Cfg.IntValue RECIPE_MAX_HEIGHT = BUILDER
            .comment("NEI's inventory.guirecipe.maxHeight: how tall the recipe window may get, in GUI pixels.")
            .defineInRange("recipeMaxHeight", 370, 166, 4000);

    public static final Cfg.BooleanValue RECIPE_SHIFT_OVERLAY = BUILDER
            .comment("NEI's inventory.guirecipe.shiftOverlayRecipe: a click on a crafting recipe's + only shows its items faded in the crafting grid; Shift + click moves the items. Off (the default here; NEI's is on): a click moves the items, Ctrl + click one set.")
            .define("shiftOverlayRecipe", false);

    public static final Cfg.BooleanValue RECIPE_SCROLL_PAGES = BUILDER
            .comment("NEI's inventory.guirecipe.scrollPages: the wheel over the recipes turns the pages (over the page row it always does).")
            .define("scrollPages", true);

    public static final Cfg.BooleanValue RECIPE_FREEZE_CYCLE = BUILDER
            .comment("NEI's inventory.guirecipe.freezeCycleOnHover: a recipe stops cycling its ingredients while the mouse is on one of its items or buttons (Shift always stops it).")
            .define("freezeCycleOnHover", false);

    public static final Cfg.BooleanValue RECIPE_HANDLER_INFO = BUILDER
            .comment("NEI's inventory.guirecipe.handlerInfo: Shift + D in the recipe window shows a box with what is known about the recipe type.")
            .define("handlerInfo", false);

    static {
    SECTIONS.put("sorting", "Item Panel Sorting");
    SECTIONS.put("collapsible", "Collapsible Items");
    SECTIONS.put("history", "History Panel");
    SECTIONS.put("craftables", "Craftables Panel");
    SECTIONS.put("quantity", "Item Quantity Widget");
    SECTIONS.put("itemzoom", "Item Zoom Panel");
    SECTIONS.put("transfer", "Mouse Wheel Transfer");
    SECTIONS.put("highlight", "Highlight Tooltips");
    SECTIONS.put("subsets", "Subsets Widget");
    SECTIONS.put("search", "Search Settings");
    SECTIONS.put("recipe", "Recipe Window");
    OPTIONS.add(new Option(null, "Show item panel", "Show the item panel, bookmarks and the other NEI panels next to container screens.", PANEL_ENABLED, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option(null, "Cheat mode (click gives items)", "A plain click on a list item gives it and Shift + click opens recipes (as NEI in cheat mode). Needs creative mode, cheats or operator permission.", CHEAT_ENABLED, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option(null, "Cheat toolbar in survival", "Show the toolbar (time, weather, heal ...) in survival mode too. It is always shown in creative mode. Needs cheats or operator permission.", TOOLBAR_IN_SURVIVAL, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("collapsible", "Enable collapsible items", "Group similar items into one slot of the item list.", COLLAPSIBLE_ENABLED, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("collapsible", "Show group name", "Show the group's own name instead of the first item's name while a group is collapsed.", COLLAPSIBLE_CUSTOM_NAME, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("collapsible", "Expanded group colour", "Background colour of an open group, as #AARRGGBB (alpha, red, green, blue in hex).", COLLAPSIBLE_EXPANDED_COLOR, Kind.COLOR, 0, 0));
    OPTIONS.add(new Option("collapsible", "Collapsed group colour", "Background colour of a closed group, as #AARRGGBB (alpha, red, green, blue in hex).", COLLAPSIBLE_COLLAPSED_COLOR, Kind.COLOR, 0, 0));
    OPTIONS.add(new Option("history", "Show history panel", "Show the items whose recipes or uses you looked up, under the item list.", HISTORY_ENABLED, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("history", "Line colour", "Colour of the dashed line above the panel, as #AARRGGBB.", HISTORY_COLOR, Kind.COLOR, 0, 0));
    OPTIONS.add(new Option("history", "Rows", "How many rows the history panel may use.", HISTORY_ROWS, Kind.INT, 1, 5));
    OPTIONS.add(new Option(null, "World specific bookmarks", "Keep separate bookmarks for each world / server. A new world starts with the global bookmarks.", BOOKMARKS_WORLD, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option(null, "World specific favorites", "Keep separate favorite recipes for each world / server. A new world starts with the global ones.", FAVORITES_WORLD, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option(null, "Mob spawn overlay range", "How far around you the mob spawn overlay (F7) reaches, in blocks (NEI: 16). Larger values draw more lines and may cost frame rate.", MOB_RANGE, Kind.INT, 8, 32));
    OPTIONS.add(new Option(null, "Autocrafting", "With a crafting grid open, Shift + the craft key over a bookmarked recipe, crafting chain group or a recipe of the recipe window crafts it (Shift + Ctrl: only what is missing).", AUTOCRAFT, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option(null, "Ignore overlap with potion effect HUD", "As in NEI: the item panels leave out the slots under the potion effect boxes the inventory draws beside it. On: they keep those slots (the boxes cover them).", IGNORE_POTION_OVERLAP, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("history", "Save history", "Keep the history between sessions (see the world specific option).", HISTORY_SAVE, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("history", "World specific history", "Keep a separate saved history for each world / server.", HISTORY_WORLD, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("subsets", "Show the Subsets dropdown", "Show the Subsets dropdown above the inventory.", SUBSETS_ENABLED, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("subsets", "Widget on top", "On: the Subsets bar at the top centre. Off: a button left of the search field.", SUBSETS_ON_TOP, Kind.BOOL, 0, 0));
    CHOICES.put(PRESENCE_OVERLAY, new String[] { "None", "Icon", "Background" });
    CHOICES.put(UI_STYLE, new String[] { "NTNH-Approximate", "GTNH-Modernity", "Vanilla" });
    OPTIONS.add(new Option(null, "UI Style", "The look of the buttons of NEI's screens, toolbar and panels. NEI takes it from the resource pack's button texture; here it is chosen. NTNH-Approximate: slate buttons that approximate the look of the NTNH pack. GTNH-Modernity: the dark buttons of Modernity-GTNH's dark UI. Vanilla: classic grey buttons.", UI_STYLE, Kind.CHOICE, 0, 2));
    CHOICES.put(SEARCH_PATTERN_MODE, new String[] { "Plain", "Extended", "Regex", "Extended+" });
    CHOICES.put(SEARCH_SPACE_MODE, new String[] { "AND", "Space", "AND-X" });
    CHOICES.put(SEARCH_MOD_MODE, new String[] { "Always", "Prefix: @", "Never" });
    CHOICES.put(SEARCH_TOOLTIP_MODE, new String[] { "Always", "Prefix: #", "Never" });
    CHOICES.put(SEARCH_ID_MODE, new String[] { "Always", "Prefix: &", "Never" });
    CHOICES.put(SEARCH_TAG_MODE, new String[] { "Always", "Prefix: $", "Never" });
    CHOICES.put(SEARCH_SUBSET_MODE, new String[] { "Always", "Prefix: %", "Never" });
    OPTIONS.add(new Option("search", "Search mode", "Plain: the text as typed. Extended: ? is one character, * anything, r/regex/ a regular expression. Regex: the text is a regular expression (plain if it is not valid). Extended+: expressions with | (or), - (not), { } (group), \"quotes\" and /regex/.", SEARCH_PATTERN_MODE, Kind.CHOICE, 0, 3));
    OPTIONS.add(new Option("search", "Space works like", "AND: every word must match. Space: a space is part of the text. AND-X: every word must match within the same kind of text (name, tooltip ...). Extended+ treats AND-X as AND.", SEARCH_SPACE_MODE, Kind.CHOICE, 0, 2));
    OPTIONS.add(new Option("search", "Mod name search", "When text is matched against mod names: always, only after the prefix, or never.", SEARCH_MOD_MODE, Kind.CHOICE, 0, 2));
    OPTIONS.add(new Option("search", "Tooltip search", "When text is matched against tooltips: always, only after the prefix, or never.", SEARCH_TOOLTIP_MODE, Kind.CHOICE, 0, 2));
    OPTIONS.add(new Option("search", "Resource ID search", "When text is matched against registry ids: always, only after the prefix, or never.", SEARCH_ID_MODE, Kind.CHOICE, 0, 2));
    OPTIONS.add(new Option("search", "Item tag search", "When text is matched against item tags: always, only after the prefix, or never.", SEARCH_TAG_MODE, Kind.CHOICE, 0, 2));
    OPTIONS.add(new Option("search", "Item subsets search", "When text is matched against item subsets: always, only after the prefix, or never. Prefix also lets Shift + click in the Subsets menu search.", SEARCH_SUBSET_MODE, Kind.CHOICE, 0, 2));
    OPTIONS.add(new Option("search", "Sync with AE2 terminals", "While an Applied Energistics terminal is open, typing in its search field also types in NEI's search and the other way round (AE2's \"sync with JEI\").", SYNC_TERMINAL_SEARCH, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("search", "Quote dropped item names", "Put quotes around an item name with spaces when it is sent to the search.", SEARCH_QUOTE_DROP, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("craftables", "Enable craftables panel", "Show what you can craft from your inventory (crafting table and inventory grid recipes).", CRAFTABLES_ENABLED, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("craftables", "Rows", "How many rows the craftables panel may use.", CRAFTABLES_ROWS, Kind.INT, 1, 5));
    OPTIONS.add(new Option("recipe", "Item presence overlay", "Ingredients of a crafting recipe while the mouse is on its \"+\" button: Off, Icon (a cross / tick in the slot's corner) or Highlight (red = missing, green = you have it). NEI's default is Icon.", PRESENCE_OVERLAY, Kind.CHOICE, 0, 2));
    OPTIONS.add(new Option("recipe", "Ghost items on the + button", "While the mouse is on a crafting recipe's \"+\", draw the ingredients faded over the open screen's crafting grid (NEI's overlay). Off by default: the recipe window covers that grid.", RECIPE_GHOST, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("recipe", "Fluid amounts in mB only", "Show fluid amounts in recipes, bookmarks and the crafting tree always in millibuckets (2500mB) instead of switching to buckets from 1000 on (2.5B). Off by default.", FLUID_MB_ONLY, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("recipe", "Single favorite counts for all its results", "If exactly one favorited recipe makes an ingredient, a recipe tree uses it even when it was favorited under another of its results (oil refinery recipes make several fluids). With several candidates nothing is guessed: Shift + F over the result in the recipe window picks one. NEI itself only uses the first result. Off by default.", FAVORITE_ANY_RESULT, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("recipe", "Choose favorite result with the mouse wheel", "NEI's way: while the mouse is on a recipe's heart, the wheel picks which result the heart favorites. Shift + F over a result in the recipe window does the same without scrolling.", FAVORITE_WHEEL, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("recipe", "Highlight present items", "Also mark the ingredients you have, not only the missing ones.", PRESENCE_HIGHLIGHT_PRESENT, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("craftables", "Favorite recipes only", "Only show recipes you marked as favorite (the heart).", CRAFTABLES_FAVORITES_ONLY, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("craftables", "Line colour", "Colour of the dashed line above the panel, as #AARRGGBB.", CRAFTABLES_COLOR, Kind.COLOR, 0, 0));
    OPTIONS.add(new Option("quantity", "Show item quantity field", "Show the quantity field under the item list: how many items a click on a list item gives.", QUANTITY_SHOW, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("quantity", "Item quantity", "How many items a click on a list item gives (0 = one stack). The field under the item list changes this too.", QUANTITY, Kind.INT, 0, 999999999));
    OPTIONS.add(new Option("itemzoom", "Enable item zoom", "Show the hovered item enlarged beside the window. Shift + the toggle key switches it.", ZOOM_ENABLED, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("itemzoom", "Only items of NEI panels", "Zoom only items of the item list, history and bookmark panels.", ZOOM_NEI_ONLY, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("itemzoom", "Only solid blocks", "Zoom only blocks that are solid.", ZOOM_ONLY_SOLID, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("itemzoom", "Preferred side (0 auto, 1 left, 2 right)", "Where to show the item when both sides are free: 0 = the side whose panel has fewer items, 1 = left, 2 = right.", ZOOM_SIDE, Kind.INT, 0, 2));
    OPTIONS.add(new Option("itemzoom", "Show help text", "Show the toggle key below the zoomed item.", ZOOM_HELP, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("itemzoom", "Zoom amount (0 - 1000)", "How much of the free side area the item fills, in thousandths. The zoom in / out keys change this too.", ZOOM_AMOUNT, Kind.INT, 0, 1000));
    OPTIONS.add(new Option("itemzoom", "Show item name", "Show the item's name below the zoomed item.", ZOOM_SHOW_NAME, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("itemzoom", "Name colour", "Colour of the item name, as #AARRGGBB.", ZOOM_NAME_COLOR, Kind.COLOR, 0, 0));
    OPTIONS.add(new Option("transfer", "Enable mouse wheel transfer", "Wheel over an item slot: wheel down moves one item out (as Shift + click would), wheel up pulls one in.", WHEEL_TRANSFER, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("transfer", "Invert wheel direction", "Swap the two wheel directions.", WHEEL_TRANSFER_INVERT, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("highlight", "Show highlight tooltip", "Show the name of the block you look at, with its item, in a box on the screen. A key (numpad 0) switches it.", HUD_ENABLED, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("highlight", "Position across (0 - 10000)", "0 = left edge, 10000 = right edge.", HUD_X, Kind.INT, 0, 10000));
    OPTIONS.add(new Option("highlight", "Position down (0 - 10000)", "0 = top edge, 10000 = bottom edge.", HUD_Y, Kind.INT, 0, 10000));
    OPTIONS.add(new Option(null, "Show hotkeys help text", "Item tooltips end with \"Hold ALT for hotkeys\".", HOTKEYS_HELP_TEXT, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option(null, "Show hotkeys", "Holding Alt over an item shows the list of its hotkeys.", SHOW_HOTKEYS, Kind.BOOL, 0, 0));
    layoutLikeNei();
    }


    /**
     * NEI's Search Format button: Old Format sets the space mode to Space, the mod name, tooltip, id and tag modes to Always
     * and the subset mode to Prefix (with {@code %} and {@code @} swapped); Extended sets AND, Prefix for the mod name, Always
     * for tooltips, ids and tags, Prefix for subsets.
     */
    static void applySearchFormat() {
        final boolean old = SEARCH_OLD_FORMAT.getAsBoolean();
        SEARCH_SPACE_MODE.set(old ? 1 : 0);
        SEARCH_MOD_MODE.set(old ? 0 : 1);
        SEARCH_TOOLTIP_MODE.set(0);
        SEARCH_ID_MODE.set(0);
        SEARCH_TAG_MODE.set(0);
        SEARCH_SUBSET_MODE.set(1);
        codechicken.nei.ItemList.refilter();
    }

    // ---- NEI's option tree ----

    private static Option original(List<Option> old, Cfg.ConfigValue<?> value) {
        for (Option o : old) if (o.value() == value) return o;
        return null;
    }

    /** Puts an option on a page with NEI's label and button texts ({@code on} / {@code off} for a yes-no option, or null). */
    private static void put(List<Option> old, String path, Cfg.ConfigValue<?> value, String label, String on, String off) {
        put(old, path, value, label, on, off, null);
    }

    private static void put(List<Option> old, String path, Cfg.ConfigValue<?> value, String label, String on, String off,
            Kind kind) {
        final Option o = original(old, value);
        if (o == null) return;
        old.remove(o);
        OPTIONS.add(new Option(path, label, o.tip(), value, kind != null ? kind : o.kind(), o.min(), o.max()));
        if (on != null) BOOL_TEXT.put(value, new String[] { on, off });
    }

    /** A row of an option that has no old entry: NEI's label, {@code on} / {@code off} for a yes-no button (or null). */
    private static void row(String path, String label, String tip, Cfg.ConfigValue<?> value, Kind kind, int min, int max,
            String on, String off) {
        OPTIONS.add(new Option(path, label, tip, value, kind, min, max));
        if (on != null) BOOL_TEXT.put(value, new String[] { on, off });
    }

    private static void bool(String path, String label, String tip, Cfg.BooleanValue value, String on, String off) {
        row(path, label, tip, value, Kind.BOOL, 0, 0, on, off);
    }

    private static void choice(String path, String label, String tip, Cfg.IntValue value, String... names) {
        CHOICES.put(value, names);
        row(path, label, tip, value, Kind.CHOICE, 0, names.length - 1, null, null);
    }

    private static void number(String path, String label, String tip, Cfg.IntValue value, int min, int max) {
        row(path, label, tip, value, Kind.INT, min, max, null, null);
    }

    private static void text(String path, String label, String tip, Cfg.StringValue value) {
        row(path, label, tip, value, Kind.TEXT, 0, 0, null, null);
    }

    private static void color(String path, String label, String tip, Cfg.StringValue value) {
        row(path, label, tip, value, Kind.COLOR, 0, 0, null, null);
    }

    /**
     * A row for an NEI option NEI Rewrite has no feature for: shown greyed out, with NEI's default as its text, so the
     * options screen can be compared with NEI's page by page. {@code text} is what the button (or field) says.
     */
    private static void stub(String path, String label, String text, String comment) {
        stub(path, label, text, comment, Kind.STUB, 0);
    }

    /** A greyed out text field; {@code max} is the biggest number it takes (0 for a text). */
    private static void stubField(String path, String label, String text, String comment, int max) {
        stub(path, label, text, comment, Kind.STUB_FIELD, max);
    }

    private static void stub(String path, String label, String text, String comment, Kind kind, int max) {
        final Option o = new Option(path, label, "Not implemented in NEI Rewrite yet. " + comment, null, kind, 0, max);
        OPTIONS.add(o);
        STUB_TEXT.put(o, text);
    }

    /**
     * Arranges the options as NEI's options screen does: Inventory, World, Commands and Tools on the first screen, the pages
     * inside them in the order NEI adds them, NEI's labels and button texts ({@code nei.options.*} of its lang file). An option
     * NEI has and NEI Rewrite has no feature for is a greyed out row ({@link #stub}). Options NEI does not have sit at the end
     * of the page they belong to. Left out on purpose: Item IDs, Grid Rendering Cache Mode / FPS, Optimize computation for GUI
     * overlap, Item Untranslator.
     */
    private static void layoutLikeNei() {
        final List<Option> old = new ArrayList<>(OPTIONS);
        OPTIONS.clear();
        SECTIONS.clear();
        SECTIONS.put("inventory", "Inventory");
        SECTIONS.put("inventory.itemsort", "Item Panel Sorting");
        SECTIONS.put("inventory.search", "Search Widget");
        SECTIONS.put("inventory.bookmarks", "Bookmarks Panel");
        SECTIONS.put("inventory.guirecipe", "Recipes GUI");
        SECTIONS.put("inventory.subsets", "Subsets Widget");
        SECTIONS.put("inventory.history", "History Panel");
        SECTIONS.put("inventory.craftables", "Craftables Panel");
        SECTIONS.put("inventory.collapsibleItems", "Collapsible Items");
        SECTIONS.put("inventory.itemzoom", "Item Zoom Panel");
        SECTIONS.put("inventory.favorites", "Favorites");
        SECTIONS.put("inventory.tooltip", "Tooltips");
        SECTIONS.put("world", "World");
        SECTIONS.put("world.highlight_tips", "Highlight Tooltips");
        SECTIONS.put("world.panels", "Panels Settings");
        SECTIONS.put("world.presets", "Presets List");
        SECTIONS.put("world.overlays", "Overlays Settings");
        SECTIONS.put("command", "Commands");
        SECTIONS.put("tools", "Tools");
        SECTIONS.put("tools.dump", "Data Dumps");

        final String inv = "inventory";
        put(old, inv, PANEL_ENABLED, "", "NEI Enabled", "NEI Disabled");
        old.remove(original(old, CHEAT_ENABLED));
        old.remove(original(old, TOOLBAR_IN_SURVIVAL));
        OPTIONS.add(new Option(inv, "", "Recipe Mode: a click on a list item opens its recipes. Utility Mode: the toolbar with Delete and Magnet shows, also in survival. Cheat Mode: a click gives the item and the whole toolbar shows (needs creative mode, cheats or operator permission). Ctrl + click on the wrench button cycles the modes.", CHEAT_ENABLED, Kind.MODE, 0, 0));
        SET_ITEMS.put(UTILITIES, new String[][] { { "time", "Time" }, { "rain", "Rain" }, { "heal", "Heal" },
                { "delete", "Delete" }, { "magnet", "Magnet" }, { "gamemode", "Gamemode" }, { "enchant", "Enchanting" },
                { "potion", "Potions" }, { "item", "Items" } });
        SET_ITEMS.put(GAMEMODES, new String[][] { { "creative", "Creative" }, { "creative+", "Creative+" },
                { "adventure", "Adventure" } });
        OPTIONS.add(new Option(inv, "Utilities", "Select features for utility mode", UTILITIES, Kind.SET, 0, 0));
        OPTIONS.add(new Option(inv, "Gamemodes", "Select the gamemodes to cycle through", GAMEMODES, Kind.SET, 0, 0));
        OPTIONS.add(new Option("inventory.itemsort", "", "", null, Kind.BOOL, 0, 0)); // the sorter page, after the modes

        final String search = "inventory.search";
        bool(search, "Widget Position", "Center: the search field at the bottom centre of the screen. Right: under the item list, left of the quantity field.", SEARCH_CENTERED, "Center", "Right");
        bool(search, "Hide Until Searching", "The item list stays empty until something is typed into the search field.", SEARCH_HIDE_UNTIL, "Yes", "No");
        AFTER_CHANGE.put(SEARCH_HIDE_UNTIL, () -> codechicken.nei.ItemList.refilter());
        choice(search, "Autofocus", "Focus the search field when the inventory opens. It lets go when the mouse moves, unless typing started first. Extended: the inventory key does not type (it closes the screen).", SEARCH_AUTOFOCUS, "No", "Yes", "Extended");
        put(old, search, SEARCH_PATTERN_MODE, "Search Mode", null, null);
        put(old, search, SEARCH_QUOTE_DROP, "Quote Drop Item Name", null, null);
        bool(search, "Search Format", "Old Format: a space is part of the text, % searches mod names, @ searches subsets and names are always matched against mod names too. Extended: the modes below. Changing it sets the modes below as NEI does.", SEARCH_OLD_FORMAT, "Old Format", "Extended");
        AFTER_CHANGE.put(SEARCH_OLD_FORMAT, NEIConfig::applySearchFormat);
        put(old, search, SEARCH_SPACE_MODE, "Space Works like", null, null);
        put(old, search, SEARCH_MOD_MODE, "Mod Name Search", null, null);
        put(old, search, SEARCH_TOOLTIP_MODE, "Tooltip Search", null, null);
        put(old, search, SEARCH_ID_MODE, "Resource ID Search", null, null);
        put(old, search, SEARCH_TAG_MODE, "Item Tag Search", null, null); // NEI: OreDict Search
        put(old, search, SEARCH_SUBSET_MODE, "Item Subsets Search", null, null);
        put(old, search, SYNC_TERMINAL_SEARCH, "Sync with AE2 Terminals", null, null);
        for (Cfg.ConfigValue<?> v : List.of(SEARCH_SPACE_MODE, SEARCH_MOD_MODE, SEARCH_TOOLTIP_MODE, SEARCH_ID_MODE,
                SEARCH_TAG_MODE, SEARCH_SUBSET_MODE)) {
            DISABLED_WHEN.put(v, SEARCH_OLD_FORMAT::getAsBoolean); // NEI: only with the extended format
        }

        final String bookmarks = "inventory.bookmarks";
        bool(bookmarks, "Bookmarks Panel Visibility", "Show or hide the bookmarks panel (the book button of the bottom bar switches it too).", BOOKMARKS_ENABLED, "Visible", "Hidden");
        put(old, bookmarks, BOOKMARKS_WORLD, "World Specific", null, null);
        bool(bookmarks, "REI Style Animation in Bookmarks", "A new bookmark pops up from the middle of its slot.", BOOKMARKS_ANIMATION, "Yes", "No");
        choice(bookmarks, "Show Recipe Tooltips in Bookmarks", "A bookmarked recipe's result shows the recipe in its tooltip. In Horizontal View: in normal groups. In Vertical View: in todo list groups. Always: in both.", BOOKMARK_RECIPE_TOOLTIPS, "None", "In Horizontal View", "In Vertical View", "Always");
        choice(bookmarks, "Show Recipe Marker", "How a bookmark that remembers its recipe is marked: Text puts an R in its corner, Background colours its slot.", BOOKMARK_MARKER, "None", "Text", "Background");
        number(bookmarks, "Max Vertical Tooltip Rows (0 - disable)", "Max rows in vertical crafting chain tooltip lists (0 - disable, always use grid).", BOOKMARK_VERTICAL_ROWS, 0, 50);
        color(bookmarks, "Recipe Marker Color", "Color of the icon marker (the R, and the xN of a bookmarked recipe).", BOOKMARK_MARKER_COLOR);
        bool(bookmarks, "Show Handler Icon", "A bookmarked recipe's result carries the small icon of its recipe type.", BOOKMARK_HANDLER_ICON, "Yes", "No");
        put(old, bookmarks, IGNORE_POTION_OVERLAP, "Ignore overlap with potion effect HUD", null, null);
        bool(bookmarks, "Bookmark items with recipe", "An item bookmarked from the recipe window remembers the recipe it was in.", BOOKMARK_ITEMS_WITH_RECIPE, "Yes", "No");
        bool(bookmarks, "Bookmark recipe with count", "Shift + the bookmark key over a recipe keeps its amounts, as Ctrl + Shift does.", BOOKMARK_RECIPE_WITH_COUNT, "Yes", "No");
        bool(bookmarks, "Tree Zoom without Ctrl", "Zoom the crafting tree with the mouse wheel without holding Ctrl.", TREE_ZOOM_NO_CTRL, "Yes", "No");

        final String recipe = "inventory.guirecipe";
        bool(recipe, "JEI Style Tabs", "Show the recipe type tabs above the recipe window.", RECIPE_JEI_TABS, "Yes", "No [Weirdo]");
        choice(recipe, "JEI Style Recipe Catalysts", "The machines that make the recipe type, in a column left of the window. None: no column. Grid: more columns when they do not fit. Scroll: one column with a scroll bar. Uses of a machine list the recipes it makes in every mode; hold Ctrl while looking up to leave them out.", RECIPE_CATALYSTS, "None", "Grid", "Scroll");
        bool(recipe, "Cycled Ingredients Tooltip", "The tooltip of an ingredient that cycles through alternatives lists them under \"Accepts following\" (favorite items first, marked F).", RECIPE_CYCLED_TOOLTIP, "Show", "Hide");
        bool(recipe, "Creative or JEI tab style", "Creative: the recipe type tabs look like the creative inventory's tabs. JEI: JEI style tabs.", RECIPE_CREATIVE_TABS, "Creative", "JEI");
        put(old, recipe, PRESENCE_OVERLAY, "Item Overlay on ?-Hover", null, null);
        put(old, recipe, PRESENCE_HIGHLIGHT_PRESENT, "Highlight Present Item", null, null);
        bool(recipe, "Use Infinite Scroll", "All recipes of a type in one list that the wheel and a scroll bar move, instead of pages.", RECIPE_INFINITE_SCROLL, "Yes", "No");
        number(recipe, "Max Height", "How tall the recipe window may get, in GUI pixels (166 - 4000).", RECIPE_MAX_HEIGHT, 166, 4000);
        bool(recipe, "Require holding shift to move items when overlaying recipe", "Yes: a click on a crafting recipe's + shows its items faded in the crafting grid, Shift + click moves them (Shift + Ctrl + click: one set). No: a click moves them (Ctrl + click: one set).", RECIPE_SHIFT_OVERLAY, "Yes", "No");
        bool(recipe, "Scroll Pages", "The wheel over the recipes turns the pages. Over the page row it always does; with Shift or over the title row it changes the recipe type.", RECIPE_SCROLL_PAGES, "Enabled", "Disabled");
        bool(recipe, "Stop cycling the recipe while hovering over an ingredient or a button", "A recipe stops cycling its ingredients while the mouse is on one of its items or buttons. Holding Shift always stops it.", RECIPE_FREEZE_CYCLE, "Yes", "No");
        bool(recipe, "Handler Info Widget", "Use shift+d while in the Recipes GUI: a box beside the window shows what is known about the recipe type.", RECIPE_HANDLER_INFO, "Enabled", "Disabled");
        put(old, recipe, RECIPE_GHOST, "Ghost Items on the + Button", null, null);
        put(old, recipe, FLUID_MB_ONLY, "Fluid Amounts in mB Only", null, null);

        put(old, "inventory.subsets", SUBSETS_ENABLED, "Subsets Visibility", "Enabled", "Disabled");
        put(old, "inventory.subsets", SUBSETS_ON_TOP, "Widget Position", "Top", "Bottom");

        final String history = "inventory.history";
        put(old, history, HISTORY_ENABLED, "History Panel Visibility", "Enabled", "Disabled");
        put(old, history, HISTORY_COLOR, "Color", null, null);
        put(old, history, HISTORY_ROWS, "Rows", null, null);
        put(old, history, HISTORY_WORLD, "World Specific", null, null);
        put(old, history, HISTORY_SAVE, "History Persistence", "Enabled", "Disabled");

        final String craft = "inventory.craftables";
        put(old, craft, CRAFTABLES_ENABLED, "Craftables Panel Visibility", "Enabled", "Disabled");
        put(old, craft, CRAFTABLES_COLOR, "Color", null, null);
        put(old, craft, CRAFTABLES_ROWS, "Rows", null, null);
        put(old, craft, CRAFTABLES_FAVORITES_ONLY, "Show Favorites Only", null, null);

        final String collapse = "inventory.collapsibleItems";
        put(old, collapse, COLLAPSIBLE_ENABLED, "Collapsible Items", "Enabled", "Disabled");
        put(old, collapse, COLLAPSIBLE_CUSTOM_NAME, "Show Custom Group Name", null, null);
        put(old, collapse, COLLAPSIBLE_EXPANDED_COLOR, "Expanded Color", null, null);
        put(old, collapse, COLLAPSIBLE_COLLAPSED_COLOR, "Collapsed Color", null, null);

        final String zoom = "inventory.itemzoom";
        put(old, zoom, ZOOM_ENABLED, "Item Zoom Panel Visibility", "Enabled", "Disabled");
        put(old, zoom, ZOOM_NEI_ONLY, "NEI Overlays Only", "Enabled", "Disabled");
        put(old, zoom, ZOOM_ONLY_SOLID, "Show Only Solid Items", "Enabled", "Disabled");
        CHOICES.put(ZOOM_SIDE, new String[] { "Auto", "Left", "Right" });
        put(old, zoom, ZOOM_SIDE, "Preferred Side", null, null, Kind.CHOICE);
        put(old, zoom, ZOOM_HELP, "Display Help Text", "Enabled", "Disabled");
        put(old, zoom, ZOOM_AMOUNT, "Zoom Amount", null, null);
        put(old, zoom, ZOOM_SHOW_NAME, "Display Name", "Show", "Hide");
        put(old, zoom, ZOOM_NAME_COLOR, "Display Name Color", null, null);

        // NEI adds the favorites page after the item zoom page
        final String fav = "inventory.favorites";
        bool(fav, "Visibility", "Show or hide the favorites: the heart buttons of the recipe window, and the recipe trees leave favorites out while hidden.", FAVORITES_ENABLED, "Visible", "Hidden");
        put(old, fav, FAVORITES_WORLD, "World Specific", null, null);
        bool(fav, "Show Recipe Tooltips in Right Panel", "An item of the item list with a favorite recipe shows that recipe in its tooltip.", FAVORITE_TOOLTIP_PANEL, "Yes", "No");
        bool(fav, "Show Recipe Tooltips in Recipe Gui", "In the recipe window, a machine with a favorite recipe shows that recipe in its tooltip.", FAVORITE_TOOLTIP_GUI, "Yes", "No");
        bool(fav, "Generate Single-Recipe Favorites", "Automatically use the one recipe of an item that only one recipe makes as its favorite (recipe trees use it when you favorited none).", FAVORITE_GENERATE, "Yes", "No");
        number(fav, "Depth", "Bookmark creation depth: how many levels of sub-recipes a recipe tree made from a favorite goes down.", FAVORITE_DEPTH, 0, 100);
        put(old, fav, FAVORITE_ANY_RESULT, "Single Favorite Counts for All Results", null, null);
        put(old, fav, FAVORITE_WHEEL, "Choose Result with Mouse Wheel", null, null);

        final String tooltip = "inventory.tooltip";
        bool(tooltip, "Show Fluid Temperature", "A vanilla fluid's tooltip (water, lava) shows its temperature in kelvin. Modded fluids show what their mod says instead.", FLUID_TEMPERATURE, "Yes", "No");
        bool(tooltip, "Show Fluid State", "A vanilla fluid's tooltip (water, lava) shows whether it is a liquid or a gas. Modded fluids show what their mod says instead.", FLUID_STATE, "Yes", "No");

        // then the plain options, in NEI's order. NEI's wheel options read "Disable ..." (False = on); ours is the positive
        // form of the same key, so the texts are swapped and the screen reads like NEI's
        number(inv, "Item Panel Scale (in percent)", "How big the slots of the item, bookmark, history and craftables panels are (100 = NEI's 18 px).", ITEM_PANEL_SCALE, 1, 1000);
        put(old, inv, WHEEL_TRANSFER, "Disable Mouse Wheel Transfer", "False", "True");
        put(old, inv, WHEEL_TRANSFER_INVERT, "Invert Mouse Wheel Transfer Direction", "True", "False");
        put(old, inv, HOTKEYS_HELP_TEXT, "Show Hotkeys Help Text", null, null);
        put(old, inv, SHOW_HOTKEYS, "Show Hotkeys", null, null);
        put(old, inv, QUANTITY_SHOW, "Show Item Quantity Widget", null, null);
        old.remove(original(old, QUANTITY)); // NEI has no such row: the field under the item list sets it
        put(old, inv, AUTOCRAFT, "Autocrafting", "Enabled", "Disabled");
        put(old, inv, UI_STYLE, "UI Style", null, null);
        bool(inv, "Release Notice", "After joining a world, a chat message asks to report issues of this release on GitHub.", RELEASE_NOTICE, "Shown", "Hidden");

        // the world page: Highlight Tooltips opens its own screen (drag the sample), Presets List opens the presets window
        OPTIONS.add(new Option("world.highlight_tips", "", "", null, Kind.BOOL, 0, 0));
        OPTIONS.add(new Option("world.panels", "", "", null, Kind.BOOL, 0, 0)); // opens NEI's GuiPanelSettings
        OPTIONS.add(new Option("world.presets", "", "", null, Kind.BOOL, 0, 0));
        bool("world.overlays", "Lock overlays between dimensions", "The mob spawn overlay stays on when you change dimension or world.", OVERLAYS_LOCK, "Enabled", "Disabled");
        put(old, "world", MOB_RANGE, "Mob Spawn Overlay Range", null, null);
        old.remove(original(old, HUD_ENABLED));
        old.remove(original(old, HUD_X));
        old.remove(original(old, HUD_Y));

        // NEI's Commands and Tools pages (a server's command, as NEI would send it, and 1.7.10 handler files)
        final String cmd = "command";
        text(cmd, "Gamemode", "{0} = survival, creative or adventure, {1} = player", COMMAND_GAMEMODE);
        text(cmd, "Item", "{0} = player, {1} = id, {2} = amount, {3} = damage, {4} = components, {5} = name", COMMAND_ITEM);
        text(cmd, "Time", "{0} = worldtime", COMMAND_TIME);
        text(cmd, "Rain", "{0} = 1 for on, 0 for off, {1} = rain or clear", COMMAND_RAIN);
        text(cmd, "Heal", "{0} = player. Several commands can be separated by ;", COMMAND_HEAL);
        stub("tools", "Load handlers from Config", "JAR", "ADVANCED: load the recipe handlers from NEI's config files.");
        stub("tools", "Load catalysts from Config", "JAR", "ADVANCED: load the catalysts from NEI's config files.");
        final String dump = "tools.dump";
        for (String name : new String[] { "Item", "Block", "Potion", "Enchantment", "Biome", "Item Panel", "NEI Handlers" }) {
            final Option o = new Option(dump, name.equals("Item Panel") || name.equals("NEI Handlers") ? name : name + "s",
                    "Writes the list to dumps/" + dumpFile(name, 0) + " in the game folder.", null, Kind.ACTION, 0, 0);
            OPTIONS.add(o);
            STUB_TEXT.put(o, "Dump");
            final Cfg.IntValue mode = name.equals("Item Panel") ? DUMP_ITEMPANEL_MODE : name.equals("NEI Handlers") ? DUMP_HANDLERS_MODE : null;
            if (mode != null) { // NEI's mode button (the ID dumps' Used / Free / All has no meaning without numeric ids)
                ACTION_MODES.put(o, mode);
                ACTION_MODE_NAMES.put(o, mode == DUMP_ITEMPANEL_MODE ? new String[] { "CSV", "NBT", "Json" } : new String[] { "CSV", "Json" });
            }
            ACTIONS.put(o, () -> {
                final int m = mode == null ? 0 : mode.getAsInt();
                codechicken.nei.client.Dumps.dump(name, dumpFile(name, m), m);
            });
        }

        // on the first screen, after Tools: the issue tracker
        final Option report = new Option(null, "Report Issues", "Opens NEI Rewrite's issue tracker on GitHub in your browser. Please add your latest.log.",
                null, Kind.ACTION, 0, 0);
        OPTIONS.add(report);
        STUB_TEXT.put(report, "Open GitHub");
        ACTIONS.put(report, () -> codechicken.nei.client.ReleaseNotice.openIssues()); // a lambda: the client class loads only when clicked

        for (Option o : old) { // an option added later and not placed above still shows up
            OPTIONS.add(new Option(inv, o.label(), o.tip(), o.value(), o.kind(), o.min(), o.max()));
        }
    }

    /** NEI's dump file names: the option's last name part plus .csv (item.csv, itempanel.csv, handlers.csv ...). */
    private static String dumpFile(String name, int mode) {
        return switch (name) {
            case "Item Panel" -> "itempanel" + (mode == 1 ? ".nbt" : mode == 2 ? ".json" : ".csv");
            case "NEI Handlers" -> "handlers" + (mode == 1 ? ".json" : ".csv");
            default -> name.toLowerCase() + ".csv";
        };
    }

    public static final Cfg.Spec SPEC = BUILDER.build();

    private static long dirtyAt;

    private NEIConfig() {}

    /**
     * NEI's {@code inventory.cheatmode}: 0 Recipe Mode (a click opens recipes), 1 Utility Mode (the toolbar with Delete and
     * Magnet), 2 Cheat Mode (a click gives the item, the whole toolbar). It is kept in the two settings that were there
     * before, so older config files still work: cheat = mode 2, toolbar in survival without cheat = mode 1.
     */
    public static int cheatMode() {
        return CHEAT_ENABLED.getAsBoolean() ? 2 : TOOLBAR_IN_SURVIVAL.getAsBoolean() ? 1 : 0;
    }

    public static void setCheatMode(int mode) {
        final int m = ((mode % 3) + 3) % 3;
        CHEAT_ENABLED.set(m == 2);
        TOOLBAR_IN_SURVIVAL.set(m >= 1);
        changed();
    }

    /** The entries of a comma separated setting, trimmed. */
    public static List<String> entries(Cfg.StringValue value) {
        final List<String> list = new ArrayList<>();
        for (String part : value.get().split(",")) {
            if (!part.isBlank()) list.add(part.trim());
        }
        return list;
    }

    /** NEI's {@code canPerformAction} for the toolbar features: Cheat Mode all of them, Utility Mode the chosen utilities. */
    public static boolean canPerform(String utility) {
        final int mode = cheatMode();
        return mode == 2 || mode == 1 && entries(UTILITIES).contains(utility);
    }

    /** A colour option as an ARGB int; a damaged value gives opaque white. */
    public static int color(Cfg.StringValue option) {
        final String s = option.get();
        if (s == null || !COLOR.matcher(s).matches()) return 0xFFFFFFFF;
        return (int) Long.parseLong(s.substring(1), 16);
    }

    /** Settings the keyboard and wheel change fast (zoom, quantity) are written to the file a moment after the last change. */
    public static void changed() {
        dirtyAt = System.currentTimeMillis();
    }

    /** Call every frame: writes the file once changes have stopped for half a second. */
    public static void flush() {
        if (dirtyAt != 0 && System.currentTimeMillis() - dirtyAt > 500) {
            dirtyAt = 0;
            try {
                SPEC.save();
            } catch (RuntimeException e) {
                NotEnoughItems.LOGGER.warn("Could not save the config: {}", e.toString());
            }
        }
    }
}
