package orsc;

import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;
import java.nio.file.Paths;

/** Exercise packaged lookup without any development asset directory. */
public final class SlayerMaterialIconsFixture {
 public static void main(String[] args) {
  Config.S_WANT_BANK_NOTES = true;
  Config.S_WANT_CERT_AS_NOTES = true;
  EntityHandler.load(true);
  ClientExternalAssetLoader loader = new ClientExternalAssetLoader(Paths.get("/nonexistent/slayer-icons-test"), mudclient.class);
  for (String arg : args) {
   String[] fields = arg.split("\\|"); int id = Integer.parseInt(fields[0]);
   ItemDef item = EntityHandler.getItemDef(id);
   if (item.spriteID != -1 || !fields[1].equals(item.spriteLocation) || item.getPictureMask() != 0)
    throw new AssertionError("Icon definition " + id);
   if (loader.getExternalItemSprite(item) == null) throw new AssertionError("Packaged icon missing " + id);
   if (item.basePrice != Integer.parseInt(fields[2])) throw new AssertionError("Price " + id);
   if (item.noteable) {
    ItemDef note = EntityHandler.getItemDef(id, true);
    if (note == null || !note.stackable || !note.getName().equals(item.getName())) throw new AssertionError("Note " + id);
   }
  }
  if (args.length != 38) throw new AssertionError("Expected all 38 active mappings");
  System.out.println("PASS: all 38 runtime icons, doses, masks, prices, notes and packaged-only image loading");
 }
}
