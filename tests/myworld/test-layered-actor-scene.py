#!/usr/bin/env python3
"""Offline production encoder/client replay. Override jars to reproduce frozen artifacts."""
import os
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
core = Path(os.environ.get("ACTOR_SCENE_CORE_JAR", ROOT / "server/core.jar"))
client = Path(os.environ.get("ACTOR_SCENE_CLIENT_JAR", ROOT / "Client_Base/Open_RSC_Client.jar"))
with tempfile.TemporaryDirectory(prefix="layered-actor-scene-") as directory:
    cp = os.pathsep.join(map(str, [core, client]))
    support = ROOT / "server/test/com/openrsc/server/combat"
    sources = [support / (name + ".java") for name in ["CurrentCombatHarness", "MutableGameClock", "SeededGameRandom"]]
    sources.append(ROOT / "tests/myworld/LayeredActorSceneFixture.java")
    subprocess.run(["javac", "-cp", cp, "-d", directory, *map(str, sources)], check=True)
    subprocess.run(["java", "-Xms128m", "-Xmx512m", "-XX:+UseSerialGC", "-Djava.awt.headless=true",
                    "-Dopenrsc.worldBuilderInstalledClientProfile=" + str(ROOT / "Client_Base/world-builder-configs/installed-client.json"),
                    "-cp", directory + os.pathsep + cp,
                    "com.openrsc.server.combat.LayeredActorSceneFixture"], cwd=ROOT / "server", check=True)
