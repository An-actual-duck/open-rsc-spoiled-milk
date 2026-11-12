#!/usr/bin/env python3
"""Compile maintained copy helper plus actual-filesystem fixture; no browser/service."""
import hashlib,json,pathlib,subprocess,sys,time
ROOT=pathlib.Path(__file__).resolve().parents[2]
subject=pathlib.Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-startup-feedback','--mkdir'],text=True).strip())
run=subject/('copy-'+str(time.time_ns()));run.mkdir();classes=run/'classes';classes.mkdir()
sources=[ROOT/'tools/browser-gameplay/src/orsc/browser/BrowserBootstrapFiles.java',ROOT/'tests/myworld/BrowserBootstrapFilesFixture.java']
commands=[['javac','-source','8','-target','8','-d',str(classes),*map(str,sources)],['java','-cp',str(classes),'orsc.browser.BrowserBootstrapFilesFixture',str(run/'files')]]
for index,cmd in enumerate(commands):
 result=subprocess.run(cmd,cwd=ROOT,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT);(run/f'{index}.log').write_text(result.stdout);print(result.stdout,end='');result.check_returncode()
(run/'receipt.json').write_text(json.dumps({'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'sourceSha256':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest()for p in sources},'commands':commands,'passed':True,'browserOrNetworkStarted':False},indent=2)+'\n')
print(run/'receipt.json')
