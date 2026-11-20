#!/usr/bin/env python3
"""Stage a private browser build that walks the outer coverage route once ready."""

import argparse
import hashlib
import json
from pathlib import Path
import subprocess


ROOT = Path(__file__).resolve().parents[3]
TCP_PORT = 43639
WS_PORT = 43539
TRACE_TCP_PORT = 43669
TRACE_WS_PORT = 43569
MANUAL_TCP_PORT = 43679
MANUAL_WS_PORT = 43579


def insert(path, anchor, lines):
    replace(path, anchor, lines + '\n' + anchor)

def replace(path, anchor, text):
    source = path.read_text()
    if source.count(anchor) != 1:
        raise ValueError(f'Expected one fixture substitution in {path}')
    path.write_text(source.replace(anchor, text))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('name')
    parser.add_argument('--http-port', type=int, required=True)
    parser.add_argument('--preupload', action='store_true', help='Stage predicted terrain buffers before the handoff')
    parser.add_argument('--trace', action='store_true', help='Trace the Java handoff on separate private ports')
    parser.add_argument('--predicted-scenery', action='store_true', help='Prebuild the predicted outer scenery strip')
    parser.add_argument('--manual', action='store_true', help='Keep the route available for a manual login and walk')
    parser.add_argument('--tcp-port', type=int, help='Override the private game port together with --ws-port')
    parser.add_argument('--ws-port', type=int, help='Override the private WebSocket port together with --tcp-port')
    args = parser.parse_args()
    tcp_port, ws_port = (TRACE_TCP_PORT, TRACE_WS_PORT) if args.trace else (TCP_PORT, WS_PORT)
    if args.manual:
        tcp_port, ws_port = MANUAL_TCP_PORT, MANUAL_WS_PORT
    if args.tcp_port is not None or args.ws_port is not None:
        if (None in (args.tcp_port, args.ws_port) or args.tcp_port == args.ws_port
                or not all(1024 <= port <= 65535 for port in (args.tcp_port, args.ws_port))):
            parser.error('Supply two different nonprivileged loopback ports')
        tcp_port, ws_port = args.tcp_port, args.ws_port
    scripts = ROOT / 'scripts/browser-gameplay'
    category = Path(subprocess.check_output([
        'python3', str(ROOT / 'scripts/workspace-layout.py'), 'path',
        'tests', 'browser-gameplay-spike', '--mkdir',
    ], text=True).strip())
    subprocess.run([
        'python3', str(scripts / 'prepare.py'), '--name', args.name,
        '--http-port', str(args.http_port),
    ], cwd=ROOT, check=True, stdout=subprocess.DEVNULL)

    run = category / args.name
    web = run / 'web'
    classes = run / 'classes'
    (web / 'Cache' / 'port.txt').write_text(f'{tcp_port}\n')
    bootstrap = run / 'BrowserBootstrap.java'
    bootstrap.write_bytes((ROOT / 'tools/browser-gameplay/src/orsc/browser/BrowserBootstrap.java').read_bytes())
    insert(bootstrap, '        BrowserPublicAlpha.validate(System.getProperties(),webgl);',
           '        if (publicAlpha) throw new IllegalStateException("Private route fixture only");\n'
           f'        System.setProperty("spoiledmilk.browserPreuploadPredictedTerrain", "{str(args.preupload).lower()}");\n'
           f'        System.setProperty("spoiledmilk.browserRegionHandoffTrace", "{str(args.trace).lower()}");\n'
           f'        System.setProperty("spoiledmilk.predictedSceneryPreview", "{str(args.predicted_scenery).lower()}");')
    if not args.manual:
        insert(bootstrap, '        final BrowserStartupDiagnostics startupDiagnostics=',
               '        System.setProperty("spoiledmilk.outerCoverageFixturePassword", "BrowserProof13");\n'
               '        new Thread(BrowserOuterCoverageProbe::run, "outer-coverage-fixture").start();')
    insert(web / 'runtime.html', "    await import('./browser.js');",
           f"    window.browserPrivateTestRoute = {{tcpPort:{tcp_port}, websocket:'ws://127.0.0.1:{ws_port}/'}};")
    if (tcp_port, ws_port) != (TCP_PORT, WS_PORT):
        replace(bootstrap, '&&!"43659".equals(stagedPort)',
                f'&&!"43659".equals(stagedPort)&&!"{tcp_port}".equals(stagedPort)')
        replace(web / 'browser-repeatable-policy.js', "[43659,'ws://127.0.0.1:43559/']",
                f"[43659,'ws://127.0.0.1:43559/'],[{tcp_port},'ws://127.0.0.1:{ws_port}/']")
    if args.trace:
        replace(web / 'browser.js', 'createRegionHandoffTraceCollector({enabled:loadProfilePolicy.requested,',
                'createRegionHandoffTraceCollector({enabled:!deployment.publicAlpha,')
    java_sources = [str(bootstrap)]
    if not args.manual:
        insert(web / 'browser.js', '  Java_orsc_BrowserWebGLBridge_loadingWork:',
               "  Java_orsc_browser_BrowserOuterCoverageProbe_loginReady:()=>window.browserProof.state?.mode==='LOGIN'\n"
               "    && window.browserProof.events.some(event=>event.message==='WebSocket closed')?1:0,\n"
               "  Java_orsc_browser_BrowserOuterCoverageProbe_worldReady:()=>gpu?.stats.worldReadiness?.phase==='ready'?1:0,")
        java_sources.append(str(Path(__file__).with_name('BrowserOuterCoverageProbe.java')))
    subprocess.run([
        'javac', '-source', '8', '-target', '8', '-cp', f'{web / "client.jar"}:{classes}',
        '-d', str(classes), *java_sources,
    ], check=True)
    subprocess.run(['jar', 'cf', str(web / 'browser-spike.jar'), '-C', str(classes), '.'], check=True)
    subprocess.run([
        'python3', str(scripts / 'prepare-server.py'), str(run), '--tcp-port', str(tcp_port),
        '--ws-port', str(ws_port), '--covered-walking-preview', '--outer-coverage',
        *(['--predicted-scenery'] if args.predicted_scenery else []),
    ], cwd=ROOT, check=True)
    staged_hashes = {
        name: hashlib.sha256((web / name).read_bytes()).hexdigest()
        for name in ('browser.js', 'runtime.html', 'browser-repeatable-policy.js', 'browser-spike.jar', 'Cache/port.txt')
    }
    stage_path = run / 'stage.json'
    stage = json.loads(stage_path.read_text())
    stage['tcp'] = f'127.0.0.1:{tcp_port}'
    stage['websocket'] = f'127.0.0.1:{ws_port}'
    for name in ('browser.js', 'runtime.html', 'browser-repeatable-policy.js'):
        stage['frontendSha256'][name] = staged_hashes[name]
    stage_path.write_text(json.dumps(stage, indent=2) + '\n')
    receipt = {
        'spawn': [239, 609], 'destination': [269, 609], 'port': args.http_port,
        'preupload': args.preupload, 'trace': args.trace, 'manual': args.manual,
        'predictedScenery': args.predicted_scenery,
        'tcpPort': tcp_port, 'websocketPort': ws_port,
        'url': f'http://127.0.0.1:{args.http_port}/?mode=webgl',
        'stagedSha256': staged_hashes,
    }
    (run / 'outer-coverage-fixture.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(run)


if __name__ == '__main__':
    main()
