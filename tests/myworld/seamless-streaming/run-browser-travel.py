#!/usr/bin/env python3
"""Run a staged sustained walk in a fresh browser WebDriver session."""

import argparse
import contextlib
import json
import math
from pathlib import Path
import subprocess
import time
import urllib.request


ROOT = Path(__file__).resolve().parents[3]
PROOF = "const w=document.querySelector('#runtime')?.contentWindow||window;const p=w.browserProof||{};"
ROUTE_EVENTS = "p.events?.filter(e=>e.message.startsWith('outer-coverage:'))||[]"


WATCH_BROWSER_FRAMES = """
if (p.state?.mode === 'GAME' && !w.travelBrowserFrames) {
    const browserFrames = w.travelBrowserFrames = {gaps: [], count: 0};
    let previous;
    const sample = stamp => {
        browserFrames.count++;
        if (previous !== undefined && stamp - previous >= 100) {
            browserFrames.gaps.push({from: previous, to: stamp});
            if (browserFrames.gaps.length > 96) browserFrames.gaps.shift();
        }
        previous = stamp;
        w.requestAnimationFrame(sample);
    };
    w.requestAnimationFrame(sample);
}
"""

WATCH_PRESENTATION = """
if (p.state?.mode === 'GAME' && !w.travelPresentation) {
    const samples = w.travelPresentation = {gaps: [], intervals: [], callbackGaps: [],
        frames: 0, holds: 0, blocked: 0, epochs: [], unobservedIntervals: 0};
    let first, last, startingFrame, startingHolds, lastUploadBytes, previousCount, previousCallback;
    const sample = stamp => {
        const events = p.events || [];
        if (first === undefined) {
            const walking = events.find(event => event.message.startsWith('outer-coverage:walking:'));
            if (walking) first = p.started + walking.milliseconds;
        }
        if (events.some(event => event.message.startsWith('outer-coverage:complete:'))) return;
        const gpu = p.gpu;
        if (first !== undefined && gpu?.lastFrame > first) {
            if (previousCallback !== undefined && stamp - previousCallback >= 50) {
                samples.callbackGaps.push(stamp - previousCallback);
                if (samples.callbackGaps.length > 256) samples.callbackGaps.shift();
            }
            previousCallback = stamp;
            if (startingFrame === undefined) {
                startingFrame = gpu.javaWorldFrames;
                startingHolds = gpu.heldWorldFrames;
            }
            const epoch = gpu.worldReadiness.epoch;
            if (!samples.epochs.includes(epoch)) samples.epochs.push(epoch);
            if (w.browserWorldInputAllowed() === false) samples.blocked++;
            samples.frames = gpu.javaWorldFrames - startingFrame + 1;
            samples.holds = gpu.heldWorldFrames - startingHolds;
            if (previousCount !== undefined && gpu.javaWorldFrames > previousCount) {
                const count = gpu.javaWorldFrames - previousCount;
                const intervals = gpu.worldFrameSamples;
                const firstAvailable = Math.max(0, intervals.length - count);
                samples.unobservedIntervals += count - (intervals.length - firstAvailable);
                for (let i = firstAvailable; i < intervals.length; i++) samples.intervals.push(intervals[i]);
                if (samples.intervals.length > 16384) samples.intervals.splice(0, 1024);
            }
            previousCount = gpu.javaWorldFrames;
            if (gpu.lastFrame !== last) {
                if (last !== undefined && gpu.lastFrame - last >= 50) {
                    samples.gaps.push({from: last, to: gpu.lastFrame, geometryUploadBytes: gpu.uploadedBytes - lastUploadBytes,
                        bridgeWork: gpu.javaBridgeWorkSamples.at(-1), javascriptSubmissionMs: gpu.jsSubmissionSamples.at(-1),
                        bridgeSampleCurrent: gpu.javaWorldFrames === gpu.javaBridgeReportedWorldFrames});
                    if (samples.gaps.length > 256) samples.gaps.shift();
                }
                last = gpu.lastFrame;
                lastUploadBytes = gpu.uploadedBytes;
            }
        }
        w.requestAnimationFrame(sample);
    };
    w.requestAnimationFrame(sample);
}
"""


def summarize_walking(result):
    """Measure fresh WebGL submissions over the real walk, including time between the legs."""
    rows = (result.get('travel') or {}).get('rows', [])
    walks = [event['milliseconds'] for event in result['events'] if event['message'].startswith('outer-coverage:walking:')]
    arrivals = [event['milliseconds'] for event in result['events'] if event['message'].startswith('outer-coverage:arrived:')]
    if not rows or not walks or not arrivals:
        return None
    origin = result['timeOrigin']
    first = origin + walks[0]
    last = origin + arrivals[-1]
    walk_rows = [row for row in rows if first <= row['time'] <= last]
    frames = [row for row in walk_rows if row['kind'] == 'frame']
    largest_visible_step = max((math.dist(before['shown'], after['shown']) / 128
                                for before, after in zip(frames, frames[1:])), default=0)
    intervals = sorted(
        ({'ms': round(after['submittedAt'] - before['submittedAt'], 1),
          'from': before['receipt'], 'to': after['receipt'], 'connection': after['connectionWork'],
          'geometryMs': after['work'][2]}
         for before, after in zip(frames, frames[1:])),
        key=lambda gap: gap['ms'], reverse=True)
    return {'frames': len(frames),
            'largestVisibleStepTiles': round(largest_visible_step, 3),
            'holds': sum(row['kind'] == 'hold' for row in walk_rows),
            'blocked': sum(row.get('inputAllowed') is False for row in walk_rows),
            'transitions': sum(row['kind'] == 'transition' for row in walk_rows),
            'slowestFrameGaps': intervals[:8]}


def run_walk(run, driver_port, browser, wasmgc=False):
    server = json.loads((run / 'server-command.json').read_text())
    stage = json.loads((run / 'stage.json').read_text())
    fixture = json.loads((run / 'outer-coverage-fixture.json').read_text())
    capture = run / (browser + '-result.json')
    if (fixture['manual'] or not (fixture['sustainedWalk'] or fixture.get('persistentMovement'))
            or not stage['http'].startswith('127.0.0.1:')
            or Path(server['cwd']).resolve() != run / 'server' or (run / 'STOP').exists()
            or capture.exists() or (run / 'server.pid').exists()):
        raise ValueError('Use a fresh private sustained-walk stage')
    url = 'http://' + stage['http'] + '/?mode=webgl'
    if wasmgc:
        url += '&wasmgc=1'

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

            capabilities = {'browserName': browser}
            if browser == 'firefox':
                capabilities['moz:firefoxOptions'] = {'args': ['-headless'],
                    'prefs': {'webgl.force-enabled': True, 'layout.css.devPixelsPerPx': '1'}}
            else:
                capabilities['goog:chromeOptions'] = {'args': ['--headless', '--window-size=1280,900',
                    '--use-gl=angle', '--use-angle=gl']}
            started = request('/session', {'capabilities': {'alwaysMatch': capabilities}})
            session = started['sessionId']
            prefix = '/session/' + session
            request(prefix + '/url', {'url': url})
            element = request(prefix + '/element', {'using': 'css selector', 'value': '#start'})
            request(prefix + '/element/' + next(iter(element.values())) + '/click')

            def evaluate(script):
                return request(prefix + '/execute/sync', {'script': PROOF + script, 'args': []})

            deadline = time.monotonic() + 228
            previous_state = None
            reported_events = 0
            watch = WATCH_PRESENTATION if fixture.get('presentationOnly') else WATCH_BROWSER_FRAMES
            while time.monotonic() < deadline:
                snap = evaluate(watch + 'return {state:p.state?.mode,reason:p.stopReason,events:' + ROUTE_EVENTS + '};')
                state = (snap.get('state'), snap.get('reason'))
                if state != previous_state:
                    print(state, flush=True)
                    previous_state = state
                for event in snap['events'][reported_events:]:
                    print(event, flush=True)
                reported_events = len(snap['events'])
                if snap.get('reason') or any(event['message'].startswith(('outer-coverage:failed', 'outer-coverage:complete'))
                                             for event in snap['events']):
                    break
                time.sleep(.85)

            result = evaluate('return {events:' + ROUTE_EVENTS + ''',browserEvents:p.events,runtime:p.state, reason:p.stopReason,
                region:p.regionHandoff, travel:p.gpu?.travelTrace, stream:p.gpu?.worldStreamSamples, terrainWorker:p.terrainWorker,surfaceMeshWorker:p.surfaceMeshWorker,sceneryWorker:p.surfaceSceneryMeshWorker,renderWorker:p.gpu?.renderWorker,
                graphics:{vendor:p.gpu?.vendor,renderer:p.gpu?.renderer,context:p.gpu?.contextAttributes},
                atlas:{plans:p.gpu?.spritePlanCalls,commits:p.gpu?.spriteCommitCalls,uploadedBytes:p.gpu?.spriteUploadedBytes},
                inlineWorldProgressFrames:p.gpu?.inlineWorldProgressFrames,inlineOverlayFrames:p.gpu?.inlineOverlayFrames,
                geometry:{uploadedBytes:p.gpu?.uploadedBytes,repacks:p.gpu?.repackedChunks},
                javaRuntime:{version:p.javaVersion,release:p.runtimeVersion,wasmGC:p.wasmGC},
                display:p.gpu?.display, visibility:w.document.visibilityState, browserFrames:w.travelBrowserFrames,
                presentation:w.travelPresentation,timeOrigin:w.performance.timeOrigin};''')
            route = 'persistent-movement' if fixture.get('persistentMovement') else 'sustained'
            completed = any(event['message'] == 'outer-coverage:complete:' + route for event in result['events'])
            server_status = json.loads((run / 'status.json').read_text())
            if completed and fixture.get('persistentMovement'):
                arrival = next(event['message'] for event in reversed(result['events'])
                               if event['message'].startswith('outer-coverage:arrived:'))
                destination = tuple(map(int, arrival.rsplit(':', 1)[1].split(',')))
                for _ in range(30):
                    server_status = json.loads((run / 'status.json').read_text())
                    if (server_status.get('x'), server_status.get('y')) == destination:
                        break
                    time.sleep(.1)
                else:
                    completed = False
            result['serverStatus'] = server_status
            result['browser'] = {key: started['capabilities'].get(key) for key in ('browserName', 'browserVersion')}
            result['walkingSummary'] = summarize_walking(result)
            if completed and fixture.get('liveSurfaceHandoff'):
                if fixture.get('presentationOnly'):
                    observed = result.get('presentation') or {}
                    completed = bool(observed.get('frames') and len(observed['epochs']) == 1
                                     and not observed['blocked'] and not observed['holds'] and not result.get('stream'))
                else:
                    walking = result['walkingSummary']
                    completed = bool(walking and walking['frames'] and not any(
                        walking[key] for key in ('holds', 'blocked', 'transitions')))
            if completed and fixture.get('surfaceMeshWorker'):
                detached = result.get('surfaceMeshWorker') or {}
                completed = detached.get('completed', 0) > 0 and detached.get('error') is None
            if completed and fixture.get('surfaceDrawWorker'):
                completed = (result.get('renderWorker') or {}).get('lastCompletedFrame', 0) > 0
            presentation = result.get('presentation')
            if presentation is not None:
                intervals = sorted(presentation['intervals'])
                if intervals:
                    presentation['intervalSummary'] = {
                        'samples': len(intervals),
                        'medianMs': round(intervals[len(intervals) // 2], 1),
                        'p95Ms': round(intervals[math.ceil(len(intervals) * .95) - 1], 1),
                        'p99Ms': round(intervals[math.ceil(len(intervals) * .99) - 1], 1),
                        'maxMs': round(intervals[-1], 1),
                        'over100Ms': sum(interval >= 100 for interval in intervals),
                    }
                print({key: value for key, value in presentation.items()
                       if key not in ('gaps', 'intervals', 'callbackGaps')},
                      'largest sampled frame gap:',
                      round(max((g['to'] - g['from'] for g in presentation['gaps']), default=0), 1), flush=True)
            capture.write_text(json.dumps(result, indent=2) + '\n')
            print(result['serverStatus'], result['walkingSummary'], flush=True)
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
    parser.add_argument('--browser', choices=('firefox', 'chrome'), default='firefox')
    parser.add_argument('--driver-port', type=int, help='Port of the local WebDriver')
    parser.add_argument('--wasmgc', action='store_true', help='Test the experimental CheerpJ 5.0 engine')
    args = parser.parse_args()
    driver_port = args.driver_port if args.driver_port is not None else {'firefox': 44495, 'chrome': 9515}[args.browser]
    return run_walk(args.run.resolve(), driver_port, args.browser, args.wasmgc)


if __name__ == '__main__':
    raise SystemExit(main())
