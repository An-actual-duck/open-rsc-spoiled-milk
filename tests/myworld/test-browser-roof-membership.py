#!/usr/bin/env python3
"""Frozen real-source roof membership differential; no browser/server/GPU."""
from pathlib import Path
from datetime import datetime, timezone
import argparse
import hashlib
import json
import subprocess

ROOT = Path(__file__).resolve().parents[2]
BASE = '8b888cd2f13d3a45d4ece42263ecf8ecc9b415ae'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--client-jar', type=Path, required=True)
args = parser.parse_args()
jar = args.client_jar.resolve()
category = Path(subprocess.check_output(['python3', str(ROOT / 'scripts/workspace-layout.py'),
                                       'path', 'tests', 'browser-roof-membership', '--mkdir'], text=True).strip())
run = category / datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
run.mkdir()
current = run / 'current'
baseline = run / 'baseline'
current.mkdir()
baseline.mkdir()


def block(source):
    start = source.index('Renderer3DRoofVisibility roofVisibility = this.currentRenderer3DRoofVisibility();')
    end = source.index('if (this.objectAnimationNumberFireLightningSpell != this.lastObjectAnimationNumberFireLightningSpell)', start)
    return source[start:end]


def harness(source):
    # The body is the actual source block, independently extracted for each
    # revision. Camera/bookkeeping control flow is not copied into a test model.
    return '''package orsc.graphics.three;
final class RoofMembershipDraw {
  Scene scene;
  BrowserRoofMembershipFixture.Grid world;
  int lastHeightOffset, amountToZoom=200, cameraZoom, minCameraZoom=100;
  boolean doCameraZoom;
  BrowserRoofMembershipFixture.Settings osConfig = new BrowserRoofMembershipFixture.Settings();
  Renderer3DRoofVisibility visibility = Renderer3DRoofVisibility.VISIBLE;
  final BrowserRoofMembership browserRoofMembership;
  RoofMembershipDraw(Scene s, BrowserRoofMembershipFixture.Grid w, boolean requested) {
    scene=s; world=w; browserRoofMembership=requested?new BrowserRoofMembership():null;
  }
  Renderer3DRoofVisibility currentRenderer3DRoofVisibility() { return visibility; }
  void draw() {
''' + block(source) + '\n}\n}\n'


old_scene = subprocess.check_output(['git', 'show', BASE + ':Client_Base/src/orsc/graphics/three/Scene.java'], cwd=ROOT)
old_client = subprocess.check_output(['git', 'show', BASE + ':Client_Base/src/orsc/mudclient.java'], cwd=ROOT).decode()
new_client = (ROOT / 'Client_Base/src/orsc/mudclient.java').read_text()
assert 'Boolean.getBoolean("spoiledmilk.browserRoofMembership") ? new BrowserRoofMembership() : null' in new_client
for folder, source in [(baseline, old_client), (current, new_client)]:
    (folder / 'RoofMembershipDraw.java').write_text(harness(source))
(baseline / 'Scene.java').write_bytes(old_scene)
fixtures = [ROOT / 'tests/myworld' / name for name in [
    'ClericWellSpotClientTest.java', 'BrowserOwnershipGraphics.java',
    'BrowserWallRoofOwnershipFixture.java', 'BrowserRoofMembershipFixture.java']]
sources = [ROOT / 'Client_Base/src/orsc/graphics/three' / name
           for name in ['Scene.java', 'BrowserRoofMembership.java']]
with (run / 'compile.log').open('w') as log:
    subprocess.run(['javac', '-source', '8', '-target', '8', '-cp', str(jar), '-d', str(current),
                    *map(str, sources + fixtures), str(current / 'RoofMembershipDraw.java')], check=True, stdout=log, stderr=subprocess.STDOUT)
    subprocess.run(['javac', '-source', '8', '-target', '8', '-cp', str(current) + ':' + str(jar),
                    '-d', str(baseline), str(baseline / 'Scene.java'), str(baseline / 'RoofMembershipDraw.java')],
                   check=True, stdout=log, stderr=subprocess.STDOUT)

rows = {}
edges = {}
for label, flag in [('baseline', 'false'), ('baseline', 'true'), ('current', 'false'), ('current', 'true')]:
    name = label + '-' + flag
    cp = (str(baseline) + ':' if label == 'baseline' else '') + str(current) + ':' + str(jar)
    with (run / (name + '.log')).open('w') as log:
        subprocess.run(['java', '-Xmx768m', '-Djava.awt.headless=true', '-cp', cp,
                        'orsc.graphics.three.BrowserRoofMembershipFixture', label, flag],
                       check=True, stdout=log, stderr=subprocess.STDOUT)
    lines = (run / (name + '.log')).read_text().strip().splitlines()
    rows[name] = {'digest': lines[-1], 'work': lines[-2]}
    with (run / (name + '-edges.log')).open('w') as log:
        subprocess.run(['java', '-Xmx768m', '-Djava.awt.headless=true', '-cp', cp,
                        'orsc.graphics.three.BrowserRoofMembershipFixture', 'edges', label, flag],
                       check=True, stdout=log, stderr=subprocess.STDOUT)
    edges[name] = (run / (name + '-edges.log')).read_text().strip().splitlines()[-1]
assert len({row['digest'] for row in rows.values()}) == 1, rows
assert len(set(edges.values())) == 1, edges
with (run / 'revisions.log').open('w') as log:
    subprocess.run(['java', '-Xmx768m', '-Djava.awt.headless=true', '-cp', str(current) + ':' + str(jar),
                    'orsc.graphics.three.BrowserRoofMembershipFixture', 'revisions'],
                   check=True, stdout=log, stderr=subprocess.STDOUT)
with (run / 'frontend.log').open('w') as log:
    for fixture in ['test-browser-preparation-candidates.mjs', 'test-browser-webgl-frontend.mjs']:
        subprocess.run(['node', '--experimental-vm-modules', str(ROOT / 'tests/myworld' / fixture)],
                       check=True, stdout=log, stderr=subprocess.STDOUT)
meta = dict(passed=True, baseline=BASE, clientJarSha256=hashlib.sha256(jar.read_bytes()).hexdigest(), rows=rows, edges=edges,
            revisions=(run / 'revisions.log').read_text().strip(),
            scope='Extracted actual mudclient roof block + real Scene:480 frames/cohort, ordered roster, metadata, camera state, picks, sprite anchors, resident exports and pixels. CPU only; no FPS claim.')
(run / 'result.json').write_text(json.dumps(meta, indent=2) + '\n')
print(json.dumps({'run': str(run), **meta}, indent=2))
