#!/usr/bin/env python3
"""Freeze exact diagnostic intake JS and run independent CPU-only bounded scalar oracle."""
import argparse,hashlib,json,re,subprocess
from pathlib import Path
from datetime import datetime,timezone
ROOT=Path(__file__).resolve().parents[2]
def sha(x):return hashlib.sha256(x).hexdigest()
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--candidate-root',required=True,type=Path);p.add_argument('--candidate-commit',required=True);a=p.parse_args();assert re.fullmatch('[0-9a-f]{40}',a.candidate_commit)
base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-load-profile-native-independent','--mkdir'],text=True).strip());run=base/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');run.mkdir();path='tools/browser-gameplay/web/browser-region-load-profile.js';data=subprocess.check_output(['git','show',a.candidate_commit+':'+path],cwd=a.candidate_root);module=run/'browser-region-load-profile.mjs';module.write_bytes(data);fixture=ROOT/'tests/myworld/test-browser-load-profile-native-independent.mjs';frozen=run/fixture.name;frozen.write_bytes(fixture.read_bytes());cmd=['node',str(frozen),str(module)];r=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.PIPE);(run/'stdout.log').write_bytes(r.stdout);(run/'stderr.log').write_bytes(r.stderr)
receipt=dict(passed=r.returncode==0,exitCode=r.returncode,candidateCommit=a.candidate_commit,sourcePath=path,sourceSha256=sha(data),fixtureSha256=sha(frozen.read_bytes()),runnerSha256=sha(Path(__file__).read_bytes()),stdoutSha256=sha(r.stdout),stderrSha256=sha(r.stderr),command=cmd,cpuOnly=True,gpuExecuted=False,browserStarted=False,serverStarted=False);(run/'result.json').write_text(json.dumps(receipt,indent=2)+'\n');print(r.stdout.decode(),end='');print(r.stderr.decode(),end='');print('Receipt:',run/'result.json');r.check_returncode()
