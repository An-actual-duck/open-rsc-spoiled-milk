import com.openrsc.client.entityhandling.EntityHandler;
import orsc.Config;
public final class SlayerPricingAudit {
 public static void main(String[] args) throws Exception {
  Config.S_WANT_CUSTOM_SPRITES=true; EntityHandler.load(true);
  String source=new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("tools/generators/item-overrides/51-slayer-economy.json")),java.nio.charset.StandardCharsets.UTF_8);
  java.util.regex.Matcher m=java.util.regex.Pattern.compile("\"id\"\\s*:\\s*(\\d+),\\s*\"basePrice\"\\s*:\\s*(\\d+)").matcher(source);
  int count=0;
  while(m.find()){int id=Integer.parseInt(m.group(1)),value=Integer.parseInt(m.group(2));if(EntityHandler.getItemDef(id).basePrice!=value)throw new AssertionError("client price mismatch "+id);count++;}
  if(count!=39)throw new AssertionError("coverage "+count);
  System.out.println("PASS: all 39 Slayer prices load in the client");
 }
}
