#!/usr/bin/env python3
"""Independent closed-source boundary tripwire; no application/browser/server execution."""
import argparse,collections,hashlib,io,json,os,subprocess,tarfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
CAT=ROOT/'tools/browser-gameplay/segmented-text-boundary-review'
def key(row):return tuple(row[k]for k in ('file','method','kind','expression'))
def compare_roster(actual,expected):
 a,b=collections.Counter(map(key,actual)),collections.Counter(map(key,expected))
 missing=list((b-a).elements());added=list((a-b).elements())
 if missing or added:raise AssertionError(json.dumps({'removedUnaudited':missing,'addedUnaudited':added},indent=2))
def bodies(rows):return {(r['file'],r['method']):r['expression']for r in rows if r['kind']=='method-body'}
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source-root',type=Path,default=ROOT);p.add_argument('--source-commit');p.add_argument('--require-segmented-hooks',action='store_true');p.add_argument('--name',default='source-1');a=p.parse_args();source=a.source_root.resolve()
 if not a.name or any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_'for c in a.name):p.error('Simple run name required')
 out=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-segmented-text-boundaries','--mkdir'],text=True).strip())/a.name
 if out.exists():p.error('Refuse existing evidence run')
 out.mkdir();source_repo=source;source_commit=subprocess.check_output(['git','rev-parse',a.source_commit or 'HEAD'],cwd=source_repo,text=True).strip()
 if a.source_commit:
  archive=subprocess.check_output(['git','archive',source_commit,'Client_Base/src','PC_Client/src'],cwd=source_repo);source=out/'frozen-source';source.mkdir()
  with tarfile.open(fileobj=io.BytesIO(archive))as files:files.extractall(source,filter='data')
 jdk=Path(os.environ.get('BOUNDARY_JDK_HOME','/usr/lib/jvm/java-21-openjdk-amd64'))/'bin'
 subprocess.run([str(jdk/'javac'),'-d',str(out),str(CAT/'SourceAccessInventory.java')],check=True)
 r=subprocess.run([str(jdk/'java'),'-cp',str(out),'SourceAccessInventory',str(source)],text=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True)
 rows=[json.loads(x)for x in r.stdout.splitlines()];(out/'actual-inventory.json').write_text(json.dumps(rows,indent=2)+'\n');manifest=json.loads((CAT/'access-roster.json').read_text());access=[r for r in rows if r['kind']!='method-body'];expected=manifest['accesses'].copy()
 if a.require_segmented_hooks:
  for x in manifest['reviewedCandidateAccessChanges']['remove']:expected.remove(x)
  expected+=manifest['reviewedCandidateAccessChanges']['add']
 compare_roster(access,expected)
 # Mutation checks are against the checker, not a second copy of runtime logic.
 mutations=[access+[{'file':'Client_Base/src/NewRawConsumer.java','method':'read()','kind':'pixelData','expression':'surface.pixelData[0]'}],access+[access[0]],access[1:],access+[{'file':'PC_Client/src/orsc/Frame.java','method':'newAliasRead()','kind':'call:getData','expression':'buffer.getData()'}]]
 for mutated in mutations:
  try:compare_roster(mutated,expected)
  except AssertionError:pass
  else:raise AssertionError('Source-roster checker accepted new/changed/removed access')
 # Parse actual synthetic NEW source consumers (unresolved types are intentional:
 # Javac parse performs no class loading/resolution). All must be newly visible.
 negative=out/'new-consumer-source';(negative/'Client_Base/src').mkdir(parents=True);(negative/'PC_Client/src').mkdir(parents=True)
 (negative/'Client_Base/src/NewRawConsumer.java').write_text('class NewRawConsumer { int[] pixelData; int read(){return pixelData[0];} void export(){sink.accept(frame.ownerPixels);} void shader(){Shader.shadeScanline();} void buffer(){buffer.getData();} }')
 synthetic=subprocess.check_output([str(jdk/'java'),'-cp',str(out),'SourceAccessInventory',str(negative)],text=True)
 new_rows=[json.loads(x)for x in synthetic.splitlines()if json.loads(x)['kind']!='method-body'];assert len(new_rows)==4
 for row in new_rows:
  try:compare_roster(access+[row],expected)
  except AssertionError:pass
  else:raise AssertionError('Actual new Java consumer escaped inventory')
 methods=bodies(rows)
 for path,digest in manifest['unchangedAliasImplementations'].items():assert hashlib.sha256((source/path).read_bytes()).hexdigest()==digest,('Audited alias/delegate implementation changed',path)
 for item in manifest['unchangedLegacyBodies']:
  body=methods[(item['file'],item['method'])];assert hashlib.sha256(body.encode()).hexdigest()==item['sha256'],('Old hot/alias body changed',item['method'])
  literal=next(r['rawSha256']for r in rows if r['kind']=='method-body'and r['file']==item['file']and r['method']==item['method']);assert literal==item['rawSha256'],('Literal source hot/alias body changed',item['method'])
 # Explicit candidate boundary assertions are added only after independently
 # reviewing the actual hook graph. Baseline inventory is never a runtime proof.
 if a.require_segmented_hooks:
  rules=json.loads((CAT/'candidate-boundaries.json').read_text());assert rules['reviewedCheckpoint'],'Candidate must have exact reviewed checkpoint'
  for rule in rules['methods']:
   body=methods[(rule['file'],rule['method'])]
   required=rule['requiredPrefix'];assert body.startswith(required),('Missing/reordered materialization before old consumer',rule['file'],rule['method'])
  for rule in rules['orderedSnippets']:assert rule['text']in methods[(rule['file'],rule['method'])],('Missing/reordered late consumer flush',rule['method'])
  for rule in rules['suffixes']:assert methods[(rule['file'],rule['method'])].endswith(rule['text']),('Abort catch is not at reviewed outermost scope',rule['method'])
  for rule in rules['coldBodies']:assert methods[(rule['file'],rule['method'])]==rule['text'],('Reviewed admission/recovery semantics changed',rule['method'])
  assert rules['methods'],'No nonvacuous boundary assertions'
  # A flush removed or moved behind the consumer must fail the independent guard.
  for rule in rules['methods']:
   body=methods[(rule['file'],rule['method'])];prefix=rule['requiredPrefix'];needle='flushPendingSegmentedText();'
   if needle in prefix:assert not body.replace(needle,'',1).startswith(prefix),'Guard accepted missing flush'
  assert not methods[(rules['suffixes'][0]['file'],rules['suffixes'][0]['method'])].replace('getRenderer3DFrame().release();','',1).endswith(rules['suffixes'][0]['text']),'Guard accepted missing abort pool cleanup'
 receipt={'passed':True,'sourceRoot':str(source),'sourceCommit':source_commit,'sourceFrozenExport':bool(a.source_commit),'sourceDirty':False if a.source_commit else bool(subprocess.check_output(['git','status','--porcelain'],cwd=source_repo,text=True).strip()),'baselineCommit':manifest['baselineCommit'],'astAccesses':len(access),'rosterMutationChecks':len(mutations),'actualNewJavaConsumerChecks':len(new_rows),'unchangedLegacyBodies':len(manifest['unchangedLegacyBodies']),'candidateBoundariesChecked':a.require_segmented_hooks,'candidateEntryGuards':len(rules['methods'])if a.require_segmented_hooks else 0,'candidateLateGuards':len(rules['orderedSnippets'])if a.require_segmented_hooks else 0,'candidateAbortTailGuards':len(rules['suffixes'])if a.require_segmented_hooks else 0,'candidateColdRecoveryGuards':len(rules['coldBodies'])if a.require_segmented_hooks else 0,'scope':'Independent closed maintained source/alias/delegate roster and explicit reviewed entry-order guards; not full alias analysis, dynamic rendering, native faults, GPU or FPS proof.','manifestSha256':hashlib.sha256((CAT/'access-roster.json').read_bytes()).hexdigest()}
 (out/'result.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(receipt,indent=2))
if __name__=='__main__':main()
