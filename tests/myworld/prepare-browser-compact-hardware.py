#!/usr/bin/env python3
"""Prepare a frozen source-bound WebGL2 compact oracle; never starts a service/GPU."""
import argparse,datetime,hashlib,json,re,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
JAVA=['PC_Client/src/orsc/OpenGLShaderProgram.java','PC_Client/src/orsc/OpenGLGeometryShadows.java','tests/myworld/BrowserColorShaderOracle.java']
DRIVERS=['tests/myworld/prepare-browser-compact-hardware.py','tests/myworld/browser-compact-hardware.js','tests/myworld/browser-compact-hardware.html']
def git(*args):return subprocess.check_output(['git',*args],cwd=ROOT)
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--name',required=True);p.add_argument('--source-commit',required=True);p.add_argument('--dependency-jar',type=Path,required=True);p.add_argument('--port',type=int,default=8235);a=p.parse_args()
 if not re.fullmatch('[A-Za-z0-9][A-Za-z0-9_.-]{0,79}',a.name)or not re.fullmatch('[0-9a-f]{40}',a.source_commit):p.error('Simple name and exact source commit required')
 if a.port<1024 or a.port>65535:p.error('Unprivileged valid port required')
 if git('rev-parse',a.source_commit).decode().strip()!=a.source_commit:p.error('Unknown source commit')
 jar=a.dependency_jar.resolve()
 if not jar.is_file():p.error('Explicit existing dependency JAR required')
 for name in DRIVERS:
  if git('show',a.source_commit+':'+name)!=(ROOT/name).read_bytes():p.error('Oracle driver must match exact committed source')
 base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-compact-hardware','--mkdir'],text=True).strip());run=base/a.name
 if run.exists():p.error('Refuse prior immutable stage')
 web=run/'web';web.mkdir(parents=True);source=run/'source';source.mkdir();classes=run/'classes';classes.mkdir();hashes={}
 for name in git('ls-tree','--name-only',a.source_commit+':tools/browser-gameplay/web').decode().splitlines():
  if name.endswith('.js'):(web/name).write_bytes(git('show',a.source_commit+':tools/browser-gameplay/web/'+name))
 for name in ['browser-compact-hardware.js','browser-compact-hardware.html']:(web/('index.html'if name.endswith('.html')else name)).write_bytes(git('show',a.source_commit+':tests/myworld/'+name))
 paths=[]
 for name in JAVA:
  out=source/Path(name).name;out.write_bytes(git('show',a.source_commit+':'+name));paths.append(out);hashes[name]=sha(out)
 with(run/'shader-export.log').open('w')as log:
  subprocess.run(['nice','-n','10','javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),*map(str,paths)],check=True,stdout=log,stderr=subprocess.STDOUT)
  subprocess.run(['nice','-n','10','java','-Djava.awt.headless=true','-cp',str(classes)+':'+str(jar),'orsc.BrowserColorShaderOracle',str(web/'shaders')],check=True,stdout=log,stderr=subprocess.STDOUT)
 for name in ['world.vert','world.frag','depth.vert','depth.frag']:
  if (web/'shaders'/name).stat().st_size<20:raise ValueError('Missing production shader '+name)
 files={str(f.relative_to(web)):{'sha256':sha(f),'bytes':f.stat().st_size}for f in sorted(web.rglob('*'))if f.is_file()}
 provenance={'sourceCommit':a.source_commit,'oracleDriverSHA256':{name:hashlib.sha256(git('show',a.source_commit+':'+name)).hexdigest()for name in DRIVERS},'productionShaderSourceSHA256':hashes,'dependencyJar':str(jar),'dependencyJarSHA256':sha(jar),'shaderExport':'Maintained Java shader constants compiled from exact source commit; dependency JAR supplies compilation dependencies, not shader strings','webFiles':files,'GPUExecutedAtPreparation':False}
 (web/'provenance.json').write_text(json.dumps(provenance,indent=2)+'\n')
 receipt={'preparedOnly':True,'gpuExecuted':False,'browserOrServerStarted':False,'preparedUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'sourceCommit':a.source_commit,'web':str(web),'provenanceSHA256':sha(web/'provenance.json'),'webFiles':files,'url':f'http://127.0.0.1:{a.port}/','httpCommand':['python3','-m','http.server',str(a.port),'--bind','127.0.0.1','--directory',str(web)],'start':'Click Run hardware checks, or evaluate document.querySelector("#run").click()','resultExpression':'window.compactGeometryProof','scope':'Preparation only. Actual transform feedback, production color/depth, cancellation and context loss await manager Firefox hardware run. No FPS/complete gameplay or GPU-memory claim.'}
 (run/'stage.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps({'prepared':str(run),'receipt':str(run/'stage.json'),'url':receipt['url'],'gpuExecuted':False}))
if __name__=='__main__':main()
