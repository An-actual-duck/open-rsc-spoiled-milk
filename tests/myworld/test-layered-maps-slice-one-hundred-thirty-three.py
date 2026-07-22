#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
RSC = ROOT / "server/src/com/openrsc/server/event/rsc"
STATE = RSC / "GameTickEventRestorationState.java"
REQUIREMENT = RSC / "GameTickEventRestorationRequirement.java"
DECISION = RSC / "GameTickEventRestorationTargetDecision.java"
ATOMIC_CONTRACT = RSC / "GameTickEventRestorationAtomicRevalidationContract.java"
REQUEST = RSC / "GameTickEventRestorationTargetRevalidationRequest.java"
REVALIDATION = RSC / "GameTickEventRestorationTargetRevalidation.java"
INTENT = RSC / "GameTickEventRestorationMutationIntent.java"
ROLLBACK = RSC / "GameTickEventRestorationTransientRollbackSnapshot.java"
TRANSACTION = RSC / "GameTickEventRestorationCollisionTransactionContract.java"
PLANNER = RSC / "GameTickEventRestorationCollisionFootprintPlanner.java"
APPLICATION = RSC / "GameTickEventRestorationCollisionApplicationContract.java"
COLLISION_FLAG = ROOT / (
    "server/src/com/openrsc/server/util/rsc/CollisionFlag.java"
)
POLICY = ROOT / (
    "server/src/com/openrsc/server/util/rsc/"
    "LegacyObjectProjectileCollisionPolicy.java"
)
WORLD = ROOT / "server/src/com/openrsc/server/model/world/World.java"
STORE = RSC / "handler/GameTickEventStore.java"
HANDLER = RSC / "handler/GameEventHandler.java"
REGION_MANAGER = ROOT / (
    "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


FIXTURE = r'''
package com.openrsc.server.event.rsc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionApplicationContract.CurrentTileState;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionApplicationContract.Evaluation;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionApplicationContract.ProjectedTileState;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionApplicationContract.Reason;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionApplicationContract.Request;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionFootprintPlanner.ConstructorState;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionFootprintPlanner.Definition;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionFootprintPlanner.Operation;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionFootprintPlanner.Result;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionFootprintPlanner.WorldBounds;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCollisionTransactionContract.PackedRegionCoordinate;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTransientRollbackSnapshot.CollisionContribution;

public final class RestorationCollisionApplicationFixture {
    private static final String[] ALLOWLIST = {"gate", "chair"};
    private static final WorldBounds BOUNDS = WorldBounds.of(1008, 4032);

    public static void main(String[] args) {
        retainsExactSixBitContributionCounts();
        projectsRegisterAndUnregisterWithoutMutation();
        refusesUnderflowAndOverflow();
        refusesEveryCoverageAndFreshnessMismatch();
        acceptsCollisionlessAnchorAndKeepsResultsInert();
    }

    private static void retainsExactSixBitContributionCounts() {
        int[] counts = {2, 0, 3, 0, 4, 5};
        CollisionContribution contribution = CollisionContribution.ofCounts(
            10, 20, 0, counts, 0);
        counts[0] = 99;
        check(contribution.getDynamicCollisionMask() == 53
                && contribution.getDynamicCollisionCount(0) == 2
                && contribution.getDynamicCollisionCount(2) == 3
                && contribution.getDynamicCollisionCount(4) == 4
                && contribution.getDynamicCollisionCount(5) == 5,
            "all six collision counters survive independently of the OR mask");
        int[] detached = contribution.getDynamicCollisionCounts();
        detached[2] = 99;
        check(contribution.getDynamicCollisionCount(2) == 3,
            "collision contribution counts are defensively copied");
    }

    private static void projectsRegisterAndUnregisterWithoutMutation() {
        Result register = directionalPlan(Operation.REGISTER);
        List<CurrentTileState> beforeRegister = currentFor(register, 3, false);
        Evaluation added = evaluate(
            register, Operation.REGISTER, true, true,
            register.getRequiredRegions(), beforeRegister);
        check(added.isProjectedPostStateAvailable()
                && added.getProjectedTileCount() == 3,
            "register projection covers every exact contribution tile");
        for (int index = 0; index < register.getContributionTileCount(); index++) {
            CollisionContribution contribution =
                register.getContributions().get(index);
            ProjectedTileState projected = added.getProjectedTiles().get(index);
            check(projected.getBlockingSceneryCount()
                        == 3 + contribution.getBlockingSceneryCount()
                    && projected.getDynamicProjectileCount()
                        == 3 + contribution.getDynamicProjectileCount(),
                "register adds exact counted contributions");
            for (int bit = 0; bit < 6; bit++) {
                check(projected.getDynamicCollisionCount(bit)
                        == 3 + contribution.getDynamicCollisionCount(bit),
                    "register adds exact dynamic bit count " + bit);
            }
        }

        Result unregister = directionalPlan(Operation.UNREGISTER);
        List<CurrentTileState> beforeUnregister = currentFor(
            unregister, 1, true);
        Evaluation removed = evaluate(
            unregister, Operation.UNREGISTER, true, true,
            unregister.getRequiredRegions(), beforeUnregister);
        check(removed.isProjectedPostStateAvailable(),
            "unregister with sufficient exact counters projects successfully");
        for (ProjectedTileState projected : removed.getProjectedTiles()) {
            check(projected.getBlockingSceneryCount() == 1
                    && projected.getDynamicProjectileCount() == 1,
                "unregister subtracts blocking and projectile counts exactly");
            for (int bit = 0; bit < 6; bit++) {
                check(projected.getDynamicCollisionCount(bit) == 1,
                    "unregister subtracts exact dynamic bit count " + bit);
            }
        }
    }

    private static void refusesUnderflowAndOverflow() {
        Result blocking = GameTickEventRestorationCollisionFootprintPlanner.plan(
            Operation.UNREGISTER, ConstructorState.of(20, 50, 50, 0, 0),
            Definition.scenery(1, 1, 1, "tree", ALLOWLIST),
            false, BOUNDS);
        List<CurrentTileState> zero = Collections.singletonList(
            CurrentTileState.of(50, 50, 0, new int[6], 0));
        expectReason(evaluate(
            blocking, Operation.UNREGISTER, true, true,
            blocking.getRequiredRegions(), zero), Reason.COUNTER_UNDERFLOW);

        Result adding = GameTickEventRestorationCollisionFootprintPlanner.plan(
            Operation.REGISTER, ConstructorState.of(21, 50, 50, 0, 0),
            Definition.scenery(1, 1, 1, "chest", ALLOWLIST),
            false, BOUNDS);
        List<CurrentTileState> maximum = Collections.singletonList(
            CurrentTileState.of(
                50, 50, Integer.MAX_VALUE, new int[6], 0));
        expectReason(evaluate(
            adding, Operation.REGISTER, true, true,
            adding.getRequiredRegions(), maximum), Reason.COUNTER_OVERFLOW);
    }

    private static void refusesEveryCoverageAndFreshnessMismatch() {
        Result footprint = GameTickEventRestorationCollisionFootprintPlanner.plan(
            Operation.REGISTER, ConstructorState.of(30, 47, 10, 0, 0),
            Definition.scenery(1, 2, 1, "tree", ALLOWLIST),
            false, BOUNDS);
        List<CurrentTileState> current = currentFor(footprint, 0, false);
        Result unavailable = GameTickEventRestorationCollisionFootprintPlanner.plan(
            Operation.REGISTER, ConstructorState.of(31, 47, 10, 0, 0),
            null, false, BOUNDS);
        expectReason(evaluate(
            unavailable, Operation.REGISTER, true, true,
            footprint.getRequiredRegions(), current), Reason.FOOTPRINT_UNAVAILABLE);
        expectReason(evaluate(
            footprint, Operation.UNREGISTER, true, true,
            footprint.getRequiredRegions(), current), Reason.OPERATION_MISMATCH);
        expectReason(evaluate(
            footprint, Operation.REGISTER, false, true,
            footprint.getRequiredRegions(), current), Reason.ORDERED_BOUNDARY_MISSING);
        expectReason(evaluate(
            footprint, Operation.REGISTER, true, false,
            footprint.getRequiredRegions(), current),
            Reason.CURRENT_STATE_COMPARISON_STALE);

        List<PackedRegionCoordinate> reversedRegions = new ArrayList<>(
            footprint.getRequiredRegions());
        Collections.reverse(reversedRegions);
        expectReason(evaluate(
            footprint, Operation.REGISTER, true, true,
            reversedRegions, current), Reason.REGION_COVERAGE_NOT_CANONICAL);
        expectReason(evaluate(
            footprint, Operation.REGISTER, true, true,
            Collections.singletonList(footprint.getRequiredRegions().get(0)),
            current), Reason.REGION_COVERAGE_MISMATCH);

        List<CurrentTileState> duplicate = Arrays.asList(
            current.get(0), current.get(0));
        expectReason(evaluate(
            footprint, Operation.REGISTER, true, true,
            footprint.getRequiredRegions(), duplicate), Reason.DUPLICATE_CURRENT_TILE);
        List<CurrentTileState> reversedTiles = new ArrayList<>(current);
        Collections.reverse(reversedTiles);
        expectReason(evaluate(
            footprint, Operation.REGISTER, true, true,
            footprint.getRequiredRegions(), reversedTiles),
            Reason.CURRENT_TILE_ORDER_NOT_CANONICAL);
        expectReason(evaluate(
            footprint, Operation.REGISTER, true, true,
            footprint.getRequiredRegions(),
            Collections.singletonList(current.get(0))),
            Reason.CURRENT_TILE_COVERAGE_MISMATCH);
    }

    private static void acceptsCollisionlessAnchorAndKeepsResultsInert() {
        Result empty = GameTickEventRestorationCollisionFootprintPlanner.plan(
            Operation.REGISTER, ConstructorState.of(1147, 100, 100, 0, 0),
            null, false, BOUNDS);
        Evaluation evaluation = evaluate(
            empty, Operation.REGISTER, true, true,
            empty.getRequiredRegions(), Collections.emptyList());
        check(evaluation.isProjectedPostStateAvailable()
                && evaluation.getProjectedTileCount() == 0,
            "collisionless object retains anchor coverage with empty tile state");
        expectUnsupported(() -> evaluation.getProjectedTiles().clear());
        check(!evaluation.isRuntimeObservationPerformed()
                && !evaluation.isRuntimeBoundaryAcquired()
                && !evaluation.isRuntimeStateRetained()
                && !evaluation.isMutationAuthorized()
                && !evaluation.isMutationPerformed()
                && !evaluation.isRollbackAuthorized()
                && !evaluation.isRollbackPerformed()
                && !evaluation.isExecutableRestoration()
                && !evaluation.isCommitToken()
                && !evaluation.isArrivalGate()
                && !evaluation.isLifecycleAuthority(),
            "projected post-state remains inert");
    }

    private static Result directionalPlan(Operation operation) {
        return GameTickEventRestorationCollisionFootprintPlanner.plan(
            operation, ConstructorState.of(12, 47, 10, 2, 0),
            Definition.scenery(2, 2, 1, "gate", ALLOWLIST),
            false, BOUNDS);
    }

    private static List<CurrentTileState> currentFor(
            Result footprint, int base, boolean includeContribution) {
        List<CurrentTileState> states = new ArrayList<>();
        for (CollisionContribution contribution : footprint.getContributions()) {
            int[] counts = new int[6];
            for (int bit = 0; bit < counts.length; bit++) {
                counts[bit] = base + (includeContribution
                    ? contribution.getDynamicCollisionCount(bit) : 0);
            }
            states.add(CurrentTileState.of(
                contribution.getX(), contribution.getY(),
                base + (includeContribution
                    ? contribution.getBlockingSceneryCount() : 0), counts,
                base + (includeContribution
                    ? contribution.getDynamicProjectileCount() : 0)));
        }
        return states;
    }

    private static Evaluation evaluate(
            Result footprint, Operation operation, boolean boundary,
            boolean fresh, List<PackedRegionCoordinate> regions,
            List<CurrentTileState> current) {
        return GameTickEventRestorationCollisionApplicationContract.evaluate(
            footprint, Request.declare(
                operation, boundary, fresh, regions, current));
    }

    private static void expectReason(Evaluation evaluation, Reason reason) {
        check(evaluation.isRefused() && evaluation.getReason() == reason,
            "expected refusal " + reason + " but got " + evaluation.getReason());
    }

    private static void expectUnsupported(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected immutable result.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
    }
}
'''


class LayeredMapsSliceOneHundredThirtyThreeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-one-hundred-thirty-three-"
        )
        cls.classes = Path(cls.compile_temp.name) / "classes"
        cls.classes.mkdir()
        fixture = Path(cls.compile_temp.name) / (
            "src/com/openrsc/server/event/rsc/"
            "RestorationCollisionApplicationFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        result = subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(COLLISION_FLAG), str(POLICY), str(STATE),
                str(REQUIREMENT), str(DECISION), str(ATOMIC_CONTRACT),
                str(REQUEST), str(REVALIDATION), str(INTENT), str(ROLLBACK),
                str(TRANSACTION), str(PLANNER), str(APPLICATION), str(fixture),
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        if result.returncode != 0:
            raise AssertionError(result.stderr)

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_application_fixture_proves_exact_fail_closed_arithmetic(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.event.rsc."
                "RestorationCollisionApplicationFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_application_contract_has_no_runtime_or_mutation_capability(self):
        source = APPLICATION.read_text(encoding="utf-8")
        for forbidden in (
            "import com.openrsc.server.model", "World world", "Region region",
            "GameObject object", "TileValue tile", "synchronized (",
            "registerGameObject", "unregisterGameObject", "getMutableTile",
            "addDynamicCollision", "removeDynamicCollision",
            "addDynamicProjectileBlock", "removeDynamicProjectileBlock",
        ):
            self.assertNotIn(forbidden, source)
        for required in (
            "for (int bit = 0; bit < dynamicCollisionCounts.length; bit++)",
            "Math.addExact", "COUNTER_UNDERFLOW", "COUNTER_OVERFLOW",
            "isRuntimeBoundaryAcquired() { return false; }",
            "isMutationAuthorized() { return false; }",
        ):
            self.assertIn(required, source)

    def test_application_contract_remains_disconnected_from_runtime(self):
        name = "GameTickEventRestorationCollisionApplicationContract"
        for path in (WORLD, STORE, HANDLER, REGION_MANAGER):
            self.assertNotIn(name, path.read_text(encoding="utf-8"))

    def test_living_plan_records_slice_one_hundred_thirty_three(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 133: Detached collision-application arithmetic", plan
        )
        self.assertIn("six dynamic-collision counters", plan)
        self.assertIn("underflow", plan)


if __name__ == "__main__":
    unittest.main()
