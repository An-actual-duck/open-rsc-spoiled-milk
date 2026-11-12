#!/usr/bin/env python3
"""Exact reviewed source normalizations; never relax a light reader or source ownership guard."""
import argparse,hashlib,json,re,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
BASE='45b0e72138c2980d4fcc900cd3e1ecff896fb170'
p=argparse.ArgumentParser();p.add_argument('--source-root',required=True,type=Path);p.add_argument('--commit',required=True);a=p.parse_args()
def git(*args):return subprocess.check_output(['git','-C',str(a.source_root),*args],text=True)
def sha(s):return hashlib.sha256(s.encode()).hexdigest()
def once(s,before,after):
 if s.count(before)!=1:raise AssertionError('Unexpected normalization boundary/count')
 return s.replace(before,after,1)
def strip(s,start,end):
 if s.count(start)!=1:raise AssertionError('Unknown/duplicate helper boundary')
 begin=s.index(start);finish=s.index(end,begin);added=s[begin:finish]
 if re.search(r'\.getVertexLight\s*\(|\bvertDiffuseLight\b',added):raise AssertionError('New guarded light read')
 return s[:begin]+s[finish:],added
MC='Client_Base/src/orsc/mudclient.java';MODEL='Client_Base/src/orsc/graphics/three/RSModel.java'
def normalize_mc(s):
 s,block=strip(s,'    private volatile BrowserPresentationCloneCache browserPresentationCloneCache;','    boolean isBrowserBaselineStaticObject(int objectId) {')
 s=once(s,'RSModel model = cloneBrowserPresentationGameObject(record.id, BrowserPresentationCloneCache.CANONICAL);','RSModel model = this.getModelCacheItem(def.modelID).clone();')
 return s,block
OLD_SIGNATURE='    public static RSModel instantiatePreparedState(int descriptor,WholeRamData.Pool pool,long[] copiedBytes) {'
NEW_SIGNATURE=OLD_SIGNATURE+'return instantiatePreparedState(descriptor,pool,copiedBytes,null);}\n    private static RSModel instantiatePreparedState(int descriptor,WholeRamData.Pool pool,long[] copiedBytes,WholeRamData.ModelLayout layout) {'
OLD_ARRAYS='        WholeRamData.ModelArrays a=new WholeRamData.ModelArrays(pool,pool.ints(descriptor),54,38);'
NEW_ARRAYS='        WholeRamData.ModelArrays a=layout==null?new WholeRamData.ModelArrays(pool,pool.ints(descriptor),54,38):new WholeRamData.ModelArrays(pool,layout);'
OLD_CTOR='        RSModel m=new RSModel(0,0);int[] v=a.scalars;'
NEW_CTOR='        RSModel m=layout==null?new RSModel(0,0):new RSModel(layout);int[] v=a.scalars;'
def normalize_model(s):
 s,block=strip(s,'    /** Only a schema-checked private cached recipe uses this constructor. All92','    /** Immutable exact raw clone inputs. Derived light/camera buffers are rebuilt')
 s=once(s,NEW_SIGNATURE,OLD_SIGNATURE);s=once(s,NEW_ARRAYS,OLD_ARRAYS);s=once(s,NEW_CTOR,OLD_CTOR)
 return s,block
originals={name:git('show',BASE+':'+name)for name in [MC,MODEL]}
candidates={name:git('show',a.commit+':'+name)for name in [MC,MODEL]}
results={}
for name,normalizer in [(MC,normalize_mc),(MODEL,normalize_model)]:
 normalized,added=normalizer(candidates[name]);assert normalized==originals[name],'Original enclosing bytes/control flow changed: '+name
 old_reads=[' '.join(s.split())for s in originals[name].splitlines()if re.search(r'\.getVertexLight\s*\(|\bvertDiffuseLight\b',s)]
 new_reads=[' '.join(s.split())for s in candidates[name].splitlines()if re.search(r'\.getVertexLight\s*\(|\bvertDiffuseLight\b',s)]
 assert old_reads==new_reads,'Reader roster changed'
 results[name]={'baselineSHA256':sha(originals[name]),'candidateSHA256':sha(candidates[name]),'reviewedAdditionSHA256':sha(added),'guardedLinesUnchanged':len(old_reads),'allOriginalBytesAfterExactNormalizations':True}
# Mutations to original code/reader sites or the ordinary decoder selector must fail.
controls=0
for name,normalizer,old,new in [(MC,normalize_mc,'return !isAnimatedResidentObjectChunkModel','return isAnimatedResidentObjectChunkModel'),(MC,normalize_mc,'model.key = -1 - index;\n\t\tmodel.addRotation','model.key = index;\n\t\tmodel.addRotation'),(MODEL,normalize_model,'m.key=v[1];','m.key=v[2];'),(MODEL,normalize_model,'private final int m_Vb = 12345678;','private final int m_Vb = 0;'),(MODEL,normalize_model,NEW_ARRAYS,NEW_ARRAYS.replace('layout==null','layout!=null')),(MODEL,normalize_model,NEW_CTOR,NEW_CTOR.replace('layout==null','layout!=null'))]:
 altered=once(candidates[name],old,new)
 try:
  normalized,_=normalizer(altered)
  if normalized==originals[name]:raise RuntimeError('Accepted negative mutation')
 except AssertionError:pass
 controls+=1
for name,normalizer,marker in [(MC,normalize_mc,'    private volatile BrowserPresentationCloneCache'),(MODEL,normalize_model,'    /** Only a schema-checked private cached recipe')]:
 altered=candidates[name].replace(marker,'    // forbidden .getVertexLight(\n'+marker,1)
 try:normalizer(altered)
 except AssertionError:pass
 else:
  # Insertion immediately outside the stripped range must fail enclosing equality.
  assert normalizer(altered)[0]!=originals[name]
 controls+=1
print(json.dumps({'passed':True,'baselineCommit':BASE,'candidateCommit':a.commit,'files':results,'negativeMutationControls':controls,'scope':'Two exact helper-block additions, one canonical factory substitution, and optional prepared-layout dispatch reviewed. All original remaining bytes, scalar/root assignments and light reader lines unchanged; ordinary decoder still selects original constructor. Added code correctness/alias/codec semantics are covered by separately bound full-JAR tests. No GPU/performance claim.'}))
