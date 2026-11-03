#!/usr/bin/env python3
"""Resolve output locations, check asset layout, and relocate legacy outputs."""
from __future__ import annotations

import argparse
import ast
import datetime
import hashlib
import json
import os
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def load_layout(root: Path = ROOT) -> dict:
    return json.loads((root / 'config/workspace-layout.json').read_text())


def output_path(category: str, parts: list[str], root: Path = ROOT) -> Path:
    layout = load_layout(root)
    if category not in layout['outputCategories']:
        raise ValueError('Unknown output category: ' + category)
    tail = Path(*parts)
    if tail.is_absolute() or '..' in tail.parts:
        raise ValueError('Output names must stay inside their category')
    path = root / layout['outputRoot'] / category / tail
    for parent in [path, *path.parents]:
        if parent == root:
            break
        if parent.is_symlink():
            raise ValueError('Output paths must not traverse symlinks')
    if not path.resolve().is_relative_to((root / layout['outputRoot'] / category).resolve()):
        raise ValueError('Output path escapes its category through a symlink')
    return path


def legacy_destination(name: str, layout: dict) -> str:
    mappings = layout['legacyOutputPaths']
    if name in mappings:
        return mappings[name]
    for old in sorted(mappings, key=len, reverse=True):
        if old.endswith('-') and name.startswith(old):
            return mappings[old] + name[len(old):]
    if name.endswith(('.png', '.png~', '.gif', '.zip')):
        stem = re.sub(r'\.(?:png|gif|zip)~?$', '', name, flags=re.IGNORECASE)
        slug = re.sub(r'[^a-z0-9]+', '-', stem.lower()).strip('-')
        if any(x in slug for x in ('bangel', 'bengal', 'bangle')):
            family = 'items/bangle'
        elif 'medallion' in slug:
            family = 'items/medallion'
        elif 'pitchfork' in slug:
            family = 'equipment/demon-pitchfork'
        elif 'shears' in slug:
            family = 'equipment/shears'
        elif 'shuriken' in slug:
            family = 'items/shuriken'
        elif 'dragon-metal' in slug:
            family = 'items/dragon-metal'
        elif any(x in slug for x in ('flower', 'mushroom', 'fungus', 'fern')):
            family = 'items/plants'
        elif 'guthix' in slug:
            family = 'animations/guthix'
        else:
            family = 'drafts/' + slug
        return 'art/' + family + '/' + name
    raise ValueError('Unclassified legacy output; choose its destination explicitly: ' + name)


def inventory(path: Path) -> dict:
    if path.is_symlink():
        raise ValueError('Review symlinks before moving output: ' + str(path))
    records = []
    paths = sorted(path.rglob('*')) if path.is_dir() else [path]
    for p in paths:
        if p.is_symlink():
            link = os.readlink(p)
            if Path(link).is_absolute() or not p.resolve(strict=True).is_relative_to(path.resolve()):
                raise ValueError('Review escaping/absolute symlinks before moving output: ' + str(p))
            s = p.lstat()
            records.append([str(p.relative_to(path)), s.st_dev, s.st_ino,
                            s.st_size, s.st_mtime_ns, s.st_mode, 'internal-symlink', link])
            continue
        if p.is_file():
            s = p.stat()
            records.append([str(p.relative_to(path)) if p != path else '.', s.st_dev,
                            s.st_ino, s.st_size, s.st_mtime_ns, s.st_mode])
    return {'files': len(records), 'bytes': sum(x[3] for x in records),
            'identitySha256': hashlib.sha256(json.dumps(records).encode()).hexdigest()}


def assert_inactive(paths: list[Path]) -> None:
    prefixes = [str(p) for p in paths]
    for proc in Path('/proc').iterdir():
        if not proc.name.isdigit():
            continue
        try:
            if proc.stat().st_uid != os.getuid():
                continue
            refs = [os.readlink(proc / 'cwd'), os.readlink(proc / 'exe')]
            for fd in (proc / 'fd').iterdir():
                try:
                    refs.append(os.readlink(fd))
                except OSError:
                    pass
            if any(r == p or r.startswith(p + '/') for r in refs for p in prefixes):
                raise ValueError('An output is in use by PID ' + proc.name)
        except OSError:
            pass


def initialize_output(root: Path = ROOT) -> None:
    layout = load_layout(root)
    out = root / layout['outputRoot']
    out.mkdir(exist_ok=True)
    readme = out / 'README.txt'
    if readme.exists() and not readme.read_text().startswith('GENERATED OUTPUTS\n'):
        raise ValueError('Preserve the existing output README before initializing the generated index')
    lines = ['GENERATED OUTPUTS', '', 'Editable art: ' + layout['authoringAssets'],
             'Game assets: ' + layout['runtimeAssets'], 'References: ' + layout['referenceAssets'], '',
             'Choose a category for every new job. Keep related files together by subject or suite.', '']
    for name, description in layout['outputCategories'].items():
        output_path(name, [], root).mkdir(exist_ok=True)
        lines.append(name + '/ — ' + description)
    lines += ['', 'Resolve a destination: python3 scripts/workspace-layout.py path CATEGORY SUBJECT',
              'Check this tree: python3 scripts/workspace-layout.py check',
              'Existing moves are recorded under archive/layout-migrations/.',
              'Ignored files are not automatically disposable. No automatic pruning is installed.', '']
    (out / 'README.txt').write_text('\n'.join(lines))


def migrate_outputs(apply: bool, root: Path = ROOT) -> list[dict]:
    if root.resolve() == Path('/tmp/spoiled-milk-live-main'):
        raise ValueError('Do not reorganize a deployed runtime')
    layout = load_layout(root)
    out = root / layout['outputRoot']
    if out.is_symlink():
        raise ValueError('Output root must not be a symlink')
    readme = out / 'README.txt'
    if readme.exists() and not readme.read_text().startswith('GENERATED OUTPUTS\n'):
        raise ValueError('Preserve the existing output README before migration')
    moves = []
    for p in sorted(out.iterdir()) if out.exists() else []:
        if p.name in layout['outputCategories'] or p.name == 'README.txt':
            continue
        relative = legacy_destination(p.name, layout)
        target = out / relative
        if target.exists() or target.is_symlink():
            raise ValueError('Destination already exists; preserve and reconcile it explicitly: ' + str(target))
        category, *parts = Path(relative).parts
        if output_path(category, parts, root) != target:
            raise ValueError('Non-canonical migration target')
        moves.append({'old': str(p.relative_to(root)), 'new': str(target.relative_to(root)),
                      'inventory': inventory(p)})
    for old, relative in layout.get('externalOutputPaths', {}).items():
        if old != 'Client_Base/renderer-v2-captures':
            raise ValueError('Unsupported external output relocation: ' + old)
        source = root / old
        if not source.exists() and not source.is_symlink():
            continue
        category, *parts = Path(relative).parts
        target = output_path(category, parts, root)
        if target.exists() or target.is_symlink():
            raise ValueError('External output destination already exists: ' + str(target))
        moves.append({'old': old, 'new': str(target.relative_to(root)), 'inventory': inventory(source)})
    assert_inactive([root / m['old'] for m in moves])
    if not apply:
        return moves
    if not moves:
        initialize_output(root)
        return []
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ')
    journal = out / 'archive/layout-migrations' / stamp
    journal.mkdir(parents=True)
    (journal / 'plan.json').write_text(json.dumps(moves, indent=2) + '\n')
    completed = []
    with (journal / 'completed.jsonl').open('x') as receipt:
        for move in moves:
            source, target = root / move['old'], root / move['new']
            if inventory(source) != move['inventory']:
                raise ValueError('Output changed since planning: ' + str(source))
            target.parent.mkdir(parents=True, exist_ok=True)
            source.rename(target)
            if inventory(target) != move['inventory']:
                raise ValueError('Output identity changed after move: ' + str(target))
            receipt.write(json.dumps(move) + '\n')
            receipt.flush()
            os.fsync(receipt.fileno())
            completed.append(move)
    initialize_output(root)
    return completed


def check(root: Path = ROOT) -> list[str]:
    layout = load_layout(root)
    errors = []
    compatibility = root / 'dev/myworld'
    for p in compatibility.iterdir():
        if p.name != 'assets' and p.name not in layout['compatibilityFiles']:
            errors.append('New work belongs in assets or its owning module, not dev/myworld: ' + p.name)
    assets = root / layout['assetRoot']
    for p in assets.iterdir():
        if p.name not in {'runtime', 'authoring', 'references', 'README.md', 'CREDITS.txt'}:
            errors.append('Unexpected asset-root entry: ' + str(p.relative_to(root)))
    for p in (root / layout['runtimeAssets']).rglob('*'):
        if p.suffix.lower() in {'.aseprite', '.psd', '.lua', '.py', '.java'} or p.name.endswith('~'):
            errors.append('Authoring/work file in runtime assets: ' + str(p.relative_to(root)))
    out = root / layout['outputRoot']
    if out.exists():
        for p in out.iterdir():
            if p.name not in layout['outputCategories'] and p.name != 'README.txt':
                errors.append('Unclassified output-root entry: ' + str(p.relative_to(root)))
    for old in ['sprites/UI', 'sprites/ui', 'ui', 'animations', 'legacy animation folder', 'remastered-sprites']:
        if (assets / old).exists():
            errors.append('Old duplicate asset hierarchy: ' + str((assets / old).relative_to(root)))
    for old in ['sprites/UI', 'sprites/ui']:
        if (root / layout['runtimeAssets'] / old).exists():
            errors.append('Game UI belongs in runtime/ui, not under sprites: ' + old)
    errors.extend(check_writer_defaults(root, layout))
    for old in layout.get('externalOutputPaths', {}):
        if (root / old).exists():
            errors.append('Legacy diagnostic output requires relocation: ' + old)
    return errors


def check_writer_defaults(root: Path, layout: dict) -> list[str]:
    """Catch new ad hoc output defaults in maintained producers, including pathlib joins."""
    allowed = set(layout['outputCategories'])
    errors = set()
    sources = [*root.glob('scripts/**/*.py'), *root.glob('scripts/**/*.sh'),
               *root.glob('tools/**/*.py'), *root.glob('tools/**/*.sh'),
               root / 'Client_Base/build.xml', root / 'server/build.xml']
    sources = [p for p in sources if not any(x in p.parts for x in ('workspace', 'build', 'vendor', '__pycache__'))]

    def parts(node):
        if isinstance(node, ast.BinOp) and isinstance(node.op, ast.Div):
            return parts(node.left) + parts(node.right)
        if isinstance(node, ast.Constant) and isinstance(node.value, str):
            return [node.value]
        return [None]

    for p in sources:
        if not p.is_file():
            continue
        text = p.read_text()
        if p.suffix == '.py':
            tree = ast.parse(text, filename=str(p))
            for node in ast.walk(tree):
                if isinstance(node, ast.Constant) and isinstance(node.value, str):
                    for match in re.finditer(r'(?:^|/)output/([a-zA-Z][a-zA-Z0-9_-]*)', node.value):
                        if match.group(1) not in allowed:
                            errors.add(str(p.relative_to(root)) + ': noncanonical output default ' + match.group(1))
                if isinstance(node, ast.BinOp) and isinstance(node.op, ast.Div):
                    values = parts(node)
                    for index, value in enumerate(values[:-1]):
                        child = values[index + 1]
                        if value == 'output' and isinstance(child, str) and child.split('/')[0] not in allowed:
                            errors.add(str(p.relative_to(root)) + ': noncanonical pathlib output default ' + child)
        else:
            for match in re.finditer(r'output/([a-zA-Z][a-zA-Z0-9_-]*)', text):
                if match.group(1) not in allowed:
                    errors.add(str(p.relative_to(root)) + ': noncanonical output default ' + match.group(1))
    return sorted(errors)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    p = sub.add_parser('path')
    p.add_argument('category')
    p.add_argument('parts', nargs='*')
    p.add_argument('--mkdir', action='store_true')
    sub.add_parser('init-output')
    p = sub.add_parser('migrate-output')
    p.add_argument('--apply', action='store_true')
    sub.add_parser('check')
    args = parser.parse_args()
    try:
        if args.command == 'path':
            path = output_path(args.category, args.parts)
            if args.mkdir:
                path.mkdir(parents=True, exist_ok=True)
            print(path)
        elif args.command == 'init-output':
            initialize_output()
        elif args.command == 'migrate-output':
            moves = migrate_outputs(args.apply)
            print(json.dumps({'mode': 'applied' if args.apply else 'dry-run', 'moves': moves}, indent=2))
        else:
            errors = check()
            if errors:
                print('\n'.join(errors))
                return 1
            print('PASS canonical asset and output layout')
    except (ValueError, OSError) as error:
        parser.exit(1, str(error) + '\n')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
