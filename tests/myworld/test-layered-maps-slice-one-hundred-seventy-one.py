#!/usr/bin/env python3
import runpy
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
HANDLER = ROOT / "server/src/com/openrsc/server/event/rsc/handler"
SCOPE = HANDLER / "GameTickEventNpcOwnerPreservationScope.java"
LIFECYCLE = HANDLER / "GameTickEventNpcOwnerPreservationLifecycle.java"
DIAGNOSTIC = HANDLER / (
    "GameTickEventNpcOwnerPreservationNoOpDiagnostic.java"
)
GAME_EVENT_HANDLER = HANDLER / "GameEventHandler.java"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_169 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-sixty-nine.py"
)))


STORE_STUB = r'''
package com.openrsc.server.event.rsc.handler;
public final class GameTickEventStore { }
'''

NPC_STUB = r'''
package com.openrsc.server.model.entity.npc;
public final class Npc { }
'''

ENTITY_LIST_STUB = r'''
package com.openrsc.server.util;
public final class EntityList<T> { }
'''

BOUNDARY_STUB = r'''
package com.openrsc.server.event.rsc.handler;

import com.openrsc.server.event.rsc.handler
    .GameTickEventNpcOwnerPreservationNoOpDiagnosticFixture;
import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionNpcOwnerPreservationRequirements;
import com.openrsc.server.util.EntityList;

final class GameTickEventNpcOwnerPreservationBoundary {
    static boolean withinPreservationScope(
        GameTickEventStore store,
        EntityList<Npc> npcs,
        LayeredPackedRegionNpcOwnerPreservationRequirements requirements,
        long tick,
        int maximumOwners,
        ScopedPreservationOperation operation) {
        if (!GameTickEventNpcOwnerPreservationNoOpDiagnosticFixture
                .enterScope) {
            return false;
        }
        GameTickEventNpcOwnerPreservationScope scope =
            GameTickEventNpcOwnerPreservationScope.open(
                requirements, 3, 2, true, true);
        try {
            operation.execute(scope);
            return true;
        } finally {
            scope.invalidate();
        }
    }

    interface ScopedPreservationOperation {
        void execute(GameTickEventNpcOwnerPreservationScope scope);
    }
}
'''

FIXTURE = r'''
package com.openrsc.server.event.rsc.handler;

import com.openrsc.server.model.entity.npc.Npc;
import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionNpcOwnerPreservationRequirements;
import com.openrsc.server.util.EntityList;

public final class
    GameTickEventNpcOwnerPreservationNoOpDiagnosticFixture {
    static boolean enterScope;

    public static void main(String[] args) {
        realScopeStopsAtUnavailableSourceLifecycle();
        refusedScopeInvokesNothing();
    }

    private static void realScopeStopsAtUnavailableSourceLifecycle() {
        enterScope = true;
        GameTickEventNpcOwnerPreservationNoOpDiagnostic.Result result =
            GameTickEventNpcOwnerPreservationNoOpDiagnostic.capture(
                new GameTickEventStore(), new EntityList<Npc>(),
                requirements(), 13L, 2);
        check(result.getGeneration() == 9L
                && result.getRequirementsObservedAtTick() == 12L
                && result.getSelectedSourceCount() == 2
                && result.getRequiredEventLinkCount() == 3
                && result.getRequiredOwnerCount() == 2
                && result.isOwnerScopeEntered()
                && result.isSourceLifecycleInvoked()
                && result.getAbsentSourceCount() == 0
                && result.getReconstructedSourceCount() == 0
                && !result.isPreservedConsumerInvoked()
                && result.getReason()
                    == GameTickEventNpcOwnerPreservationNoOpDiagnostic.Reason
                        .SOURCE_LIFECYCLE_UNAVAILABLE
                && !result.isPreservationEstablishedForConsumedWork()
                && !result.isPreservationPerformed()
                && !result.isSourceAbsencePerformed()
                && !result.isSourceReconstructionPerformed()
                && !result.isRegionMutationPerformed()
                && !result.isRuntimeHandleRetained()
                && !result.isArrivalGate()
                && !result.isVisibilityReleased()
                && !result.isLifecycleAuthority(),
            "real scope did not stop at the source-lifecycle refusal");
    }

    private static void refusedScopeInvokesNothing() {
        enterScope = false;
        GameTickEventNpcOwnerPreservationNoOpDiagnostic.Result result =
            GameTickEventNpcOwnerPreservationNoOpDiagnostic.capture(
                new GameTickEventStore(), new EntityList<Npc>(),
                requirements(), 13L, 2);
        check(!result.isOwnerScopeEntered()
                && !result.isSourceLifecycleInvoked()
                && !result.isPreservedConsumerInvoked()
                && result.getReason()
                    == GameTickEventNpcOwnerPreservationNoOpDiagnostic.Reason
                        .OWNER_SCOPE_REFUSED,
            "refused owner scope invoked lifecycle work");
    }

    private static
        LayeredPackedRegionNpcOwnerPreservationRequirements requirements() {
        return new LayeredPackedRegionNpcOwnerPreservationRequirements(true);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
'''


class LayeredMapsSliceOneHundredSeventyOneTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-npc-owner-preservation-no-op-diagnostic-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        sources = {
            "com/openrsc/server/model/world/coordinate/"
            "LayeredPackedRegionNpcOwnerPreservationRequirements.java":
                SLICE_169["REQUIREMENTS_STUB"],
            "com/openrsc/server/model/entity/npc/Npc.java": NPC_STUB,
            "com/openrsc/server/util/EntityList.java": ENTITY_LIST_STUB,
            "com/openrsc/server/event/rsc/handler/GameTickEventStore.java":
                STORE_STUB,
            "com/openrsc/server/event/rsc/handler/"
            "GameTickEventNpcOwnerPreservationBoundary.java": BOUNDARY_STUB,
            "com/openrsc/server/event/rsc/handler/"
            "GameTickEventNpcOwnerPreservationNoOpDiagnosticFixture.java":
                FIXTURE,
        }
        paths = []
        for relative, source in sources.items():
            path = cls.temp / "src" / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source, encoding="utf-8")
            paths.append(path)
        subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(SCOPE), str(LIFECYCLE), str(DIAGNOSTIC),
                *(str(path) for path in paths),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_real_scope_adapter_refuses_without_preserved_work(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.event.rsc.handler."
                "GameTickEventNpcOwnerPreservationNoOpDiagnosticFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_adapter_is_wired_to_the_real_handler_but_not_observer(self):
        handler = GAME_EVENT_HANDLER.read_text(encoding="utf-8")
        diagnostic = DIAGNOSTIC.read_text(encoding="utf-8")
        self.assertIn(
            "captureLayeredPackedRegionNpcOwnerPreservationNoOpDiagnostic(",
            handler,
        )
        self.assertIn(
            "GameTickEventNpcOwnerPreservationNoOpDiagnostic.capture(",
            handler,
        )
        self.assertIn("withinPreservationScope(", diagnostic)
        self.assertIn("SOURCE_SET_UNAVAILABLE", diagnostic)
        self.assertIn("preservedConsumerInvoked[0] = true", diagnostic)
        self.assertIn(
            "Verification-only owner lifecycle crossed its refusal",
            diagnostic,
        )

    def test_adapter_has_no_source_or_world_mutation_authority(self):
        source = DIAGNOSTIC.read_text(encoding="utf-8")
        for expected in (
            "isPreservationPerformed() { return false; }",
            "isSourceAbsencePerformed() { return false; }",
            "isSourceReconstructionPerformed() { return false; }",
            "isRegionMutationPerformed() { return false; }",
            "isArrivalGate() { return false; }",
            "isVisibilityReleased() { return false; }",
            "isLifecycleAuthority() { return false; }",
        ):
            self.assertIn(expected, source)
        for forbidden in (
            "RegionManager",
            "registerNpc(",
            "unregisterNpc(",
            "registerGameObject(",
            "unregisterGameObject(",
            "event.stop()",
            "setLocation(",
        ):
            self.assertNotIn(forbidden, source)

    def test_living_plan_records_slice_one_hundred_seventy_one(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 171: Real-boundary source-lifecycle refusal",
            plan,
        )
        self.assertIn("SOURCE_LIFECYCLE_UNAVAILABLE", plan)
        self.assertIn("zero preserved-consumer invocations", plan)


if __name__ == "__main__":
    unittest.main()
