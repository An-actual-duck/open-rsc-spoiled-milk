#!/usr/bin/env python3
"""Independent full-JAR replay review; compile fixture classes only, never game/browser."""
import argparse,hashlib,json,subprocess,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
p=argparse.ArgumentParser();p.add_argument('--candidate-commit',required=True);p.add_argument('--jar',required=True,type=Path);p.add_argument('--out',required=True,type=Path);p.add_argument('--build-receipt',required=True,type=Path);a=p.parse_args();jar=a.jar.resolve();out=a.out.resolve()
def git(*args):return subprocess.check_output(['git',*args],cwd=ROOT)
if len(a.candidate_commit)!=40 or git('rev-parse',a.candidate_commit).decode().strip()!=a.candidate_commit:raise ValueError('Exact candidate commit required')
sha=lambda b:hashlib.sha256(b).hexdigest()
receipt_path=a.build_receipt.resolve();receipt=json.loads(receipt_path.read_bytes());source_manifest=receipt_path.parent/'source-manifest.json';classes_manifest=receipt_path.parent/'compiled-classes.json'
if not(receipt.get('clientSourceCommit')==a.candidate_commit and receipt.get('jarSha256')==sha(jar.read_bytes()) and receipt.get('sourceManifestSha256')==sha(source_manifest.read_bytes()) and receipt.get('compiledClassesManifestSha256')==sha(classes_manifest.read_bytes()) and receipt.get('buildSucceeded')is True and receipt.get('singleProductionCommit')is True and receipt.get('cleanCheckoutAtBuild')is True and receipt.get('classesTransplanted')is False):raise ValueError('Exact single-source full-build receipt required')
sources=json.loads(source_manifest.read_bytes());critical=['Client_Base/src/orsc/BrowserBaselineObjectReuse.java','Client_Base/src/orsc/PacketHandler.java','Client_Base/src/orsc/graphics/three/RSModel.java','Client_Base/src/orsc/mudclient.java']
for name in critical:
 if sources.get(name)!=sha(git('show',a.candidate_commit+':'+name)):raise ValueError('Reviewed production source mismatch '+name)
compiled=json.loads(classes_manifest.read_bytes())
with zipfile.ZipFile(jar)as z:
 for name,expected in compiled.items():
  if sha(z.read(name))!=expected:raise ValueError('Built class/JAR mismatch '+name)
out.mkdir(parents=True,exist_ok=False);classes=out/'classes';classes.mkdir();source=out/'source';source.mkdir()
fixture=source/'BaselineReuseFixture.java';fixture.write_bytes(git('show',a.candidate_commit+':tests/myworld/browser-baseline-reuse/BaselineReuseFixture.java'));own=ROOT/'tests/myworld/browser-baseline-review/DerivedReplayReview.java'
def run(command,label):
 r=subprocess.run(['nice','-n','10',*command],cwd=ROOT,capture_output=True,text=True);(out/(label+'.stdout')).write_text(r.stdout);(out/(label+'.stderr')).write_text(r.stderr);r.check_returncode();return r.stdout
run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),str(fixture),str(own)],'compile');traces={};commands={}
for label,benchmark,reuse in [('off',True,False),('on',True,True),('ordinary',False,True)]:
 props={'java.awt.headless':'true','spoiledmilk.browserBenchmark':str(benchmark).lower(),'spoiledmilk.browserWholeZone':'true','spoiledmilk.browserWholeRam':'true','spoiledmilk.browserStaticSceneReuse':'true','spoiledmilk.browserBaselineReuse':str(reuse).lower()};command=['java',*[f'-D{k}={v}'for k,v in props.items()],'-cp',str(classes)+':'+str(jar),'orsc.DerivedReplayReview',str(ROOT)];text=run(command,label);traces[label]=next(s for s in text.splitlines()if s.startswith('TRACE '));commands[label]=command
assert len(set(traces.values()))==1,'Adversarial derived-cache replay output differs'
sha=lambda b:hashlib.sha256(b).hexdigest()
r={'passed':True,'candidateCommit':a.candidate_commit,'buildReceiptSHA256':sha(receipt_path.read_bytes()),'compiledClassesVerified':len(compiled),'criticalSourceSHA256':{n:sources[n]for n in critical},'jar':str(jar),'jarSHA256':sha(jar.read_bytes()),'fixtureSHA256':sha(fixture.read_bytes()),'independentReviewFixtureSHA256':sha(own.read_bytes()),'traces':traces,'commands':commands,'productionClassOverlay':False,'scope':'Actual PacketHandler replay, geometry/collision/Scene/picking/software outputs; adversarial derived caches; no GPU/browser/gameplay or performance proof'};(out/'result.json').write_text(json.dumps(r,indent=2)+'\n');print(json.dumps(r))
