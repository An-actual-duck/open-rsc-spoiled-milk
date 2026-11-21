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
    parser.add_argument('--preupload-scene', action='store_true', help='Stage prepared scenery and the predicted center mesh')
    parser.add_argument('--compact-scenery', action='store_true', help='Use compact GPU buffers for static scenery')
    parser.add_argument('--pace-terrain', action='store_true', help='Yield during browser terrain prediction builds')
    parser.add_argument('--prebuild-ownership', action='store_true', help='Stage scenery in the ownership transition strips')
    parser.add_argument('--prepare-local-terrain', action='store_true', help='Prepare collision, minimap layout and structure models before a walking handoff')
    parser.add_argument('--prepare-incoming-scenery', action='store_true', help='Clone incoming interactive object models before their authoritative update')
    parser.add_argument('--travel-trace', action='store_true', help='Record real submitted frames and server positions through extended travel')
    parser.add_argument('--presentation-only', action='store_true', help='Check sustained travel without detailed Java or server tracing')
    parser.add_argument('--sustained-walk', action='store_true', help='Walk past multiple handoffs and then reverse')
    parser.add_argument('--slice-preupload', action='store_true', help='Pack large speculative vertex buffers across frames')
    parser.add_argument('--socket-read-ahead', action='store_true', help='Buffer available socket bytes before the Java packet drain')
    parser.add_argument('--persistent-movement', action='store_true', help='Use the private movement stream independent of the terrain center')
    parser.add_argument('--persistent-collision', action='store_true', help='Publish world-addressed collision cells and live contributors')
    parser.add_argument('--surface-scenery-cells', action='store_true', help='Receive complete viewer-authorized incoming scenery cells')
    parser.add_argument('--rolling-surface-window', action='store_true', help='Preserve overlapping world instances when the playable window moves')
    parser.add_argument('--fixed-surface-draw', action='store_true', help='Draw signed terrain through persistent world cells')
    parser.add_argument('--expanded-surface-draw-border', action='store_true', help='Include the signed source neighbors of outer draw cells')
    parser.add_argument('--surface-scenery-draw', action='store_true', help='Publish revisioned scenery meshes ahead of the crossing')
    parser.add_argument('--live-surface-handoff', action='store_true', help='Keep verified playable coverage live through a rolling rebase')
    parser.add_argument('--retarget-walk', action='store_true', help='Change course before the original predicted window activates')
    parser.add_argument('--steady-walk', action='store_true', help='Repeat the approach inside the installed area after its prediction is ready')
    parser.add_argument('--surface-draw-worker', action='store_true', help='Submit live WebGL drawing through the existing render worker')
    parser.add_argument('--surface-mesh-worker', action='store_true', help='Prepare entering terrain geometry in a separate browser VM')
    parser.add_argument('--hold-optional-terrain', action='store_true', help='Hold the retargeted fringe meshes until the actual crossing')
    parser.add_argument('--manual', action='store_true', help='Keep the route available for a manual login and walk')
    parser.add_argument('--tcp-port', type=int, help='Override the private game port together with --ws-port')
    parser.add_argument('--ws-port', type=int, help='Override the private WebSocket port together with --tcp-port')
    args = parser.parse_args()
    if args.sustained_walk and (args.manual or not (args.travel_trace or args.presentation_only)):
        parser.error('The automated sustained walk requires travel or presentation sampling')
    if args.presentation_only and (not (args.sustained_walk and args.live_surface_handoff) or args.trace or args.travel_trace):
        parser.error('Presentation-only sampling requires untraced sustained live-surface travel')
    if args.hold_optional_terrain and not (args.retarget_walk and args.surface_mesh_worker and not args.manual):
        parser.error('The fringe hold requires the automated retarget walk and the terrain worker')
    if args.steady_walk and (args.manual or args.sustained_walk or args.retarget_walk or not args.live_surface_handoff):
        parser.error('The steady walk uses its own automated live-surface route')
    if args.slice_preupload and not args.preupload_scene:
        parser.error('Sliced packing requires scene uploads')
    if args.preupload_scene and not (args.preupload and args.predicted_scenery):
        parser.error('Scene uploads require terrain uploads and scenery preparation')
    if args.compact_scenery and not (args.preupload_scene or args.surface_scenery_draw):
        parser.error('Compact scenery requires scene uploads')
    if args.prebuild_ownership and not args.predicted_scenery:
        parser.error('Scenery ownership prediction requires the scenery preview')
    if args.prepare_incoming_scenery and not args.prebuild_ownership:
        parser.error('Incoming scenery preparation requires the ownership strips')
    if args.surface_scenery_cells and not (args.persistent_collision and args.persistent_movement):
        parser.error('Surface scenery cells require persistent collision and movement')
    if args.rolling_surface_window and not args.surface_scenery_cells:
        parser.error('Rolling surface windows require live scenery cells')
    if args.fixed_surface_draw and not (args.rolling_surface_window and (args.manual or args.travel_trace or args.presentation_only)):
        parser.error('Fixed terrain draw requires rolling coverage and sampling for automated proofs')
    if args.expanded_surface_draw_border and not args.fixed_surface_draw:
        parser.error('Expanded draw sources require fixed terrain draw cells')
    if args.surface_scenery_draw and not args.expanded_surface_draw_border:
        parser.error('Scenery draw cells require the expanded terrain sources')
    if args.live_surface_handoff and not args.surface_scenery_draw:
        parser.error('Live surface handoff requires independent scenery draws')
    if (args.surface_mesh_worker or args.surface_draw_worker) and not args.live_surface_handoff:
        parser.error('Surface workers require live surface handoff')
    if args.retarget_walk and (args.manual or not args.live_surface_handoff or args.sustained_walk):
        parser.error('Retarget walking requires the separate automated live surface route')
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
           f'        System.setProperty("spoiledmilk.browserPreuploadSceneMeshes", "{str(args.preupload_scene).lower()}");\n'
           f'        System.setProperty("spoiledmilk.browserCompactScenery", "{str(args.compact_scenery).lower()}");\n'
           f'        System.setProperty("spoiledmilk.browserPaceTerrainPrediction", "{str(args.pace_terrain).lower()}");\n'
           f'        System.setProperty("spoiledmilk.browserPrepareLocalTerrain", "{str(args.prepare_local_terrain).lower()}");\n'
           f'        System.setProperty("spoiledmilk.browserPrepareIncomingScenery", "{str(args.prepare_incoming_scenery).lower()}");\n'
           f'        System.setProperty("spoiledmilk.browserTravelTrace", "{str(args.travel_trace).lower()}");\n'
           f'        System.setProperty("spoiledmilk.browserPresentationOnly", "{str(args.presentation_only).lower()}");\n'
           f'        System.setProperty("spoiledmilk.browserSlicePreupload", "{str(args.slice_preupload).lower()}");\n'
           f'        System.setProperty("spoiledmilk.browserSocketReadAhead", "{str(args.socket_read_ahead).lower()}");\n'
           f'        System.setProperty("spoiledmilk.persistentMovementPreview", "{str(args.persistent_movement).lower()}");\n'
           f'        System.setProperty("spoiledmilk.persistentSurfaceCollision", "{str(args.persistent_collision).lower()}");\n'
           f'        System.setProperty("spoiledmilk.surfaceSceneryCells", "{str(args.surface_scenery_cells).lower()}");\n'
           f'        System.setProperty("spoiledmilk.rollingSurfaceWindow", "{str(args.rolling_surface_window).lower()}");\n'
           f'        System.setProperty("spoiledmilk.fixedSurfaceDraw", "{str(args.fixed_surface_draw).lower()}");\n'
           f'        System.setProperty("spoiledmilk.expandedSurfaceDrawBorder", "{str(args.expanded_surface_draw_border).lower()}");\n'
           f'        System.setProperty("spoiledmilk.surfaceSceneryDraw", "{str(args.surface_scenery_draw).lower()}");\n'
           f'        System.setProperty("spoiledmilk.liveSurfaceHandoff", "{str(args.live_surface_handoff).lower()}");\n'
           f'        System.setProperty("spoiledmilk.retargetSurfaceWalk", "{str(args.retarget_walk).lower()}");\n'
           f'        System.setProperty("spoiledmilk.steadySurfaceWalk", "{str(args.steady_walk).lower()}");\n'
           f'        System.setProperty("spoiledmilk.browserSurfaceMeshWorker", "{str(args.surface_mesh_worker).lower()}");\n'
           f'        System.setProperty("spoiledmilk.fixtureHoldOptionalTerrain", "{str(args.hold_optional_terrain).lower()}");\n'
           f'        System.setProperty("spoiledmilk.sustainedBrowserWalk", "{str(args.sustained_walk).lower()}");\n'
           f'        System.setProperty("spoiledmilk.predictedSceneryPreview", "{str(args.predicted_scenery).lower()}");')
    if not args.manual:
        insert(bootstrap, '        final BrowserStartupDiagnostics startupDiagnostics=',
               '        System.setProperty("spoiledmilk.outerCoverageFixturePassword", "BrowserProof13");\n'
               '        new Thread(BrowserOuterCoverageProbe::run, "outer-coverage-fixture").start();')
    insert(web / 'runtime.html', "    await import('./browser.js');",
           f"    window.browserPrivateTestRoute = {{tcpPort:{tcp_port}, websocket:'ws://127.0.0.1:{ws_port}/', "
           f"surfaceDrawWorker:{str(args.surface_draw_worker).lower()}}};")
    if args.manual and args.travel_trace:
        (web / 'travel-report.js').write_bytes(Path(__file__).with_name('travel-report.js').read_bytes())
        insert(web / 'runtime.html', "    await import('./browser.js');",
               "    await import('./travel-report.js');")
    if (tcp_port, ws_port) != (TCP_PORT, WS_PORT):
        replace(bootstrap, '&&!"43659".equals(stagedPort)',
                f'&&!"43659".equals(stagedPort)&&!"{tcp_port}".equals(stagedPort)')
        replace(web / 'browser-repeatable-policy.js', "[43659,'ws://127.0.0.1:43559/']",
                f"[43659,'ws://127.0.0.1:43559/'],[{tcp_port},'ws://127.0.0.1:{ws_port}/']")
    if args.trace:
        replace(web / 'browser.js', 'createRegionHandoffTraceCollector({enabled:loadProfilePolicy.requested,',
                'createRegionHandoffTraceCollector({enabled:!deployment.publicAlpha,')
    if args.compact_scenery or args.travel_trace:
        replace(web / 'browser.js', 'const gpuOptions={wholeZone:',
                'const gpuOptions={'
                + ('compactScene:!deployment.publicAlpha,' if args.compact_scenery else '')
                + ('travelTrace:!deployment.publicAlpha,' if args.travel_trace else '') + 'wholeZone:')
    java_sources = [str(bootstrap)]
    if not args.manual:
        insert(web / 'browser.js', '  Java_orsc_BrowserWebGLBridge_loadingWork:',
               "  Java_orsc_browser_BrowserOuterCoverageProbe_loginReady:()=>window.browserProof.state?.mode==='LOGIN'\n"
               "    && window.browserProof.events.some(event=>event.message==='WebSocket closed')?1:0,\n"
               "  Java_orsc_browser_BrowserOuterCoverageProbe_worldInputReady:()=>window.browserWorldInputAllowed()?1:0,\n"
               "  Java_orsc_browser_BrowserOuterCoverageProbe_worldReady:()=>gpu?.stats.worldReadiness?.phase==='ready'?1:0,")
        java_sources.append(str(Path(__file__).with_name('BrowserOuterCoverageProbe.java')))
    if args.hold_optional_terrain:
        insert(web / 'browser.js', '  Java_orsc_BrowserWebGLBridge_loadingWork:',
               '  Java_orsc_browser_BrowserOuterCoverageProbe_holdOptionalTerrain:(_library,hold)=> {\n'
               '    if (hold) window.fixtureFringeGate = Promise.withResolvers();\n'
               '    else { window.fixtureFringeGate?.resolve(); delete window.fixtureFringeGate; }\n'
               '  },')
        insert(web / 'browser.js', '    return surfaceMeshWorker.build(source, [offsetX, offsetZ, dx, dz, withRoofs, customSprites, gameplay]);',
               '    if (window.fixtureFringeGate && !gameplay) await window.fixtureFringeGate.promise;')
    subprocess.run([
        'javac', '-source', '8', '-target', '8', '-cp', f'{web / "client.jar"}:{classes}',
        '-d', str(classes), *java_sources,
    ], check=True)
    subprocess.run(['jar', 'cf', str(web / 'browser-spike.jar'), '-C', str(classes), '.'], check=True)
    subprocess.run([
        'python3', str(scripts / 'prepare-server.py'), str(run), '--tcp-port', str(tcp_port),
        '--ws-port', str(ws_port), '--covered-walking-preview', '--outer-coverage',
        *(['--predicted-scenery'] if args.predicted_scenery else []),
        *(['--prebuild-ownership'] if args.prebuild_ownership else []),
        *(['--travel-trace'] if args.travel_trace else []),
        *(['--persistent-movement'] if args.persistent_movement else []),
        *(['--surface-scenery-cells'] if args.surface_scenery_cells else []),
        *(['--expanded-surface-draw-border'] if args.expanded_surface_draw_border else []),
        *(['--surface-scenery-draw'] if args.surface_scenery_draw else []),
        *(['--live-surface-handoff'] if args.live_surface_handoff else []),
        *(['--safe-walk-fixture'] if args.retarget_walk else []),
        *(['--surface-scenery-mutation'] if args.surface_scenery_cells and not (args.manual or args.retarget_walk) else []),
    ], cwd=ROOT, check=True)
    staged_hashes = {
        name: hashlib.sha256((web / name).read_bytes()).hexdigest()
        for name in ('browser.js', 'runtime.html', 'browser-repeatable-policy.js', 'browser-spike.jar', 'Cache/port.txt')
    }
    if args.manual and args.travel_trace:
        staged_hashes['travel-report.js'] = hashlib.sha256((web / 'travel-report.js').read_bytes()).hexdigest()
    stage_path = run / 'stage.json'
    stage = json.loads(stage_path.read_text())
    stage['tcp'] = f'127.0.0.1:{tcp_port}'
    stage['websocket'] = f'127.0.0.1:{ws_port}'
    for name in ('browser.js', 'runtime.html', 'browser-repeatable-policy.js', 'travel-report.js'):
        if name not in staged_hashes:
            continue
        stage['frontendSha256'][name] = staged_hashes[name]
    stage_path.write_text(json.dumps(stage, indent=2) + '\n')
    receipt = {
        'spawn': [239, 609], 'destination': [269, 609], 'port': args.http_port,
        'preupload': args.preupload, 'trace': args.trace, 'manual': args.manual,
        'predictedScenery': args.predicted_scenery,
        'preuploadScene': args.preupload_scene,
        'compactScenery': args.compact_scenery,
        'paceTerrain': args.pace_terrain,
        'prebuildOwnership': args.prebuild_ownership,
        'prepareLocalTerrain': args.prepare_local_terrain,
        'prepareIncomingScenery': args.prepare_incoming_scenery,
        'travelTrace': args.travel_trace,
        'presentationOnly': args.presentation_only,
        'slicePreupload': args.slice_preupload,
        'socketReadAhead': args.socket_read_ahead,
        'persistentMovement': args.persistent_movement,
        'persistentCollision': args.persistent_collision,
        'surfaceSceneryCells': args.surface_scenery_cells,
        'rollingSurfaceWindow': args.rolling_surface_window,
        'fixedSurfaceDraw': args.fixed_surface_draw,
        'expandedSurfaceDrawBorder': args.expanded_surface_draw_border,
        'surfaceSceneryDraw': args.surface_scenery_draw,
        'liveSurfaceHandoff': args.live_surface_handoff,
        'surfaceMeshWorker': args.surface_mesh_worker,
        'surfaceDrawWorker': args.surface_draw_worker,
        'sustainedWalk': args.sustained_walk,
        'retargetWalk': args.retarget_walk,
        'steadyWalk': args.steady_walk,
        'holdOptionalTerrain': args.hold_optional_terrain,
        'tcpPort': tcp_port, 'websocketPort': ws_port,
        'url': f'http://127.0.0.1:{args.http_port}/?mode=webgl',
        'stagedSha256': staged_hashes,
    }
    (run / 'outer-coverage-fixture.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print(run)


if __name__ == '__main__':
    main()
