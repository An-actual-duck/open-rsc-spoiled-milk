#!/usr/bin/env python3
"""Freeze isolated source-bound real Worker GPU oracle; never start HTTP/browser/server."""
import argparse,datetime,hashlib,json,re,shutil,socket,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
BASE='58c61d98562e287c6e35a6056009cbe81c6d3464'
JAVA_SOURCES=['PC_Client/src/orsc/OpenGLShaderProgram.java','PC_Client/src/orsc/OpenGLGeometryShadows.java','PC_Client/src/orsc/BrowserSkyData.java','PC_Client/src/orsc/OpenGLSkySettings.java','Client_Base/src/orsc/RendererToneSettings.java','Client_Base/src/orsc/graphics/three/Scene.java','Client_Base/src/orsc/graphics/three/Renderer3DFrame.java','Client_Base/src/orsc/graphics/three/Renderer3DSettings.java','Client_Base/src/orsc/graphics/two/GraphicsController.java','Client_Base/src/orsc/graphics/two/MudClientGraphics.java','Client_Base/src/orsc/graphics/two/WorldOverlayCoverage.java','Client_Base/src/orsc/graphics/two/WorldOverlayDeltaTransport.java','Client_Base/src/orsc/graphics/two/WorldOverlayTileTracker.java','Client_Base/src/orsc/graphics/two/OverlayJournalObserver.java']
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def git(root,*args):return subprocess.check_output(['git',*args],cwd=root)
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--worker-source',type=Path,required=True);p.add_argument('--worker-commit',required=True);p.add_argument('--dependency-jar',type=Path,required=True);p.add_argument('--name',required=True);p.add_argument('--port',type=int,default=8218);a=p.parse_args()
 if not re.fullmatch('[A-Za-z0-9_-]{1,80}',a.name)or not re.fullmatch('[a-f0-9]{40}',a.worker_commit):p.error('Exact immutable commit/simple name required')
 with socket.socket()as s:s.bind(('127.0.0.1',a.port))
 source=a.worker_source.resolve();assert git(source,'rev-parse',a.worker_commit).decode().strip()==a.worker_commit
 category=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-render-worker-oracle','--mkdir'],text=True).strip());run=category/a.name
 if run.exists():p.error('Refuse prior immutable stage')
 web=run/'web';web.mkdir(parents=True);original=web/'original';original.mkdir();native=run/'native-source';native.mkdir();classes=run/'classes';classes.mkdir()
 def modules(repo,commit,dest):
  names=git(repo,'ls-tree','--name-only',commit+':tools/browser-gameplay/web').decode().splitlines()
  for name in names:
   if name.endswith('.js'):(dest/name).write_bytes(git(repo,'show',commit+':tools/browser-gameplay/web/'+name))
 modules(ROOT,BASE,original);modules(source,a.worker_commit,web)
 for name in ['browser-render-worker.js','browser-render-worker-entry.js','browser-render-worker-replay.js','browser-render-worker-transport.js','browser-render-worker-protocol.js']:assert(web/name).is_file(),name
 for name,out in [('browser-render-worker-hardware.js','browser-render-worker-hardware.js'),('browser-render-worker-hardware.html','index.html'),('browser-owned-scenario.mjs','browser-owned-scenario.mjs')]:shutil.copy2(ROOT/'tests/myworld'/name,web/out)
 paths=[];hashes={}
 for relative in JAVA_SOURCES:
  f=native/Path(relative).name;f.write_bytes(git(ROOT,'show',BASE+':'+relative));paths.append(f);hashes[relative]=sha(f)
 for name in ['BrowserColorShaderOracle.java','BrowserOwnedSkyOracle.java']:
  f=native/name;f.write_bytes(git(ROOT,'show',BASE+':tests/myworld/'+name));paths.append(f);hashes['tests/myworld/'+name]=sha(f)
 jar=a.dependency_jar.resolve();assert jar.is_file()
 with(run/'native-export.log').open('w')as log:
  subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),*map(str,paths)],check=True,stdout=log,stderr=subprocess.STDOUT)
  for klass,dest in [('BrowserColorShaderOracle','shaders'),('BrowserOwnedSkyOracle','sky')]:subprocess.run(['java','-Djava.awt.headless=true','-cp',str(classes)+':'+str(jar),'orsc.'+klass,str(web/dest)],check=True,stdout=log,stderr=subprocess.STDOUT)
 for name in ['world.vert','world.frag','depth.vert','depth.frag']:assert(web/'shaders'/name).stat().st_size>20
 # Same candidate backend code is used main-thread and imported by actual Worker;
 # baseline58 is a separate third comparison, not an alternate worker shader.
 modules_hash={str(f.relative_to(web)):sha(f)for f in web.rglob('*')if f.is_file()}
 receipt={'preparedOnly':True,'gpuExecuted':False,'browserOrServerStarted':False,'sourceCommit':git(ROOT,'rev-parse','HEAD').decode().strip(),'sourceDirty':bool(git(ROOT,'status','--porcelain').decode().strip()),'baselineCommit':BASE,'workerSource':str(source),'workerCommit':a.worker_commit,'dependencyJar':str(jar),'dependencyJarSHA256':sha(jar),'freshNativeJavaSourceSHA256':hashes,'webFileSHA256':modules_hash,'port':a.port,'url':f'http://127.0.0.1:{a.port}/','httpCommand':['python3','-m','http.server',str(a.port),'--bind','127.0.0.1','--directory',str(web)],'resultExpression':'window.renderWorkerProof','scope':'Prepared actual production main58/candidate-main/OffscreenCanvasWorker exactRGBA oracle; root executes hardware. Synthetic controlled CSSrect/DPR, no gameplay/realinput/FPS/scanout proof.','cases':['42 shared fullpipeline frames','nullopaqueT','fullC/T zeroholes/Float32alpha grid','physical1843x1037','odd2x/fractional/DPR2','resource delete/recreate/atlas epoch','mutateALLreturned+borrowedbuffers then no-resource-resend','actual Worker disposalACK']}
 (run/'stage.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps({'stage':str(run),'receipt':str(run/'stage.json'),'url':receipt['url'],'gpuExecuted':False},indent=2))
if __name__=='__main__':main()
