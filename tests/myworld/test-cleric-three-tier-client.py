#!/usr/bin/env python3
"""Schema3 actual-client transport, sparse book, composite HUD and detailed guide gates."""
from pathlib import Path
import argparse, os, subprocess, tempfile
ROOT = Path(__file__).resolve().parents[2]

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--scope', choices=['all', 'presentation', 'hud', 'guide'], default='all')
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
        subprocess.run([str(java_home/'bin/javac'), '-J-Xmx128m', '-cp', str(jar), '-d', str(work), str(ROOT/'tests/myworld/ClericThreeTierClientFixture.java')],check=True)
        subprocess.run([str(java_home/'bin/java'), '-Xmx256m', '-XX:+UseSerialGC', '-Djava.awt.headless=true', '-cp', os.pathsep.join([str(work),str(jar)]), 'orsc.ClericThreeTierClientFixture',str(wire),args.scope],check=True,cwd=ROOT)
if __name__ == '__main__': main()
