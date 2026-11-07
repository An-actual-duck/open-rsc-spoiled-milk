package orsc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.data.DataFileDecrypter;
import com.openrsc.data.DataOperations;
import com.openrsc.interfaces.misc.HiscoreData;
import com.openrsc.interfaces.misc.SkillGuideInterface;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import orsc.graphics.Renderer2DFrame;
import orsc.graphics.two.Fonts;
import orsc.graphics.two.MudClientGraphics;
import sun.misc.Unsafe;

/** Actual native guide font, viewport and scrollbar checks, without login or a server. */
public final class RuneAltarGuideClientFixture {

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static int u24(byte[] bytes, int offset) {
        return (bytes[offset] & 255) << 16 | (bytes[offset + 1] & 255) << 8
            | bytes[offset + 2] & 255;
    }

    private static void fonts(Path root) throws Exception {
        byte[] file = Files.readAllBytes(root.resolve("Client_Base/Cache/video/library.orsc"));
        int plain = u24(file, 0), compressed = u24(file, 3);
        byte[] library = new byte[plain];
        if (plain == compressed) System.arraycopy(file, 6, library, 0, plain);
        else DataFileDecrypter.unpackData(library, plain, file, compressed, 6);
        for (String name : new String[]{"h11p.jf", "h12b.jf", "h12p.jf", "h13b.jf",
                "h14b.jf", "h16b.jf", "h20b.jf", "h24b.jf"}) {
            Fonts.addFont(DataOperations.loadData(name, 0, library));
        }
    }

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args[0]), out = Paths.get(args[1]);
        Files.createDirectories(out);
        Config.F_CACHE_DIR = root.resolve("Client_Base/Cache").toString();
        Config.S_WANT_CUSTOM_SPRITES = true;
        Config.S_WANT_HISCORES = false;
        EntityHandler.load(true);
        fonts(root);
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        Method choose = mudclient.class.getDeclaredMethod("setSkillGuideChosen", String.class);
        choose.setAccessible(true);
        Field entries = SkillGuideInterface.class.getDeclaredField("skillMenuEntries");
        entries.setAccessible(true);
        Class<?> entryClass = Class.forName("com.openrsc.interfaces.misc.SkillMenuEntry");
        Method detail = entryClass.getDeclaredMethod("getSkillDetail");
        detail.setAccessible(true);
        List<String> measurements = new ArrayList<String>();
        int maximumWidth = 0;
        for (int[] size : new int[][]{{512, 334}, {960, 528}}) {
            for (String alias : new String[]{"Enchanting", "Magecraft", "Runecraft"}) {
                mudclient mc = (mudclient) unsafe.allocateInstance(mudclient.class);
                MudClientGraphics surface = new MudClientGraphics(size[0], size[1], 5000);
                surface.fillSpriteTree();
                mc.setSurface(surface);
                set(mc, "externalAssetLoader", new ClientExternalAssetLoader(root, RuneAltarGuideClientFixture.class));
                set(mc, "gameWidth", size[0]);
                set(mc, "gameHeight", size[1]);
                mc.hiscoreData = new HiscoreData();
                choose.invoke(mc, alias);
                SkillGuideInterface guide = new SkillGuideInterface(mc);
                guide.curTab = mc.skillGuideChosenTabs.indexOf("Altars");
                require(guide.curTab >= 0, alias + " must expose Altars tab");
                guide.populateSkillItems();
                List<?> rows = (List<?>) entries.get(guide);
                require(rows.size() == 14, "all rune rows remain present");
                for (Object row : rows) {
                    String text = (String) detail.invoke(row);
                    int width = surface.stringWidth(2, text);
                    maximumWidth = Math.max(maximumWidth, width);
                    require(width <= 335, "guide text exceeds available width: " + text);
                    if (size[0] == 512 && alias.equals("Enchanting")) {
                        measurements.add(width + "px / 335px: " + text);
                    }
                }
                for (int scroll : new int[]{0, 4, 1000}) {
                    guide.skillGuide.resetScrollIndex(guide.skillGuideScroll);
                    guide.drawSkillItems();
                    guide.skillGuide.scrollMethodList(guide.skillGuideScroll, scroll);
                    Arrays.fill(surface.pixelData, 0x22262b);
                    surface.beginRenderer2DFrame();
                    guide.onRender(surface);
                    int position = guide.skillGuide.getScrollPosition(guide.skillGuideScroll);
                    if (scroll == 1000) require(position == 8, "final six rows must be reachable");
                    Renderer2DFrame frame = surface.consumeRenderer2DFrame();
                    require(frame.getTextCommands().length > 0, "actual guide emits native text");
                    int left = (size[0] - 430) / 2, top = (size[1] - 320) / 2;
                    for (Renderer2DFrame.TextCommand command : frame.getTextCommands()) {
                        for (Renderer2DFrame.TextCommand.GlyphCommand glyph : command.getGlyphs()) {
                            require(glyph.getX() >= left && glyph.getX() + glyph.getWidth() <= left + 430,
                                "guide glyph crosses horizontal panel bounds");
                            require(glyph.getY() >= top && glyph.getY() + glyph.getHeight() <= size[1],
                                "guide glyph crosses vertical viewport bounds");
                        }
                    }
                    if (alias.equals("Enchanting")) {
                        BufferedImage image = new BufferedImage(size[0], size[1], BufferedImage.TYPE_INT_RGB);
                        image.setRGB(0, 0, size[0], size[1], surface.pixelData, 0, size[0]);
                        ImageIO.write(image, "png", out.resolve("runes-" + size[0] + "-row-" + position + ".png").toFile());
                    }
                }
            }
        }
        Files.write(out.resolve("native-font-widths.txt"), measurements);
        System.out.println("PASS: actual rune guide renders all three aliases at two viewports, "
            + "all 14 lines fit (maximum " + maximumWidth + "/335px), final scroll reaches row 8");
    }
}
