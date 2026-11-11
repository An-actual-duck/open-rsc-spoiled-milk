#!/usr/bin/env python3
"""Frozen journal isolation fixtures and exact legacy hot-body hashes; no browser/GPU."""
import argparse,hashlib,json,re,subprocess
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
BASE='5d2359d1e88939b71463a10ddbf5743314b6d11f'
SOURCES=['Client_Base/src/orsc/graphics/two/'+name for name in ['GraphicsController.java','WorldOverlayCoverage.java','WorldOverlayDeltaTransport.java','WorldOverlayTileTracker.java','Fonts.java']]
OBSERVER='Client_Base/src/orsc/graphics/two/OverlayJournalObserver.java'
HOT=['private void plotLetter(','private void plotLetterAntialiased(','private void coverageOpaque(','private void coverageOpaqueSpan(','private void coverageMultiply(']
def sha(data):return hashlib.sha256(data).hexdigest()
def body(source,marker):
 start=source.index(marker);at=source.index('{',start);depth=0;state='code';i=at
 while i<len(source):
  c=source[i];n=source[i+1:i+2]
  if state=='line':
   if c=='\n':state='code'
  elif state=='block':
   if c=='*'and n=='/':state='code';i+=1
  elif state in ['string','char']:
   if c=='\\':i+=1
   elif c==('"'if state=='string'else"'"):state='code'
  elif c=='/'and n=='/':state='line';i+=1
  elif c=='/'and n=='*':state='block';i+=1
  elif c=='"':state='string'
  elif c=="'":state='char'
  elif c=='{':depth+=1
  elif c=='}':
   depth-=1
   if depth==0:return source[start:i+1].encode()
  i+=1
 raise ValueError('Unterminated method '+marker)
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--candidate-root',required=True,type=Path);p.add_argument('--candidate-commit',required=True);p.add_argument('--client-jar',required=True,type=Path);p.add_argument('--property',default='spoiledmilk.browserOverlayJournalObserve');p.add_argument('--enabled-value',default='true');args=p.parse_args()
 if not re.fullmatch('[a-f0-9]{40}',args.candidate_commit):p.error('Exact candidate commit required')
 candidate=args.candidate_root.resolve();jar=args.client_jar.resolve();base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-overlay-journal-independent','--mkdir'],text=True).strip());run=base/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');run.mkdir();sourceHashes={};hotHashes={};results={};logs={}
 common=[ROOT/'tests/myworld'/name for name in ['WorldOverlayCoverageFixture.java','OverlayJournalRenderingIndependentFixture.java']];core=ROOT/'tests/myworld/OverlayJournalCoreIndependentFixture.java';export=ROOT/'tests/myworld/OverlayJournalLiteralExportFixture.java'
 def execute(name,command):
  r=subprocess.run(command,cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.PIPE);(run/(name+'.stdout')).write_bytes(r.stdout);(run/(name+'.stderr')).write_bytes(r.stderr);r.check_returncode();logs[name]={'stdout':sha(r.stdout),'stderr':sha(r.stderr)};return r.stdout
 for label,commit in [('baseline',BASE),('candidate',args.candidate_commit)]:
  folder=run/label;folder.mkdir();src=folder/'source';src.mkdir();classes=folder/'classes';classes.mkdir();sourceHashes[label]={}
  paths=SOURCES+([OBSERVER]if label=='candidate'else[])
  for path in paths:
   data=subprocess.check_output(['git','show',commit+':'+path],cwd=candidate);(src/Path(path).name).write_bytes(data);sourceHashes[label][path]=sha(data)
  graphics=(src/'GraphicsController.java').read_text();hotHashes[label]={name:sha(body(graphics,name))for name in HOT}
  if label=='candidate':assert hotHashes['candidate']==hotHashes['baseline'],'Legacy per-pixel glyph/coverage helper changed'
  fixtures=common+([core,export]if label=='candidate'else[])
  for fixture in fixtures:(src/fixture.name).write_bytes(fixture.read_bytes())
  execute('compile-'+label,['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),*[str(src/Path(name).name)for name in paths],*[str(src/q.name)for q in fixtures]])
  for flag in ['false',args.enabled_value]:
   name=label+'-'+flag;results[name]=execute(name,['java','-Xmx512m','-Djava.awt.headless=true','-D'+args.property+'='+flag,'-cp',str(classes)+':'+str(jar),'orsc.graphics.two.OverlayJournalRenderingIndependentFixture'])
  if label=='candidate':
   execute('core',['java','-Xmx512m','-Djava.awt.headless=true','-cp',str(classes)+':'+str(jar),'orsc.graphics.two.OverlayJournalCoreIndependentFixture'])
   execute('literal-export',['java','-Xmx512m','-Djava.awt.headless=true','-D'+args.property+'='+args.enabled_value,'-cp',str(classes)+':'+str(jar),'orsc.graphics.two.OverlayJournalLiteralExportFixture',str(run/'literal-journals.json')])
 reference=results['baseline-false'];assert reference.startswith(b'SNAPSHOT ')and len(reference.strip())==73,'Missing frozen snapshot'
 for name,data in results.items():assert data==reference,'Literal RGB/T/error mismatch: '+name
 receipt=dict(passed=True,cpuOnly=True,browserStarted=False,gpuExecuted=False,serverStarted=False,baselineCommit=BASE,candidateCommit=args.candidate_commit,property=args.property,enabledValue=args.enabled_value,sourceSha256=sourceHashes,legacyHotBodySha256=hotHashes,fixtureSha256={q.name:sha(q.read_bytes())for q in common+[core,export]},runnerSha256=sha(Path(__file__).read_bytes()),dependencyJarSha256=sha(jar.read_bytes()),snapshot=reference.decode().strip(),literalJournalsSha256=sha((run/'literal-journals.json').read_bytes()),logs=logs,scope='Frozen Java rendering/error parity, unchanged hot bodies, independent journal ownership/order/lifecycle/caps and actual hooked journal/literal export. Native replay/presentation requires separate JS proof; no GPU/FPS claim.')
 (run/'result.json').write_text(json.dumps(receipt,indent=2)+'\n');print('PASS independent journal review:',run/'result.json')
if __name__=='__main__':main()
