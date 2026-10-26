#!/usr/bin/env python3
"""Requires the matching source-built server and client jars; opens no listeners."""
from pathlib import Path
import subprocess
import tempfile
ROOT=Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix='npc-capacity-production-') as directory:
    cp=str(ROOT/'server/core.jar')+':'+str(ROOT/'Client_Base/Open_RSC_Client.jar')
    fixture=ROOT/'server/test/com/openrsc/server/combat'
    sources=[fixture/(name+'.java') for name in ['CurrentCombatHarness','MutableGameClock','SeededGameRandom']]
    sources.append(ROOT/'tests/myworld/NpcCapacityProductionFixture.java')
    subprocess.run(['javac','-cp',cp,'-d',directory,*map(str,sources)],check=True)
    subprocess.run(['java','-Xms256m','-Xmx1024m','-cp',directory+':'+cp,'com.openrsc.server.combat.NpcCapacityProductionFixture'],cwd=ROOT/'server',check=True)
