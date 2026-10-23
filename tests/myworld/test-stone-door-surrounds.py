#!/usr/bin/env python3
"""Guard additive native header content and preservation of existing resources."""
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
PATH = "server/conf/server/defs/DoorDef.xml"
baseline = ET.fromstring(subprocess.check_output(["git", "show", "a7dc80522b2342b7249054fea6a4926df03adca8:" + PATH], cwd=ROOT))
current = ET.parse(ROOT / PATH).getroot()
assert len(current) >= len(baseline) + 2
for old, new in zip(baseline, current):
    assert [(x.tag, x.text) for x in old] == [(x.tag, x.text) for x in new], "Existing wall definition changed"
headers = [row for row in current if row.findtext("name", "").startswith("Stone doorway header (")]
assert len(headers) == 2
for header in headers:
    source = current[int(header.findtext("doorSurroundFor"))]
    assert header.findtext("doorOpeningHeight") == "192"
    for key in ("modelVar1", "modelVar2", "modelVar3"):
        assert header.findtext(key) == source.findtext(key)
    assert header.findtext("doorType") == header.findtext("unknown") == "0"
world = (ROOT / "Client_Base/src/orsc/graphics/three/World.java").read_text()
assert world.count(".getDoorOpeningHeight()") == 2, "Both native wall geometry paths must honor declared openings"
client = (ROOT / "Client_Base/src/orsc/mudclient.java").read_text()
assert "int openingHeight = EntityHandler.getDoorDef(type).getDoorOpeningHeight();" in client
assert "int bottom = band == 0 ? openingHeight : lowerHeight;" in client
assert "model.insertVertex(x1, -elevation1 - bottom, z1)" in client
assert "model.insertVertex(x2, -elevation2 - bottom, y2)" in client
print("PASS additive stone door definitions and unchanged original wall definitions")
