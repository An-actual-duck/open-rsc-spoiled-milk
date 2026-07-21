#!/usr/bin/env python3
import json
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_DEFS = ROOT / "server/conf/server/defs/GameObjectDef.xml"
CLIENT_DEFS = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
SKILL_GUIDE = ROOT / "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java"
SCENERY_IDS = ROOT / "server/src/com/openrsc/server/constants/SceneryId.java"
SCENERY_LOCS = ROOT / "server/conf/server/defs/locs/MyWorldSceneryLocs.json"
HANDLER = (
    ROOT
    / "server/plugins/com/openrsc/server/plugins/custom/myworld/skills/agility/"
    / "LumbridgeAlKharidSteppingStones.java"
)
WILDERNESS = (
    ROOT
    / "server/plugins/com/openrsc/server/plugins/authentic/skills/agility/"
    / "WildernessAgilityCourse.java"
)

EXPECTED = {
    1329: (103, 686, "Jump to"),
    1330: (104, 686, "WalkTo"),
    1331: (105, 686, "Jump to"),
}


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def main() -> None:
    definitions = ET.parse(SERVER_DEFS).getroot().findall("GameObjectDef")
    require(len(definitions) > max(EXPECTED), "Server object definitions do not include the new stones")
    for object_id, (_, _, command) in EXPECTED.items():
        definition = definitions[object_id]
        require(definition.findtext("name") == "Stepping Stone", f"Object {object_id} has the wrong name")
        require(definition.findtext("command1") == command, f"Object {object_id} has the wrong primary command")
        require(definition.findtext("objectModel") == "stonedisc", f"Object {object_id} must reuse stonedisc")

    wilderness_definition = definitions[707]
    require(wilderness_definition.findtext("objectModel") == "stonedisc", "Authentic Wilderness stone visual changed")
    require(wilderness_definition.findtext("command1") == "balance on", "Authentic Wilderness stone command changed")

    client = CLIENT_DEFS.read_text(encoding="utf-8")
    for object_id, (_, _, command) in EXPECTED.items():
        require(
            f'"Stepping Stone", "It looks like I could jump on this", "{command}", "Examine", 1, 1, 1, 0, "stonedisc", ++i)); //{object_id}'
            in client,
            f"Client definition {object_id} is missing or misaligned",
        )

    scenery_ids = SCENERY_IDS.read_text(encoding="utf-8")
    require("STEPPING_STONE_AL_KHARID_TO_LUMBRIDGE(1329)" in scenery_ids, "Missing Al Kharid endpoint constant")
    require("STEPPING_STONE_LUMBRIDGE_AL_KHARID_CENTRE(1330)" in scenery_ids, "Missing centre-stone constant")
    require("STEPPING_STONE_LUMBRIDGE_TO_AL_KHARID(1331)" in scenery_ids, "Missing Lumbridge endpoint constant")

    locations = json.loads(SCENERY_LOCS.read_text(encoding="utf-8"))["sceneries"]
    crossing = {
        (int(entry["id"]), int(entry["pos"]["X"]), int(entry["pos"]["Y"]), int(entry["direction"]))
        for entry in locations
        if int(entry["pos"]["Y"]) == 686 and 103 <= int(entry["pos"]["X"]) <= 105
    }
    require(
        crossing == {(object_id, x, y, 0) for object_id, (x, y, _) in EXPECTED.items()},
        f"Lumbridge/Al Kharid crossing placements are wrong: {sorted(crossing)}",
    )
    require(
        not any(
            int(entry["id"]) == 707
            and int(entry["pos"]["Y"]) == 686
            and 103 <= int(entry["pos"]["X"]) <= 105
            for entry in locations
        ),
        "The crossing still contains authentic Wilderness object 707",
    )

    handler = HANDLER.read_text(encoding="utf-8")
    for snippet in (
        "AL_KHARID_BANK = Point.location(102, STONE_Y)",
        "WEST_STONE = Point.location(103, STONE_Y)",
        "CENTRE_STONE = Point.location(104, STONE_Y)",
        "EAST_STONE = Point.location(105, STONE_Y)",
        "LUMBRIDGE_BANK = Point.location(106, STONE_Y)",
        "obj.getX() == WEST_STONE.getX() && obj.getY() == WEST_STONE.getY()",
        "obj.getX() == EAST_STONE.getX() && obj.getY() == EAST_STONE.getY()",
        "REQUIRED_AGILITY_LEVEL = 25",
        "LEVEL_STOP_FAIL = REQUIRED_AGILITY_LEVEL + 30",
        "getCurrentLevel(player, Skill.AGILITY.id()) < REQUIRED_AGILITY_LEVEL",
        'player.message("You need an agility level of 25 to use this shortcut.")',
        "Formulae.calcProductionSuccessfulLegacy(REQUIRED_AGILITY_LEVEL,",
        "getCurrentLevel(player, Skill.AGILITY.id()), false, LEVEL_STOP_FAIL)",
        "hasEquipped(ItemId.AGILITY_CAPE.id())",
        "teleport(player, departureBank.getX(), departureBank.getY())",
        "teleport(player, destinationBank.getX(), destinationBank.getY())",
    ):
        require(snippet in handler, f"Crossing handler is missing: {snippet}")

    for forbidden in (
        "STONE_WILDERNESS_COURSE",
        "WildernessAgilityCourse",
        "AgilityUtils.completedObstacle",
        "incExp(",
        "player.damage(",
    ):
        require(forbidden not in handler, f"Crossing handler must not contain {forbidden}")

    guide = SKILL_GUIDE.read_text(encoding="utf-8")
    require(
        'new SkillMenuItem(410, "25", "Lum river stepping stone")' in guide,
        "Agility guide must list the Lum River stepping stones at level 25",
    )
    require(
        'new SkillMenuItem(410, "25", "Glough\'s watch tower")' in guide,
        "Agility guide lost the other level-25 Agility shortcut",
    )

    wilderness = WILDERNESS.read_text(encoding="utf-8")
    require("private static final int STONE = 707;" in wilderness, "Wilderness stone ID changed")
    for object_id in EXPECTED:
        require(str(object_id) not in wilderness, f"Wilderness course must not claim new object {object_id}")

    print("PASS: Lumbridge/Al Kharid stones use isolated definitions and local traversal")


if __name__ == "__main__":
    main()
