#!/usr/bin/env python3
"""Reproduce CPU-only public bulk startup lifecycle and full-bootstrap compilation."""
import argparse, hashlib, json, pathlib, subprocess, time
ROOT=pathlib.Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--client-jar',type=pathlib.Path,required=True);a=p.parse_args()
jar=a.client_jar.resolve();assert jar.is_file()
subject=pathlib.Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-startup-bulk','--mkdir'],text=True).strip())
run=subject/('suite-'+str(time.time_ns()));run.mkdir();classes=run/'classes';classes.mkdir()
sources=sorted((ROOT/'tools/browser-gameplay/src/orsc/browser').glob('*.java'))
fixtures=[ROOT/'tests/myworld/BrowserBulkStagingFixture.java',ROOT/'tests/myworld/browser-public-alpha/BootstrapFixture.java']
commands=[['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),*map(str,sources+fixtures)],
 ['java','-cp',str(classes)+':'+str(jar),'orsc.browser.BrowserBulkStagingFixture',str(run/'files')],
 ['java','-Djava.awt.headless=true','-cp',str(classes)+':'+str(jar),'orsc.browser.BootstrapFixture'],
 ['node','tests/myworld/test-browser-bulk-staging.mjs'],
 ['node','tests/myworld/test-browser-startup-progress.mjs'],
 ['node','--experimental-vm-modules','tests/myworld/test-browser-session-stop-receipt.mjs'],
 ['python3','tests/myworld/test-browser-startup-files.py'],
 ['node','tests/myworld/browser-public-alpha/policy.mjs'],
 ['node','tests/myworld/test-browser-display.mjs'],['node','tests/myworld/test-browser-world-ready.mjs']]
for extra in [[],['--public-alpha'],['--public-alpha','--stop-before-init'],['--public-alpha','--stop-during-archives'],['--public-alpha','--stop-during-runtime-download']]:
 commands.append(['node','--experimental-vm-modules','tests/myworld/test-browser-webgl-frontend.mjs',*extra])
results=[]
for index,cmd in enumerate(commands):
 result=subprocess.run(cmd,cwd=ROOT,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT);(run/(str(index)+'.log')).write_text(result.stdout)
 print(result.stdout,end='');results.append({'command':cmd,'exitCode':result.returncode});result.check_returncode()
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
tracked=sources+fixtures+[ROOT/'tools/browser-gameplay/web/browser-bulk-staging.js',ROOT/'tools/browser-gameplay/web/browser.js']
(run/'receipt.json').write_text(json.dumps({'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'clientJar':str(jar),'clientJarSha256':sha(jar),'sourceSha256':{str(f.relative_to(ROOT)):sha(f)for f in tracked},'results':results,'passed':True,'scope':'CPU fixtures and full helper compilation; no browser, GPU, network, startup timing or RSS proof. Client core JAR is supplied intact; no core classes replaced.'},indent=2)+'\n')
print(run/'receipt.json')
