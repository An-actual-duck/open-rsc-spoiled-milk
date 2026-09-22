#!/usr/bin/env python3
"""Definition-only migration: preserve every field except tradability on 35 old IDs."""
import json
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RETIRED = set(range(1807, 1817)) | set(range(1875, 1885)) | set(range(1895, 1905)) | set(range(1915, 1920))

def main():
    path = 'server/conf/server/defs/ItemDefsCustom.json'
    current = {x['id']: x for x in json.loads((ROOT / path).read_text())['items']}
    # Last published pre-retirement implementation; immutable preservation baseline.
    previous = {x['id']: x for x in json.loads(subprocess.check_output(
        ['git', 'show', 'f8c794e65:' + path], cwd=ROOT))['items']}
    for item_id in RETIRED:
        expected = dict(previous[item_id], isUntradable=1)
        assert current[item_id] == expected, ('legacy definition changed beyond binding', item_id)
    jar = ROOT / 'Client_Base/Open_RSC_Client.jar'
    with tempfile.TemporaryDirectory(prefix='retired-leather-test-') as out:
        subprocess.run(['javac', '-cp', str(jar), '-d', out,
                        str(ROOT / 'tests/myworld/RetiredLeatherItemFixture.java')], check=True)
        subprocess.run(['java', '-cp', f'{out}:{jar}', 'RetiredLeatherItemFixture'],
                       cwd=ROOT / 'Client_Base', check=True)
    print('PASS: 35 legacy server definitions only change tradability; client and note forms match')

if __name__ == '__main__':
    main()
