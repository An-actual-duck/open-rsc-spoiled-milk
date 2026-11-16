#!/usr/bin/env python3
"""Read-only packaged-client animation sizing audit; no server or gameplay state."""
from pathlib import Path
import subprocess,time
ROOT=Path(__file__).resolve().parents[1]
base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','diagnostics','animation-sizing','--mkdir'],cwd=ROOT,text=True).strip())/str(time.time_ns());base.mkdir()
jar=ROOT/'Client_Base/Open_RSC_Client.jar'
def run(cmd,name):
 r=subprocess.run([str(x) for x in cmd],cwd=base,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT);(base/(name+'.log')).write_text(r.stdout)
 if r.returncode:raise SystemExit(r.stdout)
 return r.stdout
run(['javac','-source','8','-target','8','-cp',jar,'-d',base,ROOT/'tests/myworld/java/AnimationSizingAudit.java'],'compile')
print(run(['java','-Djava.awt.headless=true','-cp',str(base)+':'+str(jar),'orsc.AnimationSizingAudit',base/'sizes.csv'],'audit').strip())
print(base/'sizes.csv')
