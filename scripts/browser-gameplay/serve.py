#!/usr/bin/env python3
"""Serve staged private browser files and optionally collect local travel timings."""
import argparse
import functools
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import re
from threading import Lock


MAX_TRACE_BATCH = 16 * 1024
MAX_TRACE_FILE = 16 * 1024 * 1024


class Handler(SimpleHTTPRequestHandler):
    def __init__(self, *args, travel_log=None, trace_lock=None, **kwargs):
        self.travel_log = travel_log
        self.trace_lock = trace_lock
        super().__init__(*args, **kwargs)

    def do_POST(self):
        if self.travel_log is None or self.path != '/private-travel-timings':
            self.send_error(404)
            return
        if self.headers.get('Origin') != f'http://127.0.0.1:{self.server.server_port}':
            self.send_error(403)
            return
        try:
            length = int(self.headers.get('Content-Length', '0'))
            if not 0 < length <= MAX_TRACE_BATCH:
                raise ValueError('Invalid timing batch size')
            self.connection.settimeout(5)
            batch = json.loads(self.rfile.read(length))
            if not isinstance(batch, dict) or not isinstance(batch.get('rows'), list):
                raise ValueError('Invalid timing batch')
        except (ValueError, OSError):
            self.send_error(400)
            return
        line = json.dumps(batch, separators=(',', ':')) + '\n'
        with self.trace_lock:
            with self.travel_log.open('a', encoding='utf-8') as log:
                if log.tell() + len(line) <= MAX_TRACE_FILE:
                    log.write(line)
        self.send_response(204)
        self.end_headers()

    def do_HEAD(self):
        self.do_GET()

    def do_GET(self):
        file = Path(self.translate_path(self.path)).resolve()
        root = Path(self.directory).resolve()
        if file.is_relative_to(root) and file.is_dir():
            return super().do_HEAD() if self.command == 'HEAD' else super().do_GET()
        if not file.is_relative_to(root) or not file.is_file():
            if self.path.split('?', 1)[0] == '/':
                file = root / 'index.html'
            else:
                self.send_error(404); return
        size = file.stat().st_size
        start, end = 0, size - 1
        range_header = self.headers.get('Range')
        if range_header:
            match = re.fullmatch(r'bytes=(\d+)-(\d*)', range_header)
            if not match:
                self.send_error(416); return
            start = int(match[1]); end = min(int(match[2]) if match[2] else size - 1, size - 1)
            if start > end:
                self.send_response(416); self.send_header('Content-Range', f'bytes */{size}'); self.end_headers(); return
        self.send_response(206 if range_header else 200)
        self.send_header('Content-Type', self.guess_type(str(file)))
        self.send_header('Accept-Ranges', 'bytes')
        self.send_header('Content-Length', str(max(0, end - start + 1)))
        self.send_header('Cache-Control', 'no-cache')
        if range_header:
            self.send_header('Content-Range', f'bytes {start}-{end}/{size}')
        self.end_headers()
        if self.command == 'HEAD':
            return
        with file.open('rb') as source:
            source.seek(start)
            remaining = end - start + 1
            try:
                while remaining > 0:
                    block = source.read(min(65536, remaining))
                    if not block: break
                    self.wfile.write(block); remaining -= len(block)
            except (BrokenPipeError, ConnectionResetError):
                pass

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('web', type=Path)
    parser.add_argument('--port', type=int, default=8079)
    parser.add_argument('--travel-log', type=Path, help='Persist travel timings in the private stage directory')

    args = parser.parse_args()
    if not 1 <= args.port <= 65535:
        parser.error('Port must be between 1 and 65535')
    web = args.web.resolve()
    if not (web / 'browser-spike.jar').is_file():
        parser.error('Select a staged web directory from prepare.py')
    travel_log = args.travel_log.resolve() if args.travel_log else None
    if travel_log and (travel_log.parent != web.parent or not (web / 'travel-report.js').is_file()):
        parser.error('Travel logging requires a manual travel stage and a log outside its web directory')
    handler = functools.partial(Handler, directory=str(web), travel_log=travel_log, trace_lock=Lock())
    server = ThreadingHTTPServer(('127.0.0.1', args.port), handler)
    print(f'Private browser test: http://127.0.0.1:{args.port}', flush=True)
    server.serve_forever()


if __name__ == '__main__':
    main()
