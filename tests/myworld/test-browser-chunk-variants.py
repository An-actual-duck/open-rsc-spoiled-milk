#!/usr/bin/env python3
"""Native texel-center references and bounded browser cache dependency tests."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[2]
suite=Path(subprocess.check_output(['python3',str(root/'scripts/workspace-layout.py'),'path','tests','browser-webgl','--mkdir'],text=True).strip())
classes=suite/'variant-classes';classes.mkdir(exist_ok=True)
jar=root/'Client_Base/Open_RSC_Client.jar'
subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),str(root/'tests/myworld/BrowserChunkVariantFixture.java')],check=True)
subprocess.run(['java','-Djava.awt.headless=true','-cp',str(classes)+':'+str(jar),'orsc.BrowserChunkVariantFixture',str(suite/'native-uv-reference.tsv')],check=True)
subprocess.run(['node',str(root/'tests/myworld/test-browser-gpu-cache.mjs'),str(suite/'native-uv-reference.tsv')],check=True)
