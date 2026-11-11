#!/usr/bin/env python3
"""Listener-free real combat proof for the source renderer workload bootstrap."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import time

ROOT=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--server-artifacts',type=Path,default=ROOT/'server')
parser.add_argument('--name',default='offline-'+str(time.time_ns()))
args=parser.parse_args()
if not re.fullmatch(r'[A-Za-z0-9_-]+',args.name):raise ValueError('Simple unique run name required')
artifacts=args.server_artifacts.resolve()
category=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-combat-workload','--mkdir'],text=True).strip())
run=category/args.name;run.mkdir();runtime=run/'runtime';runtime.mkdir();classes=run/'classes';classes.mkdir()
for folder in ('conf','database'):shutil.copytree(artifacts/folder,runtime/folder)
for name in ('badwords.txt','goodwords.txt','globalrules.txt','alertwords.txt','plugins.jar'):shutil.copy2(artifacts/name,runtime/name)
selection=json.loads(subprocess.check_output(['python3',str(ROOT/'scripts/installed-map-selection.py'),str(ROOT)],text=True))
config=(artifacts/'myworld.conf').read_text()
for key,value in {'layered_native_terrain_package_path':selection['serverPackage'],'layered_native_terrain_manifest_sha256':selection['manifestSha256'],'want_discord_bot':'false'}.items():
    config,count=re.subn(r'(?m)^([ \t]*)'+key+r':.*$',lambda m:m[1]+key+': '+value,config)
    if not count:raise ValueError('Expected existing private config field '+key)
(runtime/'myworld.conf').write_text(config)
(runtime/'connections.conf').write_text('database:\n\tdb_type: MYSQL\n\tdb_host: 127.0.0.1\n\tdb_user: isolated_test\n\tdb_pass: unused\n')
sources=[ROOT/'tools/renderer-benchmark/RendererBenchmarkServer.java',ROOT/'server/test/com/openrsc/server/combat/RendererCombatWorkloadFixture.java']
cp=':'.join(map(str,[classes,artifacts/'core-gameplay-overlay.jar',artifacts/'core.jar',artifacts/'plugins.jar',artifacts/'lib/*']))
commands=[['javac','-proc:none','-source','8','-target','8','-cp',cp,'-sourcepath',str(ROOT/'server/test'),'-d',str(classes),*map(str,sources)],['java','-Xmx512m','-XX:+UseSerialGC','-Djava.awt.headless=true','-cp',cp,'com.openrsc.server.combat.RendererCombatWorkloadFixture']]
receipt={'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'serverArtifactSourceVerified':False,'serverArtifacts':str(artifacts),'mapManifest':selection['manifestSha256'],'sources':{str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sources},'commands':commands,'listenersStarted':False,'playerDatabaseUsed':False}
(run/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
for index,command in enumerate(commands):
    log=run/('compile.log' if index==0 else 'workload.log')
    with log.open('w') as output:result=subprocess.run(command,cwd=ROOT if index==0 else runtime,stdout=output,stderr=subprocess.STDOUT,timeout=90)
    if result.returncode:
        print(log.read_text()[-12000:]);raise SystemExit(result.returncode)
text=(run/'workload.log').read_text()
if 'PASS renderer combat workload ' not in text or re.search(r'(?:Exception|Error)(?::|\s)',text):
    print(text[-12000:]);raise AssertionError('Missing pass or unexpected runtime error; inspect '+str(run))
print('\n'.join(line for line in text.splitlines() if 'PASS renderer combat workload ' in line))
print('Evidence:',run)
