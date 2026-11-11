#!/usr/bin/env python3
"""Explicit source-artifact policy gates, no browser/game/server/GPU."""
from pathlib import Path
import argparse,subprocess,hashlib,json
root=Path(__file__).resolve().parents[2];p=argparse.ArgumentParser(description=__doc__);p.add_argument('--client-jar',type=Path,required=True);a=p.parse_args();jar=a.client_jar.resolve()
suite=Path(subprocess.check_output(['python3',str(root/'scripts/workspace-layout.py'),'path','tests','browser-whole-zone-policy','--mkdir'],text=True).strip());classes=suite/'classes';classes.mkdir(exist_ok=True)
source=root/'tests/myworld/BrowserWholeZonePolicyFixture.java';subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),str(source)],check=True)
results=[]
for properties,expected in [([],False),(['-Dspoiledmilk.browserWholeZone=true'],False),(['-Dspoiledmilk.browserBenchmark=true'],False),(['-Dspoiledmilk.browserWholeZone=true','-Dspoiledmilk.browserBenchmark=true'],True)]:
 result=subprocess.run(['java','-Xmx128m','-Djava.awt.headless=true']+properties+['-cp',str(classes)+':'+str(jar),'orsc.BrowserWholeZonePolicyFixture',str(expected).lower()],text=True,capture_output=True,check=True);results.append({'properties':properties,'stdout':result.stdout,'stderr':result.stderr});print(result.stdout,end='')
(suite/'result.json').write_text(json.dumps({'clientJar':str(jar),'clientJarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'fixtureSha256':hashlib.sha256(source.read_bytes()).hexdigest(),'results':results,'scope':'CPU startup gates and resolver install/clear only; no source/GPU readiness or performance proof'},indent=2)+'\n')
