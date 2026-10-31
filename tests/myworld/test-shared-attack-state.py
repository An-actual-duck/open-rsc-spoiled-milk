#!/usr/bin/env python3
"""Pure deterministic accounting contract; native event coverage accompanies runtime wiring."""
import os, pathlib, subprocess, tempfile
root = pathlib.Path(__file__).resolve().parents[2]
java = pathlib.Path(os.environ.get('JAVA_HOME', '/home/justin/jdk8u482-b08')) / 'bin'
fixture = '''import com.openrsc.server.model.combat.*;
public class SharedAttackStateFixture {
 static int n; static void ok(boolean value){ n++; if(!value)throw new AssertionError("check "+n); }
 public static void main(String[] args){
  StaggerState s=new StaggerState(); ok(s.apply(1)); ok(!s.apply(1));
  AttackCommitment a=s.commit(); ok(a.isSuppressed()); ok(a.resolveDamage(100)==0); ok(!a.resolveCritical(true));
  for(int i=0;i<5;i++){ok(!s.apply(1));ok(!s.commit().isSuppressed());}
  ok(s.apply(3)); for(int i=0;i<3;i++){ok(s.commit().isSuppressed());ok(!s.apply(1));}
  for(int i=0;i<5;i++){ok(!s.apply(1));ok(!s.commit().isSuppressed());}
  ok(s.apply(1)); s.clear();ok(s.pendingAttacks()==0&&s.recoveryAttacks()==0);ok(!s.commit().isSuppressed());
  for(int extras=0;extras<=2;extras++){
   AttackCadenceState c=new AttackCadenceState();
   if(extras>0)ok(c.addDelay(10,AttackCadenceState.Source.WHIP));
   if(extras>1)ok(c.addDelay(10,AttackCadenceState.Source.STICKY));
   long baseAndSlowReady=15;boolean ready=c.ready(baseAndSlowReady);
   ok(ready==(extras==0));
   for(long t=15;t<15+extras;t++){ok(!c.ready(t));ok(!c.ready(t));ok(c.due()==15+extras);}
   ok(c.ready(15+extras));
   if(extras>0)ok(!c.addDelay(15+extras,AttackCadenceState.Source.WHIP));
   c.committed();ok(c.addDelay(20,AttackCadenceState.Source.WHIP));
  }
  AttackCadenceState c=new AttackCadenceState();c.addDelay(0,AttackCadenceState.Source.WHIP);
  ok(!c.ready(10));ok(c.due()==11);ok(c.addDelay(10,AttackCadenceState.Source.STICKY));
  ok(c.due()==12);ok(!c.ready(11));ok(c.ready(12));ok(!c.addDelay(12,AttackCadenceState.Source.STICKY));
  c.committed();ok(c.ready(12));ok(c.addDelay(12,AttackCadenceState.Source.STICKY));
  c.clear();ok(c.ready(12));
  c.clear();ok(!c.ready(10,1,0));ok(c.due()==11);ok(!c.ready(10,2,0));ok(c.due()==12);
  ok(!c.ready(11,0,0));ok(c.due()==12);ok(c.ready(12,1,0));
  c.committed();ok(!c.ready(20,2,1));ok(c.due()==21);ok(!c.ready(20,2,0));ok(c.due()==21);ok(c.ready(21,0,0));
  c.committed();ok(!c.ready(30,1,0));ok(c.due()==31);
  final int[] random={0},normal={0};
  java.util.function.DoubleSupplier rng=()->{random[0]++;return 0.0;};
  java.util.function.IntSupplier ordinary=()->{normal[0]++;return 20;};
  AttackRoll critical=CriticalHitResolver.resolve(20,.10,AttackRoll.Critical.COSMIC,rng,ordinary);
  ok(critical.isCritical()&&critical.getDamage()==20&&random[0]==1&&normal[0]==0);
  AttackRoll maximum=CriticalHitResolver.resolve(20,0,AttackRoll.Critical.NONE,rng,ordinary);
  ok(!maximum.isCritical()&&maximum.getDamage()==20&&random[0]==1&&normal[0]==1);
  AttackRoll miss=CriticalHitResolver.resolve(0,.50,AttackRoll.Critical.COSMIC,rng,()->0);
  ok(!miss.isCritical()&&random[0]==1);
  ok(!critical.committed(AttackCommitment.staggered()).isCritical());
  ok(critical.withDamage(0).isCritical());
  ok(!CriticalHitResolver.resolve(20,.10,AttackRoll.Critical.COSMIC,()->.10,ordinary).isCritical());
  System.out.println("PASS "+n+" shared attack-state assertions");
 }
}'''
with tempfile.TemporaryDirectory(prefix='shared-attack-state-') as tmp:
 p=pathlib.Path(tmp);(p/'SharedAttackStateFixture.java').write_text(fixture)
 sources=[root/'server/src/com/openrsc/server/model/combat'/f'{name}.java' for name in ['StaggerState','AttackCommitment','AttackCadenceState','AttackRoll','CriticalHitResolver']]
 subprocess.run([str(java/'javac'),'-cp',str(root/'server/core.jar'),'-d',tmp,*map(str,sources),str(p/'SharedAttackStateFixture.java')],check=True)
 subprocess.run([str(java/'java'),'-Xmx128m','-cp',tmp+':'+str(root/'server/core.jar'),'SharedAttackStateFixture'],check=True)
