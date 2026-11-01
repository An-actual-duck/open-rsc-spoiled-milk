#!/usr/bin/env python3
"""Offline execution of the shipped updater; downloads are fixture-owned only."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]


class PlayerUpdaterTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="player-updater-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.install = self.root / "installed"
        self.payload = self.install / "game-files"
        self.payload.mkdir(parents=True)
        (self.payload / "VERSION.txt").write_text("old payload\n")
        (self.payload / "preferences.ini").write_text("keep my settings\n")
        self.bin = self.root / "bin"
        self.bin.mkdir()
        curl = self.bin / "curl"
        curl.write_text('''#!/usr/bin/env python3
import json,os,sys,shutil
from pathlib import Path
r=Path(os.environ['UPDATER_FIXTURE']);args=sys.argv[1:];url=next(a for a in args if a.startswith('https://'))
with (r/'requests.log').open('a')as f:f.write(url+'\\n')
if url.endswith('/releases'):
 if (r/'api-fails').exists():sys.exit(22)
 sys.stdout.write((r/'releases.json').read_text());sys.exit(0)
source=r/url.rsplit('/',1)[-1]
if not source.is_file():sys.exit(22)
shutil.copyfile(source,Path(args[args.index('-o')+1]))
''')
        curl.chmod(0o755)
        self.env = {**os.environ, "PATH": str(self.bin) + os.pathsep + os.environ["PATH"],
                    "UPDATER_FIXTURE": str(self.root)}

    def releases(self, *versions):
        rows = []
        for version in versions:
            name = f"spoiled-milk-{version}-java.zip"
            with zipfile.ZipFile(self.root / name, "w") as archive:
                base = f"spoiled-milk-{version}-java"
                archive.writestr(base + "/game-files/VERSION.txt", version + "\n")
                archive.writestr(base + "/README.txt", "new release\n")
            rows.append({"tag_name": version, "assets": [{"name": name, "browser_download_url": "https://fixture.invalid/" + name}]})
        (self.root / "releases.json").write_text(json.dumps(rows, indent=2))

    def run_updater(self, current):
        script = self.payload / "update-spoiled-milk.sh"
        script.write_text((ROOT / "release/player/update-spoiled-milk.sh").read_text().replace("@VERSION@", current))
        return subprocess.run(["sh", str(script)], env=self.env, text=True, capture_output=True, timeout=15)

    def assert_no_download(self, result):
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual(["https://api.github.com/repos/An-actual-duck/open-rsc-spoiled-milk/releases"],
                         (self.root / "requests.log").read_text().splitlines())
        self.assertEqual("old payload\n", (self.payload / "VERSION.txt").read_text())

    def test_private_candidate_does_not_downgrade_to_public_release(self):
        self.releases("v0.2.89")
        self.assert_no_download(self.run_updater("v0.3.0-alpha.1"))

    def test_stable_does_not_downgrade_to_same_version_alpha(self):
        self.releases("v0.3.0-alpha.2")
        self.assert_no_download(self.run_updater("v0.3.0"))

    def test_same_version_does_not_download(self):
        self.releases("v0.2.89")
        self.assert_no_download(self.run_updater("v0.2.89"))

    def test_stable_beats_alpha_and_successful_update_preserves_preferences(self):
        self.releases("v0.3.0", "v0.3.0-alpha.2", "v0.2.89")
        result = self.run_updater("v0.2.89")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual("v0.3.0\n", (self.payload / "VERSION.txt").read_text())
        self.assertEqual("keep my settings\n", (self.payload / "preferences.ini").read_text())

    def test_newer_alpha_can_update_older_alpha(self):
        self.releases("v0.3.0-alpha.2", "v0.2.89")
        result = self.run_updater("v0.3.0-alpha.1")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual("v0.3.0-alpha.2\n", (self.payload / "VERSION.txt").read_text())

    def test_alpha_numbers_sort_numerically(self):
        self.releases("v0.3.0-alpha.9", "v0.3.0-alpha.10", "v0.2.99")
        result = self.run_updater("v0.3.0-alpha.2")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual("v0.3.0-alpha.10\n", (self.payload / "VERSION.txt").read_text())

    def test_newer_core_version_beats_older_stable(self):
        self.releases("v0.2.999", "v0.3.0-alpha.1")
        result = self.run_updater("v0.2.89")
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertEqual("v0.3.0-alpha.1\n", (self.payload / "VERSION.txt").read_text())

    def test_failed_update_check_keeps_installed_payload(self):
        self.releases("v0.3.0")
        (self.root / "api-fails").touch()
        result = self.run_updater("v0.2.89")
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("old payload\n", (self.payload / "VERSION.txt").read_text())
        self.assertEqual("keep my settings\n", (self.payload / "preferences.ini").read_text())


@unittest.skipUnless(shutil.which("pwsh"), "PowerShell is not installed; native Windows execution remains unverified")
class PowerShellPlayerUpdaterTest(PlayerUpdaterTest):
    """Run the identical end-to-end cases when PowerShell is available."""
    def run_updater(self, current):
        script = self.payload / "update-spoiled-milk.ps1"
        script.write_text((ROOT / "release/player/update-spoiled-milk.ps1").read_text()
                          .replace("@VERSION@", current).replace("@PACKAGE_KIND@", "java"))
        wrapper = self.root / "fixture.ps1"
        wrapper.write_text(r'''$ErrorActionPreference = "Stop"
function Invoke-RestMethod {
    param($Uri, $Headers)
    Add-Content -Path (Join-Path $env:UPDATER_FIXTURE "requests.log") -Value $Uri
    if (Test-Path (Join-Path $env:UPDATER_FIXTURE "api-fails")) { throw "Fixture API unavailable" }
    return Get-Content -Raw (Join-Path $env:UPDATER_FIXTURE "releases.json") | ConvertFrom-Json
}
function Invoke-WebRequest {
    param($Uri, $OutFile, $Headers)
    Add-Content -Path (Join-Path $env:UPDATER_FIXTURE "requests.log") -Value $Uri
    Copy-Item -LiteralPath (Join-Path $env:UPDATER_FIXTURE ($Uri.Split('/')[-1])) -Destination $OutFile
}
& $env:UPDATER_SCRIPT
''')
        return subprocess.run([shutil.which("pwsh"), "-NoProfile", "-File", str(wrapper)],
                              env={**self.env, "UPDATER_SCRIPT": str(script)},
                              text=True, capture_output=True, timeout=30)


if __name__ == "__main__":
    unittest.main()
