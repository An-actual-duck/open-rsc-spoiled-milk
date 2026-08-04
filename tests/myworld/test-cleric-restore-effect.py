#!/usr/bin/env python3
"""Compile and validate C09 Restore recovery and runtime boundaries."""

from __future__ import annotations

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLERIC = ROOT / "server/src/com/openrsc/server/content/cleric"
RUNTIME = CLERIC / "runtime/ClericSupportCasting.java"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


FIXTURE = r"""
package com.openrsc.server.content.cleric;

import java.util.Collections;

public final class ClericRestoreEffectFixture {
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

	private static void rankAndRecoveryChecks() {
		int[] percentages = {10, 25, 40, 60};
		for (int rank = 1; rank <= percentages.length; rank++) {
			check(ClericRestoreEffect.recoveryPercentForRank(rank)
				== percentages[rank - 1], "Restore rank drift: " + rank);
		}
		reject(() -> ClericRestoreEffect.recoveryPercentForRank(0), "rank zero");
		reject(() -> ClericRestoreEffect.recoveryPercentForRank(5), "rank five");

		int[] current = {40, 3, 1, 120, 50};
		int[] maximums = {50, 10, 99, 100, 50};
		ClericRestoreEffect.Plan rankOne = ClericRestoreEffect.plan(
			current, maximums, 1, 1);
		int[] restored = rankOne.getRestoredLevels();
		check(rankOne.isUseful() && rankOne.getRestoredSkillCount() == 2,
			"Restore must count each reduced non-Hits skill once");
		check(restored[0] == 45, "ten-percent recovery drift");
		check(restored[1] == 3, "Hits must never be restored");
		check(restored[2] == 11, "fractional recovery must round up");
		check(restored[3] == 120, "an existing boost must not be lowered");
		check(restored[4] == 50, "normal skills must remain unchanged");

		ClericRestoreEffect.Plan rankFour = ClericRestoreEffect.plan(
			new int[] {1, 1, 1}, new int[] {99, 99, 99}, 1, 4);
		check(rankFour.getRestoredLevels()[0] == 61
			&& rankFour.getRestoredLevels()[1] == 1
			&& rankFour.getRestoredLevels()[2] == 61,
			"rank-IV recovery or Hits exclusion drift");

		ClericRestoreEffect.Plan capped = ClericRestoreEffect.plan(
			new int[] {98, 10}, new int[] {99, 10}, 1, 4);
		check(capped.getRestoredLevels()[0] == 99,
			"Restore must cap at the valid active maximum");
		ClericRestoreEffect.Plan noOp = ClericRestoreEffect.plan(
			new int[] {99, 10}, new int[] {99, 10}, 1, 4);
		check(!noOp.isUseful() && noOp.getRestoredSkillCount() == 0,
			"all-normal recipients must be ineffective");

		int[] escaped = rankOne.getRestoredLevels();
		escaped[0] = 0;
		check(rankOne.getRestoredLevels()[0] == 45,
			"Restore plan snapshots must be immutable");
		ClericRestoreEffect.Plan bounded = ClericRestoreEffect.plan(
			new int[] {0, 1}, new int[] {Integer.MAX_VALUE, 1}, 1, 4);
		check(bounded.getRestoredLevels()[0] == 1_288_490_189,
			"Restore percentage arithmetic overflowed");

		reject(() -> ClericRestoreEffect.plan(null, new int[1], 0, 1), "null levels");
		reject(() -> ClericRestoreEffect.plan(new int[1], new int[2], 0, 1), "length drift");
		reject(() -> ClericRestoreEffect.plan(new int[1], new int[1], 1, 1), "bad Hits index");
		reject(() -> ClericRestoreEffect.plan(new int[] {-1}, new int[] {1}, 0, 1),
			"negative current level");
	}

	private static void transactionChecks() {
		final int[] current = {1, 10};
		ClericRestoreEffect.Plan useful = ClericRestoreEffect.plan(
			current, new int[] {99, 10}, 1, 1);
		ClericCastTransaction.PreparedApplication application =
			new ClericCastTransaction.PreparedApplication() {
				public boolean isUseful() { return useful.isUseful(); }
				public void commit() { current[0] = useful.getRestoredLevels()[0]; }
			};
		final int[] spends = {0};
		ClericCastTransaction.Result success = ClericCastTransaction.execute(
			Collections.singletonList(application), commit -> {
				spends[0]++;
				commit.run();
				return true;
			});
		check(success.getOutcome() == ClericCastTransaction.Outcome.SUCCESS
			&& current[0] == 11 && spends[0] == 1,
			"useful Restore must spend and commit once");

		final int[] blocked = {1};
		ClericCastTransaction.Result missing = ClericCastTransaction.execute(
			Collections.singletonList(new ClericCastTransaction.PreparedApplication() {
				public boolean isUseful() { return true; }
				public void commit() { blocked[0] = 11; }
			}), commit -> false);
		check(missing.getOutcome() == ClericCastTransaction.Outcome.INSUFFICIENT_RESOURCES
			&& blocked[0] == 1, "missing sigils must not restore skills");

		final int[] noOpSpends = {0};
		ClericCastTransaction.Result noOp = ClericCastTransaction.execute(
			Collections.singletonList(new ClericCastTransaction.PreparedApplication() {
				public boolean isUseful() { return false; }
				public void commit() { throw new AssertionError("unexpected commit"); }
			}), commit -> { noOpSpends[0]++; return false; });
		check(noOp.getOutcome() == ClericCastTransaction.Outcome.NO_USEFUL_APPLICATION
			&& noOpSpends[0] == 0,
			"all-normal Restore must not enter the sigil boundary");
	}

	public static void main(String[] args) {
		rankAndRecoveryChecks();
		transactionChecks();
	}
}
"""


def validate_runtime_wiring() -> None:
    runtime = RUNTIME.read_text(encoding="utf-8")
    restore_method = runtime.split(
        "private static ClericCastTransaction.PreparedApplication prepareRestore", 1
    )[1].split("private static Item[] createCost", 1)[0]
    application = runtime.split("private static final class RestoreApplication", 1)[1]

    for snippet in (
        "spellId == ClericSpellId.RESTORE",
        "case RESTORE:",
        "prepareRestore(target, effectRank)",
        "getSkills().getSkillsCount()",
        "recipient.getEquipmentAdjustedNormalLevel(skill)",
        "Skill.HITS.id()",
        "ClericRestoreEffect.plan(",
        "new RestoreApplication(recipient, plan.getRestoredLevels())",
    ):
        require(snippet in runtime, f"Restore runtime wiring missing: {snippet}")
    require("ClericEffectRegistry" not in restore_method,
            "instant Restore must not create transient status state")
    require("getEquipmentAdjustedNormalLevel(skill)" in application
            and "setLevel(skill, restored, true, true)" in application,
            "Restore commit must re-cap against active normal levels and update stats")
    require("addExperience" not in restore_method and "getCache()" not in restore_method,
            "Restore must not award XP or persist status/cache state")


def run_compiled_fixture() -> None:
    sources = (
        CLERIC / "ClericCastTransaction.java",
        CLERIC / "ClericRestoreEffect.java",
    )
    with tempfile.TemporaryDirectory(prefix="cleric-restore-") as temporary:
        temp = Path(temporary)
        fixture = temp / "com/openrsc/server/content/cleric/ClericRestoreEffectFixture.java"
        fixture.parent.mkdir(parents=True)
        fixture.write_text(textwrap.dedent(FIXTURE), encoding="utf-8")
        classes = temp / "classes"
        classes.mkdir()
        subprocess.run(
            ["javac", "-d", str(classes), *(str(source) for source in sources), str(fixture)],
            cwd=ROOT,
            check=True,
        )
        subprocess.run(
            ["java", "-cp", str(classes),
             "com.openrsc.server.content.cleric.ClericRestoreEffectFixture"],
            cwd=ROOT,
            check=True,
        )


def main() -> None:
    validate_runtime_wiring()
    run_compiled_fixture()
    print("Cleric C09 Restore effect checks passed")


if __name__ == "__main__":
    main()
