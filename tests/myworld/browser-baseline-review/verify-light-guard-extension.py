#!/usr/bin/env python3
"""Exact-file proof for reviewed baseline-reuse additions and unchanged light sites."""
import argparse,hashlib,json,re,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
BASE='743d4c2850d7244df4e0e67f50d62ea80424a654'
p=argparse.ArgumentParser();p.add_argument('--source-root',required=True,type=Path);p.add_argument('--commit',required=True);a=p.parse_args()
def git(*args):return subprocess.check_output(['git','-C',str(a.source_root),*args],text=True)
sha=lambda s:hashlib.sha256(s.encode()).hexdigest()
FILES={'Client_Base/src/orsc/mudclient.java':('47584f84068b9948abdfd29d0f6f29e6524be28f4c50297ba54e515c50096513','    boolean isBrowserBaselineStaticObject(int objectId) {','\n\tprivate boolean isAnimatedResidentObjectChunkModel('),'Client_Base/src/orsc/graphics/three/RSModel.java':('4087d583a9609ae96b44253366c5e9754d21f04136a18a49aafbc66a99bc99c9','    /** Immutable exact raw clone inputs. Derived light/camera buffers are rebuilt','\n\t@Override\n\tpublic final RSModel clone()')}
def prove(original,candidate,start,end):
 if start in original or candidate.count(start)!=1:raise AssertionError('Unknown/duplicate addition boundary')
 begin=candidate.index(start);finish=candidate.index(end,begin)+1;added=candidate[begin:finish]
 if candidate[:begin]+candidate[finish:]!=original:raise AssertionError('Existing enclosing light ownership/control-flow bytes changed')
 if re.search(r'\.getVertexLight\s*\(|\bvertDiffuseLight\b',added):raise AssertionError('Reviewed addition introduces a new guarded light read')
 return added
results={};controls=0
for name,(old_hash,start,end)in FILES.items():
 original=git('show',BASE+':'+name);candidate=git('show',a.commit+':'+name)
 if sha(original)!=old_hash:raise AssertionError('Frozen guard baseline source changed')
 added=prove(original,candidate,start,end)
 # Negative controls alter an original code byte and add a new guarded read inside
 # the permitted insertion. These must both fail; no production mutation occurs.
 mutated=candidate.replace('package orsc','package altered',1)
 for value in [mutated,candidate.replace(start,start+'\n// illicit.getVertexLight(0)',1),candidate.replace(start,start+'\n// vertDiffuseLight',1)]:
  try:prove(original,value,start,end)
  except AssertionError:controls+=1
  else:raise AssertionError('Exact-site negative mutation accepted')
 old_sites=[s for s in original.splitlines()if re.search(r'\.getVertexLight\s*\(|\bvertDiffuseLight\b',s)];new_sites=[s for s in candidate.splitlines()if re.search(r'\.getVertexLight\s*\(|\bvertDiffuseLight\b',s)]
 if old_sites!=new_sites:raise AssertionError('Original reader sites changed')
 results[name]={'baselineSHA256':old_hash,'candidateSHA256':sha(candidate),'reviewedAdditionSHA256':sha(added),'additionLines':len(added.splitlines()),'guardedLinesUnchanged':len(old_sites),'allOriginalFileBytesUnchanged':True}
name='PC_Client/src/orsc/BrowserWebGLBridge.java';original=git('show',BASE+':'+name);candidate=git('show',a.commit+':'+name)
old_hash='7159b6593533b5639b5c1cffb5b8a66d74a7516076efb8f20a839a523a00f156'
getters='    public static double[] getRegionHandoffTrace(){return BrowserRegionHandoffTrace.snapshot();}\n    public static String getRegionHandoffTracePhases(){return BrowserRegionHandoffTrace.phaseNames();}\n'
trace='BrowserRegionHandoffTrace.bridge(loadAttempt,(int)values[3],(long)values[6],0L,(long)(values[22]*1000000d));'
if sha(original)!=old_hash:raise AssertionError('Frozen bridge guard baseline changed')
def bridge_proof(value):
 if value.count(getters)!=1 or value.count(trace)!=1 or value.replace(getters,'').replace(trace,'')!=original:raise AssertionError('Existing bridge query/packing/ownership bytes changed')
 if re.search(r'\.getVertexLight\s*\(|\bvertDiffuseLight\b',getters+trace):raise AssertionError('New bridge light read')
bridge_proof(candidate)
for value in [candidate.replace('package orsc','package altered',1),candidate.replace(trace,trace+'illicit.getVertexLight(0);',1),candidate.replace(getters,getters+'// vertDiffuseLight\n',1)]:
 try:bridge_proof(value)
 except AssertionError:controls+=1
 else:raise AssertionError('Bridge negative mutation accepted')
results[name]={'baselineSHA256':old_hash,'candidateSHA256':sha(candidate),'reviewedAdditionSHA256':sha(getters+trace),'additionLines':3,'allOriginalFileBytesUnchanged':True,'priorPublishedDiagnosticCommit':'89ad84e45'}
print(json.dumps({'passed':True,'baselineCommit':BASE,'candidateCommit':git('rev-parse',a.commit).strip(),'files':results,'negativeMutationControls':controls,'scope':'Exact full guarded-file subtraction proof and unchanged original light-reader sites. New insertion text reviewed independently; no native/GPU/performance claim.'}))
