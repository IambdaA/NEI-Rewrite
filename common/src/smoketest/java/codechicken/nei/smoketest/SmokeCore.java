package codechicken.nei.smoketest;

import com.mojang.blaze3d.platform.Window;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NEI Rewrite's test driver, loader-neutral half (26.x). Never shipped with the mod: the Fabric dev run ("smokeTest") and the
 * loader modules' smoketest jars (for test copies of Prism instances) wrap it in a small entry class.
 *
 * <p>Runs the commands of {@code -Dnei.smoketest.script} or {@code <game dir>/nei-smoketest/script.txt}, then lines appended
 * to {@code nei-smoketest/commands.txt} while the game runs. Results go to {@code nei-smoketest/out.log} and the game log
 * with the prefix {@code [NEI-SMOKE]}. Coordinates are GUI-scaled, like a screen's mouse coordinates. See smoketest/README.md.
 */
public final class SmokeCore {

    private static final Logger LOG = LoggerFactory.getLogger("NEI-SMOKE");
    private static final String WORLD = "neitest";

    /** Keys held by {@code press}; vanilla's own key checks see them through InputConstantsMixin. */
    public static final java.util.Set<Integer> HELD = java.util.concurrent.ConcurrentHashMap.newKeySet();
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
    public net.minecraft.client.gui.components.Button fakeWidget(Screen screen) {
        int[] f = FAKE_WIDGET;
        if (f == null || !(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) return null;
        return net.minecraft.client.gui.components.Button.builder(net.minecraft.network.chat.Component.literal("FAKE"),
                b -> report("fake widget clicked")).bounds(f[0], f[1], f[2], f[3]).build();
    }

    /** Call at the end of every client tick. */
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!started) {
            // a modpack may replace the title screen: any menu with no world, stable for 2 seconds
            if (!mc.isGameLoadFinished() || mc.level != null || mc.screen == null) {
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
            waitTicks = 40; // let chunks and NEI's item list settle
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
                    mc.createWorldOpenFlows().openWorld(WORLD, () -> mc.setScreen(new TitleScreen()));
                } else {
                    LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE,
                            LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT);
                    mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(12345L, false, false),
                            WorldPresets::createNormalWorldDimensions, new TitleScreen());
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
                report("SCREEN " + (s == null ? "none" : s.getClass().getName()) + " gui=" + w.getGuiScaledWidth() + "x"
                        + w.getGuiScaledHeight() + " scale=" + w.getGuiScale());
            }
            case "carried" -> { // the cursor stack and the player's inventory item count, to check give / delete clicks
                int inv = 0;
                for (var st : mc.player.getInventory().getNonEquipmentItems()) inv += st.getCount();
                var carried = (mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs
                        ? cs.getMenu() : mc.player.containerMenu).getCarried(); // 1.20.1's creative screen has its own menu
                report("CARRIED " + (carried.isEmpty() ? "empty" : carried.getCount() + "x " + carried.getItem()) + " inv=" + inv);
            }
            case "use" -> { // right-click what the player looks at (opens a block placed in front of the player)
                method(Minecraft.class, "startUseItem").invoke(mc);
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
                Method onButton = method(mc.mouseHandler.getClass(), "onButton", long.class, MouseButtonInfo.class, int.class);
                onButton.invoke(mc.mouseHandler, w.handle(), new MouseButtonInfo(button, mods), GLFW.GLFW_PRESS);
                onButton.invoke(mc.mouseHandler, w.handle(), new MouseButtonInfo(button, mods), GLFW.GLFW_RELEASE);
                report("clicked " + a[1] + "," + a[2]);
            }
            case "drag" -> {
                // drag x1 y1 x2 y2: press, move in 10 steps one tick apart (the game sends drag events once per frame),
                // release. Runs as mousedown / move / mouseup steps put at the front of the queue.
                double x1 = Double.parseDouble(a[1]), y1 = Double.parseDouble(a[2]);
                double x2 = Double.parseDouble(a[3]), y2 = Double.parseDouble(a[4]);
                java.util.List<String> steps = new java.util.ArrayList<>();
                steps.add("mousedown " + x1 + " " + y1);
                for (int i = 1; i <= 10; i++) steps.add("move " + (x1 + (x2 - x1) * i / 10) + " " + (y1 + (y2 - y1) * i / 10));
                steps.add("mouseup " + x2 + " " + y2);
                for (int i = steps.size() - 1; i >= 0; i--) queue.addFirst(steps.get(i));
            }
            case "mousedown", "mouseup" -> {
                move(mc, Double.parseDouble(a[1]), Double.parseDouble(a[2]));
                method(mc.mouseHandler.getClass(), "onButton", long.class, MouseButtonInfo.class, int.class).invoke(
                        mc.mouseHandler, w.handle(), new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0),
                        a[0].equalsIgnoreCase("mousedown") ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE);
                report(a[0] + " " + a[1] + "," + a[2]);
            }
            case "press", "release" -> {
                // press/release a key without the other half: holds a modifier (shift, ctrl, alt) for later steps
                boolean press = a[0].equalsIgnoreCase("press");
                int key = keyCode(a[1]);
                if (press) HELD.add(key); else HELD.remove(key);
                method(mc.keyboardHandler.getClass(), "keyPress", long.class, int.class, KeyEvent.class).invoke(
                        mc.keyboardHandler, w.handle(), press ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE,
                        new KeyEvent(key, GLFW.glfwGetKeyScancode(key), 0));
                report((press ? "holding " : "released ") + a[1]);
            }
            case "fakewidget" -> {
                // fakewidget x y w h | off: a button on every container screen, standing in for another mod's sidebar
                FAKE_WIDGET = a[1].equals("off") ? null : new int[] {Integer.parseInt(a[1]), Integer.parseInt(a[2]),
                        Integer.parseInt(a[3]), Integer.parseInt(a[4])};
                report("fake widget " + (FAKE_WIDGET == null ? "off" : String.join(" ", a[1], a[2], a[3], a[4]))
                        + " (reopen the screen)");
            }
            case "scroll" -> {
                move(mc, Double.parseDouble(a[1]), Double.parseDouble(a[2]));
                method(mc.mouseHandler.getClass(), "onScroll", long.class, double.class, double.class)
                        .invoke(mc.mouseHandler, w.handle(), 0.0, Double.parseDouble(a[3]));
                report("scrolled " + a[3]);
            }
            case "key" -> {
                int mods = mods(a, 2);
                int key = keyCode(a[1]);
                Method keyPress = method(mc.keyboardHandler.getClass(), "keyPress", long.class, int.class, KeyEvent.class);
                KeyEvent ev = new KeyEvent(key, GLFW.glfwGetKeyScancode(key), mods);
                keyPress.invoke(mc.keyboardHandler, w.handle(), GLFW.GLFW_PRESS, ev);
                keyPress.invoke(mc.keyboardHandler, w.handle(), GLFW.GLFW_RELEASE, ev);
                report("pressed " + a[1]);
            }
            case "type" -> {
                String text = line.substring(line.indexOf(' ') + 1);
                Method charTyped = method(mc.keyboardHandler.getClass(), "charTyped", long.class, CharacterEvent.class);
                text.codePoints().forEach(cp -> {
                    try {
                        charTyped.invoke(mc.keyboardHandler, w.handle(), new CharacterEvent(cp));
                    } catch (ReflectiveOperationException e) {
                        throw new RuntimeException(e);
                    }
                });
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
                Screenshot.takeScreenshot(mc.getMainRenderTarget(), image -> {
                    try (image) {
                        image.writeToFile(file);
                        report("SHOT " + file);
                    } catch (IOException e) {
                        errors++;
                        report("ERROR screenshot " + file + ": " + e);
                    }
                });
            }
            case "areas" -> {
                // the areas of other mods / widgets NEI's panels keep clear of this frame (GuiAreas.current())
                Method current = method(Class.forName("codechicken.nei.client.GuiAreas"), "current");
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

    private void move(Minecraft mc, double gx, double gy) throws ReflectiveOperationException {
        Window w = mc.getWindow();
        lastX = gx;
        lastY = gy;
        double rx = gx * w.getScreenWidth() / w.getGuiScaledWidth();
        double ry = gy * w.getScreenHeight() / w.getGuiScaledHeight();
        GLFW.glfwSetCursorPos(w.handle(), rx, ry); // keep the real cursor (and its hover) where the test points
        method(mc.mouseHandler.getClass(), "onMove", long.class, double.class, double.class)
                .invoke(mc.mouseHandler, w.handle(), rx, ry);
    }

    private static Method method(Class<?> owner, String name, Class<?>... params) throws NoSuchMethodException {
        // NEI's mixins may subclass nothing here, but walk up just in case
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException(owner.getName() + "." + name);
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
