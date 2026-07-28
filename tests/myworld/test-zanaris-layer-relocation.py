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


class ZanarisLayerRelocationTest(unittest.TestCase):
    def test_canonical_location_contract(self):
        harness = textwrap.dedent(
            """
            import com.openrsc.server.model.world.coordinate.WorldCoordinate;
            import com.openrsc.server.model.world.coordinate.WorldLocation;
            import com.openrsc.server.model.world.coordinate.WorldSpaceId;
            import com.openrsc.server.model.world.coordinate.ZanarisLocation;

            public final class ZanarisLocationHarness {
                private static void require(
                    boolean value,
                    String message
                ) {
                    if (!value) throw new AssertionError(message);
                }

                public static void main(String[] arguments) {
                    WorldLocation legacy = new WorldLocation(
                        WorldSpaceId.GLOBAL,
                        new WorldCoordinate(126, 686, -1));
                    WorldLocation relocated =
                        ZanarisLocation.relocateLegacyComponent(legacy);
                    require(
                        relocated.equals(ZanarisLocation.entrance()),
                        "legacy entrance relocation");
                    require(
                        ZanarisLocation.migratePersistedLocation(
                            legacy, true, 0).equals(relocated),
                        "non-void persisted-location migration");
                    require(
                        ZanarisLocation.migratePersistedLocation(
                            legacy, false, 0).equals(legacy),
                        "absent target refusal");
                    require(
                        ZanarisLocation.migratePersistedLocation(
                            legacy, true, 8).equals(legacy),
                        "void target refusal");
                    require(
                        ZanarisLocation.isRelocated(relocated),
                        "relocated scope");
                    require(
                        ZanarisLocation.logicalY(3518) == 686,
                        "legacy Y translation");
                    require(
                        ZanarisLocation.isBank(
                            ZanarisLocation.at(172, 697)),
                        "irregular bank footprint");
                    require(
                        !ZanarisLocation.isBank(
                            new WorldLocation(
                                WorldSpaceId.GLOBAL,
                                new WorldCoordinate(172, 697, 0))),
                        "surface coordinate must not become a bank");
                    require(
                        ZanarisLocation.isFlourChute(
                            ZanarisLocation.at(162, 701)),
                        "flour chute");
                    require(
                        ZanarisLocation.surfaceExit().getCoordinate()
                            .getLevel() == 0,
                        "ladder exit level");
                    require(
                        ZanarisLocation.isTownAlias("LostCity")
                            && ZanarisLocation.isTownAlias("zanaris"),
                        "town aliases");
                }
            }
            """
        )
        with tempfile.TemporaryDirectory(
            prefix="zanaris-location-"
        ) as temp:
            temp_path = Path(temp)
            source = temp_path / "ZanarisLocationHarness.java"
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
                    str(
                        COORDINATE_ROOT
                        / "WorldCoordinate.java"
                    ),
                    str(COORDINATE_ROOT / "WorldLocation.java"),
                    str(COORDINATE_ROOT / "WorldSpaceId.java"),
                    str(COORDINATE_ROOT / "ZanarisLocation.java"),
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
                    "ZanarisLocationHarness",
                ],
                cwd=ROOT,
                check=True,
            )

    def test_every_runtime_coordinate_owner_uses_the_qualified_location(self):
        expected = {
            "server/plugins/com/openrsc/server/plugins/authentic/"
            "quests/members/LostCity.java": (
                "ZanarisLocation.entrance()",
                "player.teleportLayered(destination, false)",
            ),
            "server/plugins/com/openrsc/server/plugins/authentic/"
            "defaults/Ladders.java": (
                "isZanarisExitLadder",
                "ZanarisLocation.surfaceExit()",
                "npc.sharesSpatialDomain(player)",
            ),
            "server/plugins/com/openrsc/server/plugins/authentic/"
            "defaults/DoorAction.java": (
                "ZanarisLocation.logicalY(3539)",
                "candidate.sharesSpatialDomain(player)",
            ),
            "server/plugins/com/openrsc/server/plugins/authentic/"
            "misc/Hopper.java": (
                "ZanarisLocation.at(162, 701)",
            ),
            "server/plugins/com/openrsc/server/plugins/authentic/"
            "commands/Event.java": (
                "tryRelocatedZanarisTownTeleport",
                "ZanarisLocation.entrance()",
            ),
            "server/src/com/openrsc/server/service/PlayerService.java": (
                "ZanarisLocation.migratePersistedLocation",
                "getTile(relocatedZanaris)",
                "relocatedZanarisTile.overlay & 0xff",
                "ZanarisLocation.PERSISTENCE_MIGRATION_ORIGIN",
            ),
            "server/src/com/openrsc/server/model/Point.java": (
                "ZanarisLocation.isBank(worldLocation)",
                "ZanarisLocation.isFlourChute(worldLocation)",
            ),
            "server/src/com/openrsc/server/content/worldedit/"
            "WorldEditorSessionManager.java": (
                "level==-1||level==0||level==1||level==2||level==10",
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


if __name__ == "__main__":
    unittest.main()
