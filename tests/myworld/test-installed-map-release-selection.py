#!/usr/bin/env python3
"""Release-only selector/orchestration tests; no server or public paths touched."""
import importlib.util
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location("selection", ROOT / "scripts/installed-map-selection.py")
selection = importlib.util.module_from_spec(spec)
spec.loader.exec_module(selection)


class InstalledReleaseTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="installed-release-")
        self.root = Path(self.temp.name) / "source"
        self.root.mkdir()
        (self.root / "scripts").mkdir()
        shutil.copyfile(ROOT / "scripts/installed-map-selection.py", self.root / "scripts/installed-map-selection.py")
        self.install("first")

    def tearDown(self):
        self.temp.cleanup()

    def write(self, relative, data):
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(data))

    def install(self, version):
        staging = Path(self.temp.name) / ("staging-" + version)
        staging.mkdir()
        (staging / "manifest.json").write_text(json.dumps(dict(packageId="unseen-package", packageVersion=version)))
        (staging / "terrain").write_bytes(version.encode())
        _, fingerprint = selection.inventory(staging)
        server = "server/world-builder/packages/" + fingerprint + "/package"
        client = "Client_Base/world-builder/packages/" + fingerprint + "/package"
        for relative in (server, client):
            path = self.root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            shutil.copytree(staging, path)
        self.primary = dict(schemaVersion=1, manifestType="world-builder-map-configuration", active=True,
                            configurationId="primary", representation="layered", serverMapRelativePath=server,
                            clientMapRelativePath=client, assets=[], placements=[], serverRuntimeRelativePath="server/runtime.json",
                            clientRuntimeRelativePath="Client_Base/runtime.json", serverDefinitionCatalogRelativePath="server/defs.json",
                            clientDefinitionCatalogRelativePath="Client_Base/defs.json")
        self.profile = dict(schemaVersion=1, manifestType="world-builder-installed-client-profile", active=True,
                            packageId="unseen-package", packageVersion=version, packageFingerprintSha256=fingerprint,
                            manifestSha256=selection.document(staging / "manifest.json")[1],
                            packageRelativePath=client[len("Client_Base/"):])
        self.write(selection.PRIMARY, self.primary)
        self.write(selection.PROFILE, self.profile)
        return fingerprint

    def shell(self, command, success=True):
        result = subprocess.run(["bash", "-euc", 'source "$1"; '+command, "test",
                                 str(ROOT / "scripts/lib/layered-world-package.sh")], text=True,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.assertEqual(result.returncode == 0, success, result.stdout + result.stderr)
        return result

    def stub_parser(self, body=""):
        # Deliberate parser double: selector/orchestration only. Real maintained
        # package-check is exercised separately on the actual selected pair.
        script = self.root / "tools/layered-maps/layered-maps.sh"
        script.parent.mkdir(parents=True, exist_ok=True)
        script.write_text('#!/bin/bash\nset -eu\nmkdir -p "$LAYERED_MAPS_WORKSPACE"\nprintf "{}\\n" > "$LAYERED_MAPS_WORKSPACE/package-validation.json"\n'+body)
        script.chmod(0o700)

    def test_repeated_import_selects_new_pair_not_original(self):
        first = selection.select(self.root)
        new = self.install("edited")
        second = selection.select(self.root)
        self.assertEqual(new, second["packageFingerprintSha256"])
        self.assertNotEqual(first["manifestSha256"], second["manifestSha256"])
        self.assertTrue(Path(first["serverPackage"]).is_dir())
        self.assertEqual(second, selection.select(self.root))

    def test_pair_and_profile_mismatch_refuse(self):
        self.profile["manifestSha256"] = "0" * 64
        self.write(selection.PROFILE, self.profile)
        with self.assertRaises(ValueError): selection.select(self.root)
        self.profile["manifestSha256"] = selection.document(self.root / self.primary["serverMapRelativePath"] / "manifest.json")[1]
        self.write(selection.PROFILE, self.profile)
        (self.root / self.primary["clientMapRelativePath"] / "terrain").write_bytes(b"drift")
        with self.assertRaises(ValueError): selection.select(self.root)

    def test_invalid_selector_never_falls_back(self):
        for field, value in (("active", False), ("representation", "packed"), ("schemaVersion", True),
                             ("serverMapRelativePath", "../outside")):
            old = self.primary[field]; self.primary[field] = value
            self.write(selection.PRIMARY, self.primary)
            with self.assertRaises((ValueError, OSError)): selection.select(self.root)
            self.primary[field] = old

    def test_symlink_and_duplicate_controls_refuse(self):
        client = self.root / self.primary["clientMapRelativePath"] / "terrain"
        client.unlink(); client.symlink_to(self.root / self.primary["serverMapRelativePath"] / "terrain")
        with self.assertRaises(ValueError): selection.select(self.root)
        path = self.root / selection.PRIMARY
        path.write_text('{"active":true,"active":false}')
        with self.assertRaises(ValueError): selection.select(self.root)

    def test_selected_generation_copies_exact_new_bytes(self):
        self.install("edited")
        self.stub_parser()
        workspace = Path(self.temp.name) / "promotion"
        # Generation records exact source identity; approval requires clean Git.
        subprocess.run(["git", "init", "-q", str(self.root)], check=True)
        subprocess.run(["git", "-C", str(self.root), "add", "."], check=True)
        subprocess.run(["git", "-C", str(self.root), "-c", "user.name=Test", "-c", "user.email=test@example.invalid",
                        "commit", "-qm", "fixture"], check=True)
        self.shell('layered_world_generate_package "'+str(self.root)+'" "'+str(workspace)+'"')
        report = json.loads((workspace / "generation-report.json").read_text())
        self.assertEqual("edited", report["packageVersion"])
        self.assertEqual(selection.select(self.root)["packageFingerprintSha256"], selection.inventory(workspace / "package")[1])
        self.shell('layered_world_require_promotion_approved "'+str(workspace / "generation-report.json")+'"')
        (self.root / selection.PROFILE).write_text("{}")
        self.shell('layered_world_require_promotion_approved "'+str(workspace / "generation-report.json")+'"', False)

    def test_parser_time_drift_and_failures_refuse(self):
        self.stub_parser('printf drift >> "'+str(self.root / self.primary["serverMapRelativePath"] / "terrain")+'"\n')
        self.shell('layered_world_selected_pair "'+str(self.root)+'" "'+self.temp.name+'/validation"', False)

    def test_missing_hosted_pair_does_not_use_external_pin(self):
        (self.root / selection.PRIMARY).unlink()
        self.shell('layered_world_require_live_package "'+str(self.root)+'" "'+self.temp.name+'/external"', False)

    def test_installed_marker_requires_exact_selection_no_jvm(self):
        observed = selection.select(self.root)
        command = 'layered_world_installed_marker_matches "'+str(self.root)+'" "'+observed['serverPackage']+'" "'+observed['manifestSha256']+'" "'+observed['selectionSha256']+'"'
        self.shell(command)
        self.shell(command.replace(observed['manifestSha256'], '0'*64), False)
        self.install("later")
        self.shell(command, False)

    def test_hosted_inherited_pin_refuses_before_parser(self):
        self.shell('export OPENRSC_LAYERED_NATIVE_TERRAIN_PACKAGE_PATH=/old/package; '
                   'layered_world_enable_hosted_installed_profile "'+str(self.root)+'" unused', False)

    def test_hosted_clean_guard_and_explicit_rollback_wiring(self):
        source = (ROOT / "scripts/run-hosted-release.sh").read_text()
        self.assertIn("myworld_require_safe_hosted_launch", source)
        self.assertIn("--pinned-rollback", source)
        self.assertIn("layered_world_enable_hosted_installed_profile", source)
        self.assertIn("refuses explicit", (ROOT / "scripts/lib/layered-world-package.sh").read_text())
        common = (ROOT / "scripts/lib/myworld-common.sh").read_text()
        self.assertIn('[[ -z "$dirty_status" ]]', common)
        self.assertIn('[[ "$head_commit" == "$main_commit" ]]', common)


if __name__ == "__main__": unittest.main()
