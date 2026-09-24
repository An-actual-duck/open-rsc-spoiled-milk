import com.openrsc.client.entityhandling.EntityHandler;
import orsc.Config;
public final class SlayerLeatherPoisonVisualAudit {
 public static void main(String[] args) throws Exception {
  Config.S_WANT_CUSTOM_SPRITES=true;EntityHandler.load(true);
  String defs=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("server/conf/server/defs/ItemDefsCustom.json")),java.nio.charset.StandardCharsets.UTF_8);
  int[] starts={3357,3369,3375,3381,3387,3394},colors={0x238fff,0x589d40,0xd34538,0xe3b59a,0x55565a,0xefd04b};
  String[] families={"giant-frog","naga","terror-dog","bloodveld","dark-beast","ugthanki"},slots={"coif","gloves","boots","chaps","cuirass"},shapes={"mediumhelm","hidegloves","hideboots","chainmaillegs","chainmail"};
  for(int s=0;s<6;s++)for(int j=0;j<5;j++){
   int id=starts[s]+j,index=appearance(defs,id)-1;
   if(index!=1098+s*5+j || !shapes[j].equals(EntityHandler.getAnimationDef(index).getName()) || EntityHandler.getAnimationDef(index).getCharColour()!=colors[s])throw new AssertionError("worn palette "+id);
   if(!EntityHandler.getItemDef(id).getSpriteLocation().startsWith("external-png:"+families[s]+"-"+slots[j]+"@") || EntityHandler.getItemDef(id).getPictureMask()!=0)throw new AssertionError("inventory "+id);
  }
  int p=appearance(defs,3354)-1;
  if(!"daggerofterrorpoisoned".equals(EntityHandler.getAnimationDef(p).getName()) || EntityHandler.getAnimationDef(p).getCharColour()!=0)throw new AssertionError("poison mapping");
  if(!"external-png:dagger-of-terror-poisoned-icon@28x19".equals(EntityHandler.getItemDef(3354).getSpriteLocation()))throw new AssertionError("poison icon");
  System.out.println("PASS: 30 distinct worn palette mappings and inventory references; poisoned dagger one-based mapping and icon");
 }
 private static int appearance(String defs,int id){
  java.util.regex.Matcher m=java.util.regex.Pattern.compile("\"id\"\\s*:\\s*"+id+"\\s*,[^}]*?\"appearanceID\"\\s*:\\s*(\\d+)").matcher(defs);
  if(!m.find())throw new AssertionError(id);return Integer.parseInt(m.group(1));
 }
}
