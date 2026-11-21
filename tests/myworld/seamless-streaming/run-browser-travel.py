#!/usr/bin/env python3
"""Run a staged sustained walk in a fresh Firefox WebDriver session."""

import argparse
import contextlib
import json
from pathlib import Path
import subprocess
import time
import urllib.request


ROOT = Path(__file__).resolve().parents[3]
PROOF = "const w=document.querySelector('#runtime')?.contentWindow||window;const p=w.browserProof||{};"
ROUTE_EVENTS = "p.events?.filter(e=>e.message.startsWith('outer-coverage:'))||[]"


def run_walk(run, driver_port):
    server = json.loads((run / 'server-command.json').read_text())
    stage = json.loads((run / 'stage.json').read_text())
    fixture = json.loads((run / 'outer-coverage-fixture.json').read_text())
    if (not fixture['sustainedWalk'] or not stage['http'].startswith('127.0.0.1:')
            or Path(server['cwd']).resolve() != run / 'server' or (run / 'STOP').exists()
            or (run / 'firefox-result.json').exists() or (run / 'server.pid').exists()):
        raise ValueError('Use a fresh private sustained-walk stage')
    url = 'http://' + stage['http'] + '/?mode=webgl'

    def request(path, payload=None, method='POST'):
        data = json.dumps(payload or {}).encode() if method == 'POST' else None
        req = urllib.request.Request(f'http://127.0.0.1:{driver_port}' + path, data=data, method=method,
                                     headers={'Content-Type': 'application/json'})
        with urllib.request.urlopen(req, timeout=55) as response:
            return json.load(response)['value']

    with (run / 'server.log').open('wb') as server_log, (run / 'http.log').open('wb') as http_log:
        private = subprocess.Popen(server['command'], cwd=server['cwd'], stdout=server_log, stderr=subprocess.STDOUT)
        web = subprocess.Popen(['python3', 'scripts/browser-gameplay/serve.py', str(run / 'web'),
                                '--port', stage['http'].rsplit(':', 1)[1]], cwd=ROOT, stdout=http_log,
                               stderr=subprocess.STDOUT)
        session = None
        try:
            for _ in range(100):
                if (run / 'status.json').exists():
                    with contextlib.suppress(OSError):
                        with urllib.request.urlopen(url, timeout=2):
                            break
                if private.poll() is not None or web.poll() is not None:
                    raise RuntimeError('Private fixture did not start')
                time.sleep(.25)
            else:
                raise TimeoutError('Private fixture was not ready')

            firefox = {'args': ['-headless'], 'prefs': {'webgl.force-enabled': True, 'layout.css.devPixelsPerPx': '1'}}
            session = request('/session', {'capabilities': {'alwaysMatch': {
                'browserName': 'firefox', 'moz:firefoxOptions': firefox}}})['sessionId']
            prefix = '/session/' + session
            request(prefix + '/url', {'url': url})
            element = request(prefix + '/element', {'using': 'css selector', 'value': '#start'})
            request(prefix + '/element/' + next(iter(element.values())) + '/click')

            def evaluate(script):
                return request(prefix + '/execute/sync', {'script': PROOF + script, 'args': []})

            deadline = time.monotonic() + 228
            previous = None
            while time.monotonic() < deadline:
                snap = evaluate('return {state:p.state?.mode,reason:p.stopReason,events:' + ROUTE_EVENTS + '};')
                if snap != previous:
                    print(snap, flush=True)
                    previous = snap
                if snap.get('reason') or any(event['message'].startswith(('outer-coverage:failed', 'outer-coverage:complete'))
                                             for event in snap['events']):
                    break
                time.sleep(.85)

            result = evaluate('return {events:' + ROUTE_EVENTS + ''',runtime:p.state, reason:p.stopReason,
                region:p.regionHandoff, travel:p.gpu?.travelTrace, stream:p.gpu?.worldStreamSamples,
                display:p.gpu?.display, visibility:w.document.visibilityState, timeOrigin:w.performance.timeOrigin};''')
            result['serverStatus'] = json.loads((run / 'status.json').read_text())
            (run / 'firefox-result.json').write_text(json.dumps(result, indent=2) + '\n')
            print(result['serverStatus'], result.get('stream'), flush=True)
            completed = any(event['message'] == 'outer-coverage:complete:sustained' for event in result['events'])
            return 0 if completed else 1
        finally:
            if session:
                with contextlib.suppress(OSError):
                    request('/session/' + session, method='DELETE')
            (run / 'STOP').touch()
            web.terminate()
            web.wait(timeout=10)
            private.wait(timeout=30)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run', type=Path)
    parser.add_argument('--driver-port', type=int, default=44495, help='Port of the local Firefox WebDriver')
    args = parser.parse_args()
    return run_walk(args.run.resolve(), args.driver_port)


if __name__ == '__main__':
    raise SystemExit(main())
