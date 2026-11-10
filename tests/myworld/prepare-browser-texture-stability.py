#!/usr/bin/env python3
"""Freeze production-data world material comparison; never launches a browser."""
from pathlib import Path
from datetime import datetime,timezone
import argparse,hashlib,json,re,shutil,subprocess
ROOT=Path(__file__).resolve().parents[2]
APPROVED='1046b115e3a5cdc6fb9473bea0630ea52f44a08b96a17f9b55fd2645720a1326'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--dependency-jar',type=Path,required=True);p.add_argument('--run-id',required=True);a=p.parse_args()
 if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,79}',a.run_id):raise ValueError('Invalid run id')
 jar=a.dependency_jar.resolve();assert sha(jar)==APPROVED,'Unapproved dependency JAR';cache=jar.parent/'Cache';assert cache.is_dir()
 base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-texture-stability','--mkdir'],text=True).strip());run=base/'hardware'/a.run_id;run.mkdir(parents=True,exist_ok=False);web=run/'web';web.mkdir();classes=run/'classes';classes.mkdir();source=run/'source';source.mkdir()
 for file in (ROOT/'tools/browser-gameplay/web').glob('*.js'):shutil.copyfile(file,web/file.name)
 for name in ['BrowserTextureStabilityOracle.java','BrowserColorShaderOracle.java'] :shutil.copyfile(ROOT/'tests/myworld'/name,source/name)
 shutil.copyfile(ROOT/'tests/myworld/browser-texture-stability-hardware.html',web/'index.html');shutil.copyfile(ROOT/'tests/myworld/browser-texture-stability-hardware.js',web/'browser-texture-stability-hardware.js')
 with (run/'oracle.log').open('w')as log:
  subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),*[str(p)for p in source.glob('*.java')]],check=True,stdout=log,stderr=subprocess.STDOUT)
  cp=str(classes)+':'+str(jar)
  for cls,dest,extra in [('orsc.BrowserColorShaderOracle','shaders',[]),('orsc.BrowserTextureStabilityOracle','assets',[str(cache)])]:subprocess.run(['java','-Djava.awt.headless=true','-cp',cp,cls,str(web/dest),*extra],check=True,stdout=log,stderr=subprocess.STDOUT)
 meta={'preparedOnly':True,'gpuExecuted':False,'preparedUtc':datetime.now(timezone.utc).isoformat(),'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'sourceDirty':bool(subprocess.check_output(['git','status','--porcelain'],cwd=ROOT,text=True).strip()),'dependencySource':'cb699e5c425d97b01bb357955c71b201879608ca','dependencyJarSha256':sha(jar),'scope':'Firefox hardware proof pending; actual production shaders/palette/tree; synthetic arrangement; no FPS/visual acceptance claim','files':{str(p.relative_to(web)):{'sha256':sha(p),'bytes':p.stat().st_size}for p in sorted(web.rglob('*'))if p.is_file()}}
 (run/'stage.json').write_text(json.dumps(meta,indent=2)+'\n');(web/'stage.json').write_text(json.dumps(meta,indent=2)+'\n');print(run)
if __name__=='__main__':main()
