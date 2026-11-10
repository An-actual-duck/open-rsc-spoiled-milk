#!/usr/bin/env python3
"""Stage a new private client against an existing dedicated repeatable server; never start either."""
import argparse,hashlib,json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--name',required=True);parser.add_argument('--http-port',type=int,required=True)
    parser.add_argument('--server-run',type=Path,required=True);parser.add_argument('--dependency-jar',type=Path,required=True)
    parser.add_argument('--native-source',type=Path);parser.add_argument('--native-source-commit')
    provenance=parser.add_mutually_exclusive_group(required=True);provenance.add_argument('--client-source-commit');provenance.add_argument('--client-provenance',type=Path)
    args=parser.parse_args();server_run=args.server_run.resolve();jar=args.dependency_jar.resolve()
    marker=json.loads((server_run/'server/BROWSER-PRIVATE.json').read_text());fixture=json.loads((server_run/'repeatable-fixture.json').read_text())
    if marker.get('repeatableFrameWork')!=1 or fixture.get('account')!='browserdemo' or len(fixture.get('actors',[]))!=32:parser.error('Use only the dedicated repeatable fixture server')
    copied=None
    if args.client_provenance:
        copied=json.loads(args.client_provenance.read_text())
        if copied.get('jarSha256')!=sha(jar):parser.error('Composite receipt does not match selected artifact')
        if copied.get('singleProductionCommit') is not False:parser.error('Expected an explicit reviewed composite source receipt')
    command=['python3',str(ROOT/'scripts/browser-gameplay/prepare.py'),'--name',args.name,'--http-port',str(args.http_port),'--dependency-jar',str(jar)]
    if args.native_source or args.native_source_commit:
        if not args.native_source or not args.native_source_commit:parser.error('Use native source and exact commit together')
        command+=['--native-source',str(args.native_source.resolve()),'--native-source-commit',args.native_source_commit]
    if args.client_source_commit:command+=['--client-source-commit',args.client_source_commit]
    subprocess.run(command,check=True,stdout=subprocess.DEVNULL)
    category=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-gameplay-spike','--mkdir'],text=True).strip());run=category/args.name
    (run/'web/Cache/port.txt').write_text('43649\n')
    fixture['camera']=dict(zoom=75,rotation=128,sourcePitch=912,expectedSceneRotation=[112,512,0],closeInitialWelcome=True,drift=[0,0],driftIncrement=[0,0])
    (run/'repeatable-fixture.json').write_text(json.dumps(fixture,indent=2)+'\n')
    stage=json.loads((run/'stage.json').read_text());stage.update(tcp='127.0.0.1:43649',websocket='127.0.0.1:43549',fixture='repeatable-v1',serverRun=str(server_run),preparedOnly=True,fixtureSha256=sha(run/'repeatable-fixture.json'))
    if copied:
        (run/'client-source-provenance.json').write_text(json.dumps(copied,indent=2)+'\n');stage.update(clientSourceCommit=None,clientSourceComposite=copied,clientProvenanceSha256=sha(run/'client-source-provenance.json'))
    (run/'stage.json').write_text(json.dumps(stage,indent=2)+'\n')
    query='?mode=webgl&benchmark=1&fixture=repeatable-v1&cache=lru&underlay=initial&depth=gpu&loop=modern&cull=early&ownership=all&sprites=cached&overlay=coverage&ownedstate=on&uploadvalidation=on&physical=on&modelpickbounds=on&halomatch=on&geometrytransfer=off&regionprep=off&descriptorwrite=off&halolookup=off&nativecalls=async'
    receipt=dict(preparedOnly=True,processesStarted=False,run=str(run),serverRun=str(server_run),status=str(server_run/'status.json'),ready=str(server_run/'REPEATABLE-FIXTURE-READY.json'),httpCommand=['python3',str(ROOT/'scripts/browser-gameplay/serve.py'),str(run/'web'),'--port',str(args.http_port)],url='http://127.0.0.1:'+str(args.http_port)+'/'+query,clientSha256=stage['clientSha256'],clientSourceCommit=stage['clientSourceCommit'],frontendSourceCommit=stage['sourceCommit'],compositeSource=copied,nativeDispatch=stage.get('nativeDispatch'),camera=fixture['camera'])
    (run/'repeatable-preparation.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(receipt,indent=2))
if __name__=='__main__':main()
