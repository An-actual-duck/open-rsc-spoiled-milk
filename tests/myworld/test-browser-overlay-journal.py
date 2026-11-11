#!/usr/bin/env python3
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[2]
out=root/'output/tests/browser-overlay-journal/core-1';out.mkdir(parents=True,exist_ok=True)
subprocess.run(['javac','-d',str(out),*[str(root/'Client_Base/src/orsc/graphics/two'/n)for n in ['WorldOverlayCoverage.java','WorldOverlayDeltaTransport.java','WorldOverlayTileTracker.java','OverlayJournalObserver.java']],str(root/'tests/myworld/OverlayJournalFixture.java')],check=True)
subprocess.run(['java','-cp',str(out),'orsc.graphics.two.OverlayJournalFixture'],check=True)
