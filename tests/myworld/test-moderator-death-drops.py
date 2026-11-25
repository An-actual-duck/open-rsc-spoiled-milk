#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(text: str, needle: str, description: str) -> None:
    if needle not in text:
        fail(f"missing {description}: {needle}")


def main() -> None:
    player = PLAYER.read_text(encoding="utf-8")
    require(player, "private boolean keepsInventoryOnDeathFromStaffRole()", "staff death retention helper")
    require(player, "case Group.SUPER_MOD:", "super moderators retain elevated death behavior")
    require(player, "case Group.ADMIN:", "admins retain elevated death behavior")
    require(player, "case Group.OWNER:", "owners retain elevated death behavior")
    if "case Group.MOD:\n\t\t\t\treturn true;" in player.split("private boolean keepsInventoryOnDeathFromStaffRole()", 1)[1]:
        fail("regular moderators should not keep inventory on death")
    require(player, 'return keepsInventoryOnDeathFromStaffRole() && !getCache().hasKey("myworld_test_death_drops");',
            "staff death retention still respects the test override")
    require(player, "} else if (!keepsInventoryOnDeath())",
            "death drops should use the same retention decision as the preview")
    require(player, "getCarriedItems().getInventory().dropOnDeath(mob);", "normal death drop path")

    print("PASS: moderators use normal death drop behavior")


if __name__ == "__main__":
    main()
