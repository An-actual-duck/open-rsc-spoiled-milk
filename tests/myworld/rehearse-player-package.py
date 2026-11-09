#!/usr/bin/env python3
"""Rehearse actual player archives and the unchanged POSIX updater offline.

Only disposable extracted installations are executed. A PATH-local curl bridge
maps the updater's two exact remote URLs to an ephemeral loopback HTTP server;
unrecognized URLs are refused. Java is an argv recorder, not a game process.
Native client/window/GPU validation is a separate check.
"""
import argparse
import hashlib
import http.server
import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import threading
import zipfile

ROOT = Path(__file__).resolve().parents[2]
REPO = "An-actual-duck/open-rsc-spoiled-milk"
NATIVES = (
    "linux/x64/org/lwjgl/liblwjgl.so",
    "linux/x64/org/lwjgl/glfw/libglfw.so",
    "linux/x64/org/lwjgl/opengl/liblwjgl_opengl.so",
    "windows/x64/org/lwjgl/lwjgl.dll",
    "windows/x64/org/lwjgl/glfw/glfw.dll",
    "windows/x64/org/lwjgl/opengl/lwjgl_opengl.dll",
    "macos/x64/org/lwjgl/liblwjgl.dylib",
    "macos/x64/org/lwjgl/glfw/libglfw.dylib",
    "macos/x64/org/lwjgl/opengl/liblwjgl_opengl.dylib",
    "macos/arm64/org/lwjgl/liblwjgl.dylib",
    "macos/arm64/org/lwjgl/glfw/libglfw.dylib",
    "macos/arm64/org/lwjgl/opengl/liblwjgl_opengl.dylib",
)


def require(ok, message):
    if not ok:
        raise AssertionError(message)


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def members(path, prefix):
    """Validate before extraction; never follow archive symlinks or traversal."""
    with zipfile.ZipFile(path) as archive:
        seen = set()
        for entry in archive.infolist():
            name = entry.filename
            require(name.startswith(prefix) and "\\" not in name
                    and not any(p in (".", "..") for p in name.split("/"))
                    and not stat.S_ISLNK(entry.external_attr >> 16),
                    "Unsafe archive member: " + name)
            require(name not in seen, "Duplicate archive member: " + name)
            seen.add(name)
        require(archive.testzip() is None, "Archive CRC failure")
        return seen


def extract(path, prefix, destination):
    members(path, prefix)
    destination.mkdir()
    with zipfile.ZipFile(path) as archive:
        archive.extractall(destination)
        for entry in archive.infolist():
            if not entry.is_dir():
                mode = stat.S_IMODE(entry.external_attr >> 16)
                if mode:
                    (destination / entry.filename).chmod(mode)
    return destination / prefix.rstrip("/")


def tree(path):
    return {p.relative_to(path).as_posix(): digest(p)
            for p in sorted(path.rglob("*")) if p.is_file()
            and "updates" not in p.relative_to(path).parts}


def version_key(text):
    match = re.fullmatch(r"v(\d+)\.(\d+)\.(\d+)(?:-alpha\.(\d+))?", text)
    require(match is not None, "Invalid version: " + text)
    values = match.groups()
    return (*map(int, values[:3]), values[3] is None, int(values[3] or 0))


def metadata(install, args, client_hash):
    payload = install / "game-files"
    require((payload / "SOURCE-COMMIT.txt").read_text().strip() == args.expected_source,
            "Candidate source differs from reviewed commit")
    require((payload / "Cache/ip.txt").read_text().strip() == args.host
            and (payload / "Cache/port.txt").read_text().strip() == str(args.port),
            "Candidate endpoint mismatch")
    jar = payload / "Spoiled_Milk_Client.jar"
    require(digest(jar) == client_hash, "Candidate JAR hash mismatch")
    with zipfile.ZipFile(jar) as archive:
        names = set(archive.namelist())
        require("spoiled-milk-release-build.marker" in names, "Missing release-build marker")
        require(all(n in names for n in NATIVES), "Missing packaged platform native")
        require("Main-Class: orsc.OpenRSC" in archive.read("META-INF/MANIFEST.MF").decode(),
                "Unexpected production entry point")
    profile = json.loads((payload / "world-builder-configs/installed-client.json").read_text())
    relative = Path(profile["packageRelativePath"])
    require(not relative.is_absolute() and ".." not in relative.parts, "Unsafe package profile")
    package = payload / relative
    require(digest(package / "manifest.json") == profile["manifestSha256"], "Map manifest mismatch")
    hashes = []
    for p in sorted(package.rglob("*")):
        require(not p.is_symlink(), "Symlink in map package")
        if p.is_file():
            hashes.append((p.relative_to(package).as_posix(), p.stat().st_size, digest(p)))
    raw = "".join(f"{name}\0{size}\0{sha}\0" for name, size, sha in hashes).encode()
    require(hashlib.sha256(raw).hexdigest() == profile["packageFingerprintSha256"],
            "Installed map fingerprint mismatch")
    return profile


class Feed(http.server.BaseHTTPRequestHandler):
    mode = "candidate"
    candidate = None
    previous_version = None
    version = None
    requests = []

    def log_message(self, *_):
        pass

    def do_GET(self):
        type(self).requests.append({"path": self.path, "mode": self.mode})
        if self.path == "/api":
            if self.mode == "api-failure":
                self.send_error(503)
                return
            version = self.previous_version if self.mode == "older" else self.version
            assets = [] if self.mode == "missing-asset" else [{
                "name": f"spoiled-milk-{version}-java.zip",
                "browser_download_url": f"https://github.com/{REPO}/releases/download/"
                                        f"{version}/spoiled-milk-{version}-java.zip"}]
            body = json.dumps([{"tag_name": version, "assets": assets}], indent=2).encode()
        elif self.path == "/archive":
            if self.mode == "download-failure":
                self.send_error(404)
                return
            body = b"not a zip archive\n" if self.mode == "bad-archive" else self.candidate.read_bytes()
        else:
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def write_bridges(run, server_port, version):
    bindir = run / "fixture-bin"
    bindir.mkdir()
    curl = shutil.which("curl")
    require(curl is not None, "curl required")
    mapping = {f"https://api.github.com/repos/{REPO}/releases": "/api",
               f"https://github.com/{REPO}/releases/download/{version}/"
               f"spoiled-milk-{version}-java.zip": "/archive"}
    (bindir / "curl").write_text(
        "#!/usr/bin/env python3\nimport subprocess,sys\n"
        f"mapping={mapping!r}\nargs=sys.argv[1:]\n"
        "urls=[a for a in args if a.startswith(('http://','https://'))]\n"
        "if len(urls)!=1 or urls[0] not in mapping:sys.exit('Refused non-fixture curl URL')\n"
        f"args[args.index(urls[0])]='http://127.0.0.1:{server_port}'+mapping[urls[0]]\n"
        f"sys.exit(subprocess.run([{curl!r},*args]).returncode)\n")
    (bindir / "curl").chmod(0o700)
    (bindir / "java").write_text(
        "#!/usr/bin/env python3\nimport json,os,pathlib,sys\n"
        "pathlib.Path(os.environ['REHEARSAL_JAVA_RECEIPT']).write_text("
        "json.dumps({'cwd':os.getcwd(),'argv':sys.argv[1:]},indent=2)+'\\n')\n")
    (bindir / "java").chmod(0o700)
    return bindir


def launch(install, name, run, env, launcher="play-spoiled-milk.sh"):
    receipt = run / (name + "-java-argv.json")
    command = ["sh", str(install / launcher)]
    result = subprocess.run(command, cwd=run,
                            env={**env, "REHEARSAL_JAVA_RECEIPT": str(receipt)},
                            text=True, capture_output=True, timeout=120)
    (run / (name + ".log")).write_text(result.stdout + result.stderr)
    require(result.returncode == 0 and receipt.exists(), "POSIX launcher failed: " + name)
    actual = json.loads(receipt.read_text())
    require(Path(actual["cwd"]) == install / "game-files", "Launcher cwd mismatch")
    require(actual["argv"][-2:] == ["-jar", "Spoiled_Milk_Client.jar"], "Launcher JAR mismatch")
    require("-Dspoiledmilk.releaseBuild=true" in actual["argv"], "Release flag absent")
    require(not any(any(key in a.lower() for key in ("rendererdiagnostic", "renderertelemetry",
                    "hiddendiagnostic", "remasterlightazimuth", "remasterlightelevation", "shadowquality"))
                    for a in actual["argv"]), "Testing or forced-quality flags in player launcher")
    return actual


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--candidate-directory", type=Path, required=True)
    parser.add_argument("--previous-java-archive", type=Path, required=True)
    parser.add_argument("--expected-source", required=True)
    parser.add_argument("--host", required=True)
    parser.add_argument("--port", type=int, required=True)
    parser.add_argument("--name", required=True)
    args = parser.parse_args()
    require(re.fullmatch(r"[a-zA-Z0-9_-]+", args.name), "Simple unique run name required")
    require(re.fullmatch(r"[0-9a-f]{40}", args.expected_source), "Exact source commit required")
    root = Path(subprocess.check_output(["python3", str(ROOT / "scripts/workspace-layout.py"),
                "path", "tests", "renderer-player-package-validation", "--mkdir"], text=True).strip())
    run = root / args.name
    run.mkdir(mode=0o700)
    summary = {"completed": False, "publicServerModified": False,
               "nativeWindowsExecution": False, "nativeMacOSExecution": False,
               "javaExecution": "argv recorder only; native client startup is a separate check",
               "limitations": ["POSIX updater lacks transactional rollback after copying begins; "
                               "failure checks below concern pre-install refusals only."]}
    try:
        report = json.loads((args.candidate_directory / "universal-player-package.json").read_text())
        version = report["version"]
        require(report["gameSourceCommit"] == args.expected_source, "Package report source mismatch")
        paths = [args.candidate_directory / name for name in
                 (f"spoiled-milk-{version}-java.zip", f"spoiled-milk-{version}-windows-x64.zip",
                  "spoiled-milk-client.zip")]
        before = {str(p.resolve()): digest(p) for p in [*paths, args.previous_java_archive]}
        sums = {}
        for line in (args.candidate_directory / "SHA256SUMS.txt").read_text().splitlines():
            sha, name = line.split(maxsplit=1)
            sums[name.lstrip("*")] = sha
        for p in paths:
            require(sums[p.name] == digest(p), "Candidate archive checksum mismatch: " + p.name)
        require(report["sha256"] == digest(paths[2]), "Universal report digest mismatch")
        for kind, p in zip(("java", "windows-x64"), paths):
            prefix = f"spoiled-milk-{version}-{kind}/"
            names = members(p, prefix)
            require(report["inputs"][p.name] == digest(p), "Consolidation input digest mismatch")
            require(not any(Path(name).name in {"credentials.txt", "uid.dat", "clientSettings.conf"}
                            or name.endswith(".db") for name in names), "Local state in platform archive")
            with zipfile.ZipFile(p) as archive:
                payload = prefix + "game-files/"
                require(hashlib.sha256(archive.read(payload + "Spoiled_Milk_Client.jar")).hexdigest()
                        == report["clientSha256"], "Platform JAR differs from universal package")
                require(archive.read(payload + "SOURCE-COMMIT.txt").decode().strip() == args.expected_source,
                        "Platform source metadata mismatch")
                require(archive.read(payload + "Cache/ip.txt").decode().strip() == args.host
                        and archive.read(payload + "Cache/port.txt").decode().strip() == str(args.port),
                        "Platform endpoint mismatch")
        clean = extract(paths[2], "Spoiled Milk/", run / "clean-install-with-spaces")
        profile = metadata(clean, args, report["clientSha256"])
        forbidden = {"credentials.txt", "uid.dat", "clientSettings.conf", "preferences.ini"}
        require(not any(p.name in forbidden or p.suffix == ".db" for p in clean.rglob("*")),
                "Local player state included in clean package")
        for launcher in ("Play Spoiled Milk.cmd", "play-spoiled-milk.sh", "Play Spoiled Milk.command"):
            require((clean / launcher).is_file(), "Universal launcher missing: " + launcher)
        windows_launcher = (clean / "Play Spoiled Milk.cmd").read_text()
        require("runtime\\bin\\java.exe" in windows_launcher
                and "-Dspoiledmilk.releaseBuild=true" in windows_launcher
                and "if errorlevel 1 pause & exit /b 1" not in windows_launcher,
                "Windows launcher lacks its bundled runtime/release flag or has unsafe conditional")
        require((clean / "game-files/runtime/bin/java.exe").is_file(), "Bundled Windows Java missing")
        old_match = re.fullmatch(r"spoiled-milk-(v.+)-java.zip", args.previous_java_archive.name)
        require(old_match is not None, "Previous Java archive filename is unreadable")
        previous = old_match.group(1)
        require(version_key(previous) < version_key(version), "Candidate must advance the previous version")
        old = extract(args.previous_java_archive, f"spoiled-milk-{previous}-java/", run / "old-install-with-spaces")
        Feed.version, Feed.previous_version, Feed.candidate = version, previous, paths[0]
        with http.server.ThreadingHTTPServer(("127.0.0.1", 0), Feed) as server:
            worker = threading.Thread(target=server.serve_forever, daemon=True)
            worker.start()
            try:
                bindir = write_bridges(run, server.server_port, version)
                env = {k: v for k, v in os.environ.items()
                       if not k.startswith(("SPOILED_MILK_", "OPENRSC_"))
                       and k not in ("JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS",
                                     "http_proxy", "https_proxy", "HTTP_PROXY", "HTTPS_PROXY",
                                     "ALL_PROXY", "all_proxy")}
                env["PATH"] = str(bindir) + os.pathsep + os.environ["PATH"]
                env["NO_PROXY"] = env["no_proxy"] = "127.0.0.1,localhost"
                Feed.mode = "candidate"
                launch(clean, "clean-launch", run, env)
                launch(clean, "macos-shell-delegation", run, env, "Play Spoiled Milk.command")
                old_updater = old / "game-files/update-spoiled-milk.sh"
                old_updater_hash = digest(old_updater)
                preferences = {"clientSettings.conf": b"opengl_renderer_profile=remaster\nshadow_quality=low\n",
                               "preferences.ini": b"fixture-preference=true\n",
                               "Cache/credentials.txt": b"fixture-only-no-real-credentials\n",
                               "Cache/uid.dat": bytes(range(8))}
                for name, data in preferences.items():
                    p = old / "game-files" / name
                    p.parent.mkdir(parents=True, exist_ok=True)
                    p.write_bytes(data)
                snapshot = tree(old)
                failures = []
                for mode in ("api-failure", "missing-asset", "download-failure", "bad-archive"):
                    Feed.mode = mode
                    result = subprocess.run(["sh", str(old_updater)], cwd=run, env=env,
                                            text=True, capture_output=True, timeout=120)
                    (run / (mode + ".log")).write_text(result.stdout + result.stderr)
                    require(result.returncode != 0, "Invalid update was accepted: " + mode)
                    require(tree(old) == snapshot, "Installed files changed after pre-install failure: " + mode)
                    failures.append(mode)
                require(digest(old_updater) == old_updater_hash, "Released updater changed before rehearsal")
                Feed.mode = "candidate"
                launch(old, "old-to-candidate-launch", run, env)
                metadata(old, args, report["clientSha256"])
                require((old / "game-files/VERSION.txt").read_text().strip() == version, "Upgrade version mismatch")
                for name, data in preferences.items():
                    require((old / "game-files" / name).read_bytes() == data, "Preference/identity changed: " + name)
                upgraded = tree(old)
                Feed.mode = "older"
                launch(old, "no-downgrade-launch", run, env)
                require(tree(old) == upgraded, "New candidate downgraded to old public version")
                # All three shadow options must survive subsequent ordinary launches.
                for quality in ("off", "low", "high"):
                    setting = old / "game-files/clientSettings.conf"
                    data = f"opengl_renderer_profile=remaster\nshadow_quality={quality}\n".encode()
                    setting.write_bytes(data)
                    launch(old, "saved-quality-" + quality, run, env)
                    require(setting.read_bytes() == data, "Shadow preference changed: " + quality)
                summary.update(version=version, previousVersion=previous,
                               sourceCommit=args.expected_source, clientSha256=report["clientSha256"],
                               mapManifestSha256=profile["manifestSha256"],
                               mapFingerprintSha256=profile["packageFingerprintSha256"],
                               endpoint=f"{args.host}:{args.port}", preinstallFailureChecks=failures,
                               cleanInstall=True, oldToCandidate=True, noDowngrade=True,
                               savedShadowQualities=["off", "low", "high"],
                               windowsLauncherStaticCheck=True, macOSShellDelegationCheck=True,
                               unchangedReleasedUpdaterSha256=old_updater_hash,
                               packageHashes=before, loopbackRequests=Feed.requests)
            finally:
                server.shutdown()
                worker.join(timeout=5)
        require(before == {name: digest(Path(name)) for name in before}, "Source archive mutated")
        summary["completed"] = True
    finally:
        (run / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps({"completed": summary["completed"], "receipt": str(run / "summary.json")}, indent=2))


if __name__ == "__main__":
    main()
