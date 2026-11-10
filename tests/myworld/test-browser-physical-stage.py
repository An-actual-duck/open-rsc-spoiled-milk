#!/usr/bin/env python3
"""Read-only validation of a prepared explicit-artifact stage. No process or GPU launch."""
from pathlib import Path
import hashlib,json,re,sys
root=Path(__file__).resolve().parents[2]
stage=Path(sys.argv[1]).resolve();data=json.loads((stage/'stage.json').read_text());web=stage/'web'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
assert re.fullmatch(r'[0-9a-f]{40}',data['clientSourceCommit'])
assert sha(web/'client.jar')==data['clientSha256']==sha(Path(data['dependencyJar']))
assert data['credentialsCopied'] is False and (web/'Cache/credentials.txt').read_bytes()==b'\n'
assert (web/'Cache/ip.txt').read_text()=='127.0.0.1\n' and (web/'Cache/port.txt').read_text()=='43629\n'
assert (web/'Cache/uid.dat').read_bytes()==bytes(8)
for name,digest in data['frontendSha256'].items():
 assert sha(web/name)==digest
 assert sha(root/'tools/browser-gameplay/web'/name)==digest
assert 'browser-physical-output.js' in data['frontendSha256']
assert (web/'browser-spike.jar').stat().st_size>0
assert not any(p.is_dir() and p.name.lower() in ('players','accounts','credentials') for p in web.rglob('*'))
print('PASS prepared gameplay explicit JAR/source/frontend hashes, compiled Bootstrap, physical module and private no-account configuration; no browser/server/GPU run.')
