#!/usr/bin/env python3
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[2]
out=Path(subprocess.check_output(['python3',str(root/'scripts/workspace-layout.py'),'path','tests','browser-world-ready','--mkdir'],text=True).strip())/'classes';out.mkdir(exist_ok=True)
jar=root/'Client_Base/Open_RSC_Client.jar'
subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(out),str(root/'PC_Client/src/orsc/BrowserWorldLoadState.java'),str(root/'tests/myworld/BrowserWorldLoadStateFixture.java')],check=True)
subprocess.run(['java','-Djava.awt.headless=true','-cp',str(out)+':'+str(jar),'orsc.BrowserWorldLoadStateFixture'],check=True)
subprocess.run(['node',str(root/'tests/myworld/test-browser-world-ready.mjs')],check=True)
