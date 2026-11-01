#!/usr/bin/env python3
"""Adversarial review over an exact exported source + verified copied build.

Use only an immutable reviewed export and artifact receipt. No mutable worker
JARs, accounts, listeners or owner maps are used. Acquire the shared heavy lock
around this command; heap sizes are bounded independently below.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

HARNESS = r'''
package com.openrsc.server.combat;
import com.openrsc.server.constants.*;
import com.openrsc.server.content.EnchantingItemEffects;
import com.openrsc.server.content.cleric.*;
import com.openrsc.server.content.cleric.effect.*;
import com.openrsc.server.content.cleric.runtime.*;
import com.openrsc.server.model.combat.*;
import com.openrsc.server.model.entity.Mob;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.entity.player.Player;
import com.openrsc.server.net.rsc.ClericBlessingContract;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
public final class ZamorakIndependentFixture {
 static int checks;static final List<String> failures=new ArrayList<String>();
 static void check(boolean b,String why){checks++;if(!b){failures.add(why);System.out.println("FAIL "+why);}}
 static void hp(Mob m,int now,int max){m.getSkills().setTemporaryLevelAndMaxStat(Skill.HITS.id(),now,max,false);}
 static Player player(CurrentCombatHarness h,String name,int x,int y)throws Exception{
  Player p=h.player(name,x,y);p.setClientVersion(ClericBlessingContract.CLIENT_VERSION);p.getClientLimitations().maxItemId=65535;
  h.recordOutgoingPackets(p);hp(p,1000,1000);ClericBlessingsFixture.skill(p,Skill.PRAYER,160,99);return p;
 }
 static Npc npc(CurrentCombatHarness h,int x,int y){Npc n=h.npc(NpcId.GREATER_DEMON.id(),x,y);hp(n,1000,1000);return n;}
 static ClericSupportCasting.CastResult cast(Player p,int code){
  ClericSpellDefinition d=ClericSpellCatalog.fromCode(code);ClericBlessingsFixture.fund(p,d);return ClericSupportCasting.cast(p,d);
 }
 static DamageRequest request(Mob p,Npc n,int damage,int roll,AttackRoll.Critical kind,String key,CombatStyle style,DamageRequest.SourceCategory category){
  return DamageRequest.resolvedLegacy(p,n,category,key,damage).style(style).attackRoll(AttackRoll.critical(roll,kind)).build();
 }
 static DamageRequest primary(Player p,Npc n){return request(p,n,1,1,AttackRoll.Critical.AUTHORED,"pvm-melee-primary",CombatStyle.MELEE,DamageRequest.SourceCategory.ACTOR);}
 static int draws(CurrentCombatHarness h){return h.random().describeState().split("double=",-1).length-1;}
 public static void main(String[] args)throws Exception{
  List<DamageResult> observations=new ArrayList<DamageResult>();
  try(CurrentCombatHarness h=new CurrentCombatHarness(observations::add)){
   h.server().getConfig().WANT_MYWORLD=true;h.server().getConfig().WANT_FATIGUE=false;h.recordDropLogs();
   h.world().getRegionManager().preflightNativeLayeredPlacements();h.world().getRegionManager().populateNativeLayeredPlacements();
   h.openCombatProjectileRectangle(440,470,440,455);
   Player c=player(h,"reviewcurse",450,449),a=player(h,"reviewhit",450,450);
   Npc n=npc(h,451,450);check(cast(c,30).getOutcome()==ClericSupportCasting.Outcome.SUCCESS,"real Ruin cast accepted");
   for(Object[] invalid:new Object[][]{
    {AttackRoll.Critical.COSMIC,1,"pvm-melee-primary",CombatStyle.MELEE,DamageRequest.SourceCategory.ACTOR},
    {AttackRoll.Critical.AUTHORED,0,"pvm-melee-primary",CombatStyle.MELEE,DamageRequest.SourceCategory.ACTOR},
    {AttackRoll.Critical.AUTHORED,1,"pvm-melee-primary",CombatStyle.MAGIC,DamageRequest.SourceCategory.ACTOR},
    {AttackRoll.Critical.AUTHORED,1,"pvm-melee-primary",CombatStyle.MELEE,DamageRequest.SourceCategory.OWNED_EFFECT},
    {AttackRoll.Critical.AUTHORED,1,"projectile-cannon-primary",CombatStyle.RANGED,DamageRequest.SourceCategory.ACTOR},
    {AttackRoll.Critical.AUTHORED,1,"projectile-summon-magic-primary",CombatStyle.MAGIC,DamageRequest.SourceCategory.ACTOR}}){
    h.random().reset(1);h.random().scriptDoubles(0.0);hp(n,25,100);
    DamageResult r=h.server().getResolvedDamageTransaction().apply(request(a,n,1,(Integer)invalid[1],(AttackRoll.Critical)invalid[0],(String)invalid[2],(CombatStyle)invalid[3],(DamageRequest.SourceCategory)invalid[4]));
    check(r.getActualDamage()==1&&AttackState.pendingStaggers(n)==0&&draws(h)==0,"ineligible provenance cannot execute/stagger or consume RNG "+Arrays.toString(invalid));
   }
   hp(n,1000,1000);h.random().reset(2);h.random().scriptDoubles(0.0);
   h.server().getResolvedDamageTransaction().apply(primary(a,n));check(AttackState.pendingStaggers(n)==1,"pending shared Stagger established");
   cast(c,30);cast(c,28);check(AttackState.pendingStaggers(n)==1,"equal refresh and rejected downgrade preserve pending Stagger");
   check(ClericHostileRuntime.effect(n,ClericEffectFamily.ZAMORAK_HOSTILE).getTier()==3,"rejected downgrade leaves original curse active");
   check(AttackState.commitAttack(n).isSuppressed()&&AttackState.recoveryAttacks(n)==5,"NPC owns one suppressed attack and5 recovery");
   h.clock().advanceMillis(60001);check(ClericHostileRuntime.effect(n,ClericEffectFamily.ZAMORAK_HOSTILE)==null,"curse expires independently");
   cast(c,30);check(AttackState.recoveryAttacks(n)==5,"new curse cannot reset shared recovery");
   h.random().reset(3);h.random().scriptDoubles(0.0);
   h.server().getResolvedDamageTransaction().apply(primary(a,n));check(draws(h)==0&&AttackState.pendingStaggers(n)==0,"recovery excludes Weak Legs RNG");
   for(int i=0;i<4;i++){AttackState.commitAttack(n);cast(c,30);check(AttackState.recoveryAttacks(n)==4-i,"refresh cannot accelerate recovery "+i);}
   check(!AttackState.stagger(n,1),"four completed recovery attacks remain immune");AttackState.commitAttack(n);
   check(AttackState.stagger(n,1),"exactly fifth completed recovery attack rearms shared Stagger");AttackState.clearStagger(n);
   for(int base:new int[]{ItemId.WOOL_WIZARD_HAT.id(),ItemId.WOOL_ROBE_TOP.id(),ItemId.WOOL_ROBE_SKIRT.id(),ItemId.WOOL_GLOVES.id(),ItemId.WOOL_BOOTS.id()})
    h.equip(a,EnchantingItemEffects.getWoolRobeProduct(EnchantingItemEffects.COSMIC_ALTAR,base,10),1);
   check(a.getCombatLuckChance()==0.5,"real complete Cosmic equipment cap");AtomicInteger ordinary=new AtomicInteger();
   h.random().reset(4);h.random().scriptDoubles(.99,0.0,0.0);
   AttackRoll lucky=ClericCriticalRuntime.primaryRoll(a,n,20,true,CombatStyle.MELEE,()->{ordinary.incrementAndGet();return 7;});
   check(lucky.getCritical()==AttackRoll.Critical.AUTHORED&&lucky.getDamage()==20&&ordinary.get()==0&&draws(h)==3,"one favorable critical candidate without replaying ordinary attack");
   h.random().reset(5);h.random().scriptDoubles(.99,0.0,.99);ordinary.set(0);
   AttackRoll ordinaryHit=ClericCriticalRuntime.primaryRoll(a,n,20,true,CombatStyle.MELEE,()->{ordinary.incrementAndGet();return 7;});
   check(!ordinaryHit.isCritical()&&ordinary.get()==1&&draws(h)==3,"failed critical candidates invoke ordinary supplier once");
   hp(n,25,100);h.random().reset(6);h.random().scriptDoubles(.99,0.0,0.0);int before=observations.size();
   DamageResult executed=h.server().getResolvedDamageTransaction().apply(primary(a,n));
   check(executed.getActualDamage()==25&&draws(h)==3&&observations.size()==before+1,"Cosmic retries pure execution chance once but settles a single damage request");
   // Deliberately retain requests across a NEW actor lifetime/session, then
   // reapply a valid curse. Old requests may not gain new-lifetime callbacks.
   for(int stale=0;stale<3;stale++){
    Player source=player(h,"reviewstale"+stale,450,450);Npc target=npc(h,451,450);cast(c,30);hp(target,25,100);
    DamageRequest retained=primary(source,target);
    if(stale==0){target.advanceCombatLifecycle();cast(c,30);}else if(stale==1)source.advanceCombatLifecycle();else source.sessionId++;
    h.random().reset(7);h.random().scriptDoubles(0.0);
    DamageResult result=h.server().getResolvedDamageTransaction().apply(retained);
    check(result.getActualDamage()==1&&draws(h)==0&&AttackState.pendingStaggers(target)==0,"stale request cannot gain execution or Weak Legs onnew life/session "+stale);
   }
  }
  System.out.println("RESULT independent checks="+checks+" failures="+failures.size());
  if(!failures.isEmpty())throw new AssertionError(failures.toString());
 }
}
'''


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--snapshot', required=True, type=Path)
    parser.add_argument('--artifacts', required=True, type=Path)
    parser.add_argument('--receipt', required=True, type=Path)
    parser.add_argument('--evidence', required=True, type=Path)
    args = parser.parse_args()
    snapshot, artifacts = args.snapshot.resolve(), args.artifacts.resolve()
    receipt = json.loads(args.receipt.read_text())
    hashes = receipt['copiedArtifactSha256']
    def verify():
        for path, digest in hashes.items():
            assert hashlib.sha256((artifacts / path).read_bytes()).hexdigest() == digest, 'frozen artifact changed: '+path
    verify()
    evidence = args.evidence.resolve();evidence.mkdir(parents=True, exist_ok=True)
    java = Path(os.environ.get('JAVA_HOME', '/home/justin/jdk8u482-b08')) / 'bin'
    with tempfile.TemporaryDirectory(prefix='zamorak-independent-') as folder:
        work = Path(folder);runtime = work/'runtime';classes = work/'classes';classes.mkdir()
        source = work/'ZamorakIndependentFixture.java';source.write_text(HARNESS)
        cp = os.pathsep.join([str(artifacts/'server/core.jar'),str(artifacts/'server/plugins.jar'),str(snapshot/'server/lib/*')])
        commands = [
            ([os.sys.executable,str(snapshot/'tests/myworld/prepare-cleric-well-fixture.py'),str(runtime)], snapshot),
            ([str(java/'javac'),'-J-Xmx512m','-J-XX:+UseSerialGC','-proc:none','-cp',cp,'-sourcepath',str(snapshot/'server/test'),'-d',str(classes),str(source)], snapshot),
            ([str(java/'java'),'-Xmx512m','-XX:+UseSerialGC','-Djava.awt.headless=true','-cp',str(classes)+os.pathsep+cp,'com.openrsc.server.combat.ZamorakIndependentFixture'], runtime),
        ]
        for index,(command,cwd) in enumerate(commands):
            result = subprocess.run(command,cwd=cwd,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,timeout=180)
            (evidence/('step-'+str(index)+'.log')).write_text(result.stdout)
            if result.returncode:
                print('\n'.join(result.stdout.splitlines()[-35:]));verify();raise SystemExit(result.returncode)
        print('\n'.join((evidence/'step-2.log').read_text().splitlines()[-5:]))
    verify()


if __name__ == '__main__':
    main()
