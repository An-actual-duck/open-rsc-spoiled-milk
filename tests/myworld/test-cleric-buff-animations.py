#!/usr/bin/env python3
"""Actual offline casts/updater/client loader/rendering plus exact approved asset receipts."""
from pathlib import Path
import argparse,hashlib,os,subprocess,tempfile,zipfile
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser();parser.add_argument('--old-client',type=Path,help='Retained pre-animation10056 client JAR for compatibility proof');parser.add_argument('--wire',type=Path,default=ROOT/'tests/myworld/fixtures/cleric-schema5-payload.hex');parser.add_argument('--output',type=Path,default=ROOT/'output/cleric-buff-animations');args=parser.parse_args();args.output.mkdir(parents=True,exist_ok=True)
java=Path(os.environ.get('JAVA_HOME','/home/justin/jdk8u482-b08'))/'bin'
def run(cmd,**kw):subprocess.run([str(x) for x in cmd],check=True,**kw)
art=ROOT/'dev/myworld/assets/animations/on-entity';jar=ROOT/'Client_Base/Open_RSC_Client.jar'
folders=['holy-sword','holy-bells','holy-shield','cleanse 2','bark','sword-clash','fist','sword','lightning-buff']
with zipfile.ZipFile(jar) as z:
 for folder in folders:
  path=art/folder/(folder+'.png');data=path.read_bytes();assert z.read('myworld-assets/animations/on-entity/'+folder+'/'+folder+'.png')==data
 assert not any('Holy VFX 03/' in p or '/thorns/' in p for p in z.namelist())
assert hashlib.sha256((art/'holy-sword/holy-sword.png').read_bytes()).hexdigest()=='9eefe1c88c66b0b54e1f058c80997ba66c054a289fee6117cb2b7c5635e1674e'
assert hashlib.sha256((art/'lightning-buff/lightning-buff.png').read_bytes()).hexdigest()=='2b97e83235213404045ffb6a02ec0f79aff2b3058fa2692155186985a531f953'
print('PASS exact approved nine embedded animation sheets; duplicate art retired',flush=True)
with tempfile.TemporaryDirectory(prefix='cleric-buff-vfx-') as folder:
 tmp=Path(folder);classes=tmp/'classes';classes.mkdir();empty=tmp/'empty';empty.mkdir();runtime=tmp/'runtime'
 run(['python3',ROOT/'tests/myworld/prepare-cleric-well-fixture.py',runtime],stdout=subprocess.DEVNULL)
 cp=os.pathsep.join(str(ROOT/p) for p in ['server/core.jar','server/plugins.jar','server/lib/*'])
 run([java/'javac','-J-Xmx128m','-proc:none','-cp',cp,'-sourcepath',ROOT/'server/test','-d',classes,ROOT/'server/test/com/openrsc/server/combat/ClericBuffAnimationFixture.java'])
 run([java/'java','-Xmx512m','-XX:+UseSerialGC','-Djava.awt.headless=true','-cp',str(classes)+':'+cp,'com.openrsc.server.combat.ClericBuffAnimationFixture'],cwd=runtime)
 run([java/'javac','-J-Xmx128m','-cp',jar,'-d',classes,ROOT/'tests/myworld/ClericBuffAnimationClientFixture.java'])
 run([java/'java','-Xmx256m','-XX:+UseSerialGC','-Djava.awt.headless=true','-Dspoiled_milk.opengl_sprite_overlay=true','-Dspoiled_milk.opengl_sprite_overlay_mode=phased','-Dspoiledmilk.openglWorldUiReplay=true','-cp',str(classes)+':'+str(jar),'orsc.ClericBuffAnimationClientFixture',ROOT,empty,args.output.resolve()],cwd=empty)
for id,name in [(96,'saradomin'),(98,'guthix'),(100,'zamorak')]:
 frames=[Image.open(p).convert('RGB') for p in sorted(args.output.glob(str(id)+'-*.png'))]
 frames[0].save(args.output/(name+'.gif'),save_all=True,append_images=frames[1:],duration=60,loop=0)

if args.old_client:
 with tempfile.TemporaryDirectory(prefix='cleric-vfx-old-client-') as folder:
  tmp=Path(folder);wire=args.wire
  if wire.suffix=='.hex':
   raw=bytes.fromhex(''.join(x for x in wire.read_text().splitlines() if not x.startswith('#')));wire=tmp/'schema5.bin';wire.write_bytes(raw)
  run([java/'javac','-J-Xmx128m','-cp',args.old_client,'-d',tmp,ROOT/'tests/myworld/ClericBuffOldClientFixture.java'])
  run([java/'java','-Xmx128m','-XX:+UseSerialGC','-Djava.awt.headless=true','-cp',str(tmp)+':'+str(args.old_client),'orsc.ClericBuffOldClientFixture',wire.resolve()])
