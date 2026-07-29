#!/usr/bin/env python3
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
GROUND_ITEM = ROOT / "server/src/com/openrsc/server/model/entity/GroundItem.java"
REGION = ROOT / "server/src/com/openrsc/server/model/world/region/Region.java"
GROUND_ITEM_TAKE = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/GroundItemTake.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(text: str, needle: str, description: str) -> None:
    if needle not in text:
        fail(f"missing {description}: {needle}")


def forbid(text: str, needle: str, description: str) -> None:
    if needle in text:
        fail(f"retired {description} still present: {needle}")


def main() -> None:
    ground_item = GROUND_ITEM.read_text(encoding="utf-8")
    region = REGION.read_text(encoding="utf-8")
    take = GROUND_ITEM_TAKE.read_text(encoding="utf-8")
    player = PLAYER.read_text(encoding="utf-8")

    forbid(ground_item, "boolean equals(final Entity", "GroundItem value-based Entity equality overload")
    forbid(ground_item, "boolean equals(Object", "GroundItem value-based Object equality override")
    forbid(ground_item, "getSpawnedTime() == getSpawnedTime()", "spawn-time equality key")
    require(region, "items.put(entity.getLocation(), (GroundItem) entity);", "individual ground item registration")
    require(region, "items.remove(location, entity);", "individual ground item removal")
    require(region, ".filter(item -> id == item.getID())", "explicit id lookup for client pickup packets")
    require(take, "final GroundItem item = player.getViewArea().getVisibleGroundItem(itemId, location, player);",
            "pickup captures the specific visible ground item")
    require(take, "getPlayer().canTakeVisibleGroundItem(item)", "pickup rechecks captured ground item")
    require(player, "item == null || item.isInvisibleTo(this)", "shared pickup visibility validation")
    require(player, "checkPlayerActionState && (isBusy() || isRanging())", "shared pickup action-state validation")
    require(player, "item.isRemoved() || getWorld().getRegionManager()",
            "shared pickup removal validation")
    require(player, ".findInteractionGroundItem(",
            "layer-aware pickup registration validation")

    print("PASS: ground items keep object identity while lookups remain explicit")


if __name__ == "__main__":
    main()
