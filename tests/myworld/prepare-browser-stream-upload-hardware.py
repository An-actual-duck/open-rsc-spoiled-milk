#!/usr/bin/env python3
"""Prepare frozen production-shader streaming error probes. Never launches a browser/GPU/server."""
import argparse, hashlib, json, re, shutil, subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--run-id',required=True);p.add_argument('--dependency-jar',type=Path,default=ROOT/'Client_Base/Open_RSC_Client.jar');args=p.parse_args()
 if not re.fullmatch(r'[a-zA-Z0-9][a-zA-Z0-9_.-]{0,79}',args.run_id):raise ValueError('Invalid plain run ID')
 base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-stream-upload-validation','--mkdir'],text=True).strip())
 run=base/'hardware'/args.run_id;run.mkdir(parents=True,exist_ok=False);web=run/'web';web.mkdir();oracle=web/'oracle';oracle.mkdir();classes=run/'classes';classes.mkdir()
 for src in (ROOT/'tools/browser-gameplay/web').glob('*.js'):shutil.copyfile(src,web/src.name)
 for src,name in [('browser-stream-upload-hardware.html','index.html'),('browser-stream-upload-hardware.js','browser-stream-upload-hardware.js')]:shutil.copyfile(ROOT/'tests/myworld'/src,web/name)
 jar=args.dependency_jar.resolve();helper=ROOT/'tests/myworld/BrowserColorShaderOracle.java'
 with (run/'shader-oracle.log').open('w')as log:
  subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),str(helper)],stdout=log,stderr=subprocess.STDOUT,check=True)
  subprocess.run(['java','-Djava.awt.headless=true','-cp',str(classes)+':'+str(jar),'orsc.BrowserColorShaderOracle',str(oracle)],stdout=log,stderr=subprocess.STDOUT,check=True)
 sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
 receipt={'preparedOnly':True,'gpuExecuted':False,'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'dependencyJar':str(jar),'dependencyJarSha256':sha(jar),'webFiles':{'/'+str(f.relative_to(web)):{'sha256':sha(f),'bytes':f.stat().st_size}for f in sorted(web.rglob('*'))if f.is_file()},'scope':'Actual production shaders and real queued WebGL error probes; browser execution remains manager-only. No gameplay/image/FPS claim.'}
 (run/'stage.json').write_text(json.dumps(receipt,indent=2)+'\n');print(run)
if __name__=='__main__':main()
