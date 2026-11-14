import com.openrsc.client.entityhandling.EntityHandler;
import orsc.Config;

/** Loads the real client catalogs without creating a window or connecting anywhere. */
public final class GodCrossbowClientFixture {
    public static void main(String[] args) {
        int checks=0;
        Config.S_WANT_CUSTOM_SPRITES=true;
        EntityHandler.load(true);
        String[] gods={"SARADOMIN","ZAMORAK","GUTHIX"};
        String[] names={"Saradomin's Divine Bolts","Zamorak's Bolts of Wrath","Guthix's Thorned Bolts"};
        String[] mace={"Saving Grace","Divine Retribution","Corrosive Aura"};
        for(int i=0;i<3;i++) {
            EntityHandler.setPrayerBook(gods[i]);
            if(EntityHandler.prayerCount()!=17)throw new AssertionError("17-slot book");checks++;
            if(!EntityHandler.getPrayerDef(15).getName().equals(mace[i]))throw new AssertionError("mace slot shifted");checks++;
            if(!EntityHandler.getPrayerDef(16).getName().equals(names[i])||EntityHandler.getPrayerDef(16).getPointCost()!=80)throw new AssertionError("bolt prayer");checks++;
            String god=gods[i].toLowerCase();
            if(!EntityHandler.getItemDef(3444+i).getSpriteLocation().equals("external-png:"+god+"-crossbow-icon"))throw new AssertionError("approved icon binding");checks++;
            if(!EntityHandler.getItemDef(3444+i).untradeable||EntityHandler.getItemDef(3444+i).noteable)throw new AssertionError("artifact trade/note flags");checks++;
            if(!EntityHandler.getAnimationDef(1170+i).getName().equals("crossbow"))throw new AssertionError("equipped appearance binding");checks++;
        }
        if(EntityHandler.getAnimationDef(106).getCharColour()!=0)throw new AssertionError("ordinary crossbow mutated");checks++;
        System.out.println("PASS "+checks+" real client catalog, prayer-slot, icon, trade and equipped-animation checks");
    }
}
