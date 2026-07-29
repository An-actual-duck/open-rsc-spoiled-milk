#!/usr/bin/env python3
import runpy
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
REQUIREMENTS = COORDINATES / (
    "LayeredPackedRegionNpcOwnerPreservationRequirements.java"
)
HANDLER = ROOT / "server/src/com/openrsc/server/event/rsc/handler"
SCOPE = HANDLER / "GameTickEventNpcOwnerPreservationScope.java"
ADAPTER = HANDLER / "GameTickEventNpcOwnerPreservationBoundary.java"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)
SLICE_164 = runpy.run_path(str(ROOT / (
    "tests/myworld/test-layered-maps-slice-one-hundred-sixty-four.py"
)))


REQUIREMENTS_STUB = r'''
package com.openrsc.server.model.world.coordinate;

import java.util.Arrays;
import java.util.List;

public final class LayeredPackedRegionNpcOwnerPreservationRequirements {
    private final boolean complete;

    public LayeredPackedRegionNpcOwnerPreservationRequirements(
        boolean complete) {
        this.complete = complete;
    }

    public boolean isNpcRequirementSetComplete() { return complete; }
    public int getEventLinkCount() { return 3; }
    public int getUniqueNpcOwnerCount() { return 2; }
    public long getGeneration() { return 9L; }
    public long getEventObservedAtTick() { return 12L; }
    public String getSchedulerInstanceIdentity() {
        return "00000000-0000-0000-0000-000000000169";
    }
    public int getSelectedSourceCount() { return 2; }
    public List<SelectedSource> getSelectedSources() {
        return Arrays.asList(
            new SelectedSource(4, 7), new SelectedSource(5, 7));
    }

    public static final class SelectedSource {
        private final int x;
        private final int y;
        public SelectedSource(int x, int y) {
            this.x = x;
            this.y = y;
        }
        public int getPackedRegionX() { return x; }
        public int getPackedRegionY() { return y; }
    }
}
'''


SCOPE_FIXTURE = r'''
package com.openrsc.server.event.rsc.handler;

import com.openrsc.server.model.world.coordinate
    .LayeredPackedRegionNpcOwnerPreservationRequirements;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NpcOwnerPreservationScopeFixture {
    public static void main(String[] args) throws Exception {
        LayeredPackedRegionNpcOwnerPreservationRequirements requirements =
            new LayeredPackedRegionNpcOwnerPreservationRequirements(true);
        GameTickEventNpcOwnerPreservationScope scope =
            GameTickEventNpcOwnerPreservationScope.open(
                requirements, 3, 2, true, true);
        check(scope.getGeneration() == 9L
                && scope.getRequirementsObservedAtTick() == 12L
                && scope.getSchedulerInstanceIdentity().endsWith("0169")
                && scope.getRequiredEventLinkCount() == 3
                && scope.getRequiredOwnerCount() == 2
                && scope.getSelectedSources().size() == 2
                && scope.getSelectedSources().get(0).getPackedRegionX() == 4
                && scope.getSelectedSources().get(0).getPackedRegionY() == 7
                && scope.getSelectedSources().get(1).getPackedRegionX() == 5
                && scope.getSelectedSources().get(1).getPackedRegionY() == 7
                && scope.isCompleteBoundaryHeld()
                && scope.isPointInTimeScope()
                && !scope.isRuntimeHandleRetained()
                && !scope.isReusablePermit(),
            "active scope retains exact detached boundary identity");
        expectUnsupported(() -> scope.getSelectedSources().clear());

        AtomicBoolean otherThreadRefused = new AtomicBoolean();
        Thread other = new Thread(() -> {
            try {
                scope.getGeneration();
            } catch (IllegalStateException expected) {
                otherThreadRefused.set(true);
            }
        }, "scope-leak-attempt");
        other.start();
        other.join(2000L);
        check(!other.isAlive() && otherThreadRefused.get(),
            "scope must be confined to its boundary thread");

        scope.invalidate();
        expectIllegalState(() -> scope.getRequiredOwnerCount());
        expectIllegalState(() -> scope.invalidate());

        expectIllegalArgument(() ->
            GameTickEventNpcOwnerPreservationScope.open(
                requirements, 2, 2, true, true));
        expectIllegalArgument(() ->
            GameTickEventNpcOwnerPreservationScope.open(
                requirements, 3, 1, true, true));
        expectIllegalArgument(() ->
            GameTickEventNpcOwnerPreservationScope.open(
                requirements, 3, 2, false, true));
        expectIllegalArgument(() ->
            GameTickEventNpcOwnerPreservationScope.open(
                requirements, 3, 2, true, false));
        expectIllegalArgument(() ->
            GameTickEventNpcOwnerPreservationScope.open(
                new LayeredPackedRegionNpcOwnerPreservationRequirements(false),
                3, 2, true, true));
    }

    private static void expectIllegalArgument(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void expectIllegalState(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            // Expected.
        }
    }

    private static void expectUnsupported(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("expected UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
'''


def build_requirements_fixture():
    fixture = SLICE_164["build_fixture"]()
    return fixture.replace(
        "&& requirements.hasSeparateNonNpcBlockers(),",
        "&& requirements.hasSeparateNonNpcBlockers()\n"
        "                && requirements.getSelectedSources().size() == 1\n"
        "                && requirements.getSelectedSources().get(0)\n"
        "                    .getPackedRegionX() == 4\n"
        "                && requirements.getSelectedSources().get(0)\n"
        "                    .getPackedRegionY() == 0,",
    )


class LayeredMapsSliceOneHundredSixtyNineTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-npc-owner-preservation-scope-"
        )
        cls.temp = Path(cls.compile_temp.name)

        cls.scope_classes = cls.temp / "scope-classes"
        cls.scope_classes.mkdir()
        requirements_stub = cls.temp / (
            "scope-src/com/openrsc/server/model/world/coordinate/"
            "LayeredPackedRegionNpcOwnerPreservationRequirements.java"
        )
        scope_fixture = cls.temp / (
            "scope-src/com/openrsc/server/event/rsc/handler/"
            "NpcOwnerPreservationScopeFixture.java"
        )
        requirements_stub.parent.mkdir(parents=True, exist_ok=True)
        scope_fixture.parent.mkdir(parents=True, exist_ok=True)
        requirements_stub.write_text(REQUIREMENTS_STUB, encoding="utf-8")
        scope_fixture.write_text(SCOPE_FIXTURE, encoding="utf-8")
        subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.scope_classes),
                str(requirements_stub), str(SCOPE), str(scope_fixture),
            ],
            cwd=ROOT,
            check=True,
        )

        cls.requirements_classes = cls.temp / "requirements-classes"
        cls.requirements_classes.mkdir()
        point = cls.temp / "requirements-src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(
            SLICE_164["SLICE_161"]["SLICE_71"]["POINT_STUB"],
            encoding="utf-8",
        )
        requirements_fixture = cls.temp / (
            "requirements-src/com/openrsc/server/model/world/coordinate/"
            "ActiveNpcResidencyFixture.java"
        )
        requirements_fixture.parent.mkdir(parents=True, exist_ok=True)
        requirements_fixture.write_text(
            build_requirements_fixture(), encoding="utf-8"
        )
        subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.requirements_classes),
                str(point), str(requirements_fixture),
                *(str(path) for path in sorted(COORDINATES.glob("*.java"))),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_scope_is_thread_confined_and_invalidated(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.scope_classes),
                "com.openrsc.server.event.rsc.handler."
                "NpcOwnerPreservationScopeFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_requirements_retain_exact_selected_sources(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.requirements_classes),
                "com.openrsc.server.model.world.coordinate."
                "ActiveNpcResidencyFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_scope_is_opened_only_inside_the_complete_runtime_boundary(self):
        requirements = REQUIREMENTS.read_text(encoding="utf-8")
        scope = SCOPE.read_text(encoding="utf-8")
        adapter = ADAPTER.read_text(encoding="utf-8")

        self.assertIn("List<SelectedSource> selectedSources", requirements)
        self.assertIn("inventory.getSources()", requirements)
        self.assertIn("withinPreservationScope(", adapter)
        self.assertIn("scopeOperation.execute(scope)", adapter)
        self.assertIn("scope.invalidate()", adapter)
        self.assertLess(
            adapter.index("capture.ownerStates.addAll(revalidated)"),
            adapter.index("GameTickEventNpcOwnerPreservationScope.open("),
        )
        self.assertLess(
            adapter.index("GameTickEventNpcOwnerPreservationScope.open("),
            adapter.index("scopeOperation.execute(scope)"),
        )
        self.assertIn("Thread boundaryThread", scope)
        self.assertIn("NPC owner preservation scope is thread-confined", scope)
        self.assertNotIn(
            "import com.openrsc.server.event.rsc.GameTickEvent", scope
        )
        self.assertNotIn("private final GameTickEvent", scope)
        self.assertNotIn("import com.openrsc.server.model.entity.npc", scope)
        self.assertNotIn("private final Npc", scope)
        self.assertNotIn(
            "import com.openrsc.server.model.world.region", scope
        )

    def test_living_plan_records_slice_one_hundred_sixty_nine(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 169: Source-bound NPC preservation scope",
            plan,
        )
        self.assertIn("invalidated before the enclosing gates release", plan)
        self.assertIn("not a reusable preservation fact", plan)


if __name__ == "__main__":
    unittest.main()
