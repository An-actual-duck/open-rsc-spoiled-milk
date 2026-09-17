#!/usr/bin/env python3
"""Build this pinned local adapter's additive files for later manager review.

No packaged executable is replaced. No application is launched by this tool.
"""
import argparse
import hashlib
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools/local-world-builder-client"
LAUNCHER = "Start Spoiled Milk World Builder.sh"
PATCH_DIR = "local-spoiled-milk-client"
PINNED = {
    "builder-runtime/Client_Base/Open_RSC_Client.jar": "fccec5bb042594562e9496b8a3ac09e6c7029e5dbd73f0a2b8f26cfb0fb23479",
    "builder-runtime/launcher/world-builder-tools.jar": "c95cf08f4b2be22700e9c02c1cda808597763255bda4fac54ed572da0e19136b",
    "Start World Builder.sh": "a73f7df70b484e729b7a8218fc222adc8ad8b9599b9d1f35baf3e63c0bfbebbe",
}


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("installation", type=Path)
    parser.add_argument("--output", type=Path, required=True,
                        help="new, absent directory under this checkout's output/")
    args = parser.parse_args()
    app = args.installation.resolve(strict=True)
    output = args.output.resolve()
    if not output.is_relative_to(ROOT / "output") or output.exists():
        parser.error("output must be a new directory under this checkout's output/")
    for path, expected in PINNED.items():
        if (app / path).is_symlink() or digest(app / path) != expected:
            parser.error("installed client differs from reviewed v0.8.0")
    runtime_commit = (app / "RUNTIME-PROVIDER-COMMIT.txt").read_text().strip()
    if runtime_commit != "0355b2eccf1717a3235251d7b917f13b2fa454d6":
        parser.error("runtime source version differs")
    output.mkdir(parents=True)
    bundle = output / PATCH_DIR
    bundle.mkdir()
    with tempfile.TemporaryDirectory(prefix="local-builder-agent-") as scratch:
        subprocess.run(["javac", "-source", "8", "-target", "8", "-d", scratch,
                        str(SOURCE / "LocalPresentationAgent.java")], check=True)
        subprocess.run(["jar", "cfm", str(bundle / "agent.jar"), str(SOURCE / "agent.mf"),
                        "-C", scratch, "."], check=True)
    app_paths = list(PINNED) + ["RUNTIME-PROVIDER-COMMIT.txt",
                             "SOURCE-COMMIT.txt", "RELEASE-IDENTITY.json"]
    (bundle / "application-pin.sha256").write_text("".join(
        digest(app / path) + "  " + path + "\n" for path in app_paths))
    shutil.copyfile(SOURCE / LAUNCHER, output / LAUNCHER)
    (output / LAUNCHER).chmod(0o755)
    (bundle / "bundle.sha256").write_text("".join(
        digest(bundle / path) + "  " + path + "\n"
        for path in ("agent.jar", "application-pin.sha256")))
    print("Built additive local bundle:", output)
    print("NOT INSTALLED. After review copy its launcher and local-spoiled-milk-client/")
    print("into the installation only if neither destination exists. Preserve originals.")
    print("Rollback: stop using the optional launcher; normal launcher is unchanged.")


if __name__ == "__main__":
    main()
