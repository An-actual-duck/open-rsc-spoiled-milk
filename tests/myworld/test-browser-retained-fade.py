#!/usr/bin/env python3
"""Actual Java fade oracle + CPU interpretation of the exported GLSL expression."""
from pathlib import Path
import subprocess
ROOT=Path(__file__).resolve().parents[2]
out=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-retained-frame','--mkdir'],text=True).strip())
classes=out/'classes';classes.mkdir(exist_ok=True)
jar=ROOT/'Client_Base/Open_RSC_Client.jar'
subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),str(ROOT/'Client_Base/src/orsc/graphics/two/GraphicsController.java'),str(ROOT/'tests/myworld/BrowserRetainedFadeFixture.java')],check=True)
corpus=out/'native-fade.bin'
subprocess.run(['java','-Djava.awt.headless=true','-cp',str(classes)+':'+str(jar),'orsc.BrowserRetainedFadeFixture',str(corpus)],cwd=ROOT,check=True)
subprocess.run(['node',str(ROOT/'tests/myworld/test-browser-retained-fade.mjs'),str(corpus)],check=True)
