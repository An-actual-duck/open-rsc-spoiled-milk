#!/usr/bin/env python3
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
WORLD = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "model" / "world" / "World.java"
RANGE_EVENT = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "projectile" / "RangeEvent.java"
THROWING_EVENT = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "projectile" / "ThrowingEvent.java"
MAGIC_COMBAT_EVENT = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "event" / "rsc" / "impl" / "projectile" / "MagicCombatEvent.java"
PROJECTILE_POLICY = ROOT / "server" / "src" / "com" / "openrsc" / "server" / "util" / "rsc" / "LegacyObjectProjectileCollisionPolicy.java"


def fail(message: str) -> None:
    raise SystemExit(f"FAIL: {message}")


def require(text: str, needle: str, description: str) -> None:
    if needle not in text:
        fail(f"missing {description}: {needle}")


def main() -> None:
    world = WORLD.read_text(encoding="utf-8")
    policy = PROJECTILE_POLICY.read_text(encoding="utf-8")
    tree_rule = 'if (lowercaseName.contains("tree"))'
    allowlist_loop = "for (String allowedName : checkedAllowedNames)"
    require(policy, tree_rule, "all-tree projectile clipping allowance")
    require(policy, allowlist_loop, "existing projectile clip allowlist")
    if policy.find(tree_rule) > policy.find(allowlist_loop):
        fail("tree projectile allowance should run before legacy object allowlist")

    require(world, "Definition.scenery(", "scenery collision policy registration")
    require(world, "Constants.objectsProjectileClipAllowed", "projectile allowlist routing")
    require(
        RANGE_EVENT.read_text(encoding="utf-8"),
        "player.getWorld(), player.getWorldLocation(),",
        "layer-qualified ranged clear-shot path validation",
    )
    require(
        THROWING_EVENT.read_text(encoding="utf-8"),
        "getWorld(), player.getWorldLocation(),",
        "layer-qualified thrown clear-shot path validation",
    )
    require(
        MAGIC_COMBAT_EVENT.read_text(encoding="utf-8"),
        "player.getWorld(), player.getWorldLocation(),",
        "layer-qualified magic clear-shot path validation",
    )

    print("PASS: tree scenery no longer blocks ranged or magic projectile line checks")


if __name__ == "__main__":
    main()
