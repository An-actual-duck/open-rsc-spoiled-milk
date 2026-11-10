#!/usr/bin/env python3
"""Frozen production OLD/new byte oracle, with test-only preparation-call counters.

The shared maintained corpus exercises browser and real native plain/atlas/proof
writers. Source instrumentation only counts private material resolution calls;
it does not substitute any arithmetic or reconstruct expected vertex fields.
"""
from pathlib import Path
import hashlib,json,os,struct,subprocess

ROOT=Path(__file__).resolve().parents[2]
BASE='325bf42b60fe411705f9f14c0a3ef6debabe24a7'
SOURCE='PC_Client/src/orsc/OpenGLWorldChunkRenderer.java'
suite=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-geometry-transfer','--mkdir'],text=True).strip())
java=Path(os.environ.get('JAVA_HOME','/home/justin/jdk8u482-b08'))/'bin'
suite=suite/java.parent.name;suite.mkdir(exist_ok=True)
jar=Path(os.environ.get('BROWSER_TEST_DEPENDENCY_JAR',str(ROOT/'Client_Base/Open_RSC_Client.jar')))
baseline=subprocess.check_output(['git','show',BASE+':'+SOURCE],cwd=ROOT).decode()
candidate=(ROOT/SOURCE).read_text()
fixture=(ROOT/'tests/myworld/BrowserVertexPackingFixture.java').read_text()
fixture=fixture.replace('public final class BrowserVertexPackingFixture {','''public final class BrowserVertexPackingFixture {
    static long effectiveTotal,rawTotal,materialTotal;
    static float[] pack(OpenGLWorldChunkRenderer renderer,Renderer3DWorldChunkFrame.ChunkMesh mesh,Renderer3DFrame frame)throws Exception {
        OpenGLWorldChunkRenderer.fixtureEffective=OpenGLWorldChunkRenderer.fixtureRaw=OpenGLWorldChunkRenderer.fixtureMaterial=0;
        Method method=OpenGLWorldChunkRenderer.class.getDeclaredMethod(Boolean.getBoolean("fixture.resolved")?"copyBackendChunkVerticesResolved":"copyBackendChunkVertices",Renderer3DWorldChunkFrame.ChunkMesh.class,Renderer3DFrame.class);
        method.setAccessible(true);float[] result=(float[])method.invoke(renderer,mesh,frame);
        effectiveTotal+=OpenGLWorldChunkRenderer.fixtureEffective;rawTotal+=OpenGLWorldChunkRenderer.fixtureRaw;materialTotal+=OpenGLWorldChunkRenderer.fixtureMaterial;
        return result;
    }
''')
assert fixture.count('browser.copyBackendChunkVertices(mesh,sourceFrame)')==1
fixture=fixture.replace('browser.copyBackendChunkVertices(mesh,sourceFrame)','pack(browser,mesh,sourceFrame)')
fixture=fixture.replace('System.out.println("PASS emitted"','System.out.println("COUNTERS "+effectiveTotal+" "+rawTotal+" "+materialTotal);\n        System.out.println("PASS emitted"')
source_fixture=suite/'BrowserVertexPackingFixture.java';source_fixture.write_text(fixture)
outputs=[];edge_outputs=[];counts=[];hashes={}
for label,source,resolved in [('old',baseline,False),('off',candidate,False),('on',candidate,True)]:
    original=source
    needle='final class OpenGLWorldChunkRenderer'
    # Insert fields at the class opening, preserving any implements declaration.
    at=source.index('{',source.index(needle))+1
    source=source[:at]+'\n static long fixtureEffective,fixtureRaw,fixtureMaterial;\n'+source[at:]
    for name,counter in [('effectiveChunkTextureId','Effective'),('rawMaterialColorForTriangle','Raw'),('materialColorForTriangle','Material')]:
        start=source.index('private int '+name+'(');at=source.index('{',start)+1
        source=source[:at]+'fixture'+counter+'++;'+source[at:]
    src=suite/(label+'-source/orsc/OpenGLWorldChunkRenderer.java');src.parent.mkdir(parents=True,exist_ok=True);src.write_text(source)
    classes=suite/(label+'-classes-'+java.parent.name);classes.mkdir(exist_ok=True)
    subprocess.run([str(java/'javac'),'-source','8','-target','8','-cp',str(jar),'-d',str(classes),str(src),str(ROOT/'PC_Client/src/orsc/BrowserChunkMaterials.java'),str(source_fixture),str(ROOT/'tests/myworld/BrowserGeometryTransferEdgeFixture.java')],check=True)
    output=suite/(label+'-oracle.bin');outputs.append(output)
    result=subprocess.check_output([str(java/'java'),'-Xmx512m','-Djava.awt.headless=true','-Dfixture.resolved='+str(resolved).lower(),'-cp',str(classes)+os.pathsep+str(jar),'orsc.BrowserVertexPackingFixture',str(output),'new' if label!='old' else 'old'],text=True)
    (suite/(label+'-stdout.txt')).write_text(result)
    counts.append(list(map(int,next(line for line in result.splitlines() if line.startswith('COUNTERS ')).split()[1:])))
    edge_output=suite/(label+'-edge-oracle.bin');edge_outputs.append(edge_output)
    # HotSpot may elide repeated exception detail according to different JIT
    # thresholds. Keep the real original failure messages observable in all runs.
    edge_result=subprocess.check_output([str(java/'java'),'-XX:-OmitStackTraceInFastThrow','-Xmx512m','-Djava.awt.headless=true','-Dfixture.resolved='+str(resolved).lower(),'-cp',str(classes)+os.pathsep+str(jar),'orsc.BrowserGeometryTransferEdgeFixture',str(edge_output)],text=True)
    (suite/(label+'-edge-stdout.txt')).write_text(edge_result)
    hashes[label]=hashlib.sha256(original.encode()).hexdigest()
streams=[p.read_bytes() for p in outputs]
assert streams[0]==streams[1]==streams[2],'Frozen native/browser packed float streams differ'
edges=[p.read_bytes() for p in edge_outputs]
assert edges[0]==edges[1]==edges[2],'Frozen edge/material/exception streams differ'
assert counts[0]==counts[1] and counts[0]==[n*3 for n in counts[2]],counts
data=streams[0];cases=struct.unpack_from('>I',data)[0];at=4;fields=0
for _ in range(cases*3):
    size=struct.unpack_from('>I',data,at)[0];at+=4+size*4;fields+=size
assert at==len(data)
report={'baseline':BASE,'sourceSha256':hashes,'dependencyJar':str(jar),'dependencySha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'cases':cases,'edgeCases':1200,'rawFloatFieldsCompared':fields,'bytesComparedPerPolicy':len(data),'edgeBytesComparedPerPolicy':len(edges[0]),'oracleSha256':hashlib.sha256(data).hexdigest(),'edgeOracleSha256':hashlib.sha256(edges[0]).hexdigest(),'materialResolutionCounts':dict(zip(['old','off','on'],counts)),'counterOrder':['effective','raw','material'],'nativePlainAtlasAndProofCompared':True,'independentFrozenProcesses':True,'GPU':False,'FPSClaim':False}
(suite/'result.json').write_text(json.dumps(report,indent=2)+'\n')
print('PASS',fields,'raw float fields across',cases,'cases, browser OFF/ON and native streams identical to frozen source; exact material-call reduction3x; no GPU/FPS claim')
