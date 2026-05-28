#!/usr/bin/env python3
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "server/src/com/openrsc/server/model/entity/player/PrayerCatalog.java"
PRAYERS = ROOT / "server/src/com/openrsc/server/model/entity/player/Prayers.java"
DOC = ROOT / "docs/myworld/work-items.md"
PRAYER_HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/PrayerHandler.java"
PRAYER_DRAIN = ROOT / "server/src/com/openrsc/server/event/rsc/impl/PrayerDrainEvent.java"
COMBAT_FORMULA = ROOT / "server/src/com/openrsc/server/event/rsc/impl/combat/CombatFormula.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
OBJECT_DEFS = ROOT / "server/conf/server/defs/GameObjectDef.xml"
SCENERY = ROOT / "server/conf/server/defs/locs/SceneryLocs.json"
BLESSING = ROOT / "server/plugins/com/openrsc/server/plugins/custom/myworld/skills/prayer/BlessedStaffs.java"
MAGE_ARENA = ROOT / "server/plugins/com/openrsc/server/plugins/authentic/minigames/mage_arena/MageArena.java"
NPC_DROPS = ROOT / "server/src/com/openrsc/server/constants/NpcDrops.java"


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
    prayer_handler = read(PRAYER_HANDLER)
    prayer_drain = read(PRAYER_DRAIN)
    combat_formula = read(COMBAT_FORMULA)
    player = read(PLAYER)
    object_defs = read(OBJECT_DEFS)
    scenery = read(SCENERY)
    blessing = read(BLESSING)
    mage_arena = read(MAGE_ARENA)
    npc_drops = read(NPC_DROPS)

    combat_costs = parse_int_array(catalog, "COMBAT_TIER_POINT_COSTS")
    combat_effects = parse_int_array(catalog, "COMBAT_TIER_EFFECT_PERCENTS")
    skilling_costs = parse_int_array(catalog, "SKILLING_TIER_POINT_COSTS")
    skilling_effects = parse_int_array(catalog, "SKILLING_TIER_EFFECT_PERCENTS")

    require(combat_costs == [3, 6, 10, 15, 21], "Combat prayer costs drifted")
    require(combat_effects == [5, 10, 15, 20, 25], "Combat prayer effects drifted")
    require(sum(combat_costs) == 55, "All combat tiers should reserve 55 prayer points")
    require(sum(combat_effects) == 75, "Raw combat tiers should total 75 before the cap")
    require("COMBAT_EFFECT_CAP_PERCENT = 60" in catalog, "Combat prayer cap must stay at 60%")

    require(skilling_costs == [2, 8, 20, 35, 55], "Skilling prayer costs drifted")
    require(skilling_effects == [10, 15, 20, 25, 30], "Skilling prayer effects drifted")
    require(sum(skilling_costs) == 120, "All skilling tiers should reserve 120 prayer points")
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

    require('new boolean[PrayerCatalog.PRAYERS_PER_BOOK]' in prayers,
            "Server prayer state should track the current 15-slot god line")
    require("prayers.canActivate(prayerID)" in prayer_handler,
            "Prayer activation should use allocation capacity checks")
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
    require("return getSkills().getMaxStat(Skill.PRAYER.id())" in player
            and "Math.max(getCarriedItems().getEquipment().getPrayer() + Summoning.getPrayerBonus(this) - 1, 0)" in player,
            "Prayer gear should add directly to prayer allocation capacity while worn")
    require("45 prayers" in doc, "Prayer rework doc must call out the 45-prayer catalog")
    require("15-slot current-book UI" in doc, "Prayer rework doc must call out current-book UI wiring")
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
    require("awardGodCape(player, ItemId.SARADOMIN_CAPE.id());" in mage_arena
            and "awardGodCape(player, ItemId.GUTHIX_CAPE.id());" in mage_arena
            and "awardGodCape(player, ItemId.ZAMORAK_CAPE.id());" in mage_arena,
            "Mage Arena should now award the god capes instead of hiding them")
    require("The sacred cape is hidden for now." not in mage_arena,
            "Mage Arena should no longer hide sacred cape rewards")
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
