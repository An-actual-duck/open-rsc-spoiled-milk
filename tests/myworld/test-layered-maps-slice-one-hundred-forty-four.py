#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
STATE = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationState.java"
)
SNAPSHOT = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationCurrentStateRecoverySnapshot.java"
)
COORDINATOR = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationRecoveryCoordinatorContract.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


FIXTURE = r'''
package com.openrsc.server.event.rsc;

import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot
        .AuthoredConstructionKind;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot.CallbackExpectation;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot.CallbackKind;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot
        .CollisionContribution;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot.Creation;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot.CurrentScenery;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot
        .ObservedCurrentState;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationCurrentStateRecoverySnapshot.Reason;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class RestorationCurrentStateRecoveryFixture {
    public static void main(String[] args) {
        futureSpawnPreservesTransientAndCountdown();
        futureRemovalUsesTheSameCurrentStateContract();
        timingIdentityAndTargetClassificationFailClosed();
        opaqueOwnerBoundAndIncompleteStateRefuse();
    }

    private static void futureSpawnPreservesTransientAndCountdown() {
        CallbackExpectation callback = callback(
            CallbackKind.SCENERY_SPAWN, 320, 320, 18L,
            null, 0, 7L, 7L, 0, true, true, true,
            AuthoredConstructionKind.HARVESTING_SCENERY);
        CurrentScenery stump = current(
            ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT,
            321, 320, null, 0, 7L, 1,
            true, true, true, true, true,
            Arrays.asList(
                CollisionContribution.of(91, 90, 0, 1, 0),
                CollisionContribution.of(90, 90, 1, 0, 0)));
        Creation creation =
            GameTickEventRestorationCurrentStateRecoverySnapshot.assess(
                callback, stump);
        GameTickEventRestorationCurrentStateRecoverySnapshot snapshot =
            creation.getSnapshot();
        check(creation.isSnapshotAvailable()
                && snapshot.getCallbackKind()
                    == CallbackKind.SCENERY_SPAWN
                && snapshot.getTicksBeforeRun() == 18L
                && snapshot.getCallbackObjectId() == 320
                && snapshot.getCurrentObjectId() == 321
                && snapshot.getCurrentPermanentObjectId() == 320
                && snapshot.getObservedCurrentState()
                    == ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT
                && snapshot.getCollisionContributions().get(0).getX() == 90
                && snapshot.isFutureCallback()
                && snapshot.isCurrentStateKeptSeparateFromDesiredState()
                && snapshot.isCallbackRetainedScheduled(),
            "future spawn preserves the stump and countdown, not desired state");
        check(!snapshot.isRuntimeConsumerConnected()
                && !snapshot.isRegionLoadingPerformed()
                && !snapshot.isMutationPerformed()
                && !snapshot.isCallbackInvoked()
                && !snapshot.isEventCancellation()
                && !snapshot.isEventReschedule()
                && !snapshot.isArrivalGate()
                && !snapshot.isVisibilityReleased()
                && !snapshot.isLifecycleAuthority(),
            "available snapshot remains disconnected and non-authoritative");
    }

    private static void futureRemovalUsesTheSameCurrentStateContract() {
        CallbackExpectation callback = callback(
            CallbackKind.SCENERY_REMOVE, 390, 390, 9L,
            null, 0, 11L, 11L, 0, true, true, true,
            AuthoredConstructionKind.HARVESTING_SCENERY);
        CurrentScenery fire = current(
            ObservedCurrentState.EXACT_RESTORATION_SCENERY_PRESENT,
            390, 390, null, 0, 11L, 1,
            true, true, true, true, true,
            Collections.<CollisionContribution>emptyList());
        Creation creation =
            GameTickEventRestorationCurrentStateRecoverySnapshot.assess(
                callback, fire);
        check(creation.isSnapshotAvailable()
                && creation.getSnapshot().getCallbackKind()
                    == CallbackKind.SCENERY_REMOVE
                && creation.getSnapshot().getCurrentObjectId() == 390
                && creation.getSnapshot().getTicksBeforeRun() == 9L
                && creation.getSnapshot().getCollisionContributionTileCount()
                    == 0,
            "future removal preserves the exact current object symmetrically");

        CurrentScenery wrongConstructor = current(
            ObservedCurrentState.EXACT_RESTORATION_SCENERY_PRESENT,
            391, 390, null, 0, 11L, 1,
            true, true, true, true, true,
            Collections.<CollisionContribution>emptyList());
        check(GameTickEventRestorationCurrentStateRecoverySnapshot.assess(
                callback, wrongConstructor).getReason()
                    == Reason.REMOVAL_CURRENT_CONSTRUCTOR_MISMATCH,
            "removal cannot reconstruct a different current constructor");
    }

    private static void timingIdentityAndTargetClassificationFailClosed() {
        CurrentScenery transientState = current(
            ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT,
            321, 320, null, 0, 7L, 1,
            true, true, true, true, true,
            Collections.<CollisionContribution>emptyList());
        check(GameTickEventRestorationCurrentStateRecoverySnapshot.assess(
                callback(CallbackKind.SCENERY_SPAWN, 320, 320, 0L,
                    null, 0, 7L, 7L, 0, true, true, true,
                    AuthoredConstructionKind.HARVESTING_SCENERY),
                transientState).getReason() == Reason.CALLBACK_NOT_FUTURE,
            "overdue callback cannot use future current-state recovery");
        check(GameTickEventRestorationCurrentStateRecoverySnapshot.assess(
                callback(CallbackKind.SCENERY_SPAWN, 320, 320, 4L,
                    null, 0, 8L, 7L, 0, true, true, true,
                    AuthoredConstructionKind.HARVESTING_SCENERY),
                transientState).getReason()
                    == Reason.PROPOSAL_GENERATION_MISMATCH,
            "proposal generation is exact");
        check(GameTickEventRestorationCurrentStateRecoverySnapshot.assess(
                callback(CallbackKind.SCENERY_SPAWN, 320, 320, 4L,
                    null, 0, 7L, 7L, 1, true, true, true,
                    AuthoredConstructionKind.HARVESTING_SCENERY),
                transientState).getReason() == Reason.EVENT_SEMANTICS_REFUSED,
            "already-run callback refuses");
        CurrentScenery wrongClassification = current(
            ObservedCurrentState.EXACT_RESTORATION_SCENERY_PRESENT,
            321, 320, null, 0, 7L, 1,
            true, true, true, true, true,
            Collections.<CollisionContribution>emptyList());
        check(GameTickEventRestorationCurrentStateRecoverySnapshot.assess(
                callback(CallbackKind.SCENERY_SPAWN, 320, 320, 4L,
                    null, 0, 7L, 7L, 0, true, true, true,
                    AuthoredConstructionKind.HARVESTING_SCENERY),
                wrongClassification).getReason()
                    == Reason.CURRENT_TARGET_CLASSIFICATION_MISMATCH,
            "spawn must preserve a classified authored transient");
    }

    private static void opaqueOwnerBoundAndIncompleteStateRefuse() {
        CallbackExpectation callback = callback(
            CallbackKind.SCENERY_SPAWN, 320, 320, 4L,
            null, 0, 7L, 7L, 0, true, true, true,
            AuthoredConstructionKind.HARVESTING_SCENERY);
        check(GameTickEventRestorationCurrentStateRecoverySnapshot.assess(
                callback,
                current(ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT,
                    321, 320, "player", 0, 7L, 1,
                    true, true, true, true, true,
                    Collections.<CollisionContribution>emptyList())).getReason()
                    == Reason.CURRENT_OWNER_BOUND_STATE_REFUSED,
            "owner-bound current state refuses");
        check(GameTickEventRestorationCurrentStateRecoverySnapshot.assess(
                callback,
                current(ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT,
                    321, 320, null, 1, 7L, 1,
                    true, true, true, true, true,
                    Collections.<CollisionContribution>emptyList())).getReason()
                    == Reason.CURRENT_RUNTIME_ATTRIBUTES_NOT_RESTORABLE,
            "opaque current attributes refuse");
        check(GameTickEventRestorationCurrentStateRecoverySnapshot.assess(
                callback,
                current(ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT,
                    321, 320, null, 0, 7L, 1,
                    true, true, true, true, false,
                    Collections.<CollisionContribution>emptyList())).getReason()
                    == Reason.COLLISION_CONTRIBUTION_INCOMPLETE,
            "incomplete collision capture refuses");
        List<CollisionContribution> duplicate = Arrays.asList(
            CollisionContribution.of(90, 90, 1, 0, 0),
            CollisionContribution.of(90, 90, 0, 1, 0));
        check(GameTickEventRestorationCurrentStateRecoverySnapshot.assess(
                callback,
                current(ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT,
                    321, 320, null, 0, 7L, 1,
                    true, true, true, true, true, duplicate)).getReason()
                    == Reason.DUPLICATE_COLLISION_CONTRIBUTION_TILE,
            "duplicate per-tile collision state refuses");
        check(GameTickEventRestorationCurrentStateRecoverySnapshot.assess(
                callback,
                current(ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT,
                    321, 320, null, 0, 7L, 1,
                    true, true, true, false, true,
                    Collections.<CollisionContribution>emptyList())).getReason()
                    == Reason.COLLISION_BOUNDARY_MISSING,
            "capture outside the collision boundary refuses");
    }

    private static CallbackExpectation callback(
            CallbackKind kind, int objectId, int permanentObjectId,
            long ticksBeforeRun, String owner, int runtimeAttributes,
            long proposalGeneration, long authoredGeneration, int timesRan,
            boolean running, boolean oneShot, boolean continuing,
            AuthoredConstructionKind constructionKind) {
        return CallbackExpectation.declare(
            kind, "scope", 37L, proposalGeneration, 12L, ticksBeforeRun,
            timesRan, running, oneShot, continuing,
            objectId, permanentObjectId, 90, 90, 0, 0,
            owner, runtimeAttributes, authoredGeneration,
            1, 1, 5, constructionKind);
    }

    private static CurrentScenery current(
            ObservedCurrentState observedState,
            int objectId, int permanentObjectId,
            String owner, int runtimeAttributes, long authoredGeneration,
            int exactSlotObjectCount,
            boolean eventBoundary, boolean lifecycleBoundary,
            boolean regionBoundary, boolean collisionBoundary,
            boolean collisionComplete,
            List<CollisionContribution> collision) {
        return CurrentScenery.declare(
            observedState, objectId, permanentObjectId,
            90, 90, 0, 0, owner, runtimeAttributes,
            authoredGeneration, 1, 1, 5,
            AuthoredConstructionKind.HARVESTING_SCENERY,
            exactSlotObjectCount, eventBoundary, lifecycleBoundary,
            regionBoundary, collisionBoundary, collisionComplete, collision);
    }

    private static void check(boolean condition, String message) {
        if (!condition) { throw new AssertionError(message); }
    }
}
'''


class LayeredMapsSliceOneHundredFortyFourTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp_dir = tempfile.TemporaryDirectory(
            prefix="layered-restoration-current-state-"
        )
        cls.classes = Path(cls.temp_dir.name) / "classes"
        cls.classes.mkdir()
        fixture = Path(cls.temp_dir.name) / (
            "src/com/openrsc/server/event/rsc/"
            "RestorationCurrentStateRecoveryFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        result = subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(STATE), str(SNAPSHOT), str(fixture),
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        if result.returncode != 0:
            raise AssertionError(result.stderr)

    @classmethod
    def tearDownClass(cls):
        cls.temp_dir.cleanup()

    def test_current_state_recovery_fixture(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.event.rsc."
                "RestorationCurrentStateRecoveryFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_snapshot_has_no_runtime_or_arrival_capability(self):
        source = SNAPSHOT.read_text(encoding="utf-8")
        for forbidden in (
            "import com.openrsc.server.model",
            "import com.openrsc.server.event.rsc.handler",
            "GameTickEvent event", "RegionManager", "GameObject object",
            "World world", "Region region", "synchronized (",
            ".doRun()", ".run()", ".stop()", "unregisterAccepted",
        ):
            self.assertNotIn(forbidden, source)
        for required in (
            "isRuntimeConsumerConnected() { return false; }",
            "isRuntimeObservationPerformed() { return false; }",
            "isRuntimeHandleRetained() { return false; }",
            "isRegionLoadingPerformed() { return false; }",
            "isMutationAuthorized() { return false; }",
            "isMutationPerformed() { return false; }",
            "isCallbackInvoked() { return false; }",
            "isEventCancellation() { return false; }",
            "isEventReschedule() { return false; }",
            "isArrivalGate() { return false; }",
            "isVisibilityReleased() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, source)

    def test_runtime_consumers_remain_disconnected(self):
        name = "GameTickEventRestorationCurrentStateRecoverySnapshot"
        for path in (ROOT / "server/src").rglob("*.java"):
            if path in (SNAPSHOT, COORDINATOR):
                continue
            self.assertNotIn(name, path.read_text(encoding="utf-8"))

    def test_living_plan_records_slice_one_hundred_forty_four(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 144: Future current-state recovery snapshot", plan
        )
        normalized = " ".join(plan.split())
        self.assertIn("remaining positive scheduler countdown", normalized)
        self.assertIn("Spawn and removal callbacks", normalized)


if __name__ == "__main__":
    unittest.main()
