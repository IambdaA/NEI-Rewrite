package codechicken.nei.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The search box history, a copy of NEI's {@code util.TextHistory}: Up goes to older searches, Down to newer ones. */
public final class TextHistory {

    private static final int MAX_HISTORY = 100;

    private final List<String> history = new ArrayList<>();

    /** Remembers a search (moved to the newest place if already there). Empty text is not kept. */
    public boolean add(String text) {
        if (text.isEmpty()) return false;
        history.remove(text);
        history.add(text);
        if (history.size() > MAX_HISTORY) history.remove(0);
        return true;
    }

    /** The search before this one; text not in the history yet is added first, so Down brings it back. */
    public Optional<String> previous(String text) {
        int index = history.indexOf(text);
        if (index < 0) index = add(text) ? history.size() - 1 : history.size();
        return index <= 0 ? Optional.empty() : Optional.of(history.get(index - 1));
    }

    /** The search after this one; past the newest one it gives the empty text. */
    public Optional<String> next(String text) {
        final int index = history.indexOf(text);
        if (index < 0) return Optional.empty();
        return Optional.of(index + 1 < history.size() ? history.get(index + 1) : "");
    }
}
