#!/usr/bin/env python3
"""Prove the client renders every authored Monster Slayer NPC faithfully."""

from __future__ import annotations

import json
import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT_JAR = ROOT / "Client_Base/Open_RSC_Client.jar"
SERVER_DEFINITIONS = ROOT / "server/conf/server/defs/MonsterSlayerNpcDefs.json"
CLIENT_HANDLER = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
EXPECTED_IDS = tuple(range(846, 861))
RENAMED_WORLD_NPCS = {
    4: "Tough Goblin",
    23: "Young Giant Spider",
    47: "Large Rat",
    153: "Tough Goblin",
    154: "Tough Goblin",
    177: "Large Rat",
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def java_string(value: str) -> str:
    return json.dumps(value, ensure_ascii=False)


def client_assertion(entry: dict) -> str:
    sprites = ", ".join(str(entry[f"sprites{index}"]) for index in range(1, 13))
    return (
        "assertNpc("
        f"{entry['id']}, {java_string(entry['name'])}, "
        f"{java_string(entry['description'])}, {java_string(entry['command'])}, "
        f"new int[]{{{sprites}}}, {entry['hairColour']}, {entry['topColour']}, "
        f"{entry['bottomColour']}, {entry['skinColour']}, {entry['camera1']}, "
        f"{entry['camera2']}, {entry['walkModel']}, {entry['combatModel']}, "
        f"{entry['combatSprite']});"
    )


def fixture(entries: list[dict]) -> str:
    assertions = "\n        ".join(client_assertion(entry) for entry in entries)
    world_name_assertions = "\n        ".join(
        f"assertNpcName({npc_id}, {java_string(name)});"
        for npc_id, name in RENAMED_WORLD_NPCS.items()
    )
    return textwrap.dedent(
        f"""
        package com.openrsc.client.entityhandling;

        import com.openrsc.client.entityhandling.defs.NPCDef;
        import java.util.Arrays;

        public final class MonsterSlayerClientNpcDefinitionsFixture {{
            private MonsterSlayerClientNpcDefinitionsFixture() {{ }}

            public static void main(String[] args) {{
                EntityHandler.load(true);
                if (EntityHandler.npcCount() != 861) {{
                    throw new AssertionError("Monster Slayer NPC definitions did not extend the client catalog");
                }}
                {assertions}
                {world_name_assertions}
            }}

            private static void assertNpc(int id, String name, String description, String command,
                int[] sprites, int hairColour, int topColour, int bottomColour, int skinColour,
                int camera1, int camera2, int walkModel, int combatModel, int combatSprite) {{
                NPCDef npc = EntityHandler.getNpcDef(id);
                if (npc.id != id || !name.equals(npc.getName())
                    || !description.equals(npc.getDescription()) || !command.equals(npc.getCommand1())
                    || npc.getCommand2() != null || npc.getAtt() != 1 || npc.getStr() != 1
                    || npc.getHits() != 1 || npc.getDef() != 1 || npc.isAttackable()
                    || !Arrays.equals(sprites, npc.sprites) || npc.getHairColour() != hairColour
                    || npc.getTopColour() != topColour || npc.getBottomColour() != bottomColour
                    || npc.getSkinColour() != skinColour || npc.getCamera1() != camera1
                    || npc.getCamera2() != camera2 || npc.getWalkModel() != walkModel
                    || npc.getCombatModel() != combatModel || npc.getCombatSprite() != combatSprite) {{
                    throw new AssertionError("Client/server definition mismatch for Monster Slayer NPC " + id);
                }}
            }}

            private static void assertNpcName(int id, String expectedName) {{
                NPCDef npc = EntityHandler.getNpcDef(id);
                if (npc.id != id || !expectedName.equals(npc.getName())) {{
                    throw new AssertionError("Client world NPC name mismatch for " + id);
                }}
            }}
        }}
        """
    )


def main() -> None:
    document = json.loads(SERVER_DEFINITIONS.read_text(encoding="utf-8"))
    entries = document["npcs"]
    require(tuple(entry["id"] for entry in entries) == EXPECTED_IDS,
            "Monster Slayer server definition inventory drift")
    source = CLIENT_HANDLER.read_text(encoding="utf-8")
    require("addMonsterSlayerNpcDefinition" in source,
            "client has no dedicated Monster Slayer NPC definition boundary")

    subprocess.run([str(ROOT / "scripts/build-client.sh")], cwd=ROOT, check=True)
    with tempfile.TemporaryDirectory(prefix="monster-slayer-client-npcs-") as directory:
        root = Path(directory)
        java_source = root / "com/openrsc/client/entityhandling/MonsterSlayerClientNpcDefinitionsFixture.java"
        java_source.parent.mkdir(parents=True)
        java_source.write_text(fixture(entries), encoding="utf-8")
        subprocess.run(
            ["javac", "-cp", str(CLIENT_JAR), "-d", directory, str(java_source)],
            cwd=ROOT,
            check=True,
        )
        result = subprocess.run(
            ["java", "-cp", f"{directory}:{CLIENT_JAR}",
             "com.openrsc.client.entityhandling.MonsterSlayerClientNpcDefinitionsFixture"],
            cwd=ROOT / "Client_Base",
            capture_output=True,
            text=True,
        )
        require(result.returncode == 0,
                "client Monster Slayer NPC parity fixture failed:\n"
                f"stdout:\n{result.stdout}\nstderr:\n{result.stderr}")
    print("PASS: Monster Slayer contacts and renamed world NPCs match client presentation data")


if __name__ == "__main__":
    main()
