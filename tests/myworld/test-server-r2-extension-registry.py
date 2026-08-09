#!/usr/bin/env python3
"""Compiled R2-2 registry characterization without a server/listener."""
import subprocess, tempfile, textwrap
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CORE = ROOT / "server" / "core.jar"
SOURCE = r'''
import com.openrsc.server.extensions.*;
import java.util.*;
public final class ExtensionRegistryFixture {
 static final List<String> calls=new ArrayList<String>();
 static final class E implements ServerExtension { final ExtensionDescriptor d; final boolean fail;
  E(String id,String... deps){this(id,false,deps);} E(String id,boolean fail,String...deps){this.d=new ExtensionDescriptor(id,"fixture",new LinkedHashSet<String>(Arrays.asList(deps)),Collections.singleton("test"));this.fail=fail;}
  public ExtensionDescriptor descriptor(){return d;} public void activate(ExtensionContext c)throws Exception{calls.add("+"+d.getId());if(fail)throw new Exception("fail");} public void deactivate(){calls.add("-"+d.getId());}
 }
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] a)throws Exception{
  ExtensionRegistry r=new ExtensionRegistry(); r.discover(Arrays.asList(new E("b","a"),new E("a"))); r.activate(ExtensionContext.forTesting());check(r.activeIds().toString().equals("[a, b]"),"order");r.deactivate();check(calls.toString().equals("[+a, +b, -b, -a]"),"reverse");r.reset();
  try { r.discover(Arrays.asList(new E("a"),new E("a"))); throw new AssertionError("duplicate"); } catch(IllegalArgumentException ok){} r.reset();
  try {r.discover(Collections.singletonList(new E("a","missing")));r.resolve();throw new AssertionError("missing");}catch(IllegalStateException ok){}r.reset();
  try {r.discover(Arrays.asList(new E("a","b"),new E("b","a")));r.resolve();throw new AssertionError("cycle");}catch(IllegalStateException ok){}r.reset();
  calls.clear();r.discover(Arrays.asList(new E("a"),new E("b",true,"a")));try{r.activate(ExtensionContext.forTesting());throw new AssertionError("rollback");}catch(Exception ok){}check(calls.toString().equals("[+a, +b, -a]"),"rollback");check(r.activeIds().isEmpty(),"inactive");
  System.out.println("PASS");
 }
}
'''
with tempfile.TemporaryDirectory(prefix="r2-extension-") as directory:
 root=Path(directory); source=root/'ExtensionRegistryFixture.java'; source.write_text(textwrap.dedent(SOURCE))
 result=subprocess.run(['javac','-source','8','-target','8','-cp',str(CORE),'-d',str(root),str(source)],text=True,capture_output=True)
 if result.returncode: raise SystemExit(result.stderr)
 result=subprocess.run(['java','-cp',str(root)+':'+str(CORE),'ExtensionRegistryFixture'],text=True,capture_output=True)
 if result.returncode: raise SystemExit(result.stderr)
 assert result.stdout.strip()=='PASS',result.stdout
print('PASS: R2 extension registry resolves deterministically and rolls back reversibly')
