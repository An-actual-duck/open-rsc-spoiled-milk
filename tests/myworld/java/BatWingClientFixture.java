package orsc;
import com.openrsc.client.entityhandling.EntityHandler;
import java.nio.file.Paths;
public final class BatWingClientFixture {
 public static void main(String[]args) {
  Config.S_WANT_CUSTOM_SPRITES=Boolean.parseBoolean(args[1]);EntityHandler.load(true);
  ClientExternalAssetLoader loader=new ClientExternalAssetLoader(Paths.get(args[0]),BatWingClientFixture.class);
  String[] names={"Giant bat wing","Death wing wing","Stirge wing"};
  String[] icons={"bat-wing","death-wing-wing","stirge-wing"};
  for(int i=0;i<3;i++) {
   com.openrsc.client.entityhandling.defs.ItemDef def=EntityHandler.getItemDef(3467+i);
   if(!def.getName().equals(names[i])||def.stackable||def.untradeable||!def.noteable||def.membersItem)throw new AssertionError("item definition parity");
   if(!def.getSpriteLocation().equals("external-png:"+icons[i]))throw new AssertionError("wrong wing palette binding");
   if(loader.getExternalItemSprite(def)==null)throw new AssertionError("wing icon did not load");
  }
  System.out.println("PASS bat wing client names, flags and distinct native wing palettes; custom="+Config.S_WANT_CUSTOM_SPRITES);
 }
}
