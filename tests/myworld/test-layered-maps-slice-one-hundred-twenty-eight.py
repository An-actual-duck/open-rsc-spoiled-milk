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
COMPARE_APPLY = RSC / "GameTickEventRestorationCompareAndApplyContract.java"
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
    .GameTickEventRestorationMutationIntent.AuthoredConstructionKind;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTargetDecision.ObservedTargetState;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTargetDecision.TargetOperation;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTransientRollbackSnapshot.Candidate;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTransientRollbackSnapshot.CollisionContribution;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTransientRollbackSnapshot.Creation;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTransientRollbackSnapshot.Outcome;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTransientRollbackSnapshot.Reason;

public final class RestorationTransientRollbackFixture {
    public static void main(String[] args) {
        acceptsClosedTransientAndCollisionState();
        acceptsExplicitCollisionlessState();
        refusesNonExactOrUnrestorableState();
        enforcesBoundsAndImmutability();
    }

    private static void acceptsClosedTransientAndCollisionState() {
        List<CollisionContribution> collision = Arrays.asList(
            CollisionContribution.of(525, 489, 0, 8, 1),
            CollisionContribution.of(524, 489, 1, 0, 1));
        Creation creation =
            GameTickEventRestorationTransientRollbackSnapshot.assess(
                transientIntent(), candidate(
                    4, 1250, 524, 489, 2, 0, "fixture-owner", 0,
                    7L, 10, 10, 22, AuthoredConstructionKind.SCENERY,
                    1, true, true, true, collision));
        check(creation.getOutcome() == Outcome.SNAPSHOT_AVAILABLE
                && creation.getReason() == Reason.SNAPSHOT_AVAILABLE
                && creation.isSnapshotAvailable() && !creation.isRefused(),
            "exact transient state creates a snapshot");
        GameTickEventRestorationTransientRollbackSnapshot snapshot =
            creation.getSnapshot();
        check(snapshot != null && snapshot.getObjectId() == 4
                && snapshot.getPermanentObjectId() == 1250
                && snapshot.getX() == 524 && snapshot.getY() == 489
                && snapshot.getDirection() == 2 && snapshot.getType() == 0
                && snapshot.hasOwner()
                && "fixture-owner".equals(snapshot.getOwner())
                && snapshot.getAuthoredGeneration() == 7L
                && snapshot.getAuthoredPackedRegionX() == 10
                && snapshot.getAuthoredPackedRegionY() == 10
                && snapshot.getAuthoredSourceOrdinal() == 22
                && snapshot.getAuthoredConstructionKind()
                    == AuthoredConstructionKind.SCENERY
                && snapshot.getCollisionContributionTileCount() == 2
                && snapshot.getCollisionContributions().get(0).getX() == 524
                && snapshot.getCollisionContributions().get(1).getX() == 525,
            "snapshot retains and canonicalizes exact closed scalars");
        check(snapshot.getCollisionContributions().get(0)
                    .getBlockingSceneryCount() == 1
                && snapshot.getCollisionContributions().get(1)
                    .getDynamicCollisionMask() == 8
                && snapshot.getCollisionContributions().get(1)
                    .getDynamicProjectileCount() == 1,
            "collision contributions retain every supported counter");
        assertInert(creation, snapshot);
    }

    private static void acceptsExplicitCollisionlessState() {
        Creation creation =
            GameTickEventRestorationTransientRollbackSnapshot.assess(
                transientIntent(), candidate(
                    4, 4, 524, 489, 0, 0, null, 0,
                    7L, 10, 10, 22, AuthoredConstructionKind.SCENERY,
                    1, true, true, true,
                    Collections.<CollisionContribution>emptyList()));
        check(creation.isSnapshotAvailable()
                && creation.getSnapshot().getCollisionContributionTileCount()
                    == 0
                && !creation.getSnapshot().hasOwner(),
            "a complete collisionless contribution is explicit and valid");
    }

    private static void refusesNonExactOrUnrestorableState() {
        Candidate valid = validCandidate();
        expectReason(emptySpawnIntent(), valid,
            Reason.INTENT_NOT_TRANSIENT_REPLACEMENT);
        expectReason(transientIntent(), candidate(
                4, 4, 524, 489, 0, 0, null, 0,
                7L, 10, 10, 22, AuthoredConstructionKind.SCENERY,
                1, false, true, true, collision()),
            Reason.REGION_OBJECT_BOUNDARY_MISSING);
        expectReason(transientIntent(), candidate(
                4, 4, 524, 489, 0, 0, null, 0,
                7L, 10, 10, 22, AuthoredConstructionKind.SCENERY,
                2, true, true, true, collision()),
            Reason.EXACT_SLOT_NOT_SINGLE_OBJECT);
        expectReason(transientIntent(), candidate(
                4, 4, 525, 489, 0, 0, null, 0,
                7L, 10, 10, 22, AuthoredConstructionKind.SCENERY,
                1, true, true, true, collision()),
            Reason.TRANSIENT_COORDINATE_OR_TYPE_MISMATCH);
        expectReason(transientIntent(), candidate(
                4, 4, 524, 489, 0, 0, null, 0,
                8L, 10, 10, 22, AuthoredConstructionKind.SCENERY,
                1, true, true, true, collision()),
            Reason.TRANSIENT_AUTHORED_IDENTITY_MISMATCH);
        expectReason(transientIntent(), candidate(
                4, 4, 524, 489, 0, 0, null, 1,
                7L, 10, 10, 22, AuthoredConstructionKind.SCENERY,
                1, true, true, true, collision()),
            Reason.RUNTIME_ATTRIBUTES_NOT_RESTORABLE);
        expectReason(transientIntent(), candidate(
                4, 4, 524, 489, 0, 0, null, 0,
                7L, 10, 10, 22, AuthoredConstructionKind.SCENERY,
                1, true, false, true, collision()),
            Reason.COLLISION_BOUNDARY_MISSING);
        expectReason(transientIntent(), candidate(
                4, 4, 524, 489, 0, 0, null, 0,
                7L, 10, 10, 22, AuthoredConstructionKind.SCENERY,
                1, true, true, false, collision()),
            Reason.COLLISION_CONTRIBUTION_INCOMPLETE);
        CollisionContribution duplicate =
            CollisionContribution.of(524, 489, 1, 0, 0);
        expectReason(transientIntent(), candidate(
                4, 4, 524, 489, 0, 0, null, 0,
                7L, 10, 10, 22, AuthoredConstructionKind.SCENERY,
                1, true, true, true,
                Arrays.asList(duplicate, duplicate)),
            Reason.DUPLICATE_COLLISION_CONTRIBUTION_TILE);
    }

    private static void enforcesBoundsAndImmutability() {
        expectIllegal(() -> CollisionContribution.of(1, 1, 0, 0, 0));
        expectIllegal(() -> CollisionContribution.of(
            1, 1, 0,
            GameTickEventRestorationTransientRollbackSnapshot
                .MAXIMUM_DYNAMIC_COLLISION_MASK + 1,
            0));
        List<CollisionContribution> mutable = new ArrayList<>(collision());
        Candidate candidate = candidate(
            4, 4, 524, 489, 0, 0, null, 0,
            7L, 10, 10, 22, AuthoredConstructionKind.SCENERY,
            1, true, true, true, mutable);
        mutable.clear();
        check(candidate.getCollisionContributions().size() == 1,
            "candidate defensively copies collision state");
        expectUnsupported(() -> candidate.getCollisionContributions().clear());
        Creation creation =
            GameTickEventRestorationTransientRollbackSnapshot.assess(
                transientIntent(), candidate);
        expectUnsupported(() -> creation.getSnapshot()
            .getCollisionContributions().clear());

        List<CollisionContribution> oversized = new ArrayList<>();
        for (int i = 0; i <= GameTickEventRestorationTransientRollbackSnapshot
                .MAXIMUM_COLLISION_CONTRIBUTION_TILES; i++) {
            oversized.add(CollisionContribution.of(i, 1, 1, 0, 0));
        }
        expectIllegal(() -> candidate(
            4, 4, 524, 489, 0, 0, null, 0,
            7L, 10, 10, 22, AuthoredConstructionKind.SCENERY,
            1, true, true, true, oversized));
    }

    private static Candidate validCandidate() {
        return candidate(
            4, 4, 524, 489, 0, 0, null, 0,
            7L, 10, 10, 22, AuthoredConstructionKind.SCENERY,
            1, true, true, true, collision());
    }

    private static List<CollisionContribution> collision() {
        return Collections.singletonList(
            CollisionContribution.of(524, 489, 1, 0, 1));
    }

    private static Candidate candidate(
            int objectId, int permanentObjectId, int x, int y,
            int direction, int type, String owner, int runtimeAttributes,
            long generation, int regionX, int regionY, int sourceOrdinal,
            AuthoredConstructionKind constructionKind, int slotObjects,
            boolean objectBoundary, boolean collisionBoundary,
            boolean collisionComplete,
            List<CollisionContribution> collision) {
        return Candidate.declare(
            objectId, permanentObjectId, x, y, direction, type, owner,
            runtimeAttributes, generation, regionX, regionY, sourceOrdinal,
            constructionKind, slotObjects, objectBoundary, collisionBoundary,
            collisionComplete, collision);
    }

    private static GameTickEventRestorationMutationIntent transientIntent() {
        return intent(ObservedTargetState.EXACT_AUTHORED_TRANSIENT_PRESENT);
    }

    private static GameTickEventRestorationMutationIntent emptySpawnIntent() {
        return intent(ObservedTargetState.EMPTY);
    }

    private static GameTickEventRestorationMutationIntent intent(
            ObservedTargetState state) {
        TargetOperation operation = TargetOperation.SCENERY_SPAWN;
        GameTickEventRestorationTargetDecision decision =
            GameTickEventRestorationTargetDecision.decideDetached(
                operation, true, 7L, 7L, state);
        int slots = state == ObservedTargetState.EMPTY ? 0 : 1;
        int authored = slots;
        GameTickEventRestorationAtomicRevalidationContract atomic =
            GameTickEventRestorationAtomicRevalidationContract.evaluate(
                GameTickEventRestorationAtomicRevalidationContract
                    .BoundaryDeclaration.declare(
                        "scheduler", "scheduler", 4L, 4L, 7L, 7L,
                        true, false, true, true, true), decision);
        GameTickEventRestorationTargetRevalidation observed =
            GameTickEventRestorationTargetRevalidation.observe(
                true, slots, 0, authored, state, true, decision, atomic);
        GameTickEventRestorationTargetRevalidationRequest request =
            GameTickEventRestorationTargetRevalidationRequest.request(
                "scheduler", 4L, 7L, 7L, true, false, true,
                operation, 310, 310, 524, 489, 0, 0, false,
                10, 10, 22, "SCENERY");
        GameTickEventRestorationMutationIntent.Creation creation =
            GameTickEventRestorationMutationIntent.assess(
                request, observed, 12L, 12L);
        check(creation.isIntentAvailable(), "fixture intent available");
        return creation.getIntent();
    }

    private static void expectReason(
            GameTickEventRestorationMutationIntent intent,
            Candidate candidate, Reason reason) {
        Creation creation =
            GameTickEventRestorationTransientRollbackSnapshot.assess(
                intent, candidate);
        check(creation.isRefused() && !creation.isSnapshotAvailable()
                && creation.getReason() == reason
                && creation.getSnapshot() == null,
            "expected fail-closed reason " + reason);
        check(!creation.isReusablePermit()
                && !creation.isRollbackAuthorized()
                && !creation.isRollbackPerformed()
                && !creation.isMutationAuthorized()
                && !creation.isMutationPerformed()
                && !creation.isExecutableRestoration()
                && !creation.isCommitToken()
                && !creation.isArrivalGate()
                && !creation.isLifecycleAuthority(),
            "refusal remains inert");
    }

    private static void assertInert(
            Creation creation,
            GameTickEventRestorationTransientRollbackSnapshot snapshot) {
        check(!creation.isReusablePermit()
                && !creation.isRollbackAuthorized()
                && !creation.isRollbackPerformed()
                && !creation.isMutationAuthorized()
                && !creation.isMutationPerformed()
                && !creation.isExecutableRestoration()
                && !creation.isCommitToken()
                && !creation.isArrivalGate()
                && !creation.isLifecycleAuthority()
                && snapshot.isDormantSnapshot()
                && snapshot.isConstructorStateComplete()
                && snapshot.isAuthoredIdentityComplete()
                && !snapshot.isOpaqueRuntimeAttributeStateCaptured()
                && snapshot.isCollisionContributionComplete()
                && !snapshot.isRuntimeObservationPerformed()
                && !snapshot.isRuntimeHandleRetained()
                && !snapshot.isStandaloneRollbackComplete()
                && !snapshot.isRollbackAuthorized()
                && !snapshot.isRollbackPerformed()
                && !snapshot.isMutationAuthorized()
                && !snapshot.isMutationPerformed()
                && !snapshot.isExecutableRestoration()
                && !snapshot.isCommitToken()
                && !snapshot.isArrivalGate()
                && !snapshot.isLifecycleAuthority(),
            "available snapshot remains non-authoritative");
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected refusal.
        }
    }

    private static void expectUnsupported(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected immutable list.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
    }
}
'''


class LayeredMapsSliceOneHundredTwentyEightTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-one-hundred-twenty-eight-"
        )
        cls.classes = Path(cls.compile_temp.name) / "classes"
        cls.classes.mkdir()
        fixture = Path(cls.compile_temp.name) / (
            "src/com/openrsc/server/event/rsc/"
            "RestorationTransientRollbackFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        result = subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(STATE), str(REQUIREMENT), str(DECISION),
                str(ATOMIC_CONTRACT), str(REQUEST), str(REVALIDATION),
                str(INTENT), str(ROLLBACK), str(fixture),
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

    def test_snapshot_fixture_is_executable_and_fail_closed(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.event.rsc."
                "RestorationTransientRollbackFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_snapshot_has_no_runtime_or_mutation_capability(self):
        source = ROLLBACK.read_text(encoding="utf-8")
        for forbidden in (
            "import com.openrsc.server.model",
            "import com.openrsc.server.net",
            "synchronized (", "GameTickEvent event", "World world",
            "Region region", "GameObject object", "TileValue tile",
            "registerGameObject", "unregisterGameObject",
            "replaceGameObject", ".doRun()", ".stop()",
            "sendUpdatePackets", "Lock ",
        ):
            self.assertNotIn(forbidden, source)
        for required in (
            "isRuntimeObservationPerformed() { return false; }",
            "isRuntimeHandleRetained() { return false; }",
            "isStandaloneRollbackComplete() { return false; }",
            "isRollbackAuthorized() { return false; }",
            "isRollbackPerformed() { return false; }",
            "isMutationAuthorized() { return false; }",
            "isMutationPerformed() { return false; }",
            "isExecutableRestoration() { return false; }",
            "isCommitToken() { return false; }",
            "isArrivalGate() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, source)

    def test_snapshot_remains_disconnected_from_runtime_consumers(self):
        name = "GameTickEventRestorationTransientRollbackSnapshot"
        self.assertNotIn(name, COMPARE_APPLY.read_text(encoding="utf-8"))
        self.assertNotIn(name, STORE.read_text(encoding="utf-8"))
        self.assertNotIn(name, HANDLER.read_text(encoding="utf-8"))
        self.assertNotIn(name, REGION_MANAGER.read_text(encoding="utf-8"))

    def test_living_plan_records_slice_one_hundred_twenty_eight(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 128: Dormant transient rollback snapshot", plan
        )
        self.assertIn("ordered collision boundary", plan)
        self.assertIn("opaque runtime attributes", plan)


if __name__ == "__main__":
    unittest.main()
