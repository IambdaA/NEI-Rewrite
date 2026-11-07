package codechicken.nei.client;

import codechicken.nei.item.ColorMath;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/**
 * The colour picker of the settings screen (saturation / value square, hue bar, alpha bar, hex field, old and new
 * swatch). NEI types the colour as text; this popup is an addition that edits the same {@code #AARRGGBB} value.
 */
final class ColorPickerPopup {

    private enum Drag {
        NONE, SV, HUE, ALPHA
    }

    private static final int PICK_W = 280;
    private static final int PICK_H = 200;
    private static final int SV = 128;
    private static final int CELL = 4;

    private boolean open;
    private String title = "";
    private Consumer<String> onOk = s -> { };
    private int pickOld;
    private float[] hsv = new float[4];
    private Drag drag = Drag.NONE;
    private final EditBox hexBox = new EditBox(Minecraft.getInstance().font, 0, 0, 80, 18, Component.empty());
    private int sw;
    private int sh;

    ColorPickerPopup() {
        hexBox.setMaxLength(9);
        hexBox.setResponder(t -> {
            final Integer c = ColorMath.parse(t);
            if (c != null && open && hexBox.isFocused()) hsv = ColorMath.toHsv(c);
        });
    }

    boolean isOpen() {
        return open;
    }

    /** @param onOk receives the chosen colour as {@code #AARRGGBB} */
    void open(String title, int argb, Consumer<String> onOk) {
        this.open = true;
        this.title = title;
        this.onOk = onOk;
        this.pickOld = argb;
        this.hsv = ColorMath.toHsv(argb);
        this.drag = Drag.NONE;
        hexBox.setValue(ColorMath.hex(argb));
        hexBox.setFocused(false);
    }

    void close() {
        open = false;
        drag = Drag.NONE;
        hexBox.setFocused(false);
    }

    /** The hex field while it has focus, or null. */
    EditBox typingBox() {
        return open && hexBox.isFocused() ? hexBox : null;
    }

    void released() {
        drag = Drag.NONE;
    }

    static void checker(GuiGraphics g, int x, int y, int w, int h) {
        for (int cy = 0; cy < h; cy += 4) {
            for (int cx = 0; cx < w; cx += 4) {
                Gfx.fill(g, x + cx, y + cy, Math.min(x + cx + 4, x + w), Math.min(y + cy + 4, y + h),
                        ((cx / 4 + cy / 4) & 1) == 0 ? 0xFF9A9A9A : 0xFF6A6A6A);
            }
        }
    }

    private int px() {
        return (sw - PICK_W) / 2;
    }

    private int py() {
        return (sh - PICK_H) / 2;
    }

    private int current() {
        return ColorMath.fromHsv(hsv[0], hsv[1], hsv[2], hsv[3]);
    }

    private void updateDrag(int mx, int my) {
        final float fx = Math.max(0, Math.min(1, (mx - (px() + 12)) / (float) (SV - 1)));
        final float fy = Math.max(0, Math.min(1, (my - (py() + 28)) / (float) (SV - 1)));
        switch (drag) {
            case SV -> {
                hsv[1] = fx;
                hsv[2] = 1 - fy;
            }
            case HUE -> hsv[0] = fy * 360f;
            case ALPHA -> hsv[3] = fx;
            default -> { }
        }
        if (drag != Drag.NONE) hexBox.setValue(ColorMath.hex(current()));
    }

    private static void textButton(GuiGraphics g, Font font, int x, int y, int w, String text, boolean hot) {
        Draw.neiButton(g, x, y, w, 20, hot ? 2 : 1);
        Gfx.text(g, font, Component.literal(text), x + (w - font.width(text)) / 2, y + 6, hot ? 0xFFFFFFA0 : 0xFFE0E0E0, true);
    }

    void render(GuiGraphics g, Font font, int mx, int my, int sw, int sh) {
        this.sw = sw;
        this.sh = sh;
        if (drag != Drag.NONE) updateDrag(mx, my);
        final int x = px();
        final int y = py();
        Gfx.fill(g, 0, 0, sw, sh, 0xA0000000);
        Draw.panel(g, x, y, PICK_W, PICK_H);
        Gfx.text(g, font, Component.literal(title), x + 12, y + 10, 0xFF404040, false);

        final int cells = SV / CELL;
        for (int cy = 0; cy < cells; cy++) {
            for (int cx = 0; cx < cells; cx++) {
                Gfx.fill(g, x + 12 + cx * CELL, y + 28 + cy * CELL, x + 12 + (cx + 1) * CELL, y + 28 + (cy + 1) * CELL,
                        ColorMath.fromHsv(hsv[0], cx / (float) (cells - 1), 1 - cy / (float) (cells - 1), 1));
            }
        }
        // hue bar
        for (int cy = 0; cy < cells; cy++) {
            Gfx.fill(g, x + 150, y + 28 + cy * CELL, x + 164, y + 28 + (cy + 1) * CELL,
                    ColorMath.fromHsv(cy / (float) (cells - 1) * 360f, 1, 1, 1));
        }
        // alpha bar
        checker(g, x + 12, y + 164, SV, 12);
        for (int cx = 0; cx < cells; cx++) {
            Gfx.fill(g, x + 12 + cx * CELL, y + 164, x + 12 + (cx + 1) * CELL, y + 176,
                    ColorMath.fromHsv(hsv[0], hsv[1], hsv[2], cx / (float) (cells - 1)));
        }

        // markers
        final int sx = x + 12 + Math.round(hsv[1] * (SV - 1));
        final int sy = y + 28 + Math.round((1 - hsv[2]) * (SV - 1));
        Gfx.fill(g, sx - 3, sy - 3, sx + 4, sy - 2, 0xFFFFFFFF);
        Gfx.fill(g, sx - 3, sy + 3, sx + 4, sy + 4, 0xFFFFFFFF);
        Gfx.fill(g, sx - 3, sy - 3, sx - 2, sy + 4, 0xFFFFFFFF);
        Gfx.fill(g, sx + 3, sy - 3, sx + 4, sy + 4, 0xFFFFFFFF);
        final int hy = y + 28 + Math.round(hsv[0] / 360f * (SV - 1));
        Gfx.fill(g, x + 148, hy, x + 166, hy + 1, 0xFFFFFFFF);
        final int ax = x + 12 + Math.round(hsv[3] * (SV - 1));
        Gfx.fill(g, ax, y + 162, ax + 1, y + 178, 0xFFFFFFFF);

        // old / new swatches
        Gfx.text(g, font, Component.literal("Old"), x + 180, y + 28, 0xFF404040, false);
        checker(g, x + 180, y + 38, 40, 24);
        Gfx.fill(g, x + 180, y + 38, x + 220, y + 62, pickOld);
        Gfx.text(g, font, Component.literal("New"), x + 230, y + 28, 0xFF404040, false);
        checker(g, x + 230, y + 38, 40, 24);
        Gfx.fill(g, x + 230, y + 38, x + 270, y + 62, current());

        Gfx.text(g, font, Component.literal("Hex #AARRGGBB"), x + 180, y + 74, 0xFF404040, false);
        hexBox.setX(x + 180);
        hexBox.setY(y + 86);
        hexBox.setWidth(90);
        if (!hexBox.isFocused() && drag == Drag.NONE && !hexBox.getValue().equalsIgnoreCase(ColorMath.hex(current()))) {
            hexBox.setValue(ColorMath.hex(current()));
        }
        hexBox.render(g, mx, my, 0f);

        final int by = y + PICK_H - 34;
        textButton(g, font, x + 180, by, 44, "OK", Draw.in(mx, my, x + 180, by, 44, 20));
        textButton(g, font, x + 228, by, 44, "Cancel", Draw.in(mx, my, x + 228, by, 44, 20));
    }

    /** A mouse press while the popup is open. */
    void click(double mx, double my, int button) {
        final int x = px();
        final int y = py();
        if (button != 0) return;
        final int by = y + PICK_H - 34;
        if (Draw.in(mx, my, x + 180, by, 44, 20)) {
            onOk.accept(ColorMath.hex(current()));
            close();
            return;
        }
        if (Draw.in(mx, my, x + 228, by, 44, 20)) {
            close();
            return;
        }
        if (Draw.in(mx, my, x + 12, y + 28, SV, SV)) drag = Drag.SV;
        else if (Draw.in(mx, my, x + 150, y + 28, 14, SV)) drag = Drag.HUE;
        else if (Draw.in(mx, my, x + 12, y + 164, SV, 12)) drag = Drag.ALPHA;
        hexBox.setFocused(Draw.in(mx, my, hexBox.getX(), hexBox.getY(), hexBox.getWidth(), hexBox.getHeight()));
        if (drag != Drag.NONE) updateDrag((int) mx, (int) my);
    }
}
