#!/usr/bin/env python3
"""Compare complete sampler outputs with the frozen pre-optimization World."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[2]
out = Path(subprocess.check_output(['python3', str(root/'scripts/workspace-layout.py'),
    'path', 'tests', 'halo-match-reuse', '--mkdir'], text=True).strip())
jar = root/'Client_Base/Open_RSC_Client.jar'
fixture = root/'tests/myworld/HaloMatchReuseFixture.java'
reference = out/'reference-src/orsc/graphics/three/World.java'
reference.parent.mkdir(parents=True, exist_ok=True)
reference.write_bytes(subprocess.check_output(['git', 'show',
    '2b0681f841298d840ca13ee0ebb5a2ec7d9f6888:Client_Base/src/orsc/graphics/three/World.java'], cwd=root))
for name, source in [('reference', reference), ('candidate', root/'Client_Base/src/orsc/graphics/three/World.java')]:
    classes = out/name
    classes.mkdir(exist_ok=True)
    subprocess.run(['javac', '-source', '8', '-target', '8', '-cp', str(jar), '-d', str(classes),
        str(source), str(root/'tests/myworld/BrowserResidentInputsFixture.java'), str(fixture)], check=True)
    for policy in (['true'] if name == 'reference' else ['true','false']):
        subprocess.run(['java', '-Xmx512m', '-Djava.awt.headless=true',
            '-Dspoiledmilk.presentationHaloMatchReuse='+policy, '-cp', str(classes)+':'+str(jar),
            'orsc.HaloMatchReuseFixture', str(out/(name+'-'+policy+'.bin'))], check=True)
a = (out/'reference-true.bin').read_bytes()
for policy in ['true','false']:
    assert a == (out/('candidate-'+policy+'.bin')).read_bytes(), 'Frozen World elevation/picking outputs or exceptions differ'
print(f'PASS {len(a):,} bytes in each policy match frozen production World; no GPU/FPS claim')
