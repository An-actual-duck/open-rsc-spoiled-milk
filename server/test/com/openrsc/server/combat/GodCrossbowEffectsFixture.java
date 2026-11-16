package com.openrsc.server.combat;

import com.openrsc.server.constants.*;
import com.openrsc.server.content.*;
import com.openrsc.server.event.rsc.impl.projectile.*;
import com.openrsc.server.model.combat.*;
import com.openrsc.server.model.container.*;
import com.openrsc.server.model.struct.*;
import com.openrsc.server.model.entity.player.*;
import com.openrsc.server.model.entity.npc.Npc;
import java.util.UUID;

/** Offline integration of real equipment, projectile settlement, kill rewards and owned effects. */
public final class GodCrossbowEffectsFixture {
    private static int checks, serial;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static Player player(CurrentCombatHarness h,int bow)throws Exception{
        Player p=h.player("effects"+(++serial),450,450);h.recordOutgoingPackets(p);p.getClientLimitations().maxItemId=65535;
        p.getSettings().setAppearance(new com.openrsc.server.model.PlayerAppearance(2,8,14,0,1,2));
        for(int s:new int[]{Skill.RANGED.id(),Skill.PRAYER.id(),Skill.HITS.id()})p.getSkills().setTemporaryLevelAndMaxStat(s,99,99,false);
        p.setPrayerBook(GodCrossbows.god(bow));equip(p,bow);p.getPrayers().setPrayer(16,true);return p;
    }
    private static void equip(Player p,int id){
        p.getCarriedItems().getInventory().add(new Item(id));int slot=p.getCarriedItems().getInventory().getLastIndexById(id);
        EquipRequest r=new EquipRequest(p,p.getCarriedItems().getInventory().get(slot),EquipRequest.RequestType.FROM_INVENTORY,false);r.inventorySlot=slot;
        check(p.getCarriedItems().getEquipment().equipItem(r),"equip "+id);
    }
    private static Npc npc(CurrentCombatHarness h,int id,int x,int y){
        Npc n=h.npc(id,x,y);n.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),1000,1000,false);return n;
    }
    private static Npc npc(CurrentCombatHarness h){return npc(h,NpcId.GREATER_DEMON.id(),452,450);}
    private static long effects(com.openrsc.server.model.entity.Mob mob,int type){return mob.getUpdateFlags().getCombatEffects().stream().filter(e->e.getEffectType()==type).count();}
    private static int hp(Npc n){return n.getLevel(Skill.HITS.id());}
    private static void impact(Player p,Npc n,GodCrossbowEffects.Shot shot,int damage,boolean suppressed){
        GodCrossbowEffects.primaryImpact(p,n,shot,AttackRoll.normal(damage).withWoundBasis(damage,10),damage,suppressed,UUID.randomUUID());
    }
    private static ProjectileEvent projectile(CurrentCombatHarness h,Player p,Npc n,int damage,boolean suppressed,boolean holy){
        AttackRoll roll=AttackRoll.normal(damage).withWoundBasis(damage,10);
        ProjectileLaunchSpecification spec=ProjectileLaunchSpecification.builder(ProjectileLaunchSpecification.Producer.PLAYER_BOW,damage,2)
            .attackRoll(roll).attackCommitment(suppressed?AttackCommitment.staggered():AttackCommitment.normal())
            .godCrossbowShot(holy?GodCrossbowEffects.capture(p,n):null).build();
        return new ProjectileEvent(h.world(),p,n,spec);
    }
    private static void ticks(CurrentCombatHarness h,int count)throws Exception{for(int i=0;i<count;i++)h.advanceOneCombatTick();}
    public static void main(String[] args)throws Exception{
        for(int max=1;max<=200;max++)for(int percent=0;percent<=15;percent++){
            check(GodCrossbowEffects.liftRoll(max,max,percent)==max,"unchanged maximum");
            check(GodCrossbowEffects.liftRoll(0,max,percent)==max*percent/100,"raised minimum");
            int prior=-1;for(int roll=0;roll<=max;roll++){
                int lifted=GodCrossbowEffects.liftRoll(roll,max,percent);
                check(lifted>=prior&&lifted>=roll&&lifted<=max,"bounded monotone roll lift");prior=lifted;
                if(percent==0)check(lifted==roll,"inactive identity");
            }
        }
        try(CurrentCombatHarness h=new CurrentCombatHarness()){
            h.server().getConfig().WANT_MYWORLD=true;h.server().getConfig().WANT_EQUIPMENT_TAB=true;h.server().getConfig().WANT_FATIGUE=false;
            h.openCombatProjectileRectangle(448,456,448,453);
            Player z=player(h,3445);Npc wound=npc(h);GodCrossbowEffects.Shot zs=GodCrossbowEffects.capture(z,wound);
            impact(z,wound,zs,6,false);check(Bleed.activeWounds(wound)==0,"below70 excluded");
            impact(z,wound,zs,7,true);check(Bleed.activeWounds(wound)==0,"suppressed qualifying hit excluded");
            impact(z,wound,null,7,false);check(Bleed.activeWounds(wound)==0,"ordinary ammunition excluded");
            check(effects(wound,118)==0,"rejected hits have no cut animation");
            ProjectileEvent ze=projectile(h,z,wound,7,false,true);ze.run();check(Bleed.activeWounds(wound)==1,"real projectile opens wound");
            int health=hp(wound);ze.run();check(hp(wound)==health&&Bleed.activeWounds(wound)==1,"impact exactly once");
            check(effects(wound,118)==1,"one cut on accepted wound, duplicate impact is silent");
            impact(z,wound,zs,7,false);check(Bleed.activeWounds(wound)==1,"reopen keeps one wound");
            check(effects(wound,118)==2,"reopening plays one cut");
            Player z2=player(h,3445);impact(z2,wound,GodCrossbowEffects.capture(z2,wound),7,false);
            check(Bleed.activeWounds(wound)==2,"two sources independent");
            check(effects(wound,118)==3,"independent source plays cut");
            ticks(h,9);check(Bleed.activeWounds(wound)==0,"three pulses finish");
            check(health-hp(wound)>=18&&health-hp(wound)<=30,"two Bleed2 wounds roll three times 3..5");
            Object phase=new Object();NonCorporealState.tryEnter(wound,phase,100,3,()->true);
            impact(z,wound,zs,7,false);check(Bleed.activeWounds(wound)==0,"noncorporeal blocks wound");NonCorporealState.clearOwned(wound,phase);
            Npc flight=npc(h);ProjectileEvent launched=projectile(h,z,flight,7,false,true);z.getPrayers().setPrayer(16,false);launched.run();
            check(Bleed.activeWounds(flight)==1,"committed wound survives prayer swap");Bleed.clear(flight);

            com.openrsc.server.model.entity.update.CombatEffect cut=new com.openrsc.server.model.entity.update.CombatEffect(wound,118);
            z.setClientVersion(10060);check(!cut.isVisibleTo(z),"old observer does not receive new effect ID");
            z.setClientVersion(10061);z2.setClientVersion(10061);check(cut.isVisibleTo(z),"new observer receives shared wound animation");
            com.openrsc.server.model.entity.update.CombatEffect gain=new com.openrsc.server.model.entity.update.CombatEffect(z,117);
            check(gain.isVisibleTo(z)&&!gain.isVisibleTo(z2),"Revelation cosmetic belongs to the buffed player");
            Player g=player(h,3446),g2=player(h,3446);Npc seeds=npc(h),other=npc(h);
            GodCrossbowEffects.Shot gs=GodCrossbowEffects.capture(g,seeds);
            for(int i=1;i<=4;i++){impact(g,seeds,gs,0,false);check(GodCrossbowEffects.seeds(g,seeds)==i,"zero hit counts "+i);}
            impact(g,seeds,gs,7,true);check(GodCrossbowEffects.seeds(g,seeds)==4,"suppressed hit not seed");
            check(effects(seeds,116)==0,"no burst for first four or suppressed hit");
            impact(g2,seeds,GodCrossbowEffects.capture(g2,seeds),0,false);impact(g,other,GodCrossbowEffects.capture(g,other),0,false);
            check(GodCrossbowEffects.seeds(g2,seeds)==1&&GodCrossbowEffects.seeds(g,other)==1,"per-source per-target counters");
            int before=hp(seeds);ProjectileEvent fifth=projectile(h,g,seeds,0,false,true);fifth.run();
            check(GodCrossbowEffects.seeds(g,seeds)==0,"fifth real impact bursts and clears");check(before-hp(seeds)>=0&&before-hp(seeds)<=3,"centre hit once, 0..3");
            before=hp(seeds);fifth.run();check(GodCrossbowEffects.seeds(g,seeds)==0&&hp(seeds)==before,"duplicate impact no new seeds");
            check(effects(seeds,116)==1,"exactly one burst from fifth impact");
            ProjectileEvent suppressed=projectile(h,g,seeds,7,true,true);suppressed.run();check(GodCrossbowEffects.seeds(g,seeds)==0,"actual stagger no seed");
            ProjectileEvent ordinary=projectile(h,g,seeds,0,false,false);ordinary.run();check(GodCrossbowEffects.seeds(g,seeds)==0,"ordinary projectile no seed");
            seeds.advanceCombatLifecycle();check(GodCrossbowEffects.seeds(g2,seeds)==0,"respawn invalidates counters");
            g2.setLoggedIn(false);check(GodCrossbowEffects.seeds(g2,seeds)==0,"logout invalidates counters");
            Npc phased=npc(h);NonCorporealState.tryEnter(phased,phase,100,3,()->true);
            projectile(h,g,phased,0,false,true).run();check(GodCrossbowEffects.seeds(g,phased)==0,"phased projectile not a valid zero");
            NonCorporealState.clearOwned(phased,phase);

            // Repeat a deterministic sample of bursts to verify independent, bounded area rolls.
            Npc anchor=npc(h,NpcId.GREATER_DEMON.id(),454,450),adjacent=npc(h,NpcId.GREATER_DEMON.id(),455,450),far=npc(h,NpcId.GREATER_DEMON.id(),456,450);
            Player bystander=h.player("seed bystander",455,450);int bystanderHp=bystander.getLevel(Skill.HITS.id());
            boolean varied=false;int anchorStart=hp(anchor),adjacentStart=hp(adjacent),farStart=hp(far);
            GodCrossbowEffects.Shot areaShot=GodCrossbowEffects.capture(g,anchor);
            for(int burst=0;burst<20;burst++){
                int a=hp(anchor),b=hp(adjacent);
                for(int hit=0;hit<5;hit++)impact(g,anchor,areaShot,0,false);
                int ad=a-hp(anchor),bd=b-hp(adjacent);check(ad>=0&&ad<=3&&bd>=0&&bd<=3,"bounded centre and neighbour rolls");
                varied|=ad!=bd;check(GodCrossbowEffects.seeds(g,adjacent)==0,"burst cannot seed neighbour");
            }
            check(varied&&hp(anchor)<anchorStart&&hp(adjacent)<adjacentStart,"independently rolled centre and neighbour");
            check(hp(far)==farStart&&bystander.getLevel(Skill.HITS.id())==bystanderHp,"radius2 and NPC-primary player excluded");

            h.server().getConfig().WANT_PVP=true;h.server().getConfig().USES_PK_MODE=false;
            for(int tick=0;tick<20;tick++)h.advanceOneCombatTick();
            Player[] pv=CurrentPvpFoundationCharacterization.areaPlayers(h,"briar pvp");Player shooter=pv[0];
            shooter.getClientLimitations().maxItemId=65535;h.recordOutgoingPackets(shooter);
            for(Player participant:pv){participant.getSkills().setTemporaryLevelAndMaxStat(Skill.RANGED.id(),99,99,false);participant.getSkills().setTemporaryLevelAndMaxStat(Skill.PRAYER.id(),99,99,false);}
            shooter.setPrayerBook(PrayerCatalog.GodLine.GUTHIX);equip(shooter,3446);shooter.getPrayers().setPrayer(16,true);
            GodCrossbowEffects.Shot pvpShot=GodCrossbowEffects.capture(shooter,pv[1]);
            for(int hit=0;hit<100;hit++)GodCrossbowEffects.primaryImpact(shooter,pv[1],pvpShot,AttackRoll.normal(0),0,false,UUID.randomUUID());
            check(pv[1].getLevel(Skill.HITS.id())<250&&pv[2].getLevel(Skill.HITS.id())<250,"PvP burst centre and enemy damaged: "+pv[1].getLevel(Skill.HITS.id())+"/"+pv[2].getLevel(Skill.HITS.id())+" reason="+CombatEligibility.evaluate(CombatEligibilityRequest.builder(shooter,pv[1],CombatEligibilityPhase.COMMIT,CombatStyle.RANGED).currentParticipants(true).registration(true).sameSpatialDomain(true).summonRules(true).playerAttackRules(true).build()).getReason());
            check(pv[3].getLevel(Skill.HITS.id())==250&&pv[4].getLevel(Skill.HITS.id())==250,"party and clan protected");
            shooter.getSocial().addFriend(pv[2].getUsernameHash(),0,"","");int friendHp=pv[2].getLevel(Skill.HITS.id());
            for(int hit=0;hit<30;hit++)GodCrossbowEffects.primaryImpact(shooter,pv[1],pvpShot,AttackRoll.normal(0),0,false,UUID.randomUUID());
            check(pv[2].getLevel(Skill.HITS.id())==friendHp,"source-owned friend protected");
            shooter.getSocial().removeFriend(pv[2].getUsernameHash());pv[2].getSocial().addFriend(shooter.getUsernameHash(),0,"","");
            for(int hit=0;hit<30;hit++)GodCrossbowEffects.primaryImpact(shooter,pv[1],pvpShot,AttackRoll.normal(0),0,false,UUID.randomUUID());
            check(pv[2].getLevel(Skill.HITS.id())<friendHp,"recipient friendship cannot grant immunity");
            pv[2].setLocation(com.openrsc.server.model.Point.location(180,427),false);friendHp=pv[2].getLevel(Skill.HITS.id());
            for(int hit=0;hit<10;hit++)GodCrossbowEffects.primaryImpact(shooter,pv[1],pvpShot,AttackRoll.normal(0),0,false,UUID.randomUUID());
            check(pv[2].getLevel(Skill.HITS.id())==friendHp,"safe-zone collateral excluded");
            z.getPrayers().setPrayer(16,true);z.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),250,250,false);
            z.setLocation(com.openrsc.server.model.Point.location(180,200),false);
            GodCrossbowEffects.primaryImpact(z,pv[1],GodCrossbowEffects.capture(z,pv[1]),AttackRoll.normal(7).withWoundBasis(7,10),7,false,UUID.randomUUID());
            check(Bleed.activeWounds(pv[1])==1,"ranged PvP holy hit wounds");
            pv[1].setLocation(com.openrsc.server.model.Point.location(181,427),false);int safeHealth=pv[1].getLevel(Skill.HITS.id());ticks(h,3);
            check(Bleed.activeWounds(pv[1])==0&&pv[1].getLevel(Skill.HITS.id())==safeHealth,"ranged wound stops at Wilderness boundary");
            h.server().getConfig().WANT_PVP=false;

            h.recordDropLogs();
            // The real terminal projectile must invoke the authoritative reward hook once.
            Player rewarded=player(h,3444);Npc terminal=npc(h);terminal.setShouldRespawn(false);
            terminal.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),1,1000,false);
            int kills=rewarded.getNpcKills();ProjectileEvent fatal=projectile(h,rewarded,terminal,7,false,true);fatal.run();
            check(rewarded.getNpcKills()==kills+1&&GodCrossbowEffects.familiarity(rewarded)==1,"real NPC death awards Revelation");
            fatal.run();check(rewarded.getNpcKills()==kills+1&&GodCrossbowEffects.familiarity(rewarded)==1,"terminal reward once");
            UnequipRequest remove=new UnequipRequest(rewarded,rewarded.getCarriedItems().getEquipment().get(Equipment.EquipmentSlot.SLOT_MAINHAND.getIndex()),UnequipRequest.RequestType.FROM_EQUIPMENT,false);
            remove.equipmentSlot=Equipment.EquipmentSlot.SLOT_MAINHAND;
            check(rewarded.getCarriedItems().getEquipment().unequipItem(remove),"real bow removal");
            check(GodCrossbowEffects.familiarity(rewarded)==0&&!rewarded.getPrayers().isPrayerActivated(16),"bow removal resets progress and prayer");
            // A killing fifth primary hit must still burst around the corpse location.
            Npc dying=npc(h,NpcId.GREATER_DEMON.id(),454,451),survivor=npc(h,NpcId.GREATER_DEMON.id(),455,451);
            GodCrossbowEffects.Shot last=GodCrossbowEffects.capture(g,dying);
            for(int hit=0;hit<4;hit++)impact(g,dying,last,0,false);
            dying.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),1,1000,false);dying.setShouldRespawn(false);
            h.random().reset(1);h.random().scriptInts(3,3,3,3,3,3,3,3,3,3);
            projectile(h,g,dying,7,false,true).run();check(hp(survivor)==997,"fifth killing hit bursts on adjacent enemy");
            z.setLocation(com.openrsc.server.model.Point.location(450,450),false);
            Npc bleedFatal=npc(h);bleedFatal.setShouldRespawn(false);
            bleedFatal.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),1,1000,false);
            GodCrossbowEffects.primaryImpact(z,bleedFatal,GodCrossbowEffects.capture(z,bleedFatal),AttackRoll.normal(7).withWoundBasis(7,10),7,false,UUID.randomUUID());
            ticks(h,3);check(bleedFatal.killed&&z.getKillType()==com.openrsc.server.model.entity.KillType.RANGED,"wound kill retains ranged credit");
            h.awaitRecordedDropLogs();
            for(boolean osrs:new boolean[]{false,true}){
                h.server().getConfig().OSRS_COMBAT_RANGED=osrs;
                for(int i=0;i<50;i++){
                    AttackRoll typed=RangeUtils.rollRangedDamage(g,3446,ItemId.MITHRIL_BOLTS.id(),survivor,false);
                    check(typed.getWoundMaximum()>0&&typed.getWoundRoll()>=0&&typed.getWoundRoll()<=typed.getWoundMaximum(),"ranged formula captures comparable primary basis");
                }
            }
            h.server().getConfig().OSRS_COMBAT_RANGED=false;

            Player s=player(h,3444);Npc kind=npc(h);GodCrossbowEffects.Shot old=GodCrossbowEffects.capture(s,kind);
            check(GodCrossbowEffects.familiarity(s)==0,"starts0");
            for(int i=1;i<=17;i++){
                Npc killed=npc(h);impact(s,killed,GodCrossbowEffects.capture(s,killed),1,false);
                check(killed.tryBeginDeathLifecycle(s).isStarted(),"death context");GodCrossbowEffects.creditedKill(s,killed);
                check(GodCrossbowEffects.familiarity(s)==Math.min(i,15),"kill growth/cap "+i);
                check(effects(s,117)==Math.min(i,15),"Revelation animation only on real gains, stops at cap");
                GodCrossbowEffects.creditedKill(s,killed);check(GodCrossbowEffects.familiarity(s)==Math.min(i,15),"credit once");
            }
            check(GodCrossbowEffects.revelationPercent(s,kind,3444)==15,"same-type instance keeps bonus");
            equip(s,GodCrossbows.cape(PrayerCatalog.GodLine.SARADOMIN));check(GodCrossbowEffects.familiarity(s)==15,"cape alone preserves");
            Npc different=npc(h,NpcId.MOSS_GIANT.id(),452,451);GodCrossbowEffects.capture(s,different);
            check(GodCrossbowEffects.familiarity(s)==0,"new type resets");impact(s,kind,old,1,false);
            kind.tryBeginDeathLifecycle(s);GodCrossbowEffects.creditedKill(s,kind);check(GodCrossbowEffects.familiarity(s)==0,"old in-flight family cannot rebuild");
            Npc reset=npc(h);GodCrossbowEffects.Shot stale=GodCrossbowEffects.capture(s,reset);
            s.getPrayers().setPrayer(16,false);s.getPrayers().setPrayer(16,true);impact(s,reset,stale,1,false);
            reset.tryBeginDeathLifecycle(s);GodCrossbowEffects.creditedKill(s,reset);check(GodCrossbowEffects.familiarity(s)==0,"off/on rejects stale shot");
            com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerData authored=
                com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerData.load(
                    java.nio.file.Paths.get(h.server().getConfig().CONFIG_DIR,"defs","extras","MonsterSlayer.json"),
                    new com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerData.ReferenceCatalog(){
                        public boolean npcExists(int id){return h.server().getEntityHandler().getNpcDef(id)!=null;}
                        public boolean npcAttackable(int id){return npcExists(id)&&h.server().getEntityHandler().getNpcDef(id).isAttackable();}
                        public boolean npcSpawned(int id){return true;}
                        public boolean itemExists(int id){return h.server().getEntityHandler().getItemDef(id)!=null;}
                    });
            java.lang.reflect.Field data=h.world().getClass().getDeclaredField("monsterSlayerData");data.setAccessible(true);data.set(h.world(),authored);
            boolean grouped=false;
            for(com.openrsc.server.content.minigame.monsterslayer.MonsterSlayerDefinitions.Family family:authored.getFamilies()){
                if(family.getNpcIds().size()<2)continue;
                java.util.Iterator<Integer> ids=family.getNpcIds().iterator();Npc first=npc(h,ids.next(),452,450),second=npc(h,ids.next(),452,450);
                check(GodCrossbowEffects.enemyType(s,first).equals(GodCrossbowEffects.enemyType(s,second)),"authored family groups variants");grouped=true;break;
            }
            check(grouped,"at least one authored variant family exercised");
            Npc wrong=npc(h);impact(s,wrong,GodCrossbowEffects.capture(s,wrong),1,false);wrong.tryBeginDeathLifecycle(s);
            GodCrossbowEffects.creditedKill(z,wrong);check(GodCrossbowEffects.familiarity(s)==0,"other killer no progress");
        }
        System.out.println("PASS "+checks+" god-crossbow effect, projectile, wound, seed, revelation and roll-bound checks");
    }
}
