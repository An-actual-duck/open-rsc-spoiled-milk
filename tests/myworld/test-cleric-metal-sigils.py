#!/usr/bin/env python3
"""Offline native furnace/blank/carving/charging and atomic mixed-resource contract checks."""
from pathlib import Path
import os, subprocess, tempfile
ROOT=Path(__file__).resolve().parents[2]
JAVA=Path(os.environ.get('JAVA_HOME','/home/justin/jdk8u482-b08'))/'bin'
with tempfile.TemporaryDirectory(prefix='cleric-metal-sigils-')as directory:
    work=Path(directory);runtime=work/'runtime';classes=work/'classes';classes.mkdir()
    subprocess.run(['python3',str(ROOT/'tests/myworld/prepare-cleric-well-fixture.py'),str(runtime)],check=True,stdout=subprocess.DEVNULL)
    cp=':'.join(str(ROOT/n)for n in ['server/core.jar','server/plugins.jar','server/lib/*'])
    subprocess.run([str(JAVA/'javac'),'-J-Xmx128m','-proc:none','-cp',cp,'-sourcepath',str(ROOT/'server/test'),'-d',str(classes),str(ROOT/'server/test/com/openrsc/server/combat/ClericMetalSigilFixture.java')],check=True)
    subprocess.run([str(JAVA/'java'),'-Xmx512m','-XX:+UseSerialGC','-Djava.awt.headless=true','-cp',str(classes)+':'+cp,'com.openrsc.server.combat.ClericMetalSigilFixture'],cwd=runtime,check=True)
