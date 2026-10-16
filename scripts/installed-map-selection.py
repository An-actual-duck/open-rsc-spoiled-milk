#!/usr/bin/env python3
"""Bounded release input selection, NOT runtime/material/placement permission.

The maintained package-check and server preflight remain separate mandatory gates.
No target code, configuration interpreter, credentials or databases are loaded.
"""
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys

MAX_FILE = 128 * 1024 * 1024
MAX_TOTAL = 2 * 1024 * 1024 * 1024
PRIMARY = "server/world-builder-configs/primary.json"
PROFILE = "Client_Base/world-builder-configs/installed-client.json"


def refuse(message):
    raise ValueError(message)


def unique(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            refuse("Duplicate JSON member: " + key)
        result[key] = value
    return result


def contained(root, relative, directory=False):
    parts = relative.split("/")
    if not relative or any(p in ("", ".", "..") for p in parts) or "\\" in relative:
        refuse("Invalid selected relative path")
    path = root
    for index, part in enumerate(parts):
        path = path / part
        kind = path.lstat().st_mode
        if stat.S_ISLNK(kind):
            refuse("Symlink in selected map input: " + relative)
        want_directory = index < len(parts) - 1 or directory
        if not (stat.S_ISDIR(kind) if want_directory else stat.S_ISREG(kind)):
            refuse("Wrong selected input kind: " + relative)
    if root not in path.resolve().parents:
        refuse("Selected input escapes root")
    return path


def read(path, limit):
    with path.open("rb") as stream:
        data = stream.read(limit + 1)
    if len(data) > limit:
        refuse("Selected input exceeds byte budget")
    return data


def document(path, keys=None):
    data = read(path, 1024 * 1024)
    value = json.loads(data.decode("utf-8"), object_pairs_hook=unique,
                       parse_constant=lambda value: refuse("Nonfinite JSON"))
    if not isinstance(value, dict) or (keys is not None and set(value) != set(keys)):
        refuse("Unexpected selected metadata schema: " + str(path))
    return value, hashlib.sha256(data).hexdigest()


def inventory(root):
    entries = []
    total = 0
    nodes = 0
    for parent, directories, files in os.walk(root, followlinks=False):
        nodes += len(directories) + len(files)
        if nodes > 65536:
            refuse("Selected package exceeds path budget")
        for name in directories:
            if not stat.S_ISDIR((Path(parent) / name).lstat().st_mode):
                refuse("Non-directory/symlink in map package")
        for name in files:
            path = Path(parent) / name
            if not stat.S_ISREG(path.lstat().st_mode):
                refuse("Non-file/symlink in map package")
            payload = read(path, MAX_FILE)
            total += len(payload)
            entries.append((path.relative_to(root).as_posix(), len(payload), hashlib.sha256(payload).hexdigest()))
            if len(entries) > 65536 or total > MAX_TOTAL:
                refuse("Selected package exceeds inventory budget")
    entries.sort()
    digest = hashlib.sha256()
    for record in entries:
        for field in record:
            digest.update(str(field).encode("utf-8") + b"\0")
    return entries, digest.hexdigest()


def select(root):
    root = Path(root).absolute()
    # Reject redirected roots, not just symlinks inside the relative input paths.
    if root != root.resolve():
        refuse("Selected root has a symlink/redirect")
    primary, primary_hash = document(contained(root, PRIMARY), (
        "schemaVersion", "manifestType", "configurationId", "active", "representation",
        "serverMapRelativePath", "clientMapRelativePath", "serverRuntimeRelativePath",
        "clientRuntimeRelativePath", "serverDefinitionCatalogRelativePath",
        "clientDefinitionCatalogRelativePath", "assets", "placements"))
    if type(primary["schemaVersion"]) is not int or primary["schemaVersion"] != 1 or primary["active"] is not True \
            or primary["configurationId"] != "primary" or primary["manifestType"] != "world-builder-map-configuration" \
            or primary["representation"] != "layered":
        refuse("Hosted/release selection requires an active layered primary")
    if not isinstance(primary["assets"], list) or not isinstance(primary["placements"], list):
        refuse("Invalid primary resource/placement controls")
    server_relative = primary["serverMapRelativePath"]
    match = re.fullmatch(r"server/world-builder/packages/([0-9a-f]{64})/package", server_relative)
    if not match:
        refuse("Invalid server package address")
    fingerprint = match.group(1)
    client_relative = "Client_Base/world-builder/packages/" + fingerprint + "/package"
    if primary["clientMapRelativePath"] != client_relative:
        refuse("Primary does not select matching server/client roles")
    server = contained(root, server_relative, True)
    client = contained(root, client_relative, True)
    server_entries, actual = inventory(server)
    client_entries, client_actual = inventory(client)
    if server_entries != client_entries or actual != fingerprint or client_actual != fingerprint:
        refuse("Selected package bytes/fingerprint differ from paired address")
    manifest, manifest_hash = document(contained(root, server_relative + "/manifest.json"))
    profile, profile_hash = document(contained(root, PROFILE), (
        "schemaVersion", "manifestType", "active", "packageId", "packageVersion",
        "manifestSha256", "packageFingerprintSha256", "packageRelativePath"))
    if type(profile["schemaVersion"]) is not int or profile["schemaVersion"] != 1 or profile["active"] is not True \
            or profile["manifestType"] != "world-builder-installed-client-profile" \
            or profile["packageRelativePath"] != client_relative[len("Client_Base/"):] \
            or profile["packageFingerprintSha256"] != fingerprint \
            or profile["manifestSha256"] != manifest_hash \
            or profile["packageId"] != manifest.get("packageId") \
            or profile["packageVersion"] != manifest.get("packageVersion"):
        refuse("Installed client profile disagrees with selected pair")
    result = dict(schemaVersion=1, selection="installed-primary", root=str(root),
                serverPackage=str(server), clientPackage=str(client),
                packageFingerprintSha256=fingerprint, manifestSha256=manifest_hash,
                packageId=profile["packageId"], packageVersion=profile["packageVersion"],
                primarySha256=primary_hash, clientProfileSha256=profile_hash,
                fileCount=len(server_entries))
    result["selectionSha256"] = hashlib.sha256(json.dumps(result, sort_keys=True).encode()).hexdigest()
    return result


if __name__ == "__main__":
    try:
        if len(sys.argv) != 2:
            refuse("Usage: installed-map-selection.py ROOT")
        print(json.dumps(select(sys.argv[1]), sort_keys=True))
    except (OSError, ValueError, TypeError, KeyError) as failure:
        sys.exit("FAIL: installed map selection: " + str(failure))
