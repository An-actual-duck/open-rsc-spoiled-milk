#!/usr/bin/env python3
"""Offline signed-inventory/config contract negatives. Never invokes nginx or a host service."""
import argparse,copy,hashlib,importlib.util,json,pathlib,tempfile
p=argparse.ArgumentParser();p.add_argument('--out',type=pathlib.Path,required=True);a=p.parse_args();assert not a.out.exists();a.out.mkdir(parents=True)
root=pathlib.Path(__file__).resolve().parents[3];spec=importlib.util.spec_from_file_location('hosting',root/'scripts/browser-gameplay/prepare-oracle-hosting.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
checks=0
web=a.out/'web';(web/'assets/test').mkdir(parents=True)
(web/'index.html').write_text('<script src="/assets/test/session.js"></script>');(web/'assets/test/session.js').write_text('export {};');(web/'SHA256SUMS').write_text('fixture checksum file')
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
m=dict(schemaVersion=1,buildId='test',origin=module.ORIGIN,websocket=module.WSS,preset='alpha-v1',assetPrefix='/assets/test/',sourceAndCookBindingsVerified=True,sha256SumsSha256=sha(web/'SHA256SUMS'),files=[dict(path=n,bytes=(web/n).stat().st_size,sha256=sha(web/n))for n in ('index.html','assets/test/session.js')])
def save(data):
 path=web/'public-release.json';path.write_text(json.dumps(data));return sha(path)
def verify(data):global checks;result=module.verify(web,save(data));checks+=1;return result
verify(m)
def refused(data):
 global checks
 try:verify(data)
 except (ValueError,TypeError):checks+=1;return
 raise AssertionError('Malformed inventory accepted')
for key,value in [('origin','https://other.example'),('websocket','ws://127.0.0.1/'),('assetPrefix','/assets/other/'),('sourceAndCookBindingsVerified',False),('buildId','../bad')]:
 bad=copy.deepcopy(m);bad[key]=value;refused(bad)
for path in ('../escape','/index.html','assets/test/../session.js','assets/test/%2e%2e/key'):
 bad=copy.deepcopy(m);bad['files'][0]['path']=path;refused(bad)
bad=copy.deepcopy(m);bad['files'].append(bad['files'][0]);refused(bad)
(web/'secret.key').write_text('synthetic marker, not a real key');refused(m);(web/'secret.key').unlink()
(web/'assets/test/session.js').write_text('tampered');refused(m);(web/'assets/test/session.js').write_text('export {};')
(web/'assets/test/extra-link').symlink_to('/tmp');refused(m);(web/'assets/test/extra-link').unlink()
nginx=(root/'config/browser-hosting/nginx-play.conf').read_text();hook=(root/'config/browser-hosting/certbot-deploy-hook.sh').read_text()
assert 'proxy_pass'not in nginx and 'max_ranges 1;'in nginx and 'immutable'in nginx and 'no-cache'in nginx;checks+=1
assert 'systemctl reload nginx'in hook and 'reloadsslcert'not in hook and 'stop'in hook.splitlines()[1] and 'systemctl restart'not in hook;checks+=1
(a.out/'result.json').write_text(json.dumps(dict(passed=True,checks=checks,limits='Inventory and source config contracts only. Root must run nginx -t and real HTTP Range/cache/TLS smoke after installation.'),indent=2)+'\n');print('PASS checks='+str(checks))
