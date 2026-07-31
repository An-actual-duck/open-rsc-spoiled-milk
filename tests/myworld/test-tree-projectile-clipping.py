#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORLD = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "world" / "World.java"
RANGE_EVENT = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "projectile" / "RangeEvent.java"
THROWING_EVENT = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "projectile" / "ThrowingEvent.java"
MAGIC_COMBAT_EVENT = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "projectile" / "MagicCombatEvent.java"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(text: str, needle: str, description: str) -> None:
    if needle not in text:
        fail(f"missing {description}: {needle}")


def main() -> None:
    world = WORLD.read_text(encoding="utf-8")
    helper_start = world.find("private boolean isProjectileClipAllowed(GameObject o)")
    if helper_start < 0:
        fail("missing projectile clip helper")

    helper_end = world.find("\n\tprivate ", helper_start + 1)
    if helper_end < 0:
        fail("could not locate end of projectile clip helper")
    helper = world[helper_start:helper_end]

    tree_rule = 'o.getType() == 0 && o.getGameObjectDef().getName().toLowerCase().contains("tree")'
    allowlist_loop = "for (final String s : com.openrsc.server.constants.Constants.objectsProjectileClipAllowed)"
    require(helper, tree_rule, "all-tree projectile clipping allowance")
    require(helper, allowlist_loop, "existing projectile clip allowlist")
    if helper.find(tree_rule) > helper.find(allowlist_loop):
        fail("tree projectile allowance should run before legacy object allowlist")

    require(world, "handleProjectileClipAllowance(x, y, dir, o.getType(), o.getGameObjectDef().getType(), -1);", "scenery projectile allowance registration")
    require(world, "resetProjectileAllowance(x, y, dir, o.getType(), o.getGameObjectDef().getType(), -1);", "scenery projectile allowance reset")
    require(RANGE_EVENT.read_text(encoding="utf-8"), "PathValidation.checkPath(player.getWorld(), player.getLocation(), target.getLocation())", "ranged clear-shot path validation")
    require(THROWING_EVENT.read_text(encoding="utf-8"), "PathValidation.checkPath(getWorld(), player.getLocation(), target.getLocation())", "thrown clear-shot path validation")
    require(MAGIC_COMBAT_EVENT.read_text(encoding="utf-8"), "PathValidation.checkPath(player.getWorld(), player.getLocation(), target.getLocation())", "magic clear-shot path validation")

    print("PASS: tree scenery no longer blocks ranged or magic projectile line checks")


if __name__ == "__main__":
    main()
