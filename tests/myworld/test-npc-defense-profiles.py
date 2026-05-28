#!/usr/bin/env python3
import json
import math
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DEFS_DIR = ROOT / "server" / "conf" / "server" / "defs"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def load_json_array(path: Path):
    with path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    return data[next(iter(data.keys()))]


def load_npcs():
    npcs = {}
    for filename in ("NpcDefs.json", "NpcDefsCustom.json"):
        for npc in load_json_array(DEFS_DIR / filename):
            npcs[npc["id"]] = npc
    for npc in load_json_array(DEFS_DIR / "NpcDefsMyWorld.json"):
        npcs.setdefault(npc["id"], {"id": npc["id"]})
        npcs[npc["id"]].update(npc)
    return npcs


def derive_style_defenses(npc: dict) -> dict:
    legacy_defense = int(npc.get("defense", 0))
    values = {}
    defaults = {"melee": 1.0, "ranged": 0.5, "magic": 0.5}
    for style, default_multiplier in defaults.items():
        explicit_defense = npc.get(f"{style}Defense")
        if explicit_defense is not None and int(explicit_defense) > 0:
            values[style] = int(explicit_defense)
            continue
        multiplier = npc.get(f"{style}DefenseMultiplier", -1.0)
        if multiplier is None or float(multiplier) < 0.0:
            divisor = npc.get(f"{style}DefenseDivisor", -1.0)
            if divisor is not None and float(divisor) > 0.0:
                multiplier = 1.0 / float(divisor)
            else:
                multiplier = default_multiplier
        values[style] = max(0, int(math.floor(legacy_defense * float(multiplier))))
    return values


def require_profile(npcs, npc_id: int, expected: tuple[str, str, str], label: str) -> None:
    npc = npcs.get(npc_id)
    if npc is None:
        fail(f"{label} missing npc id {npc_id}")
    defenses = derive_style_defenses(npc)
    ordered = tuple(style for style, _ in sorted(defenses.items(), key=lambda item: (-item[1], item[0])))
    if ordered != expected:
        fail(f"{label} expected {expected} but found {ordered} from {defenses}")


def require_exact_profile(npcs, npc_id: int, expected: dict[str, int], label: str) -> None:
    npc = npcs.get(npc_id)
    if npc is None:
        fail(f"{label} missing npc id {npc_id}")
    actual = derive_style_defenses(npc)
    if actual != expected:
        fail(f"{label} expected {expected} but found {actual}")


def require_equal_pair(npcs, npc_id: int, first: str, second: str, stronger_than: str, label: str) -> None:
    npc = npcs.get(npc_id)
    if npc is None:
        fail(f"{label} missing npc id {npc_id}")
    actual = derive_style_defenses(npc)
    if actual[first] != actual[second] or actual[first] <= actual[stronger_than]:
        fail(f"{label} expected {first}={second}>{stronger_than}, found {actual}")


def main() -> None:
    npcs = load_npcs()

    # Early source-creature anchors used by leather families.
    require_profile(npcs, 6, ("melee", "magic", "ranged"), "Cow hide baseline")
    require_profile(npcs, 4, ("melee", "ranged", "magic"), "Goblin hide baseline")
    require_profile(npcs, 0, ("magic", "melee", "ranged"), "Unicorn hide baseline")
    require_profile(npcs, 188, ("melee", "magic", "ranged"), "Bear hide baseline")
    require_profile(npcs, 296, ("magic", "melee", "ranged"), "Black unicorn hide baseline")
    require_profile(npcs, 70, ("ranged", "melee", "magic"), "Scorpion carapace baseline")
    require_profile(npcs, 243, ("melee", "ranged", "magic"), "Wolf hide baseline")
    require_profile(npcs, 74, ("ranged", "melee", "magic"), "Spider carapace baseline")
    require_profile(npcs, 61, ("melee", "magic", "ranged"), "Giant hide baseline")
    require_profile(npcs, 531, ("melee", "ranged", "magic"), "Ogre hide baseline")
    require_profile(npcs, 203, ("magic", "melee", "ranged"), "Baby dragon hide baseline")
    require_profile(npcs, 263, ("ranged", "magic", "melee"), "Magic spider / ice spider baseline")

    # Mid/high leather-source anchors.
    require_profile(npcs, 104, ("melee", "magic", "ranged"), "Moss giant hide baseline")
    require_profile(npcs, 135, ("melee", "magic", "ranged"), "Ice giant hide baseline")
    require_profile(npcs, 22, ("magic", "melee", "ranged"), "Demon hide baseline")
    require_profile(npcs, 294, ("melee", "magic", "ranged"), "Hellhound hide baseline")
    require_profile(npcs, 344, ("melee", "magic", "ranged"), "Fire giant hide baseline")
    require_equal_pair(npcs, 202, "melee", "magic", "ranged", "Blue dragon hide baseline")
    require_equal_pair(npcs, 201, "melee", "magic", "ranged", "Red dragon hide baseline")
    require_profile(npcs, 290, ("magic", "melee", "ranged"), "Black demon hide baseline")
    require_equal_pair(npcs, 291, "melee", "magic", "ranged", "Black dragon hide baseline")
    require_equal_pair(npcs, 477, "melee", "magic", "ranged", "King Black Dragon hide baseline")

    # Core combat identity anchors outside leather.
    require_profile(npcs, 15, ("magic", "melee", "ranged"), "Ghost defensive identity")
    require_profile(npcs, 40, ("ranged", "magic", "melee"), "Skeleton defensive identity")
    require_profile(npcs, 41, ("melee", "magic", "ranged"), "Zombie defensive identity")
    require_profile(npcs, 65, ("melee", "magic", "ranged"), "Guard defensive identity")
    require_profile(npcs, 102, ("melee", "magic", "ranged"), "White knight defensive identity")
    require_profile(npcs, 789, ("magic", "melee", "ranged"), "Battle mage defensive identity")

    # Exact spot-checks for a few important runtime anchors.
    require_exact_profile(npcs, 22, {"melee": 59, "ranged": 39, "magic": 79}, "Lesser demon exact defense split")
    require_exact_profile(npcs, 202, {"melee": 105, "ranged": 78, "magic": 105}, "Blue dragon exact defense split")
    require_exact_profile(npcs, 477, {"melee": 240, "ranged": 180, "magic": 240}, "KBD exact defense split")
    require_exact_profile(npcs, 789, {"melee": 9, "ranged": 9, "magic": 90}, "Battle mage exact defense split")

    print("PASS: npc defense profiles validated")


if __name__ == "__main__":
    main()
