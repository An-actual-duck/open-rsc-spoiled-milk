#!/usr/bin/env python3
"""Prepare an offline callback fixture against the unchanged installed map."""
import hashlib
import json
from pathlib import Path
import re
import shutil
import sys

from installed_world_package import ROOT, package_root


target = Path(sys.argv[1]).resolve()
if target.exists():
    raise SystemExit("Use a fresh fixture directory; prior evidence is preserved")
source = package_root("server")
manifest_path = source / "manifest.json"
digest = hashlib.sha256(manifest_path.read_bytes()).hexdigest()
target.mkdir(parents=True)
for name in ("conf", "database"):
    shutil.copytree(ROOT / "server" / name, target / name)
for name in ("badwords.txt", "goodwords.txt", "globalrules.txt", "alertwords.txt"):
    shutil.copy2(ROOT / "server" / name, target / name)
(target / "connections.conf").write_text("db_type: sqlite\n")
configuration = (ROOT / "server/myworld.conf").read_text()
for key, value in {
    "layered_native_terrain_package_path": str(source),
    "layered_native_terrain_manifest_sha256": digest,
    "layered_native_world_runtime_profile": "native-replacement",
    "want_discord_bot": "false",
}.items():
    configuration, count = re.subn(
        r"(?m)^([ \t]*)" + key + r":.*$",
        lambda match: match[1] + key + ": " + value,
        configuration,
    )
    if not count:
        configuration += "\n" + key + ": " + value + "\n"
(target / "myworld.conf").write_text(configuration)
(target / "source-world.json").write_text(json.dumps({
    "sourcePackage": str(source), "manifestSha256": digest,
    "scope": "Read-only installed terrain/placements; no listeners or accounts",
}, indent=2) + "\n")
if list(target.rglob("*.db")):
    raise AssertionError("Fixture must not contain a player database")
print(target)
