#!/usr/bin/env python3
"""Production canonical object geometry versus real baked bytes, CPU only."""
import argparse,hashlib,json,shutil,subprocess
from pathlib import Path
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def require(b,s):
 if not b:raise ValueError(s)
def main():
 p=argparse.ArgumentParser(description=__doc__)
 for name in ('jar','baker-source','root','package','mini-cache','catalogue','out'):p.add_argument('--'+name,type=Path,required=True)
 a=p.parse_args();require(not a.out.exists(),'Preserve prior proof');a.out.mkdir(parents=True);classes=a.out/'classes';classes.mkdir()
 receipt=json.loads((a.jar.parent/'receipt.json').read_text());require(receipt.get('buildSucceeded')is True and receipt.get('classesTransplanted')is False and receipt.get('singleProductionCommit')is True,'Full source dependency receipt required');require(sha(a.jar)==receipt['jarSha256'],'JAR hash mismatch')
 for filename,key in [('source-manifest.json','sourceManifestSha256'),('compiled-classes.json','compiledClassesManifestSha256'),('asset-manifest.json','assetManifestSha256')]:require(sha(a.jar.parent/filename)==receipt[key],'Build provenance mismatch')
 fixture=Path(__file__).with_name('WholeZoneObjectParity.java');f=a.out/fixture.name;b=a.out/a.baker_source.name;shutil.copyfile(fixture,f);shutil.copyfile(a.baker_source,b)
 source={'path':str(a.baker_source.resolve()),'sha256':sha(b),'commit':subprocess.check_output(['git','-C',str(a.root),'rev-parse','HEAD'],text=True).strip(),'status':subprocess.check_output(['git','-C',str(a.root),'status','--short','--',str(a.baker_source.resolve())],text=True).strip(),'scope':'Baker source overlay supplies actual offline model/texture setup only. Compared geometry comes from unchanged production mudclient canonical builder in full dependency JAR. Not an integrated artifact.'}
 compile_command=['javac','-source','8','-target','8','-encoding','UTF-8','-cp',str(a.jar.resolve()),'-d',str(classes.resolve()),str(f.resolve()),str(b.resolve())]
 c=subprocess.run(compile_command,text=True,capture_output=True,timeout=30);(a.out/'javac.log').write_text(c.stdout+c.stderr);require(c.returncode==0,'Fixture compilation failed')
 command=['java','-Xmx512m','-Djava.awt.headless=true','-Dspoiledmilk.browserBenchmark=true','-Dspoiledmilk.browserRegionBuild=true','-cp',str(classes.resolve())+':'+str(a.jar.resolve()),'orsc.WholeZoneObjectParity',str(a.root.resolve()),str(a.package.resolve()),str(a.mini_cache.resolve()),str(a.catalogue.resolve()),str((a.out/'setup').resolve())]
 r=subprocess.run(command,text=True,capture_output=True,timeout=60);(a.out/'java.log').write_text(r.stdout+r.stderr);require(r.returncode==0,'Production object geometry proof failed; inspect java.log');result=json.loads(r.stdout.strip().splitlines()[-1]);require(result.get('passed')is True and result.get('comparedBytes',0)>0,'Nonvacuous geometry equality required')
 result.update(catalogueSha256=sha(a.catalogue),fixtureSha256=sha(f),jarSha256=sha(a.jar),dependencySourceCommit=receipt['clientSourceCommit'],bakerSourceOverlay=source,classes={p.relative_to(classes).as_posix():sha(p)for p in sorted(classes.rglob('*.class'))},commands=[compile_command,command]);(a.out/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({k:v for k,v in result.items()if k not in ('classes','commands','cases')},indent=2))
if __name__=='__main__':main()
