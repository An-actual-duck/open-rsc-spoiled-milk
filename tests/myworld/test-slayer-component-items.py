#!/usr/bin/env python3
"""Check server material identities and the built client's actual approved-art definitions."""
import json
import re
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
NAMES = [
    "Giant frog hide", "Banshee hide", "Naga hide", "Terror dog hide",
    "Bloodveld hide", "Dark beast hide", "Cockatrice Feathers", "Slimey Residue",
    "Sticky Saliva Gland", "Cockatrice Eye", "Frozen Tear", "Terror Fang",
    "Leach Tongue", "Lightning Horn", "Abyssal Vertibrae", "Abyssal Rib",
]

def main():
    entries = json.loads((ROOT / "server/conf/server/defs/ItemDefsCustom.json").read_text())["items"]
    definitions = {entry["id"]: entry for entry in entries}
    assert len(entries) == len(definitions), "Duplicate custom item IDs"
    constants = (ROOT / "server/src/com/openrsc/server/constants/custom/MyWorldItemId.java").read_text()
    fixture = (ROOT / "tests/myworld/SlayerComponentItemFixture.java").read_text()
    for item_id, name in [*enumerate(NAMES, 3333), (3399, "Ectoplasm")]:
        entry = definitions[item_id]
        assert entry["name"] == name, (item_id, "name")
        assert len(re.findall(rf"= {item_id};", constants)) == 1, (item_id, "constant")
        stackable = item_id in (3339, 3340, 3399)
        for field, expected in {
            "command": "", "isStackable": int(stackable), "isUntradable": 0,
            "isWearable": 0, "isNoteable": int(not stackable), "basePrice": 0,
            "requiredLevel": 0, "requiredSkillID": -1, "wearSlot": -1,
        }.items():
            assert entry[field] == expected, (item_id, field)
        assert json.dumps(entry["description"]) in fixture, (item_id, "client/server examine")
    assert not any(entry["name"] == "Serpant's Tail" for entry in entries), "Deferred Naga part"
    jar = ROOT / "Client_Base/Open_RSC_Client.jar"
    assert jar.exists(), "Run scripts/build-client.sh first"
    with tempfile.TemporaryDirectory(prefix="slayer-component-items-") as output:
        subprocess.run(["javac", "-cp", str(jar), "-d", output,
                        str(ROOT / "tests/myworld/SlayerComponentItemFixture.java")], check=True)
        approved = json.loads((ROOT / "dev/myworld/assets/authoring/slayer-items/material-icons-integration.json").read_text())
        effective = dict(definitions)
        for override in json.loads((ROOT / "server/conf/server/defs/ItemDefsMyWorld.json").read_text())["items"]:
            if override["id"] in effective:
                effective[override["id"]] = {**effective[override["id"]], **override}
        arguments = []
        for entry in approved:
            for item_id in entry["ids"]:
                if item_id in range(3333, 3349) or item_id == 3399:
                    sprite = "external-png:sticky-saliva-gland@16x18" if item_id == 3341 else entry["sprite"]
                    arguments.append(f"{item_id}|{sprite}|{effective[item_id]['basePrice']}")
        subprocess.run(["java", "-cp", f"{output}:{jar}", "SlayerComponentItemFixture", *arguments],
                       cwd=ROOT / "Client_Base", check=True)
    print("PASS: server/client Slayer component identities and inert scope")

if __name__ == "__main__":
    main()
