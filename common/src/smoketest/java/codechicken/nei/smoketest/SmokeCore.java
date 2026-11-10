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
    private int passes, fails;
    /** Keys held by {@code press}: with Ctrl or Alt down a letter key types no character. */
    private final java.util.Set<Integer> down = new java.util.HashSet<>();
    private String section = "";
    private final Expect expect = new Expect(() -> Minecraft.getInstance().screen);
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
            case "close" -> { // as Esc does: a container screen tells the server its menu closed
                if (mc.screen != null) mc.screen.onClose();
                else mc.setScreen(null);
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
            case "moveby" -> move(mc, lastX + Double.parseDouble(a[1]), lastY + Double.parseDouble(a[2])); // moveby dx dy: from where the mouse is
            case "click" -> { // click x y [left|right|middle] [mods]: moves there now, presses on the next tick (as a hand would)
                move(mc, Double.parseDouble(a[1]), Double.parseDouble(a[2]));
                queue.addFirst("clicknow" + line.substring(line.indexOf(' ')));
            }
            case "clicknow" -> {
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
                if (press) down.add(keyCode(a[1])); else down.remove(keyCode(a[1]));
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
                if (a[1].equals("here")) a = new String[] {a[0], String.valueOf(lastX), String.valueOf(lastY), a[2]}; // scroll here <amount>
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
                boolean ctrlAlt = (mods & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT)) != 0 || down.contains(GLFW.GLFW_KEY_LEFT_CONTROL)
                        || down.contains(GLFW.GLFW_KEY_LEFT_ALT);
                if (a[1].length() == 1 && Character.isLetterOrDigit(a[1].charAt(0)) && !ctrlAlt) {
                    boolean shift = (mods & GLFW.GLFW_MOD_SHIFT) != 0 || down.contains(GLFW.GLFW_KEY_LEFT_SHIFT);
                    queue.addFirst("type " + (shift ? a[1].toUpperCase(Locale.ROOT) : a[1].toLowerCase(Locale.ROOT))); // the character GLFW sends after the key
                }
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
            case "clickpath", "movepath" -> { // clickpath <x path> <y path> <dx> <dy> [right|left] [mods]: click a spot NEI recorded
                double x = Double.parseDouble(String.valueOf(expect.path(a[1]))) + Double.parseDouble(a[3]);
                double y = Double.parseDouble(String.valueOf(expect.path(a[2]))) + Double.parseDouble(a[4]);
                if (a[0].equalsIgnoreCase("movepath")) move(mc, x, y);
                else {
                    StringBuilder c = new StringBuilder("click " + x + " " + y);
                    for (int i = 5; i < a.length; i++) c.append(' ').append(a[i]);
                    queue.addFirst(c.toString());
                }
            }
            case "toolbar" -> { // toolbar <name> [n]: runs the n-th (from 0) toolbar button of that name (delete, rain, gamemode, magnet, time, heal)
                Method buttons = Class.forName("codechicken.nei.client.Toolbar").getDeclaredMethod("buttons",
                        net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class);
                buttons.setAccessible(true);
                int want = a.length > 2 ? Integer.parseInt(a[2]) : 0, seen = 0;
                boolean done = false;
                for (Object b : (List<?>) buttons.invoke(null, mc.screen)) {
                    Method name = b.getClass().getDeclaredMethod("name");
                    name.setAccessible(true);
                    if (!a[1].equals(name.invoke(b)) || seen++ != want) continue;
                    Method action = b.getClass().getDeclaredMethod("action");
                    action.setAccessible(true);
                    ((Runnable) action.invoke(b)).run();
                    done = true;
                    break;
                }
                if (!done) {
                    errors++;
                    report("ERROR toolbar: no button " + a[1] + (a.length > 2 ? " " + a[2] : ""));
                } else report("toolbar " + a[1]);
            }
            case "clickbottom" -> { // clickbottom <x> <distance from the bottom> [right] [mods]: NEI's bottom-left buttons
                StringBuilder c = new StringBuilder("click " + a[1] + " " + ((mc.screen).height - Double.parseDouble(a[2])));
                for (int i = 3; i < a.length; i++) c.append(' ').append(a[i]);
                queue.addFirst(c.toString());
            }
            case "clickwindow" -> { // clickwindow <dx> <dy> [right] [mods]: a click relative to the container window's top-left corner
                Screen sc = mc.screen;
                if (!(sc instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> c)) throw new IllegalStateException("no container screen");
                int left = intField(c, "leftPos", "f_97735_", "field_2776"), top = intField(c, "topPos", "f_97736_", "field_2800");
                StringBuilder cl = new StringBuilder("click " + (left + Double.parseDouble(a[1])) + " " + (top + Double.parseDouble(a[2])));
                for (int i = 3; i < a.length; i++) cl.append(' ').append(a[i]);
                queue.addFirst(cl.toString());
            }
            case "section" -> { // section <name>: names the checks that follow
                section = line.substring(line.indexOf(' ') + 1);
                report("SECTION " + section);
            }
            case "expect" -> { // expect <value> <op> <expected ...>: see Expect
                String[] e = line.split("\\s+", 4);
                String expected = e.length > 3 ? e[3] : "";
                String got;
                try {
                    got = expect.value(e[1]);
                } catch (Throwable t) {
                    got = "ERROR " + (t instanceof java.lang.reflect.InvocationTargetException ite ? ite.getCause() : t);
                }
                String bad;
                try {
                    bad = got.startsWith("ERROR ") ? got : expect.check(got, e[2], expected);
                } catch (RuntimeException ex) {
                    bad = got + " (" + ex.getMessage() + ")";
                }
                String what = section + ": " + e[1] + " " + e[2] + " " + expected;
                if (bad == null) {
                    passes++;
                    report("PASS " + what);
                } else {
                    fails++;
                    report("FAIL " + what + "  -> got: " + (bad.length() > 300 ? bad.substring(0, 300) + "..." : bad));
                }
            }
            case "call" -> { // call <path>: runs a method of NEI's classes (setting up a test)
                Object r = expect.path(line.substring(line.indexOf(' ') + 1));
                report("called " + a[1] + " = " + r);
            }
            case "chatmark" -> {
                expect.chatMark();
                report("chat mark");
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
            case "finditem" -> { // finditem [ITEMS|BOOKMARKS|HISTORY|CRAFTABLES] <name>: move the mouse onto that panel's entry with that name (exact, else containing)
                String rest = line.substring(line.indexOf(' ') + 1);
                String panelName = "ITEMS";
                boolean anyX = false;
                for (String pn : new String[] {"ITEMS", "BOOKMARKS", "HISTORY", "CRAFTABLES"}) {
                    if (rest.startsWith(pn + " ")) {
                        panelName = pn;
                        rest = rest.substring(pn.length() + 1);
                        anyX = true;
                    }
                }
                String want = rest.toLowerCase(Locale.ROOT);
                java.lang.reflect.Field f = Class.forName("codechicken.nei.client.NEIClientEvents").getDeclaredField(panelName);
                f.setAccessible(true);
                Object panel = f.get(null);
                Method entryAt = panel.getClass().getMethod("entryAt", double.class, double.class);
                Screen sc = mc.screen;
                double[] exact = null, partial = null;
                String exactName = null, partialName = null;
                for (int y = 2; y < sc.height && exact == null; y += 4) {
                    for (int x = anyX ? 1 : sc.width / 2; x < sc.width && exact == null; x += 4) {
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
                    report("ERROR finditem: no '" + want + "' in " + panelName);
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
            case "clickinv", "moveinv" -> { // clickinv <player inventory slot> [right]: 0-8 hotbar, 9-35 main inventory, on any container screen
                int want = Integer.parseInt(a[1]);
                Screen sc = mc.screen;
                if (!(sc instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> c)) throw new IllegalStateException("no container screen");
                int left = intField(c, "leftPos", "f_97735_", "field_2776"), top = intField(c, "topPos", "f_97736_", "field_2800");
                net.minecraft.world.inventory.Slot found = null;
                for (net.minecraft.world.inventory.Slot sl : c.getMenu().slots) {
                    if (sl.container == mc.player.getInventory() && sl.getContainerSlot() == want && sl.isActive()) found = sl;
                }
                if (found == null) throw new IllegalStateException("no inventory slot " + want + " on this screen");
                queue.addFirst((a[0].equalsIgnoreCase("moveinv") ? "move " : "click ") + (left + found.x + 8) + " " + (top + found.y + 8)
                        + (a.length > 2 && a[0].equalsIgnoreCase("clickinv") ? " " + a[2] : ""));
            }
            case "log" -> report("NOTE " + line.substring(line.indexOf(' ') + 1));
            case "probe" -> report("PROBE " + a[1] + " = " + expect.value(a[1])); // logs a probe's value
            case "recipes", "favor" -> {
                // recipes <itemId>: logs the item's recipes (index, tab, ingredient ids)
                // favor <itemId> <tab text> [ingredient text]: makes the first recipe of that tab (using an ingredient whose id
                // contains the text) the item's favorite, as the heart would
                final Object entry = Class.forName("codechicken.nei.ItemList").getMethod("find", String.class).invoke(null, a[1]);
                if (entry == null) throw new IllegalStateException("no item " + a[1]);
                final net.minecraft.world.item.ItemStack st = (net.minecraft.world.item.ItemStack) entry.getClass().getField("stack").get(entry);
                final List<?> views = (List<?>) Class.forName("codechicken.nei.recipe.RecipeIndex")
                        .getMethod("recipesFor", net.minecraft.world.item.Item.class).invoke(null, st.getItem());
                final Method idOf = Class.forName("codechicken.nei.item.FluidDisplay").getMethod("idOf", net.minecraft.world.item.ItemStack.class);
                final String tabWant = a.length > 2 ? a[2].replace('_', ' ').toLowerCase(Locale.ROOT) : "";
                final String ingWant = a.length > 3 ? a[3].toLowerCase(Locale.ROOT) : null;
                Object chosen = null;
                final StringBuilder b = new StringBuilder("RECIPES " + a[1] + " " + views.size() + ":");
                for (int i = 0; i < views.size(); i++) {
                    final Object v = views.get(i);
                    final String title = String.valueOf(v.getClass().getMethod("title").invoke(v));
                    final StringBuilder ings = new StringBuilder();
                    boolean has = ingWant == null;
                    for (Object slot : (List<?>) v.getClass().getMethod("slots").invoke(v)) {
                        if ((Boolean) slot.getClass().getMethod("output").invoke(slot)) continue;
                        final List<?> opts = (List<?>) slot.getClass().getMethod("options").invoke(slot);
                        if (opts.isEmpty()) continue;
                        final net.minecraft.world.item.ItemStack o = (net.minecraft.world.item.ItemStack) opts.get(0);
                        final String oid = String.valueOf(idOf.invoke(null, o));
                        ings.append(' ').append(o.getCount()).append('x').append(oid);
                        for (Object any : opts) {
                            if (ingWant != null && String.valueOf(idOf.invoke(null, any)).toLowerCase(Locale.ROOT).contains(ingWant)) has = true;
                        }
                    }
                    if (a[0].equals("recipes")) b.append(" [").append(i).append(' ').append(title).append(':').append(ings).append(']');
                    if (chosen == null && title.toLowerCase(Locale.ROOT).contains(tabWant) && has) chosen = v;
                }
                if (a[0].equals("recipes")) {
                    report(b.toString());
                } else if (chosen == null) {
                    errors++;
                    report("ERROR favor: no '" + tabWant + "' recipe" + (ingWant == null ? "" : " with " + ingWant) + " for " + a[1]);
                } else {
                    final Method key = Class.forName("codechicken.nei.client.RecipeBookmarks")
                            .getDeclaredMethod("key", Class.forName("codechicken.nei.recipe.RecipeView"));
                    key.setAccessible(true);
                    Class.forName("codechicken.nei.Favorites").getMethod("set", String.class, String.class).invoke(null, a[1], key.invoke(null, chosen));
                    report("favorite " + a[1] + " = " + chosen.getClass().getMethod("title").invoke(chosen));
                }
            }
            case "bookmarkrecipes" -> { // bookmarkrecipes <n> [mod id]: bookmarks the first n items (of that mod) that have a recipe, each with its first recipe
                final int want = Integer.parseInt(a[1]);
                final String mod = a.length > 2 ? a[2] : null;
                final Method key = Class.forName("codechicken.nei.client.RecipeBookmarks")
                        .getDeclaredMethod("key", Class.forName("codechicken.nei.recipe.RecipeView"));
                key.setAccessible(true);
                final Method recipesFor = Class.forName("codechicken.nei.recipe.RecipeIndex").getMethod("recipesFor", net.minecraft.world.item.Item.class);
                final Method toggle = Class.forName("codechicken.nei.Bookmarks").getMethod("toggle", String.class, String.class, boolean.class);
                int n = 0;
                for (Object entry : (List<?>) Class.forName("codechicken.nei.ItemList").getMethod("all").invoke(null)) {
                    if (n >= want) break;
                    if (mod != null && !mod.equals(entry.getClass().getField("modId").get(entry))) continue;
                    final net.minecraft.world.item.ItemStack st = (net.minecraft.world.item.ItemStack) entry.getClass().getField("stack").get(entry);
                    final List<?> views = (List<?>) recipesFor.invoke(null, st.getItem());
                    if (views.isEmpty()) continue;
                    toggle.invoke(null, entry.getClass().getField("registryId").get(entry), key.invoke(null, views.get(0)), false);
                    n++;
                }
                report("bookmarked " + n + " items with a recipe");
            }
            case "requires", "unless" -> {
                // requires <itemId> [modname text | !modname text]: without that item (its mod), or when its mod's name does
                // (not) contain the text, the rest of the section is skipped. unless <itemId>: skipped when the item exists.
                final Object found = Class.forName("codechicken.nei.ItemList").getMethod("find", String.class).invoke(null, a[1]);
                boolean ok = a[0].equals("unless") ? found == null : found != null;
                if (ok && found != null && a.length > 2) {
                    final String cond = line.substring(line.indexOf(a[1]) + a[1].length()).strip();
                    final boolean not = cond.startsWith("!");
                    final String text = (not ? cond.substring(1) : cond).toLowerCase(Locale.ROOT);
                    final String mod = String.valueOf(found.getClass().getField("modName").get(found)).toLowerCase(Locale.ROOT);
                    ok = mod.contains(text) != not;
                }
                if (!ok) {
                    int dropped = 0;
                    while (!queue.isEmpty() && !queue.peek().strip().startsWith("section ") && !queue.peek().strip().equals("quit")) {
                        queue.poll();
                        dropped++;
                    }
                    report("SKIP the rest of the section: " + line + " (" + dropped + " lines)");
                }
            }
            case "findslot" -> { // findslot [N:]<name>: move the mouse onto the recipe window's (Nth) slot showing that item (exact name, else containing)
                String want = line.substring(line.indexOf(' ') + 1).toLowerCase(Locale.ROOT);
                int nth = 1;
                final int colon = want.indexOf(':');
                if (colon > 0 && want.substring(0, colon).chars().allMatch(Character::isDigit)) {
                    nth = Integer.parseInt(want.substring(0, colon));
                    want = want.substring(colon + 1);
                }
                final int[] found = new int[1];
                final int[] at = slotAt(want, nth, found);
                final int page = (Integer) expect.path("NEIClientEvents.overlay.page");
                if (at != null) {
                    move(mc, at[0], at[1]);
                    report("SLOT '" + want + "' at " + (int) lastX + "," + (int) lastY);
                } else if (page + 1 < (Integer) expect.path("NEIClientEvents.overlay.pageCount()")) {
                    // fewer such slots on this page (a small window fits fewer recipes): count on on the next page
                    queue.addFirst("findslot " + (nth - found[0]) + ":" + want);
                    queue.addFirst("wait 10");
                    queue.addFirst("key right");
                    report("FINDSLOT " + found[0] + " of " + nth + " '" + want + "' on page " + (page + 1) + ", next page");
                } else {
                    errors++;
                    report("ERROR findslot: no '" + want + "' (" + nth + ") in the recipe window");
                }
            }
            case "scrolluntil" -> { // scrolluntil <path> <text> <max> [n]: scroll where the mouse is until the path's value contains the text
                final int max = Integer.parseInt(a[3]);
                final int done = a.length > 4 ? Integer.parseInt(a[4]) : 0;
                final String raw = String.valueOf(expect.value(a[1]));
                final String v = raw + " " + Expect.nameOf(raw); // an id also matches by its item's name
                if (v.toLowerCase(Locale.ROOT).contains(a[2].replace('_', ' ').toLowerCase(Locale.ROOT))) {
                    report("SCROLLED to " + a[2] + " after " + done);
                } else if (done >= max) {
                    errors++;
                    report("ERROR scrolluntil: " + a[1] + " never contained " + a[2] + " (last " + v + ")");
                } else {
                    queue.addFirst("scrolluntil " + a[1] + " " + a[2] + " " + max + " " + (done + 1));
                    queue.addFirst("wait 2");
                    queue.addFirst("scroll here -1");
                }
            }
            case "givechain" -> { // givechain <*text|@name|id> [n]: /give n (default 1) of the newest chain's ingredient that matches
                final String id = Expect.key(java.util.List.of(chainIds()), a[1]);
                queue.addFirst("cmd give @s " + id + " " + (a.length > 2 ? a[2] : "1"));
            }
            case "givemissing" -> { // givemissing: /give every item the newest crafting chain's Shift view lists as missing (not fluids)
                final String all = String.valueOf(expect.value("chain"));
                final int at = all.indexOf("missing={");
                if (at < 0) throw new IllegalStateException("no crafting chain: " + all);
                final int m = all.indexOf("machine={");
                final String machine = m < 0 ? "" : all.substring(m + 9, all.indexOf('}', m));
                final String missingPart = all.substring(at + 9, all.indexOf('}', at));
                final String body = machine.isEmpty() ? missingPart : missingPart.isEmpty() ? machine : missingPart + ", " + machine;
                final int t = all.indexOf("tools=[");
                final List<String> tools = t < 0 ? List.of() : List.of(all.substring(t + 7, all.indexOf(']', t)).split(", "));
                int given = 0;
                for (String part : body.isEmpty() ? new String[0] : body.split(", ")) {
                    final int eq = part.lastIndexOf('=');
                    final String id = part.substring(0, eq);
                    long n = Long.parseLong(part.substring(eq + 1));
                    if (tools.contains(id)) n = 1; // a tool's number is the durability the chain takes: one tool
                    if (id.startsWith("notenoughitems:fluid/") || id.indexOf('~') >= 0) {
                        report("not given (fluid or subtype): " + part);
                        continue;
                    }
                    queue.addFirst("wait 2");
                    queue.addFirst("cmd give @s " + id + " " + n);
                    given++;
                }
                report("giving " + given + " missing items");
            }
            case "waituntil" -> { // waituntil <path> <text> <max checks>: waits (5 ticks a check) until the path's value contains the text
                final int max = Integer.parseInt(a[3]);
                final int done = a.length > 4 ? Integer.parseInt(a[4]) : 0;
                final String v = String.valueOf(expect.value(a[1]));
                if (v.toLowerCase(Locale.ROOT).contains(a[2].toLowerCase(Locale.ROOT))) {
                    report("WAITED for " + a[1] + " " + a[2] + ": " + done * 5 + " ticks");
                } else if (done >= max) {
                    report("gave up waiting for " + a[1] + " " + a[2] + " (last " + v + ")");
                } else {
                    queue.addFirst("waituntil " + a[1] + " " + a[2] + " " + max + " " + (done + 1));
                    queue.addFirst("wait 5");
                }
            }
            case "listitems" -> { // listitems [max]: logs the item list's filtered entries (name and id)
                final int max = a.length > 1 ? Integer.parseInt(a[1]) : 40;
                final List<?> list = (List<?>) expect.path("ItemList.getFiltered()");
                final StringBuilder b = new StringBuilder("ITEMS " + list.size() + ":");
                for (int i = 0; i < Math.min(max, list.size()); i++) {
                    final Object e = list.get(i);
                    b.append(" [").append(e.getClass().getField("name").get(e)).append(" = ")
                            .append(e.getClass().getField("registryId").get(e)).append(']');
                }
                report(b.toString());
            }
            case "tab" -> { // tab <text>: the recipe window's tab whose title contains the text
                final String want = line.substring(line.indexOf(' ') + 1).toLowerCase(Locale.ROOT);
                final Object overlay = expect.path("NEIClientEvents.overlay");
                if (overlay == null) throw new IllegalStateException("no recipe window");
                final List<?> groups = (List<?>) expect.path("NEIClientEvents.overlay.groups");
                int found = -1;
                for (int i = 0; i < groups.size() && found < 0; i++) {
                    final Object g = groups.get(i);
                    final Method titleOf = g.getClass().getDeclaredMethod("title");
                    titleOf.setAccessible(true);
                    final String title = String.valueOf(titleOf.invoke(g));
                    if (title.toLowerCase(Locale.ROOT).contains(want)) found = i;
                }
                if (found < 0) {
                    errors++;
                    report("ERROR tab: no '" + want + "' tab");
                } else {
                    final Method select = overlay.getClass().getDeclaredMethod("selectTab", int.class);
                    select.setAccessible(true);
                    select.invoke(overlay, found);
                    report("tab " + found + " '" + want + "'");
                }
            }
            case "quit" -> {
                report("QUIT errors=" + errors + " passes=" + passes + " fails=" + fails);
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

    /** The ids the newest chain's ingredient list names. */
    private String[] chainIds() throws Exception {
        final String all = String.valueOf(expect.value("chain"));
        final int at = all.indexOf(" in={");
        if (at < 0) return new String[0];
        final String body = all.substring(at + 5, all.indexOf('}', at));
        final String[] parts = body.isEmpty() ? new String[0] : body.split(", ");
        for (int i = 0; i < parts.length; i++) parts[i] = parts[i].substring(0, parts[i].lastIndexOf('='));
        return parts;
    }

    /**
     * The middle of the recipe window's nth slot showing an item named {@code want} (exact names first, then names
     * containing it), or null with {@code found[0]} = how many such slots the page has. Recipes NEI draws keep their slot
     * areas in slotHits. Recipes drawn by their JEI layout keep none, so each layout is asked which item lies under points
     * of its area; there one slot per recipe counts.
     */
    private int[] slotAt(String want, int nth, int[] found) throws Exception {
        final Object overlay = expect.path("NEIClientEvents.overlay");
        final int left = (Integer) reflectField(overlay.getClass(), overlay, "left");
        final int windowWidth = (Integer) reflectField(overlay.getClass(), null, "W");
        final Class<?> drawn = Class.forName("codechicken.nei.recipe.RecipeView$Drawn");
        final Method itemAt = drawn.getMethod("itemAt", int.class, int.class);
        for (boolean exact : new boolean[] {true, false}) {
            int n = 0;
            for (Object h : (List<?>) expect.path("NEIClientEvents.overlay.slotHits")) {
                final int[] v = new int[4];
                final String[] names = {"x", "y", "w", "h"};
                for (int i = 0; i < 4; i++) v[i] = (Integer) reflectCall(h, names[i]);
                if (v[2] <= 1) continue; // the mouse marker of a JEI layout, not a slot area
                if (named((net.minecraft.world.item.ItemStack) reflectCall(h, "stack"), want, exact) && ++n == nth) {
                    return new int[] {v[0] + Math.max(1, v[2] / 2), v[1] + Math.max(1, v[3] / 2)};
                }
            }
            for (Object heart : (List<?>) expect.path("NEIClientEvents.overlay.heartHits")) {
                final Object d = reflectCall(reflectCall(heart, "view"), "drawn");
                if (d == null) continue;
                final int w = (Integer) drawn.getMethod("width").invoke(d);
                final int hgt = (Integer) drawn.getMethod("height").invoke(d);
                final int x0 = Math.max(left + 2, left + (windowWidth - w) / 2);
                final int y0 = (Integer) reflectCall(heart, "y") - 2; // the heart sits 2 below the recipe's top
                scan:
                for (int py = y0; py < y0 + hgt; py += 2) {
                    for (int px = x0; px < x0 + w; px += 2) {
                        final net.minecraft.world.item.ItemStack s = (net.minecraft.world.item.ItemStack) itemAt.invoke(d, px, py);
                        if (s.isEmpty() || !named(s, want, exact)) continue;
                        if (++n < nth) break scan; // one slot per recipe
                        // the first point found is near the slot's top-left corner: aim at its middle if that is the same slot
                        final net.minecraft.world.item.ItemStack mid = (net.minecraft.world.item.ItemStack) itemAt.invoke(d, px + 7, py + 7);
                        return !mid.isEmpty() && named(mid, want, exact) ? new int[] {px + 7, py + 7} : new int[] {px + 1, py + 1};
                    }
                }
            }
            found[0] = n;
            if (n > 0) return null; // exact names win over names that only contain the text
        }
        return null;
    }

    private static boolean named(net.minecraft.world.item.ItemStack stack, String want, boolean exact) {
        final String name = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
        return exact ? name.equals(want) : name.contains(want);
    }

    /** Calls a no-argument method by name, private ones included (records' accessors, NEI's private types). */
    private static Object reflectCall(Object o, String name) throws ReflectiveOperationException {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try {
                final Method m = c.getDeclaredMethod(name);
                m.setAccessible(true);
                return m.invoke(o);
            } catch (NoSuchMethodException e) {
                // look in the superclass
            }
        }
        final Method m = o.getClass().getMethod(name);
        m.setAccessible(true);
        return m.invoke(o);
    }

    private static Object reflectField(Class<?> c, Object o, String name) throws ReflectiveOperationException {
        final java.lang.reflect.Field f = c.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(o);
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
            case "KP0" -> GLFW.GLFW_KEY_KP_0;
            case "F7" -> GLFW.GLFW_KEY_F7;
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
