#!/usr/bin/env python3
"""Prepare, never start, an isolated fixed-actor real-game browser benchmark."""
import argparse, hashlib, json, re, shutil, subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
SOURCE='325bf42b60fe411705f9f14c0a3ef6debabe24a7'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def replace(config,key,value):
    output,count=re.subn(r'(?m)^([ \t]*)'+re.escape(key)+r':.*$',lambda match:match[1]+key+': '+str(value),config)
    return output if count else output+'\n'+key+': '+str(value)+'\n'
def roster():
    ids=[0,1,4,5,7,9,11,17]
    definitions={n['id']:n for n in json.loads((ROOT/'server/conf/server/defs/NpcDefs.json').read_text())['npcs']}
    return [dict(id=ids[n%8],name=definitions[ids[n%8]]['name'],x=129+n%8,y=658+n//8,direction=(n//8*2+n%2)%8) for n in range(32)]
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--name',required=True);parser.add_argument('--http-port',type=int,default=8160)
    parser.add_argument('--dependency-jar',type=Path,required=True);parser.add_argument('--client-source-commit',required=True)
    args=parser.parse_args()
    if not re.fullmatch('[0-9a-f]{40}',args.client_source_commit):parser.error('Exact client source hash required')
    subprocess.run(['python3',str(ROOT/'scripts/browser-gameplay/prepare.py'),'--name',args.name,'--http-port',str(args.http_port),'--dependency-jar',str(args.dependency_jar.resolve()),'--client-source-commit',args.client_source_commit],check=True,stdout=subprocess.DEVNULL)
    category=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-gameplay-spike','--mkdir'],text=True).strip())
    run=category/args.name
    subprocess.run(['python3',str(ROOT/'scripts/browser-gameplay/prepare-server.py'),str(run)],check=True)
    server=run/'server';config=(server/'myworld.conf').read_text()
    for key,value in dict(db_name='browser_repeatable_dev',server_port=43649,ws_server_port=43549,server_name='Repeatable Private Benchmark',server_name_welcome='Repeatable Private Benchmark').items():config=replace(config,key,value)
    (server/'myworld.conf').write_text(config)
    database=server/'inc/sqlite/browser_repeatable_dev.db'
    (server/'inc/sqlite/browser_spike_dev.db').rename(database)
    marker=json.loads((server/'BROWSER-PRIVATE.json').read_text());marker['repeatableFrameWork']=1
    (server/'BROWSER-PRIVATE.json').write_text(json.dumps(marker,indent=2)+'\n')
    fixture=dict(schema=1,account='browserdemo',spawn=[133,660,0],groupId=1,actors=roster(),camera=dict(zoom=75,rotation=128,sourcePitch=912,expectedSceneRotation=[112,512,0],drift=[0,0],driftIncrement=[0,0]),scope='Only the separate disposable fixture World; authored NPCs suppressed there, production client animations unchanged')
    (run/'repeatable-fixture.json').write_text(json.dumps(fixture,indent=2)+'\n')
    (run/'web/Cache/port.txt').write_text('43649\n')
    command=json.loads((run/'server-command.json').read_text());cp=command['command'][command['command'].index('-cp')+1]
    source=run/'repeatable-source';source.mkdir();shutil.copy2(ROOT/'tools/browser-gameplay/src/BrowserRepeatableServer.java',source/'BrowserRepeatableServer.java')
    subprocess.run(['javac','-source','8','-target','8','-cp',cp,'-d',str(run/'server-classes'),str(source/'BrowserRepeatableServer.java')],check=True)
    command['command'][-1]='BrowserRepeatableServer';(run/'server-command.json').write_text(json.dumps(command,indent=2)+'\n')
    stage=json.loads((run/'stage.json').read_text());stage.update(tcp='127.0.0.1:43649',websocket='127.0.0.1:43549',fixture='repeatable-v1',fixtureSha256=sha(run/'repeatable-fixture.json'),serverHarnessSha256=sha(source/'BrowserRepeatableServer.java'),preparedOnly=True)
    stage['frontendSha256']={p.name:sha(p) for p in (run/'web').iterdir() if p.is_file() and p.suffix in ('.js','.html','.css')}
    (run/'stage.json').write_text(json.dumps(stage,indent=2)+'\n')
    url='http://127.0.0.1:'+str(args.http_port)+'/?mode=webgl&benchmark=1&fixture=repeatable-v1&cache=lru&underlay=initial&depth=gpu&loop=modern&cull=early&ownership=all&sprites=cached&overlay=coverage&ownedstate=on&uploadvalidation=on&physical=on&modelpickbounds=on&halomatch=on'
    receipt=dict(preparedOnly=True,run=str(run),serverCommand=str(run/'server-command.json'),httpCommand=['python3',str(ROOT/'scripts/browser-gameplay/serve.py'),str(run/'web'),'--port',str(args.http_port)],url=url,status=str(run/'status.json'),ready=str(run/'REPEATABLE-FIXTURE-READY.json'),clientSha256=stage['clientSha256'],clientSourceCommit=args.client_source_commit,account='browserdemo',password='BrowserProof13',publicAccessed=False,processesStarted=False)
    (run/'repeatable-preparation.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(receipt,indent=2))
if __name__=='__main__':main()
