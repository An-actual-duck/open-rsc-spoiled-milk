#!/usr/bin/env python3
"""Guard the Slayer naming pass without allowing task behavior to drift."""

import hashlib
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SLAYER_DATA = ROOT / "server/conf/server/defs/extras/MonsterSlayer.json"
NPC_DEFINITIONS = ROOT / "server/conf/server/defs"
STATE = ROOT / "server/src/com/openrsc/server/content/minigame/monsterslayer/MonsterSlayerState.java"
RENAMED_NPCS = {
    4: ("Tough Goblin", 13),
    23: ("Young Giant Spider", 8),
    47: ("Large Rat", 13),
    153: ("Tough Goblin", 13),
    154: ("Tough Goblin", 13),
    177: ("Large Rat", 13),
}

# Excludes display labels deliberately: this pass may clarify words, never IDs
# or progression behavior.
ELIGIBILITY_FINGERPRINT = "bc241fb2e9f91aeebedd0fd5d6bc9c1e981c6fed04a481e2c240822676a32b3f"
TASK_RULE_FINGERPRINT = "74b3f1cea74510e5872a8ced1ed884cd922d175a13a05c2f368630e371626f56"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def fingerprint(value: object) -> str:
    encoded = json.dumps(value, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def effective_npcs() -> dict[int, dict]:
    definitions: dict[int, dict] = {}
    for filename in ("NpcDefs.json", "NpcDefsCustom.json"):
        for definition in json.loads((NPC_DEFINITIONS / filename).read_text())["npcs"]:
            definitions[definition["id"]] = definition
    for override in json.loads((NPC_DEFINITIONS / "NpcDefsMyWorld.json").read_text())["npcs"]:
        require(override["id"] in definitions,
                f"My World override has unknown NPC {override['id']}")
        definitions[override["id"]] = {**definitions[override["id"]], **override}
    return definitions


def main() -> None:
    data = json.loads(SLAYER_DATA.read_text())
    families = data["families"]
    tasks = [task for contact in data["contacts"]
             for task in contact["mandatoryTasks"] + contact["repeatableTasks"]]
    require(fingerprint([(family["key"], family["npcIds"]) for family in families])
            == ELIGIBILITY_FINGERPRINT,
            "Slayer eligibility IDs changed; presentation must not alter kill credit")
    require(fingerprint([(task["key"], task["familyKey"], task["requiredKills"],
                          task["pointReward"], task.get("weight"), task.get("hazards", []))
                         for task in tasks]) == TASK_RULE_FINGERPRINT,
            "Slayer progression rules changed; this pass permits presentation only")

    definitions = effective_npcs()
    for npc_id, (name, combat_level) in RENAMED_NPCS.items():
        definition = definitions[npc_id]
        require(definition["name"] == name,
                f"world NPC {npc_id} does not use its Slayer clarity name")
        require(definition["combatlvl"] == combat_level,
                f"world NPC {npc_id} combat level changed during naming pass")
    require(definitions[74]["name"] == "Giant Spider" and definitions[74]["combatlvl"] == 31,
            "adult Giant Spider must retain its existing identity")

    family_by_key = {family["key"]: family for family in families}
    task_by_key = {task["key"]: task for task in tasks}
    require(family_by_key["young_giant_spider"]["displayName"] == "Young Giant Spiders",
            "Young Giant Spider world and Slayer text disagree")
    require(task_by_key["falador.tougher_goblins"].get("displayName") == "Tough Goblins",
            "Tough Goblin world and Slayer text disagree")
    require(task_by_key["falador.large_rats"].get("displayName") == "Large Rats",
            "Large Rat world and Slayer text disagree")

    state = STATE.read_text()
    require("family.getNpcIds().contains(npcId)" in state,
            "Slayer eligibility must remain based on NPC IDs, never display names")
    print("PASS: Monster Slayer NPC clarity names preserve ID-based eligibility and task rules")


if __name__ == "__main__":
    main()
