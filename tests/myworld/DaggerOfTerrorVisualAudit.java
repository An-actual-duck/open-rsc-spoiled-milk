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
  java.util.regex.Matcher bow = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*3352\\s*,[^}]*?\"appearanceID\"\\s*:\\s*(\\d+)").matcher(defs);
  if (!bow.find()) throw new AssertionError("missing bow");
  int bowIndex = Integer.parseInt(bow.group(1)) - 1;
  if (!"leachingbow".equals(EntityHandler.getAnimationDef(bowIndex).getName())) throw new AssertionError("bow appearance");
  if (EntityHandler.getAnimationDef(bowIndex).getCharColour()!=0x333333) throw new AssertionError("ebony wood tint");
  if (!"external-png:leaching-bow-icon@39x25".equals(EntityHandler.getItemDef(3352).getSpriteLocation()) || EntityHandler.getItemDef(3352).getPictureMask()!=0) throw new AssertionError("bow icon");
  java.util.regex.Matcher staff = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*3351\\s*,[^}]*?\"appearanceID\"\\s*:\\s*(\\d+)").matcher(defs);
  if (!staff.find()) throw new AssertionError("missing staff");
  int staffIndex = Integer.parseInt(staff.group(1)) - 1;
  if (!"thunderspirestaff".equals(EntityHandler.getAnimationDef(staffIndex).getName()) || EntityHandler.getAnimationDef(staffIndex).getCharColour()!=0) throw new AssertionError("staff appearance/tint");
  if (!"external-png:thunder-spire-staff-icon@34x30".equals(EntityHandler.getItemDef(3351).getSpriteLocation()) || EntityHandler.getItemDef(3351).getPictureMask()!=0) throw new AssertionError("staff icon");
  System.out.println("PASS: server appearance resolves through player renderer indexing to Dagger of Terror; icon, neutral tints");
 }
}
