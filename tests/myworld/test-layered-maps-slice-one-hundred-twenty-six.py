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
REQUIREMENT = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationRequirement.java"
)
DECISION = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationTargetDecision.java"
)
CONTRACT = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationAtomicRevalidationContract.java"
)
REQUEST = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationTargetRevalidationRequest.java"
)
REVALIDATION = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationTargetRevalidation.java"
)
INTENT = ROOT / (
    "server/src/com/openrsc/server/event/rsc/"
    "GameTickEventRestorationMutationIntent.java"
)
STORE = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameTickEventStore.java"
)
HANDLER = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameEventHandler.java"
)
REGION_MANAGER = ROOT / (
    "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


FIXTURE = r'''
package com.openrsc.server.event.rsc;

import com.openrsc.server.event.rsc
    .GameTickEventRestorationMutationIntent.AuthoredConstructionKind;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationMutationIntent.Creation;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationMutationIntent.DesiredState;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationMutationIntent.Operation;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationMutationIntent.Reason;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTargetDecision.ObservedTargetState;
import com.openrsc.server.event.rsc
    .GameTickEventRestorationTargetDecision.TargetOperation;

public final class RestorationMutationIntentFixture {
    public static void main(String[] args) {
        acceptsExactSpawnAndRemovalIntents();
        refusesUnstableNoOpAndConflictingEvidence();
        refusesConstructionAndPayloadMismatches();
    }

    private static void acceptsExactSpawnAndRemovalIntents() {
        GameTickEventRestorationTargetRevalidationRequest spawnRequest =
            request(TargetOperation.SCENERY_SPAWN, 0, true,
                "HARVESTING_SCENERY");
        Creation emptySpawn = GameTickEventRestorationMutationIntent.assess(
            spawnRequest,
            revalidation(TargetOperation.SCENERY_SPAWN,
                ObservedTargetState.EMPTY, 0, 0, 0), 12L, 12L);
        check(emptySpawn.isIntentAvailable() && !emptySpawn.isRefused()
                && emptySpawn.getReason() == Reason.INTENT_AVAILABLE,
            "empty spawn creates an intent");
        GameTickEventRestorationMutationIntent spawn = emptySpawn.getIntent();
        check(spawn != null
                && spawn.getOperation() == Operation.SCENERY_SPAWN
                && spawn.getDesiredState() == DesiredState.PRESENT
                && spawn.getExpectedTargetState() == ObservedTargetState.EMPTY
                && spawn.getObjectId() == 310
                && spawn.getPermanentObjectId() == 310
                && spawn.getX() == 524 && spawn.getY() == 489
                && spawn.getDirection() == 0 && spawn.getType() == 0
                && spawn.isForceFullBlock()
                && spawn.getAuthoredGeneration() == 7L
                && spawn.getAuthoredPackedRegionX() == 10
                && spawn.getAuthoredPackedRegionY() == 10
                && spawn.getAuthoredSourceOrdinal() == 22
                && spawn.getAuthoredConstructionKind()
                    == AuthoredConstructionKind.HARVESTING_SCENERY,
            "spawn intent retains exact detached mutation scalars");

        Creation transientSpawn = GameTickEventRestorationMutationIntent.assess(
            spawnRequest,
            revalidation(TargetOperation.SCENERY_SPAWN,
                ObservedTargetState.EXACT_AUTHORED_TRANSIENT_PRESENT,
                1, 0, 1), 20L, 20L);
        check(transientSpawn.isIntentAvailable()
                && transientSpawn.getIntent().getExpectedTargetState()
                    == ObservedTargetState.EXACT_AUTHORED_TRANSIENT_PRESENT,
            "exact authored transient creates a spawn intent");

        GameTickEventRestorationTargetRevalidationRequest removalRequest =
            request(TargetOperation.SCENERY_REMOVE, 0, false, "SCENERY");
        Creation removal = GameTickEventRestorationMutationIntent.assess(
            removalRequest,
            revalidation(TargetOperation.SCENERY_REMOVE,
                ObservedTargetState.EXACT_RESTORATION_SCENERY_PRESENT,
                1, 1, 1), 31L, 31L);
        check(removal.isIntentAvailable()
                && removal.getIntent().getOperation()
                    == Operation.SCENERY_REMOVE
                && removal.getIntent().getDesiredState() == DesiredState.ABSENT
                && !removal.getIntent().isForceFullBlock(),
            "exact authored removal creates an absent-state intent");

        assertInert(emptySpawn, spawn);
    }

    private static void refusesUnstableNoOpAndConflictingEvidence() {
        GameTickEventRestorationTargetRevalidationRequest spawnRequest =
            request(TargetOperation.SCENERY_SPAWN, 0, false, "SCENERY");
        GameTickEventRestorationTargetRevalidation empty =
            revalidation(TargetOperation.SCENERY_SPAWN,
                ObservedTargetState.EMPTY, 0, 0, 0);
        expectReason(spawnRequest, empty, 0L, 0L,
            Reason.INVALID_LIFECYCLE_VERSION);
        expectReason(spawnRequest, empty, 4L, 5L,
            Reason.EVENT_LIFECYCLE_CHANGED);

        GameTickEventRestorationTargetRevalidation noOp =
            revalidation(TargetOperation.SCENERY_SPAWN,
                ObservedTargetState.EXACT_RESTORATION_SCENERY_PRESENT,
                1, 1, 1);
        expectReason(spawnRequest, noOp, 6L, 6L,
            Reason.DESIRED_STATE_ALREADY_SATISFIED);

        GameTickEventRestorationTargetRevalidation refused =
            revalidation(TargetOperation.SCENERY_SPAWN,
                ObservedTargetState.MISMATCHED_OR_IDENTITYLESS_OCCUPANT,
                1, 0, 0);
        expectReason(spawnRequest, refused, 7L, 7L,
            Reason.CONTRACT_REFUSED);

        GameTickEventRestorationTargetDecision unavailableDecision =
            GameTickEventRestorationTargetDecision.decideDetached(
                TargetOperation.SCENERY_SPAWN, true, 7L, 7L,
                ObservedTargetState.UNAVAILABLE);
        GameTickEventRestorationTargetRevalidation unavailable =
            GameTickEventRestorationTargetRevalidation.observe(
                false, 0, 0, 0, ObservedTargetState.UNAVAILABLE, false,
                unavailableDecision, contract(unavailableDecision, false));
        expectReason(spawnRequest, unavailable, 8L, 8L,
            Reason.REGION_UNAVAILABLE);

        GameTickEventRestorationTargetDecision emptyDecision =
            GameTickEventRestorationTargetDecision.decideDetached(
                TargetOperation.SCENERY_SPAWN, true, 7L, 7L,
                ObservedTargetState.EMPTY);
        GameTickEventRestorationTargetRevalidation inconsistent =
            GameTickEventRestorationTargetRevalidation.observe(
                true, 1, 0, 0, ObservedTargetState.EMPTY, true,
                emptyDecision, contract(emptyDecision, true));
        expectReason(spawnRequest, inconsistent, 9L, 9L,
            Reason.TARGET_EVIDENCE_INCONSISTENT);

        GameTickEventRestorationTargetRevalidation removalEvidence =
            revalidation(TargetOperation.SCENERY_REMOVE,
                ObservedTargetState.EXACT_RESTORATION_SCENERY_PRESENT,
                1, 1, 1);
        expectReason(spawnRequest, removalEvidence, 10L, 10L,
            Reason.TARGET_OPERATION_OUTCOME_MISMATCH);
    }

    private static void refusesConstructionAndPayloadMismatches() {
        GameTickEventRestorationTargetRevalidation empty =
            revalidation(TargetOperation.SCENERY_SPAWN,
                ObservedTargetState.EMPTY, 0, 0, 0);
        expectReason(
            request(TargetOperation.SCENERY_SPAWN, 0, false, "UNKNOWN"),
            empty, 11L, 11L,
            Reason.AUTHORED_CONSTRUCTION_KIND_UNSUPPORTED);
        expectReason(
            request(TargetOperation.SCENERY_SPAWN, 0, false, "BOUNDARY"),
            empty, 12L, 12L,
            Reason.AUTHORED_CONSTRUCTION_KIND_MISMATCH);
        expectReason(
            request(TargetOperation.SCENERY_SPAWN, 1, false, "SCENERY"),
            empty, 13L, 13L,
            Reason.AUTHORED_CONSTRUCTION_KIND_MISMATCH);
        expectIllegal(() -> request(
            TargetOperation.SCENERY_REMOVE, 0, true, "SCENERY"));
        expectIllegal(() -> GameTickEventRestorationTargetRevalidationRequest
            .request("fixture-scheduler", 4L, 7L, 7L, true, false, true,
                TargetOperation.SCENERY_SPAWN, 310, 310, 524, 489, 0, 0,
                false, 10, 10,
                GameTickEventRestorationState
                    .MAXIMUM_AUTHORED_SOURCE_ORDINAL + 1,
                "SCENERY"));
    }

    private static GameTickEventRestorationTargetRevalidationRequest request(
            TargetOperation operation, int type, boolean forceFullBlock,
            String constructionKind) {
        return GameTickEventRestorationTargetRevalidationRequest.request(
            "fixture-scheduler", 4L, 7L, 7L, true, false, true,
            operation, 310, 310, 524, 489, 0, type, forceFullBlock,
            10, 10, 22, constructionKind);
    }

    private static GameTickEventRestorationTargetRevalidation revalidation(
            TargetOperation operation, ObservedTargetState state,
            int slotCount, int restorationCount, int authoredCount) {
        GameTickEventRestorationTargetDecision target =
            GameTickEventRestorationTargetDecision.decideDetached(
                operation, true, 7L, 7L, state);
        return GameTickEventRestorationTargetRevalidation.observe(
            true, slotCount, restorationCount, authoredCount, state, true,
            target, contract(target, true));
    }

    private static GameTickEventRestorationAtomicRevalidationContract contract(
            GameTickEventRestorationTargetDecision target,
            boolean regionBoundaryHeld) {
        return GameTickEventRestorationAtomicRevalidationContract.evaluate(
            GameTickEventRestorationAtomicRevalidationContract
                .BoundaryDeclaration.declare(
                    "fixture-scheduler", "fixture-scheduler",
                    4L, 4L, 7L, 7L, true, false, true,
                    regionBoundaryHeld, regionBoundaryHeld),
            target);
    }

    private static void expectReason(
            GameTickEventRestorationTargetRevalidationRequest request,
            GameTickEventRestorationTargetRevalidation revalidation,
            long before, long after, Reason reason) {
        Creation creation = GameTickEventRestorationMutationIntent.assess(
            request, revalidation, before, after);
        check(creation.isRefused() && !creation.isIntentAvailable()
                && creation.getReason() == reason
                && creation.getIntent() == null,
            "expected fail-closed reason " + reason);
        check(!creation.isReusablePermit()
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
            GameTickEventRestorationMutationIntent intent) {
        check(!creation.isReusablePermit()
                && !creation.isMutationAuthorized()
                && !creation.isMutationPerformed()
                && !creation.isExecutableRestoration()
                && !creation.isCommitToken()
                && !creation.isArrivalGate()
                && !creation.isLifecycleAuthority()
                && intent.isDormantIntent()
                && intent.isStaleAfterBoundaryRelease()
                && !intent.isReusablePermit()
                && !intent.isMutationAuthorized()
                && !intent.isMutationPerformed()
                && !intent.isExecutableRestoration()
                && !intent.isCommitToken()
                && !intent.isArrivalGate()
                && !intent.isLifecycleAuthority(),
            "available intent remains non-authoritative");
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected refusal.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) { throw new AssertionError(label); }
    }
}
'''


class LayeredMapsSliceOneHundredTwentySixTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-one-hundred-twenty-six-"
        )
        cls.classes = Path(cls.compile_temp.name) / "classes"
        cls.classes.mkdir()
        fixture = Path(cls.compile_temp.name) / (
            "src/com/openrsc/server/event/rsc/"
            "RestorationMutationIntentFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        result = subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(STATE), str(REQUIREMENT), str(DECISION), str(CONTRACT),
                str(REQUEST), str(REVALIDATION), str(INTENT), str(fixture),
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

    def test_intent_fixture_is_executable_and_fail_closed(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.event.rsc."
                "RestorationMutationIntentFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_intent_has_no_runtime_or_mutation_capability(self):
        source = INTENT.read_text(encoding="utf-8")
        for forbidden in (
            "import com.openrsc.server.model",
            "import com.openrsc.server.net",
            "synchronized (", "GameTickEvent event", "World world",
            "Region region", "GameObject object", "registerGameObject",
            "unregisterGameObject", "replaceGameObject", ".doRun()",
            ".stop()", "sendUpdatePackets", "Lock ",
        ):
            self.assertNotIn(forbidden, source)
        for required in (
            "isReusablePermit() { return false; }",
            "isMutationAuthorized() { return false; }",
            "isMutationPerformed() { return false; }",
            "isExecutableRestoration() { return false; }",
            "isCommitToken() { return false; }",
            "isArrivalGate() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(required, source)

    def test_intent_remains_disconnected_and_force_block_is_not_lost(self):
        intent_name = "GameTickEventRestorationMutationIntent"
        self.assertNotIn(intent_name, STORE.read_text(encoding="utf-8"))
        self.assertNotIn(intent_name, HANDLER.read_text(encoding="utf-8"))
        self.assertNotIn(intent_name, REGION_MANAGER.read_text(encoding="utf-8"))
        request = REQUEST.read_text(encoding="utf-8")
        store = STORE.read_text(encoding="utf-8")
        self.assertIn("private final boolean forceFullBlock;", request)
        self.assertIn("isForceFullBlock() { return forceFullBlock; }", request)
        self.assertIn("fence.getType(), fence.isForceFullBlock(),", store)

    def test_living_plan_records_slice_one_hundred_twenty_six(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 126: Dormant exact mutation intent", plan
        )
        self.assertIn("not a reusable permit", plan)
        self.assertIn("compare-and-apply", plan)


if __name__ == "__main__":
    unittest.main()
