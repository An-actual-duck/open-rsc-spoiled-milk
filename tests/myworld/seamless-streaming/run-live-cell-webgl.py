#!/usr/bin/env python3
"""Bake three signed map cells and verify their live publication in Firefox WebGL."""

import argparse
import contextlib
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
import urllib.request


ROOT = Path(__file__).resolve().parents[3]


def stage(run):
    web = run / 'web'
    web.mkdir()
    jar = ROOT / 'Client_Base/Open_RSC_Client.jar'
    with jar.open('rb') as source:
        jar_hash = hashlib.file_digest(source, 'sha256').hexdigest()
    selected = json.loads(subprocess.check_output(
        ['python3', 'scripts/installed-map-selection.py', str(ROOT)], cwd=ROOT))
    cache = run / 'mini-cache'
    with (run / 'textures.log').open('w') as log:
        subprocess.run(['python3', 'tools/browser-gameplay/whole-zone/extract-textures.py',
                        '--source', 'Client_Base/Cache/video/Custom_Sprites.osar',
                        '--destination', str(cache / 'video/Custom_Sprites.osar')], cwd=ROOT, stdout=log, check=True)
    catalog = {'chunks': [], 'objectCells': [], 'clientJarSha256': jar_hash,
               'sourceManifestSha256': selected['manifestSha256']}
    for x in (4, 5, 6):
        bake = run / f'cell-{x}'
        with (run / f'cell-{x}.log').open('w') as log:
            subprocess.run(['java', '-Xmx512m', '-XX:+UseSerialGC', '-Djava.awt.headless=true',
                            '-Dspoiledmilk.browserBenchmark=true', '-Dspoiledmilk.browserRegionBuild=true',
                            '-cp', str(jar), 'orsc.BrowserWholeZoneBaker', str(ROOT), selected['serverPackage'],
                            str(cache), str(bake), jar_hash, '1', f'{x}:12'], cwd=ROOT,
                           stdout=log, stderr=subprocess.STDOUT, check=True)
        manifest = json.loads((bake / 'manifest.json').read_text())
        if manifest['manifestSha256'] != selected['manifestSha256']:
            raise ValueError('Unexpected signed terrain source')
        catalog.setdefault('settings', manifest['settings'])
        if manifest['settings'] != catalog['settings']:
            raise ValueError('Inconsistent cell packing settings')
        cell = next(c for c in manifest['chunks'] if c['plane'] == 0 and c['role'] == 0)
        filename = f'ground-{x}.bin'
        shutil.copyfile(bake / cell['path'], web / filename)
        catalog['chunks'].append(dict(cell, id=1000000 + x, path=filename))
    (web / 'terrain-cells.json').write_text(json.dumps(catalog) + '\n')
    for module in (ROOT / 'tools/browser-gameplay/web').glob('*.js'):
        shutil.copyfile(module, web / module.name)
    shutil.copyfile(Path(__file__).with_name('live-cell-webgl.mjs'), web / 'live-cell-webgl.mjs')
    (web / 'index.html').write_text('<!doctype html><title>Live terrain cell proof</title>'
                                  '<canvas width="296" height="96"></canvas>'
                                  '<script type="module" src="./live-cell-webgl.mjs"></script>')
    return web


def verify(run, web, http_port, driver_port):
    host = f'http://127.0.0.1:{http_port}'
    driver = f'http://127.0.0.1:{driver_port}'

    def request(path, args=None, method='POST'):
        data = json.dumps(args or {}).encode() if method == 'POST' else None
        req = urllib.request.Request(driver + path, data=data, method=method,
                                     headers={'Content-Type': 'application/json'})
        with urllib.request.urlopen(req, timeout=30) as response:
            return json.load(response)['value']

    with (run / 'http.log').open('w') as log:
        server = subprocess.Popen([sys.executable, '-m', 'http.server', str(http_port), '--bind', '127.0.0.1',
                                   '--directory', str(web)], stdout=log, stderr=subprocess.STDOUT)
        session = None
        try:
            for _ in range(40):
                if server.poll() is not None:
                    raise RuntimeError('Private HTTP server did not start')
                try:
                    with urllib.request.urlopen(host, timeout=.5):
                        break
                except OSError:
                    time.sleep(.1)
            else:
                raise TimeoutError('Private HTTP server was not ready')

            options = {'args': ['-headless'], 'prefs': {'webgl.force-enabled': True}}
            session = '/session/' + request('/session', {'capabilities': {'alwaysMatch': {
                'browserName': 'firefox', 'moz:firefoxOptions': options}}})['sessionId']
            request(session + '/url', {'url': host})
            for _ in range(90):
                result = request(session + '/execute/sync', {'script': 'return window.liveCellProof', 'args': []})
                if result and result['done']:
                    (run / 'firefox.json').write_text(json.dumps(result, indent=2) + '\n')
                    print(json.dumps(result))
                    return 0 if result['passed'] else 1
                time.sleep(.5)
            raise TimeoutError('Firefox did not finish the live cell proof')
        finally:
            if session:
                with contextlib.suppress(OSError):
                    request(session, method='DELETE')
            server.terminate()
            server.wait(timeout=10)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('name')
    parser.add_argument('--http-port', type=int, default=8262)
    parser.add_argument('--driver-port', type=int, default=44495)
    args = parser.parse_args()
    if not re.fullmatch(r'[a-zA-Z0-9_-]+', args.name):
        parser.error('Choose a simple fresh run name')
    output = subprocess.check_output(['python3', 'scripts/workspace-layout.py', 'path', 'tests',
                                      'persistent-terrain-cells', '--mkdir'], cwd=ROOT, text=True).strip()
    run = Path(output) / args.name
    run.mkdir()
    return verify(run, stage(run), args.http_port, args.driver_port)


if __name__ == '__main__':
    raise SystemExit(main())
