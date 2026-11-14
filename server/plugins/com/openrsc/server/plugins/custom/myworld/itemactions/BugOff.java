package com.openrsc.server.plugins.custom.myworld.itemactions;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.monsterslayer.StirgeCombat;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.triggers.OpInvTrigger;

public final class BugOff implements OpInvTrigger {
    @Override public boolean blockOpInv(Player p,Integer index,Item item,String command) {
        return p.getConfig().WANT_MYWORLD&&StirgeCombat.doses(item.getCatalogId())>0;
    }
    @Override public void onOpInv(Player p,Integer index,Item item,String command) {
        if(!blockOpInv(p,index,item,command)||!"Spray".equalsIgnoreCase(command)||item.getItemStatus().getNoted()
            ||p.killed||p.getLevel(Skill.HITS.id())<=0)return;
        int left=StirgeCombat.doses(item.getCatalogId())-1;
        int next=left==0?StirgeCombat.EMPTY_CAN:item.getCatalogId()+1;
        if(!p.getCarriedItems().getInventory().replaceExact(item,new Item(next),true))return;
        StirgeCombat.applyBugOff(p);
        p.message(left==0?"Your can of Bug Off is empty.":"Your Bug Off has "+left+(left==1?" spray":" sprays")+" left.");
    }
}
