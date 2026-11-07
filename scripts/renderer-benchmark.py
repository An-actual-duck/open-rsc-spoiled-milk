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
    artifacts = []
    for source in [*(ROOT / "server" / n for n in ("core.jar", "plugins.jar", "core-gameplay-overlay.jar")), *sorted((ROOT / "server/lib").glob("*.jar"))]:
        relative = source.relative_to(ROOT / "server"); target = server / relative; target.parent.mkdir(exist_ok=True)
        shutil.copy2(source, target); artifacts.append(dict(path=str(relative), sha256=sha(target)))
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
    marker = dict(schema=1, sourceCommit=subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(), clientSourceCommit=resolve_source(args.client_source), clientSha256=sha(client / "client.jar"), seedSha256=sha(seed), seedAccounts=0, mapManifest=selection["manifestSha256"], artifacts=artifacts, private=True, tcp=PORTS[0], websocket=PORTS[1], source=str(ROOT))
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


def capture(args):
    run = marked(args.run); validate_artifacts(run)
    visible = args.action == "preview"
    if any(run.glob("*/command.json")):
        raise ValueError("Use one fresh server fixture per capture; reconnect geometry parity is not assumed")
    processes = json.loads((run / "processes.json").read_text())
    old_client = Path("/proc") / str(processes.get("client", 0))
    if old_client.exists() and b"orsc.RendererBenchmarkClient" in (old_client / "cmdline").read_bytes():
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
    (capture / "scenario.json").write_text(json.dumps(dict(phase=args.phase, requestedViewer=[args.x,args.y,0], zoom=args.zoom, initialRotation=args.rotation, quality=args.quality, legacyMask=args.legacy_mask, sunAzimuth=args.sun_azimuth, sunElevation=args.sun_elevation, normalRateServerSun=args.phase == "sun-motion", visiblePreview=visible),indent=2)+"\n")
    request = run / "request.next"
    request.write_text(json.dumps(dict(busy=args.phase.startswith("busy") or args.phase == "sun-motion", phase=args.phase, x=args.x, y=args.y)))
    os.replace(request, run / "request.json")
    props = {"spoiledmilk.openglHiddenDiagnostic":"true", "spoiledmilk.openglHiddenWidth":"1920", "spoiledmilk.openglHiddenHeight":"1080", "spoiledmilk.rendererDiagnostics":"true", "spoiledmilk.rendererTelemetry":"true", "spoiledmilk.rendererDiagnosticSessionDir":str(capture), "spoiledmilk.rendererDiagnosticMaxLogBytes":str(128*1024*1024), "spoiledmilk.clientLog":str(capture / "runtime.log"), "spoiledmilk.clientCommit":json.loads((run / MARKER).read_text())["clientSourceCommit"], "spoiledmilk.openglVsync":"false", "renderer.benchmark.seconds":str(args.seconds), "renderer.benchmark.phase":args.phase, "renderer.benchmark.zoom":str(args.zoom), "spoiledmilk.openglRendererProfile":"remaster", "spoiledmilk.shadowQuality":args.quality}
    props["renderer.benchmark.visiblePreview"] = str(visible).lower()
    props["spoiledmilk.openglHiddenDiagnostic"] = str(not visible).lower()
    if visible: props["spoiledmilk.openglWindowMode"] = "borderless-fullscreen"
    props["spoiledmilk.shadowGpuTimings"] = "true"
    props["renderer.benchmark.rotation"] = str(args.rotation)
    if args.legacy_mask: props["spoiledmilk.shadowBenchmarkLegacyMask"] = "true"
    if args.phase != "sun-motion":
        props.update({"spoiledmilk.remasterLightAzimuth":str(args.sun_azimuth), "spoiledmilk.remasterLightElevation":str(args.sun_elevation), "spoiledmilk.openglTonePreview":"day"})
    props.update({"spoiledmilk.openglFrameCapture":str(args.snapshot).lower(), "renderer.benchmark.snapshot":str(args.snapshot).lower(), "spoiledmilk.openglFrameCaptureDir":str(capture / "captures")})
    common_git = Path(subprocess.check_output(["git", "rev-parse", "--path-format=absolute", "--git-common-dir"], cwd=ROOT, text=True).strip())
    gpu_lock = common_git / "ai-workspaces/renderer-gpu-benchmark.lock"
    argv = ["flock", "-n", "-F", str(gpu_lock), "java", "-Xms512m", "-Xmx2g", *["-D"+k+"="+v for k,v in props.items()], "-cp", str(run / "classes") + os.pathsep + str(run / "client/client.jar"), "orsc.RendererBenchmarkClient"]
    (capture / "command.json").write_text(json.dumps(dict(argv=argv, cwd=str(run / "client"), settings=props, hiddenOffscreen=not visible, initialHostLoadAverage=os.getloadavg()), indent=2)+"\n")
    environment = {key:value for key,value in os.environ.items() if not key.startswith(("SPOILED_MILK_", "OPENRSC_"))}
    environment.update(SPOILED_MILK_CLIENT_COMMIT=props["spoiledmilk.clientCommit"], SPOILED_MILK_CLIENT_BRANCH="renderer-benchmark-artifact", SPOILED_MILK_CLIENT_TARGET_MODE="private-renderer-benchmark")
    with (capture / "console.log").open("ab") as log:
        proc = subprocess.Popen(argv, cwd=run / "client", env=environment, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    time.sleep(.1)
    if proc.poll() is not None: raise ValueError("Private client refused startup/GPU lock; inspect preserved console log")
    processes = json.loads((run / "processes.json").read_text()); processes["client"] = proc.pid; (run / "processes.json").write_text(json.dumps(processes,indent=2)+"\n")
    print(capture); print("Private " + ("visible preview" if visible else "hidden") + " client PID", proc.pid)


def preview(args):
    """Explicitly open an owner-visible, automatically closed private test client."""
    capture(args)


def stop(args):
    run = marked(args.run); pids = json.loads((run / "processes.json").read_text())
    client = Path("/proc") / str(pids.get("client", 0))
    if client.exists():
        if (client / "cwd").resolve() != run / "client" or b"orsc.RendererBenchmarkClient" not in (client / "cmdline").read_bytes(): raise ValueError("Client PID ownership mismatch")
        os.kill(pids["client"], signal.SIGTERM)
    server = Path("/proc") / str(pids["server"])
    if server.exists():
        if (server / "cwd").resolve() != run / "server" or b"RendererBenchmarkServer" not in (server / "cmdline").read_bytes(): raise ValueError("Server PID ownership mismatch")
        (run / "STOP").write_text("Private renderer benchmark graceful shutdown\n")
        for _ in range(100):
            if not (run / "server.pid").exists(): break
            time.sleep(.2)
        else: raise ValueError("Private graceful shutdown did not finish; no signal sent")
    print("Stopped own private processes; all data retained:", run)


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
    keys = ("logicalWidth", "logicalHeight", "framebufferWidth", "framebufferHeight", "zoomSetting", "players", "npcs", "sunAzimuth", "sunElevation", "brightness", "tone", "lighting", "profile", "geometry", "activeProjectileActors", "windowState", "gameObjects", "wallObjects")
    context = {key:sorted({sample[key] for sample in samples if key in sample}) for key in keys}
    manifest = json.loads((capture / "manifest.json").read_text())
    result = dict(schema=1, capture=str(capture), artifact=json.loads((run / MARKER).read_text()), phase=start_event["label"], durationSeconds=(end-start)/1e9, partialRecords=partial_telemetry or partial_events or partial_samples, stages=stages, gpu={key:summary(values) for key,values in gpu.items()}, gpuDuplicateSamples=duplicates, context=context, openGL={key:manifest.get("openGL."+key) for key in ("renderer","version","vendor")}, fullClientPresentation60FpsAccepted=False, limitation="Hidden native window: frame-work/GPU workload measurements, not owner-visible presentation cadence or visual approval.")
    scenario = json.loads((capture / "scenario.json").read_text()) if (capture / "scenario.json").is_file() else {}
    result["visiblePreview"] = scenario.get("visiblePreview", False)
    if result["visiblePreview"]:
        result["limitation"] = "Owner-visible private preview. Post-swap interval distributions are evidence; final60FPS acceptance and visual approval require manager review."
    result["gpuCoverage"] = dict(trimAtEachPhaseEdgeNanos=1_000_000_000, reason="GPU queries are read asynchronously; completion timestamps near phase boundaries are excluded to avoid neighboring-work attribution. Raw CSV retains every collected sample.", passes={label:dict(allPhaseUniqueSamples=count, retainedSamples=len(gpu.get(label, [])), phaseEdgeExcludedSamples=count-len(gpu.get(label, []))) for label,count in gpu_phase_counts.items()})
    result["timingMeaning"] = dict(clientLoop="Paced loop intervals; tiny overruns of16.667ms are scheduling jitter, not independently proven missed presentation frames.", openGLRender="CPU submission/render work including swap; compare GPU timeline separately, do not add overlapping CPU/GPU durations.", gpu="Elapsed GPU timeline including possible submission starvation. Far-map samples describe rebuilds only, not every frame.")
    if distributions:
        first = min(telemetry, key=lambda row:abs(row.get("sessionElapsedNanos",0)-start))
        last = min(telemetry, key=lambda row:abs(row.get("sessionElapsedNanos",0)-end))
        result["frameCounters"] = {name:max(0,last.get("openGL."+name+".lifetime",0)-first.get("openGL."+name+".lifetime",0)) for name in ("frames","droppedFrames")}
        result["submittedSprites"] = {key:last.get(key) for key in ("counter.openGLWorldSpriteSnapshotGroup.recent.latest","counter.openGLWorldSpriteSnapshotLayer.recent.latest","counter.openGLWorldSpriteSnapshotCompatibilityFallback.recent.latest")}
    geometry_keys = ("openGLWorldChunk", "openGLWorldChunkTriangle", "openGLWorldChunkDraw", "openGLWorldChunkDrawTriangle", "openGLWorldChunkDrawGameObject", "openGLWorldChunkDrawWallObject", "openGLWorldChunkDrawTerrain", "openGLWorldChunkDrawWall", "openGLWorldChunkDrawCall", "openGLWorldSpriteSnapshotGroup", "openGLWorldSpriteSnapshotLayer", "openGLWorldSpriteSnapshotCompatibilityFallback")
    result["sceneInventory"] = {key:dict(min=min(values), max=max(values), last=values[-1]) for key in geometry_keys if (values := [record["counter."+key+".recent.latest"] for record in distributions if "counter."+key+".recent.latest" in record])}
    (capture / "benchmark-summary.json").write_text(json.dumps(result,indent=2)+"\n")
    print(json.dumps({key:result[key] for key in ("phase","durationSeconds","stages","gpu","context","fullClientPresentation60FpsAccepted")},indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__); sub = parser.add_subparsers(dest="action", required=True)
    p = sub.add_parser("prepare"); p.add_argument("--name", required=True); p.add_argument("--client-jar", type=Path, required=True); p.add_argument("--client-source", required=True)
    for name in ("start", "stop", "capture", "preview"):
        p = sub.add_parser(name); p.add_argument("run", type=Path)
        if name in ("capture", "preview"):
            p.add_argument("--phase", choices=["dense-idle", "dense-camera", "busy-idle", "busy-camera", "busy-walk", "sun-motion", "transition", "visual"], default="busy-camera" if name == "preview" else "dense-idle"); p.add_argument("--seconds", type=int, default=60); p.add_argument("--zoom", type=int, choices=[75,900], default=75); p.add_argument("--quality", choices=["off","low","high"], default="high"); p.add_argument("--snapshot", action="store_true", default=name == "preview"); p.add_argument("--legacy-mask", action="store_true")
            p.add_argument("--sun-azimuth", type=float, default=135); p.add_argument("--sun-elevation", type=float, default=45); p.add_argument("--rotation", type=int, choices=range(256), default=128); p.add_argument("--x", type=int, default=502); p.add_argument("--y", type=int, default=453)
    p = sub.add_parser("report"); p.add_argument("capture", type=Path)
    args = parser.parse_args()
    if args.action in ("capture", "preview") and not 5 <= args.seconds <= 600: parser.error("Duration must be5..600 seconds")
    if args.action in ("capture", "preview") and not (0 <= args.rotation < 256 and 0 <= args.x < 8192 and 0 <= args.y < 8192 and math.isfinite(args.sun_azimuth) and 0 <= args.sun_azimuth < 360 and math.isfinite(args.sun_elevation) and 5 <= args.sun_elevation <= 85): parser.error("Invalid camera/sun coordinate")
    try: globals()[args.action](args)
    except (ValueError,OSError,subprocess.CalledProcessError) as error: parser.exit(1, str(error)+"\n")


if __name__ == "__main__": main()
