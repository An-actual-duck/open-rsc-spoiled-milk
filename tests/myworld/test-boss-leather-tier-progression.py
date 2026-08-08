#!/usr/bin/env python3
import json
import struct
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
DEFS = ROOT / "server/conf/server/defs"


def read(relative: str) -> str:
    return (ROOT / relative).read_text(encoding="utf-8")


def items(filename: str) -> dict[int, dict]:
    payload = json.loads((DEFS / filename).read_text(encoding="utf-8"))
    return {item["id"]: item for item in payload["items"]}


def png_size(relative: str) -> tuple[int, int]:
    data = (ROOT / relative).read_bytes()
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    return struct.unpack(">II", data[16:24])


def require_all(source: str, snippets: tuple[str, ...]) -> None:
    for snippet in snippets:
        assert snippet in source, f"missing {snippet!r}"


def main() -> int:
    custom = items("ItemDefsCustom.json")
    overrides = items("ItemDefsMyWorld.json")

    assert set(range(3311, 3318)).issubset(custom)
    assert custom[3311]["name"] == "King black dragon hide"
    assert custom[3312]["name"] == "King black dragon leather"
    expected_names = (
        "King-black-dragon-hide coif",
        "King-black-dragon-hide gloves",
        "King-black-dragon-hide boots",
        "King-black-dragon-hide chaps",
        "King-black-dragon-hide cuirass",
    )
    expected_slots = (5, 8, 9, 7, 6)
    for item_id, name, slot in zip(range(3313, 3318), expected_names, expected_slots):
        definition = custom[item_id]
        assert definition["name"] == name
        assert definition["isWearable"] == 1
        assert definition["wearSlot"] == slot

    kbd_defenses = ((3, 3, 3), (6, 5, 6), (6, 5, 6), (9, 7, 9), (12, 9, 12))
    balrog_defenses = ((3, 2, 4), (6, 4, 8), (6, 4, 8), (9, 6, 12), (12, 8, 16))
    elder_defenses = ((3, 3, 3), (7, 5, 6), (7, 5, 6), (10, 7, 10), (13, 10, 13))
    for item_id, expected in zip(range(3313, 3318), kbd_defenses):
        assert tuple(overrides[item_id][field] for field in
                     ("meleeDefense", "rangedDefense", "magicDefense")) == expected
    for item_id, expected in zip(range(1945, 1950), balrog_defenses):
        assert tuple(overrides[item_id][field] for field in
                     ("meleeDefense", "rangedDefense", "magicDefense")) == expected
    for item_id, expected in zip(range(1950, 1955), elder_defenses):
        assert tuple(overrides[item_id][field] for field in
                     ("meleeDefense", "rangedDefense", "magicDefense")) == expected

    assert [overrides[item_id]["basePrice"] for item_id in range(1945, 1950)] == [
        18750, 37500, 37500, 56250, 75000
    ]
    assert [overrides[item_id]["basePrice"] for item_id in range(3313, 3318)] == [
        11250, 22500, 22500, 33750, 45000
    ]
    assert overrides[1831]["basePrice"] == 14000
    assert overrides[1832]["basePrice"] == 20000

    crafting = read("server/plugins/com/openrsc/server/plugins/authentic/skills/crafting/Crafting.java")
    tanning = read("server/plugins/com/openrsc/server/plugins/custom/skills/crafting/TanningRack.java")
    drops = read("server/src/com/openrsc/server/constants/NpcDrops.java")
    require_all(crafting, (
        'new HideArmorRecipe(materialId, "King black dragon hide", 10, 70,',
        'new HideArmorRecipe(materialId, "Balrog hide", 11, 80,',
        'new HideArmorRecipe(materialId, "Elder green dragon hide", 11, 80,',
    ))
    require_all(tanning, (
        "new MaterialProcess(MyWorldItemId.KING_BLACK_DRAGON_HIDE, MyWorldItemId.KING_BLACK_DRAGON_LEATHER, 9, 50)",
        "new MaterialProcess(ItemId.BALROG_HIDE.id(), ItemId.BALROG_LEATHER.id(), 10, 55)",
        "new MaterialProcess(ItemId.ELDER_GREEN_DRAGON_HIDE.id(), ItemId.ELDER_GREEN_DRAGON_LEATHER.id(), 10, 55)",
    ))
    require_all(drops, (
        'addGuaranteedDrop(NpcId.KING_BLACK_DRAGON.id(), MyWorldItemId.KING_BLACK_DRAGON_HIDE, "King Black Dragon hide");',
        'addGuaranteedDrop(NpcId.ELDER_GREEN_DRAGON.id(), ItemId.ELDER_GREEN_DRAGON_HIDE.id(), "Elder Green Dragon hide");',
    ))

    equipment = read("server/src/com/openrsc/server/model/container/Equipment.java")
    require_all(equipment, (
        "private static final int[] kingBlackDragonSetIds",
        "MyWorldItemId.KING_BLACK_DRAGON_CUIRASS",
        "public boolean hasFullKingBlackDragonSet()",
        "public double getDragonBreathArmorProcChance()",
        "if (hasFullKingBlackDragonSet()) {\n\t\t\treturn 0.40D;",
        "public int getDragonBreathArmorAppliedPoisonPower()",
        "public int getDragonBreathArmorMaxPoisonPower()",
        'return "king_black";',
        "if (hasFullBalrogSet()) {",
        "return 0.40D;",
        "return getInfernalFireProcMaxHit() > 0 ? 0.20D : 0.0D;",
    ))
    for item_id in range(3313, 3318):
        assert custom[item_id]["description"].startswith(
            "Full KBD-hide set: 40% chance for True Dragon's Breath"
        )
    for item_id in range(1945, 1950):
        assert "Hell's Inferno: a 40% chance" in custom[item_id]["description"]
    for item_id in range(1950, 1955):
        description = custom[item_id]["description"]
        assert "60% chance for Elder Breath" in description
        assert "half that actual damage within 2 tiles" in description
        assert "1 burn damage for 5 pulses" in description

    utility = read("server/src/com/openrsc/server/util/rsc/CombatEffectUtil.java")
    require_all(utility, (
        "HELLS_INFERNO_MAX_HIT = 18",
        "HELLS_INFERNO_SPLASH_RADIUS = 2",
        "hellsInfernoSplashDamage",
        "Summoning.isPlayerAreaEffectSuppressed(player)",
        "PlayerOwnedNpcRadiusSelection",
        "findPlayerOwnedPvpSplashTargets",
        "player.getDuel().isDuelActive()",
        "CombatEligibility.evaluate(CombatEligibilityRequest.builder(",
        ".playerAttackRules(true)",
    ))
    radius_selection = read(
        "server/src/com/openrsc/server/model/combat/PlayerOwnedNpcRadiusSelection.java"
    )
    require_all(radius_selection, (
        "owner.getViewArea().getNpcsInView()",
        "Summoning.isSummon(npc)",
        "!npc.getDef().isAttackable()",
        "npc.withinRange(center, radius)",
        "snapshotViewOrder",
    ))
    for event_path in (
        "server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java",
        "server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java",
        "server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java",
    ):
        event = read(event_path)
        require_all(event, (
            "getDragonBreathArmorMaxPoisonPower()",
            "getDragonBreathArmorProcChance()",
            "getDragonBreathArmorAppliedPoisonPower()",
            "getDragonBreathArmorProcKey()",
            '"king_black".equals(dragonBreathProc)',
            "KingBlackDragonBreathFollowup.tryApply",
            "applyHellsInfernoSplash",
            "CombatEffectUtil.hellsInfernoSplashDamage(primaryDamageDealt)",
            "CombatEffectUtil.findPlayerOwnedNpcSplashTargets(",
            "ElderGreenDragonArmorEffect.applyProc",
        ))
    elder_armor = read("server/src/com/openrsc/server/content/ElderGreenDragonArmorEffect.java")
    require_all(elder_armor, (
        "PROC_CHANCE = 0.60D",
        "MAX_TRUE_DAMAGE = 10",
        "SPLASH_RADIUS = 2",
        "BURN_DAMAGE = 1",
        "BURN_PULSES = 5",
        "findPlayerOwnedNpcSplashTargets",
        "findPlayerOwnedPvpSplashTargets",
        "existing.refresh(source)",
    ))
    assert 'return "elder_green"' not in equipment
    for event_path in (
        "server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java",
        "server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java",
        "server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java",
    ):
        assert '"elder_green".equals(dragonBreathProc)' not in read(event_path)

    legacy_melee = read("server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java")
    modern_melee = read("server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java")
    assert legacy_melee.index("applyWeaponPoison(hitter, target, damage);") \
        < legacy_melee.index("inflictDamage(hitter, target, damage);")
    assert modern_melee.index("applyWeaponPoison(attackerMob, targetMob, damage);") \
        < modern_melee.index("inflictDamage(attackerMob, targetMob, damage);")
    for event_path in (
        "server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java",
        "server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java",
    ):
        require_all(read(event_path), (
            "new CombatEffect(npc, CombatEffect.HELLS_INFERNO)",
            "inflictAuxiliaryMagicDamage(player, npc, splashDamage)",
        ))
    reciprocal = read("server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java")
    require_all(reciprocal, (
        "applyHellsInfernoFollowup(player, target,",
        "applyHellsInfernoPvpSplash(player, (Player) target, primaryDamageDealt)",
        "CombatEffectUtil.findPlayerOwnedPvpSplashTargets(",
        "new CombatEffect(target, CombatEffect.HELLS_INFERNO)",
    ))

    effect = read("server/src/com/openrsc/server/model/entity/update/CombatEffect.java")
    catalog = read("Client_Base/src/orsc/graphics/two/CombatEffectAnimationCatalog.java")
    require_all(effect, ('case "balrog":', "return BALROG_MAGIC;"))
    require_all(catalog, (
        'define(definitions, 46, "fire-orb-explosion", ON_ENTITY,',
        '"fire-orb-explosion/Fire Orb Explosion(48x48).png", 18, 1, 0, 18, 64);',
        '"explosions/Explosion VFX 1(32x32).png", 10, 1, 0, 9, 64);',
    ))
    client = read("Client_Base/src/orsc/mudclient.java")
    require_all(client, (
        "if (effectType == COMBAT_EFFECT_HELLS_INFERNO)",
        "return 336;",
        "return Math.max(1, (baseSize * 3) / 2);",
        "getCombatEffectTargetScreenXOffset(character,",
        "useCombatBFrames(character.direction) ? -correction : correction",
    ))
    asset_loader = read("Client_Base/src/orsc/ClientExternalAssetLoader.java")
    assert "Math.min(64, maxTargetSize)" in asset_loader
    assert png_size(
        "dev/myworld/assets/animations/on-entity/fire-orb-explosion/Fire Orb Explosion(48x48).png"
    ) == (864, 48)
    assert png_size(
        "dev/myworld/assets/animations/on-entity/explosions/Explosion VFX 1(32x32).png"
    ) == (320, 32)

    print("PASS: KBD tier 10, Balrog tier 11 AOE, Elder Breath AOE/burn, and Balrog impact validated")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
