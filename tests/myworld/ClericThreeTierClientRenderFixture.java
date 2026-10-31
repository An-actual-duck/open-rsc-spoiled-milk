package orsc;

import com.openrsc.client.entityhandling.defs.*;
import com.openrsc.data.*;
import com.openrsc.interfaces.misc.ClericSpellGuideCatalog;
import java.awt.image.BufferedImage;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import orsc.graphics.two.*;

/** Offscreen real native font + actual mudclient tooltip renderer; no desktop or login. */
public final class ClericThreeTierClientRenderFixture {
  static int u24(byte[] b, int i) {
    return (b[i] & 255) << 16 | (b[i + 1] & 255) << 8 | (b[i + 2] & 255);
  }

  static void fonts(Path root) throws Exception {
    byte[] file = Files.readAllBytes(root.resolve("Client_Base/Cache/video/library.orsc"));
    int plain = u24(file, 0), compressed = u24(file, 3);
    byte[] library = new byte[plain];
    if (plain == compressed) System.arraycopy(file, 6, library, 0, plain);
    else DataFileDecrypter.unpackData(library, plain, file, compressed, 6);
    for (String name :
        new String[] {
          "h11p.jf", "h12b.jf", "h12p.jf", "h13b.jf", "h14b.jf", "h16b.jf", "h20b.jf", "h24b.jf"
        }) Fonts.addFont(DataOperations.loadData(name, 0, library));
  }

  static void png(MudClientGraphics surface, Path path) throws Exception {
    BufferedImage image =
        new BufferedImage(surface.width2, surface.height2, BufferedImage.TYPE_INT_RGB);
    image.setRGB(0, 0, surface.width2, surface.height2, surface.pixelData, 0, surface.width2);
    ImageIO.write(image, "png", path.toFile());
  }

  static void invoke(mudclient mc, String name, Class<?>[] types, Object... args) throws Exception {
    Method method = mudclient.class.getDeclaredMethod(name, types);
    method.setAccessible(true);
    method.invoke(mc, args);
  }

  public static void main(String[] args) throws Exception {
    Path root = Paths.get(args[0]), out = Paths.get(args[2]);
    Files.createDirectories(out);
    fonts(root);
    Config.S_WANT_CUSTOM_SPRITES = true;
    com.openrsc.client.entityhandling.EntityHandler.load(true);
    List<ClericSpellDef> defs =
        ClericSpellbookPacketDecoder.decode(Files.readAllBytes(Paths.get(args[1])));
    ClericSpellbookCatalog catalog = new ClericSpellbookCatalog();
    catalog.replace(3, defs);
    mudclient mc =
        (mudclient) ClericThreeTierClientFixture.unsafe().allocateInstance(mudclient.class);
    ClericThreeTierClientFixture.set(mc, "clericSpellbook", catalog);
    ClericThreeTierClientFixture.set(
        mc, "clericIconSprites", new HashMap<Integer, com.openrsc.client.model.Sprite>());
    ClericThreeTierClientFixture.set(mc, "externalAssetLoader", new ClientExternalAssetLoader());
    ClericThreeTierClientFixture.set(mc, "playerStatEquipment", new int[8]);
    ClericThreeTierClientFixture.set(mc, "gameWidth", 960);
    ClericThreeTierClientFixture.set(mc, "gameHeight", 350);
    mc.playerStatEquipment[7] = 160;
    mc.setClericHolyPowerKnown(true);
    MudClientGraphics surface = new MudClientGraphics(960, 350, 1);
    mc.setSurface(surface);
    Arrays.fill(surface.pixelData, 0x22262b);
    int i = 0;
    for (int code : new int[] {14, 17, 20}) {
      ClericSpellDef d = catalog.get(code);
      surface.drawString(d.getName(), 10 + i * 315, 28, 0xffffff, 1);
      invoke(
          mc,
          "drawClericPowerTooltip",
          new Class<?>[] {ClericSpellDef.class, int.class, int.class},
          d,
          306 + i * 315,
          45);
      i++;
    }
    png(surface, out.resolve("native-power-tooltips.png"));
    ArrayList<String> measurements = new ArrayList<String>();
    for (ClericSpellDef d : defs) {
      int width = surface.stringWidth(0, d.getName());
      if (width > 182)
        throw new AssertionError("Book name does not fit: " + d.getName() + " " + width);
      measurements.add(d.getName() + ": " + width + "px / 182px available");
    }
    surface = new MudClientGraphics(960, 350, 1);
    mc.setSurface(surface);
    Arrays.fill(surface.pixelData, 0x22262b);
    ActiveStatusHudModel hud = new ActiveStatusHudModel();
    ClericThreeTierClientFixture.definitions = defs;
    hud.replace(
        ActiveStatusPacketDecoder.decode(ClericThreeTierClientFixture.statuses(14, 17, 20)),
        catalog,
        System.currentTimeMillis());
    ClericThreeTierClientFixture.set(mc, "activeStatusHud", hud);
    mc.mouseX = 12;
    mc.mouseY = 154;
    invoke(mc, "loadExternalClericIconSprites", new Class<?>[] {});
    invoke(mc, "drawActivePotionEffectsHud", new Class<?>[] {});
    png(surface, out.resolve("native-status-hud.png"));
    surface = new MudClientGraphics(460, 210, 1);
    Arrays.fill(surface.pixelData, 0x22262b);
    ClericSpellGuideCatalog.Entry chosen = null;
    for (ClericSpellGuideCatalog.Entry entry :
        ClericSpellGuideCatalog.build(
            defs, id -> com.openrsc.client.entityhandling.EntityHandler.getItemDef(id).getName()))
      if (entry.getStableCode() == 17) chosen = entry;
    for (ClericSpellGuideCatalog.Entry entry :
        ClericSpellGuideCatalog.build(
            defs, id -> com.openrsc.client.entityhandling.EntityHandler.getItemDef(id).getName()))
      for (String text : entry.getLines())
        if (surface.stringWidth(1, text) > 410)
          throw new AssertionError("Real guide line exceeds width: " + text);
    System.out.println("GUIDE LINES " + chosen.getLines());
    surface.drawString(chosen.getHeader(), 10, 22, 0xffffff, 2);
    int line = 0;
    for (String text : chosen.getLines()) {
      int width = surface.stringWidth(1, text);
      if (width > 410) throw new AssertionError("Guide line too wide " + width + ": " + text);
      surface.drawString(text, 10, 42 + (line++) * 13, 0xffffff, 1);
    }
    png(surface, out.resolve("native-guide-row.png"));
    Files.write(out.resolve("native-font-widths.txt"), measurements);
    System.out.println(
        "PASS native font bounds for all11 names, actual mudclient power tooltip/HUD rendering and"
            + " detailed guide row: "
            + out);
  }
}
