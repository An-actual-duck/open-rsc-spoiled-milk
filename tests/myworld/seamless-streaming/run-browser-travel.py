#!/usr/bin/env python3
"""Run a staged sustained walk in a fresh browser WebDriver session."""

import argparse
from collections import Counter
import contextlib
import json
import math
from pathlib import Path
import subprocess
import time
import urllib.error
import urllib.request


ROOT = Path(__file__).resolve().parents[3]
PROOF = "const w=document.querySelector('#runtime')?.contentWindow||window;const p=w.browserProof||{};"
ROUTE_EVENTS = "p.events?.filter(e=>e.message.startsWith('outer-coverage:'))||[]"
OUTPUT_DIMENSIONS = ('logicalWidth', 'logicalHeight', 'width', 'height')
PROJECTION_POINTERS = ((.25, .3), (.5, .5), (.7, .6), (.3, .68), (.52, .37), (.72, .28))


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
        frames: 0, holds: 0, blocked: 0, epochs: [], outputSizes: [], unobservedIntervals: 0};
    let first, last, startingFrame, startingHolds, lastUploadBytes, previousCount, previousCallback, lastOutput;
    let lastPanelFrame = 0;
    const sample = stamp => {
        const events = p.events || [];
        if (first === undefined) {
            const walking = events.find(event => event.message.startsWith('outer-coverage:walking:'));
            if (walking) samples.startedAt = first = p.started + walking.milliseconds;
        }
        const complete = events.find(event => event.message === 'outer-coverage:walking-window-ended'
            || event.message.startsWith('outer-coverage:complete:'));
        if (complete) {
            samples.endedAt = p.started + complete.milliseconds;
            return;
        }
        const gpu = p.gpu;
        if (first === undefined && gpu?.frameStageSamples?.length) {
            const marker = events.findLast(event => event.message.startsWith('outer-coverage:ui-profile:'));
            const panel = marker?.message.split(':').at(-1);
            if (panel && panel !== 'done') {
                const frame = gpu.frameStageSamples.at(-1);
                const hasInventory = frame.inventoryDrawMs >= 0;
                const hasMinimap = frame.minimapDrawMs >= 0;
                if (frame.worldFrame > lastPanelFrame && frame.submittedAt >= p.started + marker.milliseconds + 300 &&
                    hasInventory === (panel === 'inventory') && hasMinimap === (panel === 'minimap')) {
                    const profiles = samples.panelProfiles ??= {};
                    const captured = profiles[panel] ??= [];
                    if (captured.length < 600) captured.push(frame);
                    lastPanelFrame = frame.worldFrame;
                }
            }
        }
        if (first !== undefined && gpu?.lastFrame > first) {
            const output = gpu.physicalOutput;
            if (output && (!lastOutput || output.logicalWidth !== lastOutput.logicalWidth ||
                output.logicalHeight !== lastOutput.logicalHeight || output.width !== lastOutput.width ||
                output.height !== lastOutput.height)) {
                lastOutput = {logicalWidth: output.logicalWidth, logicalHeight: output.logicalHeight,
                    width: output.width, height: output.height};
                samples.outputSizes.push(lastOutput);
            }
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
                    const stages = gpu.frameStageSamples?.at(-1);
                    samples.gaps.push({from: last, to: gpu.lastFrame, geometryUploadBytes: gpu.uploadedBytes - lastUploadBytes,
                        bridgeWork: gpu.javaBridgeWorkSamples.at(-1), javascriptSubmissionMs: gpu.jsSubmissionSamples.at(-1),
                        overlayStageMs: gpu.overlayStageSamples?.at(-1),
                        frameStages: stages?.worldFrame === gpu.javaWorldFrames ? stages : undefined,
                        bridgeSampleCurrent: gpu.javaWorldFrames === gpu.javaBridgeReportedWorldFrames});
                    if (samples.gaps.length > 256) samples.gaps.shift();
                }
                samples.lastFrameAt = last = gpu.lastFrame;
                lastUploadBytes = gpu.uploadedBytes;
            }
        }
        w.requestAnimationFrame(sample);
    };
    w.requestAnimationFrame(sample);
}
"""


def summarize_frame_rate(intervals):
    """Count new presentations in successive complete seconds of measured travel."""
    elapsed = 0
    frames_per_second = {}
    for interval in intervals:
        second = int(elapsed // 1000)
        frames_per_second[second] = frames_per_second.get(second, 0) + 1
        elapsed += interval
    rates = sorted(frames_per_second.get(second, 0) for second in range(int(elapsed // 1000)))
    if not rates:
        return None
    return {
        'seconds': len(rates),
        'lowestFps': rates[0],
        'p10Fps': rates[math.ceil(len(rates) * .1) - 1],
        'secondsBelow50Fps': sum(rate < 50 for rate in rates),
        'secondsBelow60Fps': sum(rate < 60 for rate in rates),
    }


def summarize_roll_pacing(presentation, events, time_origin):
    """Match each replay window to fresh worker submission intervals."""
    prefix = 'outer-coverage:roll-timing:'
    rolls = [tuple(map(int, event['message'][len(prefix):].split(',')))
             for event in events if event['message'].startswith(prefix)]
    intervals = presentation['intervals']
    if (len(rolls) != 3 or presentation['unobservedIntervals']
            or len(intervals) + 1 != presentation['frames']):
        raise ValueError('Expected three verified rolls and a complete submission sequence')
    last_frame = presentation['lastFrameAt']
    first_frame = last_frame
    submissions = []
    for interval in reversed(intervals):
        preceding = first_frame - interval
        submissions.append((preceding, first_frame, interval))
        first_frame = preceding

    windows = [(context, started - time_origin, finished - time_origin) for context, started, finished in rolls]
    measurements = []
    for context, started, finished in windows:
        if not first_frame <= started <= finished <= last_frame:
            raise ValueError(f'Scene roll {context} falls outside the submission sequence')
        overlapping = [interval for begin, finish, interval in submissions if begin <= finished and finish >= started]
        measurements.append({'context': context, 'replayMs': round(finished - started, 1),
                             'longestIntervalMs': round(max(overlapping), 1), 'intervalCount': len(overlapping)})
    between_rolls = [interval for begin, finish, interval in submissions
                     if all(finish < started or begin > finished for _, started, finished in windows)]
    return {'rolls': measurements, 'longestOutsideRollMs': round(max(between_rolls), 1)}


def summarize_walking(result):
    """Measure fresh WebGL submissions over the real walk, including time between the legs."""
    rows = (result.get('travel') or {}).get('rows', [])
    walks = [event['milliseconds'] for event in result['events'] if event['message'].startswith('outer-coverage:walking:')]
    arrivals = [event['milliseconds'] for event in result['events'] if event['message'].startswith('outer-coverage:arrived:')]
    if not rows or not walks or not arrivals:
        return None
    events_started = result['timeOrigin'] + result['proofStartedAt']
    first = events_started + walks[0]
    last = events_started + arrivals[-1]
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


def firefox_profiler_command(socket, method, params):
    script = """
const [url, method, params] = process.argv.slice(1);
const socket = new WebSocket(url);
try {
  await new Promise((resolve, reject) => {socket.onopen = resolve; socket.onerror = reject;});
  const reply = await new Promise((resolve, reject) => {
    socket.onmessage = event => {
      const response = JSON.parse(event.data);
      if (response.id === 1) resolve(response);
    };
    socket.onerror = reject;
    socket.send(JSON.stringify({id:1, method, params:JSON.parse(params)}));
  });
  if (reply.type !== 'success') throw new Error(JSON.stringify(reply));
  process.stdout.write(JSON.stringify(reply.result));
} finally {
  socket.close();
}
"""
    return json.loads(subprocess.check_output(
        ['node', '--input-type=module', '-e', script, socket, method, json.dumps(params)], timeout=25, text=True))


def save_hot_jit_sources(run, profile, command):
    if 'head' in profile:
        weight_label = 'estimatedBytes'
        pending = [profile['head']]
        nodes = []
        while pending:
            node = pending.pop()
            nodes.append((node, node['selfSize']))
            pending.extend(node.get('children', ()))
    else:
        weight_label = 'samples'
        counts = Counter(profile['samples'])
        nodes = [(node, counts[node['id']]) for node in profile['nodes']]
    sites = {}
    for node, weight in nodes:
        frame = node['callFrame']
        name = frame['functionName']
        if weight and name.startswith('J_') and frame.get('scriptId') not in (None, '0') and not frame.get('url'):
            key = (name, frame['scriptId'])
            sites[key] = sites.get(key, 0) + weight
    largest = sorted(sites.items(), key=lambda item: item[1], reverse=True)[:12]
    if not largest:
        return None
    command('Debugger.enable')
    try:
        sources = []
        for (name, script_id), weight in largest:
            entry = {'function': name, 'scriptId': script_id, weight_label: weight}
            try:
                source = command('Debugger.getScriptSource', {'scriptId': script_id})['scriptSource']
                if source and len(source) <= 65536:
                    entry['source'] = source
            except (urllib.error.HTTPError, KeyError):
                # V8 may have discarded a generated script by the end of the walk.
                pass
            sources.append(entry)
    finally:
        command('Debugger.disable')
    path = run / 'chrome-hot-jit-sources.json'
    path.write_text(json.dumps(sources, indent=2) + '\n')
    return path.name


def run_walk(run, driver_port, browser, wasmgc=False, output='default', firefox_profile=False,
             java_version=8, chrome_profile=None, runtime='5.0'):
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
    if runtime != '5.0':
        url += '&runtime=' + runtime
    if wasmgc:
        url += '&wasmgc=1'
    if java_version != 8:
        url += f'&java={java_version}'
    if output != 'default':
        if not (fixture.get('presentationOnly') or fixture.get('travelTrace')):
            raise ValueError('Use a presentation control or travel trace for an explicit resolution')
        url += '&physical=' + ('on' if output == 'full-hd' else 'off')

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
                if firefox_profile:
                    capabilities['webSocketUrl'] = True
                    capabilities['moz:firefoxOptions']['prefs'].update({
                        'browser.download.folderList': 2, 'browser.download.dir': str(run)})
            else:
                capabilities['goog:chromeOptions'] = {'args': ['--headless', '--window-size=1280,900',
                    '--use-gl=angle', '--use-angle=gl']}
            started = request('/session', {'capabilities': {'alwaysMatch': capabilities}})
            session = started['sessionId']
            prefix = '/session/' + session
            if output != 'default':
                request(prefix + '/window/rect', {'width': 2240, 'height': 1400})
            request(prefix + '/url', {'url': url})
            element = request(prefix + '/element', {'using': 'css selector', 'value': '#start'})
            request(prefix + '/element/' + next(iter(element.values())) + '/click')

            def evaluate(script):
                return request(prefix + '/execute/sync', {'script': PROOF + script, 'args': []})

            def chrome_command(command, params=None):
                return request(prefix + '/goog/cdp/execute', {'cmd': command, 'params': params or {}})

            def chrome_timestamp():
                return next(row['value'] * 1000 for row in chrome_command('Performance.getMetrics')['metrics']
                            if row['name'] == 'Timestamp')

            def chrome_clock_anchor():
                before = chrome_timestamp()
                relative = evaluate('return w.performance.now();')
                after = chrome_timestamp()
                return {'pageMs': relative, 'beforeMs': before, 'afterMs': after}

            if chrome_profile == 'cpu':
                chrome_command('Performance.enable')
                chrome_command('Profiler.enable')
                chrome_command('Profiler.setSamplingInterval', {'interval': 2000})

            deadline = time.monotonic() + 228
            previous_state = None
            reported_events = 0
            profiler_active = False
            chrome_anchors = []
            pointer_moves = 0
            next_pointer_move = 0.0
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
                if (fixture.get('projectionDemand') and pointer_moves < len(PROJECTION_POINTERS)
                        and time.monotonic() >= next_pointer_move
                        and any(event['message'].startswith('outer-coverage:walking:') for event in snap['events'])):
                    x, y = PROJECTION_POINTERS[pointer_moves]
                    point = evaluate("""
                        const canvas=w.document.querySelector('#browser-webgl');
                        const rect=canvas.getBoundingClientRect(), iframe=w.frameElement.getBoundingClientRect();
                        return [iframe.left+rect.left+rect.width*%s,iframe.top+rect.top+rect.height*%s];
                        """ % (x, y))
                    request(prefix + '/actions', {'actions': [{'type': 'pointer', 'id': 'projection-pointer',
                        'parameters': {'pointerType': 'mouse'}, 'actions': [{'type': 'pointerMove',
                            'origin': 'viewport', 'x': round(point[0]), 'y': round(point[1]), 'duration': 70}]}]})
                    pointer_moves += 1
                    next_pointer_move = time.monotonic() + 6
                if firefox_profile and not profiler_active and any(
                        event['message'].startswith('outer-coverage:walking:') for event in snap['events']):
                    firefox_profiler_command(started['capabilities']['webSocketUrl'], 'moz:profiler.start', {
                        'entries': 4000000, 'interval': 2, 'features': ['js', 'stackwalk', 'cpu'],
                        'threads': ['GeckoMain', 'Compositor', 'DOM Worker', 'CanvasRenderer']})
                    profiler_active = True
                if chrome_profile and not profiler_active and any(
                        event['message'].startswith('outer-coverage:walking:') for event in snap['events']):
                    if chrome_profile == 'cpu':
                        chrome_anchors.append(chrome_clock_anchor())
                        chrome_command('Profiler.start')
                    else:
                        chrome_command('HeapProfiler.startSampling', {
                            'samplingInterval': 65536, 'stackDepth': 48,
                            'includeObjectsCollectedByMajorGC': True, 'includeObjectsCollectedByMinorGC': True})
                    profiler_active = True
                if snap.get('reason') or any(event['message'].startswith(('outer-coverage:failed', 'outer-coverage:complete'))
                                             for event in snap['events']):
                    break
                time.sleep(.85)

            result = evaluate('return {events:' + ROUTE_EVENTS + ''',browserEvents:p.events,runtime:p.state, reason:p.stopReason,
                region:p.regionHandoff, travel:p.gpu?.travelTrace, stream:p.gpu?.worldStreamSamples, terrainWorker:p.terrainWorker,surfaceMeshWorker:p.surfaceMeshWorker,sceneryWorker:p.surfaceSceneryMeshWorker,renderWorker:p.gpu?.renderWorker,
                graphics:{vendor:p.gpu?.vendor,renderer:p.gpu?.renderer,context:p.gpu?.contextAttributes},
                modelBatchWork:p.batchedModelKernelWork,
                compactPackWork:p.compactPackWork,
                atlas:{plans:p.gpu?.spritePlanCalls,commits:p.gpu?.spriteCommitCalls,uploadedBytes:p.gpu?.spriteUploadedBytes},
                inlineWorldProgressFrames:p.gpu?.inlineWorldProgressFrames,inlineOverlayFrames:p.gpu?.inlineOverlayFrames,
                geometry:{uploadedBytes:p.gpu?.uploadedBytes,repacks:p.gpu?.repackedChunks},
                javaRuntime:{version:p.javaVersion,release:p.runtimeVersion,wasmGC:p.wasmGC},
                display:p.gpu?.display, visibility:w.document.visibilityState, browserFrames:w.travelBrowserFrames,
                physicalOutput:p.gpu?.physicalOutput,
                overlay:{uploadedBytes:p.gpu?.overlayUploadBytes,uploadCalls:p.gpu?.overlayUploadCalls,
                    coverageFrames:p.gpu?.overlayCoverageFrames,rgbBytes:p.gpu?.overlayRgbBytes,
                    coverageBytes:p.gpu?.overlayCoverageBytes,retainedBytes:p.gpu?.retainedResourceBytes,
                    retainedCaptures:p.gpu?.retainedCaptures},
                timingWindow:{gpu:p.gpu?.gpuWorkSamples,submission:p.gpu?.jsSubmissionSamples,
                    bridge:p.gpu?.javaBridgeWorkSamples,overlay:p.gpu?.overlayStageSamples,stages:p.gpu?.frameStageSamples,
                    slowStages:p.gpu?.slowFrameStages},
                presentation:w.travelPresentation,timeOrigin:w.performance.timeOrigin,proofStartedAt:p.started};''')
            if profiler_active and firefox_profile:
                receipt = firefox_profiler_command(started['capabilities']['webSocketUrl'], 'moz:profiler.stop', {})
                if not receipt.get('path'):
                    raise RuntimeError('Firefox profiler returned no capture')
                profile = Path(receipt['path']).resolve()
                if profile.parent != run or not profile.is_file():
                    raise RuntimeError('Firefox profile was not saved in the private test run')
                result['firefoxProfile'] = profile.name
            if profiler_active and chrome_profile == 'cpu':
                chrome_anchors.append(chrome_clock_anchor())
                profile = chrome_command('Profiler.stop')['profile']
                path = run / 'chrome-walk.cpuprofile'
                path.write_text(json.dumps(profile) + '\n')
                result['chromeProfile'] = {'path': path.name, 'clockAnchors': chrome_anchors,
                                           'scope': 'Chrome renderer main thread JavaScript CPU samples'}
            if profiler_active and chrome_profile == 'allocations':
                profile = chrome_command('HeapProfiler.stopSampling')['profile']
                path = run / 'chrome-walk-allocations.json'
                path.write_text(json.dumps(profile) + '\n')
                result['chromeProfile'] = {'path': path.name,
                                           'scope': 'Sampled Chrome main-thread JavaScript heap allocations, including collected objects'}
            if 'chromeProfile' in result:
                try:
                    result['chromeProfile']['hotJitSources'] = save_hot_jit_sources(run, profile, chrome_command)
                except (OSError, KeyError, ValueError) as error:
                    result['chromeProfile']['hotJitSourceError'] = str(error)
            if fixture.get('compactVertexBlocks'):
                packing = result.get('compactPackWork') or {}
                if not packing.get('blocks') or packing.get('vertices', 0) < 128:
                    raise RuntimeError('The walk did not exercise batched compact scenery packing')
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
            if fixture.get('projectionDemand'):
                result['projectionPointerMoves'] = pointer_moves
                completed = completed and pointer_moves == len(PROJECTION_POINTERS)
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
            if completed and fixture.get('terrainWords'):
                detached = result.get('surfaceMeshWorker') or {}
                completed = detached.get('wordReplies', 0) > detached.get('gameplayWordReplies', 0)
            if completed and fixture.get('terrainGameplayWords'):
                expected_gameplay = 3 if fixture['sustainedWalk'] else 1 if fixture['retargetWalk'] else 2
                completed = (result.get('surfaceMeshWorker') or {}).get('gameplayWordReplies', 0) >= expected_gameplay
            if completed and fixture.get('workerSceneryPlacements'):
                worker = result.get('sceneryWorker') or {}
                completed = (worker.get('completed', 0) > 0 and worker.get('error') is None
                             and any(event['message'].startswith('outer-coverage:placement-scenery-meshes:')
                                     or (event['message'].startswith('scenery-meshing:placements,')
                                         and event['message'].endswith(',true')) for event in result['browserEvents']))
            if completed and fixture.get('sceneryWords'):
                completed = (result.get('sceneryWorker') or {}).get('wordReplies', 0) > 0
            if completed and fixture.get('surfaceDrawWorker'):
                completed = (result.get('renderWorker') or {}).get('lastCompletedFrame', 0) > 0
            if completed and fixture.get('localSpritePlan'):
                plans = (result.get('renderWorker') or {}).get('localSpritePlans', 0)
                replayed = (result.get('atlas') or {}).get('plans', 0)
                completed = replayed > 0 and 0 <= plans - replayed <= 3
            if completed and java_version != 8:
                completed = any(event['message'] == f'outer-coverage:java-runtime:{java_version}'
                                for event in result['events'])
            if completed:
                completed = (result.get('javaRuntime') or {}).get('release') == runtime
            if completed and output != 'default':
                width, height = (1920, 1080) if output == 'full-hd' else (960, 540)
                expected_size = dict(zip(OUTPUT_DIMENSIONS, (960, 540, width, height)))
                if fixture.get('presentationOnly'):
                    completed = (result.get('presentation') or {}).get('outputSizes') == [expected_size]
                else:
                    physical = result.get('physicalOutput') or {}
                    completed = {field: physical.get(field) for field in OUTPUT_DIMENSIONS} == expected_size
            if completed and fixture.get('profilePanels'):
                profiles = (result.get('presentation') or {}).get('panelProfiles', {})
                for panel, inventory, minimap in [('closed', False, False), ('inventory', True, False), ('minimap', False, True)]:
                    samples = profiles.get(panel, [])
                    completed = completed and len(samples) >= 60 and all(
                        (row['inventoryDrawMs'] >= 0) == inventory and (row['minimapDrawMs'] >= 0) == minimap
                        for row in samples)
            if completed and fixture.get('profileMinimapWalk'):
                stages = result['timingWindow'].get('stages', [])
                completed = len(stages) >= 60 and all(row['minimapRasterMs'] >= 0 for row in stages)
            presentation = result.get('presentation')
            if presentation is not None:
                if not presentation['unobservedIntervals'] and presentation['frames'] <= len(presentation['intervals']) + 1:
                    presentation['oneSecondSummary'] = summarize_frame_rate(presentation['intervals'])
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
                if fixture.get('rollTimings') and completed:
                    try:
                        presentation['rollPacing'] = summarize_roll_pacing(presentation, result['events'], result['timeOrigin'])
                    except (KeyError, ValueError) as error:
                        presentation['rollTimingError'] = str(error)
                        completed = False
                printable = {key: value for key, value in presentation.items()
                             if key not in ('gaps', 'intervals', 'callbackGaps', 'panelProfiles')}
                if fixture.get('profilePanels'):
                    printable['panelSamples'] = {name: len(rows) for name, rows in presentation.get('panelProfiles', {}).items()}
                print(printable,
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
            try:
                private.wait(timeout=45)
            except subprocess.TimeoutExpired:
                # This is the disposable subprocess launched above in this run,
                # with its own database and ports. The route receipt is saved.
                private.terminate()
                private.wait(timeout=10)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('run', type=Path)
    parser.add_argument('--browser', choices=('firefox', 'chrome'), default='firefox')
    parser.add_argument('--driver-port', type=int, help='Port of the local WebDriver')
    parser.add_argument('--wasmgc', action='store_true', help='Test the experimental CheerpJ 5.0 engine')
    parser.add_argument('--java', choices=(8, 11, 17), type=int, default=8, help='Compare supported Java runtimes')
    parser.add_argument('--runtime', choices=('5.0', '4.3'), default='5.0', help='Compare CheerpJ releases')
    parser.add_argument('--firefox-profile', action='store_true', help='Save a separate Firefox browser profiler capture in the test run')
    parser.add_argument('--chrome-profile', choices=('cpu', 'allocations'), nargs='?', const='cpu',
                        help='Save a separate Chrome CPU or allocation profiler capture in the test run')
    parser.add_argument('--output', choices=('default', 'logical', 'full-hd'), default='default',
                        help='Compare 960x540 and 1920x1080 WebGL output with the same 960x540 UI')
    args = parser.parse_args()
    if args.firefox_profile and args.browser != 'firefox':
        parser.error('The Firefox profiler requires Firefox')
    if args.chrome_profile and args.browser != 'chrome':
        parser.error('The Chrome profiler requires Chrome')
    driver_port = args.driver_port if args.driver_port is not None else {'firefox': 44495, 'chrome': 9515}[args.browser]
    return run_walk(args.run.resolve(), driver_port, args.browser, args.wasmgc, args.output,
                    args.firefox_profile, args.java, args.chrome_profile, args.runtime)


if __name__ == '__main__':
    raise SystemExit(main())
