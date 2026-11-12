#!/usr/bin/env python3
"""Compile exact committed production helpers plus independent real-I/O fixture."""
import hashlib,json,pathlib,re,subprocess,sys,time
ROOT=pathlib.Path(__file__).resolve().parents[2]
commit=sys.argv[1] if len(sys.argv)>1 else ''
if not re.fullmatch('[0-9a-f]{40}',commit):raise SystemExit('Exact source commit required')
subject=pathlib.Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-startup-feedback','--mkdir'],text=True).strip());run=subject/('bulk-java-review-'+str(time.time_ns()));(run/'classes').mkdir(parents=True)
sources=[];hashes={}
for name in ['BrowserBootstrapFiles.java','BrowserBulkStaging.java']:
 path='tools/browser-gameplay/src/orsc/browser/'+name;raw=subprocess.check_output(['git','show',commit+':'+path],cwd=ROOT);target=run/name;target.write_bytes(raw);sources.append(target);hashes[path]=hashlib.sha256(raw).hexdigest()
fixture=ROOT/'tests/myworld/BrowserBulkStagingReviewFixture.java';sources.append(fixture);hashes[str(fixture.relative_to(ROOT))]=hashlib.sha256(fixture.read_bytes()).hexdigest()
commands=[['nice','-n','10','javac','-source','8','-target','8','-d',str(run/'classes'),*map(str,sources)],['nice','-n','10','java','-cp',str(run/'classes'),'orsc.browser.BrowserBulkStagingReviewFixture',str(run/'files')]]
for i,cmd in enumerate(commands):
 result=subprocess.run(cmd,cwd=ROOT,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT);(run/f'{i}.log').write_text(result.stdout);print(result.stdout,end='');result.check_returncode()
(run/'receipt.json').write_text(json.dumps({'productionSourceCommit':commit,'sourceSha256':hashes,'commands':commands,'passed':True,'browserOrNetworkStarted':False},indent=2)+'\n');print(run/'receipt.json')
