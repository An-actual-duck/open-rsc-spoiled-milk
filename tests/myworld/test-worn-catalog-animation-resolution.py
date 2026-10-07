#!/usr/bin/env python3
"""Resolve worn IDs against executed client definitions, not source-order guesses.

Build the client first, or supply --client-jar with a compatible built client.
The current EntityHandler source is compiled ahead of that jar, so stale registry
bytecode cannot hide an animation-table regression. No server is launched.
"""
import argparse
import base64
import json
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def entries(path):
    data = json.loads(path.read_text())
    return data.get("items", data.get("item", []))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--client-jar", type=Path,
                        default=ROOT / "Client_Base/Open_RSC_Client.jar")
    args = parser.parse_args()
    jar = args.client_jar.resolve()
    assert jar.is_file(), "Build the client or supply --client-jar"
    with tempfile.TemporaryDirectory(prefix="worn-catalog-probe-") as directory:
        subprocess.run(["javac", "-cp", str(jar), "-d", directory,
                        str(ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"),
                        str(ROOT / "tools/item-visual-provider/FinalNpcDefinitionsProbe.java")], check=True)
        output = subprocess.check_output(["java", "-cp", directory + ":" + str(jar),
                                          "FinalNpcDefinitionsProbe"], text=True)
    animations = {}
    for line in output.splitlines():
        fields = line.split("\t")
        if fields[0] == "ANIMATION":
            animations[int(fields[1])] = (base64.b64decode(fields[2]).decode(), int(fields[4]))

    items = {}
    underlying = {}
    defs = ROOT / "server/conf/server/defs"
    for filename in ("ItemDefs.json", "ItemDefsCustom.json", "ItemDefsMyWorld.json"):
        for entry in entries(defs / filename):
            items.setdefault(entry["id"], {}).update(entry)
            if filename != "ItemDefsMyWorld.json":
                underlying[entry["id"]] = entry

    expected = {}

    def require(item_id, animation_index, family):
        assert animation_index in animations, f"Missing animation {animation_index}"
        assert animations[animation_index][0] == family, (item_id, animation_index, animations[animation_index])
        expected[item_id] = animation_index + 1

    # Every tier in all fourteen elemental wool glove/boot families.
    require(2794, 977, "woolgloves")
    require(2795, 978, "woolboots")
    for item_id in range(2796, 2936):
        require(item_id, 949 + (item_id - 2796) // 10, "woolgloves")
    for item_id in range(2936, 3076):
        require(item_id, 963 + (item_id - 2936) // 10, "woolboots")

    for item_id, index in {698: 1000, 699: 1006, 700: 1007, 701: 1008, 1006: 1009,
                          1960: 996, 1966: 997, 1983: 998, 1985: 999, 1989: 1001,
                          1972: 1002, 1991: 1003, 1978: 1004, 1993: 1005}.items():
        require(item_id, index, "gauntlets")
    for offset in range(6):
        require(3131 + offset, 989 + offset, "gauntlets" if offset % 2 == 0 else "greaves")
    families = ("wizardshat", "wizardsrobe", "skirt", "woolgloves", "woolboots")
    for offset in range(15):
        require(3137 + offset, 1010 + offset, families[offset % 5])
    for item_id in (3174, 3175):
        require(item_id, 995, "guthsymbol")
    require(3191, 1034, "hood")
    require(3235, 1035, "firesword")
    require(3236, 1036, "icesword")
    for item_id, index in {2161: 1031, 2162: 1025, 3123: 1032, 3124: 1026,
                          2224: 1027, 2225: 1028, 2226: 1029, 2227: 1030}.items():
        require(item_id, index, "kiteshield" if item_id in (2162, 3124) else "squareshield")

    for item_id, appearance in expected.items():
        item = items[item_id]
        assert item["appearanceID"] == appearance, (
            f"{item['name']} ({item_id}): appearance {item['appearanceID']} must be {appearance}; "
            f"currently resolves to {animations.get(item['appearanceID'] - 1)}")
        # Check the underlying definition too: MyWorld must not conceal bad base data.
        assert underlying[item_id]["appearanceID"] == appearance, (item_id, underlying[item_id]["appearanceID"])

    # Same-family shifts also change metal, element or blessing colours. Verify
    # those against the intended palette, rather than accepting any glove sprite.
    cloth_colours = (0x87CEEB, 0x8B4A3B, 0x1F4E8C, 0x7A5230, 0xC62828,
                     0xC2A57A, 0xE0C341, 0xF57C00, 0x2E7D32, 0x1B8A8F,
                     0x222222, 0x7F1D1D, 0x8FA8C9, 0xF2D75A)
    for start in (2796, 2936):
        for offset in range(140):
            item = items[start + offset]
            assert animations[item["appearanceID"] - 1][1] == cloth_colours[offset // 10], item["name"]
    metal_colours = (0xB7C9D9, 0xC86A2B, 16737817, 15654365, 15658734,
                    10072780, 0x8EA6BB, 11717785, 0x5A3F7D, 65535)
    for item_id in (1960, 1966, 1983, 1985, 698, 1989, 1972, 1991, 1978, 1993):
        index = expected[item_id] - 1
        assert animations[index][1] == metal_colours[index - 996], item_id
    for offset in range(6):
        assert animations[989 + offset][1] == (0x222222, 0xF0F0F0, 0x9EA59F)[offset // 2]
    for offset in range(15):
        assert animations[1010 + offset][1] == (0x222222, 0xF0F0F0, 0x9EA59F)[offset // 5]

    # Whole-catalog slot compatibility: a head/hand/foot layer cannot reference
    # a weapon even if the animation ID is numerically valid. Authentic costume
    # layers and deliberately visible transformation rings remain supported.
    allowed = {
        0: {"fullhelm", "dragonfullhelm"},
        1: {"platemailtop", "fplatemailtop", "fleatherbody", "dragonbody", "fdragontop"},
        2: {"platemaillegs", "legs1", "santalegs", "leatherchaps", "dragonlegs", "armorskirt"},
        3: {"kiteshield", "squareshield", "dragonkiteshield"},
        4: {"mace", "sword", "battleaxe", "crossbow", "staff", "elementalstaff", "shears",
            "longbow", "spear", "ibanstaff", "saradominstaff", "guthixstaff", "zamorakstaff",
            "scythe", "2hander", "ctfflag", "dagger", "poisoneddagger", "yoyo", "boomstick",
            "hatchet", "firesword", "icesword", "demonpitchfork", "pickaxe", "fishingpole"},
        5: {"mediumhelm", "wizardshat", "chefshat", "partyhat", "eyepatch", "gasmask",
            "halloweenmask", "santahat", "bunnyears", "fullhelm", "wolfmask", "unicornmask",
            "santahat2", "greensantahat", "antlers", "plaguemask", "rubberchicken",
            "mvalkyriehelm", "valkyriehelm", "ogreears", "crown", "halloweenmask_pink", "pinksantahat", "hood"},
        6: {"chainmail", "leatherarmour", "wizardsrobe", "santabody", "dragonscalemail",
            "leathervest", "fchainmail", "fdragonscalemail", "christmassweater", "fchristmassweater"},
        7: {"skirt", "leatherskirt", "chainmaillegs"},
        8: {"leathergloves", "gauntlets", "satansgloveswht", "santamittens", "hidegloves", "woolgloves"},
        9: {"boots", "hideboots", "greaves", "woolboots"},
        10: {"necklace", "apron", "xmasapron", "amulet", "guthsymbol"},
        13: {"bunnymorph", "eggmorph", "skeletonmorph"},
    }

    checked = 0
    for item in items.values():
        appearance = item.get("appearanceID", 0)
        if not item.get("isWearable") or appearance == 0:
            continue  # Intentionally invisible equipment, e.g. ordinary rings/ammo.
        assert appearance - 1 in animations, f"{item['name']}: out-of-range appearance {appearance}"
        checked += 1
        family = animations[appearance - 1][0]
        slot = item["wearSlot"]
        if slot == 11:
            assert family.endswith("cape") or family == "wings", (item["id"], family)
        else:
            assert family in allowed.get(slot, set()), (item["id"], item["name"], slot, family)
        name = item["name"].lower()
        # Named gloves and boots must never render held weapons or other body parts.
        if "gauntlets" in name:
            assert family == "gauntlets", (item["id"], name, family)
        elif "gloves" in name and "santa" not in name:
            assert family in ("leathergloves", "hidegloves", "woolgloves"), (item["id"], name, family)
        elif "boots" in name:
            assert family in ("boots", "hideboots", "woolboots"), (item["id"], name, family)
        elif "greaves" in name:
            assert family == "greaves", (item["id"], name, family)
    assert len(expected) == 330
    print(f"PASS: {checked} visible wearable definitions in range; all 330 corrected IDs resolve to intended animations")


if __name__ == "__main__":
    main()
