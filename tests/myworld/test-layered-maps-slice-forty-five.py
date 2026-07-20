#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
REGION_MANAGER = ROOT / "server/src/com/openrsc/server/model/world/region/RegionManager.java"
PATH_VALIDATION = ROOT / "server/src/com/openrsc/server/model/PathValidation.java"
OBSERVER = ROOT / "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
ARBITER = SERVER_COORDINATES / "LayeredRegionRetirementDecisionArbiter.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"


POINT_STUB = r'''
package com.openrsc.server.model;

public class Point {
    private final int x;
    private final int y;

    public Point(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public static Point location(int x, int y) {
        if (x < 0 || y < 0 || x > Short.MAX_VALUE || y > Short.MAX_VALUE) {
            throw new IllegalArgumentException("packed point out of range");
        }
        return new Point(x, y);
    }

    public int getX() { return x; }
    public int getY() { return y; }
}
'''


FIXTURE = r'''
package com.openrsc.server.model.world.coordinate;

public final class LayeredRegionRetirementDecisionArbiterFixture {
    public static void main(String[] args) {
        LayeredRegionInterestOwnershipLedger ownership =
            new LayeredRegionInterestOwnershipLedger();
        LayeredRegionRetirementEligibilityLedger retirement =
            new LayeredRegionRetirementEligibilityLedger(5L);
        LayeredRegionResidencyMirror residency = new LayeredRegionResidencyMirror();
        LayeredRegionRetirementDecisionArbiter arbiter =
            new LayeredRegionRetirementDecisionArbiter();
        check(residency.registerPackedRegion(4, 0), "register candidate source");

        LayeredRegionInterestOwnershipLedger.OpenedOwner first =
            ownership.openOwner(window(4), 1);
        retirement.observeOwnershipChange(first.getChange(), 1L);
        retirement.observeOwnershipChange(
            ownership.closeOwner(first.getOwnerToken()), 2L);
        LayeredRegionRetirementEligibilityLedger.Snapshot firstCandidate =
            snapshot(retirement, ownership, residency, key(4), 7L);
        check(firstCandidate.isRetirementEligible(), "first candidate eligible");

        LayeredRegionRetirementDecisionArbiter.Decision firstDecision =
            arbiter.evaluate(firstCandidate,
                snapshot(retirement, ownership, residency, key(4), 7L));
        check(firstDecision.isEligible(), "fresh candidate accepted");
        check(firstDecision.getDecisionState()
            == LayeredRegionRetirementDecisionArbiter.DecisionState.ELIGIBLE,
            "eligible decision state explicit");
        LayeredRegionRetirementDecisionArbiter.Decision repeatedDecision =
            arbiter.evaluate(firstCandidate,
                snapshot(retirement, ownership, residency, key(4), 8L));
        check(repeatedDecision.isEligible(),
            "repeated evaluation is idempotent evidence");
        check(repeatedDecision.getCandidateOwnershipVersion()
            == firstDecision.getCandidateOwnershipVersion()
            && repeatedDecision.getCurrentResidencyMirrorVersion()
                == firstDecision.getCurrentResidencyMirrorVersion(),
            "idempotent evaluation preserves source versions");

        LayeredRegionInterestOwnershipLedger.OpenedOwner reacquired =
            ownership.openOwner(window(4), 1);
        retirement.observeOwnershipChange(reacquired.getChange(), 8L);
        check(arbiter.evaluate(firstCandidate,
                snapshot(retirement, ownership, residency, key(4), 8L))
            .getDecisionState()
            == LayeredRegionRetirementDecisionArbiter.DecisionState.PINNED,
            "reacquisition rejects stale candidate");
        retirement.observeOwnershipChange(
            ownership.closeOwner(reacquired.getOwnerToken()), 9L);
        LayeredRegionRetirementEligibilityLedger.Snapshot coolingCandidate =
            snapshot(retirement, ownership, residency, key(4), 10L);
        check(arbiter.evaluate(coolingCandidate, coolingCandidate)
            .getDecisionState()
            == LayeredRegionRetirementDecisionArbiter.DecisionState
                .CANDIDATE_NOT_ELIGIBLE,
            "cooling snapshot cannot become a candidate");

        LayeredRegionRetirementEligibilityLedger.Snapshot secondCandidate =
            snapshot(retirement, ownership, residency, key(4), 14L);
        check(secondCandidate.isRetirementEligible(), "second release expires");
        check(arbiter.evaluate(firstCandidate, secondCandidate).getDecisionState()
            == LayeredRegionRetirementDecisionArbiter.DecisionState.RELEASE_CHANGED,
            "new release identity rejects old candidate");

        LayeredRegionInterestOwnershipLedger.OpenedOwner unrelated =
            ownership.openOwner(window(20), 1);
        retirement.observeOwnershipChange(unrelated.getChange(), 14L);
        LayeredRegionRetirementEligibilityLedger.Snapshot newerOwnership =
            snapshot(retirement, ownership, residency, key(4), 14L);
        LayeredRegionRetirementDecisionArbiter.Decision unrelatedDecision =
            arbiter.evaluate(secondCandidate, newerOwnership);
        check(unrelatedDecision.isEligible()
            && unrelatedDecision.getCurrentOwnershipVersion()
                > unrelatedDecision.getCandidateOwnershipVersion(),
            "unrelated ownership versions do not invalidate the same release");
        retirement.observeOwnershipChange(
            ownership.closeOwner(unrelated.getOwnerToken()), 14L);

        check(residency.registerPackedRegion(10, 0),
            "change residency version without changing candidate sources");
        LayeredRegionRetirementEligibilityLedger.Snapshot changedResidency =
            snapshot(retirement, ownership, residency, key(4), 14L);
        check(changedResidency.isRetirementEligible(),
            "candidate stays substantively eligible after unrelated residency change");
        check(arbiter.evaluate(secondCandidate, changedResidency).getDecisionState()
            == LayeredRegionRetirementDecisionArbiter.DecisionState.RESIDENCY_CHANGED,
            "residency version change requires a fresh candidate");
        LayeredRegionRetirementEligibilityLedger.Snapshot refreshedCandidate =
            snapshot(retirement, ownership, residency, key(4), 14L);
        check(arbiter.evaluate(refreshedCandidate,
                snapshot(retirement, ownership, residency, key(4), 14L))
            .isEligible(), "refreshed residency candidate accepted");

        check(residency.unregisterPackedRegion(4, 0),
            "remove candidate source");
        check(arbiter.evaluate(refreshedCandidate,
                snapshot(retirement, ownership, residency, key(4), 14L))
            .getDecisionState()
            == LayeredRegionRetirementDecisionArbiter.DecisionState.NOT_RESIDENT,
            "nonresident current state is rejected");

        LayeredRegionInterestOwnershipLedger foreignOwnership =
            new LayeredRegionInterestOwnershipLedger();
        LayeredRegionRetirementEligibilityLedger foreignRetirement =
            new LayeredRegionRetirementEligibilityLedger(5L);
        LayeredRegionResidencyMirror foreignResidency =
            new LayeredRegionResidencyMirror();
        check(foreignResidency.registerPackedRegion(4, 0),
            "register foreign source");
        LayeredRegionInterestOwnershipLedger.OpenedOwner foreignOwner =
            foreignOwnership.openOwner(window(4), 1);
        foreignRetirement.observeOwnershipChange(foreignOwner.getChange(), 1L);
        foreignRetirement.observeOwnershipChange(
            foreignOwnership.closeOwner(foreignOwner.getOwnerToken()), 2L);
        LayeredRegionRetirementEligibilityLedger.Snapshot foreignCandidate =
            snapshot(foreignRetirement, foreignOwnership, foreignResidency, key(4), 7L);
        check(arbiter.evaluate(foreignCandidate, refreshedCandidate).getDecisionState()
            == LayeredRegionRetirementDecisionArbiter.DecisionState.FOREIGN_PROJECTION,
            "foreign projection candidate rejected");

        WorldRegionKey deepKey =
            new WorldRegionKey(WorldSpaceId.GLOBAL, -2, 4, 0);
        LayeredRegionRetirementEligibilityLedger.Snapshot unsupported =
            snapshot(retirement, ownership, residency, deepKey, 14L);
        check(arbiter.evaluate(unsupported, unsupported).getDecisionState()
            == LayeredRegionRetirementDecisionArbiter.DecisionState
                .CANDIDATE_NOT_ELIGIBLE,
            "unsupported Region cannot become a candidate");
        expectIllegal(() -> arbiter.evaluate(firstCandidate, unsupported));
        expectNull(() -> arbiter.evaluate(null, refreshedCandidate));
        expectNull(() -> arbiter.evaluate(refreshedCandidate, null));
    }

    private static LayeredRegionRetirementEligibilityLedger.Snapshot snapshot(
            LayeredRegionRetirementEligibilityLedger retirement,
            LayeredRegionInterestOwnershipLedger ownership,
            LayeredRegionResidencyMirror residency,
            WorldRegionKey key,
            long tick) {
        return retirement.snapshot(
            ownership.snapshot(key), residency.snapshot(key), tick);
    }

    private static WorldRegionWindow window(int x) {
        return new WorldRegionWindow(WorldSpaceId.GLOBAL, 0, x, 0, x, 0);
    }

    private static WorldRegionKey key(int x) {
        return new WorldRegionKey(WorldSpaceId.GLOBAL, 0, x, 0);
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected refusal.
        }
    }

    private static void expectNull(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected refusal.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
'''


class LayeredMapsSliceFortyFiveTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-forty-five-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "LayeredRegionRetirementDecisionArbiterFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes), str(point),
                str(fixture),
                *(str(path) for path in sorted(SERVER_COORDINATES.glob("*.java"))),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_atomic_recheck_refuses_stale_racy_and_foreign_candidates(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "LayeredRegionRetirementDecisionArbiterFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_manager_boundary_is_bounded_atomic_and_incapable_of_eviction(self):
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        path_validation = PATH_VALIDATION.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        arbiter = ARBITER.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn("LayeredRegionRetirementDecisionArbiter", manager)
        self.assertIn("evaluateLayeredRegionRetirementCandidate(", manager)
        self.assertIn("evaluateLayeredRegionRetirementCandidates(", manager)
        self.assertIn("synchronized (layeredRegionLifecycleLock)", manager)
        self.assertIn("candidates.size() > maximumRegions", manager)
        self.assertIn(
            "maximumRegions > MAX_LAYERED_REGIONS_PER_INTEREST_OWNER", manager
        )
        self.assertIn("LinkedHashSet<WorldRegionKey> uniqueKeys", manager)
        self.assertIn("sharesProjectionWith", arbiter)
        self.assertIn("DecisionState.RELEASE_CHANGED", arbiter)
        self.assertIn("DecisionState.RESIDENCY_CHANGED", arbiter)
        self.assertNotIn(
            "com.openrsc.server.model.world.region.Region", arbiter
        )
        self.assertNotIn("getRegion(", arbiter)
        self.assertNotIn("unregisterPackedRegion", arbiter)
        self.assertNotIn(".unload(", arbiter)
        self.assertNotIn("LayeredRegionRetirementDecisionArbiter", path_validation)
        self.assertNotIn("LayeredRegionRetirementDecisionArbiter", observer)

        boundary = manager.split(
            "/**\n\t * Atomically rechecks one earlier retirement candidate", 1
        )[1].split(
            "/**\n\t * Compares one bounded logical interest change", 1
        )[0]
        self.assertNotIn("getRegion(", boundary)
        self.assertNotIn("registerPackedRegion", boundary)
        self.assertNotIn("unregisterPackedRegion", boundary)
        self.assertNotIn(".unload(", boundary)
        self.assertIn("### Slice 45: Dormant Region retirement decision arbiter", plan)


if __name__ == "__main__":
    unittest.main()
