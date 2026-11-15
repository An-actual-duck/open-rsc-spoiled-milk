import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;
public final class NagaScalesClientFixture {
 public static void main(String[]args) {
  orsc.Config.S_WANT_CUSTOM_SPRITES=Boolean.parseBoolean(args[0]);EntityHandler.load(true);
  ItemDef d=EntityHandler.getItemDef(3470);
  if(d==null||!d.getName().equals("Naga scales")||d.stackable||d.untradeable||!d.noteable||d.membersItem||!d.getSpriteLocation().equals("items:146"))throw new AssertionError("Naga scale client parity");
  ItemDef powder=EntityHandler.getItemDef(3471);
  if(powder==null||!powder.getName().equals("Ground naga scale")||powder.stackable||powder.untradeable||!powder.noteable||!powder.getSpriteLocation().equals("items:23"))throw new AssertionError("ground scale client parity");
  System.out.println("PASS Naga scales client definition and scale icon, custom="+args[0]);
 }
}
