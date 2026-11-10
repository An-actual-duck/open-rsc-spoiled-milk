#!/usr/bin/env python3
"""Frontend control contract and actual Java receipts, without CheerpJ/GPU."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[2]
out=Path(subprocess.check_output(['python3',str(root/'scripts/workspace-layout.py'),'path','tests','browser-preparation-controls','--mkdir'],text=True).strip())
jar=root/'Client_Base/Open_RSC_Client.jar'
sources=[root/'Client_Base/src/orsc/graphics/two/GraphicsController.java',root/'tests/myworld/BrowserPreparationControlFixture.java',*sorted((root/'tools/browser-gameplay/src/orsc/browser').glob('*.java'))]
subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(out),*map(str,sources)],check=True)
for prop,expected in [(None,'true'),('true','true'),('false','false')]:
 command=['java','-Djava.awt.headless=true']
 if prop is not None:command+=['-Dspoiledmilk.browserFramePreparation='+prop]
 subprocess.run([*command,'-cp',str(out)+':'+str(jar),'orsc.graphics.two.BrowserPreparationControlFixture',expected],check=True)
subprocess.run(['node',str(root/'tests/myworld/test-browser-preparation-controls.mjs')],check=True)
