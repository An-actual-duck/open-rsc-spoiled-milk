#!/usr/bin/env python3
"""Freeze actual production shaders and old/current color backend; default prepares only."""
from pathlib import Path
from datetime import datetime,timezone
import argparse,hashlib,json,re,shutil,subprocess
ROOT=Path(__file__).resolve().parents[2]
BASE='37a60529bd2569e2832338e3770feeb699aec025'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def prepare(run_id,dependency_jar,client_source_commit):
 if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,79}',run_id):raise ValueError('Invalid plain run name')
 base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-ui-preupload','--mkdir'],text=True).strip())
 run=base/'hardware'/run_id;run.mkdir(parents=True,exist_ok=False);web=run/'web';web.mkdir();source=run/'source';source.mkdir();classes=run/'classes';classes.mkdir()
 for file in (ROOT/'tools/browser-gameplay/web').glob('*.js'):shutil.copyfile(file,web/file.name)
 original=web/'original';original.mkdir()
 for file in subprocess.check_output(['git','ls-tree','--name-only',BASE+':tools/browser-gameplay/web'],cwd=ROOT,text=True).splitlines():
  if file.endswith('.js'):(original/file).write_bytes(subprocess.check_output(['git','show',BASE+':tools/browser-gameplay/web/'+file],cwd=ROOT))
 shutil.copyfile(ROOT/'tests/myworld/browser-owned-scenario.mjs',web/'browser-owned-scenario.mjs')
 shutil.copyfile(ROOT/'tests/myworld/browser-ui-preupload-hardware.html',web/'index.html');shutil.copyfile(ROOT/'tests/myworld/browser-ui-preupload-hardware.js',web/'browser-ui-preupload-hardware.js')
 paths=[ROOT/'PC_Client/src/orsc/OpenGLShaderProgram.java',ROOT/'PC_Client/src/orsc/OpenGLGeometryShadows.java',ROOT/'tests/myworld/BrowserColorShaderOracle.java',ROOT/'tests/myworld/BrowserOwnedSkyOracle.java']
 for file in paths:shutil.copyfile(file,source/file.name)
 dependency=Path(dependency_jar).expanduser().resolve(strict=True);jar=run/'dependency-client.jar';shutil.copyfile(dependency,jar)
 with (run/'shader-oracle.log').open('w')as log:
  subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),*[str(source/p.name) for p in paths]],check=True,stdout=log,stderr=subprocess.STDOUT)
  result=subprocess.check_output(['java','-Djava.awt.headless=true','-cp',str(classes)+':'+str(jar),'orsc.BrowserColorShaderOracle',str(web/'shaders')],text=True).strip();log.write(result+'\n')
 with (run/'sky-oracle.log').open('w')as log:subprocess.run(['java','-Djava.awt.headless=true','-cp',str(classes)+':'+str(jar),'orsc.BrowserOwnedSkyOracle',str(web/'sky')],check=True,stdout=log,stderr=subprocess.STDOUT)
 length,digest=result.split(':');assert int(length)==len((web/'shaders/world.vert').read_text()) and digest==sha(web/'shaders/world.vert')
 files={'/'+str(p.relative_to(web)):{'sha256':sha(p),'bytes':p.stat().st_size}for p in sorted(web.rglob('*'))if p.is_file()}
 meta={'preparedOnly':True,'gpuExecuted':False,'preparedUtc':datetime.now(timezone.utc).isoformat(),'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'baselineCommit':BASE,'moduleSha256':sha(web/'webgl.js'),'uiPreuploadModuleSha256':sha(web/'browser-ui-preupload.js'),'originalSha256':sha(original/'webgl.js'),'productionVertexSha256':digest,'productionSourceSha256':{p.name:sha(p)for p in paths},'clientSourceCommit':client_source_commit,'dependencyJar':str(dependency),'dependencyJarSha256':sha(jar),'webFiles':files,'scope':'Prepared actual-production-shader/native-sky full pre-world RGB upload pipeline pixel differential; GPU unrun, no performance claim','explicitRunCommand':['node',str(ROOT/'tests/myworld/run-browser-ui-preupload-hardware.mjs'),'--run-hardware',str(run)]}
 (run/'stage.json').write_text(json.dumps(meta,indent=2)+'\n');return run
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--dependency-jar',type=Path,required=True);parser.add_argument('--client-source-commit',required=True);parser.add_argument('--run-id',default=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'));mode=parser.add_mutually_exclusive_group();mode.add_argument('--prepare-only',action='store_true');mode.add_argument('--run-hardware',action='store_true');args=parser.parse_args();run=prepare(args.run_id,args.dependency_jar,args.client_source_commit);print(json.dumps({'prepared':str(run),'gpuExecuted':False,'status':'PREPARED_ONLY'},indent=2),flush=True)
 if args.run_hardware:subprocess.run(['node',str(ROOT/'tests/myworld/run-browser-ui-preupload-hardware.mjs'),'--run-hardware',str(run)],cwd=ROOT,check=True)
