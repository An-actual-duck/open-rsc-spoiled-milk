#!/usr/bin/env python3
"""Gracefully stop only the marked private browser fixture and its HTTP server."""
import argparse
import json
import os
from pathlib import Path
import signal
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__); parser.add_argument('run', type=Path)
    args = parser.parse_args(); run = args.run.resolve()
    if not (run / 'stage.json').is_file() or not (run / 'server/BROWSER-PRIVATE.json').is_file(): parser.error('Not a marked browser fixture')
    processes = json.loads((run / 'processes.json').read_text())
    pid = processes['server']; proc = Path('/proc') / str(pid)
    if proc.exists():
        if (proc / 'cwd').resolve() != run / 'server' or b'BrowserPrivateServer' not in (proc / 'cmdline').read_bytes(): parser.error('Recorded server PID no longer belongs to this fixture')
        (run / 'STOP').write_text('Graceful private browser test shutdown\n')
        for _ in range(100):
            if not (run / 'server.pid').exists(): break
            time.sleep(0.2)
        else: parser.error('Private server did not finish graceful shutdown; inspect logs. No signal was sent.')
    pid = processes['http']; proc = Path('/proc') / str(pid)
    if proc.exists():
        command = (proc / 'cmdline').read_bytes()
        if b'scripts/browser-gameplay/serve.py' not in command or str(run / 'web').encode() not in command: parser.error('Recorded HTTP PID no longer belongs to this fixture')
        os.kill(pid, signal.SIGTERM)
    print('Private browser fixture stopped. All data and evidence retained: ' + str(run))


if __name__ == '__main__': main()
