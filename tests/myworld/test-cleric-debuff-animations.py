#!/usr/bin/env python3
"""Real nine-curse casts, observer packets, native draw/frame/asset and lifecycle proof."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,tempfile,zipfile
from PIL import Image
ROOT=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser();parser.add_argument('--old-client',type=Path);parser.add_argument('--wire',type=Path,default=ROOT/'tests/myworld/fixtures/cleric-schema5-payload.hex');parser.add_argument('--output',type=Path,default=ROOT/'output/cleric-debuff-animations');args=parser.parse_args();out=args.output.resolve();out.mkdir(parents=True,exist_ok=True)
java=Path(os.environ.get('JAVA_HOME','/home/justin/jdk8u482-b08'))/'bin';jar=ROOT/'Client_Base/Open_RSC_Client.jar'
def run(command,**kw):subprocess.run([str(x) for x in command],check=True,**kw)
manifest=json.loads((ROOT/'docs/myworld/in-progress-work-plans/cleric-debuff-animation-assets.json').read_text())['assets'];assert len(manifest)==36
with zipfile.ZipFile(jar) as archive:
 for rel,sha in manifest.items():
  data=(ROOT/rel).read_bytes();assert hashlib.sha256(data).hexdigest()==sha;assert archive.read(rel.replace('dev/myworld/assets/','myworld-assets/'))==data
for sub,frames,cell in [('general-buffs-debuffs/defense/mag-def-down',11,(24,24)),('general-buffs-debuffs/melee/melee-down',10,(24,24)),('general-buffs-debuffs/bubble-pop/bubble-pop',12,(24,24)),('general-buffs-debuffs/heart/heart-down',9,(24,24)),('general-buffs-debuffs/accuracy/accuracy-down',10,(24,24)),('general-buffs-debuffs/break-defense/break-defense',12,(24,24)),('general-buffs-debuffs/skull/skull-down',13,(24,24)),('poison-snake/poison-snake',28,(64,72)),('cut/cut',15,(64,64))]:
 assert Image.open(ROOT/'dev/myworld/assets/animations/on-entity'/ (sub+'.png')).size==(frames*cell[0],cell[1])
print('PASS 36 exact approved source/embedded PNG bytes and9 full-source cell geometries',flush=True)
with tempfile.TemporaryDirectory(prefix='cleric-curse-') as folder:
 tmp=Path(folder);classes=tmp/'classes';classes.mkdir();runtime=tmp/'runtime';empty=tmp/'empty';empty.mkdir()
 run(['python3',ROOT/'tests/myworld/prepare-cleric-well-fixture.py',runtime],stdout=subprocess.DEVNULL)
 cp=':'.join(str(ROOT/p) for p in ['server/core.jar','server/plugins.jar','server/lib/*'])
 run([java/'javac','-J-Xmx128m','-proc:none','-cp',cp,'-sourcepath',ROOT/'server/test','-d',classes,ROOT/'server/test/com/openrsc/server/combat/ClericDebuffAnimationFixture.java'])
 run([java/'java','-Xmx512m','-XX:+UseSerialGC','-Djava.awt.headless=true','-cp',str(classes)+':'+cp,'com.openrsc.server.combat.ClericDebuffAnimationFixture'],cwd=runtime)
 run([java/'javac','-J-Xmx128m','-cp',jar,'-d',classes,ROOT/'tests/myworld/ClericBuffAnimationClientFixture.java',ROOT/'tests/myworld/ClericDebuffAnimationClientFixture.java'])
 run([java/'java','-Xmx256m','-XX:+UseSerialGC','-Djava.awt.headless=true','-Dspoiled_milk.opengl_sprite_overlay=true','-Dspoiled_milk.opengl_sprite_overlay_mode=phased','-Dspoiledmilk.openglWorldUiReplay=true','-cp',str(classes)+':'+str(jar),'orsc.ClericDebuffAnimationClientFixture',ROOT,empty,out],cwd=ROOT)
for id,name in [(111,'saradomin'),(113,'guthix'),(115,'zamorak')]:
 frames=[Image.open(p).convert('RGB') for p in sorted(out.glob(str(id)+'-*.png'))]
 frames[0].save(out/(name+'.gif'),save_all=True,append_images=frames[1:],duration=40,loop=0)

if args.old_client:
 with tempfile.TemporaryDirectory(prefix='cleric-curse-old-client-') as folder:
  tmp=Path(folder);wire=args.wire
  if wire.suffix=='.hex':
   raw=bytes.fromhex(''.join(x for x in wire.read_text().splitlines() if not x.startswith('#')));wire=tmp/'schema5.bin';wire.write_bytes(raw)
  run([java/'javac','-J-Xmx128m','-cp',args.old_client,'-d',tmp,ROOT/'tests/myworld/ClericBuffOldClientFixture.java'])
  run([java/'java','-Xmx128m','-XX:+UseSerialGC','-Djava.awt.headless=true','-cp',str(tmp)+':'+str(args.old_client),'orsc.ClericBuffOldClientFixture',wire.resolve(),'100'])
