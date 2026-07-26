#!/usr/bin/env python3
import json
import hashlib
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server"
CORE_JAR = SERVER / "core.jar"
PRIMARY = ROOT / "tools/layered-maps/fixtures/native-package-v1"
SECONDARY = (
    ROOT / "tools/layered-maps/fixtures/native-package-transition-v1"
)
REGION_MANAGER = (
    SERVER / "src/com/openrsc/server/model/world/region/RegionManager.java"
)
CATALOG = (
    SERVER / "src/com/openrsc/server/io/NativeLayeredWorldPackageCatalog.java"
)
PLAYER = SERVER / "src/com/openrsc/server/model/entity/player/Player.java"
GAME_STATE_UPDATER = SERVER / "src/com/openrsc/server/GameStateUpdater.java"
DEVELOPMENT = (
    SERVER
    / "plugins/com/openrsc/server/plugins/authentic/commands/Development.java"
)


HARNESS = r"""
import com.openrsc.server.io.NativeLayeredWorldPackage;
import com.openrsc.server.io.NativeLayeredWorldPackageCatalog;
import com.openrsc.server.io.NativeLayeredWorldPackageCatalog.Transition;
import com.openrsc.server.io.NativeLayeredWorldPackageCatalog.TransitionKind;
import com.openrsc.server.model.world.coordinate.WorldCoordinate;
import com.openrsc.server.model.world.coordinate.WorldLocation;
import com.openrsc.server.model.world.coordinate.WorldSpaceId;
import java.io.File;
import java.nio.file.Paths;
import java.util.Arrays;

public final class NativeLayeredPackageCatalogFixture {
    public static void main(String[] args) throws Exception {
        NativeLayeredWorldPackage primary =
            NativeLayeredWorldPackage.load(Paths.get(args[0]));
        NativeLayeredWorldPackage secondary =
            NativeLayeredWorldPackage.load(Paths.get(args[1]));
        NativeLayeredWorldPackageCatalog catalog =
            NativeLayeredWorldPackageCatalog.of(
                Arrays.asList(primary, secondary));
        check(catalog.size() == 2, "catalog size");
        check(catalog.getPrimaryPackage() == primary, "primary package");
        check(catalog.findPackage(location(450, 600, -2))
                .orElseThrow(() -> new AssertionError("primary owner"))
                == primary,
            "primary terrain owner");
        check(catalog.findPackage(location(450, 600, -4))
                .orElseThrow(() -> new AssertionError("secondary owner"))
                == secondary,
            "secondary terrain owner");
        check(!catalog.findPackage(location(450, 600, -1)).isPresent(),
            "legacy location has no native owner");
        check(catalog.findPackage(secondary.getPackageId())
                .orElseThrow(() -> new AssertionError("package ID lookup"))
                == secondary,
            "package ID lookup");

        Transition initial = catalog.prepareTransition(
            null, location(450, 600, -2), false);
        check(initial.getKind() == TransitionKind.INITIAL_PACKAGE,
            "initial package restore");
        Transition within = catalog.prepareTransition(
            location(450, 600, -2), location(451, 600, -2), false);
        check(within.getKind() == TransitionKind.WITHIN_PACKAGE,
            "ordinary same-package movement");
        Transition cross = catalog.prepareTransition(
            location(450, 600, -2), location(450, 600, -4), true);
        check(cross.getKind() == TransitionKind.CROSS_PACKAGE,
            "explicit cross-package transition");
        check(primary.getPackageId().equals(cross.getSourcePackageId()),
            "cross-package source");
        check(secondary.getPackageId().equals(
                cross.getDestinationPackageId()),
            "cross-package destination");
        refuseImplicitCross(catalog);
        check(catalog.prepareTransition(
                location(450, 600, -1),
                location(450, 600, -2),
                true).getKind() == TransitionKind.ENTER_PACKAGE,
            "explicit package entry");
        check(catalog.prepareTransition(
                location(450, 600, -2),
                location(450, 600, -1),
                true).getKind() == TransitionKind.EXIT_PACKAGE,
            "explicit package exit");

        NativeLayeredWorldPackageCatalog configured =
            NativeLayeredWorldPackageCatalog.loadConfigured(
                args[0] + File.pathSeparator + args[1]);
        check(configured.size() == 2, "configured package list");
        try {
            configured.getPackages().clear();
            throw new AssertionError("Expected immutable package list");
        } catch (UnsupportedOperationException expected) {
            // Expected.
        }
    }

    private static void refuseImplicitCross(
            NativeLayeredWorldPackageCatalog catalog) {
        try {
            catalog.prepareTransition(
                location(450, 600, -2),
                location(450, 600, -4),
                false);
            throw new AssertionError("Expected implicit cross refusal");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("explicit transition"),
                "implicit cross refusal reason");
        }
    }

    private static WorldLocation location(int x, int y, int level) {
        return new WorldLocation(
            WorldSpaceId.GLOBAL, new WorldCoordinate(x, y, level));
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
    }
}
"""


class LayeredNativePackageCatalogTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        subprocess.run(
            [str(ROOT / "scripts/build-server.sh")],
            cwd=ROOT,
            check=True,
            stdout=subprocess.DEVNULL,
        )
        cls.compile_temp = tempfile.TemporaryDirectory(
            prefix="layered-native-catalog-classes-"
        )
        cls.classes = Path(cls.compile_temp.name)
        fixture = cls.classes / "NativeLayeredPackageCatalogFixture.java"
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

    def run_catalog(self, primary=PRIMARY, secondary=SECONDARY):
        return subprocess.run(
            [
                "java",
                "-cp",
                f"{self.classes}:{CORE_JAR}",
                "NativeLayeredPackageCatalogFixture",
                str(primary),
                str(secondary),
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )

    def test_catalog_resolves_atomic_cross_package_transition(self):
        result = self.run_catalog()
        self.assertEqual(0, result.returncode, result.stderr)

    def test_catalog_refuses_duplicate_package_identity(self):
        result = self.run_catalog(PRIMARY, PRIMARY)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Duplicate native layered package ID", result.stderr)

    def test_catalog_refuses_overlapping_terrain_ownership(self):
        with tempfile.TemporaryDirectory(
            prefix="layered-native-overlap-"
        ) as temp:
            package = Path(temp) / "package"
            shutil.copytree(SECONDARY, package)
            manifest_path = package / "manifest.json"
            manifest = json.loads(
                manifest_path.read_text(encoding="utf-8")
            )
            manifest["levels"][0]["level"] = -2
            manifest["terrainSectors"][0]["level"] = -2
            manifest["placementSets"][0]["level"] = -2
            placement_path = (
                package / manifest["placementSets"][0]["path"]
            )
            placement = json.loads(
                placement_path.read_text(encoding="utf-8")
            )
            placement["level"] = -2
            placement_path.write_text(
                json.dumps(placement, indent=2) + "\n",
                encoding="utf-8",
            )
            manifest["placementSets"][0]["sha256"] = hashlib.sha256(
                placement_path.read_bytes()
            ).hexdigest()
            manifest_path.write_text(
                json.dumps(manifest, indent=2) + "\n",
                encoding="utf-8",
            )
            result = self.run_catalog(PRIMARY, package)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("terrain ownership overlaps", result.stderr)

    def test_runtime_wiring_resolves_package_before_mutation_and_protocol(self):
        manager = REGION_MANAGER.read_text(encoding="utf-8")
        catalog = CATALOG.read_text(encoding="utf-8")
        player = PLAYER.read_text(encoding="utf-8")
        updater = GAME_STATE_UPDATER.read_text(encoding="utf-8")
        development = DEVELOPMENT.read_text(encoding="utf-8")
        self.assertIn("NativeLayeredWorldPackageCatalog", manager)
        self.assertIn("loadConfigured(", manager)
        self.assertIn("prepareNativeLayeredTransition(", manager)
        self.assertIn("Cross-scope native layered movement", catalog)
        preflight = player.index("prepareNativeLayeredTransition(")
        mutation = player.index(
            "setLocationCompatibility(projection, location, teleported);",
            preflight,
        )
        self.assertLess(preflight, mutation)
        self.assertIn("findNativeLayeredWorldPackage(location)", updater)
        self.assertIn('"package".equals(action)', development)
        self.assertIn("Atomic package transition committed", development)


if __name__ == "__main__":
    unittest.main()
