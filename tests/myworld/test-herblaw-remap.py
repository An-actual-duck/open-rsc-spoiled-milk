#!/usr/bin/env python3
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import NoReturn


ROOT = Path(__file__).resolve().parents[2]
HERB_SECOND = ROOT / "server" / "conf" / "server" / "defs" / "extras" / "ItemHerbSecond.xml"
HERB_IDENT = ROOT / "server" / "conf" / "server" / "defs" / "extras" / "ItemUnIdentHerbDef.xml"
ITEM_DEFS = ROOT / "server" / "conf" / "server" / "defs" / "ItemDefs.json"
ITEM_DEFS_CUSTOM = ROOT / "server" / "conf" / "server" / "defs" / "ItemDefsCustom.json"
HERBLAW = ROOT / "server" / "plugins" / "com" / "openrsc" / "server" / "plugins" / "authentic" / "skills" / "herblaw" / "Herblaw.java"
GUIDE = ROOT / "Client_Base" / "src" / "com" / "openrsc" / "interfaces" / "misc" / "SkillGuideInterface.java"


def fail(message: str) -> NoReturn:
    print(f"FAIL: {message}")
    sys.exit(1)


def load_item_names(path: Path) -> dict[int, tuple[str, str, str]]:
    text = path.read_text(encoding="utf-8")
    data = json.loads(text)
    if isinstance(data, dict):
        if "items" in data:
            items = data["items"]
        elif "item" in data:
            items = data["item"]
        else:
            fail(f"Unexpected item-def container shape in {path}")
    else:
        items = data
    return {item["id"]: (item["name"], item["description"], item["command"]) for item in items}


def main() -> None:
    xml_root = ET.parse(HERB_SECOND).getroot()
    recipes = {
        (int(node.findtext("unfinishedID")), int(node.findtext("secondID"))): (
            int(node.findtext("potionID")),
            int(node.findtext("requiredLvl")),
        )
        for node in xml_root.findall("ItemHerbSecond")
    }

    expected_recipes = {
        (454, 3256): (474, 3),
        (456, 3257): (477, 12),
        (458, 3258): (480, 30),
        (460, 220): (483, 50),
        (462, 3259): (486, 66),
        (935, 3260): (3198, 78),
        (454, 1410): (489, 3),
        (456, 3240): (492, 12),
        (458, 3241): (495, 30),
        (460, 3242): (498, 50),
        (462, 3243): (566, 66),
        (935, 3244): (3201, 78),
        (454, 270): (569, 3),
        (456, 3245): (963, 12),
        (458, 3238): (1411, 30),
        (460, 3246): (1414, 50),
        (462, 3247): (1468, 66),
        (935, 3248): (3204, 78),
        (457, 473): (1471, 22),
        (455, 3400): (3402, 8),
        (455, 219): (1474, 22),
        (455, 3401): (3405, 38),
        (457, 472): (572, 23),
        (459, 469): (1477, 45),
        (459, 471): (3192, 45),
    }
    for pair, expected in expected_recipes.items():
        if recipes.get(pair) != expected:
            fail(f"Expected herb recipe {pair} -> potion {expected[0]} at Herblaw {expected[1]}")

    ident_levels = {}
    for entry in ET.parse(HERB_IDENT).getroot().findall("entry"):
        herb_id = int(entry.findtext("int"))
        def_node = entry.find("ItemUnIdentHerbDef")
        if def_node is not None:
            ident_levels[herb_id] = int(def_node.findtext("requiredLvl"))

    guide_text = GUIDE.read_text(encoding="utf-8")
    expected_ident_levels = (
        (165, 444, 3),
        (435, 445, 8),
        (436, 446, 15),
        (437, 447, 22),
        (438, 448, 29),
        (439, 449, 36),
        (440, 450, 43),
        (441, 451, 50),
        (442, 452, 57),
        (443, 453, 64),
        (933, 934, 70),
    )
    for herb_id, clean_id, level in expected_ident_levels:
        if ident_levels.get(herb_id) != level:
            fail(f"Herb {herb_id} should identify at Herblaw {level}")
        guide_snippet = f'new SkillMenuItem({clean_id}, "{level}", EntityHandler.getItemDef({clean_id}).name)'
        if guide_snippet not in guide_text:
            fail(f"Herblaw guide should show herb {clean_id} at level {level}")

    for snippet in (
        'new SkillMenuItem(tier1Id, "3", potionName + " v1',
        'new SkillMenuItem(tier2Id, "12", potionName + " v2',
        'new SkillMenuItem(tier3Id, "30", potionName + " v3',
        'new SkillMenuItem(tier4Id, "50", potionName + " v4',
        'new SkillMenuItem(tier5Id, "66", potionName + " v5',
        'new SkillMenuItem(tier6Id, "78", potionName + " v6',
        'new SkillMenuItem(3402, "8", "Weak Antidote',
        'new SkillMenuItem(1474, "22", "Antidote',
        'new SkillMenuItem(3405, "38", "Strong Antidote',
        'new SkillMenuItem(1176, "10", "Explosive compound',
        'new SkillMenuItem(1053, "18", "Ogre potion',
        'new SkillMenuItem(1471, "22", "Stat restore',
        'new SkillMenuItem(572, "23", "Weapon poison',
        'new SkillMenuItem(588, "25", "Blamish oil',
        'new SkillMenuItem(1253, "45", "Gujuo potion',
        'new SkillMenuItem(1477, "45", "Skiller\'s Brew',
        'new SkillMenuItem(3192, "45", "Warrior\'s Brew',
        'new SkillMenuItem(221, "72", "Strong Skiller\'s Brew',
        'new SkillMenuItem(3195, "72", "Strong Warrior\'s Brew',
    ):
        if snippet not in guide_text:
            fail(f"Herblaw guide is missing level line: {snippet}")

    item_defs = load_item_names(ITEM_DEFS)
    custom_defs = load_item_names(ITEM_DEFS_CUSTOM)

    expected_names = {
        221: "Strong Skiller's Brew",
        474: "Potion of Brawn v1",
        477: "Potion of Brawn v2",
        480: "Potion of Brawn v3",
        483: "Potion of Brawn v4",
        486: "Potion of Brawn v5",
        3198: "Potion of Brawn v6",
        489: "Potion of Deftness v1",
        492: "Potion of Deftness v2",
        495: "Potion of Deftness v3",
        498: "Potion of Deftness v4",
        566: "Potion of Deftness v5",
        3201: "Potion of Deftness v6",
        569: "Potion of Insight v1",
        963: "Potion of Insight v2",
        1411: "Potion of Insight v3",
        1414: "Potion of Insight v4",
        1468: "Potion of Insight v5",
        3204: "Potion of Insight v6",
        1471: "Stat restore",
        1474: "Antidote",
        1477: "Skiller's Brew",
        3192: "Warrior's Brew",
        3195: "Strong Warrior's Brew",
        1410: "Low quality fish oil",
        3240: "Fair quality fish oil",
        3241: "Good quality fish oil",
        3242: "Fine quality fish oil",
        3243: "High quality fish oil",
        3244: "Superior quality fish oil",
        3238: "Zombie eye",
        3245: "Spider eye",
        3246: "Bat eye",
        3247: "Baby dragon's eye",
        3248: "Demon eye",
        3256: "Fern leaf",
        3257: "Mushroom",
        3258: "Fungus",
        3259: "Red flower",
        3260: "Blue flower",
    }
    for item_id, expected_name in expected_names.items():
        defs = custom_defs if item_id >= 1400 else item_defs
        actual = defs.get(item_id)
        if actual is None or actual[0] != expected_name:
            fail(f"Expected item {item_id} to be named '{expected_name}'")

    herblaw_text = HERBLAW.read_text(encoding="utf-8")
    for snippet in (
        "secondaryId == ItemId.SNAPE_GRASS.id()",
        "secondaryId == ItemId.WHITE_BERRIES.id()",
        "return isFishOil(secondaryId) ? 10 : 1;",
        "ItemId.SUPERIOR_QUALITY_FISH_OIL.id()",
        "herblaw level of 18 or over",
        "player.getSkills().getLevel(Skill.HERBLAW.id()) < 25",
        "player.getSkills().getLevel(Skill.HERBLAW.id()) < 45",
        "requiredCount = 5",
        "reqLevel = 72",
        "resultId = ItemId.FULL_STRENGTH_POTION.id()",
        "resultId = ItemId.FULL_STRONG_WARRIORS_BREW.id()",
    ):
        if snippet not in herblaw_text:
            fail(f"Herblaw strong brew recipe missing: {snippet}")
    if "herblaw level of 14 or over" in herblaw_text:
        fail("Ogre potion must use the guide's level 18 requirement")

    print("PASS: herblaw recipe and item remap validated")


if __name__ == "__main__":
    main()
