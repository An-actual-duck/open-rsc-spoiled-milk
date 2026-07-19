#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
REGION_PACKAGE = ROOT / "server/src/com/openrsc/server/model/world/region"
TRAVERSAL = REGION_PACKAGE / "LayeredTraversalCollisionComparison.java"
REGION_MANAGER = REGION_PACKAGE / "RegionManager.java"
PATH_VALIDATION = ROOT / "server/src/com/openrsc/server/model/PathValidation.java"
MOB = ROOT / "server/src/com/openrsc/server/model/entity/Mob.java"
PLAYER = ROOT / "server/src/com/openrsc/server/model/entity/player/Player.java"
PLAN = ROOT / "docs/myworld/in-progress-work-plans/world-layer-capacity-exploration-plan.md"


WORLD_LOCATION_STUB = r'''
package com.openrsc.server.model.world.coordinate;

public final class WorldLocation {
    private final String id;

    public WorldLocation(String id) {
        this.id = id;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WorldLocation
            && id.equals(((WorldLocation) other).id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return id;
    }
}
'''


STEP_STUB = r'''
package com.openrsc.server.model.world.region;

import com.openrsc.server.model.world.coordinate.WorldLocation;

public final class LayeredAdjacentStepCollisionComparison {
    private final WorldLocation source;
    private final WorldLocation destination;
    private final Boolean logical;
    private final Boolean packed;
    private final boolean reasonExact;
    private final boolean statesExact;

    public LayeredAdjacentStepCollisionComparison(
            WorldLocation source,
            WorldLocation destination,
            Boolean logical,
            Boolean packed,
            boolean reasonExact,
            boolean statesExact) {
        this.source = source;
        this.destination = destination;
        this.logical = logical;
        this.packed = packed;
        this.reasonExact = reasonExact;
        this.statesExact = statesExact;
    }

    public WorldLocation getSource() { return source; }
    public WorldLocation getDestination() { return destination; }
    public boolean isLogicalDecisionAvailable() { return logical != null; }
    public boolean isPackedDecisionAvailable() { return packed != null; }
    public Boolean getLogicalPassable() { return logical; }
    public Boolean getPackedPassable() { return packed; }
    public boolean isComparable() { return logical != null && packed != null; }
    public boolean isPassabilityExact() {
        return isComparable() && logical.equals(packed);
    }
    public boolean isBlockingReasonExact() {
        return isComparable() && reasonExact;
    }
    public boolean areRequiredStatesExact() { return statesExact; }
}
'''


FIXTURE = r'''
package com.openrsc.server.model.world.region;

import com.openrsc.server.model.world.coordinate.WorldLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class LayeredTraversalCollisionComparisonFixture {
    public static void main(String[] args) {
        WorldLocation a = new WorldLocation("a");
        WorldLocation b = new WorldLocation("b");
        WorldLocation c = new WorldLocation("c");
        WorldLocation d = new WorldLocation("d");

        LayeredTraversalCollisionComparison open =
            LayeredTraversalCollisionComparison.of(Arrays.asList(
                step(a, b, true, true, true, true),
                step(b, c, true, true, true, true)));
        check(open.getStepCount() == 2, "open step count");
        check(open.getSource().equals(a), "open source");
        check(open.getDestination().equals(c), "open destination");
        check(Boolean.TRUE.equals(open.getLogicalPassable()), "open logical");
        check(Boolean.TRUE.equals(open.getPackedPassable()), "open packed");
        check(open.isComparable() && open.isPassabilityExact(), "open exact");
        check(open.areAllStepsComparable(), "open comparable steps");
        check(open.areAllStepPassabilitiesExact(), "open exact steps");
        check(open.areAllStepBlockingReasonsExact(), "open exact reasons");
        check(open.areAllRequiredStatesExact(), "open exact states");
        check(open.getFirstLogicalBlockedStepIndex() == null, "open no block");

        LayeredTraversalCollisionComparison blocked =
            LayeredTraversalCollisionComparison.of(Arrays.asList(
                step(a, b, true, true, true, true),
                step(b, c, false, false, true, true),
                step(c, d, true, true, true, true)));
        check(Boolean.FALSE.equals(blocked.getLogicalPassable()), "blocked logical");
        check(Boolean.FALSE.equals(blocked.getPackedPassable()), "blocked packed");
        check(Integer.valueOf(1).equals(blocked.getFirstLogicalBlockedStepIndex()),
            "blocked logical index");
        check(Integer.valueOf(1).equals(blocked.getFirstPackedBlockedStepIndex()),
            "blocked packed index");
        check(blocked.isPassabilityExact(), "blocked route exact");

        LayeredTraversalCollisionComparison mismatch =
            LayeredTraversalCollisionComparison.of(Arrays.asList(
                step(a, b, true, true, true, true),
                step(b, c, true, false, false, false)));
        check(Boolean.TRUE.equals(mismatch.getLogicalPassable()), "mismatch logical");
        check(Boolean.FALSE.equals(mismatch.getPackedPassable()), "mismatch packed");
        check(!mismatch.isPassabilityExact(), "mismatch route");
        check(mismatch.getPassabilityExactCount() == 1, "mismatch exact count");
        check(mismatch.getBlockingReasonExactCount() == 1, "mismatch reason count");
        check(mismatch.getRequiredStatesExactCount() == 1, "mismatch states count");
        check(Integer.valueOf(1).equals(
            mismatch.getFirstPassabilityMismatchStepIndex()), "mismatch index");
        check(Integer.valueOf(1).equals(
            mismatch.getFirstBlockingReasonMismatchStepIndex()), "reason index");

        LayeredTraversalCollisionComparison unavailable =
            LayeredTraversalCollisionComparison.of(Arrays.asList(
                step(a, b, true, true, true, true),
                step(b, c, true, null, false, false)));
        check(unavailable.isLogicalDecisionAvailable(), "logical available");
        check(!unavailable.isPackedDecisionAvailable(), "packed unavailable");
        check(Boolean.TRUE.equals(unavailable.getLogicalPassable()),
            "available logical result");
        check(unavailable.getPackedPassable() == null, "nullable packed result");
        check(!unavailable.isComparable(), "unavailable route not comparable");
        check(unavailable.getPackedDecisionAvailableCount() == 1,
            "packed available count");

        expectIllegal(() -> LayeredTraversalCollisionComparison.of(
            Arrays.asList(step(a, b, true, true, true, true),
                step(c, d, true, true, true, true))));
        expectIllegal(() -> LayeredTraversalCollisionComparison.of(
            Collections.<LayeredAdjacentStepCollisionComparison>emptyList()));
        expectIllegal(() -> LayeredTraversalCollisionComparison.of(
            Collections.nCopies(51, step(a, b, true, true, true, true))));
        expectNull(() -> LayeredTraversalCollisionComparison.of(null));
        List<LayeredAdjacentStepCollisionComparison> withNull = new ArrayList<>();
        withNull.add(null);
        expectNull(() -> LayeredTraversalCollisionComparison.of(withNull));
        try {
            open.getSteps().clear();
            throw new AssertionError("Expected immutable steps");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
        check(open.toString().contains("stepCount=2"), "comparison string");
    }

    private static LayeredAdjacentStepCollisionComparison step(
            WorldLocation source,
            WorldLocation destination,
            Boolean logical,
            Boolean packed,
            boolean reasonExact,
            boolean statesExact) {
        return new LayeredAdjacentStepCollisionComparison(
            source, destination, logical, packed, reasonExact, statesExact);
    }

    private static void expectIllegal(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }

    private static void expectNull(Runnable operation) {
        try {
            operation.run();
            throw new AssertionError("Expected NullPointerException");
        } catch (NullPointerException expected) {
            // Expected.
        }
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
'''


class LayeredMapsSliceThirtyFourTest(unittest.TestCase):
    def test_bounded_traversal_composes_adjacent_decisions(self):
        with tempfile.TemporaryDirectory(prefix="layered-maps-slice-thirty-four-") as temp:
            temp = Path(temp)
            classes = temp / "classes"
            classes.mkdir()
            sources = {
                "com/openrsc/server/model/world/coordinate/WorldLocation.java":
                    WORLD_LOCATION_STUB,
                "com/openrsc/server/model/world/region/LayeredAdjacentStepCollisionComparison.java":
                    STEP_STUB,
                "com/openrsc/server/model/world/region/LayeredTraversalCollisionComparisonFixture.java":
                    FIXTURE,
            }
            source_paths = []
            for relative, source in sources.items():
                path = temp / "src" / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(source, encoding="utf-8")
                source_paths.append(path)
            result = subprocess.run(
                [
                    "javac", "-Xlint:all", "-source", "8", "-target", "8",
                    "-encoding", "UTF-8", "-d", str(classes),
                    *map(str, source_paths), str(TRAVERSAL),
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(0, result.returncode, result.stderr)
            result = subprocess.run(
                [
                    "java", "-cp", str(classes),
                    "com.openrsc.server.model.world.region."
                    "LayeredTraversalCollisionComparisonFixture",
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
            )
            self.assertEqual(0, result.returncode, result.stderr)

    def test_traversal_projection_is_dormant_and_route_selection_stays_legacy(self):
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        path_validation = PATH_VALIDATION.read_text(encoding="utf-8")
        mob = MOB.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn(
            "compareLayeredTraversalCollision(\n\t\tfinal List<WorldLocation> route)",
            manager,
        )
        block = manager.split(
            "compareLayeredTraversalCollision(\n\t\tfinal List<WorldLocation> route)",
            1,
        )[1].split("private LayeredTileStateParityComparison", 1)[0]
        self.assertIn("compareLayeredAdjacentStepCollision(", block)
        self.assertIn("LayeredTraversalCollisionComparison.of(comparisons)", block)
        self.assertNotIn("getRegion(", block)
        self.assertNotIn("setPath(", block)
        self.assertNotIn("LayeredTraversalCollisionComparison", path_validation)
        self.assertNotIn("LayeredTraversalCollisionComparison", mob)
        self.assertNotIn("LayeredTraversalCollisionComparison", player)
        self.assertIn(
            "### Slice 34: Dormant bounded traversal collision projection",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
