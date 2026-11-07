package codechicken.nei.client;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import codechicken.nei.ItemList;
import codechicken.nei.NotEnoughItems;
import codechicken.nei.item.ItemEntry;
import codechicken.nei.recipe.RecipeIndex;
import codechicken.nei.recipe.RecipeView;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;

/**
 * NEI's Tools > Data Dumps ({@code DataDumper} and its kinds): each Dump button writes one list as a CSV file to
 * {@code dumps/} in the game folder and says so in the chat. The columns follow NEI's where today's game still has the
 * thing (numeric ids are the registry's own order; there are no free ids to list any more).
 */
public final class Dumps {

    private Dumps() {}

    public static void dump(String name, String fileName, int mode) {
        final Minecraft mc = Minecraft.getInstance();
        final List<String[]> rows = new ArrayList<>();
        try {
            if (name.equals("Item Panel") && mode == 1) {
                itemPanelNbt(mc.gameDirectory.toPath().resolve("dumps").resolve(fileName));
                if (mc.player != null) mc.player.sendSystemMessage(Component.literal("Item Panel contents were dumped to dumps/" + fileName));
                return;
            }
            switch (name) {
                case "Item" -> items(rows);
                case "Block" -> blocks(rows);
                case "Potion" -> potions(rows);
                case "Enchantment" -> enchantments(rows);
                case "Biome" -> biomes(rows);
                case "Item Panel" -> itemPanel(rows);
                default -> handlers(rows);
            }
            final Path file = mc.gameDirectory.toPath().resolve("dumps").resolve(fileName);
            Files.createDirectories(file.getParent());
            final boolean json = name.equals("Item Panel") && mode == 2 || name.equals("NEI Handlers") && mode == 1;
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(file, StandardCharsets.UTF_8))) {
                if (json) w.println(name.equals("Item Panel") ? itemPanelJson() : json(rows));
                else for (String[] row : rows) w.println(concat(row));
            }
            // NEI's nei.options.tools.dump.dumped / itempanel.dumped / handlers.dumped
            final String message = switch (name) {
                case "Item Panel" -> "Item Panel contents were dumped to dumps/" + fileName;
                case "NEI Handlers" -> "handlers were dumped to dumps/" + fileName;
                default -> name + " IDs dumped to dumps/" + fileName;
            };
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal(message));
        } catch (IOException | RuntimeException e) {
            NotEnoughItems.LOGGER.error("Error dumping {}", name, e);
            if (mc.player != null) mc.player.sendSystemMessage(Component.literal("Could not dump " + name + ": " + e));
        }
    }

    /** NEI's Json mode of a table dump: one object per row, the header's names as keys. */
    private static String json(List<String[]> rows) {
        final com.google.gson.JsonArray out = new com.google.gson.JsonArray();
        final String[] header = rows.get(0);
        for (int r = 1; r < rows.size(); r++) {
            final com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            for (int c = 0; c < header.length && c < rows.get(r).length; c++) o.addProperty(header[c], rows.get(r)[c]);
            out.add(o);
        }
        return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(out);
    }

    /** NEI's ItemPanelDumper Json mode: every listed stack as the game saves it. */
    private static String itemPanelJson() {
        final com.google.gson.JsonArray out = new com.google.gson.JsonArray();
        final var level = Minecraft.getInstance().level;
        if (level != null) {
            for (ItemEntry entry : ItemList.getFiltered()) { // 1.20.1: the stack's NBT, as JSON
                out.add(net.minecraft.nbt.NbtOps.INSTANCE.convertTo(com.mojang.serialization.JsonOps.INSTANCE,
                        codechicken.nei.item.Stacks.withCount(entry.stack, 1).save(new net.minecraft.nbt.CompoundTag())));
            }
        }
        return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(out);
    }

    /** NEI's ItemPanelDumper NBT mode: a compressed NBT file with a list of every listed stack. */
    private static void itemPanelNbt(Path file) throws IOException {
        final net.minecraft.nbt.ListTag list = new net.minecraft.nbt.ListTag();
        final var level = Minecraft.getInstance().level;
        if (level != null) {
            for (ItemEntry entry : ItemList.getFiltered()) {
                list.add(codechicken.nei.item.Stacks.withCount(entry.stack, 1).save(new net.minecraft.nbt.CompoundTag()));
            }
        }
        final net.minecraft.nbt.CompoundTag root = new net.minecraft.nbt.CompoundTag();
        root.put("list", list);
        Files.createDirectories(file.getParent());
        net.minecraft.nbt.NbtIo.writeCompressed(root, file.toFile());
    }

    /** NEI's {@code DataDumper#concat}: comma separated, quoted where a value holds a comma or a quote. */
    static String concat(String[] row) {
        final StringBuilder sb = new StringBuilder();
        for (String s : row) {
            if (sb.length() > 0) sb.append(',');
            if (s == null) s = "null";
            if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0) s = '"' + s.replace("\"", "\"\"") + '"';
            sb.append(s);
        }
        return sb.toString();
    }

    private static String mod(String namespace) {
        return NotEnoughItems.platform().modName(namespace);
    }

    private static void items(List<String[]> rows) {
        rows.add(new String[] { "Name", "ID", "Has Block", "Mod", "Class", "Display Name" });
        for (var item : BuiltInRegistries.ITEM) {
            final var key = BuiltInRegistries.ITEM.getKey(item);
            rows.add(new String[] { key.toString(), Integer.toString(BuiltInRegistries.ITEM.getId(item)),
                    Boolean.toString(item instanceof BlockItem), mod(key.getNamespace()), item.getClass().getName(),
                    item.getDefaultInstance().getHoverName().getString() });
        }
    }

    private static void blocks(List<String[]> rows) {
        rows.add(new String[] { "Name", "ID", "Has Item", "Mod", "Class", "Display Name" });
        for (var block : BuiltInRegistries.BLOCK) {
            final var key = BuiltInRegistries.BLOCK.getKey(block);
            final var item = block.asItem();
            final boolean hasItem = item != net.minecraft.world.item.Items.AIR;
            rows.add(new String[] { key.toString(), Integer.toString(BuiltInRegistries.BLOCK.getId(block)), Boolean.toString(hasItem),
                    mod(key.getNamespace()), block.getClass().getName(), hasItem ? new ItemStack(item).getHoverName().getString() : "null" });
        }
    }

    private static void potions(List<String[]> rows) {
        rows.add(new String[] { "ID", "Unlocalised name", "Class" });
        for (var effect : BuiltInRegistries.MOB_EFFECT) {
            rows.add(new String[] { BuiltInRegistries.MOB_EFFECT.getKey(effect).toString(), effect.getDescriptionId(),
                    effect.getClass().getName() });
        }
    }

    private static void enchantments(List<String[]> rows) {
        rows.add(new String[] { "ID", "Unlocalised name", "Min Level", "Max Level", "Class" });
        for (var ench : BuiltInRegistries.ENCHANTMENT) {
            rows.add(new String[] { BuiltInRegistries.ENCHANTMENT.getKey(ench).toString(), ench.getDescriptionId(),
                    Integer.toString(ench.getMinLevel()), Integer.toString(ench.getMaxLevel()), ench.getClass().getName() });
        }
    }

    private static void biomes(List<String[]> rows) {
        rows.add(new String[] { "ID", "Temperature", "Has Precipitation", "Class" });
        final var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (var e : level.registryAccess().registryOrThrow(Registries.BIOME).entrySet()) {
            final var biome = e.getValue();
            rows.add(new String[] { e.getKey().location().toString(), Float.toString(biome.getBaseTemperature()),
                    Boolean.toString(biome.hasPrecipitation()), biome.getClass().getName() });
        }
    }

    /** NEI's ItemPanelDumper (CSV mode): what the item list shows now. */
    private static void itemPanel(List<String[]> rows) {
        rows.add(new String[] { "Item Name", "Item ID", "NBT", "Has NBT", "Display Name" });
        for (ItemEntry entry : ItemList.getFiltered()) {
            final ItemStack stack = entry.stack;
            rows.add(new String[] { entry.registryId, Integer.toString(BuiltInRegistries.ITEM.getId(stack.getItem())),
                    stack.hasTag() ? stack.getTag().toString() : "",
                    Boolean.toString(stack.hasTag()), stack.getHoverName().getString() });
        }
    }

    /** NEI's HandlerDumper (CSV mode): the recipe types (NEI's handlers) the recipe window knows. */
    private static void handlers(List<String[]> rows) {
        rows.add(new String[] { "Handler Recipe Name", "Handler Class", "Overlay ResourceLocation", "Mod DisplayName", "ItemStack", "Recipes" });
        for (var e : RecipeIndex.types().entrySet()) {
            final RecipeView view = e.getValue().getKey();
            ItemStack icon = view.station();
            if (icon.isEmpty() && !view.stations().isEmpty()) icon = view.stations().get(0);
            final String source = view.drawn() != null ? view.drawn().getClass().getName() : "minecraft";
            rows.add(new String[] { e.getKey(), source, view.crafting() != RecipeView.Crafting.NONE ? "crafting" : "",
                    icon.isEmpty() ? "" : ItemTooltip.modName(icon), icon.isEmpty() ? "" : codechicken.nei.item.FluidDisplay.idOf(icon),
                    Integer.toString(e.getValue().getValue()) });
        }
    }
}
