package orsc;

import com.openrsc.client.model.Sprite;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import javax.imageio.ImageIO;
import orsc.graphics.two.MudClientGraphics;

/** Pixel bounds and glyph-preservation checks for every transmitted amount, using native fonts. */
public final class HitSplatCenteringFixture {
    private static int checks;
    private static void check(boolean value, String why) {
        checks++; if (!value) throw new AssertionError(why);
    }
    private static Sprite sprite(FloatingHitSplatRenderer renderer, int type, int value, MudClientGraphics surface) throws Exception {
        Method method = FloatingHitSplatRenderer.class.getDeclaredMethod("sprite", int.class, int.class, MudClientGraphics.class);
        method.setAccessible(true);
        return (Sprite) method.invoke(renderer, type, value, surface);
    }
    private static int[] bounds(Sprite sprite) {
        int left = sprite.getWidth(), top = sprite.getHeight(), right = -1, bottom = -1, count = 0;
        for (int y = 0; y < sprite.getHeight(); y++) for (int x = 0; x < sprite.getWidth(); x++) {
            if (sprite.getPixels()[y * sprite.getWidth() + x] == 0xFFFFFF) {
                left = Math.min(left, x); right = Math.max(right, x);
                top = Math.min(top, y); bottom = Math.max(bottom, y); count++;
            }
        }
        return new int[] {left, top, right, bottom, count};
    }
    private static Sprite before(int value, MudClientGraphics surface, boolean background) {
        String digits = Integer.toString(value);
        int w = surface.stringWidth(0, digits) + 4, h = surface.fontHeight(0) + 2;
        MudClientGraphics old = new MudClientGraphics(w, h, 1); old.beginSoftwareOwnedRenderer2DFrame();
        if (background) old.drawBoxAlpha(0, 0, w, h, FloatingHitSplatRenderer.backgroundColor(0), 256);
        old.drawShadowText(digits, 2, h - 3, background ? 0xFFFFFF : FloatingHitSplatRenderer.textColor(0), 0, false);
        return new Sprite(old.pixelData, w, h);
    }
    public static void main(String[] args) throws Exception {
        Method fonts = FloatingHitSplatFixture.class.getDeclaredMethod("fonts", Path.class);
        fonts.setAccessible(true); fonts.invoke(null, Paths.get(args[0]));
        MudClientGraphics surface = new MudClientGraphics(1000, 380, 1);
        FloatingHitSplatRenderer background = new FloatingHitSplatRenderer(FloatingHitSplatRenderer.Style.BACKGROUND);
        FloatingHitSplatRenderer text = new FloatingHitSplatRenderer(FloatingHitSplatRenderer.Style.TEXT);
        for (int value = 0; value <= 255; value++) {
            String digits = Integer.toString(value);
            MudClientGraphics reference = new MudClientGraphics(100, 40, 1); reference.beginSoftwareOwnedRenderer2DFrame();
            reference.drawString(digits, 10, 25, 0xFFFFFF, 0);
            int referenceCount = bounds(new Sprite(reference.pixelData, 100, 40))[4];
            for (int type = 0; type < ORSCharacter.HIT_SPLAT_COUNT; type++) {
                Sprite result = sprite(background, type, value, surface);
                int[] b = bounds(result);
                check(b[0] == 2 && result.getWidth() - b[2] - 1 == 2, "symmetric visible horizontal padding: " + value);
                check(b[1] == 2 && result.getHeight() - b[3] - 1 == 2, "symmetric visible vertical padding: " + value);
                check(b[4] == referenceCount && b[4] > 0, "all foreground glyph pixels retained: " + value);
                check(result.getHeight() + 2 <= FloatingHitSplatState.ROW_GAP, "glyph background fits existing row spacing");
                check(result.getPixels()[0] == FloatingHitSplatRenderer.backgroundColor(type), "type color retained");
            }
            Sprite unchangedText = sprite(text, 0, value, surface), oldText = before(value, surface, false);
            check(unchangedText.getWidth() == oldText.getWidth() && unchangedText.getHeight() == oldText.getHeight()
                && Arrays.equals(unchangedText.getPixels(), oldText.getPixels()), "accepted text-only raster unchanged: " + value);
        }
        System.out.println("PASS hit-number pixel centering / preserved glyphs and text-only appearance: " + checks);
        if (args.length > 1) {
            Arrays.fill(surface.pixelData, 0x222B2C);
            surface.drawString("Before: font advance and line height", 18, 25, 0xFFFFFF, 1);
            surface.drawString("Native size, then 4x pixel enlargement", 18, 45, 0xB8C9C4, 0);
            surface.drawString("After: actual digit pixels centered with equal padding", 18, 212, 0xFFFFFF, 1);
            int[] values = {0, 1, 7, 11, 28, 70, 100, 111, 255};
            for (int i = 0; i < values.length; i++) {
                int x = 60 + i * 105;
                Sprite old = before(values[i], surface, true), result = sprite(background, 0, values[i], surface);
                surface.drawSprite(old, x - old.getWidth() / 2, 65);
                surface.drawSprite(old, x - old.getWidth() * 2, 105, old.getWidth() * 4, old.getHeight() * 4, 5924);
                surface.drawSprite(result, x - result.getWidth() / 2, 235);
                surface.drawSprite(result, x - result.getWidth() * 2, 275, result.getWidth() * 4, result.getHeight() * 4, 5924);
                int[] a = bounds(old), b = bounds(result);
                System.out.println(values[i] + " padding L/T/R/B before=" + a[0] + "/" + a[1] + "/"
                    + (old.getWidth() - a[2] - 1) + "/" + (old.getHeight() - a[3] - 1)
                    + " after=" + b[0] + "/" + b[1] + "/" + (result.getWidth() - b[2] - 1) + "/" + (result.getHeight() - b[3] - 1));
            }
            surface.drawString("Movement, timing, priority and text-only pixels are unchanged. Background colors are unchanged.", 18, 360, 0xB8C9C4, 0);
            Path out = Paths.get(args[1]); Files.createDirectories(out);
            BufferedImage image = new BufferedImage(surface.width2, surface.height2, BufferedImage.TYPE_INT_RGB);
            image.setRGB(0, 0, surface.width2, surface.height2, surface.pixelData, 0, surface.width2);
            ImageIO.write(image, "png", out.resolve("centering-detail.png").toFile());
        }
    }
}
