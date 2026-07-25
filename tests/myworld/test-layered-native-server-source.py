#!/usr/bin/env python3
import json
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server"
CORE_JAR = SERVER / "core.jar"
PACKAGE = ROOT / "tools/layered-maps/fixtures/native-package-v1"
SOURCE = SERVER / "src/com/openrsc/server/io/NativeLayeredWorldPackage.java"
CONFIGURATION = SERVER / "src/com/openrsc/server/ServerConfiguration.java"
REGION_MANAGER = (
    SERVER
    / "src/com/openrsc/server/model/world/region/RegionManager.java"
)
DEVELOPMENT = (
    SERVER
    / "plugins/com/openrsc/server/plugins/authentic/commands/Development.java"
)


HARNESS = r"""
import com.openrsc.server.io.NativeLayeredTerrainSector;
import com.openrsc.server.io.NativeLayeredTerrainTile;
import com.openrsc.server.io.NativeLayeredWorldPackage;
import com.openrsc.server.io.Sector;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldMapSectorId;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;
import java.nio.file.Paths;

public final class NativeLayeredServerSourceFixture {
    public static void main(String[] args) throws Exception {
        NativeLayeredWorldPackage world =
            NativeLayeredWorldPackage.load(Paths.get(args[0]));
        check("rsc-remastered.native-loader-lab".equals(world.getPackageId()), "package ID");
        check("0.1.0".equals(world.getPackageVersion()), "package version");
        check(world.getPresentationChunkSize() == 24, "presentation chunk");
        check(world.getWorldSpaceCount() == 1, "world-space count");
        check(world.getLevelCount() == 3, "level count");
        check(world.getTerrainSectorCount() == 3, "sector count");
        check(world.declaresLevel(WorldSpaceId.GLOBAL, 0), "surface declaration");
        check(world.declaresLevel(WorldSpaceId.GLOBAL, -2), "deep declaration");
        check(world.declaresLevel(WorldSpaceId.GLOBAL, -3), "expanded declaration");
        check(!world.declaresLevel(WorldSpaceId.GLOBAL, -4), "absent declaration");

        NativeLayeredTerrainTile before = tile(world, 479, 600, -2);
        NativeLayeredTerrainTile after = tile(world, 480, 600, -2);
        check(before.getElevation() == 0 && before.getTexture() == 0,
            "left adjacent sector tile");
        check(after.getElevation() == 4 && after.getTexture() == 2,
            "right adjacent sector tile");
        check(tile(world, 450, 600, -3).getElevation() == 8,
            "data-declared expanded level tile");
        check(!world.findTile(location(450, 600, 0)).isPresent(),
            "same X/Y surface isolation");

        WorldMapSectorId leftId =
            new WorldMapSectorId(WorldSpaceId.GLOBAL, -2, 9, 12);
        NativeLayeredTerrainSector left =
            world.findSector(leftId).orElseThrow(() -> new AssertionError("left sector"));
        Sector detached = left.copyToDetachedLegacySector();
        check(detached.getTile(0, 0).getGroundElevation() == 0,
            "detached sector first tile");
        check(detached.getTile(47, 47).getGroundTexture() == 0,
            "detached sector last tile");
        check(detached.pack().remaining() == 48 * 48 * 10,
            "detached full-fidelity byte count");
    }

    private static NativeLayeredTerrainTile tile(
            NativeLayeredWorldPackage world, int x, int y, int level) {
        return world.findTile(location(x, y, level))
            .orElseThrow(() -> new AssertionError("missing tile " + x + "," + y + "," + level));
    }

    private static WorldLocation location(int x, int y, int level) {
        return WorldLocation.global(new WorldCoordinate(x, y, level));
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
"""


class LayeredNativeServerSourceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        subprocess.run(
            [str(ROOT / "scripts/build-server.sh")],
            cwd=ROOT,
            check=True,
            stdout=subprocess.DEVNULL,
        )
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-native-server-source-"
        )
        cls.classes = Path(cls.compile_temp.name)
        fixture = cls.classes / "NativeLayeredServerSourceFixture.java"
        fixture.write_text(HARNESS, encoding="utf-8")
        subprocess.run(
            [
                "javac",
                "-source",
                "8",
                "-target",
                "8",
                "-cp",
                str(CORE_JAR),
                "-d",
                str(cls.classes),
                str(fixture),
            ],
            cwd=ROOT,
            check=True,
        )

    @classmethod
    def tearDownClass(cls):
        cls.compile_temp.cleanup()

    def run_fixture(self, package):
        return subprocess.run(
            [
                "java",
                "-cp",
                f"{self.classes}:{CORE_JAR}",
                "NativeLayeredServerSourceFixture",
                str(package),
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )

    def test_detached_server_source_loads_adjacent_pages_and_expanded_depth(self):
        result = self.run_fixture(PACKAGE)
        self.assertEqual(0, result.returncode, result.stderr)

    def test_server_loader_has_no_minus_two_or_minus_three_level_enumeration(self):
        with tempfile.TemporaryDirectory(prefix="native-server-depth-") as temp:
            package = Path(temp) / "package"
            shutil.copytree(PACKAGE, package)
            path = package / "manifest.json"
            manifest = json.loads(path.read_text(encoding="utf-8"))
            for level in manifest["levels"]:
                if level["level"] == -3:
                    level["level"] = -37
            for sector in manifest["terrainSectors"]:
                if sector["level"] == -3:
                    sector["level"] = -37
            path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")

            probe_source = HARNESS.replace(
                'check(world.declaresLevel(WorldSpaceId.GLOBAL, -3), "expanded declaration");',
                'check(world.declaresLevel(WorldSpaceId.GLOBAL, -37), "expanded declaration");',
            ).replace(
                "tile(world, 450, 600, -3)",
                "tile(world, 450, 600, -37)",
            )
            probe = self.classes / "NativeLayeredServerSourceFixture.java"
            probe.write_text(probe_source, encoding="utf-8")
            subprocess.run(
                [
                    "javac",
                    "-source",
                    "8",
                    "-target",
                    "8",
                    "-cp",
                    str(CORE_JAR),
                    "-d",
                    str(self.classes),
                    str(probe),
                ],
                cwd=ROOT,
                check=True,
            )

            result = self.run_fixture(package)
            self.assertEqual(0, result.returncode, result.stderr)

    def test_source_is_detached_from_runtime_world_and_region_authority(self):
        source = SOURCE.read_text(encoding="utf-8")
        forbidden = (
            "com.openrsc.server.model.world.World",
            "RegionManager",
            "TileValue",
            "register",
            "unregister",
        )
        for token in forbidden:
            with self.subTest(token=token):
                self.assertNotIn(token, source)

    def test_private_runtime_gate_is_explicit_fail_closed_and_reversible(self):
        configuration = CONFIGURATION.read_text(encoding="utf-8")
        region_manager = REGION_MANAGER.read_text(encoding="utf-8")
        development = DEVELOPMENT.read_text(encoding="utf-8")
        self.assertIn("WANT_LAYERED_NATIVE_TERRAIN_PACKAGE", configuration)
        self.assertIn(
            "OPENRSC_LAYERED_NATIVE_TERRAIN_PACKAGE", configuration
        )
        self.assertIn(
            '"want_layered_native_terrain_package",\n\t\t\tfalse',
            configuration,
        )
        self.assertIn(
            "LAYERED_NATIVE_TERRAIN_PACKAGE_PATH", configuration
        )
        self.assertIn(
            "NativeLayeredWorldPackage.load", region_manager
        )
        self.assertIn(
            "validateNativeDeepFixturePackage(loaded)", region_manager
        )
        self.assertIn(
            "NativeLayeredTerrainTile source = nativeLayeredWorldPackage",
            region_manager,
        )
        self.assertIn(
            "return nativeDeepFixtureTile(location)", region_manager
        )
        self.assertIn(
            "return syntheticDeepFixtureTile()", region_manager
        )
        self.assertIn(
            "Native layered deep ", development
        )
        self.assertIn(
            "NativeLayeredWorldPackage.RUNTIME_PROJECTION_ID", development
        )
        self.assertIn(
            "nativePackage.getPresentationChunkSize()", development
        )
        self.assertIn(
            '"Deep fixture logical="', development
        )


if __name__ == "__main__":
    unittest.main()
