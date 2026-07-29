#!/usr/bin/env python3
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATES = ROOT / "server/src/com/openrsc/server/model/world/coordinate"
INVENTORY = COORDINATES / (
    "LayeredPackedRegionAuthoredConstructionInventory.java"
)
IDENTITY = COORDINATES / "LayeredAuthoredPlacementIdentity.java"
MANIFEST = COORDINATES / "LayeredPackedRegionAuthoredPlacementManifest.java"
ENTITY = ROOT / "server/src/com/openrsc/server/model/entity/Entity.java"
GAME_OBJECT_LOC = ROOT / "server/src/com/openrsc/server/external/GameObjectLoc.java"
NPC_LOC = ROOT / "server/src/com/openrsc/server/external/NPCLoc.java"
ITEM_LOC = ROOT / "server/src/com/openrsc/server/external/ItemLoc.java"
PLAN = ROOT / (
    "docs/myworld/in-progress-work-plans/"
    "world-layer-capacity-exploration-plan.md"
)


FIXTURE = r'''
package com.openrsc.server.model.world.coordinate;

public final class AuthoredPlacementIdentityFixture {
    public static void main(String[] args) {
        identityIsExactAndGenerationFenced();
        manifestOwnsTheCanonicalIdentity();
        invalidIdentityIsRefused();
    }

    private static void identityIsExactAndGenerationFenced() {
        LayeredAuthoredPlacementIdentity first = identity(4L, 2, 9, 1, "SCENERY");
        LayeredAuthoredPlacementIdentity same = identity(4L, 2, 9, 1, "SCENERY");
        check(first.equals(same) && same.equals(first)
            && first.hashCode() == same.hashCode(),
            "equal identities share a hash");
        check(!first.equals(identity(5L, 2, 9, 1, "SCENERY"))
            && !first.equals(identity(4L, 2, 9, 2, "SCENERY"))
            && !first.equals(identity(4L, 2, 9, 1, "BOUNDARY"))
            && !first.equals(null),
            "generation, ordinal, and family fence identity");
        check(first.getGeneration() == 4L
            && first.getPackedRegionX() == 2
            && first.getPackedRegionY() == 9
            && first.getSourceOrdinal() == 1
            && first.toString().equals("authored-placement:g4:r2,9:o1:SCENERY"),
            "identity fields and stable text are exact");
    }

    private static void manifestOwnsTheCanonicalIdentity() {
        LayeredPackedRegionAuthoredPlacementManifest.Builder builder =
            LayeredPackedRegionAuthoredPlacementManifest.builder(7L);
        builder.recordNpcSpawn(5, 4, 7, 250, 210, 249, 251, 209, 211)
            .recordNpcSpawn(5, 4, 7, 250, 210, 249, 251, 209, 211);
        LayeredPackedRegionAuthoredPlacementManifest.PackedSourceManifest source =
            builder.build().findSource(5, 4);
        LayeredPackedRegionAuthoredPlacementManifest.AuthoredPlacement first =
            source.findPlacement(1);
        LayeredPackedRegionAuthoredPlacementManifest.AuthoredPlacement second =
            source.findPlacement(2);
        check(first.getIdentity().equals(identity(7L, 5, 4, 1, "NPC_SPAWN"))
            && second.getIdentity().equals(identity(7L, 5, 4, 2, "NPC_SPAWN"))
            && first.getSourceOrdinal() == first.getIdentity().getSourceOrdinal()
            && first.getKind() == first.getIdentity().getConstructionKind(),
            "manifest exposes its canonical generation-fenced identity");
    }

    private static void invalidIdentityIsRefused() {
        expectIllegal(() -> identity(0L, 0, 0, 1, "SCENERY"));
        expectIllegal(() -> identity(1L, -1, 0, 1, "SCENERY"));
        expectIllegal(() -> identity(1L, 0, 0, 0, "SCENERY"));
        expectNull(() -> new LayeredAuthoredPlacementIdentity(
            1L, 0, 0, 1, null));
    }

    private static LayeredAuthoredPlacementIdentity identity(
            long generation, int x, int y, int ordinal, String kind) {
        return new LayeredAuthoredPlacementIdentity(
            generation, x, y, ordinal,
            LayeredPackedRegionAuthoredConstructionInventory
                .ConstructionKind.valueOf(kind));
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


class LayeredMapsSliceFiftyFiveTest(unittest.TestCase):
    def test_generation_fenced_identity_contract(self):
        with tempfile.TemporaryDirectory(prefix="layered-slice55-") as temp:
            temp_path = Path(temp)
            fixture_path = temp_path / (
                "com/openrsc/server/model/world/coordinate/"
                "AuthoredPlacementIdentityFixture.java"
            )
            fixture_path.parent.mkdir(parents=True)
            fixture_path.write_text(FIXTURE, encoding="utf-8")
            classes = temp_path / "classes"
            classes.mkdir()
            subprocess.run(
                [
                    "javac", "-d", str(classes), str(INVENTORY),
                    str(IDENTITY), str(MANIFEST), str(fixture_path),
                ],
                check=True,
                cwd=ROOT,
            )
            subprocess.run(
                [
                    "java", "-cp", str(classes),
                    "com.openrsc.server.model.world.coordinate."
                    "AuthoredPlacementIdentityFixture",
                ],
                check=True,
                cwd=ROOT,
            )

    def test_identity_remains_inert_and_manifest_owned(self):
        identity = IDENTITY.read_text(encoding="utf-8")
        manifest = MANIFEST.read_text(encoding="utf-8")
        plan = PLAN.read_text(encoding="utf-8")

        self.assertIn("generation-fenced identity", identity)
        self.assertIn("no entity, Region, event, registry", identity)
        self.assertIn("getIdentity()", manifest)
        self.assertIn("new LayeredAuthoredPlacementIdentity(", manifest)
        self.assertNotIn("model.entity", identity)
        self.assertNotIn("server.external", identity)
        self.assertIn(
            "### Slice 55: Generation-fenced authored placement identity",
            plan,
        )


if __name__ == "__main__":
    unittest.main()
