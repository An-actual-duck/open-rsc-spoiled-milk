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


def insert(path, anchor, lines):
    source = path.read_text()
    if source.count(anchor) != 1:
        raise ValueError(f'Expected one fixture insertion point in {path}')
    path.write_text(source.replace(anchor, lines + '\n' + anchor))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('name')
    parser.add_argument('--http-port', type=int, required=True)
    parser.add_argument('--preupload', action='store_true', help='Stage predicted terrain buffers before the handoff')
    args = parser.parse_args()
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
    (web / 'Cache' / 'port.txt').write_text(f'{TCP_PORT}\n')
    bootstrap = run / 'BrowserBootstrap.java'
    bootstrap.write_bytes((ROOT / 'tools/browser-gameplay/src/orsc/browser/BrowserBootstrap.java').read_bytes())
    insert(bootstrap, '        BrowserPublicAlpha.validate(System.getProperties(),webgl);',
           '        if (publicAlpha) throw new IllegalStateException("Private route fixture only");\n'
           f'        System.setProperty("spoiledmilk.browserPreuploadPredictedTerrain", "{str(args.preupload).lower()}");')
    insert(bootstrap, '        final BrowserStartupDiagnostics startupDiagnostics=',
           '        System.setProperty("spoiledmilk.outerCoverageFixturePassword", "BrowserProof13");\n'
           '        new Thread(BrowserOuterCoverageProbe::run, "outer-coverage-fixture").start();')
    insert(web / 'runtime.html', "    await import('./browser.js');",
           f"    window.browserPrivateTestRoute = {{tcpPort:{TCP_PORT}, websocket:'ws://127.0.0.1:{WS_PORT}/'}};")
    insert(web / 'browser.js', '  Java_orsc_BrowserWebGLBridge_loadingWork:',
           "  Java_orsc_browser_BrowserOuterCoverageProbe_loginReady:()=>window.browserProof.state?.mode==='LOGIN'?1:0,\n"
           "  Java_orsc_browser_BrowserOuterCoverageProbe_worldReady:()=>gpu?.stats.worldReadiness?.phase==='ready'?1:0,")
    subprocess.run([
        'javac', '-source', '8', '-target', '8', '-cp', f'{web / "client.jar"}:{classes}',
        '-d', str(classes), str(bootstrap), str(Path(__file__).with_name('BrowserOuterCoverageProbe.java')),
    ], check=True)
    subprocess.run(['jar', 'cf', str(web / 'browser-spike.jar'), '-C', str(classes), '.'], check=True)
    subprocess.run([
        'python3', str(scripts / 'prepare-server.py'), str(run), '--tcp-port', str(TCP_PORT),
        '--ws-port', str(WS_PORT), '--covered-walking-preview', '--outer-coverage',
    ], cwd=ROOT, check=True)
    staged_hashes = {
        name: hashlib.sha256((web / name).read_bytes()).hexdigest()
        for name in ('browser.js', 'runtime.html', 'browser-spike.jar', 'Cache/port.txt')
    }
    stage_path = run / 'stage.json'
    stage = json.loads(stage_path.read_text())
    stage['tcp'] = f'127.0.0.1:{TCP_PORT}'
    stage['websocket'] = f'127.0.0.1:{WS_PORT}'
    for name in ('browser.js', 'runtime.html'):
        stage['frontendSha256'][name] = staged_hashes[name]
    stage_path.write_text(json.dumps(stage, indent=2) + '\n')
    receipt = {
        'spawn': [239, 609], 'destination': [269, 609], 'port': args.http_port,
        'preupload': args.preupload,
        'tcpPort': TCP_PORT, 'websocketPort': WS_PORT,
        'url': f'http://127.0.0.1:{args.http_port}/?mode=webgl',
        'stagedSha256': staged_hashes,
    }
    (run / 'outer-coverage-fixture.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(run)


if __name__ == '__main__':
    main()
