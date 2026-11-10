#!/usr/bin/env python3
"""Prepare ONLY an isolated production-combat browser fixture; never start processes."""
import argparse,hashlib,json,re,subprocess,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
FIXTURE='renderer-combat-v1'

def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def category(subject):return Path(subprocess.check_output([sys.executable,str(ROOT/'scripts/workspace-layout.py'),'path','tests',subject,'--mkdir'],text=True).strip())
def asset_audit(jar,asset_root):
    """Packaging only; deliberately does not assert browser ImageIO decode or visibility."""
    rows=[]
    with zipfile.ZipFile(jar)as archive:
        names=set(archive.namelist())
        for category in ('animations','legacy-animations'):
            for file in sorted((asset_root/category).rglob('*.png')):
                relative=file.relative_to(asset_root).as_posix();resource='myworld-assets/'+relative
                expected=sha(file);actual=hashlib.sha256(archive.read(resource)).hexdigest()if resource in names else None
                rows.append(dict(path=relative,sourceSha256=expected,packagedSha256=actual,identical=expected==actual))
    return dict(schema=1,scope='Exact runtime PNG packaging parity only; no browser decode/pixel/FPS claim',clientSha256=sha(jar),files=len(rows),missing=sum(r['packagedSha256']is None for r in rows),different=sum(r['packagedSha256']is not None and not r['identical']for r in rows),entries=rows)
def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--name',required=True);p.add_argument('--http-port',type=int,default=8169)
    p.add_argument('--dependency-jar',required=True,type=Path);p.add_argument('--client-source-commit',required=True)
    p.add_argument('--server-artifacts',required=True,type=Path);p.add_argument('--server-source',required=True)
    args=p.parse_args()
    if not re.fullmatch('[A-Za-z0-9_-]+',args.name):p.error('Simple unique name required')
    if not re.fullmatch('[0-9a-f]{40}',args.client_source_commit):p.error('Exact client source hash required')
    if not 1<=args.http_port<=65535:p.error('Invalid HTTP port')
    jar=args.dependency_jar.resolve();artifacts=args.server_artifacts.resolve()
    # Refuse existing evidence before either sub-preparer runs.
    browser_run=category('browser-gameplay-spike')/args.name
    server_run=category('renderer-shadow-benchmarks')/args.name
    if browser_run.exists()or server_run.exists():p.error('Run exists; preserve prior evidence and choose a new name')
    audit=asset_audit(jar,ROOT/'dev/myworld/assets/runtime')
    if audit['missing']or audit['different']:p.error('Client animation PNG packaging differs; use a reviewed matching artifact')
    subprocess.run([sys.executable,str(ROOT/'scripts/renderer-benchmark.py'),'prepare','--name',args.name,'--client-jar',str(jar),'--client-source',args.client_source_commit,
        '--server-artifacts',str(artifacts),'--server-core',str(artifacts/'core.jar'),'--server-overlay',str(artifacts/'core-gameplay-overlay.jar'),'--server-source',args.server_source],check=True,stdout=subprocess.DEVNULL)
    subprocess.run([sys.executable,str(ROOT/'scripts/browser-gameplay/prepare.py'),'--name',args.name,'--http-port',str(args.http_port),'--dependency-jar',str(jar),'--client-source-commit',args.client_source_commit],check=True,stdout=subprocess.DEVNULL)
    # The server consumes this ONLY when the manager explicitly starts it. It
    # remains combat-real until a deliberate request.json replaces the phase.
    request=dict(busy=True,phase='combat-real',x=502,y=453)
    (server_run/'request.json').write_text(json.dumps(request,indent=2)+'\n')
    (browser_run/'asset-parity.json').write_text(json.dumps(audit,indent=2)+'\n')
    (browser_run/'web/Cache/port.txt').write_text('43639\n')
    fixture=dict(schema=1,id=FIXTURE,account='renderdemo',spawn=[502,453,0],tcp=43639,websocket=43539,
        serverRun=str(server_run),request=request,phasePolicy='Explicit request.json only; no timed auto-switch',
        spells=['ICE_CRYSTAL','THUNDER_STRIKE','BATTERING_RAM'],otherChannels=['ranged','throwing','melee'],summonsRequested=8,
        camera=dict(zoom=75,rotation=128,sourcePitch=912,expectedSceneRotation=[112,512,0],pin='once after ready per login',drift=[0,0],driftIncrement=[0,0]),
        scope='Existing RendererBenchmarkServer production combat; no client-side fake effects or gameplay edits')
    (browser_run/'combat-fixture.json').write_text(json.dumps(fixture,indent=2)+'\n')
    stage=json.loads((browser_run/'stage.json').read_text());server=json.loads((server_run/'RENDERER-BENCHMARK.json').read_text())
    stage.update(fixture=FIXTURE,tcp='127.0.0.1:43639',websocket='127.0.0.1:43539',preparedOnly=True,fixtureSha256=sha(browser_run/'combat-fixture.json'),assetParitySha256=sha(browser_run/'asset-parity.json'),serverRun=str(server_run),serverMarkerSha256=sha(server_run/'RENDERER-BENCHMARK.json'),serverSourceReceipt=server['serverSourceCommit'])
    stage['bootstrapSourcesSha256']={f.name:sha(f)for f in sorted((ROOT/'tools/browser-gameplay/src/orsc/browser').glob('*.java'))}
    (browser_run/'stage.json').write_text(json.dumps(stage,indent=2)+'\n')
    url='http://127.0.0.1:'+str(args.http_port)+'/?mode=webgl&benchmark=1&fixture='+FIXTURE+'&cache=lru&underlay=initial&depth=gpu&loop=modern&cull=early&ownership=all&sprites=cached&overlay=coverage&ownedstate=on&uploadvalidation=on&physical=on&modelpickbounds=on&halomatch=on'
    receipt=dict(preparedOnly=True,processesStarted=False,publicAccessed=False,run=str(browser_run),serverRun=str(server_run),serverCommand=str(server_run/'server-command.json'),serverStartCommand=[sys.executable,str(ROOT/'scripts/renderer-benchmark.py'),'start',str(server_run)],httpCommand=[sys.executable,str(ROOT/'scripts/browser-gameplay/serve.py'),str(browser_run/'web'),'--port',str(args.http_port)],url=url,status=str(server_run/'status.json'),request=str(server_run/'request.json'),account='renderdemo',password='RendererProof13',clientSha256=stage['clientSha256'],clientSourceCommit=args.client_source_commit,serverSourceReceipt=stage['serverSourceReceipt'],serverMarkerSha256=stage['serverMarkerSha256'],assetPNGFiles=audit['files'],assetMissing=audit['missing'],assetDifferent=audit['different'])
    (browser_run/'combat-preparation.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(receipt,indent=2))
if __name__=='__main__':main()
