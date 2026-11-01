package com.openrsc.server.combat;

import com.openrsc.server.constants.*;
import com.openrsc.server.content.cleric.*;
import com.openrsc.server.content.cleric.effect.*;
import com.openrsc.server.content.cleric.runtime.*;
import com.openrsc.server.event.custom.NpcLootEvent;
import com.openrsc.server.event.rsc.impl.combat.*;
import com.openrsc.server.event.rsc.impl.projectile.*;
import com.openrsc.server.model.combat.*;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.model.container.Item;
import com.openrsc.server.net.rsc.ClericBlessingContract;
import com.openrsc.server.net.rsc.handlers.SpellHandler;
import com.openrsc.server.net.rsc.enums.OpcodeIn;
import com.openrsc.server.net.rsc.struct.incoming.SpellStruct;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.openrsc.server.plugins.triggers.KillNpcTrigger;

/** Real curse packets, primary producers and death/reward ownership. No listeners or owner data. */
public final class ZamorakHostileFixture {
  static int checks;
  static void check(boolean value,String message) { checks++; if(!value)throw new AssertionError(message); }
  static void hp(Mob m,int hp,int maximum) {
    m.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),hp,maximum,false);
  }
  static Player player(CurrentCombatHarness h,String name,int x,int y)throws Exception {
    Player p=h.player(name,x,y);p.setClientVersion(ClericBlessingContract.CLIENT_VERSION);
    p.getClientLimitations().maxItemId=65535;h.recordOutgoingPackets(p);
    hp(p,1000,1000);ClericBlessingsFixture.skill(p,Skill.PRAYER,160,99);
    ClericBlessingsFixture.skill(p,Skill.MELEE,99,99);
    ClericBlessingsFixture.skill(p,Skill.RANGED,99,99);
    ClericBlessingsFixture.skill(p,Skill.MAGIC,99,99);return p;
  }
  static Npc npc(CurrentCombatHarness h,int id,int x,int y) {
    Npc n=h.npc(id,x,y);hp(n,1000,1000);return n;
  }
  static void curse(Player p,int code)throws Exception {
    ClericBlessingsFixture.fund(p,ClericSpellCatalog.fromCode(code));
    ClericBlessingsFixture.packet(p,code);
  }
  static DamageResult hit(CurrentCombatHarness h,Mob p,Npc n,int damage,AttackRoll roll,
      String key,CombatStyle style,AttackCommitment commitment) {
    return h.server().getResolvedDamageTransaction().apply(
        DamageRequest.resolvedLegacy(p,n,DamageRequest.SourceCategory.ACTOR,key,damage)
            .style(style).attackRoll(roll).attackCommitment(commitment).build());
  }
  static DamageResult hit(CurrentCombatHarness h,Mob p,Npc n,int damage,AttackRoll roll) {
    return hit(h,p,n,damage,roll,"pvm-melee-primary",CombatStyle.MELEE,AttackCommitment.normal());
  }
  static AttackRoll critical(int amount) {return AttackRoll.critical(amount,AttackRoll.Critical.AUTHORED);}
  static int[] xp(Player p) {
    return new int[]{p.getSkills().getExperience(Skill.MELEE.id()),
        p.getSkills().getExperience(Skill.RANGED.id()),p.getSkills().getExperience(Skill.MAGIC.id()),
        p.getSkills().getExperience(Skill.HITS.id())};
  }
  static int[] delta(int[] before,int[] after) {
    int[] result=new int[before.length];for(int i=0;i<result.length;i++)result[i]=after[i]-before[i];return result;
  }
  static int contribution(Npc n,Player p,String method)throws Exception {
    Method m=Npc.class.getDeclaredMethod(method,UUID.class);m.setAccessible(true);
    Object pair=m.invoke(n,p.getUUID());return (Integer)pair.getClass().getMethod("getLeft").invoke(pair);
  }
  static Method meleeSettlement(Class<?> type)throws Exception {
    Method m=type.getDeclaredMethod("inflictDamage",Mob.class,Mob.class,int.class,boolean.class,
        boolean.class,int.class,AttackRoll.class);m.setAccessible(true);return m;
  }
  static ProjectileEvent magic(CurrentCombatHarness h,Player p,Npc n,Spells spell)throws Exception {
    SharedAttackFoundationFixture.advance(h,12);p.setCastTimer(0);
    for(Map.Entry<Integer,Integer> rune:h.server().getEntityHandler().getSpellDef(spell).getRunesRequired())
      p.getCarriedItems().getInventory().add(new Item(rune.getKey(),100));
    SpellStruct s=new SpellStruct();s.setOpcode(OpcodeIn.CAST_ON_NPC);s.spell=spell;s.targetIndex=n.getIndex();
    h.random().reset(13);h.random().scriptDoubles(0.0,0.0,0.0);
    new SpellHandler().process(s,p);
    if(p.getWalkToAction()!=null && p.getWalkToAction().shouldExecute()) {
      p.getWalkToAction().execute();p.setWalkToAction(null);
    }
    return SharedAttackFoundationFixture.lastLaunch(h,p);
  }
  static void catalog() {
    check(ClericSpellCatalog.getAll().size()==20,"twenty active sparse definitions");
    for(int tier=1;tier<=3;tier++) {
      ClericSpellDefinition d=ClericSpellCatalog.fromCode(27+tier);
      check(d.isHostile() && d.getAlignment()==ClericAlignment.ZAMORAK,"aligned hostile identity");
      check(d.getWorshipLevel()==(tier==1?1:tier==2?20:40) && d.getRadius()==tier+1,"unlock and radius");
      check(d.getEffectRankCount()==tier*2 && d.getResourceCosts().size()==(tier==1?2:4),"power cap and mixed sigil cost");
      for(int power=1;power<=tier*2;power++) {
        ClericHostileMagnitude m=(ClericHostileMagnitude)ClericEffectCatalog.get(d.getId(),power).getMagnitude();
        check(m.criticalChance()==5+2*(power-1),"critical curve");
        check(m.staggerChance()==(tier>=2?25+5*(power-1):0),"Stagger curve/inheritance");
        check(m.executionThreshold()==(tier==3?15+2*(power-1):0),"pre-hit threshold curve");
        check(m.executionChance()==(tier==3?50+5*(power-1):0),"execution chance curve");
        check(m.components().size()==tier,"exact inherited component count");
      }
    }
  }
  public static void main(String[] args)throws Exception {
    catalog();List<DamageResult> results=new ArrayList<>();
    try(CurrentCombatHarness h=new CurrentCombatHarness(results::add)) {
      h.server().getConfig().WANT_MYWORLD=true;h.server().getConfig().WANT_FATIGUE=false;
      h.server().getConfig().NPC_KILL_LOGGING=false;
      h.recordDropLogs();h.world().getRegionManager().preflightNativeLayeredPlacements();
      h.world().getRegionManager().populateNativeLayeredPlacements();
      h.openCombatProjectileRectangle(440,470,440,455);
      Player c=player(h,"critcleric",450,449),p=player(h,"critally",450,450);
      Npc n=npc(h,NpcId.GREATER_DEMON.id(),451,450);
      curse(c,30);
      check(ClericHostileRuntime.effect(n,ClericEffectFamily.ZAMORAK_HOSTILE).getPower()==6,"real packet P6 Ruin");
      for(ClericResourceCost cost:ClericSpellCatalog.fromCode(30).getResourceCosts())
        check(ClericBlessingsFixture.count(c,cost.getItemId())==100-cost.getCount(),"cast spends each sigil once");
      ClericBlessingsFixture.fund(c,ClericSpellCatalog.fromCode(28));
      for(ClericResourceCost cost:ClericSpellCatalog.fromCode(28).getResourceCosts()) {
        int count=ClericBlessingsFixture.count(c,cost.getItemId());
        ClericBlessingsFixture.packet(c,28);
        check(ClericBlessingsFixture.count(c,cost.getItemId())==count,"lower-tier cast cannot spend/downgrade");
      }
      check(ClericHostileRuntime.effect(n,ClericEffectFamily.ZAMORAK_HOSTILE).getTier()==3,
          "funded lower tier leaves stronger god slot intact");
      h.random().reset(1);h.random().scriptDoubles(0.0);
      AttackRoll roll=CombatFormula.rollMeleeDamage(p,n);
      check(roll.isCritical() && roll.getCritical()==AttackRoll.Critical.AUTHORED,"another player's real primary melee authored critical");
      check(!CombatFormula.rollOffhandMeleeDamage(p,n).isCritical(),"offhand excluded");
      check(!CombatFormula.rollSecondaryMagicDamage(p,n,20).isCritical(),"secondary Magic excluded");
      check(!CombatFormula.rollSecondaryMeleeDamage(p,n).isCritical(),"secondary melee excluded");
      for(String key:new String[]{"naga-melee-offhand","projectile-cannon-primary","generic-poison","thunder-spire-splash"}) {
        h.random().reset(1);hit(h,p,n,10,critical(10),key,CombatStyle.MELEE,AttackCommitment.normal());
        check(AttackState.pendingStaggers(n)==0 && !h.random().describeState().contains("double="),"no secondary callback/RNG: "+key);
      }
      h.random().reset(1);hit(h,p,n,10,AttackRoll.normal(10));
      check(AttackState.pendingStaggers(n)==0 && !h.random().describeState().contains("double="),"natural maximum is not a critical");
      hit(h,p,n,10,critical(10),"pvm-melee-primary",CombatStyle.MELEE,AttackCommitment.staggered());
      check(AttackState.pendingStaggers(n)==0,"forced zero critical cannot trigger");
      Object phase=new Object();NonCorporealState.tryEnter(n,phase,100,3,()->true);
      h.random().reset(1);
      check(hit(h,p,n,10,critical(10)).isNonCorporealBlocked(),"phase precedes critical callbacks");
      check(!h.random().describeState().contains("double=") && AttackState.pendingStaggers(n)==0,"blocked critical consumes no callback RNG");
      NonCorporealState.clearOwned(n,phase);
      h.random().reset(1);h.random().scriptDoubles(0.0);hit(h,p,n,10,critical(10));
      check(AttackState.pendingStaggers(n)==1,"critical applies one shared pending Stagger");
      h.random().reset(1);hit(h,p,n,10,critical(10));
      check(AttackState.pendingStaggers(n)==1 && !h.random().describeState().contains("double="),"no pending queue or chance replay");
      n.startCombat(p);
      PvmMeleeEvent npcAttack=n.getPvmMeleeEvent();
      npcAttack.run();
      check(AttackState.pendingStaggers(n)==0 && AttackState.recoveryAttacks(n)==5,"actual NPC loses next attack then five recovery");
      for(int i=0;i<5;i++) {
        n.applyStartleDebuff(p);
        check(AttackState.pendingStaggers(n)==0,"Startle shares recovery immunity");
        h.random().reset(1);hit(h,c,n,10,critical(10));
        check(AttackState.pendingStaggers(n)==0 && !h.random().describeState().contains("double="),"other attacker cannot bypass recovery");
        SharedAttackFoundationFixture.advance(h,8);npcAttack.run();
        check(AttackState.recoveryAttacks(n)==4-i,"actual NPC recovery attack "+i);
      }
      h.random().scriptDoubles(0.0);hit(h,p,n,10,critical(10));
      check(AttackState.pendingStaggers(n)==1,"Stagger rearms after exactly five NPC attacks");
      AttackState.clearStagger(n);
      // A hit bringing HP into the threshold must not execute until a later hit.
      hp(n,26,100);h.random().reset(2);h.random().scriptDoubles(0.99);
      check(hit(h,p,n,5,critical(5)).getHitsAfter()==21,"execution uses pre-hit HP, not post-hit threshold");
      hp(n,25,100);h.random().reset(2);h.random().scriptDoubles(0.0);
      DamageResult executed=hit(h,p,n,5,critical(5));
      check(executed.getActualDamage()==25 && executed.getRequest().getResolvedDamage()==25 && executed.isTargetTerminal(),"inclusive threshold execution is same settled hit");
      hp(n,5,100);h.random().reset(2);
      check(hit(h,p,n,5,critical(5)).isTargetTerminal() && !h.random().describeState().contains("double="),"natural killing critical consumes no execution/Stagger roll");
      hp(n,1000,1000);
      for(int id:new int[]{35,96,182,196,216,315,383,388,401,410,477,568,613,614,615,649,713,757,758,759,760,769,809,844,845}) {
        Npc boss=npc(h,id,451,450);check(ClericCriticalRuntime.executionImmune(boss),"explicit boss immunity ID "+id);
      }
      Npc boss=npc(h,477,451,450);curse(c,30);hp(boss,25,100);h.random().scriptDoubles(0.0);
      DamageResult protectedBoss=hit(h,p,boss,5,critical(5));
      check(protectedBoss.getActualDamage()==5 && AttackState.pendingStaggers(boss)==1,"boss immune only execution; critical/Stagger remain");
      for(int id:new int[]{244,245,246,247,567,NpcId.SIR_MORDRED.id(),NpcId.LUCIEN_EDGE.id(),
          NpcId.PETER_SKIPPIN.id(),NpcId.SPOOKIE.id(),NpcId.SCARIE.id()}) {
        Npc quest=npc(h,id,451,450);curse(c,30);hp(quest,25,100);h.random().scriptDoubles(0.0);
        check(ClericCriticalRuntime.executionImmune(quest),"script/quest encounter protection "+id);
        check(hit(h,p,quest,5,critical(5)).getActualDamage()==5,"protected encounter receives ordinary damage without execution "+id);
      }
      // Real melee settlement: same contribution/XP/death/drop ownership as an ordinary lethal hit.
      Method melee=meleeSettlement(PvmMeleeEvent.class);
      int[][] expected=null;
      for(boolean execution:new boolean[]{false,true}) {
        Npc victim=npc(h,NpcId.GREATER_DEMON.id(),451,450);victim.setShouldRespawn(false);
        if(execution)curse(c,30);
        hp(victim,25,100);victim.addCombatDamage(c,75);
        AtomicInteger deaths=new AtomicInteger();victim.addDeathListener(new NpcLootEvent(h.world(),victim.getLocation(),victim.getID(),1,10) {
          @Override public void onLootNpcDeath(Player owner,Npc target) {deaths.incrementAndGet();}
        });
        int[] beforeP=xp(p),beforeC=xp(c);int beforeKills=c.getNpcKills(),beforeFinisherKills=p.getNpcKills();
        h.random().reset(8);h.random().scriptDoubles(0.0);
        DamageResult result=(DamageResult)melee.invoke(new PvmMeleeEvent(h.world(),p,victim),p,victim,
            execution?5:25,false,false,-1,critical(execution?5:25));
        check(result.getActualDamage()==25 && result.isTargetTerminal(),"real primary lethal settlement");
        check(deaths.get()==1 && c.getNpcKills()==beforeKills+1 && p.getNpcKills()==beforeFinisherKills,
            "normal top-contributor kill credit/listener once");
        int[][] earned={delta(beforeP,xp(p)),delta(beforeC,xp(c))};
        if(!execution)expected=earned;else check(Arrays.deepEquals(earned,expected),"execution preserves both players' contribution-based XP");
        int dropRows=h.awaitRecordedDropLogs();victim.killedBy(p);
        check(deaths.get()==1 && h.awaitRecordedDropLogs()==dropRows,"duplicate death cannot duplicate loot");
      }
      // Real primary bow/throwing/Magic producer and delayed impact carry the authored result.
      for(int style=0;style<3;style++) {
        Player a=player(h,"critshot"+style,450,450);Npc victim=npc(h,NpcId.GREATER_DEMON.id(),452,450);
        curse(c,30);
        check(ClericHostileRuntime.effect(victim,ClericEffectFamily.ZAMORAK_HOSTILE)!=null,"new launch victim actually cursed");
        check(h.world().getPlayerByUUID(a.getUUID())==a,"primary shooter currently registered");
        if(style==0){h.equip(a,ItemId.LONGBOW.id(),1);h.equip(a,ItemId.TIN_ARROWS.id(),100);}
        if(style==1)h.equip(a,ItemId.TIN_THROWING_KNIFE.id(),100);
        ProjectileEvent shot=ClericProjectileFixture.launch(h,a,victim,style);
        check(shot.getLaunchSnapshot().getTargetSnapshot().matches(victim),"actual launch target preserved");
        check(shot.getLaunchSnapshot().getSpecification().getAttackRoll().isCritical(),"real primary launch critical style "+style+" "+h.random().describeState());
        hp(victim,250,1000);h.random().reset(9);h.random().scriptDoubles(0.0);
        DamageResult settled=ClericProjectileFixture.settle(h,shot,results);
        check(settled.isTargetTerminal() && settled.getActualDamage()==250,"delayed primary execution style "+style);
        int count=results.size();shot.action();check(results.size()==count,"duplicate projectile impact cannot replay execution");
      }
      Player delayed=player(h,"critdelay",450,450);
      h.equip(delayed,ItemId.LONGBOW.id(),1);h.equip(delayed,ItemId.TIN_ARROWS.id(),100);
      Npc expired=npc(h,NpcId.GREATER_DEMON.id(),452,450);curse(c,30);
      ProjectileEvent committed=ClericProjectileFixture.launch(h,delayed,expired,0);
      int committedAmount=committed.getLaunchSnapshot().getSpecification().getProposedDamage();
      check(committed.getLaunchSnapshot().getSpecification().getAttackRoll().isCritical(),"expiry fixture committed critical");
      h.clock().advanceMillis(60_001);h.random().reset(10);
      DamageResult noCallbacks=ClericProjectileFixture.settle(h,committed,results);
      check(noCallbacks.isCritical() && noCallbacks.getActualDamage()==committedAmount
          && AttackState.pendingStaggers(expired)==0,"expiry keeps committed critical amount but removes curse callbacks");
      Npc late=npc(h,NpcId.GREATER_DEMON.id(),452,450);
      ProjectileEvent natural=new ProjectileEvent(h.world(),delayed,late,
          ProjectileLaunchSpecification.builder(ProjectileLaunchSpecification.Producer.PLAYER_MAGIC,12,1)
              .attackRoll(AttackRoll.normal(12)).build());
      curse(c,30);hp(late,250,1000);h.random().reset(11);
      DamageResult notPromoted=ClericProjectileFixture.settle(h,natural,results);
      check(!notPromoted.isCritical() && notPromoted.getActualDamage()==12 && AttackState.pendingStaggers(late)==0,
          "curse after launch cannot promote an ordinary maximum or trigger callbacks");
      for(int change=0;change<3;change++) {
        Npc changed=npc(h,NpcId.GREATER_DEMON.id(),452,450);curse(c,30);
        ProjectileEvent shot=ClericProjectileFixture.launch(h,delayed,changed,0);
        if(change==0)changed.advanceCombatLifecycle();
        else if(change==1)changed.remove();
        else changed.teleport(com.openrsc.server.model.world.coordinate.WorldLocation.global(
            new com.openrsc.server.model.world.coordinate.WorldCoordinate(452,450,1)));
        int before=results.size();h.random().reset(12);shot.action();shot.stop();
        check(results.size()==before && AttackState.pendingStaggers(changed)==0,
            "stale/lost/other-level target cannot receive delayed critical callbacks "+change);
      }
      for(Spells spell:new Spells[]{Spells.SARADOMIN_STRIKE,Spells.IBAN_BLAST,Spells.WIND_STRIKE}) {
        Player mage=player(h,"critmage"+spell.ordinal(),450,450);
        Npc victim=npc(h,spell==Spells.WIND_STRIKE?567:NpcId.GREATER_DEMON.id(),452,450);
        if(spell==Spells.SARADOMIN_STRIKE)h.equip(mage,ItemId.STAFF_OF_SARADOMIN.id(),1);
        if(spell==Spells.IBAN_BLAST) {h.equip(mage,ItemId.STAFF_OF_IBAN.id(),1);mage.setQuestStage(Quests.UNDERGROUND_PASS,-1);}
        curse(c,30);ProjectileEvent shot=magic(h,mage,victim,spell);
        check(shot.getLaunchSnapshot().getSpecification().getAttackRoll().isCritical(),"actual special Magic primary critical "+spell);
        hp(victim,250,1000);h.random().reset(13);h.random().scriptDoubles(0.0);
        DamageResult result=ClericProjectileFixture.settle(h,shot,results);
        check(spell==Spells.WIND_STRIKE ? result.getActualDamage()==9 : result.getActualDamage()==250,
            "special Magic retains Salarin restriction or ordinary execution "+spell);
      }
      KillNpcTrigger actualQuest=(KillNpcTrigger)Class.forName(
          "com.openrsc.server.plugins.authentic.quests.members.WitchesHouse").getConstructor().newInstance();
      CountDownLatch transformed=new CountDownLatch(1);
      AtomicInteger transformations=new AtomicInteger();
      h.installPlugin(KillNpcTrigger.class,new KillNpcTrigger() {
        public boolean blockKillNpc(Player who,Npc target) {return actualQuest.blockKillNpc(who,target);}
        public void onKillNpc(Player who,Npc target) {
          actualQuest.onKillNpc(who,target);transformations.incrementAndGet();transformed.countDown();
        }
      });
      Npc witch=npc(h,244,451,450);curse(c,30);hp(witch,5,100);h.random().reset(14);
      DamageResult questKill=(DamageResult)melee.invoke(new PvmMeleeEvent(h.world(),p,witch),p,witch,
          5,false,false,-1,critical(5));
      com.openrsc.server.event.rsc.GameTickEvent questCallback=h.findEvent(".onKillNpc");
      check(questCallback!=null,"normal death enqueues quest callback");questCallback.run();
      check(questKill.isTargetTerminal() && transformed.await(5,TimeUnit.SECONDS) && transformations.get()==1,
          "normal killing critical dispatches actual quest transformation exactly once");
      check(h.world().getNpcs().stream().anyMatch(next->next.getID()==245 && next!=witch && !next.isRemoved()),
          "Witch experiment advances one form instead of skipping encounter");
      // Exact P1-P6 chance boundaries through real packet snapshots and settlement.
      for(int power=1;power<=6;power++) {
        ClericBlessingsFixture.skill(c,Skill.PRAYER,ClericBlessingContract.powerThreshold(power-1),99);
        Npc boundary=npc(h,NpcId.GREATER_DEMON.id(),452,450);curse(c,30);
        ClericHostileMagnitude m=ClericHostileRuntime.effect(boundary,ClericEffectFamily.ZAMORAK_HOSTILE);
        check(m!=null && m.getPower()==power,"actual Holy Power threshold chooses curse rank "+power);
        AtomicInteger ordinary=new AtomicInteger();
        h.random().reset(20+power);h.random().scriptDoubles(m.criticalChance()/100.0D-0.00001D);
        check(ClericCriticalRuntime.primaryRoll(p,boundary,10,true,CombatStyle.MELEE,
            ()->{ordinary.incrementAndGet();return 3;}).isCritical() && ordinary.get()==0,"critical strict lower boundary "+power);
        h.random().reset(20+power);h.random().scriptDoubles(m.criticalChance()/100.0D);
        check(!ClericCriticalRuntime.primaryRoll(p,boundary,10,true,CombatStyle.MELEE,
            ()->{ordinary.incrementAndGet();return 3;}).isCritical() && ordinary.get()==1,"critical exact boundary keeps one ordinary roll "+power);
        h.random().reset(20+power);h.random().scriptDoubles(m.staggerChance()/100.0D-0.00001D);
        hit(h,p,boundary,1,critical(1));check(AttackState.pendingStaggers(boundary)==1,"Stagger lower chance boundary "+power);
        AttackState.clearStagger(boundary);h.random().reset(20+power);h.random().scriptDoubles(m.staggerChance()/100.0D);
        hit(h,p,boundary,1,critical(1));check(AttackState.pendingStaggers(boundary)==0,"Stagger exact chance boundary "+power);
        int threshold=m.executionThreshold()*10;hp(boundary,threshold,1000);
        h.random().reset(20+power);h.random().scriptDoubles(m.executionChance()/100.0D,0.99D);
        check(!hit(h,p,boundary,1,critical(1)).isTargetTerminal(),"execution exact probability is failure "+power);
        hp(boundary,threshold,1000);h.random().reset(20+power);
        h.random().scriptDoubles(m.executionChance()/100.0D-0.00001D);
        check(hit(h,p,boundary,1,critical(1)).getActualDamage()==threshold,"execution lower chance and inclusive HP threshold "+power);
      }
      ClericBlessingsFixture.skill(c,Skill.PRAYER,160,99);
      Npc ordinaryTarget=npc(h,NpcId.GREATER_DEMON.id(),453,450);
      AtomicInteger ordinaryCount=new AtomicInteger();h.random().reset(30);
      check(!ClericCriticalRuntime.primaryRoll(p,ordinaryTarget,10,true,CombatStyle.MELEE,
          ()->{ordinaryCount.incrementAndGet();return 10;}).isCritical(),"unafflicted maximum stays ordinary");
      check(ordinaryCount.get()==1 && !h.random().describeState().contains("double=")
          && ordinaryTarget.getAttribute("cleric_hostile_state",null)==null,
          "unafflicted path adds no chance RNG or hostile state installation");
      Npc lucky=npc(h,NpcId.GREATER_DEMON.id(),452,450);curse(c,30);
      h.equip(p,com.openrsc.server.content.EnchantingItemEffects.getWoolRobeProduct(
          com.openrsc.server.content.EnchantingItemEffects.COSMIC_ALTAR,ItemId.WOOL_WIZARD_HAT.id(),10),1);
      check(p.getCombatLuckChance()==0.1D,"real Cosmic piece supplies ten percent luck, no base critical chance");
      h.random().reset(40);h.random().scriptDoubles(0.9D,0.0D,0.0D);
      check(ClericCriticalRuntime.primaryRoll(p,lucky,10,true,CombatStyle.MELEE,()->1).isCritical(),
          "Cosmic salvages exactly one pure critical chance candidate");
      check(h.random().describeState().split("double=",-1).length==4,"critical selection has three draws, no attack replay");
      h.random().reset(41);h.random().scriptDoubles(0.9D,0.0D,0.0D);
      hit(h,p,lucky,1,critical(1));check(AttackState.pendingStaggers(lucky)==1,"Cosmic salvages Stagger chance");
      check(h.random().describeState().split("double=",-1).length==4,"one Stagger callback after pure selection");
      AttackState.clearStagger(lucky);hp(lucky,25,100);
      h.random().reset(42);h.random().scriptDoubles(0.9D,0.0D,0.0D);
      check(hit(h,p,lucky,1,critical(1)).getActualDamage()==25,"Cosmic salvages one execution chance");
      check(h.random().describeState().split("double=",-1).length==4 && AttackState.pendingStaggers(lucky)==0,
          "successful execution adds no Stagger roll or second callback");
      Player gone=player(h,"critgone",450,450);Npc registered=npc(h,NpcId.GREATER_DEMON.id(),452,450);curse(c,30);
      h.logout(gone);h.random().reset(43);ordinaryCount.set(0);
      check(!ClericCriticalRuntime.primaryRoll(gone,registered,10,true,CombatStyle.MELEE,
          ()->{ordinaryCount.incrementAndGet();return 5;}).isCritical()
          && ordinaryCount.get()==1 && !h.random().describeState().contains("double="),
          "unregistered attacker gains no authored roll or new RNG");
      // Retained requests cannot borrow a new lifetime/session's valid curse.
      for(int stale=0;stale<3;stale++) {
        Npc guarded=npc(h,NpcId.GREATER_DEMON.id(),452,450);curse(c,30);hp(guarded,25,100);
        DamageRequest retained=DamageRequest.resolvedLegacy(p,guarded,
            DamageRequest.SourceCategory.ACTOR,"pvm-melee-primary",1)
            .style(CombatStyle.MELEE).attackRoll(critical(1)).build();
        if(stale==0) {guarded.advanceCombatLifecycle();curse(c,30);}
        else if(stale==1)p.advanceCombatLifecycle();
        else p.sessionId++;
        check(ClericHostileRuntime.effect(guarded,ClericEffectFamily.ZAMORAK_HOSTILE)!=null,
            "stale request fixture has a valid current curse");
        h.random().reset(44+stale);h.random().scriptDoubles(0.0D);
        DamageResult result=h.server().getResolvedDamageTransaction().apply(retained);
        check(result.getActualDamage()==1 && AttackState.pendingStaggers(guarded)==0,
            "stale request cannot acquire execution/Stagger from new identity "+stale);
        check(!h.random().describeState().contains("double="),"stale critical callback consumes no RNG "+stale);
      }
      // Casting resources and active slots stay transient across expiry/lifecycle.
      Npc expiry=npc(h,NpcId.GREATER_DEMON.id(),451,450);curse(c,30);
      check(AttackState.stagger(expiry,1) && AttackState.commitAttack(expiry).isSuppressed(),"expiry recovery fixture");
      h.clock().advanceMillis(60_001);
      check(ClericHostileRuntime.effect(expiry,ClericEffectFamily.ZAMORAK_HOSTILE)==null,"60 second common expiry");
      check(AttackState.recoveryAttacks(expiry)==5,"curse expiry cannot clear shared recovery");
      curse(c,30);check(AttackState.recoveryAttacks(expiry)==5,"recasting cannot reset/bypass immunity");
      curse(c,30);expiry.advanceCombatLifecycle();
      check(ClericHostileRuntime.effect(expiry,ClericEffectFamily.ZAMORAK_HOSTILE)==null,"NPC lifecycle retires critical vulnerability");
      System.out.println("PASS "+checks+" Zamorak hostile packet/combat/attribution assertions");
    }
  }
}
