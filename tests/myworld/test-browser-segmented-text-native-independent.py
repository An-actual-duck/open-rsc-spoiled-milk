#!/usr/bin/env python3
"""Freeze actual segmented native module and verify actual producer/reference packets."""
import argparse,hashlib,json,re,subprocess
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];MODULE='tools/browser-gameplay/web/browser-segmented-text.js'
def sha(b):return hashlib.sha256(b).hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--candidate-root',required=True,type=Path);p.add_argument('--candidate-commit',required=True);p.add_argument('--java-receipt',required=True,type=Path);a=p.parse_args()
 if not re.fullmatch('[a-f0-9]{40}',a.candidate_commit):p.error('Exact candidate commit required')
 source=a.candidate_root.resolve();receiptPath=a.java_receipt.resolve();java=json.loads(receiptPath.read_text());corpus=receiptPath.parent/'native-packets.json';assert java['passed']and sha(corpus.read_bytes())==java['packetCorpusSha256']
 for path,want in java['sourceSha256']['candidate'].items():assert sha(subprocess.check_output(['git','show',a.candidate_commit+':'+path],cwd=source))==want,'Java changed; rerun Java proof'
 base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-segmented-text-native-independent','--mkdir'],text=True).strip());run=base/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');run.mkdir();data=subprocess.check_output(['git','show',a.candidate_commit+':'+MODULE],cwd=source);module=run/'browser-segmented-text.mjs';module.write_bytes(data)
 test=ROOT/'tests/myworld/test-browser-segmented-text-native-independent.mjs';command=['node',str(test),str(module),str(corpus)];r=subprocess.run(command,cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.PIPE);(run/'stdout.log').write_bytes(r.stdout);(run/'stderr.log').write_bytes(r.stderr);r.check_returncode()
 result=dict(passed=True,cpuOnly=True,gpuExecuted=False,browserStarted=False,serverStarted=False,candidateCommit=a.candidate_commit,moduleSha256=sha(data),javaReceipt=str(receiptPath),javaReceiptSha256=sha(receiptPath.read_bytes()),corpusSha256=sha(corpus.read_bytes()),testSha256=sha(test.read_bytes()),runnerSha256=sha(Path(__file__).read_bytes()),command=command,stdoutSha256=sha(r.stdout),stderrSha256=sha(r.stderr),scope='Actual JS arithmetic over arbitrary prior C/T vs independent JVM packet reference whose aggregate matches frozen baseline; genuine-view normalization, no-write rejection and fault recovery. No actual JNI/GPU/FPS.')
 (run/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(r.stdout.decode(),end='');print('Independent segmented native:',run/'result.json')
if __name__=='__main__':main()
