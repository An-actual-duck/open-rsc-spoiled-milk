#!/usr/bin/env python3
"""Check effective server/client material identities, approved icons and note forms."""
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
    for entry in json.loads((ROOT / "server/conf/server/defs/ItemDefsMyWorld.json").read_text())["items"]:
        if entry["id"] in definitions:
            definitions[entry["id"]].update(entry)
    prices = {entry["id"]: entry["basePrice"] for entry in json.loads(
        (ROOT / "tools/generators/item-overrides/51-slayer-economy.json").read_text())["items"]}
    icons = json.loads((ROOT / "dev/myworld/art-candidates/slayer-items/material-icons-integration.json").read_text())
    constants = (ROOT / "server/src/com/openrsc/server/constants/custom/MyWorldItemId.java").read_text()
    fixture = (ROOT / "tests/myworld/SlayerComponentItemFixture.java").read_text()
    for item_id, name in [*enumerate(NAMES, 3333), (3399, "Ectoplasm")]:
        entry = definitions[item_id]
        assert entry["name"] == name, (item_id, "name")
        assert len(re.findall(rf"= {item_id};", constants)) == 1, (item_id, "constant")
        stackable = item_id in (3339, 3340, 3399)
        for field, expected in {
            "command": "", "isStackable": int(stackable), "isUntradable": 0,
            "isWearable": 0, "isNoteable": int(not stackable), "basePrice": prices.get(item_id, 0),
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
        arguments = [f"{item_id}|{icon['sprite']}|{definitions[item_id]['basePrice']}"
                     for icon in icons for item_id in icon["ids"] if item_id in range(3333, 3349) or item_id == 3399]
        subprocess.run(["java", "-cp", f"{output}:{jar}", "SlayerComponentItemFixture", *arguments],
                       cwd=ROOT / "Client_Base", check=True)
    print("PASS: server/client Slayer component identities and inert scope")

if __name__ == "__main__":
    main()
