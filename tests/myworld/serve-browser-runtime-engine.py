#!/usr/bin/env python3
"""Explicit manager-run loopback service for a hash-guarded runtime-only probe."""
import argparse
import functools
import hashlib
from http.server import ThreadingHTTPServer
import importlib.util
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

def verify(stage):
    stage = Path(stage).resolve()
    metadata = json.loads((stage/'stage.json').read_text())
    if metadata.get('runtimeEngineProbe') != 1 or not metadata.get('preparedOnly'):
        raise ValueError('Select a prepared runtime-engine probe')
    web = stage/'web'
    expected={'/probe.jar','/index.html','/browser-native-runtime-probe.js','/browser-native-call-dispatch.js'}
    if any(p.is_dir() for p in web.iterdir()) or set(metadata['webFiles']) != expected or expected != {'/'+p.name for p in web.iterdir() if p.is_file()}:
        raise ValueError('Unmanifested web files')
    for name, record in metadata['webFiles'].items():
        if name not in ['/probe.jar','/index.html','/browser-native-runtime-probe.js','/browser-native-call-dispatch.js']:
            raise ValueError('Unexpected served filename')
        path = web/name.lstrip('/')
        if path.is_symlink() or path.stat().st_size != record['bytes'] or hashlib.sha256(path.read_bytes()).hexdigest() != record['sha256']:
            raise ValueError('Staged file changed: '+name)
    return web

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('stage',type=Path);parser.add_argument('--port',required=True,type=int);args=parser.parse_args()
    if not 1024<args.port<65536:parser.error('Use an explicit unprivileged port')
    web=verify(args.stage)
    spec=importlib.util.spec_from_file_location('runtime_probe_range',ROOT/'scripts/browser-gameplay/serve.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    server=ThreadingHTTPServer(('127.0.0.1',args.port),functools.partial(module.Handler,directory=str(web)))
    print('Verified runtime-only loopback range probe: http://127.0.0.1:'+str(args.port),flush=True)
    server.serve_forever()

if __name__=='__main__':main()
