#!/usr/bin/env python3
"""Run the real chunk-cache/failure corpus under both preparation policies.

The only extra instrumentation counts entry into the candidate method, proving
that passing output comparisons are not merely an unexercised private flag.
"""
from pathlib import Path
import os,subprocess
root=Path(__file__).resolve().parents[2]
suite=Path(subprocess.check_output(['python3',str(root/'scripts/workspace-layout.py'),'path','tests','browser-geometry-transfer','--mkdir'],text=True).strip())
java=Path(os.environ.get('JAVA_HOME','/home/justin/jdk8u482-b08'))/'bin'
suite=suite/java.parent.name;suite.mkdir(exist_ok=True)
jar=Path(os.environ.get('BROWSER_TEST_DEPENDENCY_JAR',str(root/'Client_Base/Open_RSC_Client.jar')))
source=(root/'PC_Client/src/orsc/OpenGLWorldChunkRenderer.java').read_text()
start=source.index('{',source.index('final class OpenGLWorldChunkRenderer'))+1
source=source[:start]+'\nstatic long fixturePreparedEntries;\n'+source[start:]
start=source.index('{',source.index('float[] copyBackendChunkVerticesResolved('))+1
source=source[:start]+'fixturePreparedEntries++;'+source[start:]
renderer=suite/'bridge-source/orsc/OpenGLWorldChunkRenderer.java';renderer.parent.mkdir(parents=True,exist_ok=True);renderer.write_text(source)
fixture=(root/'tests/myworld/BrowserAnimatedChunkFixture.java').read_text()
needle='frame.release();System.out.println('
assert fixture.count(needle)==2
fixture=fixture.replace(needle,'frame.release();require(BrowserWebGLBridge.isGeometryTransferReuseEnabled()==Boolean.getBoolean("spoiledmilk.browserGeometryTransferReuse"),"Actual preparation receipt differs");require((OpenGLWorldChunkRenderer.fixturePreparedEntries>0)==BrowserWebGLBridge.isGeometryTransferReuseEnabled(),"Candidate preparation method not exercised exactly by selected policy");System.out.println(')
test=suite/'bridge-source/orsc/BrowserAnimatedChunkFixture.java';test.write_text(fixture)
classes=suite/('bridge-classes-'+java.parent.name);classes.mkdir(exist_ok=True)
sources=[renderer,test]+[root/('PC_Client/src/orsc/'+name+'.java')for name in ['BrowserFrameSinkRegistry','BrowserWorldLoadState','BrowserWebGLBridge','BrowserChunkMaterials']]
subprocess.run([str(java/'javac'),'-source','8','-target','8','-cp',str(jar),'-d',str(classes),*map(str,sources)],check=True)
results=[]
for cache in ['lru','active']:
 for animated in ['true','false']:
  for preparation in ['false','true']:
   result=subprocess.check_output([str(java/'java'),'-Xmx512m','-Djava.awt.headless=true','-Dspoiledmilk.browserCachePolicy='+cache,'-Dspoiledmilk.browserAnimatedBufferReuse='+animated,'-Dspoiledmilk.browserGeometryTransferReuse='+preparation,'-cp',str(classes)+os.pathsep+str(jar),'orsc.BrowserAnimatedChunkFixture'],text=True)
   results.append('cache='+cache+' animated='+animated+' preparation='+preparation+'\n'+result)
(suite/'bridge-results.txt').write_text('\n'.join(results))
print('PASS actual Bridge OFF/ON selection and all8 cache/animation/preparation combinations: pose/material/topology/rebase/budget/eviction/failure parity; no GPU/FPS claim')
