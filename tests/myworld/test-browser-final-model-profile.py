#!/usr/bin/env python3
from pathlib import Path
import subprocess,argparse
root=Path(__file__).resolve().parents[2];p=argparse.ArgumentParser();p.add_argument('--baseline-jar',type=Path,required=True);a=p.parse_args();out=Path(subprocess.check_output(['python3',str(root/'scripts/workspace-layout.py'),'path','tests','browser-final-model-profile','--mkdir'],text=True).strip());jar=root/'Client_Base/Open_RSC_Client.jar'
subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(out),str(root/'tests/myworld/BrowserFinalModelProfileFixture.java')],check=True)
results=[]
for artifact,profile in [(a.baseline_jar,False),(jar,False),(jar,True)]:
 results.append(subprocess.check_output(['java','-Djava.awt.headless=true','-Dspoiledmilk.browserBenchmark=true','-Dspoiledmilk.browserFinalModelProfile='+str(profile).lower(),'-cp',str(out)+':'+str(artifact),'BrowserFinalModelProfileFixture'],text=True).strip())
assert len(set(results))==1,results
print('PASS 120 model differential cases across frozen baseline, profile OFF and profile ON; direct owner phase counts, all primitive/array model fields identical: '+results[0])
