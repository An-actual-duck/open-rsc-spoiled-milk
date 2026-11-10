#!/usr/bin/env python3
"""Freeze actual-production shader/native sky physical-output proof; never launches GPU/server."""
import argparse,hashlib,json,re,shutil,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
BASE='f2cca708956167e15eadebc4b437fab896eaa40e'
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--run-id',required=True);p.add_argument('--dependency-jar',type=Path,required=True);args=p.parse_args()
 if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,79}',args.run_id):raise ValueError('Invalid run name')
 base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-physical-output','--mkdir'],text=True).strip())
 run=base/'hardware'/args.run_id;run.mkdir(parents=True,exist_ok=False);web=run/'web';web.mkdir();original=web/'original';original.mkdir();classes=run/'classes';classes.mkdir()
 for src in (ROOT/'tools/browser-gameplay/web').glob('*.js'):shutil.copyfile(src,web/src.name)
 for name in subprocess.check_output(['git','ls-tree','--name-only',BASE+':tools/browser-gameplay/web'],cwd=ROOT,text=True).splitlines():
  if name.endswith('.js'):(original/name).write_bytes(subprocess.check_output(['git','show',BASE+':tools/browser-gameplay/web/'+name],cwd=ROOT))
 for src,dest in [('browser-owned-scenario.mjs','browser-owned-scenario.mjs'),('browser-physical-hardware.js','browser-physical-hardware.js'),('browser-physical-hardware.html','index.html')]:shutil.copyfile(ROOT/'tests/myworld'/src,web/dest)
 jar=args.dependency_jar.resolve();helpers=[ROOT/'tests/myworld/BrowserColorShaderOracle.java',ROOT/'tests/myworld/BrowserOwnedSkyOracle.java']
 with (run/'native-oracle.log').open('w')as log:
  subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),*map(str,helpers)],stdout=log,stderr=subprocess.STDOUT,check=True)
  for name,out in [('BrowserColorShaderOracle','shaders'),('BrowserOwnedSkyOracle','sky')]:subprocess.run(['java','-Djava.awt.headless=true','-cp',str(classes)+':'+str(jar),'orsc.'+name,str(web/out)],stdout=log,stderr=subprocess.STDOUT,check=True)
 sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
 receipt={'preparedOnly':True,'gpuExecuted':False,'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'baselineCommit':BASE,'dependencyJar':str(jar),'dependencyJarSha256':sha(jar),'webFiles':{'/'+str(f.relative_to(web)):{'sha256':sha(f),'bytes':f.stat().st_size}for f in sorted(web.rglob('*'))if f.is_file()},'scope':'Isolated production shader/native-sky physical backing vs expanded-logical-UI reference; no gameplay/input/FPS proof; GPU execution manager-only.'}
 (run/'stage.json').write_text(json.dumps(receipt,indent=2)+'\n');print(run)
if __name__=='__main__':main()
