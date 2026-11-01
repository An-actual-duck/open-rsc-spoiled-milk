#!/usr/bin/env python3
"""Offline production encoder/client replay. Override jars to reproduce frozen artifacts."""
import os
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
core = Path(os.environ.get("ACTOR_SCENE_CORE_JAR", ROOT / "server/core.jar"))
client = Path(os.environ.get("ACTOR_SCENE_CLIENT_JAR", ROOT / "Client_Base/Open_RSC_Client.jar"))
with tempfile.TemporaryDirectory(prefix="layered-actor-scene-") as directory:
    work = Path(directory)
    # Synthetic flat terrain only: never load an owner's installed package.
    fingerprint = "a" * 64
    package = work / "client/world-builder/packages" / fingerprint / "package"
    package.mkdir(parents=True)
    tile = {"schemaVersion": 1, "encoding": "uniform-layered-sector-v1", "size": 48,
            "tile": {"elevation": 0, "texture": 1, "overlay": 0, "roof": 0,
                     "verticalWall": 0, "horizontalWall": 0, "diagonalWall": 0}}
    raw = (json.dumps(tile) + "\n").encode()
    sectors = []
    for level in (0, -1, -37):
        for x in range(8, 12):
            for y in range(11, 14):
                name = f"terrain-{level}-{x}-{y}.json"
                (package / name).write_bytes(raw)
                sectors.append(dict(worldSpace="global", level=level, sectorX=x, sectorY=y,
                                    encoding=tile["encoding"], path=name, sha256=hashlib.sha256(raw).hexdigest()))
    manifest = dict(schemaVersion=1, packageType="layered-world", packageId="test.actor-scene",
                    packageVersion="1.0.0", coordinateModel="signed-layered-v1",
                    storage=dict(sectorSize=48, presentationChunkSize=24),
                    worldSpaces=[dict(id="global", kind="static")],
                    levels=[dict(worldSpace="global", level=l, name=str(l), role="fixture") for l in (0,-1,-37)],
                    terrainSectors=sectors, placementSets=[])
    manifest_bytes = (json.dumps(manifest) + "\n").encode()
    (package / "manifest.json").write_bytes(manifest_bytes)
    manifest_hash = hashlib.sha256(manifest_bytes).hexdigest()
    profile = work / "client/world-builder-configs/installed-client.json"
    profile.parent.mkdir(parents=True)
    profile.write_text(json.dumps(dict(schemaVersion=1, manifestType="world-builder-installed-client-profile",
        active=True, packageId=manifest["packageId"], packageVersion="1.0.0",
        packageFingerprintSha256=fingerprint, manifestSha256=manifest_hash,
        packageRelativePath=f"world-builder/packages/{fingerprint}/package")))
    env = os.environ.copy()
    for gate in ("PLAYER_LOCATION_AUTHORITY", "SPATIAL_RUNTIME_AUTHORITY", "PROTOCOL_CLIENT_AUTHORITY",
                 "NATIVE_TERRAIN_PACKAGE", "NATIVE_TERRAIN_RESIDENCY", "NATIVE_TERRAIN_READINESS",
                 "NATIVE_TERRAIN_PREDICTION", "NATIVE_TERRAIN_SYMMETRIC_RESIDENCY", "NATIVE_TERRAIN_ATOMIC_ACTIVATION"):
        env["OPENRSC_LAYERED_" + gate] = "true"
    env.update(OPENRSC_LAYERED_NATIVE_TERRAIN_PACKAGE_PATH=str(package),
               OPENRSC_LAYERED_NATIVE_TERRAIN_MANIFEST_SHA256=manifest_hash,
               OPENRSC_LAYERED_NATIVE_WORLD_RUNTIME_PROFILE="native-additive", OPENRSC_SYNC_SCENE_BASELINE="true")
    cp = os.pathsep.join(map(str, [core, client]))
    support = ROOT / "server/test/com/openrsc/server/combat"
    sources = [support / (name + ".java") for name in ["CurrentCombatHarness", "MutableGameClock", "SeededGameRandom"]]
    sources.append(ROOT / "tests/myworld/LayeredActorSceneFixture.java")
    subprocess.run(["javac", "-cp", cp, "-d", directory, *map(str, sources)], check=True)
    subprocess.run(["java", "-Xms128m", "-Xmx512m", "-XX:+UseSerialGC", "-Djava.awt.headless=true",
                    "-Dopenrsc.worldBuilderInstalledClientProfile=" + str(profile),
                    "-cp", directory + os.pathsep + cp,
                    "com.openrsc.server.combat.LayeredActorSceneFixture"], cwd=ROOT / "server", env=env, check=True)
