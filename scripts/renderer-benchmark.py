#!/usr/bin/env python3
"""Prepare and run a disposable, loopback-only native renderer benchmark."""
import argparse
import csv
import datetime
import hashlib
import importlib.util
import json
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


def free_ports():
    for port in PORTS:
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", port))


def classpath(server, classes):
    lib = server / "lib"
    first = ["log4j-api-2.17.0.jar", "log4j-core-2.17.0.jar", "log4j-iostreams-2.17.0.jar", "xpp3-1.1.4c.jar", "mysql-connector-j-9.4.0.jar", "commons-lang-2.6.jar"]
    return os.pathsep.join(map(str, [*(lib / name for name in first), server / "core-gameplay-overlay.jar", server / "core.jar", server / "plugins.jar", lib / "*", classes]))


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
    marker = dict(schema=1, sourceCommit=subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(), clientSourceCommit=args.client_source, clientSha256=sha(client / "client.jar"), seedSha256=sha(seed), seedAccounts=0, mapManifest=selection["manifestSha256"], artifacts=artifacts, private=True, tcp=PORTS[0], websocket=PORTS[1], source=str(ROOT))
    marker["toolSources"] = {p.name:sha(p) for p in sources.iterdir()}
    (run / MARKER).write_text(json.dumps(marker, indent=2) + "\n")
    (run / "server-command.json").write_text(json.dumps(dict(cwd=str(server), argv=["java", "-Xmx2g", "-XX:+UseSerialGC", "-cp", cp, "RendererBenchmarkServer"]), indent=2) + "\n")
    print(run)


def start(args):
    run = marked(args.run); free_ports()
    if (run / "STOP").exists() or (run / "processes.json").exists(): raise ValueError("Use a fresh prepared run")
    command = json.loads((run / "server-command.json").read_text())
    with (run / "server.log").open("ab") as log:
        proc = subprocess.Popen(command["argv"], cwd=command["cwd"], stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    (run / "processes.json").write_text(json.dumps(dict(server=proc.pid), indent=2) + "\n")
    print("Private renderer server PID", proc.pid)


def capture(args):
    run = marked(args.run)
    deadline = time.monotonic() + 60
    while True:
        try:
            with socket.create_connection(("127.0.0.1", PORTS[0]), timeout=1): break
        except OSError:
            if time.monotonic() >= deadline: raise ValueError("Private server did not become ready")
            time.sleep(.25)
    name = args.phase + "-" + datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    capture = run / name; capture.mkdir()
    request = run / "request.next"
    request.write_text(json.dumps(dict(busy=args.phase.startswith("busy"), phase=args.phase)))
    os.replace(request, run / "request.json")
    props = {"spoiledmilk.openglHiddenDiagnostic":"true", "spoiledmilk.openglHiddenWidth":"1920", "spoiledmilk.openglHiddenHeight":"1080", "spoiledmilk.rendererDiagnostics":"true", "spoiledmilk.rendererTelemetry":"true", "spoiledmilk.rendererDiagnosticSessionDir":str(capture), "spoiledmilk.rendererDiagnosticMaxLogBytes":str(128*1024*1024), "spoiledmilk.clientLog":str(capture / "runtime.log"), "spoiledmilk.clientCommit":json.loads((run / MARKER).read_text())["clientSourceCommit"], "spoiledmilk.openglVsync":"false", "renderer.benchmark.seconds":str(args.seconds), "renderer.benchmark.phase":args.phase, "renderer.benchmark.zoom":str(args.zoom), "spoiledmilk.openglRendererProfile":"remaster", "spoiledmilk.shadowQuality":args.quality}
    props["spoiledmilk.shadowGpuTimings"] = "true"
    if args.legacy_mask: props["spoiledmilk.shadowBenchmarkLegacyMask"] = "true"
    if args.phase != "sun-motion":
        props.update({"spoiledmilk.remasterLightAzimuth":"135", "spoiledmilk.remasterLightElevation":"45", "spoiledmilk.openglTonePreview":"day"})
    props.update({"spoiledmilk.openglFrameCapture":str(args.snapshot).lower(), "renderer.benchmark.snapshot":str(args.snapshot).lower(), "spoiledmilk.openglFrameCaptureDir":str(capture / "captures")})
    common_git = Path(subprocess.check_output(["git", "rev-parse", "--path-format=absolute", "--git-common-dir"], cwd=ROOT, text=True).strip())
    gpu_lock = common_git / "ai-workspaces/renderer-gpu-benchmark.lock"
    argv = ["flock", "-n", "-F", str(gpu_lock), "java", "-Xms512m", "-Xmx2g", *["-D"+k+"="+v for k,v in props.items()], "-cp", str(run / "classes") + os.pathsep + str(run / "client/client.jar"), "orsc.RendererBenchmarkClient"]
    (capture / "command.json").write_text(json.dumps(dict(argv=argv, cwd=str(run / "client"), settings=props, hiddenOffscreen=True), indent=2)+"\n")
    environment = {key:value for key,value in os.environ.items() if not key.startswith(("SPOILED_MILK_", "OPENRSC_"))}
    environment.update(SPOILED_MILK_CLIENT_COMMIT=props["spoiledmilk.clientCommit"], SPOILED_MILK_CLIENT_BRANCH="renderer-benchmark-artifact", SPOILED_MILK_CLIENT_TARGET_MODE="private-renderer-benchmark")
    with (capture / "console.log").open("ab") as log:
        proc = subprocess.Popen(argv, cwd=run / "client", env=environment, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    processes = json.loads((run / "processes.json").read_text()); processes["client"] = proc.pid; (run / "processes.json").write_text(json.dumps(processes,indent=2)+"\n")
    print(capture); print("Private hidden client PID", proc.pid)


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
        return dict(count=len(samples), p50ms=analyzer.percentile(samples,.50)/1e6, p95ms=analyzer.percentile(samples,.95)/1e6, p99ms=analyzer.percentile(samples,.99)/1e6, maxms=max(samples)/1e6, meanms=sum(samples)/len(samples)/1e6, over1667ms=sum(value>1e9/60 for value in samples))
    stages = {name:summary(analyzer.stage_samples(distributions, name)) for name in ("clientLoop", "sceneRender", "openGLRender", "openGLWorld")}
    gpu = {}; seen = set(); duplicates = 0
    if (capture / "gpu-samples.csv").is_file():
        with (capture / "gpu-samples.csv").open() as stream:
            for sample in csv.DictReader(stream):
                key = (sample["pass"], int(sample["sequence"]))
                if key in seen: duplicates += 1; continue
                seen.add(key)
                # Completion is read asynchronously. Exclude one second at phase edges.
                if start+1e9 <= int(sample["sessionElapsedNanos"]) <= end-1e9:
                    gpu.setdefault(sample["pass"], []).append(int(sample["gpuNanos"]))
    samples, partial_samples = analyzer.read_jsonl(capture / "client-samples.jsonl")
    keys = ("logicalWidth", "logicalHeight", "framebufferWidth", "framebufferHeight", "zoomSetting", "players", "npcs", "sunAzimuth", "sunElevation", "brightness", "tone")
    context = {key:sorted({sample[key] for sample in samples if key in sample}) for key in keys}
    manifest = json.loads((capture / "manifest.json").read_text())
    result = dict(schema=1, capture=str(capture), artifact=json.loads((run / MARKER).read_text()), phase=start_event["label"], durationSeconds=(end-start)/1e9, partialRecords=partial_telemetry or partial_events or partial_samples, stages=stages, gpu={key:summary(values) for key,values in gpu.items()}, gpuDuplicateSamples=duplicates, context=context, openGL={key:manifest.get("openGL."+key) for key in ("renderer","version","vendor")}, fullClientPresentation60FpsAccepted=False, limitation="Hidden native window: frame-work/GPU workload measurements, not owner-visible presentation cadence or visual approval.")
    (capture / "benchmark-summary.json").write_text(json.dumps(result,indent=2)+"\n")
    print(json.dumps({key:result[key] for key in ("phase","durationSeconds","stages","gpu","context","fullClientPresentation60FpsAccepted")},indent=2))


def main():
    parser = argparse.ArgumentParser(description=__doc__); sub = parser.add_subparsers(dest="action", required=True)
    p = sub.add_parser("prepare"); p.add_argument("--name", required=True); p.add_argument("--client-jar", type=Path, required=True); p.add_argument("--client-source", required=True)
    for name in ("start", "stop", "capture"):
        p = sub.add_parser(name); p.add_argument("run", type=Path)
        if name == "capture":
            p.add_argument("--phase", choices=["dense-idle", "dense-camera", "busy-idle", "busy-camera", "sun-motion", "transition"], default="dense-idle"); p.add_argument("--seconds", type=int, default=60); p.add_argument("--zoom", type=int, choices=[75,900], default=75); p.add_argument("--quality", choices=["off","low","high"], default="high"); p.add_argument("--snapshot", action="store_true"); p.add_argument("--legacy-mask", action="store_true")
    p = sub.add_parser("report"); p.add_argument("capture", type=Path)
    args = parser.parse_args()
    if args.action == "capture" and not 5 <= args.seconds <= 600: parser.error("Duration must be5..600 seconds")
    try: globals()[args.action](args)
    except (ValueError,OSError,subprocess.CalledProcessError) as error: parser.exit(1, str(error)+"\n")


if __name__ == "__main__": main()
