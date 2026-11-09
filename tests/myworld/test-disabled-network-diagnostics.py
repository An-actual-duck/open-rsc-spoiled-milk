#!/usr/bin/env python3
"""Actual client class, separate enabled/disabled JVMs; no sockets or server."""
from pathlib import Path
import subprocess,json
root=Path(__file__).resolve().parents[2]
out=Path(subprocess.check_output(['python3',str(root/'scripts/workspace-layout.py'),'path','tests','disabled-network-diagnostics','--mkdir'],text=True).strip())
classes=out/'classes';classes.mkdir(exist_ok=True)
jar=root/'Client_Base/Open_RSC_Client.jar'
subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),str(root/'tests/myworld/NetworkDisabledDiagnosticsFixture.java')],check=True)
reports=[]
for mode in ['false','true']:
    result=subprocess.run(['java','-Djava.awt.headless=true','-Dspoiledmilk.boundaryDiagnostics='+mode,'-cp',str(classes)+':'+str(jar),'orsc.net.NetworkDisabledDiagnosticsFixture'],capture_output=True,text=True)
    (out/('mode-'+mode+'.log')).write_text(result.stdout+result.stderr)
    result.check_returncode()
    report=json.loads(next(line[7:] for line in result.stdout.splitlines() if line.startswith('RESULT ')))
    reports.append(report);print(result.stdout.strip())
assert reports[0]['decodedSha256']==reports[1]['decodedSha256'],'Diagnostics changed decoded packets'
(out/'comparison.json').write_text(json.dumps({'reports':reports,'decodedPacketsIdentical':True,'scope':'Actual packaged frame reader with scripted byte delivery; no network/GPU/browser performance claim'},indent=2)+'\n')
