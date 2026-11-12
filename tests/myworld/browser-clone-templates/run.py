#!/usr/bin/env python3
"""Actual maintained full-JAR clone factory differential; fixture classes only."""
import argparse,hashlib,json,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
p=argparse.ArgumentParser();p.add_argument('--jar',required=True,type=Path);p.add_argument('--receipt',required=True,type=Path);p.add_argument('--out',required=True,type=Path);a=p.parse_args();jar=a.jar.resolve();receipt_path=a.receipt.resolve();receipt=json.loads(receipt_path.read_text());out=a.out.resolve();sha=lambda b:hashlib.sha256(b).hexdigest()
assert receipt['singleProductionCommit']and receipt['cleanCheckoutAtBuild']and receipt['buildSucceeded']and receipt['classesTransplanted']is False and receipt['jarSha256']==sha(jar.read_bytes())
sources=json.loads((receipt_path.parent/'source-manifest.json').read_text());assert sha((receipt_path.parent/'source-manifest.json').read_bytes())==receipt['sourceManifestSha256']
critical=['Client_Base/src/orsc/BrowserPresentationCloneCache.java','Client_Base/src/orsc/PacketHandler.java','Client_Base/src/orsc/mudclient.java','Client_Base/src/orsc/graphics/three/RSModel.java','Client_Base/src/orsc/graphics/three/WholeRamData.java']
for name in critical:assert sources[name]==sha((ROOT/name).read_bytes()),'Production source differs from actual full-JAR build: '+name
classes=json.loads((receipt_path.parent/'compiled-classes.json').read_text());assert sha((receipt_path.parent/'compiled-classes.json').read_bytes())==receipt['compiledClassesManifestSha256']
with zipfile.ZipFile(jar)as z:
 for name,h in classes.items():assert sha(z.read(name))==h
out.mkdir(parents=True,exist_ok=False);test_classes=out/'classes';test_classes.mkdir()
def run(command,label):
 r=subprocess.run(['nice','-n','10',*command],cwd=ROOT,text=True,capture_output=True,timeout=90);(out/(label+'.stdout')).write_text(r.stdout);(out/(label+'.stderr')).write_text(r.stderr);r.check_returncode();return r.stdout
fixtures=['tests/myworld/browser-baseline-reuse/BaselineReuseFixture.java','tests/myworld/CloneTemplateFixture.java','tests/myworld/whole-ram/WholeRamModelFieldFixture.java']
run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(test_classes),*[str(ROOT/f)for f in fixtures]],'compile');runs={};traces={}
for label,benchmark,ram,static,on in [('on',True,True,True,True),('off',True,True,True,False),('ordinary',False,True,True,True),('no-ram',True,False,True,True),('no-static',True,True,False,True)]:
 props={'java.awt.headless':'true','spoiledmilk.browserBenchmark':str(benchmark).lower(),'spoiledmilk.browserWholeZone':'true','spoiledmilk.browserWholeRam':str(ram).lower(),'spoiledmilk.browserStaticSceneReuse':str(static).lower(),'spoiledmilk.browserBaselineReuse':'false','spoiledmilk.browserCloneTemplates':str(on).lower()};command=['java',*[f'-D{k}={v}'for k,v in props.items()],'-cp',str(test_classes)+':'+str(jar),'orsc.CloneTemplateFixture',str(ROOT)];text=run(command,label);traces[label]=next(s for s in text.splitlines()if s.startswith('TRACE '));runs[label]=command
assert len(set(traces.values()))==1,'Factory outputs differ across startup policies'
fields=json.loads(run(['java','-Djava.awt.headless=true','-cp',str(test_classes)+':'+str(jar),'orsc.WholeRamModelFieldFixture'],'all-model-fields').strip().splitlines()[-1]);assert fields['passed']
result={'passed':True,'jarSHA256':receipt['jarSha256'],'sourceCommit':receipt['clientSourceCommit'],'productionReceiptSHA256':sha(receipt_path.read_bytes()),'compiledClassPayloadsVerified':len(classes),'productionClassOverlay':False,'traces':traces,'runs':runs,'fullModelFieldProof':fields,'fixtureSHA256':{f:sha((ROOT/f).read_bytes())for f in fixtures},'scope':'Actual DELTA packet decode/materialization, canonical factory, baseline, complete model alias graphs, mesh/collision/picking/software frames, concurrent copy isolation and signed scope fallbacks. No browser/GPU/FPS proof.'};(out/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({'passed':True,'policies':len(traces),'trace':next(iter(traces.values())),'fieldChecks':fields['checks'],'output':str(out)}))
