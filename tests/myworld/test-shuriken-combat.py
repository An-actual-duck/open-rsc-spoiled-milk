#!/usr/bin/env python3
import sys
import struct
import zlib
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
THROWING_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/ThrowingEvent.java"
RANGE_UTILS = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/RangeUtils.java"
CRAFTING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/crafting/Crafting.java"
SMELTING = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/smithing/Smelting.java"
ITEM_ID = ROOT / "server/src/com/openrsc/server/constants/ItemId.java"
FORMULAE = ROOT / "server/src/com/openrsc/server/util/rsc/Formulae.java"
CLIENT_ENTITY_HANDLER = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
CLIENT_MUDCLIENT = ROOT / "Client_Base/src/orsc/mudclient.java"
PROJECTILE_ANIMATION_CATALOG = ROOT / "Client_Base/src/orsc/graphics/two/ProjectileAnimationCatalog.java"
PROJECTILE = ROOT / "server/src/com/openrsc/server/model/entity/update/Projectile.java"
UPDATE_FLAGS = ROOT / "server/src/com/openrsc/server/model/entity/update/UpdateFlags.java"
GAME_STATE_UPDATER = ROOT / "server/src/com/openrsc/server/GameStateUpdater.java"
MOB = ROOT / "server/src/com/openrsc/server/model/entity/Mob.java"
PROJECTILE_EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java"
SHURIKEN_BASIC = ROOT / "dev/myworld/assets/runtime/sprites/items/inventory-ground/shuriken-basic.png"
SHURIKEN_POISON = ROOT / "dev/myworld/assets/runtime/sprites/items/inventory-ground/shuriken-basic-poison.png"
SHURIKEN_THROWN = ROOT / "dev/myworld/assets/runtime/sprites/items/inventory-ground/shuriken-thrown.png"


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def require(text: str, snippet: str, label: str) -> None:
    if snippet not in text:
        fail(f"{label} missing expected snippet: {snippet}")


def read_rgba_png(path: Path) -> list[tuple[int, int, int, int]]:
    data = path.read_bytes()
    if not data.startswith(b"\x89PNG\r\n\x1a\n"):
        fail(f"{path.name} is not a PNG")

    offset = 8
    width = height = color_type = bit_depth = interlace = None
    payload = bytearray()
    while offset < len(data):
        length = struct.unpack(">I", data[offset:offset + 4])[0]
        chunk_type = data[offset + 4:offset + 8]
        chunk_data = data[offset + 8:offset + 8 + length]
        offset += 12 + length
        if chunk_type == b"IHDR":
            width, height, bit_depth, color_type, _, _, interlace = struct.unpack(">IIBBBBB", chunk_data)
        elif chunk_type == b"IDAT":
            payload.extend(chunk_data)
        elif chunk_type == b"IEND":
            break

    if width is None or height is None or bit_depth != 8 or color_type != 6 or interlace != 0:
        fail(f"{path.name} must be a non-interlaced 8-bit RGBA PNG")

    raw = zlib.decompress(payload)
    stride = width * 4
    rows: list[bytearray] = []
    cursor = 0
    for _ in range(height):
        filter_type = raw[cursor]
        cursor += 1
        row = bytearray(raw[cursor:cursor + stride])
        cursor += stride
        previous = rows[-1] if rows else bytearray(stride)
        for i in range(stride):
            left = row[i - 4] if i >= 4 else 0
            up = previous[i]
            upper_left = previous[i - 4] if i >= 4 else 0
            if filter_type == 1:
                row[i] = (row[i] + left) & 0xFF
            elif filter_type == 2:
                row[i] = (row[i] + up) & 0xFF
            elif filter_type == 3:
                row[i] = (row[i] + ((left + up) // 2)) & 0xFF
            elif filter_type == 4:
                predictor = left + up - upper_left
                candidates = (abs(predictor - left), abs(predictor - up), abs(predictor - upper_left))
                row[i] = (row[i] + (left, up, upper_left)[candidates.index(min(candidates))]) & 0xFF
            elif filter_type != 0:
                fail(f"{path.name} uses unsupported PNG filter {filter_type}")
        rows.append(row)

    pixels: list[tuple[int, int, int, int]] = []
    for row in rows:
        for i in range(0, len(row), 4):
            pixels.append((row[i], row[i + 1], row[i + 2], row[i + 3]))
    return pixels


def assert_shuriken_palette_ready(path: Path, allow_poison_green: bool) -> None:
    opaque_pixels = [pixel for pixel in read_rgba_png(path) if pixel[3] >= 64]
    if not opaque_pixels:
        fail(f"{path.name} has no visible pixels")

    poison_green_pixels = 0
    for red, green, blue, _ in opaque_pixels:
        is_poison_green = green > red * 1.35 and green > blue * 1.35
        if is_poison_green:
            poison_green_pixels += 1
            if not allow_poison_green:
                fail(f"{path.name} should not contain poison-green pixels")
            continue
        if red != green or green != blue:
            fail(f"{path.name} has non-grayscale mask pixel ({red}, {green}, {blue})")

    if allow_poison_green and poison_green_pixels == 0:
        fail(f"{path.name} should preserve non-grayscale poison-green pixels")


def main() -> None:
    throwing = THROWING_EVENT.read_text(encoding="utf-8")
    range_utils = RANGE_UTILS.read_text(encoding="utf-8")
    crafting = CRAFTING.read_text(encoding="utf-8")
    smelting = SMELTING.read_text(encoding="utf-8")
    item_id = ITEM_ID.read_text(encoding="utf-8")
    formulae = FORMULAE.read_text(encoding="utf-8")
    client = CLIENT_ENTITY_HANDLER.read_text(encoding="utf-8")
    mudclient = CLIENT_MUDCLIENT.read_text(encoding="utf-8")
    projectile_animation_catalog = PROJECTILE_ANIMATION_CATALOG.read_text(encoding="utf-8")
    projectile = PROJECTILE.read_text(encoding="utf-8")
    update_flags = UPDATE_FLAGS.read_text(encoding="utf-8")
    game_state_updater = GAME_STATE_UPDATER.read_text(encoding="utf-8")
    mob = MOB.read_text(encoding="utf-8")
    projectile_event = PROJECTILE_EVENT.read_text(encoding="utf-8")

    for snippet in (
        "SHURIKEN_THROW_COUNT = 3",
        "shurikenTargetLock",
        "resolvePrimaryTarget(player, throwingID)",
        "findAutoRetaliatePrimaryTarget(player)",
        "shouldAutoRetaliateRetarget(final Player player, final Mob attacker)",
        "findLockedShurikenPrimary(player, attackRadius)",
        "findLockedShurikenPrimary(player, getAttackRadius(throwingID))",
        "player.setOpponent(fallback);",
        "npc.getOpponent() == player",
        "selectThrowingTargets(player, throwingID, attackRadius)",
        "throwsToConsume = RangeUtils.SHURIKENS.contains(throwingID) ? throwingTargets.size() : 1",
        "while (throwingTargets.size() > throwsToConsume)",
        "rememberShurikenTargets(player, throwingTargets, attackRadius);",
        "primeShurikenAggro(player, throwingTargets);",
        "targets.add(target)",
        "!target.isNpc()",
        "|| Summoning.isSummon(npc)",
        "countAggroedShurikenTargets(candidates, player)",
        "boolean preferAggroed = aggroedCount >= SHURIKEN_THROW_COUNT",
        "addLockedShurikenTargets(player, targets, candidates, attackRadius)",
        "addRandomShurikenTargets(targets, newTargets)",
        "addRandomShurikenTargets(targets, aggroedFallback)",
        "if (countAggroedShurikenTargets(candidates, player) > SHURIKEN_THROW_COUNT)",
        "pruneShurikenTargetLock(candidates);",
        "npc.getOpponent() == player || npc.getPreferredThreatTarget() == player",
        "npc.addRangeDamage(player, 0);",
        "npc.startPvmCounterCombat(player);",
        "applyThrowingHit(player, throwingID, throwingTargets.get(i), skillCape, i == 0 || isShuriken, i == 0)",
        "? DuplicationStrategy.ALLOW_MULTIPLE",
        "? Projectile.SHURIKEN",
    ):
        require(throwing, snippet, "ThrowingEvent shuriken combat contract")

    for snippet in (
        "ItemId.TIN_SHURIKEN.id()",
        "ItemId.RUNE_SHURIKEN.id()",
        "ItemId.POISONED_TIN_SHURIKEN.id()",
        "ItemId.POISONED_RUNE_SHURIKEN.id()",
    ):
        require(range_utils, snippet, "RangeUtils.SHURIKENS")
        require(formulae, snippet, "Formulae.throwingIDs")

    for snippet in (
        "POISONED_TIN_SHURIKEN",
        "POISONED_COPPER_SHURIKEN",
        "POISONED_BRONZE_SHURIKEN",
        "POISONED_IRON_SHURIKEN",
        "POISONED_STEEL_SHURIKEN",
        "POISONED_MITHRIL_SHURIKEN",
        "POISONED_TITAN_STEEL_SHURIKEN",
        "POISONED_ADAMANTITE_SHURIKEN",
        "POISONED_ORICHALCUM_SHURIKEN",
        "POISONED_RUNE_SHURIKEN",
    ):
        require(item_id, snippet, "ItemId poison-compatible shuriken names")

    require(crafting, 'addRangedMouldRecipe(recipes, player, barId, getShurikenId(barId), ItemId.SHURIKEN_MOULD.id(), "Shuriken", 9, 4)', "Crafting shuriken recipe")
    require(crafting, 'new RangedMouldRecipe("Shuriken", itemId, ItemId.SHURIKEN_MOULD.id(), 9', "Crafting shuriken yield")
    require(smelting, "FURNACE_CATEGORY_SHURIKEN", "Smelting shuriken category")
    require(smelting, "ItemId.SHURIKEN_MOULD.id()", "Smelting shuriken mould gate")
    require(client, 'new ItemDef("Shuriken mould", "Use with bars to cast shuriken"', "Client shuriken mould definition")
    require(client, '"external-png:shuriken-mould"', "Client shuriken mould sprite")
    require(client, '"external-png:shuriken-basic"', "Client shuriken sprite")
    require(client, '"external-png:shuriken-basic-poison"', "Client poisoned shuriken sprite")
    require(client, "SHURIKEN(24)", "Client shuriken projectile id")
    require(client, 'new SpriteDef("shuriken projectile"', "Client shuriken projectile definition")
    require(projectile, "public static final int SHURIKEN = 24;", "Server shuriken projectile id")
    require(update_flags, "private ConcurrentLinkedQueue<Projectile> projectiles", "UpdateFlags projectile queue")
    require(update_flags, "this.projectiles.add(projectile);", "UpdateFlags retains multiple projectiles")
    require(update_flags, "public List<Projectile> getProjectiles()", "UpdateFlags exposes multiple projectiles")
    require(game_state_updater, "updateFlags.getProjectiles()", "GameStateUpdater sends multiple projectiles")
    require(game_state_updater, "updates.add((short) safeNPCIndex(player, victim.getIndex()))", "NPC projectile updates are victim keyed")
    require(game_state_updater, "updates.add((byte) 4);", "NPC projectile updates support player shooters")
    require(projectile_event, "opponent.getUpdateFlags().setProjectile(projectile);", "ProjectileEvent queues visuals on victim")
    require(mob, "startPlayerRangedPvmCounterCombat(attacker)", "PvM auto-retaliate checks ranged before melee")
    require(mob, "new RangeEvent(player.getWorld(), player, 1, attacker)", "PvM auto-retaliate can resume bow ranged")
    require(mob, "new ThrowingEvent(player.getWorld(), player, 1, attacker)", "PvM auto-retaliate can resume throwing ranged")
    require(mob, "throwingEvent.shouldAutoRetaliateRetarget(player, attacker)", "PvM auto-retaliate lets shuriken preserve its locked targets")
    require(projectile_animation_catalog,
            'define(definitions, "shuriken-basic", "Shuriken", "shuriken-basic/shuriken-basic.png", 8, 1, 0, 8);',
            "Client shuriken animation sheet")
    require(projectile_animation_catalog,
            'fallback(fallbacks, PROJECTILE_TYPES.SHURIKEN, "shuriken-basic");',
            "Client shuriken projectile fallback")
    require(mudclient, "ProjectileAnimationCatalog.getProjectileFallback(projectileId)",
            "Client shuriken animation lookup")
    require(mudclient, "getProjectileSceneSizeForAnimatedEnemy(projectile, enemyProjectile, SHURIKEN_PROJECTILE_SCENE_SIZE)",
            "Client shuriken animated scene size")
    require(mudclient, "boolean enemyProjectile = true;", "Client projectile renderer tracks shooter ownership")
    require(mudclient, "int var5 = var16.currentX;", "Client projectile renderer starts at shooter")
    require(mudclient, "int var8 = var3.currentX;", "Client projectile renderer ends at victim")
    require(mudclient, "enemyProjectile = false;", "Client projectile renderer keeps player-fired shuriken normal size")
    assert_shuriken_palette_ready(SHURIKEN_BASIC, False)
    assert_shuriken_palette_ready(SHURIKEN_POISON, True)
    if not SHURIKEN_THROWN.is_file():
        fail("shuriken-thrown.png missing from packaged client asset tree")


if __name__ == "__main__":
    main()
