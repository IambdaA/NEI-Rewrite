package codechicken.nei.client;

import java.util.ArrayList;
import java.util.List;

import codechicken.nei.search.SearchParser;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * NEI's {@code SearchTextFormatter}: colours the text of a search field by its syntax (prefixes in the colour of their kind,
 * quotes gold, {@code -} blue, {@code |} and brackets gray). The colours come from {@link SearchParser#colors(String)}.
 */
final class SearchHighlight {

    private String lastText;
    private String lastKey;
    private int[] lastColors = new int[0];

    private SearchHighlight() {}

    /** Gives the box NEI's search colours. */
    static void apply(EditBox box) {
        final SearchHighlight h = new SearchHighlight();
        box.setFormatter((shown, offset) -> h.format(box.getValue(), shown, offset));
    }

    private FormattedCharSequence format(String full, String shown, int offset) {
        if (shown.isEmpty()) return FormattedCharSequence.EMPTY;

        final String key = SearchParser.settingsKey();
        if (!full.equals(lastText) || !key.equals(lastKey)) {
            lastColors = SearchParser.colors(full);
            lastText = full;
            lastKey = key;
        }
        final int[] colors = lastColors;
        final int end = Math.min(offset + shown.length(), colors.length);
        final List<FormattedCharSequence> parts = new ArrayList<>();
        int i = offset;
        while (i < end) {
            final int color = colors[i];
            int j = i + 1;
            while (j < end && colors[j] == color) j++;
            final Style style = color < 0 ? Style.EMPTY : Style.EMPTY.withColor(color);
            parts.add(FormattedCharSequence.forward(shown.substring(i - offset, j - offset), style));
            i = j;
        }
        if (end - offset < shown.length()) { // text beyond the coloured part (cannot happen unless the value changed): plain
            parts.add(FormattedCharSequence.forward(shown.substring(Math.max(0, end - offset)), Style.EMPTY));
        }
        return FormattedCharSequence.composite(parts);
    }
}
