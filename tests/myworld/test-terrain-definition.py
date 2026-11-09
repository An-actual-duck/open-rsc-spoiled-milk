#!/usr/bin/env python3
"""Actual immutable terrain cache, height shape, context and rebase probes."""
from pathlib import Path
import subprocess
import tempfile
ROOT = Path(__file__).resolve().parents[2]
jar = ROOT / 'Client_Base/Open_RSC_Client.jar'
with tempfile.TemporaryDirectory(prefix='terrain-definition-') as work:
    subprocess.run(['javac', '-source', '8', '-target', '8', '-cp', str(jar), '-d', work,
                    str(ROOT / 'tests/myworld/TerrainDefinitionFieldFixture.java')], check=True)
    subprocess.run(['java', '-cp', work + ':' + str(jar), 'orsc.TerrainDefinitionFieldFixture'], check=True)
