#!/usr/bin/env python3
"""Validate compact Cleric descriptions and the detailed Worship guide catalog."""

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLIENT = ROOT / "Client_Base/src"
SERVER = ROOT / "server/src/com/openrsc/server"
MUDCLIENT = CLIENT / "orsc/mudclient.java"
GUIDE = CLIENT / "com/openrsc/interfaces/misc/SkillGuideInterface.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def validate_layout_wiring() -> None:
    mudclient = MUDCLIENT.read_text(encoding="utf-8")
    guide = GUIDE.read_text(encoding="utf-8")
    worship_tabs = mudclient.split(
        'skillGuideChosen.equalsIgnoreCase("Worship")', 1
    )[1].split('skillGuideChosen.equalsIgnoreCase("Magic")', 1)[0]
    expected_tabs = (
        "Saradomin", "Zamorak", "Guthix", "Gear", "Devotion", "Spells", "Info"
    )
    positions = [worship_tabs.index(f'add("{tab}")') for tab in expected_tabs]
    require(positions == sorted(positions), "Worship tabs are not in their intended order")
    require(worship_tabs.count("skillGuideChosenTabs.add(") == 7,
            "Worship guide must retain six tabs and add exactly one Spells tab")
    require("if (i == 4)" in guide
            and "mc.skillGuideChosenTabs.size() - i" in guide,
            "large guide tabs must continue wrapping after four entries")
    require("CLERIC_SPELL_GUIDE_VISIBLE_ROWS = 4" in guide
            and "CLERIC_SPELL_GUIDE_ROW_HEIGHT = 55" in guide,
            "Cleric guide rows must remain bounded inside the existing panel")
    require("isClericSpellsTab() ? 4 : 2" in guide,
            "Cleric guide scrollbar needs four trailing rows for its tall layout")
    prayer_guide = guide.split("private void populatePrayerGuide()", 1)[1].split(
        "private void addPrayerLine", 1
    )[0]
    require("else if (curTab == 5)" in prayer_guide
            and "addClericSpellGuideEntries();" in prayer_guide,
            "Worship Spells tab is not populated")
    require("else if (curTab == 6)" in prayer_guide
            and "Worship at a god's altar to switch prayers" in prayer_guide,
            "existing Worship Info tab was not preserved after Spells")
    require("mc.getClericSpellbookDefinitions()" in guide
            and "this.clericSpellbook.snapshot()" in mudclient,
            "Worship guide must consume the validated server-fed Cleric catalog")


FIXTURE = r"""
package test;

import com.openrsc.client.entityhandling.defs.ClericEffectRankDef;
import com.openrsc.client.entityhandling.defs.ClericSpellDef;
import com.openrsc.interfaces.misc.ClericSpellGuideCatalog;
import com.openrsc.server.content.cleric.ClericPurifyEffect;
import com.openrsc.server.content.cleric.ClericRestoreEffect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ClericWorshipGuideFixture {
	private interface Action { void run(); }

	private static void check(boolean condition, String message) {
		if (!condition) throw new AssertionError(message);
	}

	private static void reject(Action action, String message) {
		try {
			action.run();
			throw new AssertionError("Expected rejection: " + message);
		} catch (IllegalArgumentException expected) {
			// Expected.
		}
	}

	private static ClericEffectRankDef rank(int rank, int duration, int kind,
			int counterKind, int counter, int primary, int secondary) {
		return new ClericEffectRankDef(rank, duration, kind, counterKind,
			counter, primary, secondary);
	}

	private static List<ClericEffectRankDef> timed(int kind, int[] primary,
			int[] secondary, int counterKind, int[] counters, int[] durations) {
		ArrayList<ClericEffectRankDef> ranks = new ArrayList<ClericEffectRankDef>();
		for (int index = 0; index < primary.length; index++) {
			ranks.add(rank(index + 1, durations[index], kind, counterKind,
				counters[index], primary[index], secondary[index]));
		}
		return ranks;
	}

	private static ClericSpellDef spell(int code, String key, String name,
			String alignment, int level, int tier, int radius,
			List<ClericEffectRankDef> ranks) {
		return new ClericSpellDef(code, key, name, "Compact.", alignment,
			level, tier, radius, false, 3300, 3300, tier, 3308, tier - 1,
			-1, -1, "", ranks);
	}

	private static int[] repeat(int value, int count) {
		int[] values = new int[count];
		Arrays.fill(values, value);
		return values;
	}

	private static List<ClericSpellDef> definitions() {
		int[] tactical = {30_000, 45_000, 60_000, 90_000};
		ArrayList<ClericSpellDef> spells = new ArrayList<ClericSpellDef>();
		spells.add(spell(0, "cleric.mend", "Mend", "saradomin", 1, 1, 2,
			timed(1, new int[] {1, 2, 3}, repeat(0, 3), 2, repeat(3, 3),
				repeat(10_200, 3))));
		spells.add(spell(1, "cleric.unify", "Unify", "neutral", 3, 1, 4,
			Collections.<ClericEffectRankDef>emptyList()));
		spells.add(spell(2, "cleric.fervor", "Fervor", "zamorak", 5, 1, 2,
			timed(2, new int[] {5, 10, 15, 20}, repeat(1, 4), 0, repeat(0, 4), tactical)));
		spells.add(spell(3, "cleric.purify", "Purify", "guthix", 8, 1, 2,
			Collections.<ClericEffectRankDef>emptyList()));
		spells.add(spell(4, "cleric.restore", "Restore", "guthix", 11, 1, 2,
			Collections.<ClericEffectRankDef>emptyList()));
		spells.add(spell(5, "cleric.ward", "Ward", "saradomin", 14, 1, 2,
			timed(3, repeat(25, 4), repeat(0, 4), 1,
				new int[] {2, 4, 6, 8}, tactical)));
		spells.add(spell(6, "cleric.greater_mend", "Greater Mend", "saradomin", 16, 2, 3,
			timed(1, new int[] {2, 3, 4, 5}, repeat(0, 4), 2, repeat(3, 4),
				repeat(10_200, 4))));
		spells.add(spell(7, "cleric.zeal", "Zeal", "zamorak", 19, 2, 3,
			timed(4, new int[] {5, 8, 11, 15}, repeat(0, 4), 0, repeat(0, 4), tactical)));
		spells.add(spell(8, "cleric.thorns", "Thorns", "guthix", 22, 2, 3,
			timed(5, new int[] {5, 8, 11, 15}, repeat(0, 4), 0, repeat(0, 4), tactical)));
		spells.add(spell(9, "cleric.aegis", "Aegis", "saradomin", 25, 2, 3,
			timed(3, repeat(50, 4), repeat(0, 4), 1,
				new int[] {1, 2, 3, 4}, tactical)));
		spells.add(spell(10, "cleric.rally", "Rally", "zamorak", 28, 2, 3,
			timed(6, repeat(20, 4), new int[] {55, 60, 65, 70}, 0,
				repeat(0, 4), tactical)));
		spells.add(spell(11, "cleric.respite", "Respite", "neutral", 30, 2, 3,
			timed(7, new int[] {10, 15, 20, 25}, repeat(0, 4), 0,
				repeat(0, 4), new int[] {300_000, 600_000, 900_000, 1_200_000})));
		return spells;
	}

	public static void main(String[] args) {
		List<ClericSpellDef> reversed = definitions();
		Collections.reverse(reversed);
		List<ClericSpellGuideCatalog.Entry> entries = ClericSpellGuideCatalog.build(reversed);
		check(entries.size() == 12, "all twelve spells must appear");
		for (int index = 0; index < entries.size(); index++) {
			ClericSpellGuideCatalog.Entry entry = entries.get(index);
			check(entry.getStableCode() == index, "unlock order drift at " + index);
			for (String line : Arrays.asList(entry.getHeader(), entry.getArea(),
					entry.getMechanics(), entry.getHolyPower())) {
				check(line.length() <= ClericSpellGuideCatalog.MAX_LINE_CHARACTERS,
					"guide line exceeds layout bound: " + line);
			}
		}

		ClericSpellGuideCatalog.Entry mend = entries.get(0);
		check(mend.getHeader().equals("Lvl 1  Mend - Saradomin sigil"), "Mend header drift");
		check(mend.getArea().contains("within 2 tiles; caster excluded"), "Mend area drift");
		check(mend.getHolyPower().equals("Holy Power: 1/2/3 Hits per pulse; 3 pulses"),
			"Mend pulse scaling drift");
		check(entries.get(1).getArea().contains("within 4 tiles"), "Unify radius drift");
		check(entries.get(1).getHolyPower().contains("no Holy Power scaling"),
			"Unify fixed scaling drift");
		check(entries.get(5).getHolyPower().equals(
			"Holy Power: 2/4/6/8 charges; 30/45/60/90 sec"), "Ward charges drift");
		check(entries.get(9).getMechanics().contains("50%"), "Aegis reduction drift");
		check(entries.get(10).getHolyPower().contains("55/60/65/70% Hits"),
			"Rally ending thresholds drift");
		check(entries.get(11).getHolyPower().contains("5/10/15/20 min"),
			"Respite duration drift");

		String purifyValues = ClericPurifyEffect.reductionForRank(1) + "/"
			+ ClericPurifyEffect.reductionForRank(2) + "/"
			+ ClericPurifyEffect.reductionForRank(3) + "/"
			+ ClericPurifyEffect.reductionForRank(4);
		check(entries.get(3).getHolyPower().contains(purifyValues),
			"Purify guide drifted from authoritative effect values");
		String restoreValues = ClericRestoreEffect.recoveryPercentForRank(1) + "/"
			+ ClericRestoreEffect.recoveryPercentForRank(2) + "/"
			+ ClericRestoreEffect.recoveryPercentForRank(3) + "/"
			+ ClericRestoreEffect.recoveryPercentForRank(4);
		check(entries.get(4).getHolyPower().contains(restoreValues + "%"),
			"Restore guide drifted from authoritative effect values");

		try {
			entries.clear();
			throw new AssertionError("guide entries must be immutable");
		} catch (UnsupportedOperationException expected) {
			// Expected.
		}
		check(ClericSpellGuideCatalog.build(null).isEmpty(), "null catalog fallback drift");
		reject(new Action() { public void run() {
			ClericSpellGuideCatalog.build(Arrays.asList((ClericSpellDef) null));
		} }, "null definition");
	}
}
"""


def run_compiled_fixture() -> None:
    sources = [
        CLIENT / "com/openrsc/client/entityhandling/defs/ClericEffectRankDef.java",
        CLIENT / "com/openrsc/client/entityhandling/defs/ClericSpellDef.java",
        CLIENT / "com/openrsc/interfaces/misc/ClericSpellGuideCatalog.java",
        SERVER / "content/PoisonPowerReduction.java",
        SERVER / "content/cleric/ClericPurifyEffect.java",
        SERVER / "content/cleric/ClericRestoreEffect.java",
    ]
    with tempfile.TemporaryDirectory(prefix="cleric-worship-guide-") as temporary:
        temp = Path(temporary)
        fixture = temp / "test/ClericWorshipGuideFixture.java"
        fixture.parent.mkdir(parents=True)
        fixture.write_text(textwrap.dedent(FIXTURE), encoding="utf-8")
        classes = temp / "classes"
        classes.mkdir()
        subprocess.run(
            ["javac", "-d", str(classes), *(str(source) for source in sources), str(fixture)],
            check=True,
        )
        subprocess.run(
            ["java", "-cp", str(classes), "test.ClericWorshipGuideFixture"],
            check=True,
        )


def main() -> None:
    validate_layout_wiring()
    run_compiled_fixture()
    print("PASS: compact Cleric descriptions and Worship spell guide validated")


if __name__ == "__main__":
    main()
