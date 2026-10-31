package orsc;

import com.openrsc.data.DataFileDecrypter;
import com.openrsc.data.DataOperations;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.two.Fonts;
import orsc.graphics.two.MudClientGraphics;

/** No login or world mutation: real production event/layout/font/alpha-render path, schematic actors. */
public final class FloatingHitSplatFixture {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static int u24(byte[] b, int i) {
        return (b[i] & 255) << 16 | (b[i + 1] & 255) << 8 | (b[i + 2] & 255);
    }
    private static void fonts(Path root) throws Exception {
        byte[] file = Files.readAllBytes(root.resolve("Client_Base/Cache/video/library.orsc"));
        int plain = u24(file, 0), compressed = u24(file, 3);
        byte[] library = new byte[plain];
        if (plain == compressed) System.arraycopy(file, 6, library, 0, plain);
        else DataFileDecrypter.unpackData(library, plain, file, compressed, 6);
        for (String name : new String[] {"h11p.jf", "h12b.jf", "h12p.jf", "h13b.jf", "h14b.jf", "h16b.jf", "h20b.jf", "h24b.jf"}) {
            Fonts.addFont(DataOperations.loadData(name, 0, library));
        }
    }
    private static FloatingHitSplatState state() { return new FloatingHitSplatState(); }
    private static void behavior() {
        FloatingHitSplatState s = state();
        for (int t : new int[] {2, 5, 4, 1, 3, 0}) s.offer(t, t + 1, 0);
        int[] expected = {0, 3, 1, 4, 5, 2};
        for (int i = 0; i < expected.length; i++) {
            List<FloatingHitSplatState.Entry> values = s.advance(i * 140);
            check(values.get(values.size() - 1).type == expected[i], "simultaneous semantic order " + i);
            for (int j = 1; j < values.size(); j++) {
                check(values.get(j - 1).rise - values.get(j).rise >= FloatingHitSplatState.ROW_GAP - .01,
                    "same-owner values are separated");
            }
        }
        s = state();
        s.offer(5, 5, 19); s.offer(0, 8, 21);
        check(s.advance(22).get(0).type == 0, "semantic batch priority spans wall-clock boundaries");
        s = state();
        s.legacyDamage(17); s.typed(255, 17, 0);
        check(s.advance(0).size() == 1 && s.advance(0).get(0).type == ORSCharacter.HIT_SPLAT_STANDARD,
            "unknown typed event becomes one red number, preserving classic deduplication");
        check(FloatingHitSplatRenderer.textColor(255) == FloatingHitSplatRenderer.textColor(0)
            && FloatingHitSplatRenderer.backgroundColor(-1) == FloatingHitSplatRenderer.backgroundColor(0), "unknown color fallback is red");
        check(FloatingHitSplatRenderer.textColor(2) == FloatingHitSplatRenderer.textColor(5)
            && FloatingHitSplatRenderer.backgroundColor(2) == FloatingHitSplatRenderer.backgroundColor(5), "armor and jewelry share purple");
        check(FloatingHitSplatRenderer.textColor(6) != FloatingHitSplatRenderer.textColor(2), "burn has its own orange");
        s = state();
        s.offer(0, 11, 0); s.offer(0, 12, 0); s.offer(0, 13, 0);
        check(s.advance(0).get(0).amount == 11 && s.pendingCount() == 2, "repeated type preserved");
        check(s.advance(140).size() == 2 && s.pendingCount() == 1, "next repeated event activates");
        check(s.advance(140).get(0).rise > 10, "new values accelerate older values");
        s = state();
        s.legacyDamage(18); s.typed(0, 18, 0);
        check(s.advance(0).size() == 1 && s.pendingCount() == 0, "classic plus typed pair appears once");
        s.clear(); s.legacyDamage(21);
        check(s.advance(0).get(0).amount == 21, "classic-only fallback uses new presentation");
        check(s.advance(1800).get(0).opacity(1800) < 256, "end-of-life alpha fades");
        check(s.advance(2200).isEmpty() && !s.isActive(), "expiry clears activity");
        s = state();
        for (int i = 0; i < 100; i++) s.offer(0, i, 0);
        check(s.pendingCount() == FloatingHitSplatState.MAX_PENDING, "pending queue bounded");
        for (int now = 0; now < 1200; now += 20) {
            for (FloatingHitSplatState.Entry e : s.advance(now)) {
                check(e.rise < FloatingHitSplatState.MAX_RISE, "rise cap");
            }
            check(s.advance(now).size() <= FloatingHitSplatState.MAX_ACTIVE, "active queue bounded");
        }
        s.advance(4000);
        check(!s.isActive(), "backlog naturally drains");
        FloatingHitSplatState slow = state(), fast = state();
        slow.offer(0, 1, 0); fast.offer(0, 1, 0); slow.advance(0); fast.advance(0);
        for (int now = 20; now <= 1000; now += 20) slow.advance(now);
        for (int now = 10; now <= 1000; now += 10) fast.advance(now);
        check(Math.abs(slow.advance(1000).get(0).rise - fast.advance(1000).get(0).rise) < .001,
            "single value motion is frame-rate independent");
        ORSCharacter owner = new ORSCharacter();
        owner.floatingHitSplats.offer(3, 7, 0);
        check(owner.hasActiveHitSplats(), "healing-only activity");
        owner.clearTransientVisualState();
        check(!owner.hasActiveHitSplats(), "despawn/relog reset clears values");
    }
    private static void layoutAndCapture() {
        MudClientGraphics surface = new MudClientGraphics(400, 220, 1);
        FloatingHitSplatRenderer r = new FloatingHitSplatRenderer();
        ORSCharacter a = new ORSCharacter(), b = new ORSCharacter();
        a.serverIndex = 1; b.serverIndex = 2;
        a.floatingHitSplats.offer(0, 255, 0); b.floatingHitSplats.offer(5, 24, 0);
        r.beginFrame(); r.collect(a, 180, 120, 0, surface); r.collect(b, 183, 120, 0, surface);
        List<FloatingHitSplatRenderer.Placement> p = r.layout(400, 220);
        check(p.size() == 2, "both player/NPC style owners collected");
        check(p.get(0).x + p.get(0).width + 2 <= p.get(1).x, "crowd uses side-by-side space");
        check(Math.abs(p.get(1).x - (183 - p.get(1).width / 2)) <= 24, "ownership shift bounded");
        int oldX = p.get(0).x, oldY = p.get(0).y;
        r.beginFrame(); r.collect(a, 220, 140, 0, surface); r.collect(a, 220, 140, 0, surface);
        p = r.layout(400, 220);
        check(p.size() == 1, "duplicate scene draws do not duplicate values");
        check(p.get(0).x == oldX + 40 && p.get(0).y == oldY + 20, "value follows moving owner");
        check(p.get(0).y + p.get(0).height < 140 - 3, "number clears lifebar");
        surface.beginRenderer2DFrame();
        r.draw(surface);
        Renderer2DFrame frame = surface.consumeRenderer2DFrame();
        if (orsc.graphics.Renderer2DSettings.isOpenGLSpriteCaptureEnabled()) {
            check(frame.getSpriteCommands().length == 1, "native overlay captures one sprite per value");
            check(frame.getSpriteCommands()[0].getPhase() == Renderer2DFrame.Phase.WORLD_OVERLAY,
                "native overlay phase");
            check(frame.getSpriteCommands()[0].getAlpha() == 255, "native full alpha captured");
            check(Arrays.stream(frame.getSpriteCommands()[0].getSprite().getPixels()).anyMatch(pixel -> pixel != 0),
                "native replay sprite retains rasterized font pixels");
            r.beginFrame(); r.collect(a, 220, 140, 1800, surface);
            surface.beginRenderer2DFrame(); r.draw(surface);
            Renderer2DFrame fading = surface.consumeRenderer2DFrame();
            check(fading.getSpriteCommands().length == 1
                && fading.getSpriteCommands()[0].getAlpha() < 255, "native fade alpha captured");
        }
        check(Arrays.stream(surface.pixelData).anyMatch(pixel -> pixel != 0), "production renderer emits pixels");
    }

    private static void png(MudClientGraphics s, Path path) throws Exception {
        BufferedImage image = new BufferedImage(s.width2, s.height2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, s.width2, s.height2, s.pixelData, 0, s.width2);
        ImageIO.write(image, "png", path.toFile());
    }
    private static void actor(MudClientGraphics s, int x, int y, boolean player) {
        s.drawBoxAlpha(x - 9, y + 14, 18, 26, player ? 0x7891AD : 0x927353, 256);
        s.drawBoxAlpha(x - 6, y + 3, 12, 11, player ? 0xD1B79C : 0x9BAB78, 256);
        s.drawBoxAlpha(x - 8, y + 40, 6, 12, 0x383C44, 256);
        s.drawBoxAlpha(x + 2, y + 40, 6, 12, 0x383C44, 256);
        s.drawBoxAlpha(x - 15, y - 3, 22, 5, 0x48AD51, 256);
        s.drawBoxAlpha(x + 7, y - 3, 8, 5, 0xA9443A, 256);
    }
    private static void preview(Path out) throws Exception {
        Files.createDirectories(out);
        FloatingHitSplatRenderer[] renderers = {
            new FloatingHitSplatRenderer(FloatingHitSplatRenderer.Style.TEXT),
            new FloatingHitSplatRenderer(FloatingHitSplatRenderer.Style.BACKGROUND)};
        ORSCharacter[][] actors = new ORSCharacter[2][14];
        int[] types = {0, 3, 1, 4, 5, 2, 6};
        String[] names = {"Attack", "Healing", "Poison", "Summon", "Jewelry", "Armor", "Burn"};
        for (int col = 0; col < 2; col++) for (int i = 0; i < 14; i++) {
            actors[col][i] = new ORSCharacter(); actors[col][i].serverIndex = i;
        }
        for (int frame = 0; frame <= 150; frame++) {
            int now = frame * 40;
            MudClientGraphics s = new MudClientGraphics(960, 540, 1);
            Arrays.fill(s.pixelData, 0x222B2C);
            // Schematic scenery deliberately does not pretend to be an in-game capture.
            for (int y = 70; y < 520; y += 20) for (int x = (y % 40); x < 960; x += 40) {
                s.drawBoxAlpha(x, y, 38, 18, ((x + y) % 80 == 0) ? 0x303A38 : 0x293330, 256);
            }
            s.drawString("FLOATING VALUES / first implementation / native font at 1x", 18, 22, 0xE5E8DD, 1);
            s.drawString("Production overlay renderer; schematic actors. No game server or login.", 18, 43, 0xADB9B4, 0);
            s.drawLineVert(479, 60, 460, 0x59625D);
            for (int col = 0; col < 2; col++) {
                int base = col * 480;
                FloatingHitSplatRenderer r = renderers[col]; r.beginFrame();
                s.drawString(col == 0 ? "A / COLORED TEXT" : "B / TIGHT COLORED BACKGROUND", base + 18, 82, 0xF1E3BC, 1);
                for (int i = 0; i < 7; i++) {
                    int x = base + 36 + i * 68, y = 185;
                    if (now == 0 || now == 2400) actors[col][i].floatingHitSplats.offer(types[i], new int[] {28, 9, 3, 12, 6, 14, 1}[i], now);
                    actor(s, x, y, i % 2 == 0);
                    s.drawString(names[i], x - 22, y + 72, 0xD3D9D3, 0);
                    r.collect(actors[col][i], x, y, now, s);
                }
                s.drawString("Crowded owners / repeated hits / simultaneous burst", base + 18, 292, 0xC8D5D1, 0);
                for (int i = 7; i < 13; i++) {
                    int x = base + 105 + (i - 7) * 21;
                    int y = 392 + (i % 2) * 10;
                    if (now < 3600 && (frame + i * 2) % 10 == 0) actors[col][i].floatingHitSplats.offer(types[i - 7], 4 + i, now);
                    actor(s, x, y, i % 2 == 0);
                    r.collect(actors[col][i], x, y, now, s);
                }
                int x = base + 360 + (int) (Math.sin(now / 850.0) * 24), y = 392;
                if (now == 0 || now == 2400) {
                    for (int type : new int[] {5, 4, 1, 3, 0}) actors[col][13].floatingHitSplats.offer(type, 5 + type, now);
                }
                actor(s, x, y, true);
                r.collect(actors[col][13], x, y, now, s);
                s.drawString("Moving owner", base + 316, 466, 0xD3D9D3, 0);
                r.draw(s);
            }
            s.drawString("Red attacks / blue healing / green poison / yellow summons / purple armor + jewelry / orange burn", 18, 511, 0xC8D5D1, 0);
            s.drawString("t=" + now + "ms | 64px rise cap | 2.1s lifetime | finite queue; extreme crowding may still overlap", 18, 529, 0xADB9B4, 0);
            png(s, out.resolve(String.format("frame-%03d.png", frame)));
            if (now == 1000) png(s, out.resolve("comparison.png"));
        }
    }
    public static void main(String[] args) throws Exception {
        behavior(); fonts(Paths.get(args[0])); layoutAndCapture();
        System.out.println("PASS floating hit splat behavior/render checks: " + checks);
        if (args.length > 1) { preview(Paths.get(args[1])); System.out.println("Preview frames: " + args[1]); }
    }
}
