#!/usr/bin/env python3
"""Validate the C05 sigil-production catalog, boundaries, and fixed-point cost."""

from __future__ import annotations

import json
import re
import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLERIC_ROOT = ROOT / "server/src/com/openrsc/server/content/cleric"
BALANCE = ROOT / "server/src/com/openrsc/server/content/DevotionHalfOfferingBalance.java"
PLUGIN = ROOT / "server/plugins/com/openrsc/server/plugins/custom/myworld/skills/blessing/SigilProduction.java"
INVENTORY = ROOT / "server/src/com/openrsc/server/model/container/Inventory.java"
DEVOTION = ROOT / "server/src/com/openrsc/server/content/Devotion.java"
SERVER_ITEMS = ROOT / "server/conf/server/defs/ItemDefsCustom.json"
CLIENT_ITEMS = ROOT / "Client_Base/src/com/openrsc/client/entityhandling/EntityHandler.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/cleric-spellbook-concept.md"


FIXTURE = r"""
package com.openrsc.server.content.cleric;

import com.openrsc.server.content.DevotionHalfOfferingBalance;

public final class ClericSigilProductionFixture {
	private interface Action {
		void run();
	}

	private static void check(boolean condition, String message) {
		if (!condition) {
			throw new AssertionError(message);
		}
	}

	private static void reject(Action action, String message) {
		try {
			action.run();
			throw new AssertionError("Expected rejection: " + message);
		} catch (IllegalArgumentException | ArithmeticException expected) {
			// Expected validation failure.
		}
	}

	public static void main(String[] args) {
		check(ClericSigilProductionCatalog.getUnblessedIdentities().size() == 8,
			"launch production identity count drift");
		check(ClericSigilProductionCatalog.getUnblessedIdentities(ClericSigilMaterial.STONE).size() == 4,
			"stone production identity count drift");
		check(ClericSigilProductionCatalog.getUnblessedIdentities(ClericSigilMaterial.SILVER).size() == 4,
			"silver production identity count drift");

		check(ClericSigilMaterial.STONE.getSourceItemId() == 1299, "stone source drift");
		check(ClericSigilMaterial.STONE.getCraftingLevel() == 1, "stone Crafting level drift");
		check(ClericSigilMaterial.STONE.getBlessingLevel() == 1, "stone Blessing level drift");
		check(ClericSigilMaterial.STONE.getBaseCraftingExperience() == 5, "stone Crafting XP drift");
		check(ClericSigilMaterial.STONE.getBaseBlessingExperience() == 5, "stone Blessing XP drift");
		check(ClericSigilMaterial.SILVER.getSourceItemId() == 383, "silver nugget source drift");
		check(ClericSigilMaterial.SILVER.getCraftingLevel() == 20, "silver Crafting level drift");
		check(ClericSigilMaterial.SILVER.getBlessingLevel() == 16, "silver Blessing level drift");
		check(ClericSigilMaterial.SILVER.getBaseCraftingExperience() == 10, "silver Crafting XP drift");
		check(ClericSigilMaterial.SILVER.getBaseBlessingExperience() == 10, "silver Blessing XP drift");

		for (ClericSigilItemId unblessed : ClericSigilProductionCatalog.getUnblessedIdentities()) {
			check(!unblessed.isBlessed(), "production catalog exposed a blessed input");
			check(ClericSigilProductionCatalog.fromUnblessedItemId(unblessed.getItemId()) == unblessed,
				"unblessed lookup drift " + unblessed);
			ClericSigilItemId blessed = ClericSigilItemId.get(
				unblessed.getMaterial(), unblessed.getAlignment(), true);
			check(blessed.isBlessed(), "blessed output state drift " + unblessed);
			check(blessed.getItemId() == unblessed.getItemId() + 1,
				"blessed output identity drift " + unblessed);
		}
		reject(() -> ClericSigilProductionCatalog.fromUnblessedItemId(3294),
			"blessed item as production input");
		reject(() -> ClericSigilProductionCatalog.fromSourceItemId(235),
			"silver bar as production source");

		check(ClericSigilProductionCatalog.getOutputMultiplier(0, ClericSigilMaterial.STONE) == 0,
			"under-level stone output");
		check(ClericSigilProductionCatalog.getOutputMultiplier(1, ClericSigilMaterial.STONE) == 1,
			"stone base output");
		check(ClericSigilProductionCatalog.getOutputMultiplier(10, ClericSigilMaterial.STONE) == 1,
			"stone premature duplicate");
		check(ClericSigilProductionCatalog.getOutputMultiplier(11, ClericSigilMaterial.STONE) == 2,
			"stone first duplicate");
		check(ClericSigilProductionCatalog.getOutputMultiplier(99, ClericSigilMaterial.STONE) == 10,
			"stone maximum duplicate");
		check(ClericSigilProductionCatalog.getOutputMultiplier(15, ClericSigilMaterial.SILVER) == 0,
			"under-level silver output");
		check(ClericSigilProductionCatalog.getOutputMultiplier(16, ClericSigilMaterial.SILVER) == 1,
			"silver base output");
		check(ClericSigilProductionCatalog.getOutputMultiplier(25, ClericSigilMaterial.SILVER) == 1,
			"silver premature duplicate");
		check(ClericSigilProductionCatalog.getOutputMultiplier(26, ClericSigilMaterial.SILVER) == 2,
			"silver first duplicate");
		check(ClericSigilProductionCatalog.getOutputMultiplier(99, ClericSigilMaterial.SILVER) == 9,
			"silver maximum duplicate");
		check(ClericSigilProductionCatalog.getBlessedOutputCount(30, 10) == 300,
			"full-inventory duplicate output drift");
		reject(() -> ClericSigilProductionCatalog.getBlessedOutputCount(0, 1),
			"zero input count");
		reject(() -> ClericSigilProductionCatalog.getBlessedOutputCount(Integer.MAX_VALUE, 2),
			"output overflow");

		check(ClericSigilProductionCatalog.toInternalExperience(5) == 20,
			"displayed-to-internal XP conversion drift");
		check(ClericSigilProductionCatalog.getDiminishingInternalExperience(5, 1, 1) == 20,
			"1x stone Blessing XP drift");
		check(ClericSigilProductionCatalog.getDiminishingInternalExperience(5, 1, 2) == 30,
			"1.5x stone Blessing XP drift");
		check(ClericSigilProductionCatalog.getDiminishingInternalExperience(5, 1, 3) == 35,
			"1.75x stone Blessing XP drift");
		check(ClericSigilProductionCatalog.getDiminishingInternalExperience(5, 30, 1) == 600,
			"30-input stone Blessing XP drift");
		check(ClericSigilProductionCatalog.getDiminishingInternalExperience(5, 30, 2) == 900,
			"30-input duplicate stone Blessing XP drift");
		check(ClericSigilProductionCatalog.getDiminishingInternalExperience(10, 1, 1) == 40,
			"silver Blessing XP drift");

		check(DevotionHalfOfferingBalance.fromStoredParts(100, 0) == 200,
			"legacy whole-offering interpretation drift");
		check(DevotionHalfOfferingBalance.fromStoredParts(100, 1) == 201,
			"positive half-offering remainder drift");
		check(DevotionHalfOfferingBalance.fromStoredParts(-100, -1) == -201,
			"negative half-offering remainder drift");
		check(DevotionHalfOfferingBalance.getWholeOfferings(-201) == -100,
			"signed whole-offering normalization drift");
		check(DevotionHalfOfferingBalance.getHalfOfferingRemainder(-201) == -1,
			"signed remainder normalization drift");
		check("9.95".equals(DevotionHalfOfferingBalance.format(199)),
			"positive exact display drift");
		check("-0.05".equals(DevotionHalfOfferingBalance.format(-1)),
			"negative exact display drift");
		check("1.50".equals(DevotionHalfOfferingBalance.format(30)),
			"full-batch display drift");
		check(DevotionHalfOfferingBalance.canSpendAboveMinimum(-19999, 1),
			"one sigil at -999.95 must reach the exact floor");
		check(!DevotionHalfOfferingBalance.canSpendAboveMinimum(-19999, 2),
			"multi-sigil cost must not cross the floor");
		check(!DevotionHalfOfferingBalance.canSpendAboveMinimum(-20000, 1),
			"spending may not begin at the floor");
		check(DevotionHalfOfferingBalance.adjust(20000, Long.MAX_VALUE) == 20000,
			"positive arithmetic must remain bounded");
		check(DevotionHalfOfferingBalance.adjust(-20000, Long.MIN_VALUE) == -20000,
			"negative arithmetic must remain bounded");
	}
}
"""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def validate_stackability_parity() -> None:
    entries = {
        int(entry["id"]): entry
        for entry in json.loads(SERVER_ITEMS.read_text(encoding="utf-8"))["items"]
    }
    client = CLIENT_ITEMS.read_text(encoding="utf-8")
    for item_id in range(3293, 3309):
        expected = item_id % 2 == 0
        require(bool(entries[item_id]["isStackable"]) == expected,
                f"server stackability drift for sigil {item_id}")
        block = re.search(
            rf"setCustomItemDefinition\({item_id}, new ItemDef\((?P<body>.*?)\)\);",
            client,
            re.DOTALL,
        )
        require(block is not None, f"client definition missing for sigil {item_id}")
        actual = re.search(r'"external-png:[^"]+",\s*(true|false),', block.group("body"))
        require(actual is not None, f"client stackability field missing for sigil {item_id}")
        require((actual.group(1) == "true") == expected,
                f"client stackability drift for sigil {item_id}")


def validate_runtime_boundaries() -> None:
    plugin = PLUGIN.read_text(encoding="utf-8")
    inventory = INVENTORY.read_text(encoding="utf-8")
    devotion = DEVOTION.read_text(encoding="utf-8")
    plan = PLAN.read_text(encoding="utf-8")

    for snippet in (
        "ProductionSession.TYPE_CRAFTING",
        "ItemId.CHISEL.id()",
        "startbatch(player, makeCount);",
        "ifinterrupted()",
        "replaceExact(source, new Item(output.getItemId(), 1), true)",
        "Skill.CRAFTING.id()",
        "Skill.BLESSING.id()",
        "PrayerCatalog.getGodLineForAltar",
        "inventory.canReplaceAllCatalogStacked(sourceItems, outputItems)",
        "Devotion.canSpendDevotionHalfOfferingUnits(player, chargedGod, inputCount)",
        "Devotion.trySpendDevotionHalfOfferingUnits(",
        "inventory.replaceAllCatalogStacked(sourceItems, outputItems, true)",
    ):
        require(snippet in plugin, f"C05 runtime contract missing: {snippet}")
    require("player.getPrayerBook()" not in plugin,
            "sigil blessing must not depend on the selected Worship alignment")
    require("Skill.PRAYER" not in plugin and "Skill.WORSHIP" not in plugin,
            "sigil production must not award Worship XP")
    for alignment, god in (
        ("SARADOMIN", "SARADOMIN"),
        ("GUTHIX", "GUTHIX"),
        ("ZAMORAK", "ZAMORAK"),
    ):
        require(
            f"alignment == ClericAlignment.{alignment} "
            f"&& altarGod == PrayerCatalog.GodLine.{god}" in plugin,
            f"aligned altar policy drift for {alignment}",
        )
    require("if (alignment == ClericAlignment.NEUTRAL)" in plugin
            and "return altarGod;" in plugin,
            "neutral sigils must charge the recognized altar god")
    require("replaceExactStacked" not in inventory,
            "carving must not retain the obsolete stack-merging replacement path")
    require("synchronized (list)" in inventory and "canReplaceAllCatalogStackedLocked" in inventory,
            "altar inventory conversion must retain its atomic preflight")
    require("HALF_OFFERING_REMAINDER_SUFFIX" in devotion,
            "fractional Devotion remainder persistence is missing")
    require("synchronized (player)" in devotion and "canSpendAboveMinimum" in devotion
            and "|| !stateChange.getAsBoolean()" in devotion,
            "exact Devotion spending must be serialized and floor-checked")
    require("your devotion was refunded" not in plugin.lower(),
            "a rejected altar transaction must avoid transient deduct/refund side effects")
    require("unblessed sigils deliberately remain non-stackable" in plan.lower(),
            "confirmed production-loop stackability decision is undocumented")


def build_and_run_fixture() -> None:
    sources = sorted(str(path) for path in CLERIC_ROOT.glob("*.java"))
    sources.append(str(ROOT / "server/src/com/openrsc/server/content/PoisonPowerReduction.java"))
    with tempfile.TemporaryDirectory(prefix="cleric-sigil-production-") as temporary:
        temp = Path(temporary)
        source = temp / "com/openrsc/server/content/cleric/ClericSigilProductionFixture.java"
        source.parent.mkdir(parents=True)
        source.write_text(textwrap.dedent(FIXTURE), encoding="utf-8")
        classes = temp / "classes"
        classes.mkdir()
        subprocess.run(
            ["javac", "-d", str(classes), *sources, str(BALANCE), str(source)],
            cwd=ROOT,
            check=True,
        )
        subprocess.run(
            ["java", "-cp", str(classes),
             "com.openrsc.server.content.cleric.ClericSigilProductionFixture"],
            cwd=ROOT,
            check=True,
        )


def main() -> None:
    validate_stackability_parity()
    validate_runtime_boundaries()
    build_and_run_fixture()
    print("PASS: Cleric C05 sigil production and exact Devotion checks passed")


if __name__ == "__main__":
    main()
