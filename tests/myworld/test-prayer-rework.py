#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "server/src/com/openrsc/server/model/entity/player/PrayerCatalog.java"
PRAYERS = ROOT / "server/src/com/openrsc/server/model/entity/player/Prayers.java"
DOC = ROOT / "docs/myworld/in-progress-work-plans/work-items.md"
GOD_RELIC_PLAN = ROOT / "docs/myworld/in-progress-work-plans/god-relic-reward-plan.md"
PRAYER_HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/PrayerHandler.java"
PRAYER_DRAIN = ROOT / "server/src/com/openrsc/server/event/rsc/impl/PrayerDrainEvent.java"
STAT_RESTORATION = ROOT / "server/src/com/openrsc/server/event/rsc/impl/StatRestorationEvent.java"
COMBAT_FORMULA = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
SKILLS = ROOT / "server/src/com/openrsc/server/model/Skills.java"
OBJECT_DEFS = ROOT / "server/conf/server/defs/GameObjectDef.xml"
SCENERY = ROOT / "server/conf/server/defs/locs/SceneryLocs.json"
BLESSING = ROOT / "server/plugins/com/openrsc/server/plugins/custom/myworld/skills/prayer/BlessedStaffs.java"
MAGE_ARENA = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/minigames/mage_arena/MageArena.java"
NPC_DROPS = ROOT / "server/src/com/openrsc/server/constants/NpcDrops.java"
DIVINE_GRACE = ROOT / "server/src/com/openrsc/server/content/DivineGrace.java"
DIVINE_RETRIBUTION = ROOT / "server/src/com/openrsc/server/content/DivineRetribution.java"
CORROSIVE_AURA = ROOT / "server/src/com/openrsc/server/content/CorrosiveAura.java"
PVP_MELEE = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/CombatEvent.java"
PVM_MELEE = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/PvmMeleeEvent.java"
PROJECTILE = ROOT / "server/src/com/openrsc/server/event/rsc/impl/projectile/ProjectileEvent.java"


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def read(path):
    return path.read_text(encoding="utf-8")


def parse_int_array(source, name):
    match = re.search(rf"{name}\s*=\s*\{{([^}}]+)\}}", source)
    require(match, f"Missing {name}")
    return [int(part.strip()) for part in match.group(1).split(",")]


def main():
    catalog = read(CATALOG)
    prayers = read(PRAYERS)
    doc = read(DOC)
    god_relic_plan = read(GOD_RELIC_PLAN)
    prayer_handler = read(PRAYER_HANDLER)
    prayer_drain = read(PRAYER_DRAIN)
    stat_restoration = read(STAT_RESTORATION)
    combat_formula = read(COMBAT_FORMULA)
    player = read(PLAYER)
    skills = read(SKILLS)
    object_defs = read(OBJECT_DEFS)
    scenery = read(SCENERY)
    blessing = read(BLESSING)
    mage_arena = read(MAGE_ARENA)
    npc_drops = read(NPC_DROPS)
    divine_grace = read(DIVINE_GRACE)
    divine_retribution = read(DIVINE_RETRIBUTION)
    corrosive_aura = read(CORROSIVE_AURA)
    pvp_melee = read(PVP_MELEE)
    pvm_melee = read(PVM_MELEE)
    projectile = read(PROJECTILE)

    combat_costs = parse_int_array(catalog, "COMBAT_TIER_POINT_COSTS")
    combat_effects = parse_int_array(catalog, "COMBAT_TIER_EFFECT_PERCENTS")
    skilling_costs = parse_int_array(catalog, "SKILLING_TIER_POINT_COSTS")
    skilling_effects = parse_int_array(catalog, "SKILLING_TIER_EFFECT_PERCENTS")

    require(combat_costs == [3, 6, 15, 29, 49], "Combat prayer costs drifted")
    require(combat_effects == [5, 10, 15, 20, 25], "Combat prayer effects drifted")
    require(sum(combat_costs) == 102, "All combat tiers should reserve 102 prayer points")
    require(sum(combat_effects) == 75, "Raw combat tiers should total 75 before the cap")
    require("COMBAT_EFFECT_CAP_PERCENT = 60" in catalog, "Combat prayer cap must stay at 60%")

    require(skilling_costs == [2, 7, 22, 46, 80], "Skilling prayer costs drifted")
    require(skilling_effects == [10, 15, 20, 25, 30], "Skilling prayer effects drifted")
    require(sum(skilling_costs) == 157, "All skilling tiers should reserve 157 prayer points")
    require(sum(skilling_effects) == 100, "All skilling tiers should total 100% XP bonus")

    required_lines = [
        "addGodLine(definitions, GodLine.ZAMORAK, CombatStyle.MELEE, CombatStyle.RANGED, Skills.SMITHING)",
        "addGodLine(definitions, GodLine.SARADOMIN, CombatStyle.MAGIC, CombatStyle.MELEE, \"ENCHANTING\")",
        "addGodLine(definitions, GodLine.GUTHIX, CombatStyle.RANGED, CombatStyle.MAGIC, Skills.CRAFTING)",
    ]
    for line in required_lines:
        require(line in catalog, f"Missing prayer line mapping: {line}")

    require("case 200:" in catalog and "return GodLine.SARADOMIN;" in catalog, "Monks altar should map to Saradomin")
    require("case 235:" in catalog and "return GodLine.GUTHIX;" in catalog, "Altar of Guthix should map to Guthix")
    require("case 19:" in catalog and "return GodLine.SARADOMIN;" in catalog, "Generic altar should now map cleanly to Saradomin")
    require("isGenericGuthixAltar" not in catalog, "Prayer catalog should no longer hide Guthix altars behind coordinate overrides")
    for object_id in ["144", "296", "625", "939"]:
        require(f"case {object_id}:" in catalog, f"Zamorak altar id {object_id} is not mapped")

    require("PRAYERS_PER_BOOK = 16" in catalog,
            "Server prayer catalog should reserve the special-prayer slot")
    require('new boolean[PrayerCatalog.PRAYERS_PER_BOOK]' in prayers,
            "Server prayer state should track the current 16-slot god line")
    require('"Saving Grace"' in catalog and '"Divine Grace"' not in catalog and '"Divine Retribution"' in catalog,
            "Prayer catalog should include the Saradomin and Zamorak special prayers")
    require('"Corrosive Aura"' in catalog and "GodLine.GUTHIX" in catalog,
            "Prayer catalog should include the Guthix special prayer")
    require("Prayers.DIVINE_GRACE" in divine_grace and "PrayerCatalog.GodLine.SARADOMIN" in divine_grace,
            "Saving Grace should be gated to Saradomin's special slot")
    require("ItemId.SARADOMIN_MACE.id()" in divine_grace,
            "Saving Grace should require the Saradomin mace")
    require("new CombatEffect(attacker, CombatEffect.DIVINE_GRACE)" in divine_grace,
            "Saving Grace proc should display its on-player combat effect")
    require("Prayers.DIVINE_RETRIBUTION" in divine_retribution and "PrayerCatalog.GodLine.ZAMORAK" in divine_retribution,
            "Divine Retribution should be gated to Zamorak's special slot")
    require("ItemId.ZAMORAK_MACE.id()" in divine_retribution,
            "Divine Retribution should require the Zamorak mace")
    require("new CombatEffect(attacker, CombatEffect.DIVINE_RETRIBUTION)" in divine_retribution,
            "Divine Retribution proc should display its on-enemy combat effect")
    require("Prayers.CORROSIVE_AURA" in corrosive_aura and "PrayerCatalog.GodLine.GUTHIX" in corrosive_aura,
            "Corrosive Aura should be gated to Guthix's special slot")
    require("ItemId.GUTHIX_MACE.id()" in corrosive_aura,
            "Corrosive Aura should require the Guthix mace")
    require("MIN_POISON_POWER = 10" in corrosive_aura and "MAX_POISON_POWER = 50" in corrosive_aura
            and "MAX_POWER_HEALTH_FRACTION = 0.30D" in corrosive_aura,
            "Corrosive Aura poison stacks should scale from 10 to 50 below 30% HP")
    require("attacker.applyPoison(poisonPower, attacker.getCurrentPoisonPower() + poisonPower, defender)" in corrosive_aura,
            "Corrosive Aura should add poison stacks every time it procs")
    require("new CombatEffect(attacker, CombatEffect.CORROSIVE_AURA)" in corrosive_aura,
            "Corrosive Aura proc should display its on-enemy combat effect")
    for source, label in ((pvp_melee, "PvP melee"), (pvm_melee, "PvM melee"), (projectile, "projectile")):
        require("CorrosiveAura.apply(" in source, f"Corrosive Aura should run from {label} successful player damage")
    require("prayers.canActivate(prayerID)" in prayer_handler,
            "Prayer activation should use allocation capacity checks")
    require("getActivationBlockMessage(prayerID)" in prayer_handler
            and "getRequiredSpecialPrayerMace" in prayers
            and "getActivationPointCost" in prayers
            and "getRequiredGodCape" in prayers
            and "deactivateUnavailableEquipmentPrayers" in prayers,
            "Special prayers should be blocked by mace and discounted by the matching god cape")
    require("getReqLevel" not in prayer_handler and "Return to a church to recharge" not in prayer_handler,
            "Prayer activation should not use legacy level/drain gating")
    require("player.getConfig().WANT_MYWORLD" in prayer_drain and "stop();" in prayer_drain,
            "Prayer drain event should stop on MyWorld")
    require("sourcePlayer.getConfig().WANT_MYWORLD" in combat_formula,
            "Legacy prayer stat multipliers should be disabled on MyWorld")
    require("applyMyWorldPrayerModifiers" in combat_formula
            and "PrayerCatalog.CombatStyle.MELEE" in combat_formula
            and "PrayerCatalog.CombatStyle.RANGED" in combat_formula
            and "PrayerCatalog.CombatStyle.MAGIC" in combat_formula,
            "Combat damage should apply MyWorld prayer offense/defense modifiers by style")
    require("getOffenseBonusPercent" in prayers and "getDefenseReductionPercent" in prayers,
            "Prayer state should expose style-specific combat modifiers")
    require("getSkillingBonusPercent" in prayers and "getPrayerSkillingBonusPercent" in player,
            "Prayer state should expose skilling XP modifiers")
    require("prayerSkillingBonusPercent" in player and "skillXP = (int) Math.ceil(skillXP * (100.0D + prayerSkillingBonusPercent) / 100.0D)" in player,
            "Player XP gains should apply active skilling prayer bonuses")
    require("getConfig().WANT_MYWORLD\n\t\t\t? getSkills().getLevel(Skill.PRAYER.id())\n\t\t\t: getSkills().getMaxStat(Skill.PRAYER.id())" in player
            and "Math.max(getCarriedItems().getEquipment().getPrayer() + Summoning.getPrayerBonus(this) - 1, 0)" in player,
            "MyWorld prayer allocation should use current Prayer points plus worn Prayer gear")
    require("player.getPrayers().deactivateOverflowingPrayers();" in skills,
            "Prayer drains should deactivate active prayers that no longer fit current points")
    require("} else if (skill != Skill.HITS.id()) {\n\t\t\tmob.tryResyncStatEvent();\n\t\t}" in skills,
            "Subtract-style temporary stat drains should use the normal restoration timer")
    require("skillIndex == Skill.PRAYER.id()" in stat_restoration
            and "!getOwner().getConfig().WANT_MYWORLD" in stat_restoration,
            "MyWorld Prayer should naturally restore with other temporary stat drains")
    require("48 prayers" in doc, "Prayer rework doc must call out the 48-prayer catalog")
    require("16-slot current-book UI" in doc, "Prayer rework doc must call out current-book UI wiring")
    require("server XP multiplier" in doc, "Prayer rework doc must track server XP multiplier replacement")
    require(object_defs.count("<name>Altar of Saradomin</name>") >= 2, "Saradomin prayer altars should be clearly named")
    require(object_defs.count("<name>Altar of Zamorak</name>") >= 4, "Zamorak prayer altars should be clearly named")
    require("<name>Altar of Guthix</name>" in object_defs and "An altar dedicated to Guthix" in object_defs,
            "Guthix altar should be clearly named")
    for coord in ['"X": 261', '"Y": 459', '"X": 412', '"Y": 562', '"X": 638', '"Y": 576', '"X": 588', '"Y": 667', '"X": 369', '"Y": 1381', '"X": 137', '"Y": 1401']:
        require(coord in scenery, f"Missing expected Guthix altar coordinate marker {coord}")
    require('"id": 235' in scenery, "Guthix altar placements should now use object id 235")
    require("You bless the staff at the altar." in blessing and "getBlessedStaffProduct" in blessing,
            "Blessed staff altar interaction should be wired")
    require("ItemId.BLESSED_STAFF.id() + tierIndex" in blessing,
            "Existing blessed staff IDs should remain the Zamorak conversion range")
    require("ItemId.SARADOMIN_BLESSED_STAFF.id() + tierIndex" in blessing
            and "ItemId.GUTHIX_BLESSED_STAFF.id() + tierIndex" in blessing,
            "Blessed staff altar interaction should support all three gods")
    require("Devotion.getDevotionRequirementForResourceCost(resourceCost)" in blessing,
            "Blessed staff altar interaction should be devotion-gated")
    require("PrayerBlessingTransaction.bless(" in blessing
            and "getStaffCraftingXp(item.getCatalogId())" in blessing,
            "Blessed staff altar interaction should grant scaled Worship XP atomically")
    require("Devotion.getBlessingOfferingCostForResourceCost(resourceCost)" in blessing,
            "Blessed staff altar interaction should spend 0.5 devotion per tier")
    require("awardGodCape(" not in mage_arena,
            "Mage Arena should no longer award god capes")
    require("god capes should no longer be Mage Arena rewards" in god_relic_plan,
            "God relic plan should own future god cape rewards")
    require('currentNpcDrops = new DropTable("Priest (9)");' in npc_drops
            and "ItemId.PRIEST_ROBE.id()" in npc_drops
            and "ItemId.PRIEST_GOWN.id()" in npc_drops,
            "Priests should supply the Saradomin robe line")
    require('currentNpcDrops = new DropTable("Monk of Zamorak Level 29 (139)");' in npc_drops
            and "ItemId.ROBE_OF_ZAMORAK_TOP.id()" in npc_drops
            and "ItemId.ROBE_OF_ZAMORAK_BOTTOM.id()" in npc_drops
            and "this.npcDrops.put(NpcId.MONK_OF_ZAMORAK.id(), currentNpcDrops);" in npc_drops
            and "this.npcDrops.put(NpcId.MONK_OF_ZAMORAK_AGGRESSIVE.id(), currentNpcDrops);" in npc_drops,
            "Monks of Zamorak should supply the Zamorak robe line")

    print("prayer rework catalog checks passed")


if __name__ == "__main__":
    main()
