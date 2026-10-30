package codechicken.nei.item;

/** The arithmetic of NEI's item quantity field ({@code ItemQuantityField}). Plain Java, no game classes. */
public final class QuantityMath {

    /** Digits typed into the field; more would overflow an int. */
    public static final int MAX_DIGITS = 9;

    private QuantityMath() {}

    /** NEI's step for the + / - buttons and the wheel: Ctrl = 64, Shift = 10, else 1. */
    public static int stepSize(boolean ctrl, boolean shift) {
        return ctrl ? 64 : shift ? 10 : 1;
    }

    /** {@code current + delta}, never below 0 and never above what {@link #MAX_DIGITS} digits can hold. */
    public static int step(int current, int delta) {
        final long next = (long) current + delta;
        return (int) Math.max(0, Math.min(next, 999_999_999L));
    }

    /** The number in the field's text; empty text is 0, anything else that is not a number is -1 (rejected). */
    public static int parse(String text) {
        if (text == null || text.isEmpty()) return 0;
        if (text.length() > MAX_DIGITS) return -1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '9') return -1;
        }
        return Integer.parseInt(text);
    }

    /** What the amount of one click is: the typed quantity, or a full stack when it is 0 (NEI's getScrollMultiplier). */
    public static int multiplier(boolean show, int quantity, int maxStack) {
        return show && quantity > 0 ? quantity : maxStack;
    }
}
