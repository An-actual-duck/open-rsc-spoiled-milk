#!/usr/bin/env python3
"""Compile and validate the focused C09 Purify effect and poison boundary."""

from __future__ import annotations

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server/src/com/openrsc/server"
CLERIC = SERVER / "content/cleric"
RUNTIME = CLERIC / "runtime/ClericSupportCasting.java"
MOB = SERVER / "model/entity/Mob.java"
POISON_EVENT = SERVER / "event/rsc/impl/PoisonEvent.java"
POISON_REDUCTION = SERVER / "content/PoisonPowerReduction.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/cleric-spellbook-implementation-plan.md"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


FIXTURE = r"""
package com.openrsc.server.content.cleric;

import com.openrsc.server.content.PoisonPowerReduction;

import java.util.Arrays;

public final class ClericPurifyEffectFixture {
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

	private static void rankChecks() {
		int[] expected = {10, 20, 30, 40};
		for (int rank = 1; rank <= expected.length; rank++) {
			check(ClericPurifyEffect.reductionForRank(rank) == expected[rank - 1],
				"Purify reduction drift at rank " + rank);
		}
		reject(() -> ClericPurifyEffect.reductionForRank(0), "rank zero");
		reject(() -> ClericPurifyEffect.reductionForRank(5), "rank five");
	}

	private static void powerChecks() {
		ClericPurifyEffect.Plan absent = ClericPurifyEffect.plan(0, 1);
		check(!absent.isUseful() && absent.getRemainingPoisonPower() == 0,
			"unpoisoned recipients must remain ineffective");

		ClericPurifyEffect.Plan partial = ClericPurifyEffect.plan(80, 1);
		check(partial.isUseful() && partial.getReduction() == 10
			&& partial.getRemainingPoisonPower() == 70,
			"rank-I partial reduction drift");
		check(!PoisonPowerReduction.shouldCure(partial.getRemainingPoisonPower()),
			"severe poison must continue after a partial reduction");

		ClericPurifyEffect.Plan exactThreshold = ClericPurifyEffect.plan(20, 1);
		check(exactThreshold.getRemainingPoisonPower() == 10
			&& !PoisonPowerReduction.shouldCure(exactThreshold.getRemainingPoisonPower()),
			"poison power exactly ten must continue");
		ClericPurifyEffect.Plan belowThreshold = ClericPurifyEffect.plan(19, 1);
		check(belowThreshold.getRemainingPoisonPower() == 9
			&& PoisonPowerReduction.shouldCure(belowThreshold.getRemainingPoisonPower()),
			"poison power below ten must cure");
		check(ClericPurifyEffect.plan(35, 4).getRemainingPoisonPower() == 0,
			"large Purify reductions must saturate at zero");
		check(ClericPurifyEffect.plan(Integer.MAX_VALUE, 4).getRemainingPoisonPower()
			== Integer.MAX_VALUE - 40, "maximum poison arithmetic overflowed");

		int repeated = 80;
		for (int cast = 0; cast < 4; cast++) {
			repeated = ClericPurifyEffect.plan(repeated, 1).getRemainingPoisonPower();
		}
		check(repeated == 40, "repeated Purify casts must reduce current power each time");
		reject(() -> ClericPurifyEffect.plan(-1, 1), "negative current power");
		reject(() -> PoisonPowerReduction.remainingPower(10, 0), "zero reduction");
		reject(() -> PoisonPowerReduction.shouldCure(-1), "negative cure input");
	}

	private static void transactionChecks() {
		final int[] powers = {0, 80, 19};
		final int[] commits = {0};
		final int[] spends = {0};
		ClericCastTransaction.PreparedApplication[] prepared =
			new ClericCastTransaction.PreparedApplication[powers.length];
		for (int index = 0; index < powers.length; index++) {
			final int recipient = index;
			final ClericPurifyEffect.Plan plan = ClericPurifyEffect.plan(powers[index], 1);
			prepared[index] = new ClericCastTransaction.PreparedApplication() {
				public boolean isUseful() { return plan.isUseful(); }
				public void commit() {
					commits[0]++;
					powers[recipient] = plan.getRemainingPoisonPower();
				}
			};
		}
		ClericCastTransaction.Result mixed = ClericCastTransaction.execute(
			Arrays.asList(prepared), applicationCommit -> {
				spends[0]++;
				applicationCommit.run();
				return true;
			});
		check(mixed.getOutcome() == ClericCastTransaction.Outcome.SUCCESS
			&& mixed.getAppliedRecipientCount() == 2,
			"mixed Purify cast must count only poisoned recipients");
		check(spends[0] == 1 && commits[0] == 2
			&& powers[0] == 0 && powers[1] == 70 && powers[2] == 9,
			"mixed Purify cast must spend once and apply every useful reduction");

		final int[] noPoisonSpends = {0};
		ClericCastTransaction.Result ineffective = ClericCastTransaction.execute(
			Arrays.asList(preparedApplication(false), preparedApplication(false)),
			applicationCommit -> {
				noPoisonSpends[0]++;
				return false;
			});
		check(ineffective.getOutcome() == ClericCastTransaction.Outcome.NO_USEFUL_APPLICATION
			&& noPoisonSpends[0] == 0,
			"all-unpoisoned Purify must not enter the sigil boundary");

		final int[] blockedPower = {80};
		ClericCastTransaction.Result missingSigil = ClericCastTransaction.execute(
			Arrays.asList(new ClericCastTransaction.PreparedApplication() {
				public boolean isUseful() { return true; }
				public void commit() { blockedPower[0] = 70; }
			}), applicationCommit -> false);
		check(missingSigil.getOutcome() == ClericCastTransaction.Outcome.INSUFFICIENT_RESOURCES
			&& blockedPower[0] == 80,
			"missing sigils must leave poison unchanged");
	}

	private static ClericCastTransaction.PreparedApplication preparedApplication(
			final boolean useful) {
		return new ClericCastTransaction.PreparedApplication() {
			public boolean isUseful() { return useful; }
			public void commit() { throw new AssertionError("unexpected commit"); }
		};
	}

	public static void main(String[] args) {
		rankChecks();
		powerChecks();
		transactionChecks();
	}
}
"""


def validate_runtime_wiring() -> None:
    runtime = RUNTIME.read_text(encoding="utf-8")
    mob = MOB.read_text(encoding="utf-8")
    poison_event = POISON_EVENT.read_text(encoding="utf-8")
    plan = PLAN.read_text(encoding="utf-8")

    for snippet in (
        "definition.getId() != ClericSpellId.UNIFY",
        "definition.getId() != ClericSpellId.PURIFY",
        "definition.resolveEffectRank(",
        "caster.getCarriedItems().getEquipment().getHolyPower()",
        "preparePurify(target, purifyRank)",
        "recipient.getCurrentPoisonPower()",
        "new PurifyApplication(recipient, plan.getReduction())",
        "recipient.reduceCurrentPoisonPower(reduction)",
        "ClericCastTransaction.execute(",
        "ClericSupportTargeting.resolve(",
    ):
        require(snippet in runtime, f"Purify runtime wiring missing: {snippet}")
    require("ClericEffectRegistry" not in runtime,
            "instant Purify must not create a transient Cleric status")
    require("addExperience" not in runtime and "Skill.PRAYER" not in runtime,
            "Purify must not award Worship XP")

    reduction_method = mob.split(
        "public boolean reduceCurrentPoisonPower(final int reduction)", 1
    )[1].split("// part of NPC poison feature", 1)[0]
    for snippet in (
        'getAttribute("poisonEvent", null)',
        "PoisonPowerReduction.remainingPower(",
        "PoisonPowerReduction.shouldCure(remainingPower)",
        "curePoison();",
        "poisonEvent.setPoisonPower(remainingPower);",
        "setPoisonDamage(remainingPower);",
        'getCache().set("poisoned", remainingPower)',
    ):
        require(snippet in reduction_method,
                f"shared poison reduction omits state boundary: {snippet}")
    for forbidden in ("setPoisonMaxPower", "poisoned_max", "setPoisonOwnerId"):
        require(forbidden not in reduction_method,
                f"partial Purify must preserve poison accumulation/source: {forbidden}")
    require("PoisonPowerReduction.shouldCure(poisonPower)" in poison_event,
            "normal poison ticks and Purify do not share the cure threshold")
    require("if (poisonPower < 10)" not in poison_event,
            "PoisonEvent retained a second cure-threshold literal")
    require("1. Purify;" in plan and "Each branch owns its pure calculations" in plan,
            "C09 Purify branch contract is missing from the implementation plan")


def run_compiled_fixture() -> None:
    sources = (
        POISON_REDUCTION,
        CLERIC / "ClericCastTransaction.java",
        CLERIC / "ClericPurifyEffect.java",
    )
    with tempfile.TemporaryDirectory(prefix="cleric-purify-") as temporary:
        temp = Path(temporary)
        fixture = temp / "com/openrsc/server/content/cleric/ClericPurifyEffectFixture.java"
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
             "com.openrsc.server.content.cleric.ClericPurifyEffectFixture"],
            cwd=ROOT,
            check=True,
        )


def main() -> None:
    validate_runtime_wiring()
    run_compiled_fixture()
    print("Cleric C09 Purify effect checks passed")


if __name__ == "__main__":
    main()
