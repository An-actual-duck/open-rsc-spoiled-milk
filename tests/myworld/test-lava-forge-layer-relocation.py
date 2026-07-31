#!/usr/bin/env python3

import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
COORDINATE_ROOT = (
    ROOT / "server/src/com/openrsc/server/model/world/coordinate"
)


class LavaForgeLayerRelocationTest(unittest.TestCase):
    def test_canonical_location_and_persistence_contract(self):
        harness = textwrap.dedent(
            """
            import com.openrsc.server.model.world.coordinate.LavaForgeLocation;
            import com.openrsc.server.model.world.coordinate.WorldCoordinate;
            import com.openrsc.server.model.world.coordinate.WorldLocation;
            import com.openrsc.server.model.world.coordinate.WorldSpaceId;

            public final class LavaForgeLocationHarness {
                private static void require(
                    boolean value,
                    String message
                ) {
                    if (!value) throw new AssertionError(message);
                }

                public static void main(String[] arguments) {
                    WorldLocation legacy = new WorldLocation(
                        WorldSpaceId.GLOBAL,
                        new WorldCoordinate(329, 587, -1));
                    WorldLocation relocated =
                        LavaForgeLocation
                            .relocateLegacyComponentCandidate(legacy);
                    require(
                        relocated.equals(LavaForgeLocation.entrance()),
                        "legacy entrance relocation");
                    require(
                        LavaForgeLocation.migratePersistedLocation(
                            legacy, true, 0).equals(relocated),
                        "non-void persisted-location migration");
                    require(
                        LavaForgeLocation.migratePersistedLocation(
                            legacy, false, 0).equals(legacy),
                        "absent target refusal");
                    require(
                        LavaForgeLocation.migratePersistedLocation(
                            legacy, true, 8).equals(legacy),
                        "void target refusal");
                    require(
                        LavaForgeLocation.isRelocated(relocated),
                        "relocated scope");
                    require(
                        LavaForgeLocation.isExitLadder(
                            LavaForgeLocation.at(329, 586)),
                        "level-qualified exit ladder");
                    require(
                        LavaForgeLocation.isDwarvenMineDownLadder(
                            new WorldLocation(
                                WorldSpaceId.GLOBAL,
                                new WorldCoordinate(271, 508, -1))),
                        "level-qualified down ladder");
                    require(
                        LavaForgeLocation.dwarvenMineReturn()
                            .getCoordinate().equals(
                                new WorldCoordinate(271, 507, -1)),
                        "dwarven-mine return");
                    require(
                        !LavaForgeLocation.isRelocated(
                            new WorldLocation(
                                WorldSpaceId.GLOBAL,
                                new WorldCoordinate(329, 587, -1))),
                        "source level is not relocated scope");
                    require(
                        !LavaForgeLocation.isExitLadder(
                            new WorldLocation(
                                WorldSpaceId.GLOBAL,
                                new WorldCoordinate(329, 586, -1))),
                        "legacy ladder cannot masquerade as deep ladder");
                }
            }
            """
        )
        with tempfile.TemporaryDirectory(
            prefix="lava-forge-location-"
        ) as temp:
            temp_path = Path(temp)
            source = temp_path / "LavaForgeLocationHarness.java"
            source.write_text(harness, encoding="utf-8")
            subprocess.run(
                [
                    "javac",
                    "-source",
                    "8",
                    "-target",
                    "8",
                    "-d",
                    str(temp_path),
                    str(COORDINATE_ROOT / "WorldCoordinate.java"),
                    str(COORDINATE_ROOT / "WorldLocation.java"),
                    str(COORDINATE_ROOT / "WorldSpaceId.java"),
                    str(COORDINATE_ROOT / "LavaForgeLocation.java"),
                    str(source),
                ],
                cwd=ROOT,
                check=True,
            )
            subprocess.run(
                [
                    "java",
                    "-cp",
                    str(temp_path),
                    "LavaForgeLocationHarness",
                ],
                cwd=ROOT,
                check=True,
            )

    def test_runtime_owners_are_level_qualified_with_legacy_fallback(self):
        expected = {
            "server/plugins/com/openrsc/server/plugins/authentic/"
            "defaults/Ladders.java": (
                "LavaForgeLocation.isDwarvenMineDownLadder",
                "hasNativeLayeredTerrain(",
                "LavaForgeLocation.entrance()",
                "LavaForgeLocation.isExitLadder",
                "LavaForgeLocation.dwarvenMineReturn()",
                "player.teleportLegacy(329, 3419, false)",
                "player.teleportLegacy(271, 3339, false)",
            ),
            "server/src/com/openrsc/server/service/PlayerService.java": (
                "LavaForgeLocation.relocateLegacyComponentCandidate",
                "getTile(relocatedLavaForge)",
                "relocatedLavaForgeTile.overlay & 0xff",
                "LavaForgeLocation.PERSISTENCE_MIGRATION_ORIGIN",
            ),
            "server/src/com/openrsc/server/content/worldedit/"
            "WorldEditorSessionManager.java": (
                "level==-2||level==-1||level==0||level==1||level==2||level==10",
            ),
        }
        for relative, needles in expected.items():
            source = (ROOT / relative).read_text(encoding="utf-8")
            for needle in needles:
                self.assertIn(
                    needle,
                    source,
                    f"{relative} lost {needle}",
                )

    def test_transform_has_exact_lava_and_dragon_guards(self):
        relocation = (
            ROOT
            / "tools/layered-maps/src/com/openrsc/layeredmaps/"
            "SpoiledMilkLavaForgeRelocation.java"
        ).read_text(encoding="utf-8")
        for needle in (
            "EXPECTED_COMPONENT_TILES = 2170",
            "EXPECTED_COPIED_TILES = 2374",
            "EXPECTED_TARGET_SECTORS = 7",
            "EXPECTED_DRAGON_COMPONENT_TILES = 2955",
            "EXPECTED_DRAGON_SEPARATION = 6",
            'result.put("npcs", Integer.valueOf(20))',
            'result.put("groundItems", Integer.valueOf(1))',
            'result.put("scenery", Integer.valueOf(3))',
            'result.put("npcs", Integer.valueOf(83))',
            'result.put("groundItems", Integer.valueOf(10))',
            'result.put("scenery", Integer.valueOf(217))',
            'result.put("boundaries", Integer.valueOf(11))',
            "verifyTilesUnchanged(",
            "protectedDragonPlacements",
        ):
            self.assertIn(needle, relocation)

        generator = (
            ROOT
            / "tools/layered-maps/src/com/openrsc/layeredmaps/"
            "PreservationTerrainPackageGenerator.java"
        ).read_text(encoding="utf-8")
        self.assertIn(
            "SpoiledMilkLavaForgeRelocation.apply(",
            generator,
        )
        self.assertIn(
            "lavaForgeRelocation.verifyPlacementCounts()",
            generator,
        )
        self.assertIn(
            '"terrainRelocations"',
            generator,
        )


if __name__ == "__main__":
    unittest.main()
