#!/usr/bin/env python3
"""Independent closed-source boundary tripwire; no application/browser/server execution."""
import argparse,collections,hashlib,json,os,subprocess
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
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--source-root',type=Path,default=ROOT);p.add_argument('--require-segmented-hooks',action='store_true');p.add_argument('--name',default='source-1');a=p.parse_args();source=a.source_root.resolve()
 if not a.name or any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_'for c in a.name):p.error('Simple run name required')
 out=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-segmented-text-boundaries','--mkdir'],text=True).strip())/a.name
 if out.exists():p.error('Refuse existing evidence run')
 out.mkdir();jdk=Path(os.environ.get('BOUNDARY_JDK_HOME','/usr/lib/jvm/java-21-openjdk-amd64'))/'bin'
 subprocess.run([str(jdk/'javac'),'-d',str(out),str(CAT/'SourceAccessInventory.java')],check=True)
 r=subprocess.run([str(jdk/'java'),'-cp',str(out),'SourceAccessInventory',str(source)],text=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,check=True)
 rows=[json.loads(x)for x in r.stdout.splitlines()];(out/'actual-inventory.json').write_text(json.dumps(rows,indent=2)+'\n');manifest=json.loads((CAT/'access-roster.json').read_text());access=[r for r in rows if r['kind']!='method-body'];expected=manifest['accesses'].copy()
 for x in manifest['reviewedCandidateAccessChanges']['remove']:expected.remove(x)
 expected+=manifest['reviewedCandidateAccessChanges']['add'];compare_roster(access,expected)
 # Mutation checks are against the checker, not a second copy of runtime logic.
 mutations=[access+[{'file':'Client_Base/src/NewRawConsumer.java','method':'read()','kind':'pixelData','expression':'surface.pixelData[0]'}],access+[access[0]],access[1:],access+[{'file':'PC_Client/src/orsc/Frame.java','method':'newAliasRead()','kind':'call:getData','expression':'buffer.getData()'}]]
 for mutated in mutations:
  try:compare_roster(mutated,expected)
  except AssertionError:pass
  else:raise AssertionError('Source-roster checker accepted new/changed/removed access')
 methods=bodies(rows)
 for path,digest in manifest['unchangedAliasImplementations'].items():assert hashlib.sha256((source/path).read_bytes()).hexdigest()==digest,('Audited alias/delegate implementation changed',path)
 for item in manifest['unchangedLegacyBodies']:
  body=methods[(item['file'],item['method'])];assert hashlib.sha256(body.encode()).hexdigest()==item['sha256'],('Old hot/alias body changed',item['method'])
 # Explicit candidate boundary assertions are added only after independently
 # reviewing the actual hook graph. Baseline inventory is never a runtime proof.
 if a.require_segmented_hooks:
  rules=json.loads((CAT/'candidate-boundaries.json').read_text());assert rules['reviewedCheckpoint'],'Candidate must have exact reviewed checkpoint'
  for rule in rules['methods']:
   body=methods[(rule['file'],rule['method'])]
   required=rule['requiredPrefix'];assert body.startswith(required),('Missing/reordered materialization before old consumer',rule['file'],rule['method'])
  assert rules['methods'],'No nonvacuous boundary assertions'
 receipt={'passed':True,'sourceRoot':str(source),'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=source,text=True).strip(),'sourceDirty':bool(subprocess.check_output(['git','status','--porcelain'],cwd=source,text=True).strip()),'baselineCommit':manifest['baselineCommit'],'astAccesses':len(access),'rosterMutationChecks':len(mutations),'unchangedLegacyBodies':len(manifest['unchangedLegacyBodies']),'candidateBoundariesChecked':a.require_segmented_hooks,'scope':'Independent closed maintained source/alias/delegate roster and explicit reviewed entry-order guards; not full alias analysis, dynamic rendering, native faults, GPU or FPS proof.','manifestSha256':hashlib.sha256((CAT/'access-roster.json').read_bytes()).hexdigest()}
 (out/'result.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(receipt,indent=2))
if __name__=='__main__':main()
