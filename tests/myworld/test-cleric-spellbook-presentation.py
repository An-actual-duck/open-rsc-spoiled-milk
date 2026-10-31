#!/usr/bin/env python3
"""Active schema3 client presentation gate.

The old twelve-spell/schema2 assertions are superseded, including retired casts
and offense-roll/ward behavior. This entrypoint still executes real client
behavior through the shared schema3 fixture; server casting/combat policy belongs
to the independent server foundation/runtime gates.
"""
from pathlib import Path
import subprocess, sys
if __name__ == '__main__':
    subprocess.run([sys.executable, str(Path(__file__).with_name('test-cleric-three-tier-client.py')), '--scope', 'presentation'], check=True)
