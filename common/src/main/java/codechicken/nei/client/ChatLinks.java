package codechicken.nei.client;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import codechicken.nei.Bookmarks;
import codechicken.nei.ItemList;
import codechicken.nei.NotEnoughItems;
import codechicken.nei.bookmark.BookmarkGrid;
import codechicken.nei.bookmark.BookmarkPayload;
import codechicken.nei.recipe.RecipeView;
import codechicken.nei.item.ItemEntry;
import codechicken.nei.net.LinkToClient;
import codechicken.nei.net.LinkToServer;
import codechicken.nei.net.NEINetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * NEI's chat item links, client half. Sending: the server announces the shared bookmarks in chat and gives every client
 * with the mod the data (see {@link NEINetwork}). Receiving: the chat message's click runs {@code /nei_bookmark <id>}, a
 * client command that adds the cached data to the active bookmark page.
 *
 * The server needs the mod for sharing, as in NEI (its server mod does the same); without it nothing is sent.
 */
public final class ChatLinks {

    private static final Map<String, String> CACHE = new LinkedHashMap<>(64, 0.75f, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
            return size() > 64;
        }
    };

    private ChatLinks() {}

    /** Called by the network handler when a link was shared. */
    public static void cache(String id, String data) {
        synchronized (CACHE) {
            CACHE.put(id, data);
        }
    }

    private static void say(String text) {
        final var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.literal(text));
    }

    /** The client command {@code /nei_bookmark <id>}; works for any command source type, so each loader can register it. */
    public static <S> LiteralArgumentBuilder<S> command() {
        return LiteralArgumentBuilder.<S>literal("nei_bookmark")
                .then(RequiredArgumentBuilder.<S, String>argument("id", StringArgumentType.word()).executes(ctx -> {
                    final String data;
                    synchronized (CACHE) {
                        data = CACHE.get(StringArgumentType.getString(ctx, "id"));
                    }
                    if (data == null) {
                        say("This bookmark link is unknown or has expired");
                        return 0;
                    }
                    Bookmarks.importPayload(data);
                    say("Bookmark added!");
                    return 1;
                }));
    }

    private static void send(String payload) {
        final var connection = Minecraft.getInstance().getConnection();
        if (connection == null) return;
        if (payload.length() > NEINetwork.MAX_DATA) {
            say("Too many items to share");
        } else if (!NotEnoughItems.platform().serverHas(LinkToServer.TYPE)) {
            say("This server does not have NEI Rewrite, so bookmarks cannot be shared");
        } else {
            NotEnoughItems.platform().sendToServer(new LinkToServer(payload));
        }
    }

    /** The tree window's link button and Ctrl + link key over a group's strip. */
    static void sendGroup(int groupId) {
        send(BookmarkPayload.ofGroup(Bookmarks.grid(), groupId, "Bookmark Group"));
    }

    /** Ctrl + Shift + link key over a recipe of the recipe window or an item with a favorite recipe: the whole recipe. */
    static void sendRecipe(RecipeView view) {
        send(BookmarkPayload.ofItems(AutoCraft.itemsOf(view), "Bookmark Recipe"));
    }

    /** Ctrl + link key over a bookmark: its whole recipe, or just the item when it has no recipe. */
    static void sendSlot(BookmarkGrid.Item slot) {
        final BookmarkGrid grid = Bookmarks.grid();
        if (slot.recipeId == null || slot.type == 0) {
            send(BookmarkPayload.ofItems(List.of(slot), nameOf(slot.id)));
        } else {
            send(BookmarkPayload.ofItems(grid.items.stream().filter(i -> i.inRecipe(slot.recipeId, slot.groupId)).toList(),
                    "Bookmark Recipe"));
        }
    }

    /** Ctrl + link key over an item of the panels. */
    static void sendItem(String id, int amount) {
        send(BookmarkPayload.ofItems(List.of(new BookmarkGrid.Item(id, amount, 0)), nameOf(id)));
    }

    private static String nameOf(String id) {
        final ItemEntry entry = ItemList.find(id);
        return entry == null ? id : entry.stack.getHoverName().getString();
    }
}
