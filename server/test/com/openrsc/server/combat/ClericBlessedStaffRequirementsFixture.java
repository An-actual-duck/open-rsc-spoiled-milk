package com.openrsc.server.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.player.Player;

/** Actual selected-world definition and equip admission, without server listeners/accounts. */
public final class ClericBlessedStaffRequirementsFixture {
    public static void main(String[] args)throws Exception {
        int checks=0;int[] levels={1,8,15,22,30,38,46,54,62,70};
        try(CurrentCombatHarness h=new CurrentCombatHarness()) {
            Player p=h.player("staffcheck",441,440);h.recordOutgoingPackets(p);
            p.getSkills().setTemporaryLevelAndMaxStat(Skill.MELEE.id(),1,1,false);
            p.getSkills().setTemporaryLevelAndMaxStat(Skill.PRAYER.id(),1,1,false);
            for(int first:new int[]{2228,3152,3162})for(int tier=0;tier<10;tier++) {
                p.setPrayerBook(first==2228?com.openrsc.server.model.entity.player.PrayerCatalog.GodLine.ZAMORAK:first==3152?com.openrsc.server.model.entity.player.PrayerCatalog.GodLine.SARADOMIN:com.openrsc.server.model.entity.player.PrayerCatalog.GodLine.GUTHIX);
                Item item=new Item(first+tier);int requirement=levels[tier];
                if(item.getDef(h.world()).getRequiredSkillIndex()!=Skill.MAGIC.id()
                    ||item.getDef(h.world()).getRequiredLevel()!=requirement)throw new AssertionError("Magic definition ladder "+(first+tier));checks++;
                p.getSkills().setTemporaryLevelAndMaxStat(Skill.MAGIC.id(),requirement,requirement,false);
                if(!p.getCarriedItems().getEquipment().ableToEquip(item))throw new AssertionError("No extra melee/Worship gate "+(first+tier));checks++;
                if(requirement>1){p.getSkills().setTemporaryLevelAndMaxStat(Skill.MAGIC.id(),requirement-1,requirement-1,false);
                    if(p.getCarriedItems().getEquipment().ableToEquip(item))throw new AssertionError("Magic underlevel "+(first+tier));checks++;}
                h.server().getConfig().NO_LEVEL_REQUIREMENT_WIELD=true;
                p.getSkills().setTemporaryLevelAndMaxStat(Skill.MAGIC.id(),1,1,false);
                if(!p.getCarriedItems().getEquipment().ableToEquip(item))throw new AssertionError("No-level bypass "+(first+tier));checks++;
                h.server().getConfig().NO_LEVEL_REQUIREMENT_WIELD=false;
            }
        }
        System.out.println("PASS "+checks+" blessed-staff definition/equip/Magic/no-extra-melee-or-Worship/no-level-bypass checks");
    }
}
