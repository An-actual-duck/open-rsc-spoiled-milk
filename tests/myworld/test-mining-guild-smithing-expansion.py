#!/usr/bin/env python3
"""Validate the Mining Guild/Smithing expansion seed item and plan wiring."""

import json
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def load_items(path: Path) -> dict[int, dict]:
    return {int(entry["id"]): entry for entry in json.loads(path.read_text(encoding="utf-8"))["items"]}


def require_game_object_compaction_copies_id_before_model(packet_handler: str) -> None:
    lines = packet_handler.splitlines()
    model_copy = re.compile(
        r"mc\.setGameObjectInstanceModel\(([^,]+),\s*mc\.getGameObjectInstanceModel\(([^)]+)\)\);"
    )

    for line_number, line in enumerate(lines, start=1):
        match = model_copy.search(line)
        if not match:
            continue

        destination = match.group(1).strip()
        source = match.group(2).strip()
        expected_id_copy = (
            f"mc.setGameObjectInstanceID({destination}, mc.getGameObjectInstanceID({source}));"
        )
        previous_lines = [candidate.strip() for candidate in lines[max(0, line_number - 6):line_number - 1]]
        require(
            expected_id_copy in previous_lines,
            "Game-object compaction should copy object id before model so stale Dragon sulfur tint cannot leak "
            f"into other rock models near line {line_number}",
        )


def main() -> None:
    plan = ROOT / "docs/myworld/in-progress-work-plans/mining-guild-and-smithing-expansion-plan.md"
    item_id = (ROOT / "server/src/com/openrsc/server/constants/ItemId.java").read_text(encoding="utf-8")
    scenery_id = (ROOT / "server/src/com/openrsc/server/constants/SceneryId.java").read_text(encoding="utf-8")
    game_object_defs = (ROOT / "server/conf/server/defs/GameObjectDef.xml").read_text(encoding="utf-8")
    object_mining = (ROOT / "server/conf/server/defs/extras/ObjectMining.xml").read_text(encoding="utf-8")
    retro_object_mining = (ROOT / "server/conf/server/defs/extras/retro/ObjectMining.xml").read_text(encoding="utf-8")
    client_defs = (ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java").read_text(encoding="utf-8")
    client_mudclient = (ROOT / "Client_Base/src/orsc/mudclient.java").read_text(encoding="utf-8")
    packet_handler = (ROOT / "Client_Base/src/orsc/PacketHandler.java").read_text(encoding="utf-8")
    rs_model = (ROOT / "Client_Base/src/orsc/graphics/three/RSModel.java").read_text(encoding="utf-8")
    custom_items = load_items(ROOT / "server/conf/server/defs/ItemDefsCustom.json")
    object_ids_doc = (ROOT / "docs/myworld/info/object-ids.md").read_text(encoding="utf-8")
    custom_scenery_locs = json.loads(
        (ROOT / "server/conf/server/defs/locs/MyWorldSceneryLocs.json").read_text(encoding="utf-8")
    )["sceneries"]

    plan_text = plan.read_text(encoding="utf-8")
    require("Status: in progress" in plan_text, "Mining Guild expansion plan should be in progress")
    require("`Dragon sulfur`" in plan_text, "Plan should lock the Dragon sulfur ore name")
    require(
        "- [x] Add initial `Dragon sulfur` resource item using the ash item sprite" in plan_text,
        "Plan checklist should mark the seed Dragon sulfur item complete",
    )
    require(
        "- [x] Add rock scenery definitions and object IDs." in plan_text,
        "Plan checklist should mark Dragon sulfur scenery complete",
    )
    require(
        "- [x] Add Mining requirements, XP, respawn timing, and depletion rules." in plan_text,
        "Plan checklist should mark Dragon sulfur mining rules complete",
    )
    require(
        "- [ ] Place new ore rocks only inside the new gated area." in plan_text,
        "Plan checklist should leave manual sulfur rock placement open",
    )

    require("DRAGON_SULFUR(3255)" in item_id, "ItemId should reserve Dragon sulfur")
    require("public static final int maxCustom = 3311;" in item_id, "ItemId.maxCustom should include current custom items")
    require("ROCK_DRAGON_SULFUR(1328)" in scenery_id, "SceneryId should reserve the Dragon sulfur rock")

    dragon_sulfur = custom_items.get(3255)
    require(dragon_sulfur is not None, "ItemDefsCustom should define Dragon sulfur")
    require(dragon_sulfur["name"] == "Dragon sulfur", "Dragon sulfur should keep the expected item name")
    require(dragon_sulfur["description"] == "A volatile sulfurous ore used for high-tier alloys", "Dragon sulfur examine should describe the alloy role")
    require(dragon_sulfur["isStackable"] == 0, "Dragon sulfur should behave like ore, not stackable ash")
    require(dragon_sulfur["isNoteable"] == 1, "Dragon sulfur should be noteable like other resource items")
    require(dragon_sulfur["basePrice"] == 12000, "Dragon sulfur should have an initial high-tier resource base price")

    require(
        'setCustomItemDefinition(3255, new ItemDef("Dragon sulfur", "A volatile sulfurous ore used for high-tier alloys", "", 12000, 23, "items:23", false, false, 0, 0xF05A1A, false, false, true, 3255))'
        in client_defs,
        "Client should use the ash item sprite with the Dragon sulfur orange-red mask",
    )
    require(
        "<name>Dragon sulfur rock</name>" in game_object_defs
        and "<description>A volatile sulfur-stained rock</description>" in game_object_defs
        and "<objectModel>copperrock1</objectModel>" in game_object_defs,
        "Server object defs should include the Dragon sulfur rock using the orange copper rock model",
    )
    require(
        'objects.add(new GameObjectDef("Pumpkin", "A ripe event pumpkin ready for harvest", "Harvest", "Examine", 0, 1, 1, 0, "pumpkinwhite", ++i)); //1327'
        in client_defs,
        "Client object defs should include regular pumpkin before Dragon sulfur so object IDs stay aligned",
    )
    require(
        'objects.add(new GameObjectDef("Dragon sulfur rock", "A volatile sulfur-stained rock", "Mine", "Prospect", 1, 1, 1, 0, "copperrock1", ++i)); //1328'
        in client_defs,
        "Client object defs should include the Dragon sulfur rock at ID 1328",
    )
    require("DRAGON_SULFUR_ROCK_OBJECT_ID = 1328" in client_mudclient, "Client should target the Dragon sulfur rock visual override by object ID")
    require(
        "DRAGON_SULFUR_ROCK_COLOR_RESOURCE = GenUtil.colorToResource(255, 112, 16)" in client_mudclient,
        "Client should tint Dragon sulfur rocks bright orange",
    )
    require(
        "model.tintVisibleFaces(DRAGON_SULFUR_ROCK_COLOR_RESOURCE)" in client_mudclient
        and "int tintVisibleFaces(int materialResource)" in rs_model,
        "Client should tint Dragon sulfur rock model instances through the object model preparation path",
    )
    require(
        "RSModel preparedModel = prepareGameObjectInstanceModel(getGameObjectInstanceID(i), m);" in client_mudclient
        and "this.sceneInstanceStore.setGameObjectModel(i, preparedModel);" in client_mudclient
        and "private RSModel prepareGameObjectInstanceModel(int objectId, RSModel model)" in client_mudclient,
        "Client should prepare Dragon sulfur visuals only when assigning an object model",
    )
    require(
        "private void applyGameObjectInstanceVisualOverrides" not in client_mudclient
        and "private void applyGameObjectVisualOverrides" not in client_mudclient,
        "Client should not recolor whatever stale model is present while setting object ids",
    )
    require(
        "applyGameObjectVisualOverrides(objectId, model);" not in client_mudclient
        and "applyRenderer3DMaterialMetadata(\n\t\t\tRenderer3DModelKind.GAME_OBJECT,\n\t\t\tobjectId,\n\t\t\tmodel);" in client_mudclient
        and "applyRenderer3DMaterialMetadata(kind, objectId, model);" not in client_mudclient,
        "Object model assignment should attach renderer metadata without recoloring or reclassifying live models during export",
    )
    require_game_object_compaction_copies_id_before_model(packet_handler)
    sulfur_mining_entry = """<entry>
\t\t<int>1328</int><!-- Dragon sulfur -->
\t\t<ObjectMiningDef>
\t\t\t<requiredLvl>80</requiredLvl>
\t\t\t<oreId>3255</oreId>
\t\t\t<exp>650</exp>
\t\t\t<depletion>89</depletion>
\t\t\t<respawnTime>420</respawnTime>
\t\t</ObjectMiningDef>
\t</entry>"""
    require(sulfur_mining_entry in object_mining, "ObjectMining should make Dragon sulfur rock mineable")
    require(sulfur_mining_entry in retro_object_mining, "Retro ObjectMining should include Dragon sulfur rock")
    require(
        "| Dragon sulfur | 1328 | `ROCK_DRAGON_SULFUR` | `copperrock1` | 3255 `Dragon sulfur` | 80 | 650 | 420 |"
        in object_ids_doc,
        "Object ID docs should list the Dragon sulfur rock placement ID",
    )
    for runite_pos in ({"X": 246, "Y": 3416}, {"X": 263, "Y": 3393}):
        runite_loc = next(
            (
                loc for loc in custom_scenery_locs
                if loc["pos"] == runite_pos
            ),
            None,
        )
        require(
            runite_loc is not None and runite_loc["id"] == 210,
            f"Mining Guild rock at {runite_pos['X']},{runite_pos['Y']} should remain runite and must not be saved as Dragon sulfur",
        )

    custom_npcs = (ROOT / "server/conf/server/defs/NpcDefsCustom.json").read_text(encoding="utf-8")
    npc_ids = (ROOT / "server/src/com/openrsc/server/constants/NpcId.java").read_text(encoding="utf-8")
    require("ELDER_GREEN_DRAGON(844)" in npc_ids, "NpcId should reserve Elder Green Dragon")
    require('"id": 844' in custom_npcs and '"name": "Elder Green Dragon"' in custom_npcs, "Server should define Elder Green Dragon")
    require('"meleeOffense": 250' in custom_npcs and '"rangedOffense": 235' in custom_npcs and '"magicOffense": 270' in custom_npcs, "Server Elder Green Dragon should use explicit offense stats")
    require('"meleeDefense": 265' in custom_npcs and '"rangedDefense": 210' in custom_npcs and '"magicDefense": 265' in custom_npcs, "Server Elder Green Dragon should use explicit defense stats")
    require('"sprites1": 144' in custom_npcs and '"camera1": 904' in custom_npcs and '"camera2": 652' in custom_npcs, "Server Elder Green Dragon should reuse doubled green dragon visuals")
    require(
        'setCustomNpcDefinition(844, new NPCDef(' in client_defs
        and '"Elder Green Dragon", "A towering ancient green dragon", ""' in client_defs
        and '275, 250, 280, 265, true' in client_defs
        and 'new int[]{144, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}' in client_defs
        and '904, 652, 10, 7, 70, 844' in client_defs,
        "Client should define Elder Green Dragon as a doubled green dragon boss",
    )

    print("PASS: Mining Guild/Smithing expansion seed item validated")


if __name__ == "__main__":
    main()
