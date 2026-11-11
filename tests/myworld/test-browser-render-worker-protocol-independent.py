#!/usr/bin/env python3
"""Freeze exact candidate modules and execute CPU-only independent transport oracle."""
import argparse, hashlib, json, re, subprocess
from pathlib import Path
from datetime import datetime, timezone
ROOT=Path(__file__).resolve().parents[2]
MODULES=['browser-render-worker-protocol.js','browser-render-worker-transport.js','browser-render-worker-replay.js','browser-physical-output.js']
def sha(x):return hashlib.sha256(x).hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--candidate-root',required=True,type=Path);p.add_argument('--candidate-commit',required=True);a=p.parse_args()
 if not re.fullmatch('[a-f0-9]{40}',a.candidate_commit):p.error('Exact pushed candidate commit required')
 base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-render-worker-protocol-independent','--mkdir'],text=True).strip());run=base/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');run.mkdir();modules=run/'modules';modules.mkdir();(modules/'package.json').write_text('{"type":"module"}\n')
 hashes={}
 for name in MODULES:
  path='tools/browser-gameplay/web/'+name;data=subprocess.check_output(['git','show',a.candidate_commit+':'+path],cwd=a.candidate_root);(modules/name).write_bytes(data);hashes[path]=sha(data)
 fixture=ROOT/'tests/myworld/test-browser-render-worker-protocol-independent.mjs';frozen=run/fixture.name;frozen.write_bytes(fixture.read_bytes());command=['node',str(frozen),str(modules)];r=subprocess.run(command,cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.PIPE);(run/'stdout.log').write_bytes(r.stdout);(run/'stderr.log').write_bytes(r.stderr)
 receipt=dict(passed=r.returncode==0,exitCode=r.returncode,cpuOnly=True,browserStarted=False,gpuExecuted=False,serverStarted=False,actualWorker=False,candidateCommit=a.candidate_commit,sourceSha256=hashes,fixtureSha256=sha(frozen.read_bytes()),runnerSha256=sha(Path(__file__).read_bytes()),command=command,nodeVersion=subprocess.check_output(['node','--version'],text=True).strip(),stdoutSha256=sha(r.stdout),stderrSha256=sha(r.stderr))
 (run/'result.json').write_text(json.dumps(receipt,indent=2)+'\n');print(r.stdout.decode(),end='');print(r.stderr.decode(),end='');print('Receipt:',run/'result.json');r.check_returncode()
if __name__=='__main__':main()
