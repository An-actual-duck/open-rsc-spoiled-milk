#!/usr/bin/env python3
"""Source-bound JS isolation proof against actual Java literal exports; never starts GPU."""
import argparse,hashlib,json,re,subprocess
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
MODULE='tools/browser-gameplay/web/browser-overlay-journal.js'
def sha(data):return hashlib.sha256(data).hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--candidate-root',required=True,type=Path);p.add_argument('--candidate-commit',required=True);p.add_argument('--java-receipt',required=True,type=Path);args=p.parse_args()
 if not re.fullmatch('[a-f0-9]{40}',args.candidate_commit):p.error('Exact candidate hash required')
 receiptPath=args.java_receipt.resolve();java=json.loads(receiptPath.read_text());corpus=receiptPath.parent/'literal-journals.json';assert java['passed']and sha(corpus.read_bytes())==java['literalJournalsSha256'],'Java export provenance mismatch'
 root=args.candidate_root.resolve();javaHashes={}
 for path,want in java['sourceSha256']['candidate'].items():
  data=subprocess.check_output(['git','show',args.candidate_commit+':'+path],cwd=root);javaHashes[path]=sha(data);assert javaHashes[path]==want,'Java source changed; rerun Java proof: '+path
 base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-overlay-journal-native-independent','--mkdir'],text=True).strip());run=base/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');run.mkdir()
 data=subprocess.check_output(['git','show',args.candidate_commit+':'+MODULE],cwd=root);module=run/'browser-overlay-journal.mjs';module.write_bytes(data)
 fixture=ROOT/'tests/myworld/test-browser-overlay-journal-native-independent.mjs';command=['node',str(fixture),str(module),str(corpus)];r=subprocess.run(command,cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.PIPE);(run/'stdout.log').write_bytes(r.stdout);(run/'stderr.log').write_bytes(r.stderr);r.check_returncode()
 receipt=dict(passed=True,cpuOnly=True,gpuExecuted=False,browserStarted=False,serverStarted=False,candidateCommit=args.candidate_commit,moduleSha256=sha(data),javaReceipt=str(receiptPath),javaReceiptSha256=sha(receiptPath.read_bytes()),javaSourceAtCandidateSha256=javaHashes,literalCorpusSha256=sha(corpus.read_bytes()),fixtureSha256=sha(fixture.read_bytes()),runnerSha256=sha(Path(__file__).read_bytes()),command=command,stdoutSha256=sha(r.stdout),stderrSha256=sha(r.stderr),scope='Actual Java literal-vs-JS C/raw-T, bounded hostile-input rejection, observer failure/allocation isolation, original exactly-once, no borrowed diagnostic retention; no GPU or FPS.')
 (run/'result.json').write_text(json.dumps(receipt,indent=2)+'\n');print(r.stdout.decode(),end='');print('Independent native journal receipt:',run/'result.json')
if __name__=='__main__':main()
