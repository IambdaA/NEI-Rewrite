package codechicken.nei.bookmark;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/**
 * Large numbers shortened the way NEI's {@code ReadableNumberConverter} does it (a port of AE2's converter): 12345
 * becomes {@code 12k}, 1500000 becomes {@code 1.5M}. Plain Java.
 */
public final class ReadableNumber {

    private static final int DIVISION_BASE = 1000;
    private static final char[] POSTFIXES = "kMGTPE".toCharArray();

    private ReadableNumber() {}

    /** NEI's {@code toWideReadableForm}: at most four characters. */
    public static String wide(long number) {
        return restrictedByWidth(number, 4);
    }

    private static String restrictedByWidth(long number, int width) {
        final String numberString = Long.toString(Math.max(0, number));
        int numberSize = numberString.length();
        if (numberSize <= width) return numberString;

        final DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator('.');
        symbols.setGroupingSeparator(',');
        final DecimalFormat format = new DecimalFormat(".#;0.#");
        format.setDecimalFormatSymbols(symbols);
        format.setRoundingMode(RoundingMode.DOWN);

        long base = number;
        double last = base * 1000;
        int exponent = -1;
        String postFix = "";

        while (numberSize > width && exponent < POSTFIXES.length - 1) {
            last = base;
            base /= DIVISION_BASE;
            exponent++;
            numberSize = Long.toString(base).length() + 1;
            postFix = String.valueOf(POSTFIXES[exponent]);
        }

        final String withPrecision = format.format(last / DIVISION_BASE) + postFix;
        final String withoutPrecision = Long.toString(base) + postFix;
        return withPrecision.length() <= width ? withPrecision : withoutPrecision;
    }
}
