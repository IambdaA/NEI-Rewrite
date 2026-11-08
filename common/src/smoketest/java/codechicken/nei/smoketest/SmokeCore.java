package codechicken.nei.smoketest;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/**
 * NEI Rewrite's test driver, loader-neutral half (1.20.1 / 1.21.1). Never shipped with the mod: each loader module builds it
 * with a small entry class into its own jar for test copies of Prism instances (see smoketest/README.md).
 *
 * <p>Runs the commands in {@code <game dir>/nei-smoketest/script.txt} (if present) and then lines appended to
 * {@code nei-smoketest/commands.txt} while the game runs. Results go to {@code nei-smoketest/out.log} and the game log with
 * the prefix {@code [NEI-SMOKE]}. Coordinates are GUI-scaled. The game's private input methods are found by their Mojang
 * (dev, NeoForge), SRG (Forge) or intermediary (Fabric) names.
 */
public final class SmokeCore {

    private static final Logger LOG = LogUtils.getLogger();
    private static final String WORLD = "neitest";

    private static int[] FAKE_WIDGET;

    private final Deque<String> queue = new ArrayDeque<>();
    private Path dir, commands, out;
    private long commandsRead;
    private int waitTicks;
    private boolean waitingForWorld, started;
    private int errors;
    private int menuTicks;
    /** The last position the test moved the mouse to (GUI-scaled), put back before keys and screenshots: on Wayland the
     * real cursor cannot be moved, and its own events would otherwise move the game's mouse back between ticks. */
    private double lastX = -1, lastY = -1;

    public SmokeCore() {
        dir = Path.of("nei-smoketest").toAbsolutePath(); // the game directory is the working directory (Prism, dev runs)
        commands = dir.resolve("commands.txt");
        out = dir.resolve("out.log");
        try {
            Files.createDirectories(dir.resolve("shots"));
            Files.writeString(out, "");
            Files.writeString(commands, "");
            Path script = dir.resolve("script.txt");
            String prop = System.getProperty("nei.smoketest.script");
            if (prop != null && !prop.isBlank()) script = Path.of(prop);
            if (Files.exists(script)) queue.addAll(Files.readAllLines(script));
        } catch (IOException e) {
            throw new RuntimeException("NEI smoke test: cannot set up " + dir, e);
        }
        report("driver loaded, commands file " + commands);
    }

    /** The stand-in for another mod's widget ({@code fakewidget}) to add to a container screen being set up, or null. */
    public Button fakeWidget(Screen screen) {
        int[] f = FAKE_WIDGET;
        if (f == null || !(screen instanceof AbstractContainerScreen<?>)) return null;
        return Button.builder(Component.literal("FAKE"), b -> report("fake widget clicked")).bounds(f[0], f[1], f[2], f[3]).build();
    }

    /** Call at the end of every client tick. */
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!started) {
            // a modpack may replace the title screen (ATM9's Pack Menu): any menu with no world, stable for 2 seconds
            if (mc.getOverlay() != null || mc.level != null || mc.screen == null) {
                menuTicks = 0;
                return;
            }
            if (++menuTicks < 40) return;
            started = true;
            report("READY title screen (" + mc.screen.getClass().getName() + ")");
        }
        pollCommandsFile();
        if (waitingForWorld) {
            if (mc.player == null || mc.level == null || mc.screen != null) return;
            waitingForWorld = false;
            waitTicks = 60; // let chunks and NEI's item list settle (a modpack takes longer)
            report("IN_WORLD " + WORLD);
            return;
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        // one command per tick so screens can open and lay out between steps
        while (!queue.isEmpty()) {
            String line = queue.poll().strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            try {
                run(mc, line);
            } catch (Throwable t) {
                errors++;
                StringWriter sw = new StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                report("ERROR in '" + line + "': " + sw);
            }
            break;
        }
    }

    private void run(Minecraft mc, String line) throws Exception {
        String[] a = line.split("\\s+");
        Window w = mc.getWindow();
        if (lastX >= 0 && List.of("key", "press", "release", "type", "shot", "areas", "scroll").contains(a[0].toLowerCase(Locale.ROOT))) {
            move(mc, lastX, lastY);
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "world" -> {
                if (mc.getLevelSource().levelExists(WORLD)) {
                    mc.createWorldOpenFlows().loadLevel(new TitleScreen(), WORLD);
                } else {
                    LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.NORMAL, true,
                            new GameRules(), WorldDataConfiguration.DEFAULT);
                    mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(12345L, false, false),
                            WorldPresets::createNormalWorldDimensions);
                }
                waitingForWorld = true;
                report("opening world " + WORLD);
            }
            case "wait" -> waitTicks = Integer.parseInt(a[1]);
            case "inv" -> {
                mc.setScreen(new InventoryScreen(mc.player));
                waitTicks = 5;
                report("opened inventory");
            }
            case "close" -> {
                mc.setScreen(null);
                report("closed screen");
            }
            case "screen" -> {
                Screen s = mc.screen;
                String box = "";
                report("SCREEN " + (s == null ? "none" : s.getClass().getName()) + " gui=" + w.getGuiScaledWidth() + "x"
                        + w.getGuiScaledHeight() + " scale=" + w.getGuiScale() + box);
            }
            case "carried" -> { // the cursor stack and the player's inventory item count, to check give / delete clicks
                int inv = 0;
                for (ItemStack st : mc.player.getInventory().items) inv += st.getCount();
                ItemStack carried = (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs
                        ? cs.getMenu() : mc.player.containerMenu).getCarried(); // 1.20.1's creative screen has its own menu
                report("CARRIED " + (carried.isEmpty() ? "empty" : carried.getCount() + "x " + carried.getItem()) + " inv=" + inv);
            }
            case "use" -> { // right-click what the player looks at (opens a machine placed in front of the player)
                Method use = method(Minecraft.class, "startUseItem", "m_91277_", "method_1583");
                use.invoke(mc);
                waitTicks = 10;
                report("used " + (mc.hitResult == null ? "nothing" : mc.hitResult.getType()));
            }
            case "move" -> move(mc, Double.parseDouble(a[1]), Double.parseDouble(a[2]));
            case "click" -> {
                move(mc, Double.parseDouble(a[1]), Double.parseDouble(a[2]));
                int button = a.length > 3 ? switch (a[3]) {
                    case "right" -> GLFW.GLFW_MOUSE_BUTTON_RIGHT;
                    case "middle" -> GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
                    default -> GLFW.GLFW_MOUSE_BUTTON_LEFT;
                } : GLFW.GLFW_MOUSE_BUTTON_LEFT;
                int mods = mods(a, 4);
                Method onPress = onPress(mc);
                onPress.invoke(mc.mouseHandler, w.getWindow(), button, GLFW.GLFW_PRESS, mods);
                onPress.invoke(mc.mouseHandler, w.getWindow(), button, GLFW.GLFW_RELEASE, mods);
                report("clicked " + a[1] + "," + a[2]);
            }
            case "drag" -> {
                // drag x1 y1 x2 y2: press, move in 10 steps one tick apart, release (put at the front of the queue)
                double x1 = Double.parseDouble(a[1]), y1 = Double.parseDouble(a[2]);
                double x2 = Double.parseDouble(a[3]), y2 = Double.parseDouble(a[4]);
                List<String> steps = new ArrayList<>();
                steps.add("mousedown " + x1 + " " + y1);
                for (int i = 1; i <= 10; i++) steps.add("move " + (x1 + (x2 - x1) * i / 10) + " " + (y1 + (y2 - y1) * i / 10));
                steps.add("mouseup " + x2 + " " + y2);
                for (int i = steps.size() - 1; i >= 0; i--) queue.addFirst(steps.get(i));
            }
            case "mousedown", "mouseup" -> {
                move(mc, Double.parseDouble(a[1]), Double.parseDouble(a[2]));
                onPress(mc).invoke(mc.mouseHandler, w.getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT,
                        a[0].equalsIgnoreCase("mousedown") ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, 0);
                report(a[0] + " " + a[1] + "," + a[2]);
            }
            case "press", "release" -> {
                boolean press = a[0].equalsIgnoreCase("press");
                int key = keyCode(a[1]);
                keyPress(mc).invoke(mc.keyboardHandler, w.getWindow(), key, GLFW.glfwGetKeyScancode(key),
                        press ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, 0);
                report((press ? "holding " : "released ") + a[1]);
            }
            case "fakewidget" -> {
                FAKE_WIDGET = a[1].equals("off") ? null : new int[] {Integer.parseInt(a[1]), Integer.parseInt(a[2]),
                        Integer.parseInt(a[3]), Integer.parseInt(a[4])};
                report("fake widget " + (FAKE_WIDGET == null ? "off" : String.join(" ", a[1], a[2], a[3], a[4]))
                        + " (reopen the screen)");
            }
            case "scroll" -> {
                move(mc, Double.parseDouble(a[1]), Double.parseDouble(a[2]));
                method(mc.mouseHandler.getClass(), "onScroll", "m_91526_", "method_1598", long.class, double.class, double.class)
                        .invoke(mc.mouseHandler, w.getWindow(), 0.0, Double.parseDouble(a[3]));
                report("scrolled " + a[3]);
            }
            case "key" -> {
                int mods = mods(a, 2);
                int key = keyCode(a[1]);
                Method keyPress = keyPress(mc);
                int scan = GLFW.glfwGetKeyScancode(key);
                keyPress.invoke(mc.keyboardHandler, w.getWindow(), key, scan, GLFW.GLFW_PRESS, mods);
                keyPress.invoke(mc.keyboardHandler, w.getWindow(), key, scan, GLFW.GLFW_RELEASE, mods);
                report("pressed " + a[1]);
            }
            case "type" -> {
                String text = line.substring(line.indexOf(' ') + 1);
                Method charTyped = method(mc.keyboardHandler.getClass(), "charTyped", "m_90889_", "method_1457", long.class, int.class, int.class);
                for (int cp : text.codePoints().toArray()) charTyped.invoke(mc.keyboardHandler, w.getWindow(), cp, 0);
                report("typed '" + text + "'");
            }
            case "cmd" -> {
                String c = line.substring(line.indexOf(' ') + 1);
                mc.player.connection.sendCommand(c.startsWith("/") ? c.substring(1) : c);
                waitTicks = 5;
                report("command " + c);
            }
            case "shot" -> {
                String name = a.length > 1 ? a[1] : "shot";
                Path file = dir.resolve("shots").resolve(name + ".png");
                Files.deleteIfExists(file);
                try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    image.writeToFile(file);
                }
                report("SHOT " + file);
            }
            case "areas" -> {
                Method current = Class.forName("codechicken.nei.client.GuiAreas").getDeclaredMethod("current");
                current.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<int[]> areas = (List<int[]>) current.invoke(null);
                StringBuilder sb = new StringBuilder("AREAS " + areas.size());
                for (int[] r : areas) sb.append(" [").append(r[0]).append(',').append(r[1]).append(' ').append(r[2]).append('x').append(r[3]).append(']');
                report(sb.toString());
            }
            case "cheatmode" -> { // cheatmode <0 recipe | 1 utility | 2 cheat>: NEI's wrench mode (each instance has its own config)
                Class.forName("codechicken.nei.NEIConfig").getMethod("setCheatMode", int.class).invoke(null, Integer.parseInt(a[1]));
                report("cheat mode " + a[1]);
            }
            case "search" -> { // put text into NEI's item search (works on any GUI size)
                String text = line.indexOf(' ') < 0 ? "" : line.substring(line.indexOf(' ') + 1);
                Method set = Class.forName("codechicken.nei.client.NEIClientEvents").getDeclaredMethod("searchFor", String.class);
                set.setAccessible(true);
                set.invoke(null, text);
                report("searched '" + text + "'");
            }
            case "finditem" -> { // finditem <name>: move the mouse onto the item panel entry with that name (exact, else containing)
                String want = line.substring(line.indexOf(' ') + 1).toLowerCase(Locale.ROOT);
                java.lang.reflect.Field f = Class.forName("codechicken.nei.client.NEIClientEvents").getDeclaredField("ITEMS");
                f.setAccessible(true);
                Object panel = f.get(null);
                Method entryAt = panel.getClass().getMethod("entryAt", double.class, double.class);
                Screen sc = mc.screen;
                double[] exact = null, partial = null;
                String exactName = null, partialName = null;
                for (int y = 2; y < sc.height && exact == null; y += 4) {
                    for (int x = sc.width / 2; x < sc.width && exact == null; x += 4) {
                        Object e = entryAt.invoke(panel, (double) x, (double) y);
                        if (e == null) continue;
                        net.minecraft.world.item.ItemStack st = (net.minecraft.world.item.ItemStack) e.getClass().getField("stack").get(e);
                        String name = st.getHoverName().getString();
                        if (name.toLowerCase(Locale.ROOT).equals(want)) { exact = new double[] {x, y}; exactName = name; }
                        else if (partial == null && name.toLowerCase(Locale.ROOT).contains(want)) { partial = new double[] {x, y}; partialName = name; }
                    }
                }
                double[] at = exact != null ? exact : partial;
                if (at == null) {
                    errors++;
                    report("ERROR finditem: no '" + want + "' in the item panel");
                } else {
                    // the middle of that slot: step to its edges
                    double x0 = at[0], x1 = at[0], y0 = at[1], y1 = at[1];
                    Object here = entryAt.invoke(panel, at[0], at[1]);
                    while (x0 > 0 && entryAt.invoke(panel, x0 - 1, at[1]) == here) x0--;
                    while (entryAt.invoke(panel, x1 + 1, at[1]) == here) x1++;
                    while (y0 > 0 && entryAt.invoke(panel, at[0], y0 - 1) == here) y0--;
                    while (entryAt.invoke(panel, at[0], y1 + 1) == here) y1++;
                    move(mc, Math.floor((x0 + x1) / 2), Math.floor((y0 + y1) / 2));
                    report("FOUND '" + (exact != null ? exactName : partialName) + "' at " + (int) lastX + "," + (int) lastY);
                }
            }
            case "clickhere" -> { // clickhere [right]: click where the mouse is (after finditem)
                queue.addFirst("click " + lastX + " " + lastY + (a.length > 1 ? " " + a[1] : ""));
            }
            case "clickinv" -> { // clickinv <player inventory slot> [right]: 0-8 hotbar, 9-35 main inventory, on any container screen
                int want = Integer.parseInt(a[1]);
                Screen sc = mc.screen;
                if (!(sc instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> c)) throw new IllegalStateException("no container screen");
                int left = intField(c, "leftPos", "f_97735_", "field_2776"), top = intField(c, "topPos", "f_97736_", "field_2800");
                net.minecraft.world.inventory.Slot found = null;
                for (net.minecraft.world.inventory.Slot sl : c.getMenu().slots) {
                    if (sl.container == mc.player.getInventory() && sl.getContainerSlot() == want && sl.isActive()) found = sl;
                }
                if (found == null) throw new IllegalStateException("no inventory slot " + want + " on this screen");
                queue.addFirst("click " + (left + found.x + 8) + " " + (top + found.y + 8) + (a.length > 2 ? " " + a[2] : ""));
            }
            case "log" -> report("NOTE " + line.substring(line.indexOf(' ') + 1));
            case "quit" -> {
                report("QUIT errors=" + errors);
                mc.stop();
            }
            default -> {
                errors++;
                report("ERROR unknown command '" + line + "'");
            }
        }
    }

    private static Method onPress(Minecraft mc) throws NoSuchMethodException {
        return method(mc.mouseHandler.getClass(), "onPress", "m_91530_", "method_1601", long.class, int.class, int.class, int.class);
    }

    private static Method keyPress(Minecraft mc) throws NoSuchMethodException {
        return method(mc.keyboardHandler.getClass(), "keyPress", "m_90893_", "method_1466", long.class, int.class, int.class, int.class, int.class);
    }

    private void move(Minecraft mc, double gx, double gy) throws ReflectiveOperationException {
        Window w = mc.getWindow();
        lastX = gx;
        lastY = gy;
        double rx = gx * w.getScreenWidth() / w.getGuiScaledWidth();
        double ry = gy * w.getScreenHeight() / w.getGuiScaledHeight();
        GLFW.glfwSetCursorPos(w.getWindow(), rx, ry); // keep the real cursor (and its hover) where the test points
        method(mc.mouseHandler.getClass(), "onMove", "m_91561_", "method_1600", long.class, double.class, double.class)
                .invoke(mc.mouseHandler, w.getWindow(), rx, ry);
    }

    /** A private method by its Mojang, SRG or intermediary name (whichever the running game uses). */
    private static Method method(Class<?> owner, String mojang, String srg, String intermediary, Class<?>... params)
            throws NoSuchMethodException {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            for (String n : new String[] {mojang, srg, intermediary}) {
                try {
                    Method m = c.getDeclaredMethod(n, params);
                    m.setAccessible(true);
                    return m;
                } catch (NoSuchMethodException ignored) {
                }
            }
        }
        throw new NoSuchMethodException(owner.getName() + "." + mojang);
    }

    /** A field of a screen class by its Mojang, SRG or intermediary name. */
    private static int intField(Object obj, String... names) throws ReflectiveOperationException {
        for (Class<?> c = obj.getClass(); c != null; c = c.getSuperclass()) {
            for (String n : names) {
                try {
                    java.lang.reflect.Field f = c.getDeclaredField(n);
                    f.setAccessible(true);
                    return f.getInt(obj);
                } catch (NoSuchFieldException ignored) {
                }
            }
        }
        throw new NoSuchFieldException(names[0]);
    }

    private static int mods(String[] a, int from) {
        int mods = 0;
        for (int i = from; i < a.length; i++) mods |= switch (a[i]) {
            case "shift" -> GLFW.GLFW_MOD_SHIFT;
            case "ctrl" -> GLFW.GLFW_MOD_CONTROL;
            case "alt" -> GLFW.GLFW_MOD_ALT;
            default -> throw new IllegalArgumentException("unknown modifier " + a[i]);
        };
        return mods;
    }

    private static int keyCode(String name) {
        String n = name.toUpperCase(Locale.ROOT);
        return switch (n) {
            case "ESC", "ESCAPE" -> GLFW.GLFW_KEY_ESCAPE;
            case "ENTER", "RETURN" -> GLFW.GLFW_KEY_ENTER;
            case "BACKSPACE" -> GLFW.GLFW_KEY_BACKSPACE;
            case "TAB" -> GLFW.GLFW_KEY_TAB;
            case "SPACE" -> GLFW.GLFW_KEY_SPACE;
            case "DELETE" -> GLFW.GLFW_KEY_DELETE;
            case "SHIFT" -> GLFW.GLFW_KEY_LEFT_SHIFT;
            case "CTRL", "CONTROL" -> GLFW.GLFW_KEY_LEFT_CONTROL;
            case "ALT" -> GLFW.GLFW_KEY_LEFT_ALT;
            case "UP" -> GLFW.GLFW_KEY_UP;
            case "DOWN" -> GLFW.GLFW_KEY_DOWN;
            case "LEFT" -> GLFW.GLFW_KEY_LEFT;
            case "RIGHT" -> GLFW.GLFW_KEY_RIGHT;
            case "PAGEUP" -> GLFW.GLFW_KEY_PAGE_UP;
            case "PAGEDOWN" -> GLFW.GLFW_KEY_PAGE_DOWN;
            case "HOME" -> GLFW.GLFW_KEY_HOME;
            case "END" -> GLFW.GLFW_KEY_END;
            default -> {
                if (n.length() == 1 && n.charAt(0) >= 'A' && n.charAt(0) <= 'Z') yield GLFW.GLFW_KEY_A + (n.charAt(0) - 'A');
                if (n.length() == 1 && n.charAt(0) >= '0' && n.charAt(0) <= '9') yield GLFW.GLFW_KEY_0 + (n.charAt(0) - '0');
                if (n.matches("F([1-9]|1[0-2])")) yield GLFW.GLFW_KEY_F1 + Integer.parseInt(n.substring(1)) - 1;
                throw new IllegalArgumentException("unknown key " + name);
            }
        };
    }

    private void pollCommandsFile() {
        try {
            long size = Files.size(commands);
            if (size <= commandsRead) return;
            byte[] all = Files.readAllBytes(commands);
            String fresh = new String(all, (int) commandsRead, all.length - (int) commandsRead, StandardCharsets.UTF_8);
            int lastNl = fresh.lastIndexOf('\n');
            if (lastNl < 0) return; // wait for a complete line
            commandsRead += fresh.substring(0, lastNl + 1).getBytes(StandardCharsets.UTF_8).length;
            queue.addAll(List.of(fresh.substring(0, lastNl).split("\n")));
        } catch (IOException e) {
            LOG.warn("[NEI-SMOKE] cannot read {}: {}", commands, e.toString());
        }
    }

    private synchronized void report(String msg) {
        LOG.info("[NEI-SMOKE] {}", msg);
        try {
            Files.writeString(out, msg + "\n", StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }
}
