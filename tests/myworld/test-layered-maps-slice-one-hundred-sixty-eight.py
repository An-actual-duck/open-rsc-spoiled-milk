#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
EVENT = ROOT / "server/src/com/openrsc/server/event/rsc/GameTickEvent.java"
NPC = ROOT / "server/src/com/openrsc/server/model/entity/npc/Npc.java"
NPC_GATE = ROOT / (
    "server/src/com/openrsc/server/model/entity/npc/"
    "NpcOwnerPreservationLifecycleGate.java"
)
STORE = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/GameTickEventStore.java"
)
ADAPTER = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameTickEventNpcOwnerPreservationBoundary.java"
)
DEVELOPMENT = ROOT / (
    "server/plugins/com/openrsc/server/plugins/authentic/commands/"
    "Development.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


EVENT_FIXTURE = r'''
package com.openrsc.server.event.rsc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EventPreservationGateHighCardinalityFixture {
    private static final int REAL_OWNER_CARDINALITY = 449;

    public static void main(String[] args) {
        List<GameTickEventOwnerPreservationLifecycleGate> gates =
            new ArrayList<GameTickEventOwnerPreservationLifecycleGate>();
        for (int index = 0; index < REAL_OWNER_CARDINALITY; index++) {
            gates.add(new GameTickEventOwnerPreservationLifecycleGate());
        }

        AtomicBoolean invoked = new AtomicBoolean();
        check(GameTickEventOwnerPreservationLifecycleGate
                .withinPreservationBoundaries(gates, boundary -> {
                    check(boundary.isCompleteSetHeld(),
                        "complete event set was not held");
                    check(boundary.getEventCount() == REAL_OWNER_CARDINALITY,
                        "event set count changed");
                    invoked.set(true);
                }),
            "449-event iterative boundary was refused");
        check(invoked.get(), "449-event operation was not invoked");

        gates.get(225).beginOperation();
        check(!GameTickEventOwnerPreservationLifecycleGate
                .withinPreservationBoundaries(gates, boundary -> {
                    throw new AssertionError("busy event set was entered");
                }),
            "busy event set must refuse");
        check(GameTickEventOwnerPreservationLifecycleGate
                .withinPreservationBoundaries(
                    Collections.singletonList(gates.get(0)), boundary -> {
                        check(boundary.getEventCount() == 1,
                            "partial refusal leaked an earlier event gate");
                    }),
            "partial event-set refusal did not release earlier gates");
        gates.get(225).endOperation();

        AtomicBoolean sameThreadReentryRefused = new AtomicBoolean();
        check(GameTickEventOwnerPreservationLifecycleGate
                .withinPreservationBoundaries(gates, boundary -> {
                    try {
                        gates.get(0).beginOperation();
                    } catch (IllegalStateException expected) {
                        sameThreadReentryRefused.set(true);
                    }
                }),
            "reusable event set was refused");
        check(sameThreadReentryRefused.get(),
            "event set allowed same-thread lifecycle reentry");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
'''


NPC_FIXTURE = r'''
package com.openrsc.server.model.entity.npc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NpcPreservationGateHighCardinalityFixture {
    private static final int REAL_OWNER_CARDINALITY = 449;

    public static void main(String[] args) {
        List<NpcOwnerPreservationLifecycleGate> gates =
            new ArrayList<NpcOwnerPreservationLifecycleGate>();
        for (int index = 0; index < REAL_OWNER_CARDINALITY; index++) {
            gates.add(new NpcOwnerPreservationLifecycleGate());
        }

        AtomicBoolean invoked = new AtomicBoolean();
        check(NpcOwnerPreservationLifecycleGate
                .withinPreservationBoundaries(gates, boundary -> {
                    check(boundary.isCompleteSetHeld(),
                        "complete NPC set was not held");
                    check(boundary.getOwnerCount() == REAL_OWNER_CARDINALITY,
                        "NPC set count changed");
                    invoked.set(true);
                }),
            "449-NPC iterative boundary was refused");
        check(invoked.get(), "449-NPC operation was not invoked");

        gates.get(225).beginOperation();
        check(!NpcOwnerPreservationLifecycleGate
                .withinPreservationBoundaries(gates, boundary -> {
                    throw new AssertionError("busy NPC set was entered");
                }),
            "busy NPC set must refuse");
        check(NpcOwnerPreservationLifecycleGate
                .withinPreservationBoundaries(
                    Collections.singletonList(gates.get(0)), boundary -> {
                        check(boundary.getOwnerCount() == 1,
                            "partial refusal leaked an earlier NPC gate");
                    }),
            "partial NPC-set refusal did not release earlier gates");
        gates.get(225).endOperation();

        AtomicBoolean sameThreadReentryRefused = new AtomicBoolean();
        check(NpcOwnerPreservationLifecycleGate
                .withinPreservationBoundaries(gates, boundary -> {
                    try {
                        gates.get(0).beginOperation();
                    } catch (IllegalStateException expected) {
                        sameThreadReentryRefused.set(true);
                    }
                }),
            "reusable NPC set was refused");
        check(sameThreadReentryRefused.get(),
            "NPC set allowed same-thread lifecycle reentry");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
'''


class LayeredMapsSliceOneHundredSixtyEightTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-owner-preservation-high-cardinality-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        event_fixture = cls.temp / (
            "src/com/openrsc/server/event/rsc/"
            "EventPreservationGateHighCardinalityFixture.java"
        )
        npc_fixture = cls.temp / (
            "src/com/openrsc/server/model/entity/npc/"
            "NpcPreservationGateHighCardinalityFixture.java"
        )
        event_fixture.parent.mkdir(parents=True, exist_ok=True)
        npc_fixture.parent.mkdir(parents=True, exist_ok=True)
        event_gate = cls.temp / (
            "src/com/openrsc/server/event/rsc/"
            "GameTickEventOwnerPreservationLifecycleGate.java"
        )
        event_source = EVENT.read_text(encoding="utf-8")
        event_gate_marker = (
            "/**\n"
            " * Per-event exclusion gate for a short owner-preservation"
            " observation."
        )
        event_gate.write_text(
            "package com.openrsc.server.event.rsc;\n\n"
            "import java.util.IdentityHashMap;\n"
            "import java.util.List;\n\n"
            + event_source[event_source.index(event_gate_marker):],
            encoding="utf-8",
        )
        event_fixture.write_text(EVENT_FIXTURE, encoding="utf-8")
        npc_fixture.write_text(NPC_FIXTURE, encoding="utf-8")
        subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(event_gate), str(NPC_GATE),
                str(event_fixture), str(npc_fixture),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_event_and_npc_sets_are_iterative_at_real_cardinality(self):
        for fixture in (
            "com.openrsc.server.event.rsc."
            "EventPreservationGateHighCardinalityFixture",
            "com.openrsc.server.model.entity.npc."
            "NpcPreservationGateHighCardinalityFixture",
        ):
            result = subprocess.run(
                ["java", "-Xss256k", "-cp", str(self.classes), fixture],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(0, result.returncode, result.stderr)

    def test_runtime_adapter_has_no_cardinality_recursion(self):
        event = EVENT.read_text(encoding="utf-8")
        npc = NPC.read_text(encoding="utf-8")
        store = STORE.read_text(encoding="utf-8")
        adapter = ADAPTER.read_text(encoding="utf-8")
        development = DEVELOPMENT.read_text(encoding="utf-8")

        self.assertIn(
            "withinOwnerPreservationLifecycleBoundaries(", event
        )
        self.assertIn(
            "GameTickEventOwnerPreservationLifecycleGate", event
        )
        self.assertIn(
            "withinLayeredOwnerPreservationLifecycleBoundaries(", npc
        )
        self.assertIn("withValidatedRegistrationSetFence(", store)
        self.assertIn(
            "GameTickEvent.withinOwnerPreservationLifecycleBoundaries(",
            adapter,
        )
        self.assertIn(
            "Npc.withinLayeredOwnerPreservationLifecycleBoundaries(",
            adapter,
        )
        self.assertNotIn("captureNestedBoundaries(", adapter)
        self.assertNotIn(
            "captureNestedNpcLifecycleBoundaries(", adapter
        )
        self.assertIn("catch (RuntimeException failure)", development)
        self.assertIn("catch (StackOverflowError failure)", development)
        self.assertIn("The trace remains active.", development)

    def test_living_plan_records_slice_one_hundred_sixty_eight(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 168: High-cardinality preservation-boundary correction",
            plan,
        )
        self.assertIn("449 event fences", plan)
        self.assertIn("bounded iterative acquisition", plan)


if __name__ == "__main__":
    unittest.main()
