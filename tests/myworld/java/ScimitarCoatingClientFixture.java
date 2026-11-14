import com.openrsc.client.entityhandling.EntityHandler;
import com.openrsc.client.entityhandling.defs.ItemDef;
import orsc.Config;

public final class ScimitarCoatingClientFixture {
    public static void main(String[] args) {
        Config.S_WANT_CUSTOM_SPRITES=Boolean.parseBoolean(args[0]);EntityHandler.load(true);
        int[] bases={1999,2010,82,83,84,427,2155,3117,85,2021,86,2032,398,3265};
        ItemDef dose=EntityHandler.getItemDef(3447);
        if(dose.command!=null||dose.wieldable||dose.noteable||dose.stackable||!dose.getSpriteLocation().equals("items:48"))throw new AssertionError("inert single-dose vial");
        for(int n=0;n<bases.length;n++) {
            ItemDef base=EntityHandler.getItemDef(bases[n]),coated=EntityHandler.getItemDef(3448+n);
            if(!coated.getName().equals("Anti-coagulant "+base.getName())||!coated.getSpriteLocation().equals(base.getSpriteLocation())
                ||coated.getPictureMask()!=base.getPictureMask()||coated.getBlueMask()!=base.getBlueMask()
                ||coated.spriteID!=base.spriteID||coated.wearableID!=base.wearableID||!coated.wieldable||coated.noteable
                ||coated.command!=null||coated.membersItem!=base.membersItem||coated.untradeable!=base.untradeable)
                throw new AssertionError("coated definition mismatch "+bases[n]);
        }
        System.out.println("PASS 15 actual client potion/coated inventory bindings; custom sprites="+args[0]);
    }
}
