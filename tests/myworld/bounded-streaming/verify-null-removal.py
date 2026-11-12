#!/usr/bin/env python3
"""Full-artifact Scene null-removal differential and optional source-bound prepared-array proof."""
import argparse,hashlib,json,subprocess,zipfile
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__)
for name in ('jar','receipt','out'):p.add_argument('--'+name,type=Path,required=True)
for name in ('reference-jar','reference-receipt','ram-delivery','reference-cache'):p.add_argument('--'+name,type=Path)
a=p.parse_args();root=Path(__file__).resolve().parents[3]
def sha(path):
 h=hashlib.sha256()
 with path.open('rb')as f:
  for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
 return h.hexdigest()
def build(jar,path):
 r=json.loads(path.read_text());assert r['cleanCheckoutAtBuild']and r['singleProductionCommit']and r['buildSucceeded']and r['classesTransplanted']is False
 assert sha(jar)==r['jarSha256']
 with zipfile.ZipFile(jar)as z:
  for filename,key in [('source-manifest.json','sourceManifestSha256'),('compiled-classes.json','compiledClassesManifestSha256'),('asset-manifest.json','assetManifestSha256')]:
   f=path.parent/filename;assert sha(f)==r[key]
   if filename!='source-manifest.json':
    for name,digest in json.loads(f.read_text()).items():assert hashlib.sha256(z.read(name)).hexdigest()==digest
 return r
assert not a.out.exists(),'Preserve prior evidence';a.out.mkdir(parents=True);classes=a.out/'classes';classes.mkdir();receipt=build(a.jar,a.receipt);commands=[]
def run(name,cmd):
 commands.append(cmd);r=subprocess.run(cmd,text=True,capture_output=True,timeout=90);(a.out/(name+'.log')).write_text(r.stdout+r.stderr);assert r.returncode==0,name+' failed; inspect log';return r.stdout
fixture=Path(__file__).with_name('NullModelRemovalFixture.java')
run('compile',['javac','-source','8','-target','8','-cp',str(a.jar.resolve()),'-d',str(classes),str(fixture)])
flags=['-Djava.awt.headless=true','-Dspoiledmilk.browserBenchmark=true','-Dspoiledmilk.browserWholeZone=true']
result=json.loads(run('differential',['java','-Xmx256m',*flags,'-cp',str(classes)+':'+str(a.jar.resolve()),'orsc.graphics.three.NullModelRemovalFixture']).strip().splitlines()[-1]);assert result['passed']and result['operations']>=6000
references=[a.reference_jar,a.reference_receipt,a.ram_delivery,a.reference_cache];assert not any(references)or all(references),'Supply complete original reference binding'
prepared=None
if all(references):
 reference=build(a.reference_jar,a.reference_receipt);m=json.loads((a.ram_delivery/'manifest.json').read_text());assert m['complete']and m['clientJarSha256']==reference['jarSha256']and m['prepared']['hiddenRoofs']
 data=a.ram_delivery/'prepared.bin';assert data.stat().st_size==m['prepared']['bytes']and sha(data)==m['prepared']['sha256']
 refclasses=a.out/'reference-classes';refclasses.mkdir();source=Path(__file__).with_name('PreparedRoofNullFixture.java')
 run('reference-compile',['javac','-source','8','-target','8','-cp',str(a.reference_jar.resolve()),'-d',str(refclasses),str(source)])
 prepared=json.loads(run('prepared-arrays',['java','-Xmx1024m','-Djava.awt.headless=true','-cp',str(refclasses)+':'+str(a.reference_jar.resolve()),'orsc.graphics.three.PreparedRoofNullFixture',str(data),str(a.reference_cache)]).strip().splitlines()[-1]);assert prepared['passed']and prepared['activatedNullRemovalRequests']==720
 prepared.update(referenceReceipt=reference,manifestSha256=sha(a.ram_delivery/'manifest.json'),preparedSha256=sha(data))
report=dict(passed=True,productionReceipt=receipt,differential=result,preparedReference=prepared,commands=commands,fixtureSha256={v.name:sha(v)for v in Path(__file__).parent.glob('*Null*Fixture.java')},limitations=['Fixture-only classes compiled; no production overlays.','No browser/server/GPU/FPS claim.','Prepared activation is detached, not a live process array snapshot.'])
(a.out/'result.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(dict(passed=True,differential=result,prepared=prepared,out=str(a.out)),indent=2))
