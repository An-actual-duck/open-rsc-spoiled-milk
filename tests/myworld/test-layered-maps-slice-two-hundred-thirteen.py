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
PLUGIN = ROOT / (
    "server/src/com/openrsc/server/event/rsc/PluginTickEvent.java"
)
REDUCER = ROOT / (
    "server/src/com/openrsc/server/model/world/region/"
    "LayeredPackedRegionSchedulerBlockerFamilyInventory.java"
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

public final class EventExecutionContextIdentityFixture {
    public static void main(String[] args) {
        EventExecutionContextIdentity none =
            EventExecutionContextIdentity.none();
        check(none.getKind() == EventExecutionContextKind.NONE
                && none.getContextName() == null
                && !none.isWalkToActionBound(),
            "empty execution context drifted");

        EventExecutionContextIdentity plugin =
            EventExecutionContextIdentity.pluginEntryPoint(
                "Development.onCommand", true);
        EventState state = EventState.of(
            0, 1L,
            EventTypeIdentity.of(
                "com.openrsc.server.event.rsc.PluginTickEvent",
                "com.openrsc.server.event.rsc.PluginTickEvent",
                "com.openrsc.server.event.rsc.GameTickEvent",
                false, false, false),
            plugin, OwnerKind.PLAYER, null,
            AttributionKind.OWNER_POSITION_HINT,
            true, 0L, 0,
            Collections.singletonList(SpatialReference.of(
                SpatialRole.OWNER_CURRENT_POSITION, 120, 620)),
            EventRestorationState.unavailable(), false);
        LayeredPackedRegionEventOwnershipInventory inventory =
            LayeredPackedRegionEventOwnershipInventory.inventory(
                1L, 10L, "00000000-0000-0000-0000-000000000213",
                Collections.singletonList(PackedSource.of(2, 10)),
                Collections.singletonList(state), 1, 1, 1);
        EventExecutionContextIdentity detached = inventory.getEvents()
            .get(0).getEventExecutionContextIdentity();
        check(detached.getKind()
                    == EventExecutionContextKind.PLUGIN_ENTRY_POINT
                && detached.getContextName()
                    .equals("Development.onCommand")
                && detached.isWalkToActionBound()
                && detached.isCaptured()
                && detached.isDetachedValue()
                && !detached.isPluginTaskHandle()
                && !detached.isScriptDataRetained()
                && !detached.isActionHandle()
                && !detached.isCallbackHandle()
                && !detached.isSchedulerHandle()
                && !detached.isLifecycleAuthority(),
            "detached plugin execution context drifted");

        expectIllegal(() ->
            EventExecutionContextIdentity.pluginEntryPoint("", false));
        expectIllegal(() ->
            EventExecutionContextIdentity.pluginEntryPoint(
                repeat('a', 513), false));
        expectIllegal(() ->
            EventExecutionContextIdentity.pluginEntryPoint(
                "Default.onTimedEvent\n", false));
        System.out.println("detached-event-execution-context-ok");
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


class LayeredMapsSliceTwoHundredThirteenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-event-execution-context-"
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
            "EventExecutionContextIdentityFixture.java"
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
                    "EventExecutionContextIdentityFixture"
                ),
            ],
            cwd=ROOT, check=False, capture_output=True, text=True,
        )
        if cls.fixture_run.returncode:
            raise AssertionError(cls.fixture_run.stderr)

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_execution_context_is_detached_and_bounded(self):
        self.assertIn(
            "detached-event-execution-context-ok",
            self.fixture_run.stdout,
        )

    def test_runtime_seam_copies_only_plugin_code_identity(self):
        handler = HANDLER.read_text(encoding="utf-8")
        plugin = PLUGIN.read_text(encoding="utf-8")
        identity = INVENTORY.read_text(encoding="utf-8").split(
            "public static final class EventExecutionContextIdentity {", 1
        )[1].split("/**", 1)[0]
        self.assertIn(
            "detachEventExecutionContextIdentity(event)", handler
        )
        self.assertIn("pluginEvent.getPluginName()", handler)
        self.assertIn("pluginEvent.isWalkToActionBound()", handler)
        self.assertIn("return walkToAction != null;", plugin)
        for forbidden in (
            "PluginTask ", "WalkToAction ", "GameTickEvent ", "Mob ",
            "Player ", "Object[]", "ScriptContext",
        ):
            self.assertNotIn(forbidden, identity)

    def test_blocker_family_key_includes_execution_context(self):
        reducer = REDUCER.read_text(encoding="utf-8")
        self.assertIn("executionContextKind", reducer)
        self.assertIn("executionContextName", reducer)
        self.assertIn("walkToActionBound", reducer)
        self.assertIn(
            "isEventExecutionContextIdentityComplete() { return true; }",
            reducer,
        )

    def test_plan_records_slice_213_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 213: Detached plugin execution-context identity",
            plan,
        )
        self.assertIn("no schema change", plan)


if __name__ == "__main__":
    unittest.main()
