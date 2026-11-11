#!/usr/bin/env python3
"""Run actual private Java reader/capture semantics in a detached headless fixture."""
import argparse,hashlib,json,shutil,subprocess,zipfile
from pathlib import Path
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def require(b,s):
 if not b:raise ValueError(s)
def main():
 p=argparse.ArgumentParser(description=__doc__)
 for n in ('jar','catalogue','package','cache','out'):p.add_argument('--'+n,type=Path,required=True)
 p.add_argument('--reader-source',type=Path,help='Explicit unit-only source overlay; never labelled a full integration JAR');a=p.parse_args();require(not a.out.exists(),'Preserve prior result');a.out.mkdir(parents=True);classes=a.out/'classes';classes.mkdir()
 build=a.jar.parent;receipt=json.loads((build/'receipt.json').read_text());require(receipt.get('buildSucceeded')is True and receipt.get('singleProductionCommit')is True and receipt.get('classesTransplanted')is False,'Exact full-source build receipt required');require(sha(a.jar)==receipt['jarSha256'],'JAR receipt mismatch')
 for filename,key in [('source-manifest.json','sourceManifestSha256'),('compiled-classes.json','compiledClassesManifestSha256'),('asset-manifest.json','assetManifestSha256')]:require(sha(build/filename)==receipt[key],'Build provenance mismatch '+filename)
 fixture=Path(__file__).with_name('WholeZoneDynamicFixture.java');copied=a.out/fixture.name;shutil.copyfile(fixture,copied)
 override=None
 if a.reader_source:
  reader_copy=a.out/a.reader_source.name;shutil.copyfile(a.reader_source,reader_copy);override={'source':str(a.reader_source.resolve()),'sha256':sha(a.reader_source),'sourceCommit':subprocess.check_output(['git','-C',str(a.reader_source.resolve().parents[3]),'rev-parse','HEAD'],text=True).strip(),'sourceStatus':subprocess.check_output(['git','-C',str(a.reader_source.resolve().parents[3]),'status','--short','--',str(a.reader_source.resolve())],text=True).strip(),'scope':'Explicit reader-only unit source overlay against full dependency JAR; final integrated JAR GPU proof remains separate.'}
 compile_command=['javac','-source','8','-target','8','-encoding','UTF-8','-cp',str(a.jar.resolve()),'-d',str(classes.resolve()),str(copied.resolve())]+([str(reader_copy.resolve())]if override else[])
 compile_result=subprocess.run(compile_command,text=True,capture_output=True,timeout=30);(a.out/'javac.log').write_text(compile_result.stdout+compile_result.stderr);require(compile_result.returncode==0,'Fixture compilation failed; inspect javac.log')
 command=['java','-Xmx256m','-Djava.awt.headless=true','-Dspoiledmilk.browserBenchmark=true','-Dspoiledmilk.browserWholeZone=true','-cp',str(classes.resolve())+':'+str(a.jar.resolve()),'orsc.WholeZoneDynamicFixture',str(a.catalogue.resolve()),str(a.package.resolve()),str(a.cache.resolve())]
 run=subprocess.run(command,text=True,capture_output=True,timeout=30);(a.out/'java.log').write_text(run.stdout+run.stderr);require(run.returncode==0,'Actual Java proof failed; inspect java.log');result=json.loads(run.stdout.strip().splitlines()[-1]);require(result.get('passed')is True and result.get('checks',0)>=43,'Nonvacuous actual Java checks required')
 targets=['orsc/mudclient.class','orsc/mudclient$ResidentObjectChunkInput.class','orsc/BrowserWholeOverworldZone.class','orsc/PacketHandler.class','orsc/SceneBaselineState.class','orsc/graphics/three/WholeOverworldZoneRegistry.class','orsc/graphics/three/World.class']
 with zipfile.ZipFile(a.jar)as z:hashes={name:hashlib.sha256(z.read(name)).hexdigest()for name in targets}
 compiled={p.relative_to(classes).as_posix():sha(p)for p in sorted(classes.rglob('*.class'))}
 result.update(executedFixtureOrOverlayClasses=compiled,readerSourceOverlay=override,jar=str(a.jar.resolve()),jarSha256=sha(a.jar),clientSourceCommit=receipt['clientSourceCommit'],sourceManifestSha256=receipt['sourceManifestSha256'],catalogueSha256=sha(a.catalogue),fixtureSha256=sha(fixture),runtimeClasses=hashes,buildReceiptSha256=sha(build/'receipt.json'),commands=[compile_command,command],limitations=['Reader index is injected from owned partial metadata to isolate runtime semantics; no constructor/full catalogue admission bypass exists in gameplay.','Bare actual mudclient allocated without GUI; actual authority classes receive detached owned state. No live packets, native calls, browser/GPU allocation/pixel or performance proof.'])
 (a.out/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({k:v for k,v in result.items()if k not in ('runtimeClasses','commands')},indent=2))
if __name__=='__main__':main()
