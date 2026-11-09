#!/usr/bin/env python3
"""CPU production-data boundary checks; deliberately not a GPU/performance claim."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[2]
out=Path(subprocess.check_output(['python3',str(root/'scripts/workspace-layout.py'),'path','tests','browser-lighting','--mkdir'],text=True).strip())/'classes'
out.mkdir(exist_ok=True)
jar=root/'Client_Base/Open_RSC_Client.jar'
sources=[root/'tests/myworld'/n for n in ['TerrainDefinitionFieldFixture.java','GeometryShadowRoofMaskFixture.java','BrowserLightingFixture.java']]
subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(out),*map(str,sources)],check=True)
subprocess.run(['java','-Djava.awt.headless=true','-cp',str(out)+':'+str(jar),'orsc.BrowserLightingFixture'],cwd=root,check=True)
