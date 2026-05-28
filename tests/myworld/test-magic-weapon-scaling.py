#!/usr/bin/env python3
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EQUIPMENT_PATH = ROOT / "server/src/com/openrsc/server/model/container/Equipment.java"
PLAYER_PATH = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
COMBAT_FORMULA_PATH = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require_contains(path: Path, needle: str, label: str) -> None:
    text = path.read_text()
    if needle not in text:
        fail(f"{label} missing `{needle}` in {path}")


def main() -> None:
    require_contains(EQUIPMENT_PATH, "private boolean isStaffMagicWeapon(Item item)", "staff-only magic weapon gate")
    require_contains(EQUIPMENT_PATH, "item.getDef(player.getWorld()).getWieldPosition() != EquipmentSlot.SLOT_MAINHAND.getIndex()", "magic weapon mainhand-only check")
    require_contains(EQUIPMENT_PATH, 'getName().toLowerCase().contains("staff")', "magic weapon staff-name check")
    require_contains(EQUIPMENT_PATH, "if (!isStaffMagicWeapon(item)) {\n\t\t\treturn 0;\n\t\t}", "non-staff magic offense rejection")
    require_contains(PLAYER_PATH, "public int getMeleeOffense()", "melee offense method")
    require_contains(PLAYER_PATH, "hasEquippedMeleeWeapon()", "unarmed melee penalty gate")
    require_contains(PLAYER_PATH, "public int getMagicOffense()", "magic offense method")
    require_contains(COMBAT_FORMULA_PATH, "source.getMagicOffense()", "spell damage uses magic offense")
    if "hasEquippedMeleeWeapon()" in PLAYER_PATH.read_text().split("public int getMagicOffense()", 1)[1].split("public double", 1)[0]:
        fail("magic offense should not apply the unarmed melee-weapon penalty")
    print("PASS: magic spell offense is staff-only and unarmed penalty remains melee-scoped")


if __name__ == "__main__":
    main()
