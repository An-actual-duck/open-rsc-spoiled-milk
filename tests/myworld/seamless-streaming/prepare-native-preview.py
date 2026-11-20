#!/usr/bin/env python3
"""Stage a disposable native client and private server for covered walking."""

import argparse
import hashlib
import json
from pathlib import Path
import shutil
import subprocess


ROOT = Path(__file__).resolve().parents[3]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('name')
    parser.add_argument('--tcp-port', type=int, required=True)
    parser.add_argument('--ws-port', type=int, required=True)
    parser.add_argument('--control', action='store_true')
    args = parser.parse_args()
    if not args.name or not all(c.isascii() and (c.isalnum() or c in '-_') for c in args.name):
        parser.error('Use a simple new run name')
    if not all(1024 <= port <= 65535 for port in (args.tcp_port, args.ws_port)) or args.tcp_port == args.ws_port:
        parser.error('Use two different nonprivileged loopback ports')

    category = Path(subprocess.check_output([
        'python3', str(ROOT / 'scripts/workspace-layout.py'), 'path',
        'tests', 'seamless-world-streaming', '--mkdir',
    ], text=True).strip())
    run = category / args.name
    run.mkdir()
    client = run / 'client'
    cache = client / 'Cache'
    cache.mkdir(parents=True)
    (run / 'home').mkdir()
    shutil.copy2(ROOT / 'Client_Base/Open_RSC_Client.jar', client / 'client.jar')
    for name in ('audio', 'video'):
        shutil.copytree(ROOT / 'Client_Base/Cache' / name, cache / name)
    config = subprocess.check_output(['git', 'show', 'HEAD:Client_Base/Cache/config.txt'], cwd=ROOT)
    (cache / 'config.txt').write_bytes(config)
    (cache / 'ip.txt').write_text('127.0.0.1\n')
    (cache / 'port.txt').write_text(f'{args.tcp_port}\n')
    (cache / 'credentials.txt').write_text('\n')
    profile = Path('world-builder-configs/installed-client.json')
    source = ROOT / 'Client_Base'
    package = Path(json.loads((source / profile).read_text())['packageRelativePath'])
    if package.is_absolute() or '..' in package.parts:
        raise ValueError('Expected tracked relative map package')
    for path in (profile, package / 'manifest.json'):
        (client / path).parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(source / path, client / path)

    artifact = client / 'client.jar'
    stage = {
        'sourceCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
        'clientJarSha256': hashlib.sha256(artifact.read_bytes()).hexdigest(),
        'tcp': f'127.0.0.1:{args.tcp_port}',
        'websocket': f'127.0.0.1:{args.ws_port}',
        'coveredWalkingPreview': not args.control,
    }
    (run / 'stage.json').write_text(json.dumps(stage, indent=2) + '\n')
    command = [
        'python3', str(ROOT / 'scripts/browser-gameplay/prepare-server.py'), str(run),
        '--tcp-port', str(args.tcp_port), '--ws-port', str(args.ws_port),
    ]
    if not args.control:
        command.append('--covered-walking-preview')
    subprocess.run(command, cwd=ROOT, check=True)

    launcher = run / 'Open Covered Walking Preview.sh'
    launcher.write_text('''#!/usr/bin/env bash
set -euo pipefail
run_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$run_dir/client"
exec java -Duser.home="$run_dir/home" -Xmx2g -jar client.jar
''')
    launcher.chmod(0o755)
    print(run)


if __name__ == '__main__':
    main()
