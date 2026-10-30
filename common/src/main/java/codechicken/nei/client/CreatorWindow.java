package codechicken.nei.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import codechicken.nei.NEIActions;
import codechicken.nei.item.GiveCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * NEI's enchantment modifier ({@code GuiEnchantmentModifier}) and potion creator ({@code GuiPotionCreator}) as windows over
 * the inventory.
 *
 * Enchantments: opened over an item with the enchant key; one row per enchantment that fits the item, with a level
 * (0 = none, up to 255 so over-max levels work too). Potions: one row per effect with a level and a duration; the
 * button above the list picks potion, splash, lingering or tipped arrow. The result is a new item given with
 * {@code /give} (see {@link GiveCommands}), so cheats or operator permission are needed.
 *
 * Differences from NEI: NEI edits the item in a slot of its own screen; here the result is a new stack, so the item's
 * other data (name, damage) is not copied. No custom potion name or colour.
 */
final class CreatorWindow implements ModalWindow {

    enum Kind {
        ENCHANT, POTION
    }

    private static final int ROW = 20;
    private static final int BTN = 20;
    private static final int MAX_LEVEL = 255;
    private static final String[] POTION_ITEMS = { "minecraft:potion", "minecraft:splash_potion", "minecraft:lingering_potion",
            "minecraft:tipped_arrow" };
    private static final String[] POTION_NAMES = { "Potion", "Splash Potion", "Lingering Potion", "Tipped Arrow" };

    private static final class Row {

        final String id;
        final Component name;
        int level;
        int seconds = 180;

        Row(String id, Component name, int level) {
            this.id = id;
            this.name = name;
            this.level = level;
        }
    }

    private final Screen screen;
    private final Kind kind;
    private final ItemStack base;
    private final List<Row> rows = new ArrayList<>();
    private int potionType;
    private int scroll;
    private boolean close;
    private int sw;
    private int sh;

    CreatorWindow(Screen screen, Kind kind, ItemStack base) {
        this.screen = screen;
        this.kind = kind;
        this.base = base;

        final var level = Minecraft.getInstance().level;
        if (level == null) return;

        if (kind == Kind.ENCHANT) {
            // ItemEnchantments#getLevel(Holder)
            final ItemEnchantments current = base.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
            level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).listElements().forEach(ref -> {
                final Enchantment e = ref.value();
                final String id = ref.getRegisteredName();
                if (!e.isSupportedItem(base) && current.getLevel(ref) == 0) return;
                final int colon = id.indexOf(':');
                rows.add(new Row(id, Component.translatable("enchantment." + id.substring(0, colon) + "."
                        + id.substring(colon + 1)), current.getLevel(ref)));
            });
        } else {
            for (MobEffect effect : BuiltInRegistries.MOB_EFFECT) {
                rows.add(new Row(BuiltInRegistries.MOB_EFFECT.getKey(effect).toString(), effect.getDisplayName(), 0));
            }
        }
        rows.sort(Comparator.comparing(r -> r.name.getString()));
    }

    @Override
    public Screen screen() {
        return screen;
    }

    @Override
    public boolean wantsClose() {
        return close;
    }

    @Override
    public EditBox typingBox() {
        return null;
    }

    @Override
    public void escape() {
        close = true;
    }

    // ---- geometry ----

    private int ww() {
        return Math.min(kind == Kind.POTION ? 340 : 280, sw - 12);
    }

    private int wx() {
        return (sw - ww()) / 2;
    }

    private static final int WY = 14;

    private int wh() {
        return sh - 28;
    }

    private int top() {
        return WY + 48;
    }

    private int bottom() {
        return WY + wh() - 30;
    }

    private int visible() {
        return Math.max(1, (bottom() - top()) / ROW);
    }

    private int counterX(int which) {
        // enchant: the level counter at the right edge; potion: level, then seconds
        final int right = wx() + ww() - 8 - 4;
        return which == 0 && kind == Kind.POTION ? right - 2 * 62 - 4 : right - 62;
    }

    private boolean canGive() {
        for (Row r : rows) if (r.level > 0) return Perms.canUseCommands();
        return false;
    }

    private String command() {
        if (kind == Kind.ENCHANT) {
            final Map<String, Integer> levels = new LinkedHashMap<>();
            for (Row r : rows) levels.put(r.id, r.level);
            return GiveCommands.enchanted(codechicken.nei.item.FluidDisplay.idOf(base.getItem()), levels);
        }
        final List<GiveCommands.Effect> effects = new ArrayList<>();
        for (Row r : rows) if (r.level > 0) effects.add(new GiveCommands.Effect(r.id, r.level - 1, r.seconds));
        return GiveCommands.potion(POTION_ITEMS[potionType], effects);
    }

    // ---- drawing ----

    private void counter(GuiGraphics g, Font font, int x, int y, String text, boolean over, boolean dim) {
        Draw.button(g, x, y + 1, 14, 18, over);
        Gfx.text(g, font, Component.literal("-"), x + 5, y + 7, 0xFFFFFFFF, true);
        Gfx.text(g, font, Component.literal(text), x + 14 + (34 - font.width(text)) / 2, y + 6, dim ? 0xFF808080 : 0xFF202020, false);
        Draw.button(g, x + 48, y + 1, 14, 18, over);
        Gfx.text(g, font, Component.literal("+"), x + 48 + 4, y + 7, 0xFFFFFFFF, true);
    }

    private boolean overMinus(double mx, double my, int x, int y) {
        return Draw.in(mx, my, x, y + 1, 14, 18);
    }

    private boolean overPlus(double mx, double my, int x, int y) {
        return Draw.in(mx, my, x + 48, y + 1, 14, 18);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, int sw, int sh) {
        this.sw = sw;
        this.sh = sh;
        final Font font = Minecraft.getInstance().font;
        Gfx.fill(g, 0, 0, sw, sh, 0x90000000);
        Draw.panel(g, wx(), WY, ww(), wh());

        final String title = kind == Kind.ENCHANT ? "Enchantment Modifier" : "Potion Creator";
        Gfx.text(g, font, Component.literal(title), wx() + (ww() - font.width(title)) / 2, WY + 7, 0xFF404040, false);

        if (kind == Kind.ENCHANT) {
            Draw.stack(g, base, wx() + 8, WY + 22);
            Gfx.text(g, font, base.getHoverName(), wx() + 30, WY + 27, 0xFF202020, false);
        } else {
            final boolean over = Draw.in(mx, my, wx() + 8, WY + 22, 130, BTN);
            Draw.button(g, wx() + 8, WY + 22, 130, BTN, over);
            Gfx.text(g, font, Component.literal(POTION_NAMES[potionType]), wx() + 8 + (130 - font.width(POTION_NAMES[potionType])) / 2,
                    WY + 28, 0xFFFFFFFF, true);
            Gfx.text(g, font, Component.literal("Level"), counterX(0) + 18, WY + 30, 0xFF404040, false);
            Gfx.text(g, font, Component.literal("Seconds"), counterX(1) + 12, WY + 30, 0xFF404040, false);
        }

        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - visible())));
        if (rows.isEmpty()) Gfx.text(g, font, Component.literal("Nothing fits this item"), wx() + 10, top() + 4, 0xFF606060, false);
        for (int i = 0; i < visible() && scroll + i < rows.size(); i++) {
            final Row r = rows.get(scroll + i);
            final int y = top() + i * ROW;
            Gfx.text(g, font, r.name, wx() + 10, y + 6, r.level > 0 ? 0xFF105010 : 0xFF404040, false);
            final boolean overRow = Draw.in(mx, my, counterX(0), y, 62, ROW);
            counter(g, font, counterX(0), y, r.level == 0 ? "-" : Integer.toString(r.level), overRow, r.level == 0);
            if (kind == Kind.POTION) {
                final boolean overSec = Draw.in(mx, my, counterX(1), y, 62, ROW);
                counter(g, font, counterX(1), y, Integer.toString(r.seconds), overSec, r.level == 0);
            }
        }

        final int by = WY + wh() - 26;
        final int bx = wx() + ww() / 2;
        final boolean give = canGive();
        Draw.button(g, bx - 100, by, 98, BTN, Draw.in(mx, my, bx - 100, by, 98, BTN));
        Gfx.text(g, font, Component.literal("Close"), bx - 100 + (98 - font.width("Close")) / 2, by + 6, 0xFFFFFFFF, true);
        Draw.button(g, bx + 2, by, 98, BTN, give && Draw.in(mx, my, bx + 2, by, 98, BTN));
        Gfx.text(g, font, Component.literal("Give"), bx + 2 + (98 - font.width("Give")) / 2, by + 6, give ? 0xFFFFFFFF : 0xFF777777, true);
        if (Draw.in(mx, my, bx + 2, by, 98, BTN) && !give) {
            ItemTooltip.requestPlain(List.of(Component.literal(rows.stream().anyMatch(r -> r.level > 0)
                    ? "Needs cheats or operator permission" : "Choose at least one level first")));
        }
    }

    // ---- input ----

    private void step(Row r, boolean seconds, int sign, boolean shift) {
        if (seconds) {
            r.seconds = Math.max(1, Math.min(99_999, r.seconds + sign * (shift ? 600 : 30)));
        } else {
            final int before = r.level;
            r.level = Math.max(0, Math.min(MAX_LEVEL, r.level + sign * (shift ? 5 : 1)));
            if (before == 0 && r.level > 0 && kind == Kind.POTION && r.seconds < 1) r.seconds = 180;
        }
    }

    @Override
    public void click(double mx, double my, int button, boolean ctrl, boolean shift) {
        if (!Draw.in(mx, my, wx(), WY, ww(), wh())) {
            close = true;
            return;
        }
        final int by = WY + wh() - 26;
        final int bx = wx() + ww() / 2;
        if (button != 0 && button != 1) return;

        if (Draw.in(mx, my, bx - 100, by, 98, BTN)) {
            close = true;
            return;
        }
        if (Draw.in(mx, my, bx + 2, by, 98, BTN)) {
            if (canGive()) NEIActions.command(command());
            return;
        }
        if (kind == Kind.POTION && Draw.in(mx, my, wx() + 8, WY + 22, 130, BTN)) {
            potionType = (potionType + (button == 1 ? POTION_ITEMS.length - 1 : 1)) % POTION_ITEMS.length;
            return;
        }
        for (int i = 0; i < visible() && scroll + i < rows.size(); i++) {
            final Row r = rows.get(scroll + i);
            final int y = top() + i * ROW;
            for (int which = 0; which < (kind == Kind.POTION ? 2 : 1); which++) {
                final int x = counterX(which);
                final int sign = overPlus(mx, my, x, y) ? 1 : overMinus(mx, my, x, y) ? -1 : 0;
                if (sign != 0) step(r, kind == Kind.POTION && which == 1, sign, shift);
            }
        }
    }

    @Override
    public void scrolled(double mx, double my, double dy) {
        scroll = Math.max(0, scroll + (dy > 0 ? -1 : 1));
    }
}
