#!/usr/bin/env python3
"""Additive definition guard plus offline actual maintained Java geometry checks."""
from pathlib import Path
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
PATH = "server/conf/server/defs/DoorDef.xml"
BASE = "eb89c714ec1d8570b13783cd5b09ea1f5ddfa47a"
before = list(ET.fromstring(subprocess.check_output(["git", "show", BASE+":"+PATH], cwd=ROOT)))
after = list(ET.parse(ROOT/PATH).getroot())
assert len(before)==216 and len(after)==220
for a,b in zip(before,after):
    assert [(x.tag,x.text) for x in a]==[(x.tag,x.text) for x in b], "Existing boundary definition changed"
for offset,source in enumerate((3,6,13,34)):
    d = after[216+offset]
    assert [d.findtext(k) for k in ("modelVar1","wallLowerHeight","wallUpperFront","wallUpperBack","doorType","unknown")]==["275","192","2","2","1","0"]
    for key in ("modelVar2","modelVar3"): assert d.findtext(key)==before[source].findtext(key)
build = ROOT/"Client_Base/build"
assert (build/"orsc/graphics/three/World.class").is_file(), "Build the maintained client first: ./scripts/build-client.sh"
with tempfile.TemporaryDirectory(prefix="tall-window-tests-") as directory:
    cp = str(build)+":"+str(ROOT/"server/core.jar")+":"+str(ROOT/"server/lib/*")
    subprocess.run(["javac","-cp",cp,"-d",directory,str(ROOT/"server/src/com/openrsc/server/external/DoorDef.java"),str(ROOT/"server/test/com/openrsc/server/io/TallStoneWindowTest.java"),str(ROOT/"server/test/com/openrsc/server/io/StoneDoorSurroundTest.java")],check=True,cwd=ROOT)
    for test in ("TallStoneWindowTest","StoneDoorSurroundTest"):
        subprocess.run(["java","-Xmx384m","-Djava.awt.headless=true","-cp",directory+":"+cp,"com.openrsc.server.io."+test,str(ROOT/PATH)],check=True,cwd=ROOT)
print("PASS four additive tall windows; all216 prior definitions and legacy/header behavior preserved")
