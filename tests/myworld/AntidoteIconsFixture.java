package orsc;
import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;
import java.nio.file.Paths;
public final class AntidoteIconsFixture {
 public static void main(String[] args){
  Config.S_WANT_BANK_NOTES=true;Config.S_WANT_CERT_AS_NOTES=true;
  for(boolean custom:new boolean[]{true,false}){
   Config.S_WANT_CUSTOM_SPRITES=custom;EntityHandler.load(true);
   ClientExternalAssetLoader loader=new ClientExternalAssetLoader(Paths.get("/nonexistent/antidote-icon-check"),mudclient.class);
   for(int id:new int[]{3400,3401}){
    ItemDef d=EntityHandler.getItemDef(id);check(loader.getExternalItemSprite(d)!=null,"packaged egg "+id);check(d.getPictureMask()==0,"no second tint");
    check(EntityHandler.getItemDef(id,true).stackable,"egg notes");
   }
   for(int first:new int[]{3402,1474,3405})for(int n=0;n<3;n++){
    ItemDef d=EntityHandler.getItemDef(first+n);
    check(d.getCommand()[0].equals("Drink"),"drink command");
    check(d.spriteID==(n==0||!custom?48:435+n),"dose-specific inventory art");
    check(d.getDescription().contains("cleansing")&&!d.getDescription().contains("immunity"),"current effect examine");
    check(EntityHandler.getItemDef(first+n,true).stackable,"potion notes");
   }
  }
  System.out.println("PASS: all antidote doses and egg icons/notes in custom and fallback client catalogs");
 }
 private static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
}
