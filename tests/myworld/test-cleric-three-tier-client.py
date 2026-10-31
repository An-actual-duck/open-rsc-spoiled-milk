#!/usr/bin/env python3
"""Schema3 actual-client transport, sparse book, composite HUD and detailed guide gates."""
from pathlib import Path
import argparse, os, subprocess, tempfile
ROOT = Path(__file__).resolve().parents[2]

def validate_wiring():
    client = ROOT/'Client_Base/src'
    mud = (client/'orsc/mudclient.java').read_text()
    handler = (client/'orsc/PacketHandler.java').read_text()
    config = (client/'orsc/Config.java').read_text()
    npc = (client/'orsc/NpcCapacityContract.java').read_text()
    for required in ('updateClericSpellbook(length)', 'ClericSpellbookPacketDecoder.decode(payload)', 'mc.clearClericSpellbook()', 'mc.setClericHolyPowerKnown(intCount>=8)'):
        assert required in handler, required
    for required in ('codeAtIndex(this.panelMagic.getControlSelectedListIndex', 'this.clericSpellbook.atIndex(index)', 'this.clericSpellbook.atIndex(code)', 'putShort(definition.getStableCode())', 'definition.hasResources(id->this.getInventoryCount(id,false))', 'playerStatEquipment[7]', 'ClericPowerPresentation.rank(power', 'getClericHoverText()', 'status.getClericHoverLines()'):
        assert required in mud, required
    assert 'CLIENT_VERSION = ClericBlessingContract.CLIENT_VERSION' in config
    assert 'FIRST_EXPANDED_CLIENT_VERSION = 10053' in npc

def main():
    validate_wiring()
    parser = argparse.ArgumentParser()
    parser.add_argument('--scope', choices=['all', 'presentation', 'hud', 'guide'], default='all')
    parser.add_argument('--render', type=Path, help='Render actual offscreen native-font tooltip/HUD/guide proofs')
    parser.add_argument('--wire', type=Path, help='Fresh real PayloadCustomGenerator bytes; otherwise use recorded native fixture')
    args = parser.parse_args()
    java_home = Path(os.environ.get('JAVA_HOME', '/home/justin/jdk8u482-b08'))
    jar = ROOT / 'Client_Base/Open_RSC_Client.jar'
    if not jar.exists():
        raise SystemExit('Build Client_Base with Java8 first; the actual PacketHandler is required.')
    with tempfile.TemporaryDirectory(prefix='cleric-three-tier-client-') as directory:
        work=Path(directory)
        wire=args.wire
        if wire is None:
            wire=work/'schema3.bin'
            lines=(ROOT/'tests/myworld/fixtures/cleric-schema3-payload.hex').read_text().splitlines()
            wire.write_bytes(bytes.fromhex(''.join(line for line in lines if not line.startswith('#'))))
        subprocess.run([str(java_home/'bin/javac'), '-J-Xmx128m', '-cp', str(jar), '-d', str(work), str(ROOT/'tests/myworld/ClericThreeTierClientFixture.java'),str(ROOT/'tests/myworld/ClericGenericStatusHudFixture.java'),str(ROOT/'tests/myworld/ClericThreeTierClientRenderFixture.java')],check=True)
        subprocess.run([str(java_home/'bin/java'), '-Xmx256m', '-XX:+UseSerialGC', '-Djava.awt.headless=true', '-cp', os.pathsep.join([str(work),str(jar)]), 'orsc.ClericThreeTierClientFixture',str(wire),args.scope],check=True,cwd=ROOT)
        if args.render:
            subprocess.run([str(java_home/'bin/java'), '-Xmx256m', '-XX:+UseSerialGC', '-Djava.awt.headless=true', '-cp', os.pathsep.join([str(work),str(jar)]), 'orsc.ClericThreeTierClientRenderFixture', str(ROOT), str(wire), str(args.render.resolve())],check=True,cwd=ROOT)
        if args.scope in ('all','hud'):
            subprocess.run([str(java_home/'bin/java'), '-Xmx128m', '-XX:+UseSerialGC', '-cp', os.pathsep.join([str(work),str(jar)]), 'orsc.ClericGenericStatusHudFixture'],check=True,cwd=ROOT)
if __name__ == '__main__': main()
