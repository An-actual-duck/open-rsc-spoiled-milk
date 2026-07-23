#!/usr/bin/env python3
import os
import runpy
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server"
EVENT_ROOT = SERVER / "src/com/openrsc/server/event/rsc"
HANDLER_ROOT = EVENT_ROOT / "handler"
REGION_ROOT = SERVER / "src/com/openrsc/server/model/world/region"
DIAGNOSTIC = HANDLER_ROOT / "GameTickEventRestorationNoOpDiagnostic.java"
TRANSACTION = REGION_ROOT / "RegionObjectCollisionTransactionExecutor.java"
DIRECTIVE = HANDLER_ROOT / (
    "GameTickEventRestorationRecoveryDirectiveExecutor.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_156 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-fifty-six.py"
)))
SLICE_146 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-forty-six.py"
)))


DIAGNOSTIC_METHODS = r'''
    private static void noOpDiagnosticVerifiesWithoutMutation() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = registered(store);
        RegionManager region = new RegionManager(true);
        GameTickEventRestorationNoOpDiagnostic result =
            GameTickEventRestorationNoOpDiagnostic.capture(
                store, region, inventory(store, event, true), 1);
        check(result.getReason()
                    == GameTickEventRestorationNoOpDiagnostic.Reason
                        .NO_OP_VERIFICATION_READY
                && result.getProposalGeneration() == GENERATION
                && result.getInventoryEventCount() == 1
                && result.getRecoveryCandidateCount() == 1
                && result.getFutureSnapshotCount() == 1
                && result.getRuntimeVerificationCount() == 1
                && result.getMutationOperationCount() == 0
                && result.getTerminalEventConsumptionCount() == 0
                && result.isReconstructionInvoked()
                && result.isRecoveryInvoked()
                && result.isContractuallyReadyForFirstVisibility()
                && !result.isFreshInventoryRetryRequired()
                && !result.isRegionMutationAllowed()
                && !result.isOverdueConsumptionAllowed()
                && !result.isRegionLoadingPerformed()
                && !result.isRetryPerformed()
                && !result.isArrivalGate()
                && !result.isVisibilityReleased()
                && !result.isRuntimeHandleRetained()
                && region.getCaptureCalls() == 1
                && region.getApplicationCalls() == 0
                && region.getVerificationCalls() == 1
                && store.eventIsContained(event),
            "no-op diagnostic verifies a future callback without mutation");
    }

    private static void overdueCandidateIsRejectedBeforeExecution() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = registered(store);
        event.makeOverdue();
        RegionManager region = new RegionManager(true);
        GameTickEventRestorationNoOpDiagnostic result =
            GameTickEventRestorationNoOpDiagnostic.capture(
                store, region, inventory(store, event, true), 1);
        check(result.getReason()
                    == GameTickEventRestorationNoOpDiagnostic.Reason
                        .NON_FUTURE_CANDIDATE_REFUSED
                && result.getRecoveryCandidateCount() == 1
                && result.getFutureSnapshotCount() == 0
                && result.getRuntimeVerificationCount() == 0
                && result.getTerminalEventConsumptionCount() == 0
                && !result.isReconstructionInvoked()
                && !result.isRecoveryInvoked()
                && region.getApplicationCalls() == 0
                && region.getVerificationCalls() == 0
                && store.eventIsContained(event),
            "no-op diagnostic never consumes an overdue callback");
    }

    private static void incompleteInventoryIsRejectedBeforeExecution() {
        GameTickEventStore store = new GameTickEventStore();
        RestorableEvent event = registered(store);
        RegionManager region = new RegionManager(true);
        GameTickEventRestorationNoOpDiagnostic result =
            GameTickEventRestorationNoOpDiagnostic.capture(
                store, region, inventory(store, event, false), 1);
        check(result.getReason()
                    == GameTickEventRestorationNoOpDiagnostic.Reason
                        .LIVE_CAPTURE_REFUSED
                && result.getRuntimeVerificationCount() == 0
                && !result.isReconstructionInvoked()
                && !result.isRecoveryInvoked()
                && region.getApplicationCalls() == 0
                && region.getVerificationCalls() == 0
                && store.eventIsContained(event),
            "incomplete inventory never reaches no-op reconstruction");
    }
'''


TRANSACTION_METHOD = r'''
    private static void verificationOnlyRecoveryNeverRestoresMissingState() {
        FixtureWorld empty = new FixtureWorld();
        GameObject expected = authoredObject(120, 110, 321, 320);
        Result register = plan(Operation.REGISTER, expected, BLOCKING);
        GameTickEventRestorationCurrentStateRecoverySnapshot snapshot =
            currentSnapshot(
                CallbackKind.SCENERY_SPAWN,
                ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT,
                320, 320, expected, register);
        List<Result> footprints = Collections.singletonList(register);
        RegionObjectCollisionTransactionExecutor.CurrentStateRecoveryResult
            refused = RegionObjectCollisionTransactionExecutor
                .executeCurrentStateRecovery(
                    empty.boundaries(footprints),
                    empty.regionAt(snapshot.getX(), snapshot.getY()),
                    snapshot, null, expected, register, empty.tiles,
                    region -> empty.invalidations.incrementAndGet(), false);
        check(refused.isRefused()
                && refused.getReason()
                    == RegionObjectCollisionTransactionExecutor
                        .CurrentStateRecoveryReason.RECOVERY_MUTATION_DISABLED
                && empty.regionAt(120, 110).getGameObjects().isEmpty()
                && empty.tiles.nonZeroTileCount() == 0
                && empty.invalidations.get() == 0,
            "verification-only recovery refuses missing state unchanged");

        FixtureWorld satisfied = new FixtureWorld();
        GameObject present = authoredObject(122, 110, 321, 320);
        Result presentRegister = plan(Operation.REGISTER, present, BLOCKING);
        GameTickEventRestorationCurrentStateRecoverySnapshot presentSnapshot =
            currentSnapshot(
                CallbackKind.SCENERY_SPAWN,
                ObservedCurrentState.EXACT_AUTHORED_TRANSIENT_PRESENT,
                320, 320, present, presentRegister);
        List<Result> presentFootprints =
            Collections.singletonList(presentRegister);
        RegionObjectCollisionTransactionExecutor.CurrentStateRecoveryResult
            applied = RegionObjectCollisionTransactionExecutor
                .executeCurrentStateRecovery(
                    satisfied.boundaries(presentFootprints),
                    satisfied.regionAt(
                        presentSnapshot.getX(), presentSnapshot.getY()),
                    presentSnapshot, null, present, presentRegister,
                    satisfied.tiles,
                    region -> satisfied.invalidations.incrementAndGet(), true);
        int invalidations = satisfied.invalidations.get();
        RegionObjectCollisionTransactionExecutor.CurrentStateRecoveryResult
            verified = RegionObjectCollisionTransactionExecutor
                .executeCurrentStateRecovery(
                    satisfied.boundaries(presentFootprints),
                    satisfied.regionAt(
                        presentSnapshot.getX(), presentSnapshot.getY()),
                    presentSnapshot, present, present, presentRegister,
                    satisfied.tiles,
                    region -> satisfied.invalidations.incrementAndGet(), false);
        check(applied.isApplied()
                && verified.isNoOp()
                && satisfied.regionAt(122, 110).getGameObjects().size() == 1
                && satisfied.regionAt(122, 110).getGameObjects()
                    .contains(present)
                && satisfied.tiles.get(122, 110)
                    .getBlockingSceneryCount() == 1
                && satisfied.invalidations.get() == invalidations,
            "verification-only recovery accepts exact satisfied state");
    }
'''


def build_diagnostic_fixture():
    fixture = SLICE_156["FIXTURE"]
    fixture = fixture.replace(
        "        reconstructionRefusalNeverInvokesRecovery();",
        "        reconstructionRefusalNeverInvokesRecovery();\n"
        "        noOpDiagnosticVerifiesWithoutMutation();\n"
        "        overdueCandidateIsRejectedBeforeExecution();\n"
        "        incompleteInventoryIsRejectedBeforeExecution();",
    )
    fixture = fixture.replace(
        "        public void run() { }",
        "        void makeOverdue() {\n"
        "            setDelayTicks(0L);\n"
        "            resetCountdown();\n"
        "        }\n"
        "        public void run() { }",
    )
    fixture = fixture.replace(
        "    private static LayeredPackedRegionEventOwnershipInventory inventory(",
        DIAGNOSTIC_METHODS
        + "\n    private static LayeredPackedRegionEventOwnershipInventory inventory(",
    )
    return fixture


def build_transaction_fixture():
    fixture = SLICE_146["build_fixture"]()
    fixture = fixture.replace(
        "        collisionSnapshotMismatchRefusesUnchanged();",
        "        collisionSnapshotMismatchRefusesUnchanged();\n"
        "        verificationOnlyRecoveryNeverRestoresMissingState();",
    )
    fixture = fixture.replace(
        "    private static GameObject authoredObject(",
        TRANSACTION_METHOD + "\n    private static GameObject authoredObject(",
    )
    return fixture


class LayeredMapsSliceOneHundredFiftySevenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp_dir = tempfile.TemporaryDirectory(
            prefix="layered-no-op-recovery-diagnostic-"
        )
        cls.temp = Path(cls.temp_dir.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        shared = SLICE_156["SHARED"]
        sources = {
            "com/openrsc/server/model/entity/Mob.java": shared["MOB_STUB"],
            "com/openrsc/server/model/entity/player/Player.java":
                shared["PLAYER_STUB"],
            "com/openrsc/server/model/entity/npc/Npc.java":
                shared["NPC_STUB"],
            "com/openrsc/server/Server.java": shared["SERVER_STUB"],
            "com/openrsc/server/model/world/World.java":
                shared["WORLD_STUB"],
            "com/openrsc/server/model/world/region/RegionManager.java":
                SLICE_156["REGION_MANAGER_STUB"],
            "com/openrsc/server/model/world/coordinate/"
            "LayeredPackedRegionRetirementRefinementProposal.java": r'''
package com.openrsc.server.model.world.coordinate;
public final class LayeredPackedRegionRetirementRefinementProposal {
    public static final int MAXIMUM_CANDIDATE_SOURCES = 8192;
}
''',
            "com/openrsc/server/model/world/coordinate/WorldRegionKey.java": r'''
package com.openrsc.server.model.world.coordinate;
public final class WorldRegionKey { public static final int REGION_SIZE = 48; }
''',
            "com/openrsc/server/event/rsc/PluginTickEvent.java":
                shared["PLUGIN_STUB"],
            "com/openrsc/server/event/rsc/"
            "GameTickEventRestorationTargetDecision.java":
                shared["TARGET_DECISION_STUB"],
            "com/openrsc/server/event/rsc/"
            "GameTickEventRestorationTargetRevalidationRequest.java":
                shared["TARGET_REVALIDATION_REQUEST_STUB"],
            "com/openrsc/server/event/rsc/"
            "GameTickEventRestorationTargetRevalidation.java":
                shared["TARGET_REVALIDATION_STUB"],
            "com/openrsc/server/event/rsc/handler/"
            "LiveReconstructionCoordinatorFixture.java":
                build_diagnostic_fixture(),
        }
        paths = []
        for relative, source in sources.items():
            path = cls.temp / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source, encoding="utf-8")
            paths.append(path)
        classpath = ":".join(str(path) for path in (
            ROOT / "server/lib/guava-30.1.1-jre.jar",
            ROOT / "server/lib/guice-5.0.2-jar-with-dependencies.jar",
            ROOT / "server/lib/commons-lang3-3.12.0.jar",
            ROOT / "server/lib/log4j-api-2.17.0.jar",
        ))
        event_sources = [
            SLICE_156[name] for name in (
                "STORE", "CURRENT_CAPTURE", "LIVE_PREPARATION",
                "FUTURE_APPLICATION", "DIRECTIVE_EXECUTOR",
                "BATCH_EXECUTOR", "LIFECYCLE", "LIVE_RECONSTRUCTION",
                "EVENT", "STATE", "AFFINITY", "SNAPSHOT", "REQUEST",
                "ONE_SHOT", "BATCH", "COORDINATOR_CONTRACT", "INVENTORY",
            )
        ]
        result = subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-cp", classpath, "-d", str(cls.classes),
                *(str(path) for path in event_sources), str(DIAGNOSTIC),
                str(EVENT_ROOT / "DuplicationStrategy.java"),
                *(str(path) for path in paths),
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        if result.returncode != 0:
            raise AssertionError(result.stderr)
        cls.diagnostic_classpath = classpath + ":" + str(cls.classes)

        cls.transaction_classes = cls.temp / "transaction-classes"
        cls.transaction_classes.mkdir()
        transaction_fixture = cls.temp / (
            "transaction-src/com/openrsc/server/model/world/region/"
            "RegionObjectCollisionTransactionFixture.java"
        )
        transaction_fixture.parent.mkdir(parents=True, exist_ok=True)
        transaction_fixture.write_text(
            build_transaction_fixture(), encoding="utf-8"
        )
        transaction_classpath = os.pathsep.join(
            [str(SERVER / "core.jar"), str(SERVER / "lib/*")]
        )
        transaction_sources = [
            SLICE_146[name] for name in (
                "STATE", "CURRENT", "BOUNDARY", "COLLISION_EXECUTOR",
                "TRANSACTION", "ENTITY", "GAME_OBJECT", "REGION",
            )
        ]
        result = subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-cp", transaction_classpath,
                "-d", str(cls.transaction_classes),
                *(str(path) for path in transaction_sources),
                str(transaction_fixture),
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        if result.returncode != 0:
            raise AssertionError(result.stderr)
        cls.transaction_classpath = os.pathsep.join([
            str(cls.transaction_classes), str(SERVER / "core.jar"),
            str(SERVER / "lib/*"),
        ])

    @classmethod
    def tearDownClass(cls):
        cls.temp_dir.cleanup()

    def test_no_op_diagnostic_fixture_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", self.diagnostic_classpath,
                "com.openrsc.server.event.rsc.handler."
                "LiveReconstructionCoordinatorFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
            timeout=20,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_verification_only_region_fixture_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", self.transaction_classpath,
                "com.openrsc.server.model.world.region."
                "RegionObjectCollisionTransactionFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
            timeout=20,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_policy_blocks_overdue_consumption_and_missing_state_mutation(self):
        diagnostic = DIAGNOSTIC.read_text(encoding="utf-8")
        transaction = TRANSACTION.read_text(encoding="utf-8")
        directive = DIRECTIVE.read_text(encoding="utf-8")
        self.assertIn("NON_FUTURE_CANDIDATE_REFUSED", diagnostic)
        self.assertIn("isRegionMutationAllowed() { return false; }", diagnostic)
        self.assertIn("isOverdueConsumptionAllowed() { return false; }", diagnostic)
        self.assertIn("RECOVERY_MUTATION_DISABLED", transaction)
        self.assertLess(
            transaction.index("if (!mutationAllowed)"),
            transaction.index("Result applied = executeInsideBoundaries("),
        )
        self.assertIn("|| !mutationAllowed", directive)
        for forbidden in (
            "WorldLoader", "Player", "retry(", "arrive(", "visibility(",
        ):
            self.assertNotIn(forbidden, diagnostic)

    def test_living_plan_records_slice_one_hundred_fifty_seven(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn("### Slice 157: Verification-only no-op diagnostic", plan)
        normalized = " ".join(plan.split())
        self.assertIn("never permits Region mutation", normalized)
        self.assertIn("never consumes an overdue callback", normalized)


if __name__ == "__main__":
    unittest.main()
