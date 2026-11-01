#!/usr/bin/env python3
"""Deterministic semantic candidates and exact RNG boundaries, without game sessions."""
from pathlib import Path
import os,subprocess,tempfile
root=Path(__file__).resolve().parents[2]
java=Path(os.environ.get('JAVA_HOME','/home/justin/jdk8u482-b08'))/'bin'
fixture=r'''
import com.openrsc.server.model.combat.*;
import java.util.concurrent.atomic.AtomicInteger;
public class CombatLuckFixture {
 static int checks;
 static void ok(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 public static void main(String[] args){
  AtomicInteger calls=new AtomicInteger();
  ok(CombatLuck.higher(0,()->{throw new AssertionError("zero luck RNG");},()->{calls.incrementAndGet();return 7;})==7&&calls.get()==1,"zero luck preserves one original candidate");
  calls.set(0);
  ok(CombatLuck.higher(.5,()->0,()->calls.incrementAndGet()==1?2:9)==9&&calls.get()==2,"one higher extra damage/defense candidate");
  calls.set(0);ok(CombatLuck.higher(.5,()->.5,()->calls.incrementAndGet())==1&&calls.get()==1,"strict luck threshold");
  ok(CombatLuck.higher(.5,()->0,()->7)==7,"ties do not alter values");
  for(double p:new double[]{0,.1,.15,1})for(boolean first:new boolean[]{false,true})for(boolean second:new boolean[]{false,true}) {
   if(p==0&&(first||second)||p==1&&(!first||!second))continue;
   calls.set(0);boolean value=CombatLuck.favorable(.5,p,()->0,()->calls.incrementAndGet()==1?first:second);
   ok(value==(first||second),"favorable boolean selection");ok(calls.get()==(p==0||p==1?1:2),"one extra candidate maximum; guaranteed checks keep original draw");
  }
  ok(Math.abs(CombatLuck.effectiveProbability(.1,.5)-.145)<1e-12,"10% proc becomes14.5%");
  ok(Math.abs(CombatLuck.effectiveProbability(.15,.5)-.21375)<1e-12,"15% crit becomes21.375%");
  for(double p:new double[]{.1,.15}) {
   double probability=0;
   for(int a=0;a<2;a++)for(int b=0;b<2;b++)for(int l=0;l<2;l++) {
    final boolean first=a==1,second=b==1;final double gate=l==1?0:.9;calls.set(0);
    boolean value=CombatLuck.favorable(.5,p,()->gate,()->calls.incrementAndGet()==1?first:second);
    if(value)probability+=(a==1?p:1-p)*(b==1?p:1-p)*.5;
   }
   ok(Math.abs(probability-(p==.1?.145:.21375))<1e-12,"independent candidate enumeration");
  }
  calls.set(0);ok(!CombatLuck.favorable(0,.1,()->{throw new AssertionError("zero chance extra RNG");},()->{calls.incrementAndGet();return false;})&&calls.get()==1,"zero luck exact sampler count");
  ok(!AttackRoll.normal(99).isCritical(),"normal maximum is not critical");
  AttackRoll ordinary=CriticalHitResolver.resolve(9,.15,AttackRoll.Critical.AUTHORED,()->.9,()->9);
  ok(!ordinary.isCritical()&&ordinary.getDamage()==9,"authored failed critical remains normal even at maximum");
  ok(CriticalHitResolver.resolve(9,.15,AttackRoll.Critical.AUTHORED,()->.1,()->1).isCritical(),"authored critical authority remains available");
  System.out.println("PASS "+checks+" semantic Combat Luck candidate/probability/call-count assertions");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='combat-luck-pure-') as directory:
 p=Path(directory);source=p/'CombatLuckFixture.java';source.write_text(fixture)
 cp=str(root/'server/core.jar')
 subprocess.run([str(java/'javac'),'-J-Xmx128m','-cp',cp,'-d',str(p),str(root/'server/src/com/openrsc/server/model/combat/CombatLuck.java'),str(root/'server/src/com/openrsc/server/model/combat/CriticalHitResolver.java'),str(root/'server/src/com/openrsc/server/model/combat/AttackRoll.java'),str(source)],check=True)
 subprocess.run([str(java/'java'),'-Xmx128m','-XX:+UseSerialGC','-cp',str(p)+':'+cp,'CombatLuckFixture'],check=True)
