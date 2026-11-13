#!/usr/bin/env python3
"""Compile real staged client sources and run CPU protocol/policy contracts."""
import argparse,hashlib,json,subprocess,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--client-jar',required=True,type=Path);a=p.parse_args();jar=a.client_jar.resolve();assert jar.is_file()
base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-staged-login-client','--mkdir'],text=True).strip());out=base/('suite-'+str(time.time_ns()));out.mkdir();classes=out/'classes';classes.mkdir()
sources=[ROOT/'Client_Base/src/orsc'/n for n in ('BrowserStagedLogin.java','PacketHandler.java','mudclient.java')]+[ROOT/'PC_Client/src/orsc'/n for n in ('BrowserWorldPreparationProgress.java','BrowserWholeOverworldRam.java','BrowserWebGLBridge.java','ORSCApplet.java')]
fixtures=[ROOT/'tests/myworld/BrowserStagedLoginFixture.java',ROOT/'tests/myworld/browser-public-alpha/BootstrapFixture.java']
bootstrap=list((ROOT/'tools/browser-gameplay/src/orsc/browser').glob('*.java'))
cp=str(classes)+':'+str(jar)
commands=[['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),*map(str,sources),str(fixtures[0])],['javac','-source','8','-target','8','-cp',cp,'-d',str(classes),*map(str,bootstrap),str(fixtures[1])],['java','-Djava.awt.headless=true','-Dopenrsc.worldBuilderInstalledClientProfile='+str(ROOT/'Client_Base/world-builder-configs/installed-client.json'),'-cp',cp,'orsc.BrowserStagedLoginFixture'],['java','-Djava.awt.headless=true','-cp',cp,'orsc.browser.BootstrapFixture'],['node','tests/myworld/test-browser-transport.mjs'],['node','tests/myworld/test-browser-staged-login.mjs'],['node','tests/myworld/test-browser-repeatable-policy.mjs'],['node','tests/myworld/browser-public-alpha/policy.mjs'],['node','tests/myworld/test-browser-world-preparation.mjs'],['node','tests/myworld/test-browser-world-ready.mjs'],['node','--experimental-vm-modules','tests/myworld/test-browser-webgl-frontend.mjs','--public-alpha'],['node','--experimental-vm-modules','tests/myworld/test-browser-webgl-frontend.mjs'],['node','--experimental-vm-modules','tests/myworld/test-browser-session-stop-receipt.mjs']]
result=dict(passed=False,clientJarSha256=hashlib.sha256(jar.read_bytes()).hexdigest(),sourceCommit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),sourceSha256={str(f.relative_to(ROOT)):hashlib.sha256(f.read_bytes()).hexdigest()for f in [*sources,*fixtures,*bootstrap]},commands=commands,scope='CPU codec/state/retention/frontend and focused source compilation. No full build, replacement JAR, actual browser/GPU/server, cold login or performance acceptance.')
try:
 for n,c in enumerate(commands):
  r=subprocess.run(c,cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True);(out/(str(n)+'.log')).write_text(r.stdout);print(r.stdout,end='');r.check_returncode()
 result['passed']=True
finally:(out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
print(out/'result.json')
