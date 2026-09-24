import com.openrsc.client.entityhandling.EntityHandler;
import orsc.Config;
public final class DaggerOfTerrorVisualAudit {
 public static void main(String[] args) throws Exception {
  Config.S_WANT_CUSTOM_SPRITES=true;
  EntityHandler.load(true);
  String defs = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("server/conf/server/defs/ItemDefsCustom.json")), java.nio.charset.StandardCharsets.UTF_8);
  java.util.regex.Matcher mapping = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*3353\\s*,[^}]*?\"appearanceID\"\\s*:\\s*(\\d+)").matcher(defs);
  if (!mapping.find()) throw new AssertionError("missing server item");
  int animationIndex = Integer.parseInt(mapping.group(1)) - 1; // Player renderer uses one-based appearances.
  if (!"daggerofterror".equals(EntityHandler.getAnimationDef(animationIndex).getName())) throw new AssertionError("equipped server appearance resolves to wrong animation");
  if (EntityHandler.getAnimationDef(1092).getCharColour()!=0) throw new AssertionError("double tint");
  if (!"external-png:dagger-of-terror-icon@28x19".equals(EntityHandler.getItemDef(3353).getSpriteLocation())) throw new AssertionError("icon");
  if (EntityHandler.getItemDef(3353).getPictureMask()!=0) throw new AssertionError("icon tint");
  System.out.println("PASS: server appearance resolves through player renderer indexing to Dagger of Terror; icon, neutral tints");
 }
}
