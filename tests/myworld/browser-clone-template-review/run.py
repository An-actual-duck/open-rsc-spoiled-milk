#!/usr/bin/env python3
"""Independent literal clone/full-state/real-factory/old-wire review against complete source JARs."""
import argparse,hashlib,json,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
p=argparse.ArgumentParser();p.add_argument('--jar',required=True,type=Path);p.add_argument('--receipt',required=True,type=Path);p.add_argument('--commit',required=True);p.add_argument('--reference-jar',required=True,type=Path);p.add_argument('--reference-receipt',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args()
jar=a.jar.resolve();out=a.out.resolve();out.mkdir(parents=True,exist_ok=False);classes=out/'classes';classes.mkdir()
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def bind(jar,receipt):
 r=json.loads(receipt.read_text());assert r['buildSucceeded']and r['cleanCheckoutAtBuild']and r['singleProductionCommit']and r['classesTransplanted']is False and r['jarSha256']==sha(jar)
 manifests={}
 with zipfile.ZipFile(jar)as archive:
  assert len(archive.namelist())==len(set(archive.namelist()))
  for filename,key in [('source-manifest.json','sourceManifestSha256'),('compiled-classes.json','compiledClassesManifestSha256'),('asset-manifest.json','assetManifestSha256')]:
   f=receipt.parent/filename;assert sha(f)==r[key];j=json.loads(f.read_text());manifests[filename]=j
   if filename!='source-manifest.json':
    for name,h in j.items():assert hashlib.sha256(archive.read(name)).hexdigest()==h
  assert {n for n in archive.namelist()if n.endswith('.class')and n.startswith(('orsc/','com/openrsc/'))}==set(manifests['compiled-classes.json'])
 return r,manifests
r,manifests=bind(jar,a.receipt.resolve());assert r['clientSourceCommit']==a.commit
for name,h in manifests['source-manifest.json'].items():assert hashlib.sha256(subprocess.check_output(['git','show',a.commit+':'+name],cwd=ROOT)).hexdigest()==h
reference,refmanifests=bind(a.reference_jar.resolve(),a.reference_receipt.resolve())
def run(args,label):
 proc=subprocess.run(args,cwd=ROOT,text=True,capture_output=True);(out/(label+'.stdout')).write_text(proc.stdout);(out/(label+'.stderr')).write_text(proc.stderr);proc.check_returncode();print(label,'\n'.join(proc.stdout.splitlines()[-2:]));return proc.stdout
sources=['tests/myworld/browser-baseline-reuse/BaselineReuseFixture.java','tests/myworld/browser-clone-template-review/CloneTemplateIndependentFixture.java','tests/myworld/whole-ram/WholeRamModelFieldFixture.java','tests/myworld/browser-clone-template-review/PreparedCodecWireFixture.java']
run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),*sources],'compile-fixtures')
traces={};wire={}
for label,benchmark,selected in [('off',True,False),('on',True,True),('ordinary',False,True)]:
 flags={'java.awt.headless':'true','spoiledmilk.browserBenchmark':str(benchmark).lower(),'spoiledmilk.browserWholeZone':'true','spoiledmilk.browserWholeRam':'true','spoiledmilk.browserStaticSceneReuse':'true','spoiledmilk.browserCloneTemplates':str(selected).lower()}
 result=run(['java',*[f'-D{k}={v}'for k,v in flags.items()],'-cp',str(classes)+':'+str(jar),'orsc.CloneTemplateIndependentFixture',str(ROOT)],label)
 traces[label]=next(s for s in result.splitlines()if s.startswith('TRACE '))
for label,path in [('reference',a.reference_jar.resolve()),('candidate',jar)]:
 wire[label]=run(['java','-Djava.awt.headless=true','-cp',str(classes)+':'+str(path),'orsc.PreparedCodecWireFixture'],'wire-'+label).strip()
assert len(set(traces.values()))==1 and len(set(wire.values()))==1
result={'passed':True,'candidateCommit':a.commit,'jar':str(jar),'jarSHA256':sha(jar),'receiptSHA256':sha(a.receipt),'compiledClassesVerified':len(manifests['compiled-classes.json']),'allMaintainedSourcesVerified':len(manifests['source-manifest.json']),'sourceSHA256':{n:manifests['source-manifest.json'][n]for n in ['Client_Base/src/orsc/BrowserPresentationCloneCache.java','Client_Base/src/orsc/PacketHandler.java','Client_Base/src/orsc/mudclient.java','Client_Base/src/orsc/graphics/three/RSModel.java','Client_Base/src/orsc/graphics/three/WholeRamData.java']},'referenceJarSHA256':sha(a.reference_jar),'referenceSource':reference['clientSourceCommit'],'productionClassOverlay':False,'testOnlyClassesCompiled':True,'fixtureSHA256':{n:sha(ROOT/n)for n in sources},'traces':traces,'wire':wire,'GPUExecuted':False,'browserStarted':False,'serverStarted':False}
(out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
