package codechicken.nei.client;

import java.util.ArrayList;
import java.util.List;

import codechicken.nei.NEIConfig;
import codechicken.nei.NotEnoughItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * NEI's toolbar (LayoutStyleMinecraft): small buttons at the top left, 18 x 17 with 2 pixels between, wrapping to the
 * width left of the window. Icons are NEI's own ({@code nei_sprites.png}). Like NEI without its server mod, everything
 * but delete goes through vanilla commands, so the server's permissions decide.
 *
 * Delete mode, "delete all" and the magnet need NEI Rewrite on the server (as NEI needs its server mod): see {@link Deleting}, {@link Magnet}.
 * Shown in creative mode, or in survival when the "toolbarInSurvival" option is on (both need the cheat setting).
 * Hidden when the player may not run operator commands (see {@link Perms}).
 */
final class Toolbar {

    private static final int W = 18;
    private static final int H = 17;
    private static final int MARGIN = 2;
    private static final int COUNT = 9;

    /** The buttons in order and the utility each one belongs to (NEI's {@code inventory.utilities} names). */
    private static final String[] NAMES = { "delete", "rain", "gamemode", "magnet", "time", "time", "time", "time", "heal" };

    private record B(String name, int u, int v, boolean active, List<String> tips, Runnable action) {}

    private Toolbar() {}

    static boolean visible() {
        final var player = Minecraft.getInstance().player;
        if (player == null) return false;
        return switch (NEIConfig.cheatMode()) {
            case 2 -> (player.isCreative() || NEIConfig.TOOLBAR_IN_SURVIVAL.getAsBoolean()) && Perms.canUseCommands();
            case 1 -> count() > 0; // Utility Mode: the chosen utilities (default Delete and Magnet), no permission check
            default -> false;
        };
    }

    /** How many buttons are shown: all in Cheat Mode, the chosen utilities in Utility Mode (NEI's canPerformAction). */
    private static int count() {
        int n = 0;
        for (String name : NAMES) if (NEIConfig.canPerform(name)) n++;
        return n;
    }

    private static int perRow(int screenWidth, int xSize) {
        return Math.max(((screenWidth - xSize) / 2 - 4) / 20, 1);
    }

    private static String playerName() {
        final var player = Minecraft.getInstance().player;
        return player == null ? "@s" : player.getName().getString();
    }

    /** NEI's {@code canPerformAction} without a server mod: a button whose command template does not start with / is off. */
    private static boolean commandOn(String name) {
        return switch (name) {
            case "rain" -> NEIConfig.commandSet(NEIConfig.COMMAND_RAIN);
            case "gamemode" -> NEIConfig.commandSet(NEIConfig.COMMAND_GAMEMODE);
            case "time" -> NEIConfig.commandSet(NEIConfig.COMMAND_TIME);
            case "heal" -> NEIConfig.commandSet(NEIConfig.COMMAND_HEAL);
            default -> true;
        };
    }

    private static List<B> buttons(AbstractContainerScreen<?> screen) {
        final var mc = Minecraft.getInstance();
        final var player = mc.player;
        final boolean raining = mc.level != null && mc.level.isRaining();
        final boolean creative = player.isCreative();
        final List<B> list = new ArrayList<>();

        final ItemStack carried = screen.getMenu().getCarried();
        final boolean shift = HotkeyHelp.isShiftDown();
        final List<String> deleteTips = new ArrayList<>();
        if (shift) deleteTips.add("DELETE ALL ITEMS from current inventory screen");
        else if (Deleting.canUseMode(screen)) deleteTips.add(Deleting.modeOn(screen) ? "Turn delete mode off" : "Turn delete mode on");
        if (!carried.isEmpty()) deleteTips.add((shift ? "Delete ALL " : "Delete ") + carried.getHoverName().getString());
        list.add(new B("delete", 144, 12, Deleting.modeOn(screen), deleteTips, () -> Deleting.button(screen, false)));
        list.add(new B("rain", 120, 12, raining, List.of(raining ? "Turn rain off" : "Turn rain on"), Toolbar::rain));
        final int cur = currentMode();
        list.add(new B("gamemode", 132, 12, creative, List.of("Current gamemode: " + MODES[cur],
                "Click: Set gamemode to " + MODES[nextMode(cur)]), Toolbar::gamemode));

        final boolean magnet = Magnet.on();
        list.add(new B("magnet", 180, magnet ? 36 : 24, magnet, List.of(magnet ? "Turn magnet mode off" : "Turn magnet mode on"), Magnet::toggle));

        // NEI's time buttons: dawn, noon, dusk, midnight (hours 0, 6, 12, 18 of the day)
        final int[][] icons = { { 132, 24 }, { 120, 24 }, { 144, 24 }, { 156, 24 } };
        final String[] zones = { "dawn", "noon", "dusk", "midnight" };
        for (int i = 0; i < 4; i++) {
            final int zone = i;
            list.add(new B("time", icons[i][0], icons[i][1], false, List.of("Set time to " + zones[i]), () -> time(zone)));
        }

        list.add(new B("heal", 168, 24, false, List.of("Heal the player"), Toolbar::heal));
        list.removeIf(b -> !NEIConfig.canPerform(b.name()) || !commandOn(b.name())); // Utility Mode: only the chosen utilities
        return list;
    }

    // The actions of the buttons, also run by the world.* keys.

    private static void rain() {
        final var level = Minecraft.getInstance().level;
        final boolean raining = level != null && level.isRaining();
        NEIConfig.runCommand(NEIConfig.COMMAND_RAIN, raining ? 0 : 1, raining ? "clear" : "rain"); // NEI's command.rain
    }

    private static final String[] MODES = { "survival", "creative", "creative+", "adventure" };

    /** NEI's {@code getGamemode}: 0 survival, 1 creative, 2 creative+, 3 adventure. */
    private static int currentMode() {
        final var mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isCreative()) return CreativePlus.active() ? 2 : 1;
        return mc.gameMode != null && mc.gameMode.getPlayerMode() == net.minecraft.world.level.GameType.ADVENTURE ? 3 : 0;
    }

    /** NEI's {@code isValidGamemode}: survival always, the others when the Gamemodes setting has them. */
    private static boolean validMode(int mode) {
        final List<String> on = NEIConfig.entries(NEIConfig.GAMEMODES);
        return switch (mode) {
            case 0 -> true;
            case 1 -> on.contains("creative");
            case 2 -> on.contains("creative+") && CreativePlus.available();
            default -> on.contains("adventure");
        };
    }

    private static int nextMode(int current) {
        for (int i = 1; i <= MODES.length; i++) {
            final int next = (current + i) % MODES.length;
            if (validMode(next)) return next;
        }
        return current;
    }

    private static void gamemode() {
        final var player = Minecraft.getInstance().player;
        if (player == null) return;
        // NEI's cycle through the chosen modes: survival, creative, creative+ (the extended inventory), adventure
        final int cur = currentMode();
        final int next = nextMode(cur);
        if (next == cur) return;
        switch (next) {
            case 0 -> {
                CreativePlus.set(false);
                NEIConfig.runCommand(NEIConfig.COMMAND_GAMEMODE, "survival", playerName());
            }
            case 1 -> {
                CreativePlus.set(false);
                NEIConfig.runCommand(NEIConfig.COMMAND_GAMEMODE, "creative", playerName());
            }
            case 2 -> {
                if (!player.isCreative()) NEIConfig.runCommand(NEIConfig.COMMAND_GAMEMODE, "creative", playerName());
                CreativePlus.set(true);
                CreativePlus.open(); // NEI opens it at once
            }
            default -> {
                CreativePlus.set(false);
                NEIConfig.runCommand(NEIConfig.COMMAND_GAMEMODE, "adventure", playerName());
            }
        }
    }

    /** NEI's time buttons: dawn, noon, dusk, midnight (hours 0, 6, 12, 18 of the day). */
    private static void time(int zone) {
        // NEI's setHourForward: the hour (0, 6, 12, 18) of the next day, as a world time
        final var level = Minecraft.getInstance().level;
        final long now = level == null ? 0 : level.getDayTime();
        NEIConfig.runCommand(NEIConfig.COMMAND_TIME, (now / 24000L) * 24000L + 24000L + zone * 6000L);
    }

    private static void heal() {
        NEIConfig.runCommand(NEIConfig.COMMAND_HEAL, playerName()); // NEI's command.heal
    }

    /**
     * NEI's {@code LayoutManager.tickKeyStates}: the world.* keys work only with no screen open. They need the cheat
     * setting and operator commands, like the buttons (the toolbar itself may be hidden).
     */
    public static void onTick() {
        final var mc = Minecraft.getInstance();
        final boolean usable = mc.screen == null && mc.player != null && NEIConfig.CHEAT_ENABLED.getAsBoolean()
                && Perms.canUseCommands();
        // Keys pressed meanwhile are drained so they do not fire later.
        final Runnable[] actions = { () -> time(0), () -> time(1), () -> time(2), () -> time(3), Toolbar::rain,
                Toolbar::heal, Toolbar::gamemode };
        final KeyMapping[] keys = { NEIKeys.WORLD_DAWN, NEIKeys.WORLD_NOON, NEIKeys.WORLD_DUSK, NEIKeys.WORLD_MIDNIGHT,
                NEIKeys.WORLD_RAIN, NEIKeys.WORLD_HEAL, NEIKeys.WORLD_CREATIVE };
        for (int i = 0; i < keys.length; i++) {
            while (keys[i].consumeClick()) if (usable) actions[i].run();
        }
    }

    /** How many button rows the toolbar takes (0 when hidden): the bookmark panel starts below them. */
    static int rows(int screenWidth, int xSize) {
        if (!visible() || count() == 0) return 0;
        final int[] last = pos(count() - 1, screenWidth, xSize);
        return (last[1] - MARGIN) / (H + MARGIN) + 1;
    }

    /** Bottom edge of the toolbar, or 0 when hidden. */
    static int bottom(int screenWidth, int xSize) {
        final int rows = rows(screenWidth, xSize);
        return rows == 0 ? 0 : MARGIN + rows * (H + MARGIN);
    }

    /**
     * Where the {@code index}th button goes: NEI's rows from the top left, but a cell that touches what another mod draws
     * beside the inventory (FTB Library's sidebar buttons, areas mods register with JEI, their widgets) is skipped and the
     * button takes the next free cell, so the toolbar never covers another mod's buttons (the panels do the same).
     */
    private static int[] pos(int index, int screenWidth, int xSize) {
        final int n = perRow(screenWidth, xSize);
        final java.util.List<int[]> areas = GuiAreas.current();
        int free = -1;
        for (int cell = 0; cell < 400; cell++) {
            final int x = MARGIN + (cell % n) * (W + MARGIN);
            final int y = MARGIN + (cell / n) * (H + MARGIN);
            if (EffectAreas.hits(areas, x, y, W, H)) continue;
            if (++free == index) return new int[] { x, y };
        }
        return new int[] { MARGIN + (index % n) * (W + MARGIN), MARGIN + (index / n) * (H + MARGIN) };
    }

    static void render(GuiGraphics g, int mx, int my, AbstractContainerScreen<?> screen) {
        if (!visible()) return;
        final List<B> buttons = buttons(screen);
        for (int i = 0; i < buttons.size(); i++) {
            final B b = buttons.get(i);
            final int[] p = pos(i, screen.width, Frame.width(screen));
            final boolean over = Draw.in(mx, my, p[0], p[1], W, H);
            Draw.button(g, p[0], p[1], W, H, over || b.active());
            if (b.active()) { // NEI marks a switched-on button with a white outline
                Gfx.fill(g, p[0], p[1], p[0] + W, p[1] + 1, 0xFFFFFFFF);
                Gfx.fill(g, p[0], p[1] + H - 1, p[0] + W, p[1] + H, 0xFFFFFFFF);
                Gfx.fill(g, p[0], p[1], p[0] + 1, p[1] + H, 0xFFFFFFFF);
                Gfx.fill(g, p[0] + W - 1, p[1], p[0] + W, p[1] + H, 0xFFFFFFFF);
            }
            Tex.blit(g, Tex.ICONS, 256, 256, p[0] + 3, p[1] + 2, b.u(), b.v(), 12, 12);
            if (over) {
                final List<Component> lines = new ArrayList<>();
                for (String t : b.tips()) lines.add(Component.literal(t));
                ItemTooltip.requestPlain(lines);
            }
        }
    }

    /** Whether the point is on a toolbar button. */
    static boolean contains(double mx, double my, AbstractContainerScreen<?> screen) {
        if (!visible()) return false;
        for (int i = 0; i < count(); i++) {
            final int[] p = pos(i, screen.width, Frame.width(screen));
            if (Draw.in(mx, my, p[0], p[1], W, H)) return true;
        }
        return false;
    }

    /** Runs the button under the point. Returns true if there was one. */
    static boolean click(double mx, double my, int button, AbstractContainerScreen<?> screen) {
        if (!contains(mx, my, screen)) return false;
        if (button != 0 && button != 1) return true;
        final List<B> buttons = buttons(screen);
        for (int i = 0; i < buttons.size(); i++) {
            final int[] p = pos(i, screen.width, Frame.width(screen));
            if (Draw.in(mx, my, p[0], p[1], W, H)) {
                if (buttons.get(i).name().equals("delete")) Deleting.button(screen, button == 1); // NEI: the delete button also takes a right click
                else if (button == 0) buttons.get(i).action().run();
                break;
            }
        }
        return true;
    }
}
