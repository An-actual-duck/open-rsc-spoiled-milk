import com.openrsc.client.entityhandling.EntityHandler;
import orsc.Config;
public final class DaggerOfTerrorVisualAudit {
 public static void main(String[] args) {
  Config.S_WANT_CUSTOM_SPRITES=true;
  EntityHandler.load(true);
  if (!"daggerofterror".equals(EntityHandler.getAnimationDef(1092).getName())) throw new AssertionError("appearance");
  if (EntityHandler.getAnimationDef(1092).getCharColour()!=0) throw new AssertionError("double tint");
  if (!"external-png:dagger-of-terror-icon@28x19".equals(EntityHandler.getItemDef(3353).getSpriteLocation())) throw new AssertionError("icon");
  if (EntityHandler.getItemDef(3353).getPictureMask()!=0) throw new AssertionError("icon tint");
  System.out.println("PASS: Dagger of Terror runtime appearance 1092, icon, neutral tints");
 }
}
