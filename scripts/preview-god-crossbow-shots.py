#!/usr/bin/env python3
"""Current prayer-bolt review page; older source studies remain in authoring/."""
from pathlib import Path
import subprocess,sys
raise SystemExit(subprocess.call([sys.executable,str(Path(__file__).with_name('preview-divine-bolt-visuals.py')),*sys.argv[1:]]))
