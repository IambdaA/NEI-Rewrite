package codechicken.nei.client;

import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.imageio.ImageIO;

import com.mojang.blaze3d.pipeline.RenderTarget;

import codechicken.nei.NotEnoughItems;
import codechicken.nei.bookmark.TreeCamera;
import codechicken.nei.bookmark.TreeLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;

/**
 * NEI's "Save tree as image". NEI draws the tree into an off-screen framebuffer at twice the size; the retained mode GUI
 * of 26.2 has no such thing, so the tree is drawn on its own (no toolbar; the stats panel is added under it) one screenful at a time, each
 * screenful is read back from the finished frame and the pieces are put together into one PNG in
 * {@code screenshots/}. The tree is shown at zoom 1 in GUI pixels, so a larger GUI scale gives a sharper picture.
 *
 * The window drives it: while {@link #busy()} it calls {@link #applyCamera} before drawing each frame; this class takes
 * the picture in {@link #onFrame} (the frame is complete then).
 */
final class TreeShot {

    private static final int SETTLE_FRAMES = 2;
    private static final int MAX_SIDE = 8000;
    private static final long MAX_PIXELS = 40_000_000L;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");
    private static final int MARGIN = 8; // world pixels around the tree, as in NEI's 16 total

    private static TreeShot active;

    private final TreeCamera camera;
    private final float savedX;
    private final float savedY;
    private final float savedZoom;
    private final int cols;
    private final int rows;
    private final int tileW; // pixels of one screenful of the viewport
    private final int tileH;
    private final int viewX; // viewport in framebuffer pixels
    private final int viewY;
    private final BufferedImage out;
    private final int[] stats; // the stats panel's area on screen (GUI pixels), or null
    private final int treeH; // pixel height of the tree part of the image
    private final double scale;
    private int tile;
    private int wait = SETTLE_FRAMES;
    private boolean pending;
    private boolean drawn;

    private TreeShot(TreeCamera camera, int cols, int rows, int tileW, int tileH, int viewX, int viewY, int totalW, int totalH,
            int[] stats, int treeH, double scale) {
        this.stats = stats;
        this.treeH = treeH;
        this.scale = scale;
        this.camera = camera;
        this.savedX = camera.scrollX;
        this.savedY = camera.scrollY;
        this.savedZoom = camera.zoomLevel;
        this.cols = cols;
        this.rows = rows;
        this.tileW = tileW;
        this.tileH = tileH;
        this.viewX = viewX;
        this.viewY = viewY;
        this.out = new BufferedImage(totalW, totalH, BufferedImage.TYPE_INT_ARGB);
    }

    static boolean busy() {
        return active != null;
    }

    static TreeShot active() {
        return active;
    }

    private static void say(String text) {
        final var player = Minecraft.getInstance().player;
        if (player != null) player.sendSystemMessage(Component.literal(text));
    }

    /** The last capture is the stats panel, which goes under the tree (NEI draws it there too). */
    boolean statsTile() {
        return stats != null && tile == cols * rows;
    }

    private int tiles() {
        return cols * rows + (stats != null ? 1 : 0);
    }

    /**
     * Starts a capture of the whole tree, or says why it cannot.
     *
     * @param statsArea the visible stats panel's {@link StatsPanel#contentArea()}, or null
     */
    static TreeShot begin(TreeCamera camera, TreeLayout layout, int[] statsArea) {
        if (active != null) return null;
        final double scale = Minecraft.getInstance().getWindow().getGuiScale();
        final int worldW = layout.treeWidth + MARGIN * 2;
        final int worldH = layout.treeHeight + MARGIN * 2;
        final int treeW = (int) Math.round(worldW * scale);
        final int treeHeight = (int) Math.round(worldH * scale);
        final int totalW = statsArea == null ? treeW : Math.max(treeW, (int) Math.round(statsArea[2] * scale));
        final int totalH = treeHeight + (statsArea == null ? 0 : (int) Math.round(statsArea[3] * scale));
        if (totalW > MAX_SIDE || totalH > MAX_SIDE || (long) totalW * totalH > MAX_PIXELS) {
            say("The tree is too large for an image at this GUI scale (try a smaller one)");
            return null;
        }
        final int tileW = (int) Math.round(camera.widgetW * scale);
        final int tileH = (int) Math.round(camera.widgetH * scale);
        if (tileW <= 0 || tileH <= 0) return null;

        final int cols = (int) Math.ceil((double) worldW / camera.widgetW);
        final int rows = (int) Math.ceil((double) worldH / camera.widgetH);
        active = new TreeShot(camera, cols, rows, tileW, tileH, (int) Math.round(camera.widgetX * scale),
                (int) Math.round(camera.widgetY * scale), totalW, totalH, statsArea, treeHeight, scale);
        return active;
    }

    /** Called by the window before it draws the tree: shows the screenful that is being captured. */
    void applyCamera() {
        if (!statsTile()) {
            camera.zoomLevel = 1f;
            camera.scrollX = -MARGIN + (tile % cols) * camera.widgetW;
            camera.scrollY = -MARGIN + (tile / cols) * camera.widgetH;
        }
        drawn = true;
    }

    private void finish(boolean saved) {
        camera.scrollX = savedX;
        camera.scrollY = savedY;
        camera.zoomLevel = savedZoom;
        active = null;
        if (!saved) {
            say("Tree screenshot cancelled");
            return;
        }

        final Minecraft mc = Minecraft.getInstance();
        final Path dir = mc.gameDirectory.toPath().resolve("screenshots");
        final Path file = dir.resolve("crafting_tree_" + DATE.format(LocalDateTime.now()) + ".png");
        final BufferedImage image = out;
        new Thread(() -> {
            try {
                Files.createDirectories(dir);
                ImageIO.write(image, "png", file.toFile());
                mc.execute(() -> {
                    final var player = mc.player;
                    if (player == null) return;
                    final File f = file.toFile();
                    player.sendSystemMessage(Component.literal("Saved tree image as ").append(Component.literal(f.getName())
                            .withStyle(style -> style.withUnderlined(true).withClickEvent(new ClickEvent.OpenFile(f.getAbsolutePath())))));
                });
            } catch (Exception e) {
                NotEnoughItems.LOGGER.warn("Could not save the tree image: {}", e.toString());
                mc.execute(() -> say("Could not save the tree image: " + e.getMessage()));
            }
        }, "NEI-tree-image").start();
    }

    private static RenderTarget target;
    private static boolean searched;

    /**
     * The window's framebuffer. Found by its type because Minecraft's accessor for it has been renamed between versions
     * (the game is not obfuscated, so the names can be read at run time). Looks at the game and the game renderer, public
     * or not, methods and fields; whatever looks like a render target is logged (debug level) so a miss can be fixed from the log.
     */
    private static RenderTarget mainTarget() {
        if (target != null || searched) return target;
        searched = true;
        final Minecraft mc = Minecraft.getInstance();
        final java.util.List<Object> owners = new java.util.ArrayList<>();
        owners.add(mc);
        for (Field f : Minecraft.class.getDeclaredFields()) { // the renderer, found by type so its visibility does not matter
            if (f.getType().getSimpleName().equals("GameRenderer")) {
                try {
                    f.setAccessible(true);
                    owners.add(f.get(mc));
                } catch (ReflectiveOperationException | RuntimeException ignored) {}
            }
        }
        for (Object owner : owners) {
            if (owner == null) continue;
            for (Class<?> c = owner.getClass(); c != null && c != Object.class && target == null; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (f.getType().getName().contains("Target")) NotEnoughItems.LOGGER.debug("TreeShot: field {}.{} : {}", c.getSimpleName(), f.getName(), f.getType().getName());
                    if (target == null && RenderTarget.class.isAssignableFrom(f.getType())) target = read(f, owner);
                }
                for (Method m : c.getDeclaredMethods()) {
                    if (m.getParameterCount() != 0) continue;
                    if (m.getReturnType().getName().contains("Target")) NotEnoughItems.LOGGER.debug("TreeShot: method {}.{}() : {}", c.getSimpleName(), m.getName(), m.getReturnType().getName());
                    if (target == null && RenderTarget.class.isAssignableFrom(m.getReturnType())) target = call(m, owner);
                }
            }
        }
        if (target == null) NotEnoughItems.LOGGER.warn("TreeShot: no render target found on Minecraft or GameRenderer");
        return target;
    }

    private static RenderTarget read(Field f, Object owner) {
        try {
            f.setAccessible(true);
            return (RenderTarget) f.get(owner);
        } catch (ReflectiveOperationException | RuntimeException e) {
            NotEnoughItems.LOGGER.warn("TreeShot: cannot read {}: {}", f.getName(), e.toString());
            return null;
        }
    }

    private static RenderTarget call(Method m, Object owner) {
        try {
            m.setAccessible(true);
            return (RenderTarget) m.invoke(owner);
        } catch (ReflectiveOperationException | RuntimeException e) {
            NotEnoughItems.LOGGER.warn("TreeShot: cannot call {}: {}", m.getName(), e.toString());
            return null;
        }
    }
    static void onFrame() {
        final TreeShot shot = active;
        if (shot == null || shot.pending) return;
        if (!shot.drawn) { // the window closed (or something else covered the tree): give up
            shot.finish(false);
            return;
        }
        shot.drawn = false;
        if (shot.wait > 0) {
            shot.wait--;
            return;
        }

        final RenderTarget main = mainTarget();
        if (main == null) { // never hand null to Screenshot: that crashes the game
            shot.finish(false);
            say("Tree screenshot is not supported here (see latest.log, lines starting with TreeShot)");
            return;
        }
        shot.pending = true;
        Screenshot.takeScreenshot(main, image -> {
            if (shot.statsTile()) { // under the tree, from the left
                final int w = Math.min((int) Math.round(shot.stats[2] * shot.scale), shot.out.getWidth());
                final int h = Math.min((int) Math.round(shot.stats[3] * shot.scale), shot.out.getHeight() - shot.treeH);
                final int fromX = (int) Math.round(shot.stats[0] * shot.scale);
                final int fromY = (int) Math.round(shot.stats[1] * shot.scale);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        if (fromX + x < image.getWidth() && fromY + y < image.getHeight()) {
                            shot.out.setRGB(x, shot.treeH + y, image.getPixel(fromX + x, fromY + y) | 0xFF000000);
                        }
                    }
                }
            } else {
                final int col = shot.tile % shot.cols;
                final int row = shot.tile / shot.cols;
                final int dx = col * shot.tileW;
                final int dy = row * shot.tileH;
                final int w = Math.min(shot.tileW, shot.out.getWidth() - dx);
                final int h = Math.min(shot.tileH, shot.treeH - dy);
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        final int sx = shot.viewX + x;
                        final int sy = shot.viewY + y;
                        if (sx < image.getWidth() && sy < image.getHeight()) shot.out.setRGB(dx + x, dy + y, image.getPixel(sx, sy) | 0xFF000000);
                    }
                }
            }
            image.close();

            shot.tile++;
            shot.wait = SETTLE_FRAMES;
            shot.pending = false;
            if (shot.tile >= shot.tiles()) shot.finish(true);
        });
    }
}
