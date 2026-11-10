#!/usr/bin/env python3
"""Independently compiled frozen descriptor values and test-only ctor entry counts."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[2]
suite=Path(subprocess.check_output(['python3',str(root/'scripts/workspace-layout.py'),'path','tests','browser-resident-descriptors','--mkdir'],text=True).strip())
jar=root/'Client_Base/Open_RSC_Client.jar'; frozen='325bf42b60fe411705f9f14c0a3ef6debabe24a7'
old=subprocess.check_output(['git','show',f'{frozen}:Client_Base/src/orsc/mudclient.java'],cwd=root,text=True)
new=(root/'Client_Base/src/orsc/mudclient.java').read_text()
def method(source,marker):
 start=source.index(marker);body=source.index('{',start);depth=1;end=body+1
 while depth:
  depth+=(source[end]=='{')-(source[end]=='}');end+=1
 return source[start:end]
creator='\tprivate ResidentObjectCanonicalIdentity\n\t\tcreateResidentObjectCanonicalIdentity('
assert method(old,creator)==method(new,creator), 'Original native canonical creator changed'
arithmetic=method(old,creator).split('\t\tint localX =',1)[1].split('\t\treturn new ResidentObjectCanonicalIdentity(',1)[0]
assert arithmetic==method(new,'\tprivate int writeResidentStaticDescriptor(').split('\t\tint localX =',1)[1].split('\t\tint[] values =',1)[0], 'Direct sampling diverged from original arithmetic'
assert method(old,'\tprivate List<ResidentObjectChunkInput> buildResidentObjectChunkInputsUncached(')==method(new,'\tprivate List<ResidentObjectChunkInput> buildResidentObjectChunkInputsUncached('), 'Uncached/native builder changed'
outputs=[]
for variant,source in [('frozen',old),('off',new),('on',new)]:
 run=suite/variant;run.mkdir(exist_ok=True);classes=run/'classes';classes.mkdir(exist_ok=True)
 # Independent source-only instrumentation counts constructor entry; neither
 # production sources nor field/descriptor values are modified by that counter.
 instrumented=source.replace('\tprivate static final boolean BROWSER_RESIDENT_INPUT_CACHE =','\tprivate static long fixtureIdentityConstructions;\n\tprivate static final boolean BROWSER_RESIDENT_INPUT_CACHE =',1)
 at=instrumented.index('\t\tprivate ResidentObjectCanonicalIdentity(');at=instrumented.index('{',at)+1
 instrumented=instrumented[:at]+'\n            fixtureIdentityConstructions++;'+instrumented[at:]
 selected=run/'mudclient.java';selected.write_text(instrumented)
 subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),str(selected),str(root/'tests/myworld/BrowserResidentInputsFixture.java'),str(root/'tests/myworld/BrowserResidentDescriptorFixture.java')],check=True)
 output=run/'descriptors.bin'
 args=['java','-Xmx512m','-Djava.awt.headless=true','-Dspoiledmilk.browserResidentInputCache=true','-Dspoiledmilk.browserResidentDescriptorWriting='+str(variant=='on').lower(),'-cp',str(classes)+':'+str(jar),'orsc.BrowserResidentDescriptorFixture',str(output)]
 result=subprocess.run(args,check=True,text=True,capture_output=True);(run/'result.txt').write_text(result.stdout+result.stderr);print(variant,result.stdout.strip());outputs.append(output.read_bytes())
assert outputs[0]==outputs[1]==outputs[2], 'Frozen/defaultOFF/directON descriptor data or animated indices differ'
print(f'PASS frozen {frozen[:9]} vs OFF/ON exact {len(outputs[0])}-byte descriptor corpus; original creator/uncached builder unchanged')
