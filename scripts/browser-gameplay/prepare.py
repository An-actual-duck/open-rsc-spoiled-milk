#!/usr/bin/env python3
"""Stage an isolated browser client, never owner credentials or player saves."""
import argparse
import datetime
import hashlib
import json
from pathlib import Path
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name', default=datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
    parser.add_argument('--http-port', type=int, default=8079)
    parser.add_argument('--client-source-commit', help='Explicit provenance for the selected immutable client JAR')
    parser.add_argument('--dependency-jar', type=Path, default=ROOT / 'Client_Base/Open_RSC_Client.jar', help='Explicit maintained client artifact for Bootstrap compilation and staging')
    args = parser.parse_args()
    if not 1 <= args.http_port <= 65535:
        parser.error('Port must be between 1 and 65535')
    if not args.name or any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_' for c in args.name):
        parser.error('Use a simple run name')
    category = subprocess.check_output(['python3', str(ROOT / 'scripts/workspace-layout.py'), 'path', 'tests', 'browser-gameplay-spike', '--mkdir'], text=True).strip()
    run = Path(category) / args.name
    if run.exists():
        parser.error('Run already exists; prior evidence is preserved')
    run.mkdir()
    web = run / 'web'; web.mkdir()
    for file in (ROOT / 'tools/browser-gameplay/web').iterdir():
        shutil.copy2(file, web / file.name)
    client = args.dependency_jar.expanduser().resolve()
    if not client.is_file():
        parser.error('Build the maintained client first with scripts/build-client.sh')
    classes = run / 'classes'; classes.mkdir()
    sources = sorted((ROOT / 'tools/browser-gameplay/src/orsc/browser').rglob('*.java'))
    subprocess.run(['javac', '-source', '8', '-target', '8', '-cp', str(client), '-d', str(classes)] + list(map(str, sources)), check=True)
    subprocess.run(['jar', 'cf', str(web / 'browser-spike.jar'), '-C', str(classes), '.'], check=True)
    game = web
    shutil.copy2(client, game / 'client.jar')
    cache = game / 'Cache'; cache.mkdir()
    for name in ('audio', 'video'):
        shutil.copytree(ROOT / 'Client_Base/Cache' / name, cache / name)
    # Read the maintained config, not any locally saved account/client settings.
    config = subprocess.check_output(['git', 'show', 'HEAD:Client_Base/Cache/config.txt'], cwd=ROOT)
    (cache / 'config.txt').write_bytes(config)
    (cache / 'ip.txt').write_text('127.0.0.1\n')
    (cache / 'port.txt').write_text('43629\n')
    (cache / 'credentials.txt').write_text('\n')
    (cache / 'discord_inuse.txt').write_text('1\n')
    (cache / 'uid.dat').write_bytes(bytes(8))
    profile = ROOT / 'Client_Base/world-builder-configs/installed-client.json'
    descriptor = json.loads(profile.read_text())
    relative = Path(descriptor['packageRelativePath'])
    assert not relative.is_absolute() and '..' not in relative.parts
    (game / 'world-builder-configs').mkdir()
    shutil.copy2(profile, game / 'world-builder-configs/installed-client.json')
    (game / relative).mkdir(parents=True)
    shutil.copy2(ROOT / 'Client_Base' / relative / 'manifest.json', game / relative / 'manifest.json')
    # The maintained native client validates this manifest and receives terrain
    # from the private server. Its local sector files are not read by gameplay.
    files = sorted(p.relative_to(web).as_posix() for p in web.rglob('*') if p.is_file() and p.relative_to(web).parts[0] in ('Cache', 'world-builder', 'world-builder-configs'))
    (web / 'bootstrap-files.txt').write_text('\n'.join(files) + '\n')
    report = dict(root=str(ROOT), run=str(run), web=str(web), sourceCommit=subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
                  clientSourceCommit=args.client_source_commit, dependencyJar=str(client), clientSha256=hashlib.sha256(client.read_bytes()).hexdigest(), runtime='CheerpJ 5.0, Java 8, CDN',
                  http='127.0.0.1:' + str(args.http_port), tcp='127.0.0.1:43629', websocket='127.0.0.1:43529', credentialsCopied=False)
    (run / 'stage.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
