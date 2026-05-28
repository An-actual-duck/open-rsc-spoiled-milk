#!/usr/bin/env python3
"""Validate reviewed MyWorld combat exceptions and PvM-only guardrails."""

import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server"
MYWORLD_CONF = SERVER / "myworld.conf"
NPC = SERVER / "src" / "com" / "openrsc" / "server" / "model" / "entity" / "npc" / "Npc.java"
SPELL_HANDLER = SERVER / "src" / "com" / "openrsc" / "server" / "net" / "rsc" / "handlers" / "SpellHandler.java"
ATTACK_PLAYER = SERVER / "plugins" / "com" / "openrsc" / "server" / "plugins" / "shared" / "AttackPlayer.java"
PK_BOT = SERVER / "plugins" / "com" / "openrsc" / "server" / "plugins" / "custom" / "npcs" / "PkBot.java"

ALLOWED_KILLED_BY_FILES = {
    "server/src/com/openrsc/server/Server.java",
    "server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java",
    "server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java",
    "server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java",
    "server/src/com/openrsc/server/model/entity/Mob.java",
    "server/src/com/openrsc/server/model/entity/npc/Npc.java",
    "server/src/com/openrsc/server/model/entity/player/Player.java",
    "server/src/com/openrsc/server/net/rsc/handlers/SpellHandler.java",
    "server/plugins/com/openrsc/server/plugins/authentic/commands/Admins.java",
    "server/plugins/com/openrsc/server/plugins/authentic/quests/members/SheepHerder.java",
    "server/plugins/com/openrsc/server/plugins/custom/misc/ResetCrystal.java",
}


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def read(path: Path) -> str:
    if not path.exists():
        fail(f"Missing file: {path}")
    return path.read_text(encoding="utf-8")


def relative(path: Path) -> str:
    return path.relative_to(ROOT).as_posix()


def require_contains(path: Path, needle: str) -> None:
    text = read(path)
    if needle not in text:
        fail(f"{relative(path)} missing expected text: {needle}")


def require_regex(path: Path, pattern: str, description: str) -> None:
    text = read(path)
    if not re.search(pattern, text, re.DOTALL):
        fail(f"{relative(path)} missing expected invariant: {description}")


def require_config_false(key: str) -> None:
    text = read(MYWORLD_CONF)
    if not re.search(rf"^\s*{re.escape(key)}:\s*false\b", text, re.MULTILINE):
        fail(f"server/myworld.conf must keep {key}: false for the PvM-only world")


def validate_no_external_drop_items_calls() -> None:
    offenders = []
    for path in SERVER.rglob("*.java"):
        text = read(path)
        if ".dropItems(" in text and path != NPC:
            offenders.append(relative(path))
    if offenders:
        fail("Unexpected direct NPC dropItems call outside Npc.java: " + ", ".join(offenders))


def validate_killed_by_call_sites_are_reviewed() -> None:
    offenders = []
    for path in SERVER.rglob("*.java"):
        text = read(path)
        if "killedBy(" not in text:
            continue
        rel = relative(path)
        if rel not in ALLOWED_KILLED_BY_FILES:
            offenders.append(rel)
    if offenders:
        fail("Unreviewed killedBy call site(s): " + ", ".join(sorted(offenders)))


def main() -> None:
    require_config_false("want_pvp")
    require_config_false("want_openpk_points")
    require_config_false("want_pk_bots")
    require_config_false("want_openpk_presets")
    require_contains(ATTACK_PLAYER, "if (!player.getConfig().WANT_PVP)")
    require_contains(PK_BOT, "if (!player.getLocation().inWilderness())")

    validate_no_external_drop_items_calls()
    validate_killed_by_call_sites_are_reviewed()

    require_regex(
        SPELL_HANDLER,
        r"secondDamageLastHits = affectedMob\.getSkills\(\)\.getLevel\(Skill\.HITS\.id\(\)\).*?\(\(Npc\) affectedMob\)\.addMageDamage\(getPlayer\(\), Math\.min\(appliedSecondDamage, secondDamageLastHits\)\).*?affectedMob\.killedBy\(getPlayer\(\)\);",
        "Salarin's delayed second strike records magic contribution before it can kill",
    )

    require_regex(
        NPC,
        r"removeHandledInPlugin = \{.*?NpcId\.DELRITH\.id\(\).*?NpcId\.COUNT_DRAYNOR\.id\(\).*?NpcId\.CHRONOZON\.id\(\).*?NpcId\.BLACK_KNIGHT_TITAN\.id\(\).*?\};",
        "quest/plugin-owned NPC deaths stay explicit in removeHandledInPlugin",
    )

    print("PASS: combat exception guardrails validated")


if __name__ == "__main__":
    main()
