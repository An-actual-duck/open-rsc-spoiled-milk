#!/usr/bin/env python3
"""Executable state/layout/native-font checks; optionally export review frames."""
from pathlib import Path
import os
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix='hitsplat-check-') as build:
    cp = os.pathsep.join([str(root / 'Client_Base/build'), str(root / 'PC_Client/lib/*')])
    subprocess.run(['javac', '-J-Xmx384m', '-cp', cp, '-d', build,
                    str(root / 'tests/myworld/FloatingHitSplatFixture.java'),
                    str(root / 'tests/myworld/HitSplatSettingsFixture.java')], check=True)
    for profile in ['software', 'phased', 'native-ui']:
        command = ['java', '-Xmx384m', '-Djava.awt.headless=true']
        if profile != 'software':
            command += ['-Dspoiled_milk.opengl_sprite_overlay=true',
                        '-Dspoiled_milk.opengl_sprite_overlay_mode=phased',
                        '-Dspoiledmilk.openglWorldUiReplay=true']
        if profile == 'native-ui':
            command += ['-Dspoiled_milk.opengl_native_ui_replace=true',
                        '-Dspoiled_milk.opengl_ui_base_frame=true']
        command += ['-cp', build + os.pathsep + cp, 'orsc.FloatingHitSplatFixture', str(root)]
        if profile == 'software' and len(sys.argv) > 1:
            command += [str(Path(sys.argv[1]).resolve())]
        subprocess.run(command, cwd=root, check=True)

    with tempfile.TemporaryDirectory(prefix='hitsplat-local-settings-') as settings_dir:
        for mode in ['write', 'reload']:
            subprocess.run(['java', '-Xmx384m', '-Djava.awt.headless=true', '-cp',
                            build + os.pathsep + cp, 'orsc.HitSplatSettingsFixture', str(root), mode],
                           cwd=settings_dir, check=True)
