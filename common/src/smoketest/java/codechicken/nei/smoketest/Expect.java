package codechicken.nei.smoketest;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

/**
 * The checks of NEI's test driver: {@code expect <value> <op> <expected>} and {@code call <path>}.
 *
 * <p>A value is either a game probe or a path into NEI's own classes (their names are never obfuscated):
 * <ul>
 * <li>{@code carried} / {@code carriedcount}: the stack on the cursor ({@code minecraft:stone} or {@code empty}) / its size</li>
 * <li>{@code inv:<item id>}: how many of the item the player has (main inventory and hotbar)</li>
 * <li>{@code screen}: the open screen's class name ({@code none}); {@code screenkind}: creative, inventory, chest, merchant,
 * crafting, container, other, none, or the simple name of an NEI screen; {@code searchfocused}: NEI's search has the focus</li>
 * <li>{@code tooltip}: the text NEI drew as its tooltip in the last frame (lines joined by {@code |}, empty for none)</li>
 * <li>{@code chat}: the chat and command lines logged since the last {@code chatmark}</li>
 * <li>{@code file:<path>}: whether that file exists (relative to the game directory)</li>
 * <li>{@code clipboard}, {@code raining}, {@code gamemode}, {@code health}, {@code daytime} (0 - 23999)</li>
 * <li>a path such as {@code NEIClientEvents.overlay}, {@code Bookmarks.grid().size()}, {@code NEIConfig.CHEAT_ENABLED.get()}:
 * dot-separated fields and methods (with literal arguments) starting at a class of {@code codechicken.nei} or its
 * sub-packages; private members are read too</li>
 * </ul>
 * Ops: {@code ==}, {@code !=}, {@code contains}, {@code !contains}, {@code >}, {@code <}, {@code >=}, {@code <=} (numbers).
 */
final class Expect {

    private static final String[] PACKAGES = { "codechicken.nei.", "codechicken.nei.client.", "codechicken.nei.item.",
            "codechicken.nei.bookmark.", "codechicken.nei.recipe.", "codechicken.nei.search.", "codechicken.nei.api.",
            "codechicken.nei.jei." };

    private final Supplier<Screen> screen;
    private long chatMark;

    Expect(Supplier<Screen> screen) {
        this.screen = screen;
    }

    /** Remembers where the game log ends: {@code chat} reads what was logged after this. */
    void chatMark() throws Exception {
        final Path log = Path.of("logs", "latest.log");
        chatMark = Files.exists(log) ? Files.size(log) : 0;
    }

    /**
     * The newest crafting chain group's tooltip totals ({@code BookmarkPanel.chainTotals}): {@code chain} is all of them,
     * {@code chainin:<id>} an ingredient (a tool: its durability use), {@code chainout:<id>} a result,
     * {@code chaincraft:<id>} / {@code chainmissing:<id>} / {@code chainhave:<id>} the Shift view, {@code chaintool:<id>}
     * whether it counts as a tool, {@code chaingroups} how many crafting groups there are. Missing: 0.
     */
    @SuppressWarnings("unchecked")
    private static String chain(String what) throws Exception {
        // the driver is not compiled against NEI: everything by reflection
        final Object grid = Class.forName("codechicken.nei.Bookmarks").getMethod("grid").invoke(null);
        final java.util.Map<Integer, Object> groups = (java.util.Map<Integer, Object>) grid.getClass().getField("groups").get(grid);
        int newest = -1, count = 0;
        for (java.util.Map.Entry<Integer, Object> e : groups.entrySet()) {
            if (!e.getValue().getClass().getField("crafting").getBoolean(e.getValue())) continue;
            count++;
            newest = Math.max(newest, e.getKey());
        }
        if (what.equals("chaingroups")) return String.valueOf(count);
        if (newest < 0) return "no crafting group";
        final boolean shift = what.startsWith("chaincraft:") || what.startsWith("chainmissing:") || what.startsWith("chainhave:")
                || what.equals("chain");
        final Class<?> panel = Class.forName("codechicken.nei.client.BookmarkPanel");
        final Object t = panel.getMethod("chainTotals", grid.getClass(), int.class, boolean.class).invoke(null, grid, newest, shift);
        final java.util.function.Function<String, java.util.Map<String, long[]>> part = name -> {
            try {
                return (java.util.Map<String, long[]>) t.getClass().getMethod(name).invoke(t);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException(ex);
            }
        };
        final java.util.Set<String> tools = (java.util.Set<String>) t.getClass().getMethod("tools").invoke(t);
        if (what.equals("chain")) {
            return "group " + newest + " out=" + fmt(part.apply("outputs")) + " in=" + fmt(part.apply("inputs")) + " crafts="
                    + fmt(part.apply("crafts")) + " missing=" + fmt(part.apply("missing")) + " have=" + fmt(part.apply("available"))
                    + " tools=" + tools + " machine=" + fmt(machineMade(grid, newest, part.apply("crafts")));
        }
        final int colon = what.indexOf(':');
        final String id = what.substring(colon + 1);
        final String name = switch (what.substring(0, colon)) {
            case "chainin" -> "inputs";
            case "chainout" -> "outputs";
            case "chaincraft" -> "crafts";
            case "chainmissing" -> "missing";
            case "chainhave" -> "available";
            case "chaintool" -> null;
            case "chainuses" -> "uses";
            default -> throw new IllegalArgumentException("unknown chain probe " + what);
        };
        if (name == null) return String.valueOf(tools.contains(key(tools, id)));
        if (name.equals("uses")) { // how many crafts use a tool: its durability in the list over what one craft takes
            final java.util.Map<String, long[]> in = part.apply("inputs");
            final String k = key(in.keySet(), id);
            final long[] v = in.get(k);
            final int wear = (Integer) panel.getMethod("toolWear", String.class).invoke(null, k);
            return String.valueOf(v == null || wear <= 0 ? 0 : v[0] / wear);
        }
        final java.util.Map<String, long[]> map = part.apply(name);
        final long[] v = map.get(key(map.keySet(), id));
        return String.valueOf(v == null ? 0 : v[0]);
    }

    /**
     * An id as a chain probe names it: the id itself, {@code *text} the first id containing the text, {@code @name} the
     * first whose item name is that name (underscores for spaces). Ids differ between versions (subtypes, tool materials).
     */
    static String key(java.util.Collection<String> ids, String want) throws ReflectiveOperationException {
        if (want.startsWith("*")) {
            for (String k : ids) if (k.contains(want.substring(1))) return k;
            return want;
        }
        if (want.startsWith("@")) {
            final String name = want.substring(1).replace('_', ' ');
            for (String k : ids) if (name.equalsIgnoreCase(nameOf(k))) return k;
            return want;
        }
        return want;
    }

    /** The item list name of an id, or "". */
    static String nameOf(String id) throws ReflectiveOperationException {
        final Object e = Class.forName("codechicken.nei.ItemList").getMethod("find", String.class).invoke(null, id);
        return e == null ? "" : String.valueOf(e.getClass().getField("name").get(e));
    }

    /** What the chain makes in a machine or furnace (not on a crafting grid): autocrafting cannot, a test hands it over. */
    @SuppressWarnings("unchecked")
    private static java.util.Map<String, long[]> machineMade(Object grid, int groupId, java.util.Map<String, long[]> crafts)
            throws ReflectiveOperationException {
        final java.util.Map<String, long[]> out = new java.util.LinkedHashMap<>();
        final Method resolve = Class.forName("codechicken.nei.client.RecipeBookmarks").getDeclaredMethod("resolve", String.class, String.class);
        resolve.setAccessible(true);
        for (Object item : (java.util.List<Object>) grid.getClass().getField("items").get(grid)) {
            final Class<?> c = item.getClass();
            if (c.getField("groupId").getInt(item) != groupId || c.getField("type").getInt(item) != 1) continue;
            final String id = (String) c.getField("id").get(item);
            final String recipe = (String) c.getField("recipeId").get(item);
            final long[] used = crafts.get(id);
            if (recipe == null || used == null || out.containsKey(id)) continue;
            final Object view = resolve.invoke(null, id, recipe);
            if (view == null) continue;
            final Object kind = view.getClass().getMethod("crafting").invoke(view);
            if ("NONE".equals(String.valueOf(kind))) out.put(id, used.clone());
        }
        return out;
    }

    private static String fmt(java.util.Map<String, long[]> map) {
        final StringBuilder b = new StringBuilder("{");
        for (java.util.Map.Entry<String, long[]> e : map.entrySet()) {
            if (b.length() > 1) b.append(", ");
            b.append(e.getKey()).append('=').append(e.getValue()[0]);
        }
        return b.append('}').toString();
    }

    /** The value of a probe or path, as text ("null" for null). */
    String value(String what) throws Exception {
        final Minecraft mc = Minecraft.getInstance();
        switch (what) {
            case "carried" -> {
                final ItemStack c = carried(mc);
                return c.isEmpty() ? "empty" : BuiltInRegistries.ITEM.getKey(c.getItem()).toString();
            }
            case "carriedcount" -> {
                return String.valueOf(carried(mc).getCount());
            }
            case "screen" -> {
                final Screen s = screen.get();
                return s == null ? "none" : s.getClass().getName();
            }
            case "screenkind" -> { // the screen's kind, the same on every version and loader (class names are obfuscated on some)
                final Screen s = screen.get();
                if (s == null) return "none";
                if (s instanceof net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen) return "creative";
                if (s instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) return "inventory";
                if (s instanceof net.minecraft.client.gui.screens.inventory.MerchantScreen) return "merchant";
                if (s instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen) return "chest";
                if (s instanceof net.minecraft.client.gui.screens.inventory.CraftingScreen) return "crafting";
                if (s.getClass().getName().startsWith("codechicken.")) return s.getClass().getSimpleName();
                if (s instanceof AbstractContainerScreen<?>) return "container";
                return "other";
            }
            case "searchfocused" -> { // whether NEI's item search field has the focus
                final Field f = Class.forName("codechicken.nei.client.NEIClientEvents").getDeclaredField("searchBox");
                f.setAccessible(true);
                final Object box = f.get(null);
                return String.valueOf(box != null && ((net.minecraft.client.gui.components.EditBox) box).isFocused());
            }
            case "tooltip" -> {
                return tooltip();
            }
            case "hotkeys" -> { // the last hotkey help box NEI drew (Alt held): its title and "key: text" lines
                final Field f = Class.forName("codechicken.nei.client.HotkeyHelp").getDeclaredField("lastBox");
                f.setAccessible(true);
                return String.valueOf(f.get(null));
            }
            case "fps" -> { // frames in the last second
                return String.valueOf(mc.getFps());
            }
            case "chat" -> {
                return chat();
            }
            case "clipboard" -> {
                return mc.keyboardHandler.getClipboard();
            }
            case "raining" -> {
                return String.valueOf(mc.level != null && mc.level.isRaining());
            }
            case "gamemode" -> {
                return mc.gameMode == null ? "none" : mc.gameMode.getPlayerMode().getName();
            }
            case "health" -> {
                return String.valueOf((int) mc.player.getHealth());
            }
            case "daytime" -> {
                return String.valueOf(Math.floorMod(SmokeVersion.dayTime(mc), 24000L));
            }
            default -> {
                if (what.startsWith("inv:")) return String.valueOf(count(mc, what.substring(4)));
                if (what.startsWith("chain")) return chain(what);
                if (what.startsWith("standin:")) { // the item a fluid entry is drawn as (NTM - Continuum's droplet), or "none"
                    final Object e = Class.forName("codechicken.nei.ItemList").getMethod("find", String.class).invoke(null, what.substring(8));
                    if (e == null) return "no item";
                    final Class<?> fd = Class.forName("codechicken.nei.item.FluidDisplay");
                    final Object fluid = fd.getMethod("fluidOf", net.minecraft.world.item.ItemStack.class).invoke(null, e.getClass().getField("stack").get(e));
                    final net.minecraft.world.item.ItemStack st = (net.minecraft.world.item.ItemStack) fd
                            .getMethod("representative", net.minecraft.world.level.material.Fluid.class).invoke(null, fluid);
                    return st.isEmpty() ? "none" : String.valueOf(fd.getMethod("idOf", net.minecraft.world.item.ItemStack.class).invoke(null, st));
                }
                if (what.startsWith("file:")) return String.valueOf(Files.exists(Path.of(what.substring(5))));
                return String.valueOf(path(what));
            }
        }
    }

    /** {@code <value> <op> <expected...>}: null if it holds, else what was found. */
    String check(String value, String op, String expected) {
        final boolean ok = switch (op) {
            case "==" -> value.equals(expected);
            case "!=" -> !value.equals(expected);
            case "contains" -> value.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
            case "!contains" -> !value.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
            case ">", "<", ">=", "<=" -> {
                final double a = Double.parseDouble(value), b = Double.parseDouble(expected);
                yield switch (op) {
                    case ">" -> a > b;
                    case "<" -> a < b;
                    case ">=" -> a >= b;
                    default -> a <= b;
                };
            }
            default -> throw new IllegalArgumentException("unknown op " + op);
        };
        return ok ? null : value;
    }

    private ItemStack carried(Minecraft mc) {
        // 1.20.1's creative screen keeps the cursor stack in its own menu
        return (screen.get() instanceof AbstractContainerScreen<?> cs ? cs.getMenu() : mc.player.containerMenu).getCarried();
    }

    private static int count(Minecraft mc, String id) {
        int n = 0;
        final var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            final ItemStack st = inv.getItem(i);
            if (!st.isEmpty() && BuiltInRegistries.ITEM.getKey(st.getItem()).toString().equals(id)) n += st.getCount();
        }
        return n;
    }

    /** ItemTooltip's request of the last frame: the plain lines, or the requested stack's name. */
    private static String tooltip() throws Exception {
        final Class<?> c = Class.forName("codechicken.nei.client.ItemTooltip");
        final Field plain = c.getDeclaredField("pendingPlain");
        plain.setAccessible(true);
        final List<?> lines = (List<?>) plain.get(null);
        if (!lines.isEmpty()) {
            final List<String> out = new ArrayList<>();
            for (Object l : lines) out.add(((net.minecraft.network.chat.Component) l).getString());
            return String.join("|", out);
        }
        final Field pending = c.getDeclaredField("pending");
        pending.setAccessible(true);
        final ItemStack st = (ItemStack) pending.get(null);
        return st.isEmpty() ? "" : st.getHoverName().getString();
    }

    private String chat() throws Exception {
        final Path log = Path.of("logs", "latest.log");
        final byte[] all = Files.readAllBytes(log);
        final int from = (int) Math.min(chatMark, all.length);
        final StringBuilder sb = new StringBuilder();
        for (String line : new String(all, from, all.length - from, StandardCharsets.UTF_8).split("\n")) {
            final int i = line.indexOf("[CHAT] ");
            if (i >= 0) sb.append(line.substring(i + 7).strip()).append(" | ");
        }
        return sb.toString();
    }

    /** Runs a path for its effect ({@code call}). */
    Object path(String path) throws Exception {
        final List<String> parts = split(path);
        Object target = null;
        Class<?> type = null;
        int i = 0;
        for (String prefix : PACKAGES) {
            try {
                type = Class.forName(prefix + parts.get(0));
                break;
            } catch (ClassNotFoundException ignored) {
            }
        }
        if (type == null) throw new ClassNotFoundException(parts.get(0));
        for (i = 1; i < parts.size(); i++) {
            final String part = parts.get(i);
            final Class<?> owner = target == null ? type : target.getClass();
            final int open = part.indexOf('(');
            if (open < 0) {
                final Field f = field(owner, part);
                target = f.get(Modifier.isStatic(f.getModifiers()) ? null : target);
            } else {
                final String name = part.substring(0, open);
                final List<String> args = args(part.substring(open + 1, part.length() - 1));
                final Method m = method(owner, name, args.size());
                final Object[] values = new Object[args.size()];
                for (int a = 0; a < values.length; a++) values[a] = convert(args.get(a), m.getParameterTypes()[a]);
                target = m.invoke(Modifier.isStatic(m.getModifiers()) ? null : target, values);
            }
            if (target == null && i < parts.size() - 1) return null;
        }
        return target;
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            try {
                final Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(owner.getName() + "." + name);
    }

    private static Method method(Class<?> owner, String name, int args) throws NoSuchMethodException {
        // a JDK or game object (List.size(), ...): its public interface or class methods, never the hidden implementation's
        if (!owner.getName().startsWith("codechicken.")) {
            for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
                for (Class<?> in : allInterfaces(c)) {
                    for (Method m : in.getMethods()) if (m.getName().equals(name) && m.getParameterCount() == args) return m;
                }
                if (Modifier.isPublic(c.getModifiers())) {
                    for (Method m : c.getMethods()) if (m.getName().equals(name) && m.getParameterCount() == args) return m;
                }
            }
        }
        for (Class<?> c = owner; c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterCount() == args) {
                    m.setAccessible(true);
                    return m;
                }
            }
            for (Class<?> in : c.getInterfaces()) {
                for (Method m : in.getMethods()) {
                    if (m.getName().equals(name) && m.getParameterCount() == args) return m;
                }
            }
        }
        throw new NoSuchMethodException(owner.getName() + "." + name + "/" + args);
    }

    private static List<Class<?>> allInterfaces(Class<?> c) {
        final List<Class<?>> out = new ArrayList<>();
        for (Class<?> in : c.getInterfaces()) {
            if (Modifier.isPublic(in.getModifiers())) out.add(in);
            out.addAll(allInterfaces(in));
        }
        return out;
    }

    private static Object convert(String arg, Class<?> to) {
        if (arg.equals("null")) return null;
        if (to == boolean.class || to == Boolean.class) return Boolean.parseBoolean(arg);
        if (to == int.class || to == Integer.class) return Integer.parseInt(arg);
        if (to == long.class || to == Long.class) return Long.parseLong(arg);
        if (to == double.class || to == Double.class) return Double.parseDouble(arg);
        if (to == float.class || to == Float.class) return Float.parseFloat(arg);
        if (arg.startsWith("\"") && arg.endsWith("\"")) return arg.substring(1, arg.length() - 1);
        if (to == Object.class) {
            if (arg.equals("true") || arg.equals("false")) return Boolean.parseBoolean(arg);
            try {
                return Integer.parseInt(arg);
            } catch (NumberFormatException e) {
                return arg;
            }
        }
        return arg;
    }

    /** Splits {@code a.b(x, "y.z").c} at the dots outside parentheses and quotes. */
    private static List<String> split(String path) {
        final List<String> out = new ArrayList<>();
        int depth = 0, start = 0;
        boolean quoted = false;
        for (int i = 0; i < path.length(); i++) {
            final char ch = path.charAt(i);
            if (ch == '"') quoted = !quoted;
            else if (!quoted && ch == '(') depth++;
            else if (!quoted && ch == ')') depth--;
            else if (!quoted && depth == 0 && ch == '.') {
                out.add(path.substring(start, i));
                start = i + 1;
            }
        }
        out.add(path.substring(start));
        return out;
    }

    private static List<String> args(String inner) {
        final List<String> out = new ArrayList<>();
        if (inner.isBlank()) return out;
        boolean quoted = false;
        int start = 0;
        for (int i = 0; i < inner.length(); i++) {
            final char ch = inner.charAt(i);
            if (ch == '"') quoted = !quoted;
            else if (!quoted && ch == ',') {
                out.add(inner.substring(start, i).strip());
                start = i + 1;
            }
        }
        out.add(inner.substring(start).strip());
        return out;
    }
}
