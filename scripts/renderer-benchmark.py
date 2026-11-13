#!/usr/bin/env python3
"""Prepare and run a disposable, loopback-only native renderer benchmark."""
import argparse
import csv
import datetime
import hashlib
import importlib.util
import json
import math
import os
from pathlib import Path
import re
import shutil
import signal
import socket
import sqlite3
import subprocess
import sys
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
MARKER = "RENDERER-BENCHMARK.json"
PORTS = (43639, 43539)


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def marked(run):
    run = Path(run).resolve()
    if not (run / MARKER).is_file():
        raise ValueError("A marked renderer benchmark run is required")
    return run


def validate_artifacts(run, include_client=True):
    marker = json.loads((run / MARKER).read_text())
    if marker.get("schema") != 1 or marker.get("private") is not True or marker.get("tcp") != PORTS[0] or marker.get("websocket") != PORTS[1]:
        raise ValueError("Private renderer marker identity mismatch")
    server = run / "server"
    database = server / "inc/sqlite/renderer_benchmark_dev.db"
    if not database.is_file() or database.is_symlink(): raise ValueError("Private regular database required")
    for artifact in marker["artifacts"]:
        source = server / artifact["path"]
        if not source.resolve().is_relative_to(server.resolve()) or source.is_symlink() or sha(source) != artifact["sha256"]:
            raise ValueError("Immutable server artifact mismatch")
    if include_client and sha(run / "client/client.jar") != marker["clientSha256"]:
        raise ValueError("Immutable client artifact mismatch")
    for relative, expected in marker.get("compiledClasses", {}).items():
        source = run / "classes" / relative
        if not source.resolve().is_relative_to((run / "classes").resolve()) or sha(source) != expected:
            raise ValueError("Immutable benchmark bootstrap mismatch")


def free_ports():
    for port in PORTS:
        with socket.socket() as sock:
            sock.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)  # Ignore closed-connection TIME_WAIT, never a live listener.
            sock.bind(("127.0.0.1", port))


def classpath(server, classes):
    lib = server / "lib"
    first = ["log4j-api-2.17.0.jar", "log4j-core-2.17.0.jar", "log4j-iostreams-2.17.0.jar", "xpp3-1.1.4c.jar", "mysql-connector-j-9.4.0.jar", "commons-lang-2.6.jar"]
    return os.pathsep.join(map(str, [*(lib / name for name in first), server / "core-gameplay-overlay.jar", server / "core.jar", server / "plugins.jar", lib / "*", classes]))


def resolve_source(source):
    if re.fullmatch(r"[0-9a-f]{7,40}", source):
        return subprocess.check_output(["git", "rev-parse", "--verify", source + "^{commit}"], cwd=ROOT, text=True).strip()
    return source  # Explicit patch-based baseline labels are not falsely called full commits.


def prepare(args):
    if not re.fullmatch(r"[A-Za-z0-9_-]+", args.name):
        raise ValueError("Use a simple unique run name")
    overrides = (args.server_core, args.server_overlay, args.server_source)
    if any(overrides) and not all(overrides):
        raise ValueError("Server override requires matched core, gameplay overlay and source receipt")
    jar = args.client_jar.resolve()
    with zipfile.ZipFile(jar) as archive:
        if b"spoiledmilk.openglHiddenDiagnostic" not in archive.read("orsc/OpenGLWindowController.class"):
            raise ValueError("Client must support the opt-in hidden diagnostic window")
    category = Path(subprocess.check_output([sys.executable, str(ROOT / "scripts/workspace-layout.py"), "path", "tests", "renderer-shadow-benchmarks", "--mkdir"], text=True).strip())
    run = category / args.name
    run.mkdir()  # Existing runs, databases and captures are never overwritten.
    server = run / "server"; server.mkdir()
    client = run / "client"; client.mkdir()
    selection = json.loads(subprocess.check_output([sys.executable, str(ROOT / "scripts/installed-map-selection.py"), str(ROOT)], text=True))
    for directory in ("conf", "database"):
        shutil.copytree(ROOT / "server" / directory, server / directory)
    for name in ("badwords.txt", "goodwords.txt", "globalrules.txt", "alertwords.txt"):
        shutil.copy2(ROOT / "server" / name, server / name)
    package = server / "package"; shutil.copytree(selection["serverPackage"], package)
    config = (ROOT / "server/myworld.conf").read_text()
    values = dict(db_name="renderer_benchmark_dev", server_name="Private Renderer Benchmark", server_name_welcome="Private Renderer Benchmark", server_bind_address="127.0.0.1", server_port="43639", ws_server_port="43539", want_feature_websockets="true", want_discord_bot="false", want_discord_general_logging="false", want_discord_auction_updates="false", want_discord_item_showcase="false", want_discord_monitoring_updates="false", want_auto_server_shutdown="false", layered_native_terrain_package_path=str(package), layered_native_terrain_manifest_sha256=selection["manifestSha256"], layered_native_world_runtime_profile="world-builder-installed")
    for key, value in values.items():
        config, count = re.subn(r"(?m)^([ \t]*)" + key + r":.*$", lambda m: m[1] + key + ": " + value, config)
        if not count: config += "\n" + key + ": " + value + "\n"
    (server / "myworld.conf").write_text(config)
    subprocess.run(["openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-keyout", str(server / "private-key.pem"), "-out", str(server / "private-cert.pem"), "-days", "7", "-subj", "/CN=localhost"], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)
    (server / "private-key.pem").chmod(0o600)
    (server / "connections.conf").write_text("db_type: sqlite\nssl:\n\tssl_server_cert_path: " + str(server / "private-cert.pem") + "\n\tssl_server_key_path: " + str(server / "private-key.pem") + "\n")
    seed = ROOT / "server/inc/sqlite/myworld_seed.db"
    with sqlite3.connect("file:" + str(seed) + "?mode=ro", uri=True) as db:
        if db.execute("SELECT COUNT(*) FROM players").fetchone()[0] != 0 or db.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
            raise ValueError("Seed must be intact and have zero accounts")
    dbdir = server / "inc/sqlite"; dbdir.mkdir(parents=True)
    shutil.copy2(seed, dbdir / "renderer_benchmark_dev.db"); (dbdir / "renderer_benchmark_dev.db").chmod(0o600)
    artifact_root = args.server_artifacts.resolve() if args.server_artifacts else ROOT / "server"
    if args.server_artifacts and not args.server_source:
        raise ValueError("External server artifacts require an explicit source receipt")
    artifacts = []
    for source in [*(artifact_root / n for n in ("core.jar", "plugins.jar", "core-gameplay-overlay.jar")), *sorted((artifact_root / "lib").glob("*.jar"))]:
        if args.server_artifacts and (not source.is_file() or source.is_symlink()): raise ValueError("Regular immutable server artifact required")
        relative = source.relative_to(artifact_root); target = server / relative; target.parent.mkdir(exist_ok=True)
        actual = args.server_core if relative.as_posix() == "core.jar" and args.server_core else args.server_overlay if relative.as_posix() == "core-gameplay-overlay.jar" and args.server_overlay else source
        shutil.copy2(actual, target); artifacts.append(dict(path=str(relative), sha256=sha(target)))
    shutil.copy2(jar, client / "client.jar")
    cache = client / "Cache"; cache.mkdir()
    for name in ("audio", "video"): shutil.copytree(ROOT / "Client_Base/Cache" / name, cache / name)
    (cache / "config.txt").write_bytes(subprocess.check_output(["git", "show", "HEAD:Client_Base/Cache/config.txt"], cwd=ROOT))
    for name, value in {"ip.txt":"127.0.0.1\n", "port.txt":"43639\n", "credentials.txt":"\n", "discord_inuse.txt":"1\n"}.items(): (cache / name).write_text(value)
    (cache / "uid.dat").write_bytes(bytes(8))
    profile = ROOT / "Client_Base/world-builder-configs/installed-client.json"
    relative = Path(json.loads(profile.read_text())["packageRelativePath"])
    if relative.is_absolute() or ".." in relative.parts: raise ValueError("Unsafe package path")
    (client / "world-builder-configs").mkdir(); shutil.copy2(profile, client / "world-builder-configs/installed-client.json")
    (client / relative).mkdir(parents=True); shutil.copy2(ROOT / "Client_Base" / relative / "manifest.json", client / relative / "manifest.json")
    classes = run / "classes"; classes.mkdir()
    sources = run / "sources"; sources.mkdir()
    shutil.copy2(ROOT / "tools/renderer-benchmark/RendererBenchmarkServer.java", sources / "RendererBenchmarkServer.java")
    shutil.copy2(ROOT / "tools/renderer-benchmark/orsc/RendererBenchmarkClient.java", sources / "RendererBenchmarkClient.java")
    cp = classpath(server, classes)
    subprocess.run(["javac", "-source", "8", "-target", "8", "-cp", cp, "-d", str(classes), str(sources / "RendererBenchmarkServer.java")], check=True)
    subprocess.run(["javac", "-source", "8", "-target", "8", "-cp", str(client / "client.jar"), "-d", str(classes), str(sources / "RendererBenchmarkClient.java")], check=True)
    marker = dict(schema=1, sourceCommit=subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(), clientSourceCommit=resolve_source(args.client_source), clientSourceBranch=args.client_branch, clientSha256=sha(client / "client.jar"), seedSha256=sha(seed), seedAccounts=0, mapManifest=selection["manifestSha256"], artifacts=artifacts, private=True, hideRoofsPreference=not args.show_roofs, tcp=PORTS[0], websocket=PORTS[1], source=str(ROOT))
    marker["serverSourceCommit"] = resolve_source(args.server_source) if args.server_source else marker["sourceCommit"]
    marker["serverArtifactRoot"] = str(artifact_root)
    marker["sustainSummonsForBenchmark"] = args.sustain_summons_for_benchmark
    marker["toolSources"] = {p.name:sha(p) for p in sources.iterdir()}
    marker["compiledClasses"] = {p.relative_to(classes).as_posix():sha(p) for p in classes.rglob("*.class")}
    (run / MARKER).write_text(json.dumps(marker, indent=2) + "\n")
    (run / "server-command.json").write_text(json.dumps(dict(cwd=str(server), argv=["java", "-Xmx2g", "-XX:+UseSerialGC", "-cp", cp, "RendererBenchmarkServer"]), indent=2) + "\n")
    print(run)


def start(args):
    run = marked(args.run); validate_artifacts(run); free_ports()
    if (run / "STOP").exists() or (run / "processes.json").exists(): raise ValueError("Use a fresh prepared run")
    command = dict(cwd=str(run / "server"), argv=["java", "-Xmx2g", "-XX:+UseSerialGC", "-cp", classpath(run / "server", run / "classes"), "RendererBenchmarkServer"])
    with (run / "server.log").open("ab") as log:
        proc = subprocess.Popen(command["argv"], cwd=command["cwd"], stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    (run / "processes.json").write_text(json.dumps(dict(server=proc.pid), indent=2) + "\n")
    print("Private renderer server PID", proc.pid)


def capture_history(run, phase):
    previous = sorted(path.parent for path in run.glob("*/command.json"))
    if phase == "relog":
        if len(previous)!=1 or not (previous[0]/"CLIENT-DONE").is_file():
            raise ValueError("Relog proof requires exactly one completed first login")
        return previous[0]
    if previous:
        raise ValueError("Use one fresh server fixture per capture; reconnect geometry parity is not assumed")
    return None


def capture(args):
    run = marked(args.run); validate_artifacts(run)
    visible = args.action == "preview"
    previous_capture = capture_history(run,args.phase)
    processes = json.loads((run / "processes.json").read_text())
    if processes.get("client") and owned_process_live(processes["client"],run/"client","orsc.RendererBenchmarkClient"):
        raise ValueError("A fixture client is already running")
    deadline = time.monotonic() + 60
    while True:
        try:
            with socket.create_connection(("127.0.0.1", PORTS[0]), timeout=1): break
        except OSError:
            if time.monotonic() >= deadline: raise ValueError("Private server did not become ready")
            time.sleep(.25)
    name = args.phase + "-" + datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    capture = run / name; capture.mkdir()
    (capture / "scenario.json").write_text(json.dumps(dict(phase=args.phase, requestedViewer=[args.x,args.y,0], zoom=args.zoom, initialRotation=args.rotation, quality=args.quality, legacyMask=args.legacy_mask, sunAzimuth=args.sun_azimuth if args.phase not in ("sun-motion","night") else None, sunElevation=args.sun_elevation if args.phase not in ("sun-motion","night") else None, normalRateServerSun=args.phase in ("sun-motion","night"),relogFrom=str(previous_capture) if previous_capture else None, visiblePreview=visible,excludedCasterKinds=args.exclude_caster,dumpTerrain=args.dump_terrain,dumpFences=args.dump_fences,requestedWindowMode=args.window_mode if visible else "diagnostic-hidden",warmupSeconds=args.warmup_seconds,fogRequested=args.fog,snapshotFrames=args.snapshot_frames,tonePreview=args.tone_preview if args.phase not in ("sun-motion","night") else None),indent=2)+"\n")
    request = run / "request.next"
    request.write_text(json.dumps(dict(busy=args.phase.startswith("busy") or args.phase in ("sun-motion","combat-real"), phase=args.phase, x=args.x, y=args.y)))
    os.replace(request, run / "request.json")
    props = {"spoiledmilk.openglHiddenDiagnostic":"true", "spoiledmilk.openglHiddenWidth":"1920", "spoiledmilk.openglHiddenHeight":"1080", "spoiledmilk.rendererDiagnostics":"true", "spoiledmilk.rendererTelemetry":"true", "spoiledmilk.rendererDiagnosticSessionDir":str(capture), "spoiledmilk.rendererDiagnosticMaxLogBytes":str(128*1024*1024), "spoiledmilk.clientLog":str(capture / "runtime.log"), "spoiledmilk.clientCommit":json.loads((run / MARKER).read_text())["clientSourceCommit"], "spoiledmilk.openglVsync":"false", "renderer.benchmark.seconds":str(args.seconds), "renderer.benchmark.phase":args.phase, "renderer.benchmark.zoom":str(args.zoom), "spoiledmilk.openglRendererProfile":"remaster", "spoiledmilk.shadowQuality":args.quality}
    if args.native_resident_input_cache is not None:
        selected = str(args.native_resident_input_cache == "on").lower()
        props.update({"spoiledmilk.nativeResidentInputCache":selected,
                      "renderer.benchmark.nativeResidentInputCache":selected})
    props["renderer.benchmark.visiblePreview"] = str(visible).lower()
    props["spoiledmilk.openglHiddenDiagnostic"] = str(not visible).lower()
    if visible: props["spoiledmilk.openglWindowMode"] = args.window_mode
    props["spoiledmilk.shadowGpuTimings"] = "true"
    props["renderer.benchmark.fog"] = args.fog
    props["renderer.benchmark.dumpRoofMask"] = str(args.dump_roof_mask).lower()
    props["renderer.benchmark.captureFrames"] = str(args.snapshot_frames)
    props["renderer.benchmark.warmupSeconds"] = str(args.warmup_seconds)
    props["renderer.benchmark.dumpTerrain"] = str(args.dump_terrain).lower()
    props["renderer.benchmark.dumpFences"] = str(args.dump_fences).lower()
    props["renderer.benchmark.rotation"] = str(args.rotation)
    if args.legacy_mask: props["spoiledmilk.shadowBenchmarkLegacyMask"] = "true"
    if args.exclude_caster: props["spoiledmilk.shadowDiagnosticExclude"] = ",".join(args.exclude_caster)
    if args.phase not in ("sun-motion","night"):
        props.update({"spoiledmilk.remasterLightAzimuth":str(args.sun_azimuth), "spoiledmilk.remasterLightElevation":str(args.sun_elevation), "spoiledmilk.openglTonePreview":args.tone_preview})
    props.update({"spoiledmilk.openglFrameCapture":str(args.snapshot).lower(), "renderer.benchmark.snapshot":str(args.snapshot).lower(), "spoiledmilk.openglFrameCaptureDir":str(capture / "captures")})
    common_git = Path(subprocess.check_output(["git", "rev-parse", "--path-format=absolute", "--git-common-dir"], cwd=ROOT, text=True).strip())
    gpu_lock = common_git / "ai-workspaces/renderer-gpu-benchmark.lock"
    argv = ["flock", "-w", "10", "-F", str(gpu_lock), "java", "-Xms512m", "-Xmx2g", *(["-verbose:gc"] if args.gc_log else []), *["-D"+k+"="+v for k,v in props.items()], "-cp", str(run / "classes") + os.pathsep + str(run / "client/client.jar"), "orsc.RendererBenchmarkClient"]
    (capture / "command.json").write_text(json.dumps(dict(argv=argv, cwd=str(run / "client"), settings=props, hiddenOffscreen=not visible, initialHostLoadAverage=os.getloadavg()), indent=2)+"\n")
    environment = {key:value for key,value in os.environ.items() if not key.startswith(("SPOILED_MILK_", "OPENRSC_"))}
    environment.update(SPOILED_MILK_CLIENT_COMMIT=props["spoiledmilk.clientCommit"], SPOILED_MILK_CLIENT_BRANCH=json.loads((run / MARKER).read_text()).get("clientSourceBranch","unspecified-artifact-branch"), SPOILED_MILK_CLIENT_TARGET_MODE="private-renderer-benchmark")
    with (capture / "console.log").open("ab") as log:
        proc = subprocess.Popen(argv, cwd=run / "client", env=environment, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    time.sleep(.1)
    if proc.poll() is not None: raise ValueError("Private client refused startup/GPU lock; inspect preserved console log")
    processes = json.loads((run / "processes.json").read_text()); processes["client"] = proc.pid; (run / "processes.json").write_text(json.dumps(processes,indent=2)+"\n")
    print(capture); print("Private " + ("visible preview" if visible else "hidden") + " client PID", proc.pid)


def preview(args):
    """Explicitly open an owner-visible, automatically closed private test client."""
    capture(args)


def owned_process_live(pid, expected_cwd, entry):
    proc=Path("/proc")/str(pid)
    try:
        if (proc/"stat").read_text().rsplit(") ",1)[1].split()[0]=="Z": return False
        cwd=(proc/"cwd").resolve(strict=True); command=(proc/"cmdline").read_bytes()
        if cwd!=expected_cwd or entry.encode() not in command.split(b"\0"):
            if (proc/"stat").read_text().rsplit(") ",1)[1].split()[0]=="Z": return False
            raise ValueError(("Client" if entry.startswith("orsc.") else "Server")+" PID ownership mismatch")
        return True
    except (FileNotFoundError,ProcessLookupError): return False


def signal_owned_client(pid, expected_cwd):
    if not hasattr(os,"pidfd_open") or not hasattr(signal,"pidfd_send_signal"):
        raise ValueError("Safe private client shutdown requires Linux pidfd support; no signal sent")
    try: descriptor=os.pidfd_open(pid)
    except ProcessLookupError: return
    try:
        if owned_process_live(pid,expected_cwd,"orsc.RendererBenchmarkClient"):
            try: signal.pidfd_send_signal(descriptor,signal.SIGTERM)
            except ProcessLookupError: pass
    finally: os.close(descriptor)


def stop(args):
    run = marked(args.run); pids = json.loads((run / "processes.json").read_text())
    if pids.get("client"): signal_owned_client(pids["client"],run/"client")
    if owned_process_live(pids["server"],run/"server","RendererBenchmarkServer"):
        (run / "STOP").write_text("Private renderer benchmark graceful shutdown\n")
        for _ in range(100):
            if not (run / "server.pid").exists() and not owned_process_live(pids["server"],run/"server","RendererBenchmarkServer"): break
            time.sleep(.2)
        else: raise ValueError("Private graceful shutdown did not finish; no signal sent")
    print("Stopped own private processes; all data retained:", run)


def validate_runtime(run, samples, phase):
    if (run / "FIXTURE-FAILED").exists():
        raise ValueError("Fixture setup/runtime failure; reject this run")
    log = (run / "server.log").read_text(errors="replace") if (run / "server.log").is_file() else ""
    for line in log.splitlines():
        if "java.io.IOException: Connection reset by peer" in line:
            continue  # Expected own-client exit; no gameplay setup exception is exempt.
        if "RENDERER_FIXTURE_SETUP_FAILURE" in line or re.search(r"(?:^|[\s:])(?:[A-Za-z_$][\w$]*\.)*[A-Za-z_$][\w$]*(?:Exception|Error)(?::|$)", line):
            raise ValueError("Unexpected server runtime exception; reject this run: " + line[:240])
    positions = sorted({(sample["serverState"]["x"],sample["serverState"]["y"]) for sample in samples if isinstance(sample.get("serverState"),dict) and "x" in sample["serverState"] and "y" in sample["serverState"]})
    distance = max(max(p[axis] for p in positions)-min(p[axis] for p in positions) for axis in (0,1)) if positions else 0
    if phase == "busy-walk" and distance < 4:
        raise ValueError("Busy-walk failed to move the actual viewer at least4tiles")
    if phase == "combat-real":
        combat=[s.get("serverState",{}).get("realCombat",{}) for s in samples]
        if not combat or not all(c.get("authoritative") is True for c in combat):raise ValueError("Missing authoritative combat evidence")
        families=("ice","thunder","wood","ranged","throwing","melee")
        if len(combat)<2:raise ValueError("Combat coverage requires before/after workload samples")
        for state in combat:
            channels=state.get("channels",{})
            if state.get("workloadComplete") is not True or state.get("activeChannels")!=len(families):
                raise ValueError("Incomplete live combat workload")
            if any(not all(channels.get(k,{}).get(flag) is True for flag in ("running","registered","playerRegistered")) for k in families):
                raise ValueError("Missing, stopped, or orphaned combat channel")
            identities=[channels[k].get("actorIdentity") for k in families]
            indices=[channels[k].get("actorIndex") for k in families]
            if any(not isinstance(value,str) or not value for value in identities) or len(set(identities))!=len(families) or any(type(value) is not int or value<0 for value in indices) or len(set(indices))!=len(families):
                raise ValueError("Combat actor identities/indices are not distinct")
        last=combat[-1]
        first=combat[0]
        if any(first.get("creditedDamageByFamily",{}).get(k,0)<=0 or last.get("creditedDamageByFamily",{}).get(k,0)<=first.get("creditedDamageByFamily",{}).get(k,0) for k in families):raise ValueError("An expected combat family has no advancing authoritative damage contribution")
        if any(first.get("resourceUnitsSpent",{}).get(k,0)<=0 or last.get("resourceUnitsSpent",{}).get(k,0)<=first.get("resourceUnitsSpent",{}).get(k,0) for k in families[:-1]):raise ValueError("An expected combat family consumed no additional resources")
        if any(first["channels"][k].get("eventTicks",0)<=0 or last["channels"][k].get("eventTicks",0)<=first["channels"][k].get("eventTicks",0) for k in families):raise ValueError("An expected combat scheduler channel did not advance")
        if max(c.get("summonsEngaged",0) for c in combat)<=0:raise ValueError("Summons never engaged production combat")
        if max(s.get("combatVisuals",{}).get("activeHitSplatActors",0) for s in samples)<=0:raise ValueError("No received active hit splats")
        if max(s.get("activeProjectileActors",0) for s in samples)<2:raise ValueError("No overlapping received projectiles")
    return dict(positions=positions,maxAxisDistanceTiles=distance,fixtureRuntimeExceptions=False)


def validate_window_lifecycle(events):
    expected=["windowed","resize","fullscreen","borderless","windowed-again","iconify","activate-restore","resumed"]
    if [e.get("action") for e in events] != expected:raise ValueError("Full native game-window lifecycle evidence is incomplete")
    for event in events:
        if event.get("gameObjects",0)<=0:raise ValueError("Window lifecycle lost the actual game world")
        if event["action"]!="iconify" and event.get("afterFrames",0)<=event.get("beforeFrames",0):raise ValueError("Completed world frames did not resume after native window operation")
    if events[-2].get("flags")!=1 or events[-1].get("flags")!=1:raise ValueError("Native window activation did not restore visible uniconified rendering")
    if events[1].get("surface",{}).get("windowWidth")!=1100 or events[1].get("surface",{}).get("windowHeight")!=700:raise ValueError("Native resize dimensions were not observed")
    return dict(actions=expected,restoredVisibleUniconified=True,completedFramesResume=True,worldRetained=True)


def report(args):
    capture = args.capture.resolve(); run = marked(capture.parent)
    if not (capture / "CLIENT-DONE").is_file():
        raise ValueError("Wait for client completion before summarizing buffered GPU samples")
    spec = importlib.util.spec_from_file_location("renderer_analyzer", ROOT / "scripts/analyze-renderer-session.py")
    analyzer = importlib.util.module_from_spec(spec); spec.loader.exec_module(analyzer)
    telemetry, partial_telemetry = analyzer.read_jsonl(capture / "telemetry.jsonl")
    events, partial_events = analyzer.read_jsonl(capture / "events.jsonl")
    phases = analyzer.performance_phases(events)
    if len(phases) != 1: raise ValueError("Exactly one completed timed phase is required")
    start_event, stop_event = phases[0]
    start = start_event["sessionElapsedNanos"]; end = stop_event["sessionElapsedNanos"]
    selected = [record for record in telemetry if start <= record.get("sessionElapsedNanos", -1) <= end]
    distributions = [record for record in selected if record.get("trigger") in ("periodic", "slow-frame", "performance-phase-stop")]
    def summary(samples):
        if not samples: return None
        return dict(count=len(samples), p50ms=analyzer.percentile(samples,.50)/1e6, p95ms=analyzer.percentile(samples,.95)/1e6, p99ms=analyzer.percentile(samples,.99)/1e6, maxms=max(samples)/1e6, meanms=sum(samples)/len(samples)/1e6, over16_667ms=sum(value>1e9/60 for value in samples), over25ms=sum(value>25_000_000 for value in samples), over33_334ms=sum(value>1e9/30 for value in samples), over50ms=sum(value>50_000_000 for value in samples))
    stages = {name:summary(analyzer.stage_samples(distributions, name)) for name in ("clientLoop", "sceneRender", "openGLRender", "openGLWorld", "openGLFrameInterval")}
    gpu = {}; seen = set(); duplicates = 0; gpu_phase_counts = {}
    if (capture / "gpu-samples.csv").is_file():
        with (capture / "gpu-samples.csv").open() as stream:
            for sample in csv.DictReader(stream):
                key = (sample["pass"], int(sample["sequence"]))
                if key in seen: duplicates += 1; continue
                seen.add(key)
                if start <= int(sample["sessionElapsedNanos"]) <= end:
                    gpu_phase_counts[sample["pass"]] = gpu_phase_counts.get(sample["pass"], 0) + 1
                # Completion is read asynchronously. Exclude one second at phase edges.
                if start+1e9 <= int(sample["sessionElapsedNanos"]) <= end-1e9:
                    gpu.setdefault(sample["pass"], []).append(int(sample["gpuNanos"]))
    samples, partial_samples = analyzer.read_jsonl(capture / "client-samples.jsonl")
    keys = ("logicalWidth", "logicalHeight", "framebufferWidth", "framebufferHeight", "contentViewportWidth", "contentViewportHeight", "zoomSetting", "players", "npcs", "sunAzimuth", "sunElevation", "brightness", "tone", "toneMode", "fogMode", "lighting", "profile", "geometry", "shadowQuality", "hideRoofs", "gpuFarPollEveryFrame", "onScreenProjectedActorCandidates", "activeProjectileActors", "windowMode", "windowState", "gameObjects", "wallObjects")
    context = {key:sorted({sample[key] for sample in samples if key in sample and sample[key] is not None}) for key in keys}
    movement = validate_runtime(run, samples, start_event["label"])
    manifest = json.loads((capture / "manifest.json").read_text())
    result = dict(schema=1, capture=str(capture), artifact=json.loads((run / MARKER).read_text()), phase=start_event["label"], durationSeconds=(end-start)/1e9, partialRecords=partial_telemetry or partial_events or partial_samples, stages=stages, gpu={key:summary(values) for key,values in gpu.items()}, gpuDuplicateSamples=duplicates, context=context, openGL={key:manifest.get("openGL."+key) for key in ("renderer","version","vendor")}, fullClientPresentation60FpsAccepted=False, limitation="Hidden native window: frame-work/GPU workload measurements, not owner-visible presentation cadence or visual approval.")
    scenario = json.loads((capture / "scenario.json").read_text()) if (capture / "scenario.json").is_file() else {}
    effects = [sample["activeProjectileActors"] for sample in samples if "activeProjectileActors" in sample]
    surfaces = [sample["surface"] for sample in samples if isinstance(sample.get("surface"),dict)]
    viewport_pairs = sorted({(sample.get("contentViewportWidth",sample.get("framebufferWidth")),sample.get("contentViewportHeight",sample.get("framebufferHeight"))) for sample in samples if sample.get("contentViewportWidth",sample.get("framebufferWidth")) is not None and sample.get("contentViewportHeight",sample.get("framebufferHeight")) is not None})
    flags = [sample["windowState"] for sample in samples if isinstance(sample.get("windowState"),int)]
    result["presentationEvidence"] = dict(windowStateSamples=len(flags),allSamplesVisibleUniconified=bool(flags) and all(flag==1 for flag in flags),contentViewportSizes=viewport_pairs,contentMatches1080Target=bool(viewport_pairs) and viewport_pairs==[(1920,1080)])
    result["surfaceContext"] = {key:sorted({value[key] for value in surfaces if key in value}) for key in ("framebufferWidth","framebufferHeight","windowWidth","windowHeight")}
    for sample in samples:
        if sample.get("publishedFrameAvailable") is False and (sample.get("onScreenProjectedActorCandidates") is not None or sample.get("roof") is not None):
            raise ValueError("Unavailable published frame must not claim actor/roof observations, including zero")
    projections=[sample["onScreenProjectedActorCandidates"] for sample in samples if isinstance(sample.get("onScreenProjectedActorCandidates"),int) and not isinstance(sample["onScreenProjectedActorCandidates"],bool)]
    reasons=[sample.get("publishedFrameUnavailableReason") for sample in samples if isinstance(sample.get("publishedFrameUnavailableReason"),str)]
    result["observationCoverage"]=dict(totalSamples=len(samples),declaredFrameAvailableSamples=sum(sample.get("publishedFrameAvailable") is True for sample in samples),declaredFrameUnavailableSamples=sum(sample.get("publishedFrameAvailable") is False for sample in samples),legacyFrameAvailabilityUnrecordedSamples=sum("publishedFrameAvailable" not in sample for sample in samples),actorProjectionAvailableSamples=len(projections),actorProjectionUnknownSamples=len(samples)-len(projections),actorProjectionMeasuredZeroSamples=sum(value==0 for value in projections),allActorProjectionSamplesAvailable=bool(samples) and len(projections)==len(samples),roofAvailableSamples=sum(isinstance(sample.get("roof"),dict) for sample in samples),roofUnknownSamples=sum(not isinstance(sample.get("roof"),dict) for sample in samples),unavailableReasons={reason:reasons.count(reason) for reason in sorted(set(reasons))})
    result["actorCountMeaning"] = "players/npcs count received local actors. Available-only onScreenProjectedActorCandidates intersects projected bounds with the viewport, not pixel visibility through scenery. Null/missing observations are unknown, excluded from values and separately counted; they never establish full projection coverage or zero actors. Native snapshot groups/layers describe submitted ownership workload independently; active projectile actors may be offscreen."
    result["dimensionMeaning"] = "Content viewport is the letterboxed GL draw rectangle. Historical samples named framebufferWidth/Height actually recorded this viewport, not the outer GLFW framebuffer. New surfaceContext explicitly records actual cached GL-thread surface/window dimensions when supported."
    result["manifestReceipt"] = {key:manifest.get(key) for key in ("branch","commit","targetMode","runtime.javaVersion","runtime.javaVendor","runtime.vmName","runtime.osName","runtime.osVersion","runtime.osArch","runtime.availableProcessors")}
    result["cameraContext"] = {key:sorted({row[key] for row in distributions if key in row}) for key in ("camera.zoomSetting","camera.baseZoom","camera.effectiveZoom","camera.pitch","camera.rotation","camera.fogMode","camera.drawDistanceWorldUnits","camera.fogStartDistanceWorldUnits","camera.drawDistanceTiles","camera.fogStartDistanceTiles")}
    result["effectCoverage"] = dict(samples=len(effects), nonzeroSamples=sum(value>0 for value in effects), maxActiveActors=max(effects,default=0),meanActiveActors=sum(effects)/len(effects) if effects else None)
    clocks=[sample["serverState"] for sample in samples if isinstance(sample.get("serverState"),dict)]
    result["serverClockContext"]=dict(rateMultipliers=sorted({clock["cycleRate"] for clock in clocks if "cycleRate" in clock}),cycleLengthsMillis=sorted({clock["cycleLengthMillis"] for clock in clocks if "cycleLengthMillis" in clock}),firstCycleMillis=clocks[0].get("cycleMillis") if clocks else None,lastCycleMillis=clocks[-1].get("cycleMillis") if clocks else None)
    result["scenario"] = scenario
    result["runtimeValidation"] = movement
    result["visiblePreview"] = scenario.get("visiblePreview", False)
    if result["visiblePreview"]:
        result["limitation"] = "Owner-visible private preview. Post-swap interval distributions are evidence; final60FPS acceptance and visual approval require manager review."
    capture_records, capture_partial = analyzer.read_jsonl(capture / "captures/capture-index.jsonl") if (capture / "captures/capture-index.jsonl").is_file() else ([],False)
    completed_captures=sum(row.get("status")=="completed" for row in capture_records); failed_captures=sum(row.get("status")=="failed" for row in capture_records)
    result["captureEvidence"] = dict(requestedBudget=scenario.get("snapshotFrames",12),completedFrames=completed_captures,failedFrames=failed_captures,partialIndex=capture_partial,strictTwelveFrameProof=scenario.get("snapshotFrames",12)==12 and completed_captures==12 and failed_captures==0 and not capture_partial,note="Bounded post-phase fixture capture can cap the ordinary12-frame diagnostic burst to1–2completed frames; this is not a strict12-frame proof.")
    result["roofMaskExport"] = json.loads((capture / "roof-mask.json").read_text()) if (capture / "roof-mask.json").is_file() else None
    result["roofContextMeaning"] = "New roofContext values use the same published frame as actor projection; missing optional frames are null/unknown, not roof counts of zero. Historical raw Scene observations may precede coverage attachment. Exported roofMaskExport is the authoritative immutable GPU receiver mask; do not infer final policy from room walls or unknown observations."
    result["roofContext"] = {key:sorted({sample["roof"][key] for sample in samples if isinstance(sample.get("roof"),dict) and key in sample["roof"]}) for key in ("visibility","residentRoofTriangles","maximumSourceCoveredTiles")}
    if result["phase"] == "lifecycle":
        if not (capture / "SETTINGS-PERSISTED").is_file():raise ValueError("Production settings persistence not verified")
        lifecycle,partial_lifecycle=analyzer.read_jsonl(capture / "lifecycle-events.jsonl")
        if partial_lifecycle or [e.get("event") for e in lifecycle] != ["logout","relogin","logout","relogin"]:raise ValueError("Two ordinary same-JVM relogs not completed")
        if set(context.get("shadowQuality",[])) != {"OFF","LOW","HIGH"}:raise ValueError("Lifecycle did not exercise all shadow qualities")
        if not {0,2}.issubset({s.get("serverState",{}).get("level") for s in samples}):raise ValueError("Native signed-level transition not observed")
        last=samples[-1].get("serverState",{})
        if not all(last.get(k,0)>0 for k in ("samePositionTeleports","areaTeleports","levelTeleports")):raise ValueError("An expected teleport path did not execute")
        window_events,partial_window=analyzer.read_jsonl(capture / "window-events.jsonl")
        if partial_window:raise ValueError("Partial native window lifecycle evidence")
        window_validation=validate_window_lifecycle(window_events)
        result["lifecycleValidation"]={"sameJvmRelogs":2,"shadowQualities":context["shadowQuality"],"nativeLevels":[0,2],"samePositionTeleports":last["samePositionTeleports"],"areaTeleports":last["areaTeleports"],"levelTeleports":last["levelTeleports"],"window":window_validation}
        result["gpu"]={}
        result["limitation"]="Functional same-JVM lifecycle run with deliberate Off/Low/High, teleports and login pauses. Not a steady 60 FPS performance cohort. GPU timer lifetimes reset at relog, so combined GPU distributions are omitted."
    result["gpuSequenceScope"] = ("Lifecycle performs ordinary same-JVM logout/relogin. Shadow timer instances may be recreated; combined GPU distributions and cross-lifetime sequence uniqueness are deliberately omitted." if result["phase"]=="lifecycle" else "Per capture/fresh-client JVM CSV. Separate relog phase opens another JVM/file; it does not establish same-JVM timer-instance uniqueness.")
    result["gpuCoverage"] = dict(farPhaseAttributionVerified=bool(samples) and all(sample.get("gpuFarPollEveryFrame") is True for sample in samples), farAttributionNote="Older engines polled far queries only on cache rebuild, so completion could lag2.5s and1s trim was insufficient. Earlier far phase counts are provisional; rawsamples remain untouched. Verified per-frame polling is required for final phase attribution.", trimAtEachPhaseEdgeNanos=1_000_000_000, reason="GPU queries are read asynchronously; completion timestamps near phase boundaries are excluded to avoid neighboring-work attribution. Raw CSV retains every collected sample.", passes={label:dict(allPhaseUniqueSamples=count, retainedSamples=len(gpu.get(label, [])), phaseEdgeExcludedSamples=count-len(gpu.get(label, []))) for label,count in gpu_phase_counts.items()})
    result["timingMeaning"] = dict(clientLoop="Paced loop intervals; tiny overruns of16.667ms are scheduling jitter, not independently proven missed presentation frames.", openGLRender="CPU submission/render work including swap; compare GPU timeline separately, do not add overlapping CPU/GPU durations.", gpu="Elapsed GPU timeline including possible submission starvation. Far-map samples describe rebuilds only, not every frame.")
    if distributions:
        first = min(telemetry, key=lambda row:abs(row.get("sessionElapsedNanos",0)-start))
        last = min(telemetry, key=lambda row:abs(row.get("sessionElapsedNanos",0)-end))
        result["frameCounters"] = {name:max(0,last.get("openGL."+name+".lifetime",0)-first.get("openGL."+name+".lifetime",0)) for name in ("frames","droppedFrames")}
        result["submittedSprites"] = {key:last.get(key) for key in ("counter.openGLWorldSpriteSnapshotGroup.recent.latest","counter.openGLWorldSpriteSnapshotLayer.recent.latest","counter.openGLWorldSpriteSnapshotCompatibilityFallback.recent.latest")}
    geometry_keys = ("openGLWorldChunk", "openGLWorldChunkTriangle", "openGLWorldChunkDraw", "openGLWorldChunkDrawTriangle", "openGLWorldChunkDrawGameObject", "openGLWorldChunkDrawWallObject", "openGLWorldChunkDrawTerrain", "openGLWorldChunkDrawWall", "openGLWorldChunkDrawRoof", "openGLWorldChunkDrawCall", "openGLWorldSpriteSnapshotGroup", "openGLWorldSpriteSnapshotLayer", "openGLWorldSpriteSnapshotCompatibilityFallback")
    result["sceneInventory"] = {key:dict(min=min(values), max=max(values), last=values[-1]) for key in geometry_keys if (values := [record["counter."+key+".recent.latest"] for record in distributions if "counter."+key+".recent.latest" in record])}
    if result["phase"] == "relog":
        previous=Path(scenario["relogFrom"]).resolve()
        if previous.parent!=run: raise ValueError("Relog receipt is outside this fixture")
        earlier,earlier_partial=analyzer.read_jsonl(previous/"client-samples.jsonl")
        parity={key:dict(before=sorted({sample[key] for sample in earlier if key in sample}),after=context.get(key,[])) for key in ("gameObjects","wallObjects")}
        if earlier_partial or any(not item["before"] or item["before"]!=item["after"] for item in parity.values()):
            raise ValueError("Relog changed actual object population; reject scene parity")
        if not (previous/"benchmark-summary.json").is_file(): raise ValueError("Analyze first login before verifying relog parity")
        earlier_summary=json.loads((previous/"benchmark-summary.json").read_text())
        for key in ("openGLWorldChunk","openGLWorldChunkTriangle"):
            before=earlier_summary.get("sceneInventory",{}).get(key,{})
            after=result["sceneInventory"].get(key,{})
            parity[key]=dict(before=[before.get("min"),before.get("max")],after=[after.get("min"),after.get("max")])
            if not before.get("min") or parity[key]["before"]!=parity[key]["after"]: raise ValueError("Relog changed actual resident geometry; reject scene parity")
        result["relogParity"]=parity
    (capture / "benchmark-summary.json").write_text(json.dumps(result,indent=2)+"\n")
    print(json.dumps({key:result[key] for key in ("phase","durationSeconds","stages","gpu","context","fullClientPresentation60FpsAccepted")},indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__); sub = parser.add_subparsers(dest="action", required=True)
    p = sub.add_parser("prepare"); p.add_argument("--name", required=True); p.add_argument("--client-jar", type=Path, required=True); p.add_argument("--client-source", required=True); p.add_argument("--client-branch", default="unspecified-artifact-branch"); p.add_argument("--server-core", type=Path); p.add_argument("--server-overlay", type=Path); p.add_argument("--server-source"); p.add_argument("--server-artifacts",type=Path,help="Explicit congruent core/plugins/overlay/lib snapshot; requires source receipt"); p.add_argument("--show-roofs",action="store_true"); p.add_argument("--sustain-summons-for-benchmark",action="store_true",help="Private combat-real fixture only: one-time30000HP on its8controlled bears")
    for name in ("start", "stop", "capture", "preview"):
        p = sub.add_parser(name); p.add_argument("run", type=Path)
        if name in ("capture", "preview"):
            p.add_argument("--phase", choices=["dense-idle", "dense-camera", "busy-idle", "busy-camera", "busy-walk", "sun-motion", "night", "transition", "same-position", "relog", "visual", "combat-real", "lifecycle"], default="busy-camera" if name == "preview" else "dense-idle"); p.add_argument("--seconds", type=int, default=60); p.add_argument("--zoom", type=int, choices=[75,900], default=75); p.add_argument("--quality", choices=["off","low","high"], default="high"); p.add_argument("--snapshot", action="store_true", default=name == "preview"); p.add_argument("--snapshot-frames",type=int,choices=[1,12],default=12); p.add_argument("--legacy-mask", action="store_true")
            p.add_argument("--gc-log",action="store_true",help="Retain JVM GC messages in the private console log"); p.add_argument("--fog",choices=["on","off"],default="on"); p.add_argument("--tone-preview",choices=["day","cycle","sunrise-amber","dawn-gold","rose-dusk","cool-night","deep-blue"],default="day"); p.add_argument("--warmup-seconds",type=int,default=15); p.add_argument("--window-mode",choices=["windowed","fullscreen","borderless-fullscreen"],default="borderless-fullscreen"); p.add_argument("--dump-terrain",action="store_true"); p.add_argument("--dump-fences",action="store_true"); p.add_argument("--dump-roof-mask",action="store_true"); p.add_argument("--exclude-caster", choices=["TERRAIN","WALL","ROOF","GAME_OBJECT","WALL_OBJECT"],action="append",default=[]); p.add_argument("--sun-azimuth", type=float, default=135); p.add_argument("--sun-elevation", type=float, default=45); p.add_argument("--rotation", type=int, choices=range(256), default=128); p.add_argument("--x", type=int, default=502); p.add_argument("--y", type=int, default=453)
            p.add_argument("--native-resident-input-cache", choices=["on","off"], help="Explicit native static-input comparison; omitted preserves normal defaults")
    p = sub.add_parser("report"); p.add_argument("capture", type=Path)
    args = parser.parse_args()
    if args.action in ("capture", "preview") and not 5 <= args.warmup_seconds <= 120: parser.error("Warmup must be5..120 seconds")
    if args.action in ("capture", "preview") and not 5 <= args.seconds <= 600: parser.error("Duration must be5..600 seconds")
    if args.action in ("capture", "preview") and not (0 <= args.rotation < 256 and 0 <= args.x < 8192 and 0 <= args.y < 8192 and math.isfinite(args.sun_azimuth) and 0 <= args.sun_azimuth < 360 and math.isfinite(args.sun_elevation) and 5 <= args.sun_elevation <= 85): parser.error("Invalid camera/sun coordinate")
    try: globals()[args.action](args)
    except (ValueError,OSError,subprocess.CalledProcessError) as error: parser.exit(1, str(error)+"\n")


if __name__ == "__main__": main()
