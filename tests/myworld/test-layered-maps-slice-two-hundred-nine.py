#!/usr/bin/env python3
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
INVENTORY = COORDINATES / (
    "LayeredPackedRegionEventOwnershipInventory.java"
)
HANDLER = ROOT / (
    "server/src/com/openrsc/server/event/rsc/handler/"
    "GameEventHandler.java"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


POINT_STUB = r"""
package com.openrsc.server.model;

public class Point {
    private final int x;
    private final int y;
    public Point(int x, int y) { this.x = x; this.y = y; }
    public static Point location(int x, int y) {
        return new Point(x, y);
    }
    public int getX() { return x; }
    public int getY() { return y; }
}
"""


FIXTURE = r"""
package com.openrsc.server.model.world.coordinate;

import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionEventOwnershipInventory.*;
import java.util.Collections;

public final class EventTypeIdentityFixture {
    public static void main(String[] args) {
        EventTypeIdentity identity = EventTypeIdentity.of(
            "com.openrsc.server.model.world.World$12",
            "com.openrsc.server.model.world.World",
            "com.openrsc.server.event.rsc.SingleTickEvent",
            true, false, false);
        EventState state = EventState.of(
            0, 1L, identity, OwnerKind.NONE, null,
            AttributionKind.UNATTRIBUTED, false, 5L, 2,
            Collections.emptyList(),
            EventRestorationState.unavailable(), false);
        LayeredPackedRegionEventOwnershipInventory inventory =
            LayeredPackedRegionEventOwnershipInventory.inventory(
                1L, 10L, "00000000-0000-0000-0000-000000000209",
                Collections.singletonList(PackedSource.of(2, 10)),
                Collections.singletonList(state), 1, 1, 0);
        EventTypeIdentity detached =
            inventory.getEvents().get(0).getEventTypeIdentity();
        check(detached.getRuntimeTypeName().endsWith("World$12")
                && detached.getFamilyTypeName().endsWith("World")
                && detached.getDirectSupertypeName().endsWith(
                    "SingleTickEvent")
                && detached.isAnonymousType()
                && !detached.isLocalType()
                && !detached.isSyntheticType()
                && detached.isCaptured()
                && detached.isDetachedValue()
                && !detached.isClassHandle()
                && !detached.isCallbackHandle()
                && !detached.isSchedulerHandle()
                && !detached.isLifecycleAuthority(),
            "detached event type identity drifted");

        EventState legacy = EventState.of(
            0, 2L, OwnerKind.NONE, AttributionKind.UNATTRIBUTED,
            false, 0L, 0, Collections.emptyList());
        check(!legacy.getEventTypeIdentity().isCaptured()
                && legacy.getEventTypeIdentity().getRuntimeTypeName()
                    .equals("unknown"),
            "legacy fixture identity is not explicit");

        expectIllegal(() -> EventTypeIdentity.of(
            "", "family", "base", false, false, false));
        expectIllegal(() -> EventTypeIdentity.of(
            repeat('a', 513), "family", "base",
            false, false, false));
        expectIllegal(() -> EventTypeIdentity.of(
            "runtime\n", "family", "base", false, false, false));
        System.out.println("detached-event-type-identity-ok");
    }

    private static void expectIllegal(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static String repeat(char value, int count) {
        char[] values = new char[count];
        java.util.Arrays.fill(values, value);
        return new String(values);
    }
}
"""


class LayeredMapsSliceTwoHundredNineTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-event-type-identity-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(
            textwrap.dedent(POINT_STUB).lstrip(), encoding="utf-8"
        )
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "EventTypeIdentityFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(
            textwrap.dedent(FIXTURE).lstrip(), encoding="utf-8"
        )
        subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes),
                str(point), str(fixture),
                *(str(path) for path in sorted(COORDINATES.glob("*.java"))),
            ],
            cwd=ROOT, check=True, capture_output=True, text=True,
        )
        cls.fixture_run = subprocess.run(
            [
                "java", "-cp", str(cls.classes),
                (
                    "com.openrsc.server.model.world.coordinate."
                    "EventTypeIdentityFixture"
                ),
            ],
            cwd=ROOT, check=True, capture_output=True, text=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_event_type_identity_is_detached_and_bounded(self):
        self.assertIn(
            "detached-event-type-identity-ok",
            self.fixture_run.stdout,
        )

    def test_runtime_seam_copies_names_without_retaining_class_handles(self):
        handler = HANDLER.read_text(encoding="utf-8")
        source = INVENTORY.read_text(encoding="utf-8")
        self.assertIn("detachEventTypeIdentity(event)", handler)
        self.assertIn("runtimeType.getName()", handler)
        identity = source.split(
            "public static final class EventTypeIdentity {", 1
        )[1].split("/**", 1)[0]
        self.assertNotIn("Class<?>", identity)
        self.assertNotIn("GameTickEvent ", identity)
        self.assertIn("isClassHandle() { return false; }", identity)

    def test_plan_records_slice_209_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 209: Detached scheduler event-type identity",
            plan,
        )
        self.assertIn("UNATTRIBUTED", plan)
        self.assertIn("no schema change", plan)


if __name__ == "__main__":
    unittest.main()
