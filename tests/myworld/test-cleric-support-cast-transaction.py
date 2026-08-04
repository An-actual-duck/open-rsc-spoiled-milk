#!/usr/bin/env python3
"""Compile and validate the C07 party targeting and cast transaction."""

from __future__ import annotations

import subprocess
import tempfile
import textwrap
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CLERIC_ROOT = ROOT / "server/src/com/openrsc/server/content/cleric"
RUNTIME = CLERIC_ROOT / "runtime/ClericSupportCasting.java"
HANDLER = ROOT / "server/src/com/openrsc/server/net/rsc/handlers/InterfaceOptionHandler.java"
RESTORATION = ROOT / "server/src/com/openrsc/server/event/rsc/impl/StatRestorationEvent.java"
NATURAL_REGEN = ROOT / "server/src/com/openrsc/server/event/rsc/impl/NaturalHitsRegeneration.java"
CARRIED_ITEMS = ROOT / "server/src/com/openrsc/server/model/container/CarriedItems.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/cleric-spellbook-implementation-plan.md"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


FIXTURE = r"""
package com.openrsc.server.content.cleric;

import com.openrsc.server.event.rsc.impl.NaturalHitsRegeneration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class ClericSupportCastTransactionFixture {
	private interface Action {
		void run();
	}

	private static final class Candidate {
		final String name;
		final Object world;
		final int level;
		final int x;
		final int y;
		final boolean eligible;
		final boolean lineOfEffect;

		Candidate(String name, Object world, int level, int x, int y,
				boolean eligible, boolean lineOfEffect) {
			this.name = name;
			this.world = world;
			this.level = level;
			this.x = x;
			this.y = y;
			this.eligible = eligible;
			this.lineOfEffect = lineOfEffect;
		}
	}

	private static final ClericSupportTargeting.CandidateView<Candidate> VIEW =
		new ClericSupportTargeting.CandidateView<Candidate>() {
			public boolean isEligibleRecipient(Candidate candidate) { return candidate.eligible; }
			public Object getWorldSpace(Candidate candidate) { return candidate.world; }
			public int getSignedLevel(Candidate candidate) { return candidate.level; }
			public int getX(Candidate candidate) { return candidate.x; }
			public int getY(Candidate candidate) { return candidate.y; }
			public boolean hasLineOfEffect(Candidate caster, Candidate candidate) {
				return candidate.lineOfEffect;
			}
		};

	private static final class Application implements ClericCastTransaction.PreparedApplication {
		final boolean useful;
		int commits;

		Application(boolean useful) {
			this.useful = useful;
		}

		public boolean isUseful() { return useful; }
		public void commit() { commits++; }
	}

	private static final class ResourceBoundary
			implements ClericCastTransaction.ResourceCommitBoundary {
		final boolean resourcesAvailable;
		int attempts;
		int spends;

		ResourceBoundary(boolean resourcesAvailable) {
			this.resourcesAvailable = resourcesAvailable;
		}

		public boolean commit(Runnable applicationCommit) {
			attempts++;
			if (!resourcesAvailable) {
				return false;
			}
			spends++;
			applicationCommit.run();
			return true;
		}
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
		} catch (IllegalArgumentException | IllegalStateException expected) {
			// Expected validation failure.
		}
	}

	private static void targetingChecks() {
		Object global = new Object();
		Candidate caster = new Candidate("caster", global, 1, 100, 100, true, true);
		Candidate edge = new Candidate("edge", global, 1, 102, 102, true, true);
		Candidate outside = new Candidate("outside", global, 1, 103, 100, true, true);
		Candidate otherLevel = new Candidate("level", global, -1, 100, 100, true, true);
		Candidate otherWorld = new Candidate("world", new Object(), 1, 100, 100, true, true);
		Candidate obstructed = new Candidate("wall", global, 1, 101, 100, true, false);
		Candidate unavailable = new Candidate("nonparty-or-pvp", global, 1, 101, 101, false, true);

		List<Candidate> recipients = ClericSupportTargeting.resolve(caster,
			Arrays.asList(caster, edge, edge, outside, otherLevel, otherWorld,
				obstructed, unavailable), 2, VIEW);
		check(recipients.size() == 1 && recipients.get(0) == edge,
			"targeting must enforce self, duplicate, party/PvP, square, world, level, and line checks");
		try {
			recipients.clear();
			throw new AssertionError("recipient snapshot must be immutable");
		} catch (UnsupportedOperationException expected) {
			// Expected immutable result.
		}
		reject(() -> ClericSupportTargeting.resolve(caster,
			Collections.singletonList(edge), 0, VIEW), "zero radius");
	}

	private static void transactionChecks() {
		Application useful = new Application(true);
		Application equalRefresh = new Application(true);
		Application ineffective = new Application(false);
		ResourceBoundary resources = new ResourceBoundary(true);
		ClericCastTransaction.Result success = ClericCastTransaction.execute(
			Arrays.asList(useful, ineffective, equalRefresh), resources);
		check(success.getOutcome() == ClericCastTransaction.Outcome.SUCCESS,
			"partial cast must succeed");
		check(success.getAppliedRecipientCount() == 2,
			"only useful applications must count");
		check(resources.attempts == 1 && resources.spends == 1,
			"one full cast vector must be spent exactly once");
		check(useful.commits == 1 && equalRefresh.commits == 1 && ineffective.commits == 0,
			"useful and equal-refresh applications must commit while no-ops are skipped");

		Application noOp = new Application(false);
		ResourceBoundary untouched = new ResourceBoundary(true);
		ClericCastTransaction.Result empty = ClericCastTransaction.execute(
			Collections.singletonList(noOp), untouched);
		check(empty.getOutcome() == ClericCastTransaction.Outcome.NO_USEFUL_APPLICATION,
			"wholly ineffective cast outcome drift");
		check(untouched.attempts == 0 && noOp.commits == 0,
			"wholly ineffective casts must not touch resources or applications");

		Application blockedByCost = new Application(true);
		ResourceBoundary missing = new ResourceBoundary(false);
		ClericCastTransaction.Result rejected = ClericCastTransaction.execute(
			Collections.singletonList(blockedByCost), missing);
		check(rejected.getOutcome() == ClericCastTransaction.Outcome.INSUFFICIENT_RESOURCES,
			"missing-resource outcome drift");
		check(missing.attempts == 1 && missing.spends == 0 && blockedByCost.commits == 0,
			"resource failure must apply nothing");

		reject(() -> ClericCastTransaction.execute(Collections.singletonList(new Application(true)),
			commit -> true), "successful boundary without application commit");
		reject(() -> ClericCastTransaction.execute(Collections.singletonList(new Application(true)),
			commit -> { commit.run(); return false; }), "failed boundary after application commit");
	}

	private static void unifyChecks() {
		ClericUnifyStepPlanner.Traversability open = (sx, sy, dx, dy) -> true;
		check(ClericUnifyStepPlanner.plan(2, 2, 0, 0, open).isEmpty(),
			"party members already in the two-tile support area must not move");
		List<ClericUnifyStepPlanner.Step> three =
			ClericUnifyStepPlanner.plan(3, 0, 0, 0, open);
		check(three.size() == 1 && three.get(0).getX() == 2,
			"distance-three recipient must move exactly one step into the support area");
		List<ClericUnifyStepPlanner.Step> four =
			ClericUnifyStepPlanner.plan(4, 4, 0, 0, open);
		check(four.size() == 2 && four.get(1).getX() == 2 && four.get(1).getY() == 2,
			"distance-four recipient must move at most two ordinary diagonal steps");
		check(ClericUnifyStepPlanner.plan(5, 0, 0, 0, open).isEmpty(),
			"Unify must not plan movement outside its four-tile area");

		List<ClericUnifyStepPlanner.Step> zigzag = ClericUnifyStepPlanner.plan(
			3, 3, 0, 0, (sx, sy, dx, dy) -> !(sx == 3 && sy == 3 && dx == 2 && dy == 2));
		check(zigzag.size() == 2 && zigzag.get(0).getX() == 2 && zigzag.get(0).getY() == 3,
			"blocked diagonal must allow ordinary cardinal progress");

		List<ClericUnifyStepPlanner.Step> partial = ClericUnifyStepPlanner.plan(
			4, 0, 0, 0, (sx, sy, dx, dy) -> sx == 4);
		check(partial.size() == 1 && partial.get(0).getX() == 3,
			"one safe step must remain a useful partial Unify application");
		check(ClericUnifyStepPlanner.plan(4, 0, 0, 0,
			(sx, sy, dx, dy) -> false).isEmpty(),
			"fully blocked movement must be ineffective");
	}

	private static void regenerationChecks() {
		check(NaturalHitsRegeneration.applySpeedBonus(64_000L, 640L, 0.25D) == 51_200L,
			"rank-IV Respite interval math drift");
		long combined = NaturalHitsRegeneration.applySpeedBonus(
			NaturalHitsRegeneration.applySpeedBonus(64_000L, 640L, 0.10D),
			640L, 1.0D);
		check(combined == 29_091L,
			"independent regeneration factors must compose multiplicatively with bounded rounding");
		check(NaturalHitsRegeneration.applySpeedBonus(640L, 640L, 100.0D) == 640L,
			"regeneration interval must retain the game-tick floor");
		reject(() -> NaturalHitsRegeneration.applySpeedBonus(64_000L, 640L, -0.1D),
			"negative speed bonus");
	}

	public static void main(String[] args) {
		targetingChecks();
		transactionChecks();
		unifyChecks();
		regenerationChecks();
	}
}
"""


def validate_runtime_boundaries() -> None:
    runtime = RUNTIME.read_text(encoding="utf-8")
    handler = HANDLER.read_text(encoding="utf-8")
    restoration = RESTORATION.read_text(encoding="utf-8")
    natural = NATURAL_REGEN.read_text(encoding="utf-8")
    carried_items = CARRIED_ITEMS.read_text(encoding="utf-8")
    plan = PLAN.read_text(encoding="utf-8")

    for snippet in (
        "new ArrayList<PartyPlayer>(party.getPlayers())",
        "candidate.getParty() == party",
        "candidate.isLoggedIn()",
        "!candidate.isUnregistering()",
        "!isPvpContext(candidate)",
        "candidate.getWorldLocation().getWorldSpace()",
        "candidate.getWorldLocation().getCoordinate().getLevel()",
        "PathValidation.checkPath(",
        "ClericUnifyStepPlanner.plan(",
        "PathValidation.checkAdjacent(",
        "recipient.resetPath();",
        "recipient.setLocation(step, false);",
        "caster.getCarriedItems().removeWithStateChange(",
        "ClericSigilItemId.get(\n\t\t\t\t\tmaterial, definition.getAlignment(), true)",
        "applicationCommit.run();",
        "ActionSender.sendInventory(caster);",
    ):
        require(snippet in runtime, f"C07 runtime boundary missing: {snippet}")
    require("definition.getId() != ClericSpellId.UNIFY" in runtime
            and "definition.getId() != ClericSpellId.PURIFY" in runtime,
            "only approved C07 Unify and C09 Purify effects may be reachable")
    require("teleport(" not in runtime and "teleportLayer" not in runtime,
            "Unify must never use teleport movement")
    require("Skill.PRAYER" not in runtime and "addExperience" not in runtime,
            "Cleric casting must not award Worship XP")
    require("player.getDuel().isDuelActive()" in runtime
            and "player.getLocation().inWilderness()" in runtime
            and "player.getConfig().USES_PK_MODE" in runtime,
            "caster and recipient PvP exclusion must cover active PvP contexts")
    require("ClericSupportCasting.isPvpContext(player)" in handler,
            "request handler must preserve the shared C07 PvP boundary")

    atomic_method = carried_items.split(
        "public boolean removeWithStateChange", 1
    )[1].split("\n\t}\n}", 1)[0]
    preflight = atomic_method.index("for (final Item item : items)")
    state_change = atomic_method.index("stateChange.getAsBoolean()")
    removal = atomic_method.index("inventory.remove(item, updateClient)")
    require(preflight < state_change < removal,
            "sigil vector preflight, state commit, and removal ordering drift")

    require("getNaturalHitsInterval((Player) getOwner(), 0.0D)" in restoration,
            "C07 must leave Respite inactive until shared effect state exists")
    require("lastHitRestoration" not in natural and "normalizeLevel" not in natural,
            "regeneration factor math must not reset the clock or grant a tick")
    require("must not reschedule or directly heal" in restoration.lower(),
            "Respite clock integration boundary must remain explicit")
    require("## C07 Completion Record" in plan
            and "C11 — Unify Movement (retired into C07)" in plan,
            "C07 completion and retired duplicate Unify phase are undocumented")


def run_compiled_fixture() -> None:
    sources = [
        CLERIC_ROOT / "ClericSupportTargeting.java",
        CLERIC_ROOT / "ClericCastTransaction.java",
        CLERIC_ROOT / "ClericUnifyStepPlanner.java",
        NATURAL_REGEN,
    ]
    with tempfile.TemporaryDirectory(prefix="cleric-c07-") as temporary:
        temp = Path(temporary)
        fixture = temp / "com/openrsc/server/content/cleric/ClericSupportCastTransactionFixture.java"
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
             "com.openrsc.server.content.cleric.ClericSupportCastTransactionFixture"],
            cwd=ROOT,
            check=True,
        )


def main() -> None:
    validate_runtime_boundaries()
    run_compiled_fixture()
    print("Cleric C07 support targeting and cast transaction checks passed")


if __name__ == "__main__":
    main()
