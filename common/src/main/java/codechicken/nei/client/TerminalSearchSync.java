package codechicken.nei.client;

import codechicken.nei.NEIConfig;
import codechicken.nei.NotEnoughItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

/**
 * Applied Energistics' "sync with JEI" for NEI: while an ME terminal is open, its search field and NEI's item search show
 * the same text, whichever one you type in. AE2's own sync talks to JEI's search, which NEI replaces, so this reads the
 * terminal's text box from the screen's widgets instead (the first {@link EditBox} of a screen of {@code appeng.client.gui.me}).
 * Nothing here refers to AE2's classes, so NEI runs without AE2.
 */
final class TerminalSearchSync {

    private static final String TERMINALS = "appeng.client.gui.me.";

    private static Screen tracked;
    private static EditBox terminal;
    /** The two texts as they were after the last sync, to tell which side changed. */
    private static String lastTerminal;
    private static String lastNei;

    private TerminalSearchSync() {}

    /** Is this an ME terminal screen with a search field? */
    static boolean isTerminal(Screen screen) {
        return screen != null && screen.getClass().getName().startsWith(TERMINALS) && field(screen) != null;
    }

    private static EditBox field(Screen screen) {
        for (GuiEventListener child : screen.children()) if (child instanceof EditBox box) return box;
        return null;
    }

    /** Types a name into the terminal's search field (its own text is replaced). Returns false when there is no terminal. */
    static boolean type(Screen screen, String text) {
        if (screen == null || !screen.getClass().getName().startsWith(TERMINALS)) return false;
        final EditBox box = field(screen);
        if (box == null) return false;
        box.setValue(text);
        return true;
    }

    public static void onTick() {
        final Screen screen = Minecraft.getInstance().screen;
        if (screen == null || !NEIConfig.SYNC_TERMINAL_SEARCH.get() || !screen.getClass().getName().startsWith(TERMINALS)) {
            tracked = null;
            terminal = null;
            return;
        }
        if (screen != tracked) { // a new terminal screen: find its field again
            tracked = screen;
            terminal = null;
            lastTerminal = null;
            lastNei = null;
        }
        if (terminal == null) {
            for (GuiEventListener child : screen.children()) {
                if (child instanceof EditBox box) {
                    terminal = box;
                    break;
                }
            }
            if (terminal == null) return;
        }

        String nei = NEIClientEvents.searchText();
        if (nei == null) return; // NEI has no search field on this screen
        final String text = terminal.getValue();

        if (lastTerminal == null) { // first sight: text AE2 remembered wins, else NEI's text is kept
            if (!text.isEmpty() && !text.equals(nei)) {
                NEIClientEvents.searchFor(text);
                nei = NEIClientEvents.searchText();
            }
            lastTerminal = text;
            lastNei = nei;
        } else if (!text.equals(lastTerminal)) { // typed in the terminal
            NEIClientEvents.searchFor(text);
            lastTerminal = text;
            lastNei = NEIClientEvents.searchText();
        } else if (!nei.equals(lastNei)) { // typed in NEI's search
            terminal.setValue(nei);
            lastTerminal = terminal.getValue();
            lastNei = nei;
        }
    }
}
