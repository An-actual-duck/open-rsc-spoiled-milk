#!/usr/bin/env python3
"""Real pooled-frame backend ownership and shared camera/billboard projection contracts."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[2]
suite=Path(subprocess.check_output(['python3',str(root/'scripts/workspace-layout.py'),'path','tests','browser-webgl','--mkdir'],text=True).strip())
out=suite/'sink-classes';out.mkdir(exist_ok=True)
jar=root/'Client_Base/Open_RSC_Client.jar'
subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(out),str(root/'tests/myworld/BrowserFrameSinkFixture.java')],check=True)
subprocess.run(['java','-Djava.awt.headless=true','-cp',str(out)+':'+str(jar),'orsc.BrowserFrameSinkFixture'],check=True)
