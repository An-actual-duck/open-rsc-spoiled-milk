#!/usr/bin/env python3
"""Read-only public package verification and offline Oracle config bundle. Never deploys."""
import argparse,hashlib,json,pathlib,re,shutil
ROOT=pathlib.Path(__file__).resolve().parents[2]
ORIGIN='https://play.rscspoiledmilk.com';WSS='wss://play.rscspoiledmilk.com:43505/'
def sha(path):
 h=hashlib.sha256()
 with path.open('rb')as f:
  for data in iter(lambda:f.read(1024*1024),b''):h.update(data)
 return h.hexdigest()
def verify(web,expected):
 manifest=web/'public-release.json'
 if not re.fullmatch('[0-9a-f]{64}',expected)or sha(manifest)!=expected:raise ValueError('Public release manifest hash mismatch')
 m=json.loads(manifest.read_text());build=m['buildId']
 if not re.fullmatch('[a-zA-Z0-9_-]{1,80}',build):raise ValueError('Unsafe build ID')
 prefix='assets/'+build+'/'
 if m['schemaVersion']!=1 or m['origin']!=ORIGIN or m['websocket']!=WSS or m['preset']!='alpha-v1' or m['assetPrefix']!='/'+prefix or m['sourceAndCookBindingsVerified']is not True:raise ValueError('Public endpoint/source contract mismatch')
 seen=set();total=0
 for row in m['files']:
  path=row['path'];p=pathlib.PurePosixPath(path)
  if not isinstance(path,str)or not re.fullmatch(r'[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.-]+)*',path)or p.is_absolute()or any(part in ('..','.')for part in p.parts)or str(p)!=path or path in seen:raise ValueError('Unsafe/duplicate inventory path')
  if path!='index.html'and not path.startswith(prefix):raise ValueError('Unversioned public asset')
  actual=web/path
  if any(part.is_symlink()for part in [actual,*actual.parents]if part!=web.parent)or not actual.is_file():raise ValueError('Public symlink/missing asset')
  if actual.stat().st_size!=row['bytes']or sha(actual)!=row['sha256']:raise ValueError('Public payload bytes/hash mismatch: '+path)
  seen.add(path);total+=row['bytes']
 if 'index.html'not in seen or sha(web/'SHA256SUMS')!=m['sha256SumsSha256']:raise ValueError('Root/checksum binding missing')
 if any(p.is_symlink()for p in web.rglob('*')):raise ValueError('Public tree contains symlink')
 actual={str(p.relative_to(web))for p in web.rglob('*')if p.is_file()}
 if actual!=seen|{'public-release.json','SHA256SUMS'}:raise ValueError('Undeclared public files')
 return m,total
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--web',type=pathlib.Path,required=True);p.add_argument('--manifest-sha256',required=True);p.add_argument('--out',type=pathlib.Path,required=True);a=p.parse_args()
 web=a.web.absolute();m,total=verify(web,a.manifest_sha256)
 if a.out.exists():raise ValueError('Preserve previous preparation')
 a.out.mkdir(parents=True)
 for name in ('nginx-play.conf','certbot-deploy-hook.sh'):shutil.copyfile(ROOT/'config/browser-hosting'/name,a.out/name)
 plan=dict(schemaVersion=1,preparedOnly=True,liveOperations=False,origin=ORIGIN,websocket=WSS,subprotocol='binary',buildId=m['buildId'],files=len(m['files']),bytes=total,releaseManifestSha256=a.manifest_sha256,staticRoot='/srv/spoiled-milk-browser',releaseDirectory='/srv/spoiled-milk-browser/releases/'+m['buildId'],immutableAssetDirectory='/srv/spoiled-milk-browser/assets/'+m['buildId'],currentRootMustBeSymlink=True,serverSystemProperties={'spoiledmilk.publicWebSocketOrigin':ORIGIN,'spoiledmilk.publicWebSocketCert':'/home/ubuntu/spoiled-milk/tls/current/fullchain.pem','spoiledmilk.publicWebSocketKey':'/home/ubuntu/spoiled-milk/tls/current/key.pem'},hostOperationsRequireManager=True,configSha256={n:sha(a.out/n)for n in ('nginx-play.conf','certbot-deploy-hook.sh')})
 (a.out/'deployment-plan.json').write_text(json.dumps(plan,indent=2)+'\n');print(json.dumps(plan))
