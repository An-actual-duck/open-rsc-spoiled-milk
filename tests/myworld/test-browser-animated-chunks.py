#!/usr/bin/env python3
"""Actual browser bridge chunk-cache policy through a recording native boundary."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[2]
suite=Path(subprocess.check_output(['python3',str(root/'scripts/workspace-layout.py'),'path','tests','browser-animated-chunks','--mkdir'],text=True).strip())
classes=suite/'classes';classes.mkdir(exist_ok=True)
jar=root/'Client_Base/Open_RSC_Client.jar'
subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),str(root/'PC_Client/src/orsc/BrowserFrameSinkRegistry.java'),str(root/'PC_Client/src/orsc/BrowserWorldLoadState.java'),str(root/'PC_Client/src/orsc/BrowserWebGLBridge.java'),str(root/'tests/myworld/BrowserAnimatedChunkFixture.java')],check=True)
for policy in ['lru','active']:
 for reuse in [None,'true','false']:
  subprocess.run(['java','-Xmx512m','-Djava.awt.headless=true','-Dspoiledmilk.browserCachePolicy='+policy]+([] if reuse is None else ['-Dspoiledmilk.browserAnimatedBufferReuse='+reuse])+['-cp',str(classes)+':'+str(jar),'orsc.BrowserAnimatedChunkFixture'],check=True)
