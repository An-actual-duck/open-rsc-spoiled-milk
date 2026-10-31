#!/usr/bin/env python3
"""Export native-overlay comparison, GIF and timed contact sheet without a server."""
from pathlib import Path
import subprocess
import sys
from PIL import Image

root = Path(__file__).resolve().parents[1]
out = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else root / 'output/hitsplat-preview'
subprocess.run([sys.executable, str(root / 'tests/myworld/test-floating-hit-splats.py'), str(out)], check=True)
frames = [Image.open(out / f'frame-{i:03d}.png').convert('RGB') for i in range(151)]
frames[0].save(out / 'floating-styles.gif', save_all=True, append_images=frames[1:], duration=40, loop=0, optimize=False)
contact = Image.new('RGB', (1920, 2160))
for j, i in enumerate([0, 10, 25, 45, 70, 100, 125, 150]):
    contact.paste(frames[i], ((j % 2) * 960, (j // 2) * 540))
contact.save(out / 'motion-sequence.png')
print(f'Comparison: {out / "comparison.png"}')
print(f'Animation: {out / "floating-styles.gif"}')
print(f'Sequence: {out / "motion-sequence.png"}')
