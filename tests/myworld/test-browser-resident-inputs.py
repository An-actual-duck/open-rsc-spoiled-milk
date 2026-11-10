#!/usr/bin/env python3
"""Exact differential against the production uncached resident input builder."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[2]
suite=Path(subprocess.check_output(['python3',str(root/'scripts/workspace-layout.py'),'path','tests','browser-resident-inputs','--mkdir'],text=True).strip())
classes=suite/'classes';classes.mkdir(exist_ok=True)
jar=root/'Client_Base/Open_RSC_Client.jar'
subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),str(root/'Client_Base/src/orsc/mudclient.java'),str(root/'tests/myworld/BrowserResidentInputsFixture.java')],check=True)
for selected in ['true','false']:
 for writing in ['false','true']:
  subprocess.run(['java','-Xmx512m','-Djava.awt.headless=true','-Dspoiledmilk.browserResidentInputCache='+selected,'-Dspoiledmilk.browserResidentDescriptorWriting='+writing,'-cp',str(classes)+':'+str(jar),'orsc.BrowserResidentInputsFixture'],check=True)
