package codechicken.nei.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import codechicken.nei.NEIConfig;
import codechicken.nei.api.ItemFilter;
import codechicken.nei.item.ItemEntry;

/**
 * Port of NEI's {@code SearchTokenParser} with its search settings:
 * <ul>
 * <li>pattern mode: Plain, Extended ({@code ?} one character, {@code *} anything, {@code r/regex/}), Regex, Extended+
 * (the expression mode, see below);</li>
 * <li>space mode: AND, Space (a space is part of the text), AND-X (all words must match within the same kind of text);</li>
 * <li>per kind of text a mode: Always (also without a prefix), Prefix (only after its prefix character), Never. The kinds:
 * {@code @} mod, {@code #} tooltip, {@code &} registry id, {@code $} item tag, {@code %} subset; no prefix is the name;</li>
 * <li>{@code a b | c} is (a AND b) OR c, a leading {@code -} or {@code !} negates, quotes keep text together.</li>
 * </ul>
 * Expression mode (Extended+, NEI's ANTLR grammar written by hand): {@code |} or, a space and, {@code -x} not,
 * {@code { ... }} groups, {@code "quoted text"}, {@code /regex/} (or {@code r/regex/}), a prefix before a token, {@code \x}
 * an escaped character.
 *
 * Not ported: prefix redefinitions (the JSON option) and the recipe tokens {@code < > ^} (an expression with them
 * matches nothing as an item search, as in NEI).
 */
public final class SearchParser {

    // ---- settings ----

    /** Search modes of one kind of text. */
    public static final int ALWAYS = 0;
    public static final int PREFIX = 1;
    public static final int NEVER = 2;

    /** The settings a parse depends on; part of the cache key. {@code modes} are mod, tooltip, id, tag, subset. */
    public record Settings(int pattern, int space, int[] modes) {

        /** Plain matching with the default modes: for fixed rules (collapsible groups, hide rules). */
        public static final Settings PLAIN = new Settings(0, 0, new int[] { PREFIX, PREFIX, PREFIX, PREFIX, PREFIX });

        static Settings current() {
            return new Settings(NEIConfig.SEARCH_PATTERN_MODE.getAsInt(), NEIConfig.SEARCH_SPACE_MODE.getAsInt(),
                    new int[] { NEIConfig.SEARCH_MOD_MODE.getAsInt(), NEIConfig.SEARCH_TOOLTIP_MODE.getAsInt(),
                            NEIConfig.SEARCH_ID_MODE.getAsInt(), NEIConfig.SEARCH_TAG_MODE.getAsInt(),
                            NEIConfig.SEARCH_SUBSET_MODE.getAsInt() });
        }

        String key() {
            return pattern + "," + space + "," + Arrays.toString(modes);
        }
    }

    // ---- providers ----

    /** One kind of text to search: its prefix, colour in the search box and the filter for one matcher. */
    private record Provider(char prefix, int color, int modeIndex, Function<Predicate<String>, ItemFilter> make) {

        int mode(Settings s) {
            return modeIndex < 0 ? ALWAYS : s.modes()[modeIndex];
        }
    }

    private static final int GREEN = 0x55FF55;
    private static final int GRAY = 0xAAAAAA;
    private static final int BLUE = 0x5555FF;
    private static final int AQUA = 0x55FFFF;
    private static final int GOLD = 0xFFAA00;
    private static final int LIGHT_PURPLE = 0xFF55FF;
    private static final int YELLOW = 0xFFFF55;
    private static final int DARK_PURPLE = 0xAA00AA;

    /** The name (no prefix): always searched. */
    private static final Provider NAME = new Provider('\0', -1, -1,
            m -> e -> m.test(e.name) || e.aliases.stream().anyMatch(m));

    private static final List<Provider> PREFIXED = List.of(
            new Provider('@', LIGHT_PURPLE, 0, m -> e -> m.test(e.modName) || m.test(e.modId)),
            new Provider('#', YELLOW, 1, m -> e -> m.test(e.tooltip())),
            new Provider('&', GOLD, 2, m -> e -> m.test(e.registryId)),
            new Provider('$', AQUA, 3, m -> e -> e.tags().stream().anyMatch(m)),
            // NEI's subset search: the items of the subsets whose path matches (the set is made when the search is parsed)
            new Provider('%', DARK_PURPLE, 4, m -> {
                final java.util.Set<ItemEntry> items = codechicken.nei.Subsets.entriesMatching(m);
                return items::contains;
            }));

    private static final ItemFilter NOTHING = e -> false;

    // ---- parsing entry points ----

    private final Map<String, ItemFilter> cache = new LinkedHashMap<>(32, 0.75f, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ItemFilter> eldest) {
            return size() > 20;
        }
    };

    public SearchParser() {}

    /** Forgets the parsed searches (a {@code %} search holds the subsets as they were; settings may have changed). */
    public synchronized void clearCache() {
        cache.clear();
    }

    /** The filter for a search typed by the player, with the search settings of the options. */
    public ItemFilter parse(String raw) {
        return parse(raw, Settings.current());
    }

    /** The filter for a fixed rule: plain matching, default modes (not changed by the options). */
    public ItemFilter parsePlain(String raw) {
        return parse(raw, Settings.PLAIN);
    }

    public synchronized ItemFilter parse(String raw, Settings st) {
        final String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) return ItemFilter.EVERYTHING;
        return cache.computeIfAbsent(st.key() + ":" + text,
                k -> st.pattern() == 3 ? buildExpression(text, st) : buildClassic(text, st));
    }

    // ---- matchers ----

    /** NEI's {@code SearchField.getPattern}: the test for one piece of text, or null if there is nothing to test. */
    static Predicate<String> matcher(String word, int patternMode) {
        if (word.isEmpty()) return null;
        switch (patternMode) {
            case 0 -> {
                return s -> s.contains(word);
            }
            case 1 -> {
                if (word.length() >= 3 && word.startsWith("r/") && word.endsWith("/")) {
                    return regex(word.substring(2, word.length() - 1), true);
                }
                if (word.indexOf('?') < 0 && word.indexOf('*') < 0) return s -> s.contains(word);
                final Matcher m = Pattern.compile("[?*]").matcher(word);
                final StringBuilder sb = new StringBuilder();
                int last = 0;
                while (m.find()) {
                    sb.append(Pattern.quote(word.substring(last, m.start())));
                    sb.append(m.group().charAt(0) == '?' ? "." : ".+?");
                    last = m.end();
                }
                sb.append(Pattern.quote(word.substring(last)));
                return regex(sb.toString(), true);
            }
            default -> {
                return regex(word, true);
            }
        }
    }

    /** A regular expression test. If it is not valid yet (the player is still typing) plain matching stands in when allowed. */
    private static Predicate<String> regex(String pattern, boolean plainFallback) {
        try {
            final Pattern p = Pattern.compile(pattern, Pattern.MULTILINE | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            return s -> p.matcher(s).find();
        } catch (PatternSyntaxException ignored) {
            return plainFallback ? s -> s.contains(pattern) : null;
        }
    }

    private static ItemFilter filterOf(Provider provider, String word, Settings st) {
        // the subset search ignores spaces in its path, as in NEI
        final String w = provider.prefix() == '%' && st.pattern() != 3 ? word.replaceAll("\\s+", "") : word;
        final Predicate<String> m = matcher(w, st.pattern());
        return m == null ? NOTHING : provider.make().apply(m);
    }

    // ---- classic modes (Plain, Extended, Regex) ----

    private static String prefixesOf(Settings st) {
        final StringBuilder sb = new StringBuilder();
        for (Provider p : PREFIXED) if (p.mode(st) == PREFIX) sb.append(p.prefix());
        return sb.toString();
    }

    private static Provider providerFor(char c, Settings st) {
        for (Provider p : PREFIXED) if (p.prefix() == c && p.mode(st) == PREFIX) return p;
        return null;
    }

    /** NEI's {@code SearchToken}. */
    private static final class Token {

        Character ignore;
        boolean quotes;
        Character firstChar;
        String[] words;
        String rawText = "";
        int start;
        int end;

        boolean simple() {
            return firstChar == null && !quotes && ignore == null;
        }

        ItemFilter filter(Settings st) {
            if (words == null || words.length == 0) return null;
            if (rawText.isEmpty()) return ignore == null ? NOTHING : null;

            final ItemFilter result = parseToken(st);
            return ignore != null ? ItemFilter.not(result) : result;
        }

        private ItemFilter parseToken(Settings st) {
            final Provider provider = firstChar == null ? null : providerFor(firstChar, st);
            if (provider != null) return allWords(provider, st);

            final List<ItemFilter> filters = new ArrayList<>();
            filters.add(allWords(NAME, st));
            for (Provider p : PREFIXED) if (p.mode(st) == ALWAYS) filters.add(allWords(p, st));
            return filters.size() == 1 ? filters.get(0) : ItemFilter.any(filters);
        }

        private ItemFilter allWords(Provider provider, Settings st) {
            final List<ItemFilter> filters = new ArrayList<>();
            for (String word : words) filters.add(filterOf(provider, word, st));
            return filters.size() == 1 ? filters.get(0) : ItemFilter.all(filters);
        }
    }

    private ItemFilter buildClassic(String text, Settings st) {
        final List<ItemFilter> alternatives = new ArrayList<>();

        for (String[] sub : splitByDelimiters(text, "|", false)) {
            if (sub[1].isEmpty()) continue;

            final List<ItemFilter> tokens = new ArrayList<>();
            for (Token token : splitSearchText(sub[1], st)) {
                final ItemFilter f = token.filter(st);
                if (f != null) tokens.add(f);
            }

            if (tokens.size() == 1) alternatives.add(tokens.get(0));
            else if (!tokens.isEmpty()) alternatives.add(ItemFilter.all(tokens));
        }

        if (alternatives.isEmpty()) return ItemFilter.EVERYTHING;
        return alternatives.size() == 1 ? alternatives.get(0) : ItemFilter.any(alternatives);
    }

    /** NEI's {@code splitSearchText}, the space modes included. */
    private static List<Token> splitSearchText(String filterText, Settings st) {
        if (filterText.isEmpty()) return Collections.emptyList();

        final List<Token> tokens = new ArrayList<>();
        final String prefixes = prefixesOf(st);
        Token last = null;
        int lastEnd = 0;

        for (String[] part : splitByDelimiters(filterText, "-!" + prefixes, true)) {
            final Token token = createToken(lastEnd, part, prefixes);
            final int length = part[0].length() + part[1].length();

            if (token.simple() && token.rawText.isEmpty()) {
                lastEnd += length;
                continue;
            }

            if (st.space() == 1) { // space
                if (last != null && last.simple() && token.simple()) {
                    last.rawText = filterText.substring(last.start, token.end).trim();
                    last.words[0] = last.rawText;
                    last.end = token.end;
                } else {
                    tokens.add(last = token);
                }
            } else if (st.space() == 2) { // and-x
                if (last != null && last.simple() && token.simple()) {
                    final String[] words = new String[last.words.length + token.words.length];
                    System.arraycopy(last.words, 0, words, 0, last.words.length);
                    System.arraycopy(token.words, 0, words, last.words.length, token.words.length);
                    last.words = words;
                    last.rawText = filterText.substring(last.start, token.end).trim();
                    last.end = token.end;
                } else {
                    tokens.add(last = token);
                }
            } else { // and
                tokens.add(last = token);
            }

            lastEnd += length;
        }
        return tokens;
    }

    private static Token createToken(int lastEnd, String[] part, String prefixes) {
        final Token token = new Token();
        String pref = part[0];
        final String query = part[1].trim();

        token.start = lastEnd;
        token.end = lastEnd + pref.length() + query.length();

        if (pref.startsWith("-")) {
            token.ignore = '-';
            pref = pref.substring(1);
        } else if (pref.startsWith("!")) {
            token.ignore = '!';
            pref = pref.substring(1);
        }

        if (!pref.isEmpty() && prefixes.indexOf(pref.charAt(0)) >= 0) {
            token.firstChar = pref.charAt(0);
            pref = pref.substring(1);
        }

        String text = pref + query;
        token.quotes = text.length() > 1 && text.startsWith("\"") && text.endsWith("\"");
        if (token.quotes) text = text.substring(1, text.length() - 1);

        token.rawText = text;
        token.words = new String[] { token.quotes ? text.replace("\\\"", "\"") : text };
        return token;
    }

    /**
     * NEI's {@code splitByDelimiters}: cuts the text at the delimiter characters (outside quotes). Each part is
     * {delimiters in front, text}. With {@code space} a delimiter only counts at the start of a word and a new part starts
     * at every word.
     */
    static List<String[]> splitByDelimiters(String input, String delimiters, boolean space) {
        if (delimiters == null || delimiters.isEmpty()) return Collections.singletonList(new String[] { "", input });

        final List<String[]> tokens = new ArrayList<>();
        final int length = input.length();
        boolean insideQuotes = false;
        String token = "";
        int lastEnd = 0;

        for (int index = 0; index < length; index++) {
            final char ch = input.charAt(index);

            if (!insideQuotes && delimiters.indexOf(ch) >= 0
                    && (!space || index == 0 || input.charAt(index - 1) == ' ' || lastEnd == index)) {
                if (lastEnd == index) {
                    token += ch;
                } else {
                    tokens.add(new String[] { token, input.substring(lastEnd, index) });
                    token = String.valueOf(ch);
                }
                lastEnd = index + 1;
            } else if (!insideQuotes && space && ch != ' ' && index > 0 && input.charAt(index - 1) == ' ') {
                tokens.add(new String[] { token, input.substring(lastEnd, index) });
                token = "";
                lastEnd = index;
            }

            if (ch == '"' && (index == 0 || input.charAt(index - 1) != '\\')) insideQuotes = !insideQuotes;
        }

        tokens.add(new String[] { token, input.substring(lastEnd) });
        return tokens;
    }

    // ---- expression mode (Extended+) ----

    private enum T {
        REGEX_LEFT, REGEX_CONTENT, REGEX_RIGHT, QUOTE_LEFT, QUOTED_CONTENT, QUOTE_RIGHT, DASH, RECIPE, OR, LEFT, RIGHT, SPACE,
        ESCAPED, PREFIX, CLEAN
    }

    private record Lexed(T type, int start, int end, String text) {}

    /** NEI's SearchExpressionLexer. Tabs and line breaks are skipped. */
    private static List<Lexed> lex(String s, Settings st) {
        final String prefixes = prefixesOf(st);
        final List<Lexed> out = new ArrayList<>();
        int i = 0;
        final int n = s.length();
        while (i < n) {
            final char c = s.charAt(i);
            if (c == '\t' || c == '\r' || c == '\n') {
                i++;
            } else if (c == '/' || (c == 'r' && i + 1 < n && s.charAt(i + 1) == '/')) {
                final int open = c == 'r' ? 2 : 1;
                out.add(new Lexed(T.REGEX_LEFT, i, i + open, s.substring(i, i + open)));
                i += open;
                final int from = i;
                while (i < n && s.charAt(i) != '/') i += s.charAt(i) == '\\' && i + 1 < n && s.charAt(i + 1) == '/' ? 2 : 1;
                if (i > from) out.add(new Lexed(T.REGEX_CONTENT, from, i, s.substring(from, i)));
                if (i < n) {
                    out.add(new Lexed(T.REGEX_RIGHT, i, i + 1, "/"));
                    i++;
                }
            } else if (c == '"') {
                out.add(new Lexed(T.QUOTE_LEFT, i, i + 1, "\""));
                i++;
                final int from = i;
                while (i < n && s.charAt(i) != '"') i += s.charAt(i) == '\\' && i + 1 < n && s.charAt(i + 1) == '"' ? 2 : 1;
                if (i > from) out.add(new Lexed(T.QUOTED_CONTENT, from, i, s.substring(from, i)));
                if (i < n) {
                    out.add(new Lexed(T.QUOTE_RIGHT, i, i + 1, "\""));
                    i++;
                }
            } else if (c == '-') {
                out.add(new Lexed(T.DASH, i, i + 1, "-"));
                i++;
            } else if (c == '<' || c == '>' || c == '^') {
                final int len = i + 1 < n && s.charAt(i + 1) == '&' ? 2 : 1;
                out.add(new Lexed(T.RECIPE, i, i + len, s.substring(i, i + len)));
                i += len;
            } else if (c == '|') {
                out.add(new Lexed(T.OR, i, i + 1, "|"));
                i++;
            } else if (c == '{') {
                out.add(new Lexed(T.LEFT, i, i + 1, "{"));
                i++;
            } else if (c == '}') {
                out.add(new Lexed(T.RIGHT, i, i + 1, "}"));
                i++;
            } else if (c == ' ') {
                out.add(new Lexed(T.SPACE, i, i + 1, " "));
                i++;
            } else if (c == '\\' && i + 1 < n) {
                out.add(new Lexed(T.ESCAPED, i, i + 2, s.substring(i, i + 2)));
                i += 2;
            } else if (c == '\\') {
                i++; // a lone backslash at the end: the lexer cannot use it
            } else if (prefixes.indexOf(c) >= 0) {
                out.add(new Lexed(T.PREFIX, i, i + 1, String.valueOf(c)));
                i++;
            } else {
                out.add(new Lexed(T.CLEAN, i, i + 1, String.valueOf(c)));
                i++;
            }
        }
        return out;
    }

    /** A parsed node of the expression: kind, children and the text of a token. */
    private static final class Node {

        final String kind; // or, seq, not, prefixed, plain, regex, quoted, nothing
        final List<Node> kids = new ArrayList<>();
        String text = "";
        char prefix;

        Node(String kind) {
            this.kind = kind;
        }
    }

    /** NEI's SearchExpressionParser (the item part: one expression, no recipe clauses). */
    private static final class ExprParser {

        private final List<Lexed> toks;
        private int pos;
        boolean recipe; // a recipe token was seen

        ExprParser(List<Lexed> toks) {
            this.toks = toks;
        }

        private T peek() {
            return pos < toks.size() ? toks.get(pos).type() : null;
        }

        Node parse() {
            final Node all = new Node("seq");
            while (pos < toks.size()) {
                final Node part = orExpr();
                if (part != null) all.kids.add(part);
                if (pos < toks.size()) pos++; // a stray token (an unmatched }) is skipped
            }
            if (all.kids.isEmpty()) return null;
            return all.kids.size() == 1 ? all.kids.get(0) : all;
        }

        private Node orExpr() {
            final Node or = new Node("or");
            Node seq = sequence();
            if (seq != null) or.kids.add(seq);
            while (peek() == T.OR) {
                pos++;
                seq = sequence();
                if (seq != null) or.kids.add(seq);
            }
            return or.kids.isEmpty() ? null : or;
        }

        private Node sequence() {
            final Node seq = new Node("seq");
            while (true) {
                while (peek() == T.SPACE) pos++;
                if (peek() == T.RECIPE) { // a recipe clause: an item search does not accept it
                    recipe = true;
                    pos++;
                    continue;
                }
                final Node u = unary();
                if (u == null) break;
                seq.kids.add(u);
            }
            return seq.kids.isEmpty() ? null : seq;
        }

        private Node unary() {
            final T t = peek();
            if (t == T.LEFT) {
                pos++;
                final Node inner = orExpr();
                if (peek() == T.RIGHT) pos++;
                return inner == null ? new Node("nothing") : inner;
            }
            if (t == T.DASH) {
                pos++;
                final Node inner = unary();
                if (inner == null) return new Node("nothing");
                final Node not = new Node("not");
                not.kids.add(inner);
                return not;
            }
            if (t == T.PREFIX) {
                final char prefix = toks.get(pos).text().charAt(0);
                pos++;
                final Node token = token();
                if (token == null) return new Node("nothing"); // a prefix with nothing after it matches nothing
                final Node p = new Node("prefixed");
                p.prefix = prefix;
                p.kids.add(token);
                return p;
            }
            return token();
        }

        private Node token() {
            final T t = peek();
            if (t == T.REGEX_LEFT) {
                pos++;
                final Node n = new Node("regex");
                n.text = peek() == T.REGEX_CONTENT ? toks.get(pos++).text() : null;
                if (peek() == T.REGEX_RIGHT) pos++;
                return n;
            }
            if (t == T.QUOTE_LEFT) {
                pos++;
                final Node n = new Node("quoted");
                n.text = peek() == T.QUOTED_CONTENT ? toks.get(pos++).text() : null;
                if (peek() == T.QUOTE_RIGHT) pos++;
                return n;
            }
            if (t == T.CLEAN || t == T.ESCAPED) {
                final StringBuilder sb = new StringBuilder();
                while (peek() == T.CLEAN || peek() == T.ESCAPED) sb.append(toks.get(pos++).text());
                final Node n = new Node("plain");
                n.text = sb.toString();
                return n;
            }
            return null;
        }
    }

    private static final Pattern REGEX_ESCAPED_SPACE = Pattern.compile("([^\\\\](?:\\\\\\\\)+)?\\\\ ");
    private static final Pattern ESCAPED_SYMBOL = Pattern.compile("\\\\(.)");

    private ItemFilter buildExpression(String text, Settings st) {
        // as in NEI, with the Space mode the spaces are part of the text (escaped before the expression is read)
        final String source = st.space() == 1 ? text.replace(" ", "\\ ") : text;
        final ExprParser parser = new ExprParser(lex(source, st));
        final Node root = parser.parse();
        if (parser.recipe || root == null) return NOTHING; // recipe clauses make it no item search
        return visit(root, st);
    }

    private ItemFilter visit(Node n, Settings st) {
        switch (n.kind) {
            case "or" -> {
                return combine(n, st, true);
            }
            case "seq" -> {
                return combine(n, st, false);
            }
            case "not" -> {
                return ItemFilter.not(visit(n.kids.get(0), st));
            }
            case "prefixed" -> {
                final Provider provider = providerFor(n.prefix, st);
                final Predicate<String> m = tokenMatcher(n.kids.get(0), st);
                if (provider == null || m == null) return NOTHING;
                return provider.make().apply(m);
            }
            case "plain", "regex", "quoted" -> {
                final Predicate<String> m = tokenMatcher(n, st);
                if (m == null) return NOTHING;
                final List<ItemFilter> filters = new ArrayList<>();
                filters.add(NAME.make().apply(m));
                for (Provider p : PREFIXED) if (p.mode(st) == ALWAYS) filters.add(p.make().apply(m));
                return filters.size() == 1 ? filters.get(0) : ItemFilter.any(filters);
            }
            default -> {
                return NOTHING;
            }
        }
    }

    private ItemFilter combine(Node n, Settings st, boolean any) {
        final List<ItemFilter> filters = new ArrayList<>();
        for (Node kid : n.kids) filters.add(visit(kid, st));
        if (filters.isEmpty()) return NOTHING;
        if (filters.size() == 1) return filters.get(0);
        return any ? ItemFilter.any(filters) : ItemFilter.all(filters);
    }

    /** The test of a token: plain and quoted text match as written, regex content as a regular expression (nothing if invalid). */
    private static Predicate<String> tokenMatcher(Node n, Settings st) {
        if (n.text == null || n.text.isEmpty()) return null;
        switch (n.kind) {
            case "plain" -> {
                final String literal = ESCAPED_SYMBOL.matcher(n.text).replaceAll("$1");
                return s -> s.contains(literal);
            }
            case "quoted" -> {
                String literal = n.text.replace("\\\"", "\"");
                if (st.space() == 1) literal = literal.replace("\\ ", " ");
                final String l = literal;
                return s -> s.contains(l);
            }
            case "regex" -> {
                String pattern = n.text;
                if (st.space() == 1) pattern = REGEX_ESCAPED_SPACE.matcher(pattern).replaceAll("$1 ");
                return regex(pattern, false);
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * NEI's {@code SearchField.getEscapedSearchText}: the text of an item name as a search that finds it literally in the
     * current mode (special characters escaped, quotes around a name with spaces when the option is on).
     */
    public static String escapedText(String text) {
        text = text.replaceAll("\u00a7.", ""); // formatting codes
        final boolean addQuotes = text.contains(" ") && NEIConfig.SEARCH_QUOTE_DROP.getAsBoolean();
        final Settings st = Settings.current();
        switch (st.pattern()) {
            case 1 -> text = text.replaceAll("[?*]", "\\\\$0");
            case 2 -> text = text.replaceAll("[{}()\\[\\].+*?^$\\\\|]", "\\\\$0");
            case 3 -> text = addQuotes ? text.replaceAll("\"", "\\\\$0")
                    : text.replaceAll("[-<>^{}|/\\\\\"" + Pattern.quote(prefixesOf(st)) + "]", "\\\\$0");
            default -> {
            }
        }
        return addQuotes ? "\"" + text + "\"" : text;
    }

    // ---- syntax colours of the search box ----

    /** Changes when an option that changes the colours changes. */
    public static String settingsKey() {
        return Settings.current().key();
    }

    /**
     * The colour of every character of the text (RGB, or -1 for the normal colour), as NEI's search box colours it:
     * prefixes and the text after them in their kind's colour, quotes gold, {@code -} blue, {@code |} and brackets gray.
     */
    public static int[] colors(String text) {
        final int[] colors = new int[text.length()];
        Arrays.fill(colors, -1);
        if (text.isEmpty()) return colors;
        final Settings st = Settings.current();
        if (st.pattern() == 3) colorExpression(text, st, colors);
        else colorClassic(text.toLowerCase(Locale.ROOT), st, colors);
        return colors;
    }

    private static void colorClassic(String text, Settings st, int[] colors) {
        int base = 0;
        for (String[] sub : splitByDelimiters(text, "|", false)) {
            for (int i = 0; i < sub[0].length() && base + i < colors.length; i++) colors[base + i] = GRAY;
            final int offset = base + sub[0].length();
            if (!sub[1].isEmpty()) {
                for (Token t : splitSearchText(sub[1], st)) {
                    int i = offset + t.start;
                    final int end = Math.min(offset + t.end, colors.length);
                    final Provider provider = t.firstChar == null ? null : providerFor(t.firstChar, st);
                    final int color = provider == null ? -1 : provider.color();
                    if (t.ignore != null && i < end) colors[i++] = BLUE;
                    if (t.firstChar != null && i < end) colors[i++] = color;
                    for (int j = i; j < end; j++) colors[j] = color;
                    if (t.quotes) {
                        if (i < end) colors[i] = GOLD;
                        if (end - 1 >= i) colors[end - 1] = GOLD;
                    }
                }
            }
            base = offset + sub[1].length();
        }
    }

    private static void colorExpression(String text, Settings st, int[] colors) {
        int prefixColor = -1;
        boolean afterPrefix = false;
        for (Lexed t : lex(text, st)) {
            int color = -1;
            switch (t.type()) {
                case RECIPE -> color = GREEN;
                case OR, LEFT, RIGHT -> color = GRAY;
                case DASH -> color = BLUE;
                case REGEX_LEFT, REGEX_RIGHT -> color = AQUA;
                case QUOTE_LEFT, QUOTE_RIGHT -> color = GOLD;
                case PREFIX -> {
                    final Provider p = providerFor(t.text().charAt(0), st);
                    prefixColor = p == null ? -1 : p.color();
                    color = prefixColor;
                }
                case CLEAN, ESCAPED, REGEX_CONTENT, QUOTED_CONTENT -> color = afterPrefix ? prefixColor : -1;
                default -> color = -1;
            }
            // the text of a token keeps the prefix's colour until the token ends
            afterPrefix = switch (t.type()) {
                case PREFIX, REGEX_LEFT, QUOTE_LEFT, REGEX_CONTENT, QUOTED_CONTENT, CLEAN, ESCAPED -> afterPrefix || t.type() == T.PREFIX;
                default -> false;
            };
            for (int i = t.start(); i < t.end() && i < colors.length; i++) colors[i] = color;
        }
    }
}
