#!/usr/bin/env python3
"""Combine an existing, matching pair of player packages without rebuilding."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import stat
import zipfile

NAME = "spoiled-milk-client.zip"
ROOT = "Spoiled Milk/"
PAYLOAD = "game-files/"


def digest(data):
    return hashlib.sha256(data).hexdigest()


def read_package(path, prefix):
    files = {}
    modes = {}
    with zipfile.ZipFile(path) as archive:
        for entry in archive.infolist():
            name = entry.filename
            if (
                not name.startswith(prefix)
                or "\\" in name
                or any(part in (".", "..") for part in name.split("/"))
                or stat.S_ISLNK(entry.external_attr >> 16)
            ):
                raise ValueError(f"Unsafe or unexpected archive member: {name}")
            if entry.is_dir():
                continue
            relative = name[len(prefix):]
            if not relative or relative in files:
                raise ValueError(f"Duplicate or empty archive member: {name}")
            files[relative] = archive.read(entry)
            modes[relative] = stat.S_IMODE(entry.external_attr >> 16) or 0o644
    return files, modes


def build(version, directory, templates, expected_commit):
    if not re.fullmatch(r"v\d+\.\d+\.\d+(?:-alpha\.\d+)?", version):
        raise ValueError("Invalid release version")
    java_path = directory / f"spoiled-milk-{version}-java.zip"
    windows_path = directory / f"spoiled-milk-{version}-windows-x64.zip"
    java, java_modes = read_package(java_path, f"spoiled-milk-{version}-java/")
    windows, modes = read_package(windows_path, f"spoiled-milk-{version}-windows-x64/")
    for key in ("VERSION.txt", "SOURCE-COMMIT.txt", "Spoiled_Milk_Client.jar"):
        name = PAYLOAD + key
        if java[name] != windows[name]:
            raise ValueError(f"Player packages disagree on {key}")
    if java[PAYLOAD + "VERSION.txt"].decode().strip() != version:
        raise ValueError("Player packages do not match the requested version")
    source_commit = java[PAYLOAD + "SOURCE-COMMIT.txt"].decode().strip()
    if not re.fullmatch(r"[0-9a-f]{40}", source_commit):
        raise ValueError("Player packages have an invalid source commit")
    if expected_commit and source_commit != expected_commit:
        raise ValueError("Player packages do not match the expected release commit")
    if PAYLOAD + "runtime/bin/java.exe" not in windows:
        raise ValueError("Windows package is missing its bundled Java runtime")
    shared = {name for name in java if name.startswith(PAYLOAD)}
    shared_windows = {
        name for name in windows
        if name.startswith(PAYLOAD) and not name.startswith(PAYLOAD + "runtime/")
    }
    if shared - shared_windows != {PAYLOAD + "update-spoiled-milk.sh"}:
        raise ValueError("Unexpected Java-only player payload files")
    if shared_windows - shared:
        raise ValueError("Unexpected Windows-only player payload files")
    for name in shared & shared_windows:
        left, right = java[name], windows[name]
        if name == PAYLOAD + "update-spoiled-milk.ps1":
            left = left.replace(b'$PackageKind = "java"', b'$PackageKind = "platform"')
            right = right.replace(b'$PackageKind = "windows-x64"', b'$PackageKind = "platform"')
        if left != right:
            raise ValueError(f"Player payloads differ unexpectedly: {name}")

    # Each OS keeps its established update channel. Older installations can
    # continue using the two versioned archives while the website uses one ZIP.
    files = {name: data for name, data in windows.items() if name.startswith(PAYLOAD)}
    files[PAYLOAD + "update-spoiled-milk.sh"] = java[PAYLOAD + "update-spoiled-milk.sh"]
    files["play-spoiled-milk.sh"] = java["play-spoiled-milk.sh"]
    modes["play-spoiled-milk.sh"] = java_modes["play-spoiled-milk.sh"]
    launcher = windows["Play Spoiled Milk.cmd"].decode()
    # Preserve the released JVM options, including when consolidating an older
    # release. CMD must keep the error exit inside the conditional block.
    newline = "\r\n" if "\r\n" in launcher else "\n"
    launcher = launcher.replace(
        "if errorlevel 1 pause & exit /b 1",
        newline.join(("if errorlevel 1 (", "  pause", "  exit /b 1", ")")),
    )
    files["Play Spoiled Milk.cmd"] = launcher.encode()
    files["Play Spoiled Milk.command"] = (templates / "Play Spoiled Milk.command").read_bytes()
    files["README.txt"] = (
        (templates / "UNIVERSAL-README.txt").read_text().replace("@VERSION@", version).encode()
    )
    modes["Play Spoiled Milk.command"] = 0o755
    modes["play-spoiled-milk.sh"] = 0o755
    modes[PAYLOAD + "update-spoiled-milk.sh"] = 0o755
    target = directory / NAME
    # Refuse to replace a previously generated download without review.
    with zipfile.ZipFile(target, "x", zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(files.items()):
            entry = zipfile.ZipInfo(ROOT + name, date_time=(2020, 1, 1, 0, 0, 0))
            entry.create_system = 3
            entry.external_attr = (stat.S_IFREG | modes.get(name, 0o644)) << 16
            entry.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(entry, data)
    report = {
        "version": version,
        "gameSourceCommit": source_commit,
        "clientSha256": digest(files[PAYLOAD + "Spoiled_Milk_Client.jar"]),
        "inputs": {p.name: digest(p.read_bytes()) for p in (java_path, windows_path)},
        "output": NAME,
        "sha256": digest(target.read_bytes()),
        "bytes": target.stat().st_size,
        "rootFolder": ROOT.rstrip("/"),
        "launchers": {
            "Windows": "Play Spoiled Milk.cmd",
            "Linux": "play-spoiled-milk.sh",
            "macOS": "Play Spoiled Milk.command",
        },
        "gamePayloadUnchanged": True,
    }
    (directory / "universal-player-package.json").write_text(json.dumps(report, indent=2) + "\n")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True)
    parser.add_argument("--directory", type=Path, required=True)
    parser.add_argument("--templates", type=Path, required=True)
    parser.add_argument("--expected-source-commit")
    args = parser.parse_args()
    try:
        report = build(args.version, args.directory, args.templates, args.expected_source_commit)
    except (ValueError, KeyError, OSError, zipfile.BadZipFile) as exc:
        parser.exit(1, f"FAIL: {exc}\n")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
