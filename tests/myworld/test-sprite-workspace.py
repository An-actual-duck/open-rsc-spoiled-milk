#!/usr/bin/env python3
"""Released v0.3.14 pixel oracle plus real borrowed-storage ownership/lifetime probes."""
from pathlib import Path
import subprocess
import tempfile
ROOT = Path(__file__).resolve().parents[2]
JAR = ROOT / 'Client_Base/Open_RSC_Client.jar'
# Produced by the unchanged independent-buffer API from released client
# 18779f57d12f076413a7222235a6edf8717af9770e68b549733984981b85da28.
EXPECTED = 'PIXEL_ORACLE_SHA256=c460a449b004bf6ee0b8f6def7d9b3a9d1a1997364117349f47e1c1b98818098'
with tempfile.TemporaryDirectory(prefix='sprite-workspace-') as temp:
    subprocess.run(['javac', '-source', '8', '-target', '8', '-cp', str(JAR), '-d', temp,
                    str(ROOT / 'tests/myworld/SpriteWorkspaceFixture.java')], check=True)
    for mode in ('baseline', 'reuse'):
        result = subprocess.check_output(['java', '-cp', temp + ':' + str(JAR),
                                         'orsc.SpriteWorkspaceFixture', mode], text=True)
        if EXPECTED not in result or 'PASS 1000 seeded' not in result:
            raise AssertionError(result)
        print(result.strip())
