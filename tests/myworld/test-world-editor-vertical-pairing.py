#!/usr/bin/env python3
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
PAIRING = (
    ROOT
    / "server/src/com/openrsc/server/content/worldedit/"
    "WorldEditorVerticalPairing.java"
)
SCENERY_IDS = ROOT / "server/src/com/openrsc/server/constants/SceneryId.java"
SESSIONS = (
    ROOT
    / "server/src/com/openrsc/server/content/worldedit/"
    "WorldEditorSessionManager.java"
)
LADDERS = (
    ROOT
    / "server/plugins/com/openrsc/server/plugins/authentic/defaults/"
    "Ladders.java"
)


HARNESS = r"""
import com.openrsc.server.constants.SceneryId;
import com.openrsc.server.content.worldedit.WorldEditorVerticalPairing;

public final class WorldEditorVerticalPairingHarness {
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void pair(
        SceneryId source,
        SceneryId inverse,
        int delta
    ) {
        WorldEditorVerticalPairing.Pairing pairing =
            WorldEditorVerticalPairing.find(source.id());
        require(pairing != null, source + " is not paired");
        require(pairing.getSourceSceneryId() == source.id(),
            source + " source ID");
        require(pairing.getInverseSceneryId() == inverse.id(),
            source + " inverse ID");
        require(pairing.getLevelDelta() == delta,
            source + " level delta");
        WorldEditorVerticalPairing.Pairing reverse =
            WorldEditorVerticalPairing.find(inverse.id());
        require(reverse != null, inverse + " reverse is not paired");
        require(reverse.getInverseSceneryId() == source.id(),
            inverse + " reverse inverse ID");
        require(reverse.getLevelDelta() == -delta,
            inverse + " reverse level delta");
    }

    public static void main(String[] args) {
        pair(
            SceneryId.LADDER_GENERIC_UP,
            SceneryId.LADDER_GENERIC_DOWN,
            1);
        pair(
            SceneryId.STAIRS_WOODEN_GENERIC_UP,
            SceneryId.STAIRS_WOODEN_GENERIC_DOWN,
            1);
        pair(
            SceneryId.STAIRS_STONE_GENERIC_UP,
            SceneryId.STAIRS_STONE_GENERIC_DOWN,
            1);
        require(WorldEditorVerticalPairing.find(223) == null,
            "specialized quest ladder was paired");
        require(WorldEditorVerticalPairing.find(487) == null,
            "transport lever was paired");
        require(WorldEditorVerticalPairing.find(-1) == null,
            "unknown scenery was paired");
        System.out.println("world-editor-vertical-pairing-ok");
    }
}
"""


class WorldEditorVerticalPairingTest(unittest.TestCase):
    def test_generic_pair_table_is_exact_and_reversible(self):
        with tempfile.TemporaryDirectory(
            prefix="world-editor-vertical-pairing-"
        ) as output:
            harness = Path(output) / "WorldEditorVerticalPairingHarness.java"
            harness.write_text(textwrap.dedent(HARNESS), encoding="utf-8")
            subprocess.run(
                [
                    "javac",
                    "-encoding",
                    "UTF-8",
                    "-d",
                    output,
                    str(SCENERY_IDS),
                    str(PAIRING),
                    str(harness),
                ],
                cwd=ROOT,
                check=True,
            )
            subprocess.run(
                ["java", "-cp", output, "WorldEditorVerticalPairingHarness"],
                cwd=ROOT,
                check=True,
            )

    def test_runtime_boundary_is_builder_owned_and_fail_closed(self):
        sessions = SESSIONS.read_text(encoding="utf-8")
        ladders = LADDERS.read_text(encoding="utf-8")

        self.assertIn("prepareNativeVerticalPair(", sessions)
        self.assertIn("WORLD_BUILDER_MODE", sessions)
        self.assertIn("WORLD_BUILDER_LAYERED_REVIEW_MODE", sessions)
        self.assertIn(
            "nativeSceneryPlacementId(sourceIdentity.getLocation())",
            sessions,
        )
        self.assertIn(
            "Automatic pairing cannot modify an accepted source level.",
            sessions,
        )
        self.assertIn("rollbackNativeVerticalProvision(provision)", sessions)
        self.assertIn("destinationCoordinate.getX()-1", sessions)
        self.assertIn("destinationCoordinate.getX()+1", sessions)
        self.assertIn("destinationCoordinate.getY()-1", sessions)
        self.assertIn("destinationCoordinate.getY()+1", sessions)

        hook = ladders.index("tryWorldBuilderVerticalPair(obj, command, player)")
        special = ladders.index("if (obj.getID() == 487")
        self.assertLess(hook, special)
        self.assertIn("if (!result.applicable)", ladders)
        self.assertIn(
            "player.teleportLayered(result.destination, false)",
            ladders,
        )
        self.assertIn(
            "catch (IllegalArgumentException | IllegalStateException failure)",
            ladders,
        )


if __name__ == "__main__":
    unittest.main()
