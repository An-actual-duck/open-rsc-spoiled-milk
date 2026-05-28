#!/usr/bin/env python3
import re
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]


def fail(message: str) -> None:
    print(f"FAIL: {message}")
    sys.exit(1)


def java_major_version() -> int:
    result = subprocess.run(
        ["java", "-version"],
        cwd=ROOT,
        capture_output=True,
        text=True,
        check=True,
    )
    first_line = (result.stderr or result.stdout).splitlines()[0]
    match = re.search(r'version "([^"]+)"', first_line)
    if not match:
        fail(f"Unable to parse Java version from: {first_line}")
    raw_version = match.group(1)
    if raw_version.startswith("1."):
        major = raw_version.split(".")[1]
    else:
        major = raw_version.split(".")[0]
    return int(major)


def run_command(*args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        args,
        cwd=ROOT,
        capture_output=True,
        text=True,
    )


def require_output(result: subprocess.CompletedProcess[str], snippet: str, label: str) -> None:
    output = result.stdout + result.stderr
    if snippet not in output:
        fail(f"{label} missing expected output {snippet!r}:\n{output}")


def test_run_server_exit_behavior() -> None:
    result = run_command("timeout", "10s", "./scripts/run-server.sh")
    if result.returncode == 0:
        fail("run-server.sh unexpectedly exited 0 after the server process stopped")
    if result.returncode not in {1, 124}:
        fail(f"run-server.sh exited with unexpected code {result.returncode}")
    if result.returncode == 1:
        output = result.stdout + result.stderr
        if "Operation not permitted" in output:
            return
        if "ClassNotFoundException: com.openrsc.server.Server" in output:
            return
        fail(
            "run-server.sh returned a propagated failure, but not one of the expected "
            f"startup/build failures:\n{output}"
        )


def test_run_server_zgc_guard() -> None:
    result = run_command("timeout", "10s", "./scripts/run-server-zgc.sh")
    major = java_major_version()
    if major < 17:
        if result.returncode == 0:
            fail("run-server-zgc.sh unexpectedly exited 0 under Java < 17")
        require_output(result, "requires Java 17+", "run-server-zgc.sh Java preflight")
        return

    if result.returncode == 0:
        fail("run-server-zgc.sh unexpectedly exited 0 after startup ended")
    if result.returncode not in {1, 124}:
        fail(f"run-server-zgc.sh exited with unexpected code {result.returncode}")


def main() -> None:
    test_run_server_exit_behavior()
    test_run_server_zgc_guard()
    print("PASS: MyWorld entrypoint exit behavior validated")


if __name__ == "__main__":
    main()
