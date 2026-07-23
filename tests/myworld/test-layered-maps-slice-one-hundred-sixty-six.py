#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
NPC = ROOT / "server/src/com/openrsc/server/model/entity/npc/Npc.java"
NPC_GATE = ROOT / (
    "server/src/com/openrsc/server/model/entity/npc/"
    "NpcOwnerPreservationLifecycleGate.java"
)
BOUNDARY_ADAPTER = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameTickEventNpcOwnerPreservationBoundary.java"
)
REQUIREMENTS = ROOT / (
    "server/src/com/openrsc/server/model/world/coordinate/"
    "LayeredPackedRegionNpcOwnerPreservationRequirements.java"
)
EVENT_STORE = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/GameTickEventStore.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
FIXTURE = r'''
package com.openrsc.server.model.entity.npc;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class NpcOwnerPreservationLifecycleGateFixture {
    public static void main(String[] args) throws Exception {
        NpcOwnerPreservationLifecycleGate gate =
            new NpcOwnerPreservationLifecycleGate();

        gate.beginOperation();
        check(!gate.withinPreservationBoundary(boundary -> {
            throw new AssertionError("busy lifecycle entered preservation");
        }), "in-flight lifecycle work refuses preservation");
        gate.endOperation();

        CountDownLatch preservationEntered = new CountDownLatch(1);
        CountDownLatch releasePreservation = new CountDownLatch(1);
        CountDownLatch lifecycleEntered = new CountDownLatch(1);
        AtomicBoolean boundaryAccepted = new AtomicBoolean();
        AtomicBoolean reentryRefused = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        Thread preservation = new Thread(() -> {
            try {
                boundaryAccepted.set(gate.withinPreservationBoundary(
                    boundary -> {
                        check(boundary.isPreservationGateActive()
                                && boundary.getLifecycleOperationsAtEntry() == 0,
                            "accepted boundary reports a closed empty gate");
                        try {
                            gate.beginOperation();
                        } catch (IllegalStateException expected) {
                            reentryRefused.set(true);
                        }
                        preservationEntered.countDown();
                        await(releasePreservation);
                    }));
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        }, "preservation-boundary");
        preservation.start();
        check(preservationEntered.await(2, TimeUnit.SECONDS),
            "preservation boundary did not enter");

        Thread lifecycle = new Thread(() -> {
            try {
                gate.beginOperation();
                lifecycleEntered.countDown();
                gate.endOperation();
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        }, "normal-lifecycle");
        lifecycle.start();
        check(!lifecycleEntered.await(150, TimeUnit.MILLISECONDS),
            "new lifecycle work crossed an active preservation gate");
        releasePreservation.countDown();
        preservation.join(2000L);
        lifecycle.join(2000L);

        check(!preservation.isAlive() && !lifecycle.isAlive(),
            "gate threads did not finish");
        check(failure.get() == null,
            "gate thread failed: " + failure.get());
        check(boundaryAccepted.get() && reentryRefused.get(),
            "accepted boundary must reject same-thread lifecycle reentry");
        check(lifecycleEntered.getCount() == 0L,
            "blocked lifecycle work did not resume after release");

        gate.beginOperation();
        gate.endOperation();
        check(gate.withinPreservationBoundary(boundary -> {
            check(boundary.isPreservationGateActive(),
                "gate is reusable after complete lifecycle work");
        }), "gate did not return to an idle reusable state");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("timed out waiting for fixture latch");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("fixture interrupted", interrupted);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
'''


class LayeredMapsSliceOneHundredSixtySixTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-npc-owner-lifecycle-gate-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        fixture = cls.temp / (
            "src/com/openrsc/server/model/entity/npc/"
            "NpcOwnerPreservationLifecycleGateFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(NPC_GATE), str(fixture),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_npc_owner_lifecycle_gate_is_executable(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.entity.npc."
                "NpcOwnerPreservationLifecycleGateFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_runtime_routes_share_the_gate_without_polluting_the_store(self):
        npc = NPC.read_text(encoding="utf-8")
        gate = NPC_GATE.read_text(encoding="utf-8")
        adapter = BOUNDARY_ADAPTER.read_text(encoding="utf-8")
        requirements = REQUIREMENTS.read_text(encoding="utf-8")
        store = EVENT_STORE.read_text(encoding="utf-8")

        for route in (
            "removeWithinLayeredOwnerLifecycle",
            "setLocation(final Point point, final boolean teleported)",
            "moveToAdjacentTileWithinLayeredOwnerLifecycle",
            "updateBehaviorWithinLayeredOwnerLifecycle",
            "updateMovementOnly()",
            "superRemove()",
            "n.beginLayeredOwnerLifecycleOperation();",
            "n.endLayeredOwnerLifecycleOperation();",
        ):
            self.assertIn(route, npc)
        self.assertIn("operationsInProgress", gate)
        self.assertIn("preservationBoundaryActive", gate)
        self.assertIn("lock.wait()", gate)
        self.assertIn(
            "withinLayeredOwnerPreservationLifecycleBoundaries",
            adapter,
        )
        self.assertIn("captureIterativeNpcLifecycleBoundaries", adapter)
        self.assertIn("captureOwnerCorrelation", adapter)
        self.assertIn("capture.ownerStates.clear()", adapter)
        self.assertIn(
            "capture.regionAbsenceQuiescenceHeld =",
            adapter,
        )
        self.assertIn("regionLifecycleBoundaryHeld", adapter)
        self.assertIn(
            "for (EventRecord event : checkedInventory.getEvents())",
            requirements,
        )
        self.assertIn("getSupportingEventLinkCount()", requirements)
        self.assertNotIn(
            "com.openrsc.server.model.world.coordinate",
            store,
        )

    def test_living_plan_records_slice_one_hundred_sixty_six(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 166: Scoped NPC lifecycle and quiescence gate",
            plan,
        )
        self.assertIn("supporting callbacks", plan)
        self.assertIn("preservation gate", plan)


if __name__ == "__main__":
    unittest.main()
