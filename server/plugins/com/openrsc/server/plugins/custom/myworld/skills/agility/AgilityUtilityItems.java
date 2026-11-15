package com.openrsc.server.plugins.custom.myworld.skills.agility;
import com.openrsc.server.content.AgilityUtilityEquipment;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpInvTrigger;
import static com.openrsc.server.plugins.Functions.multi;
public final class AgilityUtilityItems implements OpInvTrigger {
    public boolean blockOpInv(Player p,Integer slot,Item item,String command){return p.getConfig().WANT_MYWORLD&&item.getCatalogId()>=AgilityUtilityEquipment.BOOTS&&item.getCatalogId()<=AgilityUtilityEquipment.CAPE;}
    public void onOpInv(Player p,Integer slot,Item item,String command){
        if("check".equalsIgnoreCase(command)){AgilityUtilityEquipment.check(p,item.getCatalogId());return;}
        if(item.getCatalogId()!=AgilityUtilityEquipment.CAPE||!"teleport".equalsIgnoreCase(command))return;
        String[] options=java.util.Arrays.copyOf(AgilityUtilityEquipment.COURSE_NAMES,AgilityUtilityEquipment.COURSE_NAMES.length+1);
        options[options.length-1]="Cancel";int choice=multi(p,options);
        // Revalidate possession, location and charges after the menu has yielded.
        AgilityUtilityEquipment.teleport(p,choice);
    }
}
