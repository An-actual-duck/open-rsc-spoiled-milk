#!/usr/bin/env python3
"""Preserve 35 retired IDs plus the five separately approved Ogre descriptions."""
import json
import subprocess
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RETIRED = set(range(1807, 1817)) | set(range(1875, 1885)) | set(range(1895, 1905)) | set(range(1915, 1920))

def validate_retired_definitions(current, previous, approved_stagger):
    for item_id in RETIRED:
        expected = dict(previous[item_id], isUntradable=1)
        # Compose only this later reviewed description change. Never refresh
        # the entire preservation baseline or exempt any other item field.
        if item_id in range(1880, 1885):
            expected["description"] = approved_stagger[item_id]["description"]
        assert current[item_id] == expected, ('legacy definition changed beyond approved migration', item_id)


def verify_mutations_are_rejected(current, previous, approved_stagger):
    for item_id, field, value in (
        (1880, "description", "incorrect Stagger behavior"),
        (1880, "basePrice", -1),
        (1880, "isUntradable", 0),
        (1807, "description", "unapproved unrelated description"),
    ):
        mutated = dict(current)
        mutated[item_id] = dict(current[item_id], **{field: value})
        try:
            validate_retired_definitions(mutated, previous, approved_stagger)
        except AssertionError:
            continue
        raise AssertionError(('retirement check accepted unexpected mutation', item_id, field))


def main():
    path = 'server/conf/server/defs/ItemDefsCustom.json'
    current = {x['id']: x for x in json.loads((ROOT / path).read_text())['items']}
    # Last published pre-retirement implementation; immutable preservation baseline.
    previous = {x['id']: x for x in json.loads(subprocess.check_output(
        ['git', 'show', 'f8c794e65:' + path], cwd=ROOT))['items']}
    # Exact accepted shared Stagger integration; only descriptions on five IDs
    # are composed with the older retirement baseline above.
    approved_stagger = {x['id']: x for x in json.loads(subprocess.check_output(
        ['git', 'show', '5d8ce1695:' + path], cwd=ROOT))['items']}
    validate_retired_definitions(current, previous, approved_stagger)
    verify_mutations_are_rejected(current, previous, approved_stagger)
    jar = ROOT / 'Client_Base/Open_RSC_Client.jar'
    with tempfile.TemporaryDirectory(prefix='retired-leather-test-') as out:
        subprocess.run(['javac', '-cp', str(jar), '-d', out,
                        str(ROOT / 'tests/myworld/RetiredLeatherItemFixture.java')], check=True)
        subprocess.run(['java', '-cp', f'{out}:{jar}', 'RetiredLeatherItemFixture'],
                       cwd=ROOT / 'Client_Base', check=True)
    print('PASS: 35 retired definitions, five approved Ogre descriptions, mutation rejection, client and note forms')

if __name__ == '__main__':
    main()
