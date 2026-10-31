#!/usr/bin/env python3
"""Server mixed-status priority/count/overflow/state invariants, retained from the original HUD gate."""
from pathlib import Path
import subprocess,tempfile
ROOT=Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix='cleric-status-inventory-') as tmp:
 sources=[ROOT/'server/src/com/openrsc/server/content/status'/name for name in ['ActiveStatusEntry.java','ActiveStatusInventory.java']]
 subprocess.run(['javac','-d',tmp,*map(str,sources),str(ROOT/'tests/myworld/ActiveStatusInventoryFixture.java')],check=True)
 subprocess.run(['java','-Xmx128m','-cp',tmp,'test.ActiveStatusInventoryFixture'],check=True)
print('PASS server mixed-status god priority,31/32/33/64/65 bounds,overflow,timer-independent order and retained gameplay state')
