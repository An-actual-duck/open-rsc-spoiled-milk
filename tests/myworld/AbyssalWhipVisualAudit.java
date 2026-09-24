import com.openrsc.client.entityhandling.EntityHandler;
import orsc.Config;
public final class AbyssalWhipVisualAudit {
 public static void main(String[] args) throws Exception {
  Config.S_WANT_CUSTOM_SPRITES=true; EntityHandler.load(true);
  String defs=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("server/conf/server/defs/ItemDefsCustom.json")),java.nio.charset.StandardCharsets.UTF_8);
  java.util.regex.Matcher m=java.util.regex.Pattern.compile("\"id\"\\s*:\\s*3350\\s*,[^}]*?\"appearanceID\"\\s*:\\s*(\\d+)").matcher(defs);
  if(!m.find())throw new AssertionError("missing whip");
  int index=Integer.parseInt(m.group(1))-1;
  if(index!=1128||!"abyssalwhip".equals(EntityHandler.getAnimationDef(index).getName())||EntityHandler.getAnimationDef(index).getCharColour()!=0)throw new AssertionError("whip appearance/tint");
  if(!"external-png:abyssal-whip-icon@40x22".equals(EntityHandler.getItemDef(3350).getSpriteLocation())||EntityHandler.getItemDef(3350).getPictureMask()!=0)throw new AssertionError("whip icon/tint");
  System.out.println("PASS: whip item 3350 resolves to dedicated equipment animation; inventory icon and neutral tint");
 }
}
