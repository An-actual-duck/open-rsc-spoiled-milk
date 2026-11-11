#!/usr/bin/env python3
"""Actual production OSAR decoder equivalence. Bounded headless CPU test only."""
import argparse,hashlib,json,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
SOURCES=['Unpacker.java','Workspace.java','Subspace.java','Entry.java','Frame.java']
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--original',type=Path,required=True);p.add_argument('--derived',type=Path,required=True);p.add_argument('--jar',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
 if a.out.exists():p.error('Refuse replacing prior evidence')
 if a.original.resolve()==a.derived.resolve():p.error('Independent original and derivative required')
 a.out.mkdir(parents=True);src=a.out/'source';src.mkdir();classes=a.out/'classes';classes.mkdir();paths=[];hashes={}
 for n in SOURCES:
  f=ROOT/'Client_Base/src/orsc/graphics/two/SpriteArchive'/n;dest=src/n;dest.write_bytes(f.read_bytes());paths.append(dest);hashes[str(f.relative_to(ROOT))]=sha(f)
 fixture=Path(__file__).with_name('WholeZoneTextureEquivalence.java');dest=src/fixture.name;dest.write_bytes(fixture.read_bytes());paths.append(dest);hashes[str(fixture.relative_to(ROOT))]=sha(fixture)
 with(a.out/'compile.log').open('w')as log:subprocess.run(['javac','-source','8','-target','8','-cp',str(a.jar.resolve()),'-d',str(classes),*[str(f)for f in paths]],check=True,stdout=log,stderr=subprocess.STDOUT,timeout=30)
 cp=str(classes)+':'+str(a.jar.resolve());r=subprocess.run(['java','-Xmx256m','-Djava.awt.headless=true','-cp',cp,'WholeZoneTextureEquivalence',str(a.original.resolve()),str(a.derived.resolve())],text=True,capture_output=True,timeout=30);(a.out/'execution.log').write_text(r.stdout+r.stderr);r.check_returncode();result=json.loads(r.stdout.strip().splitlines()[-1]);result.update(sourceCommit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),sourceFileSHA256=hashes,originalSHA256=sha(a.original),derivedSHA256=sha(a.derived),dependencyJarSHA256=sha(a.jar),scope='Actual production Unpacker/Entry/Frame pixel+metadata equivalence; no material quantization, scene/GPU or whole-catalog residency proof.');(a.out/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
if __name__=='__main__':main()
