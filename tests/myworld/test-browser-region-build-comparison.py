#!/usr/bin/env python3
"""Offline verify exact private walking comparison against hash-bound raw routes."""
import argparse,hashlib,json,math,statistics
from pathlib import Path

def check(value,message):
 if not value:raise ValueError(message)
def verify(path,expected):
 check(hashlib.sha256(path.read_bytes()).hexdigest()==expected,'Comparison receipt hash mismatch')
 a=json.loads(path.read_text());check(a['publicChanged']is False and a['ordinaryDefaultsChanged']is False,'Private/default gates')
 check([c['mode']for c in a['cohorts']]==['off','on','off','on'],'Matched order')
 sets={'off':[],'on':[]};counts=0
 for c in a['cohorts']:
  p=Path(c['source']);check(hashlib.sha256(p.read_bytes()).hexdigest()==c['sha256'],'Raw route hash '+c['name']);r=json.loads(p.read_text())
  check(r['accepted']is True and r['publicChanged']is False and r['drive']is True,'Original pointer authority route '+c['name']);check(len(r['legs'])==7,'Calibration plus six boundary legs')
  gaps=[]
  for i,l in enumerate(r['legs']):
   b=l['before']['client'];e=l['after']['client'];expectedMode=c['mode']=='on'
   for state in [b,e]:
    check(state['error']is None and state['contextLost']is False and state['ready']['phase']=='ready','Error/ready')
    check(state['policies']['regionBuild']=={'available':True,'enabled':expectedMode,'requested':expectedMode},'Actual Java policy')
    check(state['worker']['enabled']is True,'Worker-on comparison')
    check(state['requestedFixture']=='repeatable-v1','Fixture policy')
    check(any(x['url']=='ws://127.0.0.1:43549/'and x['readyState']==1 and x['closed']is False for x in state['transportConnections']),'Actual socket')
   check(l['summary']['loadReportPollGaps']==0,'No load report gaps')
   if i:
    n=e['frames']-b['frames'];intervals=e['recentWorldIntervals'];check(0<n<=len(intervals)<=64,'Full measured boundary new-frame interval tail available');gap=max(intervals[-n:]);check(gap==l['summary']['maxNewWorldIntervalMs'],'New tail maximum')
    check(b['ready']['epoch']==i and e['ready']['epoch']==i+1,'Consecutive source authority epochs')
    check(any(s['epoch']==i+1 and s['ordinary']is True for s in e['streams']),'Completed ordinary stream receipt')
    check(l['before']['authority']['level']==l['after']['authority']['level']==0,'Signed level unchanged');gaps.append(gap);counts+=1
  check(gaps[0]==c['coldCrossingGapMs']and gaps[1]==c['firstReturnGapMs']and gaps[2:]==c['warmCrossingGapsMs'],'Correct cold/return/warm separation')
  check(statistics.mean(gaps[2:])==c['warmMeanMs'],'Warm mean');check(math.isclose(r['summary']['elapsedHostMs'],c['routeElapsedMs'],abs_tol=1e-6),'Route elapsed');sets[c['mode']]+=gaps[2:]
 off=statistics.mean(sets['off']);on=statistics.mean(sets['on']);reduction=100*(off-on)/off
 check(len(sets['off'])==len(sets['on'])==8,'Eight warm crossings per policy')
 for field,value in [('meanWarmGapOffMs',off),('meanWarmGapOnMs',on),('meanWarmGapReductionPercent',reduction)]:check(math.isclose(a[field],value,abs_tol=1e-9),'Recomputed '+field)
 check(max(sets['on'])==a['maxWarmGapOnMs']==3300,'Remaining multi-second outlier retained')
 check(any(x['name']=='candidate-off-2'for x in a['excludedAttempts']),'Preserved excluded setup attempt')
 return {'passed':True,'comparisonSHA256':expected,'cohorts':4,'ordinaryBoundaryLegs':counts,'warmSamplesPerPolicy':8,'offMeanMs':off,'onMeanMs':on,'reductionPercent':reduction,'maximumOnWarmGapMs':max(sets['on']),'scope':'Offline raw-evidence/hash/arithmetic/authority proof, not new hardware or broad-device acceptance.'}
if __name__=='__main__':
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('comparison',type=Path);p.add_argument('--expected-sha256',required=True);a=p.parse_args();print(json.dumps(verify(a.comparison,a.expected_sha256),indent=2))
