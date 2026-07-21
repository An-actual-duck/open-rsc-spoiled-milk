#!/usr/bin/env python3
import json
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
OUTCOME = COORDINATES / "LayeredPackedRegionAuthoredPopulationOutcome.java"
OBSERVATION = COORDINATES / (
    "LayeredPackedRegionAuthoredProvenanceObservation.java"
)
POPULATOR = ROOT / "server/src/com/openrsc/server/database/WorldPopulator.java"
REGION_MANAGER = ROOT / (
    "server/src/com/openrsc/server/model/world/region/RegionManager.java"
)
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
DEVELOPMENT = ROOT / (
    "server/plugins/com/openrsc/server/plugins/authentic/commands/Development.java"
)
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/LayeredCoordinateParityObserver.java"
)
SCHEMA_V18 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v18.schema.json"
)
SCHEMA_V19 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v19.schema.json"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


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

import java.util.Collections;

public final class AuthoredPopulationOutcomeFixture {
    public static void main(String[] args) {
        LayeredPackedRegionAuthoredPlacementManifest.Builder manifestBuilder =
            LayeredPackedRegionAuthoredPlacementManifest.builder(5L);
        manifestBuilder.recordScenery(
            4, 0, 3, 3, 200, 20, 0, 0, null);
        LayeredAuthoredPlacementIdentity table =
            manifestBuilder.getLastRecordedIdentity();
        manifestBuilder.recordHarvestingScenery(
            4, 0, 18, 1262, 1262, 200, 20, 0, 0, null, 1, 30, 0);
        LayeredAuthoredPlacementIdentity cabbage =
            manifestBuilder.getLastRecordedIdentity();
        manifestBuilder.recordBoundary(
            4, 0, 1, 1, 201, 20, 0, 1, null);
        LayeredAuthoredPlacementIdentity frame =
            manifestBuilder.getLastRecordedIdentity();
        manifestBuilder.recordBoundary(
            4, 0, 2, 2, 201, 20, 0, 1, null);
        LayeredAuthoredPlacementIdentity door =
            manifestBuilder.getLastRecordedIdentity();
        LayeredPackedRegionAuthoredPlacementManifest manifest =
            manifestBuilder.build();

        LayeredPackedRegionAuthoredPopulationOutcome outcome =
            LayeredPackedRegionAuthoredPopulationOutcome.builder(5L)
                .recordSupersession(table, cabbage)
                .recordSupersession(frame, door)
                .build(manifest);
        check(outcome.getGeneration() == 5L
            && outcome.getManifestPlacementCount() == 4
            && outcome.getSupersessionCount() == 2
            && outcome.getFinalExpectedPlacementCount() == 2,
            "outcome separates replay history from final-live expectations");
        check(outcome.isSuperseded(table) && outcome.isSuperseded(frame)
            && !outcome.isSuperseded(cabbage) && !outcome.isSuperseded(door),
            "only collision predecessors are superseded");
        check(outcome.getSupersessions().get(0).getCollisionKind().name()
                .equals("SCENERY_ANCHOR")
            && outcome.getSupersessions().get(1).getCollisionKind().name()
                .equals("BOUNDARY_ANCHOR_DIRECTION"),
            "supersessions are deterministic and collision-family explicit");
        check(outcome.getSupersessions().get(0).getPredecessor()
                .getAuthoredDefinitionId() == 3
            && outcome.getSupersessions().get(0).getSuccessor()
                .getConstructedEntityId() == 1262,
            "detached metadata explains the replacement exactly");
        expectImmutable(outcome.getSupersessions());

        LayeredPackedRegionRetirementSafetyAssessment safety = safety();
        LayeredPackedRegionAuthoredProvenanceObservation.Builder observed =
            LayeredPackedRegionAuthoredProvenanceObservation.builder(
                manifest, outcome, safety, 9L);
        observed.recordRuntimeInstance(cabbage, 1262, 4, 0, true);
        observed.recordRuntimeInstance(door, 2, 4, 0, true);
        LayeredPackedRegionAuthoredProvenanceObservation result =
            observed.build();
        check(result.getManifestPlacementCount() == 4
            && result.getSupersededManifestIdentityCount() == 2
            && result.getExpectedPlacementCount() == 2
            && result.getMatchedIdentityCount() == 2
            && result.getAbsentIdentityCount() == 0,
            "provenance evaluates the final population rather than replay history");
        check(result.getPopulationSupersessionDetailCount() == 2
            && result.getDroppedPopulationSupersessionDetailCount() == 0
            && result.getSupersededRuntimeInstanceCount() == 0
            && result.getAnomalyDetailCount() == 0,
            "normal supersessions are details rather than false absences");
        check(result.getExpectedHarvestingSceneryCount() == 1
            && result.getExpectedBoundaryCount() == 1
            && result.getExpectedSceneryCount() == 0,
            "expected family counts describe final-live identities");
        expectImmutable(result.getPopulationSupersessions());

        LayeredPackedRegionAuthoredProvenanceObservation.Builder unexpected =
            LayeredPackedRegionAuthoredProvenanceObservation.builder(
                manifest, outcome, safety, 10L);
        unexpected.recordRuntimeInstance(cabbage, 1262, 4, 0, true);
        unexpected.recordRuntimeInstance(door, 2, 4, 0, true);
        unexpected.recordRuntimeInstance(table, 3, 4, 0, true);
        LayeredPackedRegionAuthoredProvenanceObservation unexpectedResult =
            unexpected.build();
        check(unexpectedResult.getMatchedIdentityCount() == 2
            && unexpectedResult.getAbsentIdentityCount() == 0
            && unexpectedResult.getSupersededRuntimeInstanceCount() == 1
            && unexpectedResult.getAnomalyDetailCount() == 1
            && unexpectedResult.getAnomalyDetails().get(0).getAnomalyKind()
                == LayeredPackedRegionAuthoredProvenanceObservation
                    .AnomalyKind.SUPERSEDED_IDENTITY_PRESENT,
            "a superseded predecessor reappearing remains an explicit anomaly");

        expectIllegal(() -> LayeredPackedRegionAuthoredPopulationOutcome
            .builder(6L).recordSupersession(table, cabbage));
        LayeredPackedRegionAuthoredPlacementManifest.Builder alteredBuilder =
            LayeredPackedRegionAuthoredPlacementManifest.builder(5L);
        alteredBuilder.recordScenery(
            4, 0, 30, 30, 200, 20, 0, 0, null);
        alteredBuilder.recordHarvestingScenery(
            4, 0, 18, 1262, 1262, 200, 20, 0, 0, null, 1, 30, 0);
        alteredBuilder.recordBoundary(
            4, 0, 1, 1, 201, 20, 0, 1, null);
        alteredBuilder.recordBoundary(
            4, 0, 2, 2, 201, 20, 0, 1, null);
        LayeredPackedRegionAuthoredPlacementManifest alteredManifest =
            alteredBuilder.build();
        expectIllegal(() -> LayeredPackedRegionAuthoredProvenanceObservation
            .builder(alteredManifest, outcome, safety, 11L));
        expectState(() -> observed.build());
    }

    private static LayeredPackedRegionRetirementSafetyAssessment safety() {
        LayeredRegionInterestOwnershipLedger ownership =
            new LayeredRegionInterestOwnershipLedger();
        LayeredRegionRetirementEligibilityLedger retirement =
            new LayeredRegionRetirementEligibilityLedger(5L);
        LayeredRegionResidencyMirror residency =
            new LayeredRegionResidencyMirror();
        LayeredRegionRetirementDecisionArbiter arbiter =
            new LayeredRegionRetirementDecisionArbiter();
        WorldRegionKey key = new WorldRegionKey(WorldSpaceId.GLOBAL, 0, 4, 0);
        WorldRegionWindow window = new WorldRegionWindow(
            key.getWorldSpace(), key.getLevel(), key.getRegionX(),
            key.getRegionY(), key.getRegionX(), key.getRegionY());
        check(residency.registerPackedRegion(4, 0), "register packed source");
        LayeredRegionInterestOwnershipLedger.OpenedOwner opened =
            ownership.openOwner(window, 1);
        retirement.observeOwnershipChange(opened.getChange(), 1L);
        retirement.observeOwnershipChange(
            ownership.closeOwner(opened.getOwnerToken()), 2L);
        LayeredRegionRetirementEligibilityLedger.Snapshot candidate =
            retirement.snapshot(
                ownership.snapshot(key), residency.snapshot(key), 7L);
        LayeredRegionRetirementDecisionArbiter.Decision decision =
            arbiter.evaluate(candidate, retirement.snapshot(
                ownership.snapshot(key), residency.snapshot(key), 7L));
        LayeredPackedRegionRetirementReadiness readiness =
            LayeredPackedRegionRetirementReadiness.fromDecisions(
                Collections.singletonList(decision), 1, 2);
        LayeredPackedRegionRetirementReadiness.SourceReadiness source =
            readiness.getSources().get(0);
        return LayeredPackedRegionRetirementSafetyAssessment.assess(
            readiness,
            Collections.singletonList(
                LayeredPackedRegionRetirementSafetyAssessment
                    .PackedSourceContents.of(
                        source.getPackedRegionX(), source.getPackedRegionY(),
                        true, true, false, 0, 0, 0, 0)),
            8L, 1);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void expectImmutable(java.util.List values) {
        try {
            values.add(new Object());
            throw new AssertionError("Expected immutable list");
        } catch (UnsupportedOperationException expected) {
            // Expected refusal.
        }
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected refusal.
        }
    }

    private static void expectState(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalStateException");
        } catch (IllegalStateException expected) {
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


class LayeredMapsSliceFiftyNineTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-maps-slice-fifty-nine-"
        )
        cls.temp = Path(cls.compile_temp.name)
        cls.classes = cls.temp / "classes"
        cls.classes.mkdir()
        point = cls.temp / "src/com/openrsc/server/model/Point.java"
        point.parent.mkdir(parents=True, exist_ok=True)
        point.write_text(POINT_STUB, encoding="utf-8")
        fixture = cls.temp / (
            "src/com/openrsc/server/model/world/coordinate/"
            "AuthoredPopulationOutcomeFixture.java"
        )
        fixture.parent.mkdir(parents=True, exist_ok=True)
        fixture.write_text(FIXTURE, encoding="utf-8")
        subprocess.run(
            [
                "javac", "-Xlint:all", "-source", "8", "-target", "8",
                "-encoding", "UTF-8", "-d", str(cls.classes), str(point),
                str(fixture),
                *(str(path) for path in sorted(COORDINATES.glob("*.java"))),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def test_population_outcome_and_final_live_provenance_are_exact(self):
        result = subprocess.run(
            [
                "java", "-cp", str(self.classes),
                "com.openrsc.server.model.world.coordinate."
                "AuthoredPopulationOutcomeFixture",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_v19_contract_is_additive_and_detached(self):
        outcome = OUTCOME.read_text(encoding="utf-8")
        observation = OBSERVATION.read_text(encoding="utf-8")
        populator = POPULATOR.read_text(encoding="utf-8")
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        development = DEVELOPMENT.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")
        v18 = json.loads(SCHEMA_V18.read_text(encoding="utf-8"))
        v19 = json.loads(SCHEMA_V19.read_text(encoding="utf-8"))

        self.assertNotIn("com.openrsc.server.model.entity", outcome)
        self.assertNotIn("com.openrsc.server.model.world.region", outcome)
        self.assertIn("Collections.unmodifiableList", outcome)
        self.assertIn("collidingAuthoredObjectIdentity", populator)
        self.assertIn("object.getLoc().getX()", populator)
        self.assertIn("object.getLoc().getY()", populator)
        collision_helper = populator.split(
            "private LayeredAuthoredPlacementIdentity "
            "collidingAuthoredObjectIdentity", 1
        )[1].split("private void recordObjectDependency", 1)[0]
        self.assertNotIn("object.getX()", collision_helper)
        self.assertNotIn("object.getY()", collision_helper)
        self.assertIn("recordSupersession", populator)
        self.assertIn("getAuthoredPopulationOutcome", populator)
        self.assertIn("LayeredPackedRegionAuthoredPopulationOutcome", manager)
        self.assertIn("getAuthoredPopulationOutcome()", player)
        command_provenance_source = development.split(
            "layeredPackedRegionAuthoredProvenanceSource(final Player player)",
            1,
        )[1].split(
            "private List<LayeredCoordinateParityObserver.", 1
        )[0]
        self.assertIn(
            "getAuthoredPopulationOutcome()", command_provenance_source
        )
        self.assertIn("SUPERSEDED_IDENTITY_PRESENT", observation)
        self.assertIn(
            'EVENT_SCHEMA = "layered-map-parity-event-v31"', observer
        )
        self.assertIn("populationSupersessions", observer)

        self.assertEqual(
            "layered-map-parity-event-v18",
            v18["properties"]["schema"]["const"],
        )
        self.assertNotIn(
            "populationSupersessions",
            v18["$defs"]["packedRegionAuthoredProvenance"]["properties"],
        )
        self.assertEqual(
            "layered-map-parity-event-v19",
            v19["properties"]["schema"]["const"],
        )
        provenance = v19["$defs"]["packedRegionAuthoredProvenance"]
        for field in (
            "manifestPlacementCount", "supersededManifestIdentityCount",
            "supersededRuntimeInstanceCount", "populationSupersessions",
        ):
            self.assertIn(field, provenance["required"])

    def test_living_plan_records_slice_fifty_nine_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 59: Detached population supersession projection",
            plan,
        )
        self.assertIn("final-live expectation", plan)


if __name__ == "__main__":
    unittest.main()
