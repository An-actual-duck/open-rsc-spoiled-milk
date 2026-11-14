#!/usr/bin/env python3
"""Offline actual gameplay/client catalogs and approved asset parity. Build client and server first."""
from pathlib import Path
import hashlib,json,os,subprocess,time
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'output/tests/god-crossbows'/str(time.time_ns())
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
client=ROOT/'Client_Base/Open_RSC_Client.jar'
server=ROOT/'server/core.jar'
for jar in (client,server):
    if not jar.is_file():raise SystemExit('Build required: '+str(jar))
run(['python3',ROOT/'tests/myworld/prepare-cleric-well-fixture.py',BASE/'runtime'],name='prepare')
cp=':'.join(str(ROOT/p) for p in ['server/core.jar','server/plugins.jar','server/lib/*'])
run(['javac','-source','8','-target','8','-proc:none','-cp',cp,'-sourcepath',ROOT/'server/test','-d',classes,ROOT/'server/test/com/openrsc/server/combat/GodCrossbowFixture.java'],name='compile-server-test')
run(['java','-Xmx768m','-XX:+UseSerialGC','-Djava.awt.headless=true','-cp',str(classes)+':'+cp,'com.openrsc.server.combat.GodCrossbowFixture'],cwd=BASE/'runtime',name='server')
run(['javac','-source','8','-target','8','-cp',client,'-d',classes,ROOT/'tests/myworld/java/GodCrossbowClientFixture.java'],name='compile-client-test')
run(['java','-Djava.awt.headless=true','-cp',str(classes)+':'+str(client),'GodCrossbowClientFixture'],name='client')
assets=[]
for god,prayer in [('saradomin','divine-bolts'),('zamorak','bolts-of-wrath'),('guthix','thorned-bolts')]:
    source=ROOT/f'dev/myworld/assets/authoring/items/god-crossbows/selected-mythic-v1/{god}-crossbow.png'
    runtime=ROOT/f'dev/myworld/assets/runtime/sprites/items/inventory-ground/weapons/god-crossbows/{god}-crossbow-icon.png'
    ui=ROOT/f'dev/myworld/assets/runtime/ui/prayer/{prayer}.png'
    assert source.read_bytes()==runtime.read_bytes()==ui.read_bytes(),god+' approved asset drift'
    im=Image.open(runtime).convert('RGBA');assert im.size==(48,32)
    assert set(im.getchannel('A').get_flattened_data())=={0,255}
    assets.append({'god':god,'runtimeSha256':hashlib.sha256(runtime.read_bytes()).hexdigest()})
receipt={'passed':True,'clientSha256':hashlib.sha256(client.read_bytes()).hexdigest(),'serverSha256':hashlib.sha256(server.read_bytes()).hexdigest(),'assets':assets,'liveServerTouched':False}
(BASE/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
print('PASS three approved native PNGs unchanged in runtime and prayer artwork')
print('Evidence:',BASE)
