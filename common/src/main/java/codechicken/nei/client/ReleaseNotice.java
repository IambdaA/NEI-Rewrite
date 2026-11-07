package codechicken.nei.client;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import codechicken.nei.NEIConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

/**
 * A chat message after joining a world: this release is ambitious, please report issues on GitHub (the word is a link), and
 * a link that turns the message off (Inventory > Release Notice turns it back on). Shown once per world joined.
 */
public final class ReleaseNotice {

    private static final String ISSUES = "https://github.com/IambdaA/NEI-Rewrite/issues/";
    /** Ticks after joining before the message is sent, so the join messages do not bury it. */
    private static final int DELAY = 60;

    private static int ticks = -1;

    private ReleaseNotice() {}

    /** Called every client tick. */
    static void onTick() {
        final var player = Minecraft.getInstance().player;
        if (player == null) {
            ticks = -1; // left the world: the next join shows it again
            return;
        }
        if (ticks < 0) ticks = 0;
        if (ticks > DELAY) return;
        if (++ticks <= DELAY || !NEIConfig.RELEASE_NOTICE.getAsBoolean()) return;
        player.sendSystemMessage(message());
    }

    private static MutableComponent message() {
        final MutableComponent github = Component.literal("GitHub").withStyle(style -> style.withColor(ChatFormatting.AQUA)
                .withUnderlined(true).withClickEvent(OPEN_ISSUES).withHoverEvent(HOVER_ISSUES));
        final MutableComponent off = Component.literal("[Turn off this message]").withStyle(style -> style
                .withColor(ChatFormatting.GRAY).withClickEvent(TURN_OFF).withHoverEvent(HOVER_OFF));
        return Component.literal("[NEI Rewrite] ").withStyle(ChatFormatting.GOLD)
                .append(Component.literal("This release is ambitious. Please report any issues you find on ").withStyle(ChatFormatting.WHITE))
                .append(github)
                .append(Component.literal(". ").withStyle(ChatFormatting.WHITE))
                .append(off);
    }

    /** Settings > Inventory > Report Issues: opens the issue tracker in the browser. */
    public static void openIssues() {
        net.minecraft.Util.getPlatform().openUri(ISSUES);
    }

    /** The client command {@code /nei_notice off} behind the "Turn off this message" link. */
    public static <S> LiteralArgumentBuilder<S> command() {
        return LiteralArgumentBuilder.<S>literal("nei_notice").then(LiteralArgumentBuilder.<S>literal("off").executes(ctx -> {
            NEIConfig.RELEASE_NOTICE.set(false);
            NEIConfig.changed();
            final var player = Minecraft.getInstance().player;
            if (player != null) {
                player.sendSystemMessage(Component.literal("[NEI Rewrite] ").withStyle(ChatFormatting.GOLD).append(Component.literal(
                        "The release notice is off. NEI's settings turn it back on: Inventory > Release Notice.").withStyle(ChatFormatting.GRAY)));
            }
            return 1;
        }));
    }

    private static final ClickEvent OPEN_ISSUES = new ClickEvent(ClickEvent.Action.OPEN_URL, ISSUES);
    private static final HoverEvent HOVER_ISSUES = new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(ISSUES));
    private static final ClickEvent TURN_OFF = new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/nei_notice off");
    private static final HoverEvent HOVER_OFF = new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Don't show this message again"));
}
