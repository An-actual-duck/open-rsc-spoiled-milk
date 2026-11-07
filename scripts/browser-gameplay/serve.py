#!/usr/bin/env python3
"""Loopback-only, read-only range server for the staged private browser test."""
import argparse
import functools
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import re


class Handler(SimpleHTTPRequestHandler):
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
    args = parser.parse_args()
    web = args.web.resolve()
    if not (web / 'browser-spike.jar').is_file():
        parser.error('Select a staged web directory from prepare.py')
    server = ThreadingHTTPServer(('127.0.0.1', 8079), functools.partial(Handler, directory=str(web)))
    print('Private browser test: http://127.0.0.1:8079', flush=True)
    server.serve_forever()


if __name__ == '__main__':
    main()
