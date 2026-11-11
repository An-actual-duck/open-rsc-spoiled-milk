#!/usr/bin/env python3
"""Frozen producer/native-injection and poison proof. Never starts GPU/server/browser."""
import argparse,hashlib,importlib.util,json,re,subprocess
from datetime import datetime,timezone
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];BASE='58c61d98562e287c6e35a6056009cbe81c6d3464'
HELPER=ROOT/'tests/myworld/test-browser-overlay-journal-independent.py'
spec=importlib.util.spec_from_file_location('hotbody',HELPER);helper=importlib.util.module_from_spec(spec);spec.loader.exec_module(helper)
SOURCES=['Client_Base/src/orsc/graphics/two/'+n for n in ['GraphicsController.java','WorldOverlayCoverage.java','WorldOverlayDeltaTransport.java','WorldOverlayTileTracker.java','Fonts.java','OverlayJournalObserver.java']]
COMMON=['WorldOverlayCoverageFixture.java','SceneSpriteRasterPolicyFixture.java','OverlayJournalRenderingIndependentFixture.java','SegmentedTextPacketReference.java','SegmentedTextInjectedBackendFixture.java','SegmentedTextSceneGuardFixture.java','SegmentedTextPhaseContextFixture.java']
EXTRA=['SegmentedTextCoreIndependentFixture.java','SegmentedTextPoisonIndependentFixture.java']
PROP='spoiledmilk.browserSegmentedTextRaster'
def sha(data):return hashlib.sha256(data).hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--candidate-root',type=Path,required=True);p.add_argument('--candidate-commit',required=True);p.add_argument('--client-jar',type=Path,required=True);p.add_argument('--trace-cases',action='store_true');a=p.parse_args()
 if not re.fullmatch('[a-f0-9]{40}',a.candidate_commit):p.error('Exact candidate commit required')
 root=a.candidate_root.resolve();jar=a.client_jar.resolve();base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-segmented-text-independent','--mkdir'],text=True).strip());run=base/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');run.mkdir();hashes={};hot={};results={};logs={}
 def execute(name,command):
  r=subprocess.run(command,cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.PIPE);(run/(name+'.stdout')).write_bytes(r.stdout);(run/(name+'.stderr')).write_bytes(r.stderr);r.check_returncode();logs[name]=dict(stdout=sha(r.stdout),stderr=sha(r.stderr));return r.stdout
 def java(classes,flag,main,*args):return ['java','-Xmx512m','-XX:-OmitStackTraceInFastThrow','-Djava.awt.headless=true','-Dspoiledmilk.browserOverlayDeltaTransport=false','-Dspoiledmilk.browserOverlayJournalObserve=false','-Dspoiledmilk.testRenderingCaseTrace='+str(a.trace_cases).lower(),'-D'+PROP+'='+flag,'-cp',str(classes)+':'+str(jar),'orsc.graphics.two.'+main,*args]
 for label,commit in [('baseline',BASE),('candidate',a.candidate_commit)]:
  folder=run/label;folder.mkdir();src=folder/'source';src.mkdir();classes=folder/'classes';classes.mkdir();hashes[label]={};paths=SOURCES+(['Client_Base/src/orsc/graphics/two/SegmentedTextRaster.java']if label=='candidate'else[])
  for path in paths:
   data=subprocess.check_output(['git','show',commit+':'+path],cwd=root);(src/Path(path).name).write_bytes(data);hashes[label][path]=sha(data)
  hot[label]={m:sha(helper.body((src/'GraphicsController.java').read_text(),m))for m in helper.HOT}
  if label=='candidate':assert hot[label]==hot['baseline'],'Old per-pixel helper changed'
  fixtures=COMMON+(EXTRA if label=='candidate'else[])
  for name in fixtures:(src/name).write_bytes((ROOT/'tests/myworld'/name).read_bytes())
  execute('compile-'+label,['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),*[str(src/Path(n).name)for n in paths],*[str(src/n)for n in fixtures]])
  modes=[('false','accept'),('true','accept')]+([('true','reject'),('true','unavailable')]if label=='candidate'else[])
  for flag,mode in modes:
   name=label+'-'+flag+'-'+mode;args=[mode,label,PROP]+([str(run/'native-packets.json')]if label=='candidate'and flag=='true'and mode=='accept'else[])
   results[name]=execute(name,java(classes,flag,'SegmentedTextInjectedBackendFixture',*args))
  if label=='candidate':
   execute('core',java(classes,'true','SegmentedTextCoreIndependentFixture'))
   execute('poison',java(classes,'true','SegmentedTextPoisonIndependentFixture'))
 reference=results['baseline-false-accept'];assert b'SNAPSHOT 'in reference and b'SCENE-GUARD 'in reference,'Vacuous render corpus'
 for name,value in results.items():assert value==reference,'Literal render/error/Scene ownership mismatch: '+name
 fixtures={n:sha((ROOT/'tests/myworld'/n).read_bytes())for n in COMMON+EXTRA}
 receipt=dict(passed=True,cpuOnly=True,gpuExecuted=False,browserStarted=False,serverStarted=False,baselineCommit=BASE,candidateCommit=a.candidate_commit,sourceSha256=hashes,hotBodySha256=hot,fixturesSha256=fixtures,runnerSha256=sha(Path(__file__).read_bytes()),hotExtractorSha256=sha(HELPER.read_bytes()),jarSha256=sha(jar.read_bytes()),packetCorpusSha256=sha((run/'native-packets.json').read_bytes()),logs=logs,snapshot=reference.decode().strip(),scope='Frozen Java full C/raw-T/error and Scene guard parity, JVM injected native acceptance/rejection, poison/fresh recovery. Actual JS/JNI/GPU requires separate proof.')
 (run/'result.json').write_text(json.dumps(receipt,indent=2)+'\n');print('PASS independent segmented Java:',run/'result.json')
if __name__=='__main__':main()
