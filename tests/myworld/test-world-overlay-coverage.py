#!/usr/bin/env python3
"""Production RGB oracle against parent source plus opt-in software writer coverage."""
from pathlib import Path
import os
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
JAVA = Path(os.environ.get('JAVA_HOME', '/home/justin/jdk8u482-b08')) / 'bin'
JAR = ROOT / 'Client_Base/Open_RSC_Client.jar'
GRAPHICS = 'Client_Base/src/orsc/graphics/two/GraphicsController.java'
BASE = '9c07de9995366916bfa5a10918b076e709af24e5'
with tempfile.TemporaryDirectory(prefix='world-overlay-coverage-') as temp:
    temp = Path(temp)
    current, baseline = temp / 'current', temp / 'baseline'
    current.mkdir(); baseline.mkdir()
    sources = [ROOT / GRAPHICS, ROOT / 'Client_Base/src/orsc/graphics/two/WorldOverlayCoverage.java',
               ROOT / 'tests/myworld/WorldOverlayCoverageFixture.java',
               ROOT / 'tests/myworld/SceneSpriteRasterPolicyFixture.java']
    subprocess.run([str(JAVA / 'javac'), '-source', '8', '-target', '8', '-cp', str(JAR),
                    '-d', str(current), *map(str, sources)], check=True)
    old = temp / 'GraphicsController.java'
    old.write_bytes(subprocess.check_output(['git', 'show', BASE + ':' + GRAPHICS], cwd=ROOT))
    subprocess.run([str(JAVA / 'javac'), '-source', '8', '-target', '8', '-cp', str(JAR),
                    '-d', str(baseline), str(old)], check=True)
    command = [str(JAVA / 'java'), '-cp']
    main = 'orsc.graphics.two.WorldOverlayCoverageFixture'
    ordinary = subprocess.check_output(command + [str(current) + os.pathsep + str(JAR), main, 'native'], text=True)
    original = subprocess.check_output(command + [os.pathsep.join(map(str, (baseline, current, JAR))), main, 'native'], text=True)
    assert ordinary == original, (ordinary, original)
    assert ordinary.startswith('NATIVE_RGB_SHA256='), ordinary
    print(ordinary.strip(), '(unchanged parent source parity)')
    subprocess.run(command + [str(current) + os.pathsep + str(JAR), main], check=True)
    for properties, mode in ((['-Dspoiledmilk.openglWorldSpritesVisible=true'], 'world'),
                             (['-Dspoiled_milk.opengl_sprite_overlay=true'], 'capture-only'),
                             ([], 'capture-off')):
        subprocess.run([str(JAVA / 'java'), *properties, '-cp', str(current) + os.pathsep + str(JAR),
                        'orsc.graphics.two.SceneSpriteRasterPolicyFixture', mode], check=True)
