#!/usr/bin/env python3
import json
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
OBSERVATION = ROOT / (
    "server/src/com/openrsc/server/model/world/coordinate/"
    "LayeredPackedRegionAuthoredProvenanceObservation.java"
)
OBSERVER = ROOT / (
    "server/src/com/openrsc/server/diagnostics/"
    "LayeredCoordinateParityObserver.java"
)
SCHEMA_V17 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v17.schema.json"
)
SCHEMA_V18 = ROOT / (
    "tools/layered-maps/schema/layered-map-parity-event-v18.schema.json"
)
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


class LayeredMapsSliceFiftyEightTest(unittest.TestCase):
    def test_anomaly_details_are_bounded_detached_and_serialized(self):
        observation = OBSERVATION.read_text(encoding="utf-8")
        observer = OBSERVER.read_text(encoding="utf-8")

        self.assertIn("MAXIMUM_ANOMALY_DETAILS = 4096", observation)
        for kind in (
            "ABSENT", "DUPLICATE", "REPLACEMENT_OBJECT",
            "STALE_GENERATION", "UNRECOGNIZED_IDENTITY",
        ):
            self.assertIn(kind, observation)
        self.assertIn("Collections.unmodifiableList", observation)
        self.assertIn("droppedAnomalyDetailCount", observation)
        self.assertNotIn("com.openrsc.server.model.entity", observation)
        self.assertNotIn("com.openrsc.server.model.world.region", observation)
        self.assertNotIn("registerPackedRegion", observation)
        self.assertNotIn("unregisterPackedRegion", observation)
        self.assertNotIn(".unload(", observation)

        self.assertIn(
            'EVENT_SCHEMA = "layered-map-parity-event-v37"', observer
        )
        self.assertIn('anomalyDetails', observer)
        self.assertIn('droppedAnomalyDetailCount', observer)
        self.assertIn("appendPackedRegionAuthoredProvenanceAnomaly", observer)

    def test_v18_is_additive_and_v17_contract_remains_immutable(self):
        v17 = json.loads(SCHEMA_V17.read_text(encoding="utf-8"))
        v18 = json.loads(SCHEMA_V18.read_text(encoding="utf-8"))

        self.assertEqual(
            "layered-map-parity-event-v17",
            v17["properties"]["schema"]["const"],
        )
        old_provenance = v17["$defs"]["packedRegionAuthoredProvenance"]
        self.assertNotIn("anomalyDetails", old_provenance["properties"])
        self.assertNotIn("anomalyDetailCount", old_provenance["required"])

        self.assertEqual(
            "layered-map-parity-event-v18",
            v18["properties"]["schema"]["const"],
        )
        provenance = v18["$defs"]["packedRegionAuthoredProvenance"]
        self.assertEqual(4096, provenance["properties"]["anomalyDetails"]["maxItems"])
        self.assertIn("anomalyDetailCount", provenance["required"])
        self.assertIn("droppedAnomalyDetailCount", provenance["required"])
        anomaly = v18["$defs"]["authoredProvenanceAnomaly"]
        self.assertFalse(anomaly["additionalProperties"])
        self.assertEqual(
            [
                "ABSENT", "DUPLICATE", "REPLACEMENT_OBJECT",
                "STALE_GENERATION", "UNRECOGNIZED_IDENTITY",
            ],
            anomaly["properties"]["anomalyKind"]["enum"],
        )

    def test_living_plan_records_slice_fifty_eight_boundary(self):
        plan = PLAN.read_text(encoding="utf-8")
        self.assertIn(
            "### Slice 58: Bounded authored provenance anomaly details",
            plan,
        )
        self.assertIn("4,096", plan)
        self.assertIn("no entity, Region", plan)


if __name__ == "__main__":
    unittest.main()
