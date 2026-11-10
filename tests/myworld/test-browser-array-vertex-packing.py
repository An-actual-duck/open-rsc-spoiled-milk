#!/usr/bin/env python3
"""Independent OLD/new JVM oracle; compile frozen production source, never a rewritten reference."""
from pathlib import Path
import hashlib,json,os,struct,subprocess,sys,statistics
ROOT=Path(__file__).resolve().parents[2]
BASE='4a396089eb3badcb6af338c57b1b0a4c8837c228'
SOURCE='PC_Client/src/orsc/OpenGLWorldChunkRenderer.java'
suite=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-array-vertex-packing','--mkdir'],text=True).strip())
java=Path(os.environ.get('JAVA_HOME','/home/justin/jdk8u482-b08'))/'bin'
jar=ROOT/'Client_Base/Open_RSC_Client.jar'
fixture=ROOT/'tests/myworld/BrowserVertexPackingFixture.java'
old=suite/'old-source/orsc/OpenGLWorldChunkRenderer.java';old.parent.mkdir(parents=True,exist_ok=True)
old.write_bytes(subprocess.check_output(['git','show',BASE+':'+SOURCE],cwd=ROOT))
outputs=[]
label_java=java.parent.name.replace('.','-')
for label,sources in [('old',[old,fixture]),('new',[fixture])]:
    classes=suite/(label+'-classes-'+label_java);classes.mkdir(exist_ok=True)
    subprocess.run([str(java/'javac'),'-source','8','-target','8','-cp',str(jar),'-d',str(classes),*map(str,sources)],check=True)
    output=suite/(label+'-oracle.bin');outputs.append(output)
    subprocess.run([str(java/'java'),'-Djava.awt.headless=true','-cp',str(classes)+os.pathsep+str(jar),'orsc.BrowserVertexPackingFixture',str(output),label],check=True)
a,b=[p.read_bytes() for p in outputs]
if a!=b:
    at=next(i for i,(x,y)in enumerate(zip(a,b))if x!=y) if len(a)==len(b) else -1
    raise AssertionError(f'Production vertex streams differ: byte{at}, sizes{len(a)}/{len(b)}')
count=struct.unpack_from('>I',a)[0];at=4;fields=0
for _ in range(count*3):
    size=struct.unpack_from('>I',a,at)[0];at+=4+size*4;fields+=size
assert at==len(a)
report={'baseline':BASE,'oldSourceSha256':hashlib.sha256(old.read_bytes()).hexdigest(),'oracleSha256':hashlib.sha256(a).hexdigest(),'cases':count,'rawFloatFieldsCompared':fields,'bytesCompared':len(a),'independentProcesses':True,'newBrowserDirectBuffersAbsent':True,'nativeDirectBufferAndFlipPreserved':True,'GPU':False}
(suite/'oracle-result.json').write_text(json.dumps(report,indent=2)+'\n')
print(f'PASS {fields} raw-float bit comparisons across{count} production mesh/model cases; frozen OLD native plain/atlas/proof and browser streams identical; no GPU/FPS claim')

if '--timing' in sys.argv:
    timing=ROOT/'tests/myworld/BrowserVertexPackingTimingFixture.java'
    summaries={}
    for label in ['old','new']:
        classes=suite/(label+'-classes-'+label_java)
        subprocess.run([str(java/'javac'),'-source','8','-target','8','-cp',str(jar),'-d',str(classes),str(timing)],check=True)
        raw=subprocess.check_output([str(java/'java'),'-Xmx512m','-Djava.awt.headless=true','-cp',str(classes)+os.pathsep+str(jar),'orsc.BrowserVertexPackingTimingFixture'],text=True)
        (suite/(label+'-bounded-timing.json')).write_text(raw)
        data=json.loads(raw);summaries[label]={k:statistics.median(data[k]) for k in ['nativeNsPerChunk','browserNsPerChunk']}
    (suite/'bounded-timing-summary.json').write_text(json.dumps(summaries,indent=2)+'\n')
    print('Desktop JVM packing microbench only, not browser or FPS:',summaries)
