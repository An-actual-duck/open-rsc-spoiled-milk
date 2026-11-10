#!/usr/bin/env python3
"""Freeze actual production shaders and old/current color backend; default prepares only."""
from pathlib import Path
from datetime import datetime,timezone
import argparse,hashlib,json,re,shutil,subprocess
ROOT=Path(__file__).resolve().parents[2]
BASE='e696adeee737dfdd36a9f7d3b500f1d5283f7feb'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def prepare(run_id):
 if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,79}',run_id):raise ValueError('Invalid plain run name')
 base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-color-frustum','--mkdir'],text=True).strip())
 run=base/'hardware'/run_id;run.mkdir(parents=True,exist_ok=False);web=run/'web';web.mkdir();source=run/'source';source.mkdir();classes=run/'classes';classes.mkdir()
 for file in (ROOT/'tools/browser-gameplay/web').glob('*.js'):shutil.copyfile(file,web/file.name)
 (web/'original-webgl.mjs').write_bytes(subprocess.check_output(['git','show',BASE+':tools/browser-gameplay/web/webgl.js'],cwd=ROOT))
 shutil.copyfile(ROOT/'tests/myworld/browser-color-frustum-hardware.html',web/'index.html');shutil.copyfile(ROOT/'tests/myworld/browser-color-frustum-hardware.js',web/'browser-color-frustum-hardware.js')
 paths=[ROOT/'PC_Client/src/orsc/OpenGLShaderProgram.java',ROOT/'PC_Client/src/orsc/OpenGLGeometryShadows.java',ROOT/'tests/myworld/BrowserColorShaderOracle.java']
 for file in paths:shutil.copyfile(file,source/file.name)
 jar=ROOT/'Client_Base/Open_RSC_Client.jar'
 with (run/'shader-oracle.log').open('w')as log:
  subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),*[str(source/p.name) for p in paths]],check=True,stdout=log,stderr=subprocess.STDOUT)
  result=subprocess.check_output(['java','-Djava.awt.headless=true','-cp',str(classes)+':'+str(jar),'orsc.BrowserColorShaderOracle',str(web/'shaders')],text=True).strip();log.write(result+'\n')
 length,digest=result.split(':');assert int(length)==len((web/'shaders/world.vert').read_text()) and digest==sha(web/'shaders/world.vert')
 files={'/'+str(p.relative_to(web)):{'sha256':sha(p),'bytes':p.stat().st_size}for p in sorted(web.rglob('*'))if p.is_file()}
 meta={'preparedOnly':True,'gpuExecuted':False,'preparedUtc':datetime.now(timezone.utc).isoformat(),'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'baselineCommit':BASE,'moduleSha256':sha(web/'webgl.js'),'mathModuleSha256':sha(web/'browser-color-frustum.js'),'originalSha256':sha(web/'original-webgl.mjs'),'productionVertexSha256':digest,'productionSourceSha256':{p.name:sha(p)for p in paths},'dependencyJarSha256':sha(jar),'webFiles':files,'scope':'Prepared actual-production-shader color-only pixel differential; GPU unrun, no performance claim','explicitRunCommand':['node',str(ROOT/'tests/myworld/run-browser-color-frustum-hardware.mjs'),'--run-hardware',str(run)]}
 (run/'stage.json').write_text(json.dumps(meta,indent=2)+'\n');return run
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--run-id',default=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'));mode=parser.add_mutually_exclusive_group();mode.add_argument('--prepare-only',action='store_true');mode.add_argument('--run-hardware',action='store_true');args=parser.parse_args();run=prepare(args.run_id);print(json.dumps({'prepared':str(run),'gpuExecuted':False,'status':'PREPARED_ONLY'},indent=2),flush=True)
 if args.run_hardware:subprocess.run(['node',str(ROOT/'tests/myworld/run-browser-color-frustum-hardware.mjs'),'--run-hardware',str(run)],cwd=ROOT,check=True)
