package orsc;
import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.SlayerMovementPreview;
import orsc.graphics.two.SpriteArchive.Entry;
import java.nio.file.Paths;
import java.io.File;
/** Real disk/JAR icon and eighteen-frame loading, preserving published equipment IDs. */
public final class StirgeClientFixture {
 public static void main(String[]args) {
  Config.S_WANT_CUSTOM_SPRITES=Boolean.parseBoolean(args[1]);EntityHandler.load(true);
  SlayerMovementPreview p=SlayerMovementPreview.forNpc(882);
  if(p!=SlayerMovementPreview.STIRGE||p.loadedFrameCount()!=18||p.frameHeight()!=160||p.cameraWidth()!=315||p.cameraHeight()!=240)throw new AssertionError("Stirge presentation contract");
  if(!EntityHandler.getNpcDef(882).getName().equals("Stirge"))throw new AssertionError("NPC identity");
  ClientExternalAssetLoader loader=new ClientExternalAssetLoader(Paths.get(args[0]),StirgeClientFixture.class);
  File source=loader.findFirstFile(new String[]{"dev/myworld/assets/runtime/sprites/npcs/slayer-monsters"},"stirge.png");
  Entry entry=loader.loadExternalNpcDirectionSheet(source,p.animationName(),p.columnWidths(),3);
  if(entry==null||entry.getFrames().length!=18)throw new AssertionError("Full flight/attack atlas did not load");
  for(int id=3462;id<=3466;id++)if(loader.getExternalItemSprite(EntityHandler.getItemDef(id))==null)throw new AssertionError("Missing item sprite "+id);
  for(int id=3462;id<=3465;id++)if(!EntityHandler.getItemDef(id).untradeable||EntityHandler.getItemDef(id).membersItem)throw new AssertionError("Gimmick item trade/membership parity");
  for(int id=3462;id<=3464;id++)if(!java.util.Arrays.asList(EntityHandler.getItemDef(id).getCommand()).contains("Spray"))throw new AssertionError("Missing spray action");
  if(Config.S_WANT_CUSTOM_SPRITES)for(int id=1170;id<=1172;id++)if(!EntityHandler.getAnimationDef(id).getName().equals("crossbow"))throw new AssertionError("Published god crossbow appearance shifted");
  System.out.println("PASS Stirge client atlas, five item icons, spray actions and published appearance IDs; custom="+Config.S_WANT_CUSTOM_SPRITES);
 }
}
