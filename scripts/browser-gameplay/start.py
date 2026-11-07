#!/usr/bin/env python3
"""Start a fresh, disposable loopback browser test; prints its review URL."""
import argparse
import json
from pathlib import Path
import socket
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('--name', required=True)
    args = parser.parse_args()
    for port in (8079, 43629, 43529):
        with socket.socket() as probe:
            try: probe.bind(('127.0.0.1', port))
            except OSError: parser.error(f'Private port {port} is occupied. Stop that test before starting another.')
    base = Path(subprocess.check_output(['python3', str(ROOT / 'scripts/workspace-layout.py'), 'path', 'tests', 'browser-gameplay-spike', '--mkdir'], text=True).strip())
    if not args.name or any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_' for c in args.name): parser.error('Use a simple run name')
    run = base / args.name
    if run.exists(): parser.error('That run exists; choose a fresh name to preserve previous data')
    # Ordinary maintained builds, not source overlays or the public runner.
    for script in ('build-client.sh', 'build-server.sh'):
        subprocess.run([str(ROOT / 'scripts' / script)], cwd=ROOT, check=True)
    subprocess.run(['python3', str(ROOT / 'scripts/browser-gameplay/prepare.py'), '--name', args.name], cwd=ROOT, check=True)
    subprocess.run(['python3', str(ROOT / 'scripts/browser-gameplay/prepare-server.py'), str(run)], cwd=ROOT, check=True)
    command = json.loads((run / 'server-command.json').read_text())
    with (run / 'server.log').open('wb') as log:
        game = subprocess.Popen(command['command'], cwd=command['cwd'], stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    with (run / 'http.log').open('wb') as log:
        web = subprocess.Popen(['python3', str(ROOT / 'scripts/browser-gameplay/serve.py'), str(run / 'web')], cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
    (run / 'processes.json').write_text(json.dumps(dict(server=game.pid, http=web.pid, root=str(ROOT)), indent=2) + '\n')
    for _ in range(120):
        if game.poll() is not None or web.poll() is not None: raise SystemExit('Private startup failed; inspect logs in ' + str(run))
        try:
            with socket.create_connection(('127.0.0.1', 43629), timeout=0.2): pass
            if (run / 'status.json').is_file(): break
        except OSError: pass
        time.sleep(0.5)
    else: raise SystemExit('Private startup timed out; inspect ' + str(run))
    print('\nREADY http://127.0.0.1:8079/?mode=software\nDisposable login: browserdemo / BrowserProof13\nRun: ' + str(run), flush=True)


if __name__ == '__main__': main()
