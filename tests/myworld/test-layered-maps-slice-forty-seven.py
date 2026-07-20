#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER_COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
REGION_MANAGER = ROOT / "server/src/com/openrsc/server/model/world/region/RegionManager.java"
READINESS = SERVER_COORDINATES / "LayeredPackedRegionRetirementReadiness.java"
PATH_VALIDATION = ROOT / "server/src/com/openrsc/server/model/PathValidation.java"
OBSERVER = ROOT / "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class LayeredPackedRegionRetirementReadinessFixture {
    public static void main(String[] args) {
        ordinarySourceBecomesReady();
        crossLevelSourceRequiresEveryDecision();
        partialDomainSourceRemainsBlocked();
        emptyAndInvalidInputsRemainBounded();
    }

    private static void ordinarySourceBecomesReady() {
        Scenario scenario = new Scenario();
        check(scenario.residency.registerPackedRegion(4, 0),
            "register ordinary source");
        WorldRegionKey key = key(0, 4, 0);
        LayeredRegionRetirementEligibilityLedger.Snapshot candidate =
            scenario.release(key, 1L, 2L, 7L);
        LayeredRegionRetirementDecisionArbiter.Decision decision =
            scenario.currentDecision(candidate, 7L);
        LayeredPackedRegionRetirementReadiness readiness =
            LayeredPackedRegionRetirementReadiness.fromDecisions(
                Collections.singletonList(decision), 1, 2);
        check(readiness.getObservedAtTick() == 7L,
            "ordinary readiness preserves atomic tick");
        check(readiness.getLogicalDecisionCount() == 1,
            "ordinary logical decision count");
        check(readiness.getSourceCount() == 1
            && readiness.getReadySourceCount() == 1
            && readiness.getBlockedSourceCount() == 0,
            "ordinary source ready");
        LayeredPackedRegionRetirementReadiness.SourceReadiness source =
            readiness.getSources().get(0);
        check(source.getPackedRegionX() == 4
            && source.getPackedRegionY() == 0
            && source.getCoveredLogicalRegions().equals(
                Collections.singletonList(key))
            && source.getMissingLogicalDecisions().isEmpty()
            && source.getRefusedLogicalDecisions().isEmpty()
            && source.getPartialResidencyLogicalDecisions().isEmpty()
            && !source.spansLevels()
            && source.getSourceState()
                == LayeredPackedRegionRetirementReadiness.SourceState.READY,
            "ordinary source evidence exact");
        expectImmutable(readiness.getSources());
        expectImmutable(source.getCoveredLogicalRegions());
    }

    private static void crossLevelSourceRequiresEveryDecision() {
        Scenario scenario = new Scenario();
        check(scenario.residency.registerPackedRegion(4, 19),
            "register level-boundary source");
        check(scenario.residency.registerPackedRegion(4, 20),
            "register adjacent assembly source");
        LegacyPackedRegionCoverage coverage =
            LegacyPackedRegionCoverage.fromPackedRegionCoordinates(4, 19);
        check(coverage.isFullyInsideLegacyDomain()
            && coverage.spansLevels()
            && coverage.getCoveredKeys().size() == 2,
            "fixture source crosses one legacy level boundary");
        WorldRegionKey firstKey = coverage.getCoveredKeys().get(0);
        WorldRegionKey secondKey = coverage.getCoveredKeys().get(1);

        LayeredRegionInterestOwnershipLedger.OpenedOwner first =
            scenario.open(firstKey, 1L);
        LayeredRegionInterestOwnershipLedger.OpenedOwner second =
            scenario.open(secondKey, 1L);
        scenario.close(first, 2L);
        scenario.close(second, 2L);
        LayeredRegionRetirementEligibilityLedger.Snapshot firstCandidate =
            scenario.snapshot(firstKey, 7L);
        LayeredRegionRetirementEligibilityLedger.Snapshot secondCandidate =
            scenario.snapshot(secondKey, 7L);
        check(firstCandidate.isRetirementEligible()
            && secondCandidate.isRetirementEligible(),
            "both level fragments expire");
        LayeredRegionRetirementDecisionArbiter.Decision firstDecision =
            scenario.currentDecision(firstCandidate, 7L);
        LayeredRegionRetirementDecisionArbiter.Decision secondDecision =
            scenario.currentDecision(secondCandidate, 7L);

        LayeredPackedRegionRetirementReadiness missing =
            LayeredPackedRegionRetirementReadiness.fromDecisions(
                Collections.singletonList(firstDecision), 1, 2);
        LayeredPackedRegionRetirementReadiness.SourceReadiness missingSource =
            missing.getSources().get(0);
        check(missingSource.getSourceState()
            == LayeredPackedRegionRetirementReadiness.SourceState
                .INCOMPLETE_COVERAGE
            && missingSource.getMissingLogicalDecisions().equals(
                Collections.singletonList(secondKey))
            && missingSource.spansLevels(),
            "cross-level source blocks a missing logical decision");

        LayeredPackedRegionRetirementReadiness complete =
            LayeredPackedRegionRetirementReadiness.fromDecisions(
                Arrays.asList(firstDecision, secondDecision), 2, 4);
        check(complete.getSourceCount() == 2
            && complete.getReadySourceCount() == 1
            && complete.getBlockedSourceCount() == 1
            && source(complete, 4, 19).spansLevels()
            && source(complete, 4, 19).isReady()
            && source(complete, 4, 20).getSourceState()
                == LayeredPackedRegionRetirementReadiness.SourceState
                    .INCOMPLETE_COVERAGE,
            "only fully covered cross-level source becomes ready");

        LayeredRegionInterestOwnershipLedger.OpenedOwner repinned =
            scenario.open(secondKey, 7L);
        LayeredRegionRetirementDecisionArbiter.Decision currentFirst =
            scenario.currentDecision(firstCandidate, 7L);
        LayeredRegionRetirementDecisionArbiter.Decision refusedSecond =
            scenario.currentDecision(secondCandidate, 7L);
        check(currentFirst.isEligible()
            && refusedSecond.getDecisionState()
                == LayeredRegionRetirementDecisionArbiter.DecisionState.PINNED,
            "one level fragment repinned");
        LayeredPackedRegionRetirementReadiness refused =
            LayeredPackedRegionRetirementReadiness.fromDecisions(
                Arrays.asList(currentFirst, refusedSecond), 2, 4);
        check(refused.getReadySourceCount() == 0
            && refused.getBlockedSourceCount() == 2
            && source(refused, 4, 19).getSourceState()
                == LayeredPackedRegionRetirementReadiness.SourceState
                    .REFUSED_COVERAGE
            && source(refused, 4, 19).getRefusedLogicalDecisions().equals(
                Collections.singletonList(secondKey))
            && source(refused, 4, 20).getSourceState()
                == LayeredPackedRegionRetirementReadiness.SourceState
                    .INCOMPLETE_COVERAGE
            && source(refused, 4, 20).getRefusedLogicalDecisions().equals(
                Collections.singletonList(secondKey)),
            "repinned level fragment blocks its whole packed assembly");
        scenario.close(repinned, 7L);

        check(scenario.residency.unregisterPackedRegion(4, 20),
            "remove one source from the second logical Region");
        LayeredRegionRetirementEligibilityLedger.Snapshot partialCandidate =
            scenario.snapshot(secondKey, 12L);
        check(partialCandidate.isRetirementEligible()
            && partialCandidate.getResidentSourceCount() == 1
            && partialCandidate.getSourceCount() == 2,
            "older logical evidence exposes partial residency explicitly");
        LayeredRegionRetirementDecisionArbiter.Decision partialDecision =
            scenario.currentDecision(partialCandidate, 12L);
        check(partialDecision.isEligible()
            && !partialDecision.isCurrentResidencyComplete(),
            "partial logical decision remains evidence but not source readiness");
        LayeredRegionRetirementEligibilityLedger.Snapshot refreshedFirst =
            scenario.snapshot(firstKey, 12L);
        LayeredPackedRegionRetirementReadiness partial =
            LayeredPackedRegionRetirementReadiness.fromDecisions(
                Arrays.asList(
                    scenario.currentDecision(refreshedFirst, 12L),
                    partialDecision), 2, 4);
        check(source(partial, 4, 19).getSourceState()
                == LayeredPackedRegionRetirementReadiness.SourceState
                    .PARTIAL_RESIDENCY
            && source(partial, 4, 19)
                .getPartialResidencyLogicalDecisions().equals(
                    Collections.singletonList(secondKey)),
            "partial logical residency blocks an otherwise covered source");

        expectIllegal(() ->
            LayeredPackedRegionRetirementReadiness.fromDecisions(
                Arrays.asList(firstDecision, refusedSecond), 2, 4));
        expectIllegal(() ->
            LayeredPackedRegionRetirementReadiness.fromDecisions(
                Arrays.asList(currentFirst, currentFirst), 2, 4));
    }

    private static void partialDomainSourceRemainsBlocked() {
        Scenario scenario = new Scenario();
        check(scenario.residency.registerPackedRegion(682, 0),
            "register partial edge source");
        LegacyPackedRegionCoverage coverage =
            LegacyPackedRegionCoverage.fromPackedRegionCoordinates(682, 0);
        check(coverage.hasLegacyTiles() && !coverage.isFullyInsideLegacyDomain(),
            "fixture source extends outside legacy x domain");
        WorldRegionKey key = coverage.getCoveredKeys().get(0);
        LayeredRegionRetirementEligibilityLedger.Snapshot candidate =
            scenario.release(key, 1L, 2L, 7L);
        LayeredPackedRegionRetirementReadiness readiness =
            LayeredPackedRegionRetirementReadiness.fromDecisions(
                Collections.singletonList(
                    scenario.currentDecision(candidate, 7L)), 1, 2);
        check(readiness.getReadySourceCount() == 0
            && readiness.getBlockedSourceCount() == 1
            && readiness.getSources().get(0).getSourceState()
                == LayeredPackedRegionRetirementReadiness.SourceState
                    .PARTIAL_LEGACY_DOMAIN,
            "partial edge source blocked despite logical eligibility");
    }

    private static void emptyAndInvalidInputsRemainBounded() {
        LayeredPackedRegionRetirementReadiness empty =
            LayeredPackedRegionRetirementReadiness.fromDecisions(
                Collections.emptyList(), 0, 0);
        check(empty.getObservedAtTick() == -1L
            && empty.getOwnershipVersion() == -1L
            && empty.getResidencyMirrorVersion() == -1L
            && empty.getSourceCount() == 0,
            "empty readiness explicit");
        expectNull(() ->
            LayeredPackedRegionRetirementReadiness.fromDecisions(null, 0, 0));

        Scenario scenario = new Scenario();
        check(scenario.residency.registerPackedRegion(4, 0),
            "register bounded source");
        LayeredRegionRetirementEligibilityLedger.Snapshot candidate =
            scenario.release(key(0, 4, 0), 1L, 2L, 7L);
        LayeredRegionRetirementDecisionArbiter.Decision decision =
            scenario.currentDecision(candidate, 7L);
        expectIllegal(() ->
            LayeredPackedRegionRetirementReadiness.fromDecisions(
                Collections.singletonList(decision), 0, 2));
        expectIllegal(() ->
            LayeredPackedRegionRetirementReadiness.fromDecisions(
                Collections.singletonList(decision), 1, 0));
    }

    private static WorldRegionKey key(int level, int x, int y) {
        return new WorldRegionKey(WorldSpaceId.GLOBAL, level, x, y);
    }

    private static LayeredPackedRegionRetirementReadiness.SourceReadiness source(
            LayeredPackedRegionRetirementReadiness readiness,
            int packedRegionX,
            int packedRegionY) {
        for (LayeredPackedRegionRetirementReadiness.SourceReadiness source
                : readiness.getSources()) {
            if (source.getPackedRegionX() == packedRegionX
                    && source.getPackedRegionY() == packedRegionY) {
                return source;
            }
        }
        throw new AssertionError("Missing packed source " + packedRegionX
            + "," + packedRegionY);
    }

    private static WorldRegionWindow window(WorldRegionKey key) {
        return new WorldRegionWindow(
            key.getWorldSpace(), key.getLevel(), key.getRegionX(),
            key.getRegionY(), key.getRegionX(), key.getRegionY());
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void expectImmutable(List values) {
        try {
            values.add(new Object());
            throw new AssertionError("Expected immutable list");
        } catch (UnsupportedOperationException expected) {
            // Expected refusal.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }

    private static final class Scenario {
        private final LayeredRegionInterestOwnershipLedger ownership =
            new LayeredRegionInterestOwnershipLedger();
        private final LayeredRegionRetirementEligibilityLedger retirement =
            new LayeredRegionRetirementEligibilityLedger(5L);
        private final LayeredRegionResidencyMirror residency =
            new LayeredRegionResidencyMirror();
        private final LayeredRegionRetirementDecisionArbiter arbiter =
            new LayeredRegionRetirementDecisionArbiter();

        private LayeredRegionInterestOwnershipLedger.OpenedOwner open(
                WorldRegionKey key, long tick) {
            LayeredRegionInterestOwnershipLedger.OpenedOwner opened =
                ownership.openOwner(window(key), 1);
            retirement.observeOwnershipChange(opened.getChange(), tick);
            return opened;
        }

        private void close(
                LayeredRegionInterestOwnershipLedger.OpenedOwner opened,
                long tick) {
            retirement.observeOwnershipChange(
                ownership.closeOwner(opened.getOwnerToken()), tick);
        }

        private LayeredRegionRetirementEligibilityLedger.Snapshot release(
                WorldRegionKey key, long openTick, long closeTick,
                long eligibleTick) {
            LayeredRegionInterestOwnershipLedger.OpenedOwner opened =
                open(key, openTick);
            close(opened, closeTick);
            return snapshot(key, eligibleTick);
        }

        private LayeredRegionRetirementEligibilityLedger.Snapshot snapshot(
                WorldRegionKey key, long tick) {
            return retirement.snapshot(
                ownership.snapshot(key), residency.snapshot(key), tick);
        }

        private LayeredRegionRetirementDecisionArbiter.Decision currentDecision(
                LayeredRegionRetirementEligibilityLedger.Snapshot candidate,
                long tick) {
            return arbiter.evaluate(
                candidate, snapshot(candidate.getLogicalRegionKey(), tick));
        }
    }
}
'''


class LayeredMapsSliceFortySevenTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-forty-seven-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "LayeredPackedRegionRetirementReadinessFixture.java"
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

    def test_source_aggregation_blocks_cross_level_races_and_partial_edges(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "LayeredPackedRegionRetirementReadinessFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_manager_boundary_is_atomic_bounded_and_non_mutating(self):
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        readiness = READINESS.read_text(encoding="utf-8")
        path_validation = PATH_VALIDATION.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn(
            "prepareLayeredPackedRegionRetirementReadiness(", manager
        )
        self.assertIn("synchronized (layeredRegionLifecycleLock)", manager)
        self.assertIn(
            "MAX_LAYERED_PACKED_SOURCES_PER_RETIREMENT_PLAN", manager
        )
        self.assertIn("LegacyPackedRegionCoverage", readiness)
        self.assertIn("coverage.spansLevels()", readiness)
        self.assertIn("SourceState.INCOMPLETE_COVERAGE", readiness)
        self.assertIn("SourceState.REFUSED_COVERAGE", readiness)
        self.assertIn("SourceState.PARTIAL_RESIDENCY", readiness)
        self.assertIn("SourceState.PARTIAL_LEGACY_DOMAIN", readiness)
        self.assertNotIn(
            "com.openrsc.server.model.world.region.Region", readiness
        )
        self.assertNotIn("getRegion(", readiness)
        self.assertNotIn("unregisterPackedRegion", readiness)
        self.assertNotIn(".unload(", readiness)
        self.assertNotIn("LayeredPackedRegionRetirementReadiness", path_validation)
        self.assertNotIn("LayeredPackedRegionRetirementReadiness", observer)

        boundary = manager.split(
            "/**\n\t * Atomically rechecks one bounded candidate batch and aggregates",
            1,
        )[1].split(
            "/**\n\t * Compares one bounded logical interest change", 1
        )[0]
        self.assertNotIn("getRegion(", boundary)
        self.assertNotIn("registerPackedRegion", boundary)
        self.assertNotIn("unregisterPackedRegion", boundary)
        self.assertNotIn(".unload(", boundary)
        self.assertIn(
            "### Slice 47: Dormant packed-source retirement readiness", plan
        )


if __name__ == "__main__":
    unittest.main()
