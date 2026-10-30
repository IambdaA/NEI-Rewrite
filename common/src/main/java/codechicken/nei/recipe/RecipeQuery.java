package codechicken.nei.recipe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * NEI's recipe search grammar. A marker starts a clause: {@code <} looks at the ingredients, {@code >} at the results,
 * {@code ^} at the other things (the stations); text before the first marker looks everywhere. A {@code &} right after a
 * marker means every item must match instead of any one. Clauses are ANDed; a clause's text is the normal item search.
 * Markers inside quotes, between slashes (regex) or after a backslash are plain text.
 *
 * Pure Java so it can be tested without Minecraft.
 */
public final class RecipeQuery {

    public enum Where {
        ANY, INPUT, OUTPUT, OTHER
    }

    /** @param all true when every item (not just one) must match */
    public record Clause(Where where, boolean all, String text) {}

    private RecipeQuery() {}

    public static List<Clause> parse(String query) {
        final List<Clause> out = new ArrayList<>();
        Where where = Where.ANY;
        boolean all = false;
        final StringBuilder text = new StringBuilder();
        boolean quoted = false;
        boolean regex = false;

        for (int i = 0; i < query.length(); i++) {
            final char c = query.charAt(i);
            if (c == '\\' && i + 1 < query.length()) { // escaped: keep both characters for the item search
                text.append(c).append(query.charAt(++i));
                continue;
            }
            if (c == '"' && !regex) quoted = !quoted;
            else if (c == '/' && !quoted) regex = !regex;
            else if (!quoted && !regex && (c == '<' || c == '>' || c == '^')) {
                add(out, where, all, text);
                where = c == '<' ? Where.INPUT : c == '>' ? Where.OUTPUT : Where.OTHER;
                all = i + 1 < query.length() && query.charAt(i + 1) == '&';
                if (all) i++;
                continue;
            }
            text.append(c);
        }
        add(out, where, all, text);
        return out;
    }

    private static void add(List<Clause> out, Where where, boolean all, StringBuilder text) {
        final String t = text.toString().trim();
        text.setLength(0);
        if (!t.isEmpty()) out.add(new Clause(where, all, t));
    }

    /**
     * @param stacks the items to look at, each with every stack it can show (a tag slot has several)
     * @return any mode: some item has a matching stack; all mode: every item has one (no items: passes)
     */
    public static <T> boolean test(boolean all, List<List<T>> stacks, Predicate<T> matches) {
        for (List<T> options : stacks) {
            final boolean hit = options.stream().anyMatch(matches);
            if (hit != all) return hit; // any: first hit decides; all: first miss decides
        }
        return all;
    }
}
