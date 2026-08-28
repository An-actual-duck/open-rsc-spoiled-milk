#!/usr/bin/env python3
import hashlib
import json
import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
CORE_JAR = ROOT / "server/core.jar"
GENERATOR = ROOT / "scripts/generate-world-builder-target-contract.py"
CAPABILITY = ROOT / "server/world-builder-capabilities.json"
CONFIGURATION = ROOT / "server/world-builder-configs/primary.json"
PACKAGE = Path(
    os.environ.get(
        "SPOILED_MILK_LAYERED_PACKAGE",
        ROOT / "tools/layered-maps/workspace/spoiled-milk-package/package",
    )
)

HARNESS = r"""
import com.openrsc.server.io.WorldBuilderInstalledMapActivation;
import com.openrsc.server.io.NativeLayeredWorldPackageCatalog;
import com.openrsc.server.io.NativeLayeredWorldRuntimeProfile;
import java.util.Optional;

public final class WorldBuilderInstalledMapActivationFixture {
    public static void main(String[] args) throws Exception {
        System.setProperty("openrsc.worldBuilderTargetRoot", args[0]);
        Optional<WorldBuilderInstalledMapActivation.Activation> found =
            WorldBuilderInstalledMapActivation.discover();
        if (Boolean.parseBoolean(args[1])) {
            if (!found.isPresent()) throw new AssertionError("activation absent");
            NativeLayeredWorldRuntimeProfile.fromConfiguration(
                WorldBuilderInstalledMapActivation.RUNTIME_PROFILE).validate(
                    NativeLayeredWorldPackageCatalog.loadConfigured(
                        found.get().getServerPackageRoot().toString()));
            System.out.println(found.get().getPackageFingerprintSha256());
            System.out.println(found.get().getManifestSha256());
        } else if (found.isPresent()) {
            throw new AssertionError("packed configuration activated");
        }
    }
}
"""


def package_fingerprint(root: Path) -> str:
    digest = hashlib.sha256()
    for path in sorted(item for item in root.rglob("*") if item.is_file()):
        relative = path.relative_to(root).as_posix()
        sha256 = hashlib.sha256(path.read_bytes()).hexdigest()
        for value in (relative, sha256, str(path.stat().st_size)):
            digest.update(value.encode("utf-8"))
            digest.update(b"\0")
    return digest.hexdigest()


class WorldBuilderTargetContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        subprocess.run(
            [str(ROOT / "scripts/build-server.sh")],
            cwd=ROOT,
            check=True,
            stdout=subprocess.DEVNULL,
        )
        cls.compiled = tempfile.TemporaryDirectory(prefix="wb-target-contract-")
        classes = Path(cls.compiled.name)
        source = classes / "WorldBuilderInstalledMapActivationFixture.java"
        source.write_text(HARNESS, encoding="utf-8")
        subprocess.run(
            [
                "javac", "-source", "8", "-target", "8",
                "-cp", str(CORE_JAR), "-d", str(classes), str(source),
            ],
            cwd=ROOT,
            check=True,
        )
        cls.classpath = f"{classes}:{CORE_JAR}"

    @classmethod
    def tearDownClass(cls):
        cls.compiled.cleanup()

    def run_harness(self, target: Path, expected: bool):
        return subprocess.run(
            [
                "java", "-cp", self.classpath,
                "WorldBuilderInstalledMapActivationFixture",
                str(target), str(expected).lower(),
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
        )

    def test_generated_contract_is_deterministic_and_truthful(self):
        before = {
            path: path.read_bytes()
            for path in (
                CAPABILITY,
                CONFIGURATION,
                ROOT / "server/world-builder-fallback/definitions.json",
                ROOT / "Client_Base/world-builder-fallback/definitions.json",
                ROOT / "server/world-builder-fallback/runtime.json",
                ROOT / "Client_Base/world-builder-fallback/runtime.json",
            )
        }
        subprocess.run(["python3", str(GENERATOR)], cwd=ROOT, check=True)
        self.assertEqual(before, {path: path.read_bytes() for path in before})

        capability = json.loads(CAPABILITY.read_text(encoding="utf-8"))
        configuration = json.loads(CONFIGURATION.read_text(encoding="utf-8"))
        self.assertEqual("spoiled-milk-packed-v1", capability["adapterId"])
        self.assertEqual(
            "spoiled-milk-layered-install-v1",
            capability["install"]["mutationProfileId"],
        )
        self.assertTrue(capability["install"]["enabled"])
        self.assertEqual(["layered-package"], capability["install"]["serverRoles"])
        self.assertEqual(["layered-package"], capability["install"]["clientRoles"])
        self.assertEqual("primary", configuration["configurationId"])
        self.assertEqual("packed", configuration["representation"])
        self.assertEqual(
            (ROOT / configuration["serverMapRelativePath"]).read_bytes(),
            (ROOT / configuration["clientMapRelativePath"]).read_bytes(),
        )
        server_catalog = ROOT / configuration["serverDefinitionCatalogRelativePath"]
        client_catalog = ROOT / configuration["clientDefinitionCatalogRelativePath"]
        self.assertEqual(server_catalog.read_bytes(), client_catalog.read_bytes())
        self.assertEqual(
            hashlib.sha256(server_catalog.read_bytes()).hexdigest(),
            capability["definitions"]["catalogSha256"],
        )
        self.assertEqual(
            {"boundary", "ground-item", "npc", "scenery"},
            {item["family"] for item in configuration["placements"]},
        )

    def test_packed_configuration_is_inert(self):
        result = self.run_harness(ROOT, False)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def make_layered_target(self, base: Path) -> tuple[Path, str]:
        fingerprint = package_fingerprint(PACKAGE)
        target = base / "target"
        server_package = target / f"server/world-builder/packages/{fingerprint}/package"
        client_package = target / f"Client_Base/world-builder/packages/{fingerprint}/package"
        shutil.copytree(PACKAGE, server_package)
        shutil.copytree(PACKAGE, client_package)
        configuration = json.loads(CONFIGURATION.read_text(encoding="utf-8"))
        configuration.update(
            {
                "representation": "layered",
                "serverMapRelativePath": server_package.relative_to(target).as_posix(),
                "clientMapRelativePath": client_package.relative_to(target).as_posix(),
                "placements": [],
            }
        )
        destination = target / "server/world-builder-configs/primary.json"
        destination.parent.mkdir(parents=True)
        destination.write_text(
            json.dumps(configuration, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        return target, fingerprint

    def test_layered_configuration_activates_matching_content_addressed_roles(self):
        with tempfile.TemporaryDirectory(prefix="wb-layered-activation-") as raw:
            target, fingerprint = self.make_layered_target(Path(raw))
            result = self.run_harness(target, True)
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            lines = result.stdout.splitlines()
            self.assertEqual(fingerprint, lines[0])
            self.assertEqual(
                hashlib.sha256((PACKAGE / "manifest.json").read_bytes()).hexdigest(),
                lines[1],
            )

    def test_client_drift_fails_closed(self):
        with tempfile.TemporaryDirectory(prefix="wb-layered-drift-") as raw:
            target, fingerprint = self.make_layered_target(Path(raw))
            manifest = (
                target
                / f"Client_Base/world-builder/packages/{fingerprint}/package/manifest.json"
            )
            manifest.write_bytes(manifest.read_bytes() + b"\n")
            result = self.run_harness(target, True)
            self.assertNotEqual(0, result.returncode)
            self.assertIn("server/client package roles differ", result.stderr)


if __name__ == "__main__":
    unittest.main()
