#!/usr/bin/env python3
"""Exercise Harvest and Clip through the server's scenery packet handler."""
import os
import subprocess
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SERVER = ROOT / "server"
FIXTURE = "com.openrsc.server.combat.HarvestingRequirementsFixture"


def main() -> None:
    java_home = os.environ.get("JAVA_HOME")
    java = str(Path(java_home) / "bin/java") if java_home else "java"
    javac = str(Path(java_home) / "bin/javac") if java_home else "javac"
    classpath = os.pathsep.join(str(SERVER / name) for name in ("core.jar", "plugins.jar"))
    source = SERVER / "test" / (FIXTURE.replace(".", "/") + ".java")
    with tempfile.TemporaryDirectory(prefix="harvesting-requirements-") as folder:
        temporary = Path(folder)
        runtime = temporary / "runtime"
        classes = temporary / "classes"
        classes.mkdir()
        subprocess.run(
            [sys.executable, str(ROOT / "tests/myworld/prepare-cleric-well-fixture.py"), str(runtime)],
            cwd=ROOT, check=True, stdout=subprocess.DEVNULL,
        )
        subprocess.run(
            [javac, "-proc:none", "-cp", classpath, "-sourcepath", str(SERVER / "test"),
             "-d", str(classes), str(source)],
            cwd=ROOT, check=True,
        )
        result = subprocess.run(
            [java, "-Xmx512m", "-Djava.awt.headless=true", "-cp", os.pathsep.join((str(classes), classpath)), FIXTURE],
            cwd=runtime, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=45,
        )
        if result.returncode:
            print(result.stdout, end="")
            result.check_returncode()
        for line in result.stdout.splitlines():
            if "PASS flax harvesting" in line:
                print("PASS " + line.split("PASS ", 1)[1])
                break
        else:
            raise AssertionError(result.stdout)


if __name__ == "__main__":
    main()
