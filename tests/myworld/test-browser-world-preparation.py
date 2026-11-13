#!/usr/bin/env python3
"""CPU-only initial world progress contracts and exact Java compilation."""
import argparse,hashlib,json,subprocess,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--client-jar',required=True,type=Path);a=p.parse_args();jar=a.client_jar.resolve();assert jar.is_file()
base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-initial-world-progress','--mkdir'],text=True).strip());out=base/('suite-'+str(time.time_ns()));out.mkdir();classes=out/'classes';classes.mkdir()
sources=[ROOT/'PC_Client/src/orsc'/name for name in ['BrowserWorldPreparationProgress.java','BrowserWholeOverworldRam.java','BrowserWebGLBridge.java']]+[ROOT/'Client_Base/src/orsc'/name for name in ['BrowserStagedLogin.java','PacketHandler.java','mudclient.java']]+[ROOT/'PC_Client/src/orsc/ORSCApplet.java'];fixture=ROOT/'tests/myworld/BrowserWorldPreparationProgressFixture.java'
commands=[['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),*map(str,sources),str(fixture)],['java','-cp',str(classes)+':'+str(jar),'orsc.BrowserWorldPreparationProgressFixture'],['node','tests/myworld/test-browser-world-preparation.mjs'],['node','tests/myworld/test-browser-world-ready.mjs'],['node','--experimental-vm-modules','tests/myworld/test-browser-webgl-frontend.mjs','--public-alpha'],['node','--experimental-vm-modules','tests/myworld/test-browser-webgl-frontend.mjs'],['node','--experimental-vm-modules','tests/myworld/test-browser-session-stop-receipt.mjs']]
result={'passed':False,'clientJarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'sourceSha256':{str(f.relative_to(ROOT)):hashlib.sha256(f.read_bytes()).hexdigest()for f in [*sources,fixture]},'commands':commands,'scope':'CPU contracts and Java compilation; no browser/GPU/server or performance acceptance'}
try:
 for n,c in enumerate(commands):
  r=subprocess.run(c,cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);(out/(str(n)+'.log')).write_text(r.stdout);print(r.stdout,end='');r.check_returncode()
 result['passed']=True
finally:(out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
print(out/'result.json')
