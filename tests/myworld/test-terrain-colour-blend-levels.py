#!/usr/bin/env python3
"""Exercise the actual shared desktop/browser terrain producers on each native plane."""
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
jar = ROOT / 'Client_Base/Open_RSC_Client.jar'
with tempfile.TemporaryDirectory(prefix='terrain-colour-blend-levels-') as work:
    subprocess.run(['javac', '-source', '8', '-target', '8', '-cp', str(jar), '-d', work,
                    str(ROOT / 'Client_Base/src/orsc/graphics/three/World.java'),
                    str(ROOT / 'tests/myworld/TerrainColourBlendLevelsFixture.java')], check=True)
    subprocess.run(['java', '-Djava.awt.headless=true', '-cp', work + ':' + str(jar),
                    'orsc.TerrainColourBlendLevelsFixture'], check=True)
