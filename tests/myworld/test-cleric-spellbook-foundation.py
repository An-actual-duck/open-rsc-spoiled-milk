#!/usr/bin/env python3
"""Compile and validate the unreachable Cleric definition foundation."""

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
    require(len(java_files) == 6, "Cleric foundation source inventory drift")
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
            if "ClericSpellCatalog" in text or "content.cleric" in text:
                references.append(str(source.relative_to(ROOT)))
    require(not references, "C01 must remain unreachable; external references: " + ", ".join(references))

    plan = IMPLEMENTATION_PLAN.read_text(encoding="utf-8")
    for unresolved in (
        "introductory unlock/quest",
        "Silver sigil input form",
        "Sigil consumption",
        "PvP rules",
        "Status-HUD priority",
    ):
        require(unresolved in plan, f"implementation stop condition missing: {unresolved}")


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
		check(ClericSpellCatalog.SCHEMA_VERSION == 1, "schema version drift");
		check(ClericSpellCatalog.MAX_LAUNCH_HOLY_POWER == 11, "Holy Power cap drift");

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
		int[][] thresholds = {
			{0, 2, 5}, {0}, {0, 2, 5, 8}, {0, 2, 5, 8},
			{0, 2, 5, 8}, {0, 2, 4, 6}, {0, 4, 8, 11}, {0, 4, 8, 11},
			{0, 4, 8, 11}, {0, 4, 8, 11}, {0, 4, 8, 11}, {0, 4, 8, 11}
		};

		for (int index = 0; index < definitions.size(); index++) {
			ClericSpellDefinition definition = definitions.get(index);
			check(definition.getId() == ids[index], "stable identity order drift at " + index);
			check(definition.getStableCode() == index, "stable code drift at " + index);
			check(keys[index].equals(definition.getStableKey()), "stable key drift at " + index);
			check(names[index].equals(definition.getDisplayName()), "display name drift at " + index);
			check(definition.getAlignment() == alignments[index], "alignment drift at " + index);
			check(definition.getWorshipLevel() == levels[index], "Worship level drift at " + index);
			check(definition.getSpellTier() == tiers[index], "spell tier drift at " + index);
			check(definition.getRadius() == radii[index], "radius drift at " + index);
			check(!definition.affectsCaster(), "launch caster exclusion drift at " + index);
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
			new ClericSpellDefinition(ClericSpellId.MEND, "Bad", ClericAlignment.SARADOMIN,
				1, 1, 2, false, ClericSigilCost.forLaunchTier(1), 0, 0);
		} }, "duplicate thresholds");
		reject(new Action() { public void run() {
			new ClericSpellDefinition(ClericSpellId.MEND, "Bad", ClericAlignment.SARADOMIN,
				1, 1, 2, false, ClericSigilCost.forLaunchTier(1), 1);
		} }, "thresholds without zero");
	}
}
"""


def run_compiled_fixture() -> None:
    sources = sorted(str(path) for path in JAVA_ROOT.glob("*.java"))
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
