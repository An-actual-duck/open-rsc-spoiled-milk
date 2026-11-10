#!/usr/bin/env python3
"""Frame-local elevation memo proof against the real mutable World source."""
from pathlib import Path
import os,subprocess
root=Path(__file__).resolve().parents[2]
out=Path(subprocess.check_output(['python3',str(root/'scripts/workspace-layout.py'),'path','tests','browser-elevation-sample-batch','--mkdir'],text=True).strip())
classes=out/'classes';classes.mkdir(exist_ok=True);jar=root/'Client_Base/Open_RSC_Client.jar'
subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),str(root/'Client_Base/src/orsc/mudclient.java'),str(root/'tests/myworld/BrowserResidentInputsFixture.java'),str(root/'tests/myworld/BrowserElevationSampleBatchFixture.java')],check=True)
for selected in ['true','false']:
 subprocess.run(['java','-Xmx512m','-Djava.awt.headless=true','-Dspoiledmilk.browserElevationSampleBatch='+selected,'-cp',str(classes)+os.pathsep+str(jar),'orsc.BrowserElevationSampleBatchFixture'],check=True)
