#!/usr/bin/env python3
"""Offline source-bound probe receipts; no browser, server or process mutation."""
import argparse,hashlib,json,struct,subprocess,zipfile
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('raw_base',type=Path);p.add_argument('output',type=Path);a=p.parse_args();here=Path(__file__).resolve().parent
sha=lambda x:hashlib.sha256(x).hexdigest()
def read(path):return json.loads(path.read_text())
results=[]
for name in ['text-abi-probe-3-clean','text-abi-probe-4-full-clean','text-abi-probe-5-framebatch-clean']:
 raw=a.raw_base/name;actual=read(raw/'actual.json');receipt=read(raw/'receipt.json');manifest=read(raw/'source-manifest.json')
 assert actual['completed']and receipt['accepted']and receipt['completed']and not actual['publicChanged']and not actual['diagnostic'];assert actual['manifest']==manifest
 assert actual['runtimeVersion']=='5.0'and actual['javaVersion']==8
 stage=Path(manifest['startCommand'][2]);assert stage.is_dir()
 for n,digest in manifest['files'].items():assert sha((stage/n).read_bytes())==digest,(name,n)
 for n in ['kernel.mjs','probe.mjs','index.html']:
  source=subprocess.check_output(['git','show',manifest['probeCommit']+':tools/browser-gameplay/text-raster-probe/'+n],cwd=here)
  assert sha(source)==manifest['files'][n],(name,n,'source checkpoint')
 assert not manifest['probeDirty']
 with zipfile.ZipFile(stage/'probe.jar')as jar:
  classes=[n for n in jar.namelist()if n.endswith('.class')];assert classes
  assert all(struct.unpack('>H',jar.read(n)[6:8])[0]==52 for n in classes)
  font=jar.read('font.bin');assert sha(font[4:])==manifest['fontSHA256'];assert int.from_bytes(font[:4],'big')==len(font)-4
 surface=manifest.get('surface',{});w=surface.get('width',67);h=surface.get('height',43);group=surface.get('textsPerClear',1);modes=['literal','validate-then-literal','native-raster'];mode_count=surface.get('modeCount',3);assert mode_count in [3,5]
 if mode_count==5:modes+=['validate-framebatch-then-literal','native-framebatch'];assert surface['frameBatch']and group==32
 k=actual['kernel'];assert not k['poisoned']and k['error']is None and k['rejections']==0
 assert k['calls']==k['validated']==k['rasterCalls']+k['validateOnlyCalls'];assert k['rasterCalls']==k['wroteCalls']+k['zeroWriteCalls'];assert k['adaptedBuffers']==0
 assert k['rawKinds']==dict(c='[object Int32Array]',commands='[object Int32Array]',font='[object Int8Array]',surface='[object Int32Array]',t='[object Float32Array]')
 for key,type_,size,length in [('c','Int32Array',4,w*h),('t','Float32Array',4,w*h),('font','Int8Array',1,len(font)-4),('commands','Int32Array',4,640),('surface','Int32Array',4,10)]:
  v=k['views'][key];assert v['constructor']==type_ and v['elementBytes']==size and v['length']==length and v['byteLength']==size*length and v['bufferKind']=='[object ArrayBuffer]';assert v['byteOffset']>=0 and v['byteOffset']+v['byteLength']<=v['bufferBytes']
 assert k['views']['zeroCopyBorrow']
 if mode_count==5:
  shapes=k['commandViewShapes'];assert set(shapes)=={'2560','81920'}
  for n in [640,20480]:assert shapes[str(n*4)]['constructor']=='Int32Array'and shapes[str(n*4)]['length']==n
 gate=next(e for e in actual['events']if e['phase']=='exact-pass');assert gate['sameCallVisibility'];assert gate['exactWords']==w*h*2*5*(mode_count-1);assert gate['nativeCalls']==5*(2*group+(2 if mode_count==5 else 0))
 final=next(e for e in actual['events']if e['phase']=='finished');assert final['nativeCalls']==k['calls']and final['exactWords']==gate['exactWords'];assert receipt['kernel']==k
 assert len(actual['rows'])==mode_count*10;cohorts=[];timedCalls=0
 for i,row in enumerate(actual['rows']):
  span=5*mode_count;order=i//span;counts=[0,1,8,32,128]if order==0 else[128,32,8,1,0];orderModes=modes if order==0 else modes[::-1];expectedCount=counts[(i%span)//mode_count];expectedMode=orderModes[i%mode_count]
  assert row['order']==order and row['glyphs']==expectedCount and row['mode']==expectedMode
  n=row['iterations'];assert isinstance(n,int)and n>0 and n%8==0;assert 500000000<=row['elapsedNanos']<10000000000
  expectedCalls=0 if row['mode']=='literal'else n*(1 if 'framebatch'in row['mode']else group);assert row['nativeCalls']==expectedCalls;timedCalls+=expectedCalls
  assert row['clearIncluded']and row['packetBuildIncluded']==(row['mode']!='literal');assert row.get('textsPerClear',1)==group
  if surface:assert row['width']==w and row['height']==h
  cohorts.append(dict(order=order,glyphs=row['glyphs'],mode=row['mode'],iterations=n,nativeCalls=expectedCalls,msPerGroup=row['elapsedNanos']/n/1e6,usPerText=row['elapsedNanos']/n/group/1000))
 assert k['calls']>timedCalls+gate['nativeCalls'] # Explicit warm calls are outside measured rows.
 results.append(dict(name=name,sourceCommit=manifest['probeCommit'],literalSource=manifest['literalProvenance']['sourceCommit'],sourceDirty=manifest['literalProvenance']['sourceDirty'],width=w,height=h,textsPerClear=group,exactWords=gate['exactWords'],actualNativeCalls=k['calls'],timedNativeCalls=timedCalls,firstViewShapes=k['views'],inputs={n:sha((raw/n).read_bytes())for n in ['actual.json','receipt.json','source-manifest.json']},cohorts=cohorts))
# Keep failed and debug captures separate; neither enters clean timing tables.
failed=read(a.raw_base/'text-abi-probe-2'/'actual.json');assert not failed['completed']and failed['kernel']['calls']==0
_debug=read(a.raw_base/'text-abi-probe-3-debug'/'actual.json');assert _debug['completed']and _debug['diagnostic']and not _debug['rows']and _debug['kernel']['calls']==10
literalHashes=[read(a.raw_base/n/'source-manifest.json')['literalProvenance']['methodHashes']for n in ['text-abi-probe-3-clean','text-abi-probe-4-full-clean','text-abi-probe-5-framebatch-clean']];assert literalHashes[0]==literalHashes[1]==literalHashes[2]
onceBodies=[]
for name in ['text-abi-probe-4-full-clean','text-abi-probe-5-framebatch-clean']:
 m=read(a.raw_base/name/'source-manifest.json');body=subprocess.check_output(['git','show',m['probeCommit']+':tools/browser-gameplay/text-raster-probe/ProbeMain.java.inc'],cwd=here,text=True);start=body.index('static void once(');end=body.index('static LiteralOverlayReference create()',start);part=body[start:end];onceBodies.append(dict(run=name,sourceCommit=m['probeCommit'],characters=len(part),sha256=sha(part.encode())))
baselineDrift=[]
for glyphs in [0,1,8,32,128]:
 old=[r['msPerGroup']for r in results[1]['cohorts']if r['glyphs']==glyphs and r['mode']=='literal'];new=[r['msPerGroup']for r in results[2]['cohorts']if r['glyphs']==glyphs and r['mode']=='literal'];baselineDrift.append(dict(glyphs=glyphs,stage4=old,stage5=new,ratio=[b/a for a,b in zip(old,new)]))
report=dict(validated=True,publicChanged=False,scope='Actual isolated CheerpJ8 API+packet+preflight+common-clear wall cost only; no game/GPU/FPS',runs=results,literalBaselineCrossArtifact=baselineDrift,unchangedLiteralMethodHashes=literalHashes[0],onceHelperSourceBodies=onceBodies,excludedInputHashes={n:{f:sha((a.raw_base/n/f).read_bytes())for f in ['actual.json','receipt.json','source-manifest.json']}for n in ['text-abi-probe-2','text-abi-probe-3-debug']},excludedFromTiming={'stage2':'Startup exited1 before raster calls; cause not localized','stage3debug':'Actual ABI exact gate passed, debug timing disabled'},limits=['Two short reversed-order synthetic cohorts; no confidence interval','CRITICAL literal baseline changes between stage4 and5; no headline speedup versus original literal or causal compiler attribution','Same-stage perTEXT versus framebatch supports amortization only, not game coverage or adoption','Common per-group clear is included; small and full tests differ group shape and cannot isolate copy cost','zeroCopyBorrow describes adapter sharing incoming storage, not all runtime internals','No production coverage receipt/transport premark/ACK or complete journal implemented'])
a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(report,indent=2)+'\n');print('PASS source hashes, class52, real typed-view/exact gates,110 cohort counts/order/arithmetic, separate debug/failure scope')
for run in results:
 print(run['name'],run['width'],run['height'],'TEXT/group',run['textsPerClear'])
 for count in [0,1,8,32,128]:
  print(count,{mode:[round(r['usPerText'],3)for r in run['cohorts']if r['glyphs']==count and r['mode']==mode]for mode in ['literal','validate-then-literal','native-raster','validate-framebatch-then-literal','native-framebatch']if any(r['mode']==mode for r in run['cohorts'])})
