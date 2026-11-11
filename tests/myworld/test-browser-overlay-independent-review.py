#!/usr/bin/env python3
"""Independent CPU review of exact candidate source; never starts a browser/server/GPU."""
from pathlib import Path
from datetime import datetime,timezone
import argparse,hashlib,json,re,subprocess
ROOT=Path(__file__).resolve().parents[2]
def sha(data):return hashlib.sha256(data).hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--candidate-root',type=Path,required=True);p.add_argument('--candidate-commit',required=True);p.add_argument('--client-jar',type=Path,required=True);p.add_argument('--rectangles',action='store_true');args=p.parse_args()
 if not re.fullmatch('[a-f0-9]{40}',args.candidate_commit):p.error('An exact candidate commit is required')
 candidate=args.candidate_root.resolve();jar=args.client_jar.resolve()
 category=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-overlay-rect-independent-review','--mkdir'],text=True).strip())
 run=category/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');run.mkdir();sources=run/'source';sources.mkdir();classes=run/'classes';classes.mkdir()
 paths=['Client_Base/src/orsc/graphics/two/'+n for n in ['WorldOverlayCoverage.java','WorldOverlayDeltaTransport.java','WorldOverlayTileTracker.java']]+['tools/browser-gameplay/web/'+n for n in ['browser-overlay.js','browser-overlay-delta.js','browser-retained-frame.js']]
 hashes={};queue=list(paths)
 if args.rectangles:queue += ['tools/browser-gameplay/web/browser-owned-state.js','tools/browser-gameplay/web/browser-sprite-atlas.js']
 while queue:
  relative=queue.pop(0)
  if relative in hashes:continue
  if len(hashes)>32:raise ValueError('Unexpectedly large dependency graph')
  data=subprocess.check_output(['git','show',args.candidate_commit+':'+relative],cwd=candidate);hashes[relative]=sha(data);(sources/Path(relative).name).write_bytes(data)
  if relative.endswith('.js'):
   for module in re.findall(r"from\s+['\"]\./([^'\"]+)['\"]",data.decode()):
    if '/'in module or '..'in module:raise ValueError('Unexpected module path')
    queue.append('tools/browser-gameplay/web/'+module)
 def execute(name,command):
  result=subprocess.run(command,cwd=ROOT,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT);(run/(name+'.log')).write_text(result.stdout);print(result.stdout,end='');result.check_returncode()
 fixture=ROOT/'tests/myworld/WorldOverlayTileIndependentReviewFixture.java'
 fixtures=[fixture]+([ROOT/'tests/myworld/WorldOverlayRectIndependentReviewFixture.java']if args.rectangles else [])
 execute('compile',['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),*[str(sources/Path(n).name)for n in paths[:3]],*[str(q)for q in fixtures]])
 execute('footprint',['java','-Djava.awt.headless=true','-cp',str(classes)+':'+str(jar),'orsc.graphics.two.WorldOverlayTileIndependentReviewFixture'])
 if args.rectangles:execute('rect-footprint',['java','-Djava.awt.headless=true','-cp',str(classes)+':'+str(jar),'orsc.graphics.two.WorldOverlayRectIndependentReviewFixture'])
 execute('spec',['node',str(ROOT/'tests/myworld/test-browser-overlay-unpack-spec.mjs')])
 execute('observe',['node',str(ROOT/'tests/myworld/test-browser-overlay-observation-independent.mjs'),str(sources/'browser-overlay.js')])
 if args.rectangles:
  execute('rect-transfer',['node',str(ROOT/'tests/myworld/test-browser-overlay-rect-independent.mjs'),str(sources/'browser-overlay.js')])
  execute('packing',['node',str(ROOT/'tests/myworld/test-browser-overlay-staging-independent.mjs'),str(sources/'browser-overlay-staging.js')])
 independent=[*fixtures,*[ROOT/'tests/myworld'/n for n in ['browser-overlay-unpack-spec-oracle.mjs','test-browser-overlay-unpack-spec.mjs','test-browser-overlay-observation-independent.mjs','test-browser-overlay-independent-review.py']+(['test-browser-overlay-rect-independent.mjs','test-browser-overlay-staging-independent.mjs']if args.rectangles else [])]]
 receipt=dict(passed=True,cpuOnly=True,browserStarted=False,serverStarted=False,gpuExecuted=False,candidateCommit=args.candidate_commit,candidateSourceSha256=hashes,dependencyJarSha256=sha(jar.read_bytes()),independentSourceSha256={str(q.relative_to(ROOT)):sha(q.read_bytes())for q in independent},scope='Per-pixel footprint/ACK/planner obligations and independent Khronos transfer semantics over actual candidate overlay/staging modules. No real GPU, performance or actual game claim.')
 (run/'result.json').write_text(json.dumps(receipt,indent=2)+'\n');print('Independent review result:',run/'result.json')
if __name__=='__main__':main()
