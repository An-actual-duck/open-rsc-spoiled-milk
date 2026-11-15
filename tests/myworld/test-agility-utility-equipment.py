#!/usr/bin/env python3
"""Offline integration of agility equipment, movement and rewards. Build the server first."""
from pathlib import Path
import hashlib,json,os,subprocess,time
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'output/tests/agility-utility-equipment'/str(time.time_ns())
BASE.mkdir(parents=True)
classes=BASE/'classes';classes.mkdir()
def run(argv,cwd=ROOT,name='step'):
    result=subprocess.run([str(x) for x in argv],cwd=cwd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    (BASE/(name+'.log')).write_text(result.stdout)
    if result.returncode:
        print('\n'.join(result.stdout.splitlines()[-30:]));raise SystemExit(result.returncode)
    for line in result.stdout.splitlines():
        if 'PASS ' in line:print(line)
    return result
server=ROOT/'server/core.jar'
for jar in (server,):
    if not jar.is_file():raise SystemExit('Build required: '+str(jar))
run(['python3',ROOT/'tests/myworld/prepare-agility-utility-fixture.py',BASE/'runtime'],name='prepare')
cp=':'.join(str(ROOT/p) for p in ['server/core.jar','server/plugins.jar','server/lib/*'])
run(['javac','-source','8','-target','8','-proc:none','-cp',cp,'-sourcepath',ROOT/'server/test','-d',classes,ROOT/'server/test/com/openrsc/server/combat/AgilityUtilityFixture.java'],name='compile-server-test')
run(['java','-Xmx768m','-XX:+UseSerialGC','-Djava.awt.headless=true','-cp',str(classes)+':'+cp,'com.openrsc.server.combat.AgilityUtilityFixture'],cwd=BASE/'runtime',name='server')
client=ROOT/'Client_Base/Open_RSC_Client.jar'
run(['javac','-source','8','-target','8','-cp',client,'-d',classes,ROOT/'tests/myworld/java/AgilityUtilityClientFixture.java'],name='compile-client')
for custom in ['true','false']:
    run(['java','-Djava.awt.headless=true','-cp',str(classes)+':'+str(client),'AgilityUtilityClientFixture',custom],name='client-'+custom)
(BASE/'receipt.json').write_text(json.dumps({'passed':True,'serverSha256':hashlib.sha256(server.read_bytes()).hexdigest(),'liveServerTouched':False},indent=2)+'\n')
print("Evidence:",BASE)
