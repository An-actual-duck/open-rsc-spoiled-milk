#!/usr/bin/env python3
"""Actual CPU glow masks: cache identity/bytes, invalidation and bounded ownership."""
from pathlib import Path
import subprocess
root = Path(__file__).resolve().parents[2]
out = Path(subprocess.check_output(['python3',str(root/'scripts/workspace-layout.py'),'path','tests','glow-mask-content-cache','--mkdir'],text=True).strip())/'classes'
out.mkdir(exist_ok=True)
jar = root/'Client_Base/Open_RSC_Client.jar'
subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(out),str(root/'tests/myworld/RemasterGlowMaskContentCacheFixture.java')],check=True)
subprocess.run(['java','-Djava.awt.headless=true','-Dspoiledmilk.remasterGlowMaskCacheEntries=2','-cp',str(out)+':'+str(jar),'orsc.RemasterGlowMaskContentCacheFixture'],check=True)
