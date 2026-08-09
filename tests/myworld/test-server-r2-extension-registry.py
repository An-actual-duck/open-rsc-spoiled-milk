#!/usr/bin/env python3
"""Compiled R2-2 lifecycle, capability, ownership, health, and reload coverage."""
import subprocess
import tempfile
import textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / "server" / "core.jar"
SOURCE = r'''
import com.openrsc.server.extensions.*;
import java.util.*;
public final class ExtensionRegistryFixture {
 static final List<String> calls=new ArrayList<String>();
 static Set<String> names(String... values){return new LinkedHashSet<String>(Arrays.asList(values));}
 static Set<ExtensionCapability> caps(String... values){Set<ExtensionCapability> r=new LinkedHashSet<ExtensionCapability>();for(String v:values){String[] p=v.split("@");r.add(new ExtensionCapability(p[0],p[1]));}return r;}
 static class E implements ServerExtension {
  final ExtensionDescriptor d; final boolean fail; final boolean lease; final ExtensionHealth health;
  E(String id,String... deps){this(id,false,false,ExtensionHealth.HEALTHY,ExtensionReloadPolicy.HOT_RELOAD_SUPPORTED,Collections.<ExtensionCapability>emptySet(),Collections.<ExtensionCapability>emptySet(),deps);}
  E(String id,boolean fail,boolean lease,ExtensionHealth health,ExtensionReloadPolicy reload,Set<ExtensionCapability> provides,Set<ExtensionCapability> requires,String... deps){d=new ExtensionDescriptor(id,"1.2.0","fixture",names(deps),provides,requires,reload);this.fail=fail;this.lease=lease;this.health=health;}
  public ExtensionDescriptor descriptor(){return d;}
  public void activate(ExtensionContext c)throws Exception{calls.add("+"+d.getId());if(lease)c.onDeactivate("lease-"+d.getId(),()->calls.add("release-"+d.getId()));if(fail)throw new Exception("fail");}
  public void deactivate(){calls.add("-"+d.getId());}
 public ExtensionHealth health(){return health;}
 }
 static final class BrokenCleanup extends E { BrokenCleanup(){super("cleanup-broken");} public void deactivate(){calls.add("-cleanup-broken");throw new IllegalStateException("cleanup");} }
 static final class FailsOnReload extends E { int activations; FailsOnReload(){super("reload-fail");} public void activate(ExtensionContext c)throws Exception{super.activate(c);if(++activations>1)throw new Exception("reload");} }
 static final class BrokenHealth extends E { BrokenHealth(){super("health-broken",false,false,ExtensionHealth.HEALTHY,ExtensionReloadPolicy.HOT_RELOAD_SUPPORTED,Collections.<ExtensionCapability>emptySet(),Collections.<ExtensionCapability>emptySet());} public ExtensionHealth health(){throw new IllegalStateException("secret");} }
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 static void expect(Class<?> type,Runnable r,String m){try{r.run();throw new AssertionError(m);}catch(Throwable e){if(!type.isInstance(e))throw new AssertionError(m+": "+e);}}
 public static void main(String[] a)throws Exception{
  ExtensionRegistry r=new ExtensionRegistry(); r.discover(Arrays.asList(new E("b","a"),new E("a"))); r.activate(ExtensionContext.forTesting());check(r.activeIds().toString().equals("[a, b]"),"order");r.deactivate();check(calls.toString().equals("[+a, +b, -b, -a]"),"reverse");r.reset();
  expect(IllegalArgumentException.class,()->r.discover(Arrays.asList(new E("a"),new E("a"))),"duplicate");r.reset();
  r.discover(Collections.singletonList(new E("a","missing")));expect(IllegalStateException.class,()->r.resolve(),"missing dependency");r.reset();
  r.discover(Arrays.asList(new E("a","b"),new E("b","a")));expect(IllegalStateException.class,()->r.resolve(),"cycle");r.reset();
  r.discover(Arrays.asList(new E("z-provider",false,false,ExtensionHealth.HEALTHY,ExtensionReloadPolicy.HOT_RELOAD_SUPPORTED,caps("world@1.4.0"),Collections.<ExtensionCapability>emptySet()),new E("a-consumer",false,false,ExtensionHealth.HEALTHY,ExtensionReloadPolicy.HOT_RELOAD_SUPPORTED,Collections.<ExtensionCapability>emptySet(),caps("world@1.2.0"))));r.activate(ExtensionContext.forTesting());check(r.activeIds().toString().equals("[z-provider, a-consumer]"),"capability provider activates first");r.deactivate();r.reset();
  r.discover(Collections.singletonList(new E("consumer",false,false,ExtensionHealth.HEALTHY,ExtensionReloadPolicy.HOT_RELOAD_SUPPORTED,Collections.<ExtensionCapability>emptySet(),caps("world@1.2.0"))));expect(IllegalStateException.class,()->r.resolve(),"missing capability");r.reset();
  r.discover(Arrays.asList(new E("provider",false,false,ExtensionHealth.HEALTHY,ExtensionReloadPolicy.HOT_RELOAD_SUPPORTED,caps("world@2.0.0"),Collections.<ExtensionCapability>emptySet()),new E("consumer",false,false,ExtensionHealth.HEALTHY,ExtensionReloadPolicy.HOT_RELOAD_SUPPORTED,Collections.<ExtensionCapability>emptySet(),caps("world@1.2.0"))));expect(IllegalStateException.class,()->r.resolve(),"incompatible capability");r.reset();
  r.discover(Arrays.asList(new E("one",false,false,ExtensionHealth.HEALTHY,ExtensionReloadPolicy.HOT_RELOAD_SUPPORTED,caps("world@1.0.0"),Collections.<ExtensionCapability>emptySet()),new E("two",false,false,ExtensionHealth.HEALTHY,ExtensionReloadPolicy.HOT_RELOAD_SUPPORTED,caps("world@1.0.0"),Collections.<ExtensionCapability>emptySet())));expect(IllegalStateException.class,()->r.resolve(),"duplicate capability provider");r.reset();
  calls.clear();r.discover(Arrays.asList(new E("a",false,true,ExtensionHealth.HEALTHY,ExtensionReloadPolicy.HOT_RELOAD_SUPPORTED,Collections.<ExtensionCapability>emptySet(),Collections.<ExtensionCapability>emptySet()),new E("b",true,true,ExtensionHealth.HEALTHY,ExtensionReloadPolicy.HOT_RELOAD_SUPPORTED,Collections.<ExtensionCapability>emptySet(),Collections.<ExtensionCapability>emptySet(),"a")));try{r.activate(ExtensionContext.forTesting());throw new AssertionError("rollback");}catch(Exception ok){}check(calls.toString().equals("[+a, +b, -b, release-b, -a, release-a]"),"failed extension and receipt rollback");check(r.activeIds().isEmpty(),"inactive");r.reset();
  E degraded=new E("health",false,true,ExtensionHealth.DEGRADED,ExtensionReloadPolicy.HOT_RELOAD_SUPPORTED,Collections.<ExtensionCapability>emptySet(),Collections.<ExtensionCapability>emptySet());r.discover(Arrays.asList(degraded,new BrokenHealth()));r.activate(ExtensionContext.forTesting());check(r.ownershipReceipts().get(0).getResourceIds().toString().equals("[lease-health]"),"ownership receipt");List<ExtensionHealthReceipt> health=r.healthReceipts();check(health.get(0).getHealth()==ExtensionHealth.DEGRADED,"reported health");check(health.get(1).getHealth()==ExtensionHealth.FAILED&&!health.get(1).getDetail().contains("secret"),"bounded failed health");r.deactivate();r.reset();
  calls.clear();r.discover(Arrays.asList(new E("cleanup-ok"),new BrokenCleanup()));r.activate(ExtensionContext.forTesting());ExtensionCleanupReport cleanup=r.deactivate();check(!cleanup.isSuccessful()&&cleanup.getFailures().get(0).getExtensionId().equals("cleanup-broken"),"cleanup failures reported");r.reset();
  calls.clear();r.discover(Collections.singletonList(new E("hot")));r.activate(ExtensionContext.forTesting());check(r.reload(ExtensionContext.forTesting()).isReloaded(),"hot reload");check(calls.toString().equals("[+hot, -hot, +hot]"),"hot lifecycle");r.deactivate();r.reset();
  r.discover(Collections.singletonList(new FailsOnReload()));r.activate(ExtensionContext.forTesting());ExtensionReloadResult failedReload=r.reload(ExtensionContext.forTesting());check(failedReload.isFailed()&&failedReload.getStatus()==ExtensionReloadResult.Status.FAILED&&r.activeIds().isEmpty(),"failed reload is explicit and clean");r.reset();
  calls.clear();r.discover(Collections.singletonList(new E("legacy",false,false,ExtensionHealth.HEALTHY,ExtensionReloadPolicy.RESTART_REQUIRED,Collections.<ExtensionCapability>emptySet(),Collections.<ExtensionCapability>emptySet())));r.activate(ExtensionContext.forTesting());ExtensionReloadResult restart=r.reload(ExtensionContext.forTesting());check(!restart.isReloaded()&&r.activeIds().toString().equals("[legacy]"),"restart required leaves active state intact");r.deactivate();
  System.out.println("PASS");
 }
}
'''
with tempfile.TemporaryDirectory(prefix="r2-extension-") as directory:
    root = Path(directory)
    source = root / "ExtensionRegistryFixture.java"
    source.write_text(textwrap.dedent(SOURCE))
    result = subprocess.run(['javac', '-source', '8', '-target', '8', '-cp', str(CORE), '-d', str(root), str(source)], text=True, capture_output=True)
    if result.returncode:
        raise SystemExit(result.stderr)
    result = subprocess.run(['java', '-cp', str(root) + ':' + str(CORE), 'ExtensionRegistryFixture'], text=True, capture_output=True)
    if result.returncode:
        raise SystemExit(result.stderr)
    assert result.stdout.strip() == 'PASS', result.stdout
print('PASS: R2 extension registry enforces capability, ownership, health, and reload contracts')
