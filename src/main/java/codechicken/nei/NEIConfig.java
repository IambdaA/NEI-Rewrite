package codechicken.nei;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client config, shown by the wrench button as NeoForge's settings screen with one sub screen per NEI options group.
 * Colours are written as {@code #AARRGGBB}. Generated layout: keep names in step with {@code lang/en_us.json}.
 */
public final class NEIConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final Pattern COLOR = Pattern.compile("#[0-9A-Fa-f]{8}");

    public enum Kind {
        BOOL, INT, COLOR,
        /** A whole number 0 .. n - 1 shown as a button that cycles through named choices ({@link #CHOICES}). */
        CHOICE
    }

    /** The names of the choices of a {@link Kind#CHOICE} option, by its value. */
    public static final Map<ModConfigSpec.ConfigValue<?>, String[]> CHOICES = new java.util.HashMap<>();

    /** One option as the settings window shows it; {@code section} is null for the first screen. */
    public record Option(String section, String label, String tip, ModConfigSpec.ConfigValue<?> value, Kind kind, int min,
            int max) {}

    /** Section key to its name, in screen order. */
    public static final Map<String, String> SECTIONS = new LinkedHashMap<>();
    public static final List<Option> OPTIONS = new ArrayList<>();

    public static final ModConfigSpec.BooleanValue PANEL_ENABLED = BUILDER
            .comment("Show the item panel, bookmarks and the other NEI panels next to container screens.")
            .define("panelEnabled", true);

    public static final ModConfigSpec.BooleanValue CHEAT_ENABLED = BUILDER
            .comment("A plain click on a list item gives it and Shift + click opens recipes (as NEI in cheat mode). Needs creative mode, cheats or operator permission.")
            .define("cheatEnabled", true);

    public static final ModConfigSpec.BooleanValue TOOLBAR_IN_SURVIVAL = BUILDER
            .comment("Show the toolbar (time, weather, heal ...) in survival mode too. It is always shown in creative mode. Needs cheats or operator permission.")
            .define("toolbarInSurvival", false);

    public static final ModConfigSpec.BooleanValue AUTOCRAFT = BUILDER
            .comment("Autocrafting: with a crafting grid open, Shift + the craft key over a bookmarked recipe, crafting chain group or a recipe of the recipe window crafts it.")
            .define("autocrafting", true);

    /** NEI's "creative+" mode: the creative inventory key opens the extended inventory. Set by the gamemode button. */
    /** NEI's magnet mode (toolbar button): dropped items drift to you. Needs NEI Rewrite on the server. */
    public static final ModConfigSpec.BooleanValue MAGNET = BUILDER
            .comment("Magnet mode (set by the toolbar button): dropped items near you drift to you. Needs NEI Rewrite on the server.")
            .define("magnet", false);

    public static final ModConfigSpec.BooleanValue CREATIVE_PLUS = BUILDER
            .comment("Creative+ mode (set by the gamemode button): in creative mode the inventory key opens the extended creative inventory. Needs NEI Rewrite on the server.")
            .define("creativePlus", false);

    public static final ModConfigSpec.ConfigValue<String> ITEM_SORT = BUILDER
            .comment("The order the item list is sorted in: comma separated keys (minecraft, mod, id, default, name), each deciding when the ones before it tie. Changed in the Item Panel Sorting settings.")
            .define("itemSort", codechicken.nei.item.SortOrder.DEFAULT);

    // ---- Collapsible Items ----
    public static final ModConfigSpec.BooleanValue BOOKMARKS_WORLD = BUILDER
            .comment("Keep separate bookmarks for each world / server (a new world starts with the global bookmarks).")
            .define("bookmarksWorldSpecific", false);

    public static final ModConfigSpec.BooleanValue FAVORITES_WORLD = BUILDER
            .comment("Keep separate favorite recipes for each world / server (a new world starts with the global ones).")
            .define("favoritesWorldSpecific", false);

    static {
        BUILDER.comment("Groups similar items (spawn eggs, music discs ...) into one slot of the item list. Alt + click a group opens it; the G button opens all.").push("collapsible");
    }

    public static final ModConfigSpec.BooleanValue COLLAPSIBLE_ENABLED = BUILDER
            .comment("Group similar items into one slot of the item list.")
            .define("collapsibleItems", true);

    public static final ModConfigSpec.BooleanValue COLLAPSIBLE_CUSTOM_NAME = BUILDER
            .comment("Show the group's own name instead of the first item's name while a group is collapsed.")
            .define("collapsibleItemsCustomName", true);

    public static final ModConfigSpec.ConfigValue<String> COLLAPSIBLE_EXPANDED_COLOR = BUILDER
            .comment("Background colour of an open group, as #AARRGGBB (alpha, red, green, blue in hex).")
            .define("collapsibleExpandedColor", "#335555EE", o -> o instanceof String s && COLOR.matcher(s).matches());

    public static final ModConfigSpec.ConfigValue<String> COLLAPSIBLE_COLLAPSED_COLOR = BUILDER
            .comment("Background colour of a closed group, as #AARRGGBB (alpha, red, green, blue in hex).")
            .define("collapsibleCollapsedColor", "#335555EE", o -> o instanceof String s && COLOR.matcher(s).matches());

    static {
        BUILDER.pop();
    }

    // ---- Craftables Panel ----
    static {
        BUILDER.comment("What you can craft from your inventory, shown above the history panel.").push("craftables");
    }

    public static final ModConfigSpec.BooleanValue CRAFTABLES_ENABLED = BUILDER
            .comment("Show what you can craft from your inventory (crafting table and inventory grid recipes).")
            .define("craftablesPanel", false);

    /** 0 Off, 1 Icon, 2 Highlight. NEI's {@code inventory.guirecipe.itemPresenceOverlay} (NEI's default is 1). */
    public static final ModConfigSpec.IntValue PRESENCE_OVERLAY = BUILDER
            .comment("Ingredients on the \"+\" button's recipe while the mouse is on it: 0 Off, 1 Icon (a cross / tick in the corner of the slot), 2 Highlight (red / green slot). NEI's own default is Icon.")
            .defineInRange("itemPresenceOverlay", 2, 0, 2);

    public static final ModConfigSpec.BooleanValue RECIPE_GHOST = BUILDER
            .comment("While the mouse is on a crafting recipe's \"+\", draw the ingredients faded over the open screen's crafting grid. The recipe window covers that grid, so this is off by default.")
            .define("recipeGhostItems", false);

    public static final ModConfigSpec.BooleanValue PRESENCE_HIGHLIGHT_PRESENT = BUILDER
            .comment("Also mark the ingredients you have (green / tick), not only the missing ones.")
            .define("slotHighlightPresent", true);

    public static final ModConfigSpec.IntValue MOB_RANGE = BUILDER
            .comment("How far around you the mob spawn overlay (F7) reaches, in blocks. NEI uses 16.")
            .defineInRange("mobOverlayRange", 16, 8, 32);

    public static final ModConfigSpec.IntValue CRAFTABLES_ROWS = BUILDER
            .comment("How many rows the craftables panel may use.")
            .defineInRange("craftablesRows", 2, 1, 5);

    public static final ModConfigSpec.BooleanValue CRAFTABLES_FAVORITES_ONLY = BUILDER
            .comment("Only show recipes you marked as favorite (the heart).")
            .define("craftablesFavoritesOnly", false);

    public static final ModConfigSpec.ConfigValue<String> CRAFTABLES_COLOR = BUILDER
            .comment("Colour of the dashed line above the panel, as #AARRGGBB.")
            .define("craftablesColor", "#EE555555", o -> o instanceof String s && COLOR.matcher(s).matches());

    static {
        BUILDER.pop();
    }

    // ---- History Panel ----
    static {
        BUILDER.comment("The items whose recipes or uses you looked up, under the item list.").push("history");
    }

    public static final ModConfigSpec.BooleanValue HISTORY_ENABLED = BUILDER
            .comment("Show the history panel under the item list.")
            .define("historyEnabled", true);

    public static final ModConfigSpec.ConfigValue<String> HISTORY_COLOR = BUILDER
            .comment("Colour of the dashed line above the panel, as #AARRGGBB.")
            .define("historyColor", "#EE555555", o -> o instanceof String s && COLOR.matcher(s).matches());

    public static final ModConfigSpec.IntValue HISTORY_ROWS = BUILDER
            .comment("How many rows the history panel may use.")
            .defineInRange("historyRows", 2, 1, 5);

    public static final ModConfigSpec.BooleanValue HISTORY_SAVE = BUILDER
            .comment("Keep the history between sessions (see the world specific option).")
            .define("historySave", false);

    public static final ModConfigSpec.BooleanValue HISTORY_WORLD = BUILDER
            .comment("Keep a separate saved history for each world / server.")
            .define("historyWorldSpecific", true);

    static {
        BUILDER.pop();
    }

    // ---- Subsets Widget ----
    static {
        BUILDER.comment("The Subsets dropdown above the inventory.").push("subsets");
    }

    public static final ModConfigSpec.BooleanValue SUBSETS_ENABLED = BUILDER
            .comment("Show the Subsets dropdown above the inventory.")
            .define("subsetsEnabled", true);

    public static final ModConfigSpec.BooleanValue SUBSETS_ON_TOP = BUILDER
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
    public static final ModConfigSpec.IntValue SEARCH_PATTERN_MODE = BUILDER
            .comment("Search mode: 0 Plain, 1 Extended (? one character, * anything, r/regex/), 2 Regex, 3 Extended+ (expressions with | - { } quotes and /regex/).")
            .defineInRange("patternMode", 2, 0, 3);

    /** 0 AND, 1 Space, 2 AND-X. */
    public static final ModConfigSpec.IntValue SEARCH_SPACE_MODE = BUILDER
            .comment("Space works like: 0 AND (every word must match), 1 Space (a space is part of the text), 2 AND-X (every word must match within the same kind of text).")
            .defineInRange("spaceMode", 0, 0, 2);

    public static final ModConfigSpec.IntValue SEARCH_MOD_MODE = BUILDER
            .comment("Search mode for mod names (prefix @): 0 Always, 1 Prefix, 2 Never.")
            .defineInRange("modNameSearchMode", 1, 0, 2);

    public static final ModConfigSpec.IntValue SEARCH_TOOLTIP_MODE = BUILDER
            .comment("Search mode for tooltips (prefix #): 0 Always, 1 Prefix, 2 Never.")
            .defineInRange("tooltipSearchMode", 1, 0, 2);

    public static final ModConfigSpec.IntValue SEARCH_ID_MODE = BUILDER
            .comment("Search mode for resource ids (prefix &): 0 Always, 1 Prefix, 2 Never.")
            .defineInRange("identifierSearchMode", 1, 0, 2);

    public static final ModConfigSpec.IntValue SEARCH_TAG_MODE = BUILDER
            .comment("Search mode for item tags (prefix $): 0 Always, 1 Prefix, 2 Never.")
            .defineInRange("tagSearchMode", 1, 0, 2);

    public static final ModConfigSpec.IntValue SEARCH_SUBSET_MODE = BUILDER
            .comment("Search mode for item subsets (prefix %): 0 Always, 1 Prefix, 2 Never. With Prefix, Shift + click in the Subsets menu searches.")
            .defineInRange("subsetsSearchMode", 1, 0, 2);

    public static final ModConfigSpec.BooleanValue SEARCH_QUOTE_DROP = BUILDER
            .comment("Put quotes around an item name with spaces when it is sent to the search (Shift + click on an item of the Subsets menu).")
            .define("quoteDropItemName", true);

    public static final ModConfigSpec.BooleanValue SYNC_TERMINAL_SEARCH = BUILDER
            .comment("Keep the item search and the search field of an Applied Energistics terminal the same (AE2's \"sync with JEI\", done with NEI's search).")
            .define("syncTerminalSearch", true);

    static {
        BUILDER.pop();
    }

    // ---- Item Quantity Widget ----
    static {
        BUILDER.comment("The - / + field under the item list.").push("quantity");
    }

    public static final ModConfigSpec.BooleanValue QUANTITY_SHOW = BUILDER
            .comment("Show the quantity field under the item list: how many items a click on a list item gives.")
            .define("showItemQuantity", true);

    public static final ModConfigSpec.IntValue QUANTITY = BUILDER
            .comment("How many items a click on a list item gives (0 = one stack). The field under the item list changes this too.")
            .defineInRange("itemQuantity", 0, 0, 999999999);

    static {
        BUILDER.pop();
    }

    // ---- Item Zoom Panel ----
    static {
        BUILDER.comment("Shows the hovered item enlarged beside the window.").push("itemzoom");
    }

    public static final ModConfigSpec.BooleanValue ZOOM_ENABLED = BUILDER
            .comment("Show the hovered item enlarged beside the window. Shift + the toggle key switches it.")
            .define("itemZoomEnabled", true);

    public static final ModConfigSpec.BooleanValue ZOOM_NEI_ONLY = BUILDER
            .comment("Zoom only items of the item list, history and bookmark panels.")
            .define("itemZoomNeiOnly", false);

    public static final ModConfigSpec.BooleanValue ZOOM_ONLY_SOLID = BUILDER
            .comment("Zoom only blocks that are solid.")
            .define("itemZoomOnlySolid", false);

    public static final ModConfigSpec.IntValue ZOOM_SIDE = BUILDER
            .comment("Where to show the item when both sides are free: 0 = the side whose panel has fewer items, 1 = left, 2 = right.")
            .defineInRange("itemZoomPreferredSide", 0, 0, 2);

    public static final ModConfigSpec.BooleanValue ZOOM_HELP = BUILDER
            .comment("Show the toggle key below the zoomed item.")
            .define("itemZoomHelpText", true);

    public static final ModConfigSpec.IntValue ZOOM_AMOUNT = BUILDER
            .comment("How much of the free side area the item fills, in thousandths. The zoom in / out keys change this too.")
            .defineInRange("itemZoomAmount", 500, 0, 1000);

    public static final ModConfigSpec.BooleanValue ZOOM_SHOW_NAME = BUILDER
            .comment("Show the item's name below the zoomed item.")
            .define("itemZoomShowName", true);

    public static final ModConfigSpec.ConfigValue<String> ZOOM_NAME_COLOR = BUILDER
            .comment("Colour of the item name, as #AARRGGBB.")
            .define("itemZoomNameColor", "#FFFFFFFF", o -> o instanceof String s && COLOR.matcher(s).matches());

    static {
        BUILDER.pop();
    }

    // ---- Mouse Wheel Transfer ----
    static {
        BUILDER.comment("The wheel over a slot of a container moves single items.").push("transfer");
    }

    public static final ModConfigSpec.BooleanValue WHEEL_TRANSFER = BUILDER
            .comment("Wheel over an item slot: wheel down moves one item out (as Shift + click would), wheel up pulls one in.")
            .define("mouseWheelTransfer", true);

    public static final ModConfigSpec.BooleanValue WHEEL_TRANSFER_INVERT = BUILDER
            .comment("Swap the two wheel directions.")
            .define("mouseWheelTransferInvert", false);

    static {
        BUILDER.pop();
    }

    // ---- Highlight Tooltips ----
    static {
        BUILDER.comment("A box with the name of the block you look at.").push("highlight");
    }

    public static final ModConfigSpec.BooleanValue HUD_ENABLED = BUILDER
            .comment("Show the name of the block you look at, with its item, in a box on the screen. A key (numpad 0) switches it.")
            .define("highlightTips", false);

    public static final ModConfigSpec.IntValue HUD_X = BUILDER
            .comment("0 = left edge, 10000 = right edge.")
            .defineInRange("highlightTipsX", 5000, 0, 10000);

    public static final ModConfigSpec.IntValue HUD_Y = BUILDER
            .comment("0 = top edge, 10000 = bottom edge.")
            .defineInRange("highlightTipsY", 100, 0, 10000);

    static {
        BUILDER.pop();
    }

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
    OPTIONS.add(new Option("history", "Save history", "Keep the history between sessions (see the world specific option).", HISTORY_SAVE, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("history", "World specific history", "Keep a separate saved history for each world / server.", HISTORY_WORLD, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("subsets", "Show the Subsets dropdown", "Show the Subsets dropdown above the inventory.", SUBSETS_ENABLED, Kind.BOOL, 0, 0));
    OPTIONS.add(new Option("subsets", "Widget on top", "On: the Subsets bar at the top centre. Off: a button left of the search field.", SUBSETS_ON_TOP, Kind.BOOL, 0, 0));
    CHOICES.put(PRESENCE_OVERLAY, new String[] { "Off", "Icon", "Highlight" });
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
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private static long dirtyAt;

    private NEIConfig() {}

    /** A colour option as an ARGB int; a damaged value gives opaque white. */
    public static int color(ModConfigSpec.ConfigValue<String> option) {
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
