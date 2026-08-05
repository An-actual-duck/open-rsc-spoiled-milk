#!/usr/bin/env python3
"""Compile and validate the stable Cleric definition foundation."""

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
JAVA_ROOT = ROOT / "server/src/com/openrsc/server/content/cleric"
LEGACY_SPELLS = ROOT / "server/src/com/openrsc/server/constants/Spells.java"
IMPLEMENTATION_PLAN = ROOT / "docs/myworld/in-progress-work-plans/cleric-spellbook-implementation-plan.md"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def validate_source_boundaries() -> None:
    java_files = sorted(JAVA_ROOT.glob("*.java"))
    required_foundation = {
        "ClericAlignment.java",
        "ClericSigilCost.java",
        "ClericSigilMaterial.java",
        "ClericSpellCatalog.java",
        "ClericSpellDefinition.java",
        "ClericSpellId.java",
        "ClericSpellPresentation.java",
    }
    actual = {source.name for source in java_files}
    require(required_foundation <= actual,
            "Cleric C01 source missing: " + ", ".join(sorted(required_foundation - actual)))
    for source in java_files:
        text = source.read_text(encoding="utf-8")
        require(".ordinal()" not in text, f"{source.name} must not persist or transport enum ordinals")

    legacy = LEGACY_SPELLS.read_text(encoding="utf-8")
    require("CLERIC_MEND" not in legacy and "CLERIC_SPELL" not in legacy,
            "Cleric identities leaked into the legacy Magic enum")

    references = []
    for base in (ROOT / "server/src", ROOT / "server/plugins"):
        for source in base.rglob("*.java"):
            if source.parent == JAVA_ROOT:
                continue
            text = source.read_text(encoding="utf-8")
            if any(name in text for name in (
                "ClericSpellCatalog", "ClericSpellDefinition", "ClericSpellId"
            )):
                references.append(str(source.relative_to(ROOT)))
    expected_authorized_references = {
        "server/src/com/openrsc/server/content/cleric/effect/ClericEffectCatalog.java",
        "server/src/com/openrsc/server/content/cleric/effect/ClericEffectRankDefinition.java",
        "server/src/com/openrsc/server/content/cleric/effect/ClericEffectRegistry.java",
        "server/src/com/openrsc/server/content/status/ClericActiveStatusCollector.java",
        "server/src/com/openrsc/server/content/cleric/runtime/ClericDirectCombatRuntime.java",
        "server/src/com/openrsc/server/content/cleric/runtime/ClericSupportCasting.java",
        "server/src/com/openrsc/server/content/cleric/runtime/ClericTimedEffectRuntime.java",
        "server/src/com/openrsc/server/net/rsc/ActionSender.java",
        "server/src/com/openrsc/server/net/rsc/generators/impl/PayloadCustomGenerator.java",
        "server/src/com/openrsc/server/net/rsc/handlers/InterfaceOptionHandler.java",
        "server/src/com/openrsc/server/net/rsc/struct/outgoing/ClericSpellbookStruct.java",
    }
    require(set(references) == expected_authorized_references,
            "Cleric catalog exposure drift: " + ", ".join(references))

    plan = IMPLEMENTATION_PLAN.read_text(encoding="utf-8")
    for settled_c07_rule in (
        "Spend one full cast vector exactly once",
        "if every recipient is\nineffective, spend nothing",
        "queued walking",
        "natural regeneration clock",
    ):
        require(settled_c07_rule in plan,
                f"settled C07 contract missing: {settled_c07_rule}")
    require("C07 remains blocked" not in plan,
            "C07 must not remain blocked after its runtime rules were settled")


FIXTURE = r"""
package com.openrsc.server.content.cleric;

import java.util.Arrays;
import java.util.List;

public final class ClericSpellbookFoundationFixture {
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
		} catch (IllegalArgumentException expected) {
			// Expected validation failure.
		}
	}

	private static void checkArray(int[] actual, int[] expected, String message) {
		check(Arrays.equals(actual, expected), message + ": " + Arrays.toString(actual));
	}

	public static void main(String[] args) {
		check(ClericSpellCatalog.SCHEMA_VERSION == 2, "schema version drift");
		check(ClericSpellCatalog.MAX_LAUNCH_HOLY_POWER == 64, "Holy Power cap drift");

		ClericSpellId[] ids = ClericSpellId.values();
		List<ClericSpellDefinition> definitions = ClericSpellCatalog.getAll();
		check(ids.length == 12 && definitions.size() == 12, "launch roster must contain twelve spells");

		String[] keys = {
			"cleric.mend", "cleric.unify", "cleric.fervor", "cleric.purify",
			"cleric.restore", "cleric.ward", "cleric.greater_mend", "cleric.zeal",
			"cleric.thorns", "cleric.aegis", "cleric.rally", "cleric.respite"
		};
		String[] names = {
			"Mend", "Unify", "Fervor", "Purify", "Restore", "Ward",
			"Greater Mend", "Zeal", "Thorns", "Aegis", "Rally", "Respite"
		};
		ClericAlignment[] alignments = {
			ClericAlignment.SARADOMIN, ClericAlignment.NEUTRAL, ClericAlignment.ZAMORAK,
			ClericAlignment.GUTHIX, ClericAlignment.GUTHIX, ClericAlignment.SARADOMIN,
			ClericAlignment.SARADOMIN, ClericAlignment.ZAMORAK, ClericAlignment.GUTHIX,
			ClericAlignment.SARADOMIN, ClericAlignment.ZAMORAK, ClericAlignment.NEUTRAL
		};
		int[] levels = {1, 3, 5, 8, 11, 14, 16, 19, 22, 25, 28, 30};
		int[] tiers = {1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2};
		int[] radii = {2, 4, 2, 2, 2, 2, 3, 3, 3, 3, 3, 3};
		int[] onEntityAnimations = {67, -1, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77};
		int[][] thresholds = {
			{0, 12, 28}, {0}, {0, 12, 28, 44}, {0, 12, 28, 44},
			{0, 12, 28, 44}, {0, 12, 24, 32}, {0, 24, 44, 64}, {0, 24, 44, 64},
			{0, 24, 44, 64}, {0, 24, 44, 64}, {0, 24, 44, 64}, {0, 24, 44, 64}
		};

		for (int index = 0; index < definitions.size(); index++) {
			ClericSpellDefinition definition = definitions.get(index);
			check(definition.getId() == ids[index], "stable identity order drift at " + index);
			check(definition.getStableCode() == index, "stable code drift at " + index);
			check(keys[index].equals(definition.getStableKey()), "stable key drift at " + index);
			check(names[index].equals(definition.getDisplayName()), "display name drift at " + index);
			check(definition.getEffectDescription() != null
				&& !definition.getEffectDescription().trim().isEmpty(),
				"effect description missing at " + index);
			check(definition.getAlignment() == alignments[index], "alignment drift at " + index);
			check(definition.getWorshipLevel() == levels[index], "Worship level drift at " + index);
			check(definition.getSpellTier() == tiers[index], "spell tier drift at " + index);
			check(definition.getRadius() == radii[index], "radius drift at " + index);
			check(!definition.affectsCaster(), "launch caster exclusion drift at " + index);
			check(definition.getPresentation().getSpellbookIconItemId() >= 0,
				"spellbook icon missing at " + index);
			check(!definition.getPresentation().hasCasterIcon(),
				"unapproved caster icon configured at " + index);
			check(definition.getPresentation().getOnEntityAnimationId()
				== onEntityAnimations[index], "on-entity animation drift at " + index);
			check(definition.getPresentation().hasOnEntityAnimation()
				== (onEntityAnimations[index] >= 0), "on-entity animation presence drift at " + index);
			check(definition.getPresentation().getCasterAnimationId()
				== onEntityAnimations[index], "legacy animation accessor drift at " + index);
			check(definition.getPresentation().hasCasterAnimation()
				== (onEntityAnimations[index] >= 0), "legacy animation predicate drift at " + index);
			checkArray(definition.getHolyPowerThresholds(), thresholds[index],
				"Holy Power thresholds drift at " + index);

			ClericSigilCost cost = definition.getPrimarySigilCost();
			check(cost.getCount(ClericSigilMaterial.STONE) == tiers[index],
				"stone cost drift at " + index);
			check(cost.getCount(ClericSigilMaterial.SILVER) == tiers[index] - 1,
				"silver cost drift at " + index);
			check(cost.getTotalCount() == (tiers[index] == 1 ? 1 : 3),
				"total cost drift at " + index);

			check(ClericSpellCatalog.get(ids[index]) == definition, "identity lookup drift at " + index);
			check(ClericSpellCatalog.fromCode(index) == definition, "code lookup drift at " + index);
			check(ClericSpellCatalog.fromKey(keys[index]) == definition, "key lookup drift at " + index);
			check(ClericSpellId.fromCode(index) == ids[index], "identity code resolution drift at " + index);
			check(ClericSpellId.fromKey(keys[index]) == ids[index], "identity key resolution drift at " + index);

			int[] authored = thresholds[index];
			for (int rankIndex = 0; rankIndex < authored.length; rankIndex++) {
				check(definition.resolveEffectRank(authored[rankIndex]) == rankIndex + 1,
					"rank threshold drift for " + keys[index]);
				if (rankIndex > 0) {
					check(definition.resolveEffectRank(authored[rankIndex] - 1) == rankIndex,
						"rank lower boundary drift for " + keys[index]);
				}
			}
			check(definition.resolveEffectRank(1_000) == authored.length,
				"rank upper clamp drift for " + keys[index]);
		}

		int[] defensiveThresholds = ClericSpellCatalog.get(ClericSpellId.MEND).getHolyPowerThresholds();
		defensiveThresholds[0] = 99;
		check(ClericSpellCatalog.get(ClericSpellId.MEND).getHolyPowerThresholds()[0] == 0,
			"threshold getter exposed mutable state");
		try {
			definitions.clear();
			throw new AssertionError("catalog list must be immutable");
		} catch (UnsupportedOperationException expected) {
			// Expected immutable view.
		}

		check(ClericAlignment.fromKey("neutral") == ClericAlignment.NEUTRAL,
			"neutral alignment lookup drift");
		check(ClericAlignment.fromKey("saradomin") == ClericAlignment.SARADOMIN,
			"Saradomin alignment lookup drift");
		check(ClericAlignment.fromKey("guthix") == ClericAlignment.GUTHIX,
			"Guthix alignment lookup drift");
		check(ClericAlignment.fromKey("zamorak") == ClericAlignment.ZAMORAK,
			"Zamorak alignment lookup drift");

		reject(new Action() { public void run() { ClericSpellCatalog.fromCode(-1); } }, "unknown code");
		reject(new Action() { public void run() { ClericSpellCatalog.fromKey("mend"); } }, "unknown key");
		reject(new Action() { public void run() { ClericSpellId.fromCode(12); } }, "unknown identity code");
		reject(new Action() { public void run() { ClericSpellId.fromKey(null); } }, "null identity key");
		reject(new Action() { public void run() { ClericAlignment.fromKey("SARADOMIN"); } }, "unstable alignment key");
		reject(new Action() { public void run() { ClericSigilCost.forLaunchTier(0); } }, "tier zero cost");
		reject(new Action() { public void run() { ClericSigilCost.forLaunchTier(3); } }, "unsettled tier-three cost");
		reject(new Action() { public void run() {
			ClericSigilCost.forLaunchTier(1).getCount(null);
		} }, "null material");
		reject(new Action() { public void run() {
			ClericSpellCatalog.get(ClericSpellId.MEND).resolveEffectRank(-1);
		} }, "negative Holy Power");
		reject(new Action() { public void run() {
			new ClericSpellDefinition(ClericSpellId.MEND, "Bad", "Bad definition",
				ClericAlignment.SARADOMIN, 1, 1, 2, false,
				ClericSigilCost.forLaunchTier(1), new ClericSpellPresentation(1, -1, -1), 0, 0);
		} }, "duplicate thresholds");
		reject(new Action() { public void run() {
			new ClericSpellDefinition(ClericSpellId.MEND, "Bad", "Bad definition",
				ClericAlignment.SARADOMIN, 1, 1, 2, false,
				ClericSigilCost.forLaunchTier(1), new ClericSpellPresentation(1, -1, -1), 1);
		} }, "thresholds without zero");
	}
}
"""


def run_compiled_fixture() -> None:
    sources = sorted(str(path) for path in JAVA_ROOT.glob("*.java"))
    sources.append(str(ROOT / "server/src/com/openrsc/server/content/PoisonPowerReduction.java"))
    with tempfile.TemporaryDirectory(prefix="cleric-foundation-") as temporary:
        temp = Path(temporary)
        fixture = temp / "com/openrsc/server/content/cleric/ClericSpellbookFoundationFixture.java"
        fixture.parent.mkdir(parents=True)
        fixture.write_text(textwrap.dedent(FIXTURE), encoding="utf-8")
        classes = temp / "classes"
        classes.mkdir()
        subprocess.run(["javac", "-d", str(classes), *sources, str(fixture)], check=True)
        subprocess.run(
            ["java", "-cp", str(classes),
             "com.openrsc.server.content.cleric.ClericSpellbookFoundationFixture"],
            check=True,
        )


def main() -> None:
    validate_source_boundaries()
    run_compiled_fixture()
    print("Cleric spellbook definition foundation checks passed")


if __name__ == "__main__":
    main()
