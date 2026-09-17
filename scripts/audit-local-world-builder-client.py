#!/usr/bin/env python3
"""Read-only gate for this one-off installed World Builder client experiment.

Does not launch Java applications, write packages, or inspect project/user data.
Matching metadata is necessary but never proves runtime compatibility.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import zipfile


CONSTANT_CLASSES = ("orsc.Config", "orsc.AdaptiveWorldBuilderClientSession")
CONSTANT = re.compile(r'public static final (?:java\.lang\.String|int) (\w+) = (.*);')
REQUIRED = (
    "CLIENT_VERSION", "SESSION_SCHEMA", "CAPABILITY_ID", "SERVER_BUILD_ID",
    "CLIENT_BUILD_ID", "LOADER_ID", "AUTHORING_ID", "DEFINITION_CONTRACT_ID",
    "ASSET_CONTRACT_ID", "PROTOCOL_ID", "EFFECTIVE_COMPOSITION_ID",
    "PACKAGE_SCHEMA_ID", "COORDINATE_MODEL", "PROFILE_ID",
)


def inspect_client(path):
    path = path.resolve(strict=True)
    with zipfile.ZipFile(path) as archive:
        entries = set(archive.namelist())
    result = subprocess.run(
        ["javap", "-constants", "-classpath", str(path), *CONSTANT_CLASSES],
        text=True, capture_output=True, check=True, timeout=30,
    )
    constants = {}
    for line in result.stdout.splitlines():
        match = CONSTANT.search(line)
        if match:
            constants[match[1]] = match[2].strip('"')
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return {"path": str(path), "sha256": digest.hexdigest(),
            "constants": constants, "entries": entries}


def compare(installed, candidate):
    problems = []
    old, new = installed["constants"], candidate["constants"]
    for key in REQUIRED:
        if key not in old or key not in new or old[key] != new[key]:
            problems.append({"check": key, "installed": old.get(key),
                             "candidate": new.get(key)})
    encodings = lambda values: {value for key, value in values.items()
                                if key.endswith("PLACEMENT_ENCODING")}
    for missing in sorted(encodings(old) - encodings(new)):
        problems.append({"check": "placement-encoding-missing", "value": missing})
    entry = "orsc/CurrentBaseInstalledClient.class"
    if entry in installed["entries"] and entry not in candidate["entries"]:
        problems.append({"check": "managed-entrypoint-missing", "value": entry,
                         "scope": "managed current-base launch; not adaptive editor entrypoint"})
    return problems


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("installation", type=Path)
    parser.add_argument("candidate_jar", type=Path)
    args = parser.parse_args()
    root = args.installation.resolve(strict=True)
    identity = json.loads((root / "RELEASE-IDENTITY.json").read_text())
    if identity.get("productId") != "rsc-world-editor-v2":
        parser.error("not an identified World Builder 2 installation")
    installed = inspect_client(root / "builder-runtime/Client_Base/Open_RSC_Client.jar")
    candidate = inspect_client(args.candidate_jar)
    findings = compare(installed, candidate)
    for record in (installed, candidate):
        del record["entries"]
    print(json.dumps({
        "status": "BLOCKED_DIRECT_REPLACEMENT" if findings else "REQUIRES_RUNTIME_VALIDATION",
        "installed": installed, "candidate": candidate, "findings": findings,
        "notes": ["No application launched or files modified.",
                  "No package signatures/manifests or project bindings bypassed.",
                  "Matching constants alone do not prove packet, input, rendering, or save compatibility.",
                  "Packaged automatic updater verifies owned files; local replacement needs a reviewed update/rollback plan."],
    }, indent=2))
    return 2 if findings else 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (OSError, ValueError, zipfile.BadZipFile, subprocess.SubprocessError) as error:
        print("Audit failed closed: " + str(error), file=sys.stderr)
        sys.exit(1)
