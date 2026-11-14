package com.openrsc.server.combat;

import com.openrsc.server.constants.Skill;
import com.openrsc.server.content.Bleed;
import com.openrsc.server.content.minigame.monsterslayer.*;
import com.openrsc.server.content.monsterslayer.StirgeCombat;
import com.openrsc.server.model.Cache;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.plugins.custom.myworld.itemactions.BugOff;
import java.nio.file.Paths;

/** Offline real-world events/inventory/roster test. No live account or database access. */
public final class StirgeCombatFixture {
    private static int checks,serial;
    private static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    private static Player player(CurrentCombatHarness h)throws Exception {
        Player p=h.player("stirge"+(++serial),450,450);h.recordOutgoingPackets(p);p.getClientLimitations().maxItemId=65535;
        p.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),1000,1000,false);return p;
    }
    private static void ticks(CurrentCombatHarness h,int n)throws Exception {for(int i=0;i<n;i++)h.advanceOneCombatTick();}
    private static MonsterSlayerData roster(CurrentCombatHarness h,boolean placed) {
        return MonsterSlayerData.load(Paths.get("conf/server/defs/extras/MonsterSlayer.json"),new MonsterSlayerData.ReferenceCatalog(){
            public boolean npcExists(int id){return true;}public boolean npcAttackable(int id){return true;}
            public boolean npcSpawned(int id){return id!=882||placed;}
            public boolean itemExists(int id){return h.server().getEntityHandler().getItemDef(id)!=null;}
        },true);
    }
    public static void main(String[]args)throws Exception {
        try(CurrentCombatHarness h=new CurrentCombatHarness()) {
            h.server().getConfig().WANT_MYWORLD=true;
            Player p=player(h);Npc a=h.npc(882,451,450),b=h.npc(882,450,451);
            check(a.getDef().combatLevel==55,"combat 55 definition");
            check(com.openrsc.server.model.entity.npc.NpcAttackStyleProfile.forNpc(a)==com.openrsc.server.model.entity.npc.NpcAttackStyleProfile.MELEE,"melee offense only");
            check(a.getDef().getMeleeDefense()==a.getDef().getMagicDefense()&&a.getDef().getRangedDefense()>a.getDef().getMeleeDefense(),"specialized defenses");
            StirgeCombat.primaryHit(a,p,0,false);check(Bleed.activeWounds(p)==0,"miss cannot bleed");
            StirgeCombat.primaryHit(a,p,1,true);check(Bleed.activeWounds(p)==0,"suppressed primary cannot bleed");
            StirgeCombat.primaryHit(a,p,1,false);check(Bleed.severity(a,p)==1,"even smallest positive primary opens wound, no max-roll threshold");
            StirgeCombat.primaryHit(a,p,1,false);check(Bleed.severity(a,p)==2,"second hit increases severity");
            StirgeCombat.primaryHit(a,p,1,false);StirgeCombat.primaryHit(a,p,1,false);
            check(Bleed.severity(a,p)==3&&Bleed.activeWounds(p)==1,"severity caps at three without duplicate wound");
            a.getSkills().setLevel(Skill.HITS.id(),20);int victim=p.getLevel(Skill.HITS.id()),health=a.getLevel(Skill.HITS.id());
            ticks(h,2);check(p.getLevel(Skill.HITS.id())==victim,"no immediate bleed pulse");
            ticks(h,1);int damage=victim-p.getLevel(Skill.HITS.id());
            check(damage>=5&&damage<=7,"severity three shared pulse range");
            check(a.getLevel(Skill.HITS.id())-health==damage,"lifesteal equals actual bleed damage");
            StirgeCombat.primaryHit(b,p,1,false);check(Bleed.activeWounds(p)==2&&Bleed.severity(b,p)==1,"separate Stirges own separate wounds");
            StirgeCombat.applyBugOff(p);check(Bleed.activeWounds(p)==0,"spray clears existing Stirge wounds");
            victim=p.getLevel(Skill.HITS.id());health=a.getLevel(Skill.HITS.id());ticks(h,9);
            check(p.getLevel(Skill.HITS.id())==victim&&a.getLevel(Skill.HITS.id())==health,"no stale pulse or heal after protection");
            StirgeCombat.primaryHit(a,p,9,false);check(Bleed.activeWounds(p)==0,"protection prevents wound application");
            h.clock().advanceMillis(600001);check(!StirgeCombat.protectedByBugOff(p),"protection expires");
            StirgeCombat.primaryHit(a,p,1,false);check(Bleed.severity(a,p)==1,"new wound starts at severity one after expiry");
            a.advanceCombatLifecycle();victim=p.getLevel(Skill.HITS.id());ticks(h,3);
            check(Bleed.activeWounds(p)==0&&p.getLevel(Skill.HITS.id())==victim,"source respawn/lifecycle cannot keep draining");
            Player realVictim=player(h);Npc attacker=h.npc(882,451,450);attacker.getSkills().setLevel(Skill.HITS.id(),10);
            boolean opened=false;
            for(int attempt=0;attempt<40&&!opened;attempt++) {
                attacker.setOpponent(realVictim);
                com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent event=new com.openrsc.server.event.rsc.impl.combat.PvmMeleeEvent(h.world(),attacker,realVictim);
                attacker.setPvmMeleeEvent(event);event.run();event.stop();opened=Bleed.activeWounds(realVictim)>0;
                if(!opened)ticks(h,4);
            }
            check(opened,"real committed NPC primary melee applies Stirge wound");
            check(attacker.getLevel(Skill.HITS.id())==10,"direct melee hit does not lifesteal; only bleed does");
            Bleed.clear(realVictim);
            Item can=new Item(3462);check(p.getCarriedItems().getInventory().add(can),"give full can");
            BugOff spray=new BugOff();
            for(int id=3462;id<=3464;id++) {
                Item current=p.getCarriedItems().getInventory().get(p.getCarriedItems().getInventory().getLastIndexById(id));
                spray.onOpInv(p,0,current,"Spray");check(p.getCarriedItems().getInventory().countId(id)==0,"one charge consumed");
                check(p.getCarriedItems().getInventory().countId(id+1)==1,"next can state delivered");
                long until=StirgeCombat.protectionRemaining(p);spray.onOpInv(p,0,current,"Spray");
                check(StirgeCombat.protectionRemaining(p)==until&&p.getCarriedItems().getInventory().countId(id+1)==1,"stale action cannot consume/refresh again");
            }
            check(StirgeCombat.doses(3465)==0,"empty can cannot spray");
            MonsterSlayerData before=roster(h,false),after=roster(h,true);
            check(before.getRosterVersion()==5&&before.getTask("brimhaven.stirges")==null,"no unreachable task before owner placement");
            check(after.getRosterVersion()==6&&after.getTask("brimhaven.stirges")!=null&&after.getTask("brimhaven.stirges.repeatable")!=null,"placed Stirges enable mandatory and rotation tasks");
            check(MonsterSlayerTaskSupplies.itemIds(after.getTask("brimhaven.stirges")).equals(java.util.Collections.singletonList(3462)),"first-task supply uses full Bug Off can");
            Cache cache=new Cache();MonsterSlayerState.Snapshot saved=MonsterSlayerState.completeIntroduction(MonsterSlayerState.beginIntroduction(MonsterSlayerState.defaults(before),before),before);
            MonsterSlayerState.write(cache,before,saved);MonsterSlayerState.Snapshot migrated=MonsterSlayerState.read(cache,after);
            check(migrated.getRank()==saved.getRank()&&migrated.getTasksCompleted()==saved.getTasksCompleted(),"existing player roster migration preserves standing and rewards");
            check(h.server().getEntityHandler().getItemDef(3466)!=null,"gland discoverable");
            com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerReleaseCharacterization.run();
        }
        System.out.println("PASS Stirge: "+checks+" checks; deterministic escalation, shared bleed pulses, actual-damage lifesteal, independent wounds, Bug Off, three charges, stale actions, lifecycle, staged roster and migration.");
    }
}
