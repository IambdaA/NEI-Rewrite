package codechicken.nei.client;

import java.util.ArrayList;
import java.util.List;

import codechicken.nei.NEIConfig;
import codechicken.nei.NotEnoughItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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

    private record B(int u, int v, boolean active, List<String> tips, Runnable action) {}

    private Toolbar() {}

    static boolean visible() {
        final var player = Minecraft.getInstance().player;
        return NEIConfig.CHEAT_ENABLED.getAsBoolean() && player != null
                && (player.isCreative() || NEIConfig.TOOLBAR_IN_SURVIVAL.getAsBoolean()) && Perms.canUseCommands();
    }

    private static int perRow(int screenWidth, int xSize) {
        return Math.max(((screenWidth - xSize) / 2 - 4) / 20, 1);
    }

    private static void command(String text) {
        final var player = Minecraft.getInstance().player;
        if (player != null) player.connection.sendCommand(text);
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
        list.add(new B(144, 12, Deleting.modeOn(screen), deleteTips, () -> Deleting.button(screen, false)));
        list.add(new B(120, 12, raining, List.of(raining ? "Turn rain off" : "Turn rain on"), Toolbar::rain));
        final boolean plus = CreativePlus.active();
        final String now = plus ? "creative+" : creative ? "creative" : "survival";
        final String next = !creative ? "creative" : CreativePlus.available() && !plus ? "creative+" : "survival";
        list.add(new B(132, 12, creative,
                List.of("Current gamemode: " + now, "Click: Set gamemode to " + next), Toolbar::gamemode));

        final boolean magnet = Magnet.on();
        list.add(new B(180, magnet ? 36 : 24, magnet, List.of(magnet ? "Turn magnet mode off" : "Turn magnet mode on"), Magnet::toggle));

        // NEI's time buttons: dawn, noon, dusk, midnight (hours 0, 6, 12, 18 of the day)
        final int[][] icons = { { 132, 24 }, { 120, 24 }, { 144, 24 }, { 156, 24 } };
        final String[] zones = { "dawn", "noon", "dusk", "midnight" };
        for (int i = 0; i < 4; i++) {
            final int zone = i;
            list.add(new B(icons[i][0], icons[i][1], false, List.of("Set time to " + zones[i]), () -> time(zone)));
        }

        list.add(new B(168, 24, false, List.of("Heal the player"), Toolbar::heal));
        return list;
    }

    // The actions of the buttons, also run by the world.* keys.

    private static void rain() {
        final var level = Minecraft.getInstance().level;
        command(level != null && level.isRaining() ? "weather clear" : "weather rain");
    }

    private static void gamemode() {
        final var player = Minecraft.getInstance().player;
        if (player == null) return;
        // NEI's cycle: survival, creative, creative+ (the extended inventory), survival
        if (!player.isCreative()) {
            CreativePlus.set(false);
            command("gamemode creative");
        } else if (CreativePlus.available() && !CreativePlus.active()) {
            CreativePlus.set(true);
            CreativePlus.open(); // NEI opens it at once
        } else {
            CreativePlus.set(false);
            command("gamemode survival");
        }
    }

    /** NEI's time buttons: dawn, noon, dusk, midnight (hours 0, 6, 12, 18 of the day). */
    private static void time(int zone) {
        command("time set " + zone * 6000);
    }

    private static void heal() {
        command("effect give @s minecraft:instant_health 1 4");
        command("effect give @s minecraft:saturation 1 20");
    }

    /**
     * NEI's {@code LayoutManager.tickKeyStates}: the world.* keys work only with no screen open. They need the cheat
     * setting and operator commands, like the buttons (the toolbar itself may be hidden).
     */
    public static void onTick() {
        final var mc = Minecraft.getInstance();
        final boolean usable = mc.gui.screen() == null && mc.player != null && NEIConfig.CHEAT_ENABLED.getAsBoolean()
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
        if (!visible()) return 0;
        final int n = perRow(screenWidth, xSize);
        return (COUNT + n - 1) / n;
    }

    /** Bottom edge of the toolbar, or 0 when hidden. */
    static int bottom(int screenWidth, int xSize) {
        final int rows = rows(screenWidth, xSize);
        return rows == 0 ? 0 : MARGIN + rows * (H + MARGIN);
    }

    private static int[] pos(int index, int screenWidth, int xSize) {
        final int n = perRow(screenWidth, xSize);
        return new int[] { MARGIN + (index % n) * (W + MARGIN), MARGIN + (index / n) * (H + MARGIN) };
    }

    static void render(GuiGraphicsExtractor g, int mx, int my, AbstractContainerScreen<?> screen) {
        if (!visible()) return;
        final List<B> buttons = buttons(screen);
        for (int i = 0; i < buttons.size(); i++) {
            final B b = buttons.get(i);
            final int[] p = pos(i, screen.width, screen.imageWidth);
            final boolean over = Draw.in(mx, my, p[0], p[1], W, H);
            Draw.button(g, p[0], p[1], W, H, over || b.active());
            if (b.active()) { // NEI marks a switched-on button with a white outline
                g.fill(p[0], p[1], p[0] + W, p[1] + 1, 0xFFFFFFFF);
                g.fill(p[0], p[1] + H - 1, p[0] + W, p[1] + H, 0xFFFFFFFF);
                g.fill(p[0], p[1], p[0] + 1, p[1] + H, 0xFFFFFFFF);
                g.fill(p[0] + W - 1, p[1], p[0] + W, p[1] + H, 0xFFFFFFFF);
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
        for (int i = 0; i < COUNT; i++) {
            final int[] p = pos(i, screen.width, screen.imageWidth);
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
            final int[] p = pos(i, screen.width, screen.imageWidth);
            if (Draw.in(mx, my, p[0], p[1], W, H)) {
                if (i == 0) Deleting.button(screen, button == 1); // NEI: the delete button also takes a right click
                else if (button == 0) buttons.get(i).action().run();
                break;
            }
        }
        return true;
    }
}
