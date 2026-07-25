#!/usr/bin/env python3
"""Validate god artifact altar reward wiring."""

import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def load_items(path: Path) -> dict[int, dict]:
    return {int(entry["id"]): entry for entry in json.loads(path.read_text(encoding="utf-8"))["items"]}


def main() -> None:
    artifacts = (ROOT / "server/src/com/openrsc/server/content/GodArtifacts.java").read_text(encoding="utf-8")
    prayer = (ROOT / "server/plugins/com/openrsc/server/plugins/authentic/skills/prayer/Prayer.java").read_text(encoding="utf-8")
    equipment = (ROOT / "server/src/com/openrsc/server/model/container/Equipment.java").read_text(encoding="utf-8")
    skill_guide = (ROOT / "Client_Base/src/com/openrsc/interfaces/misc/SkillGuideInterface.java").read_text(encoding="utf-8")
    myworld_items = load_items(ROOT / "server/conf/server/defs/ItemDefsMyWorld.json")
    plan = (ROOT / "docs/myworld/in-progress-work-plans/god-relic-reward-plan.md").read_text(encoding="utf-8")

    for snippet in (
        "public static final int REQUIRED_PRAYER_LEVEL = 80;",
        "public static final int REQUIRED_DEVOTION = 800;",
        "public static final int DEVOTION_COST = 400;",
        'private static final String CLAIMED_CACHE_PREFIX = "god_artifact_claimed_";',
        "player.getPrayerBook() != godLine",
        "Devotion.getDevotionLevel(player, godLine) < REQUIRED_DEVOTION",
        "getUnclaimedArtifacts(player, godLine).isEmpty()",
        "DataConversions.random(0, artifacts.size() - 1)",
        "player.getCache().store(getClaimedCacheKey(godLine, itemId), true);",
        "Devotion.removeDevotionLevels(player, godLine, DEVOTION_COST);",
        'multi(player, "Yes, I desire it.", "No, I am not worthy.")',
        'say(player, "Yes, I desire it.");',
        'say(player, "No, I am not worthy.");',
        "player.getCarriedItems().getInventory().canHold(artifact)",
    ):
        require(snippet in artifacts, f"GodArtifacts missing snippet: {snippet}")

    for snippet in (
        "final PrayerCatalog.GodLine currentGodLine = player.getPrayerBook();",
        "if (currentGodLine == godLine) {",
        "GodArtifacts.offerIfEligible(player, godLine);",
        "player.setPrayerBook(godLine);",
        "PrayerCatalog.getGodLineForAltar(object.getID(), object.getX(), object.getY())",
    ):
        require(snippet in prayer, f"Prayer altar artifact integration missing: {snippet}")

    for item in (
        "ItemId.SARADOMIN_CAPE.id()",
        "ItemId.STAFF_OF_SARADOMIN.id()",
        "ItemId.SARADOMIN_MACE.id()",
        "ItemId.ZAMORAK_CAPE.id()",
        "ItemId.STAFF_OF_ZAMORAK.id()",
        "ItemId.ZAMORAK_MACE.id()",
        "ItemId.GUTHIX_CAPE.id()",
        "ItemId.STAFF_OF_GUTHIX.id()",
        "ItemId.GUTHIX_MACE.id()",
    ):
        require(item in artifacts, f"Artifact pool missing {item}")

    for stale_hint in (
        "low-Worship",
        "low-devotion",
        "You need 800 devotion",
        "You need to have a Worship level of 80 to request",
    ):
        require(stale_hint not in artifacts, f"Hidden artifact trigger should not expose ordinary failure hint: {stale_hint}")

    require("GOD_MACE_DEVOTION_REQUIREMENT" not in equipment, "God artifacts should not require 800 devotion to wield")

    expected_requirements = {
        1213: (5, 80),
        1214: (5, 80),
        1215: (5, 80),
        1216: (6, 80),
        1217: (6, 80),
        1218: (6, 80),
    }
    for item_id, (skill_id, level) in expected_requirements.items():
        item = myworld_items[item_id]
        require(item["requiredSkillID"] == skill_id, f"Item {item_id} should keep required skill {skill_id}")
        require(item["requiredLevel"] == level, f"Item {item_id} should require level {level}")

    for snippet in (
        'new SkillMenuItem(1214, "80", "Saradomin Cape - requires Saradomin worship")',
        'new SkillMenuItem(1218, "80", "Staff of Saradomin - requires Saradomin worship")',
        'new SkillMenuItem(1213, "80", "Zamorak Cape - requires Zamorak worship")',
        'new SkillMenuItem(1216, "80", "Staff of Zamorak - requires Zamorak worship")',
        'new SkillMenuItem(1215, "80", "Guthix Cape - requires Guthix worship")',
        'new SkillMenuItem(1217, "80", "Staff of Guthix - requires Guthix worship")',
    ):
        require(snippet in skill_guide, f"Skill guide missing updated artifact requirement: {snippet}")

    for snippet in (
        "Initial implementation checkpoint",
        "the first live artifact pool awards the existing cape, staff, and mace for each god",
        "future artifact expansion",
    ):
        require(snippet in plan, f"Artifact plan should document current implementation scope: {snippet}")

    print("PASS: god artifact altar reward wiring validated")


if __name__ == "__main__":
    main()
