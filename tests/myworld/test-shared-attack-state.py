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
  for(int slow=0;slow<=2;slow++)for(int extras=0;extras<=2;extras++){
   AttackCadenceState c=new AttackCadenceState();c.commit(10,3+slow);
   for(int i=0;i<extras;i++)c.addDelay(11,1);
   long due=13+slow+extras;ok(c.due()==due);
   for(long t=11;t<due;t++){ok(!c.ready(t));ok(!c.ready(t));ok(c.due()==due);}
   ok(c.ready(due));
  }
  AttackCadenceState c=new AttackCadenceState();c.addDelay(0,1);c.addDelay(0,1);
  ok(!c.ready(0));ok(c.due()==2);ok(!c.ready(1));ok(c.ready(2));
  c.addDelay(2,1);c.commit(2,4);ok(c.due()==7);ok(c.ready(7));
  c.clear();c.commit(10,4);c.commit(10,4);ok(c.due()==14);
  System.out.println("PASS "+n+" shared attack-state assertions");
 }
}'''
with tempfile.TemporaryDirectory(prefix='shared-attack-state-') as tmp:
 p=pathlib.Path(tmp);(p/'SharedAttackStateFixture.java').write_text(fixture)
 sources=[root/'server/src/com/openrsc/server/model/combat'/f'{name}.java' for name in ['StaggerState','AttackCommitment','AttackCadenceState']]
 subprocess.run([str(java/'javac'),'-d',tmp,*map(str,sources),str(p/'SharedAttackStateFixture.java')],check=True)
 subprocess.run([str(java/'java'),'-Xmx128m','-cp',tmp,'SharedAttackStateFixture'],check=True)
