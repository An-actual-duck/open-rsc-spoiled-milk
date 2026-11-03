#!/usr/bin/env python3
"""Exercise the recovered catalog in fresh custom/authentic packaged clients."""

import os
from pathlib import Path
import subprocess
import tempfile


ROOT = Path(__file__).resolve().parents[2]
JAVA = Path(os.environ.get("JAVA_HOME", "/home/justin/jdk8u482-b08")) / "bin"
JAR = ROOT / "Client_Base/Open_RSC_Client.jar"


def main() -> None:
    for generator in ("slayer-art-appearances", "held-equipment-families"):
        subprocess.run(
            ["python3", str(ROOT / f"tools/generators/generate-{generator}.py"), "--check"],
            cwd=ROOT, check=True,
        )
    with tempfile.TemporaryDirectory(prefix="slayer-art-recovery-") as temporary:
        subprocess.run(
            [str(JAVA / "javac"), "-cp", str(JAR), "-d", temporary,
             str(ROOT / "tests/myworld/SlayerArtRecoveryClientFixture.java")], check=True,
        )
        # EntityHandler.load appends definitions. Each mode must start in a fresh JVM.
        for custom in ("true", "false"):
            subprocess.run(
                [str(JAVA / "java"), "-Xmx384m", "-XX:+UseSerialGC",
                 "-Djava.awt.headless=true", "-cp", temporary + os.pathsep + str(JAR),
                 "orsc.SlayerArtRecoveryClientFixture", str(ROOT), custom],
                cwd=temporary, check=True,
            )


if __name__ == "__main__":
    main()
