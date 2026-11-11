#!/usr/bin/env python3
"""Attach only to an existing isolated loopback WebDriver; never launch processes.
Normal pointer route input is opt-in --drive; otherwise observe manual travel.
"""
import argparse,hashlib,json,math,re,time,urllib.request
from pathlib import Path

SNAPSHOT=r"""
const f=document.querySelector('#runtime'),w=f?.contentWindow||window,p=w.browserProof,g=p?.gpu;
if(!g)throw Error('No existing private renderer');
const c=w.document.querySelector('#browser-webgl'),r=c?.getBoundingClientRect(),fr=f?.getBoundingClientRect();
const take=(k,n=32)=>Array.isArray(g[k])?g[k].slice(-n):null;
return {clock:w.performance.now(),url:w.location.href,visible:w.document.visibilityState,focused:document.hasFocus(),
 fixture:p.repeatableFixture,requestedFixture:p.repeatableFixtureRequested,route:p.javaPrivateLoopbackPort??null,transportConnections:w.browserTransport?.connections instanceof w.Map&&w.browserTransport.connections.size<=8?Array.from(w.browserTransport.connections.values(),e=>({url:e.socket?.url,readyState:e.socket?.readyState,closed:e.closed})):null,policies:p.privatePolicies,state:p.state,error:g.lastError,
 contextLost:g.contextLost,ready:g.worldReadiness,pose:g.pose,physical:g.physicalOutput,worker:g.renderWorker,
 frames:g.javaWorldFrames,reportedFrames:g.javaBridgeReportedWorldFrames,held:g.heldWorldFrames,lastFrame:g.lastFrame,
 uploadedChunks:g.uploadedChunks,uploadedBytes:g.uploadedBytes,gpuBytes:g.gpuBytes,
 streams:take('worldStreamSamples'),recentWorldIntervals:take('worldFrameSamples',64),
 bridgeCompletedWorldOnly:take('javaBridgeWorkSamples',8),
 loadProfile:g.loadProfile?{totalReports:g.loadProfile.totalReports,last:g.loadProfile.last}:null,loadProfileSamples:take('loadProfileSamples'),
 bounds:r?{x:(fr?.x||0)+r.x,y:(fr?.y||0)+r.y,w:r.width,h:r.height,
 logicalW:g.physicalOutput?.logicalWidth||g.display?.internalWidth,logicalH:g.physicalOutput?.logicalHeight||g.display?.internalHeight}:null};
"""

def validate_route(a):
 if not isinstance(a,dict) or a.get('schema')!=1 or a.get('account')!='browserdemo':raise ValueError('Explicit browserdemo schema1 route required')
 origin=a.get('origin');legs=a.get('legs')
 if not isinstance(origin,list) or len(origin)!=3 or any(type(x)!=int for x in origin):raise ValueError('Integer signed origin required')
 if not isinstance(legs,list) or not 1<=len(legs)<=64:raise ValueError('1..64 bounded legs required')
 previous=origin
 for l in legs:
  if not isinstance(l,dict):raise ValueError('Route legs must be objects')
  t=l.get('target');click=l.get('click')
  if not isinstance(t,list) or len(t)!=3 or any(type(x)!=int for x in t) or t[2]!=origin[2]:raise ValueError('Same signed-level targets required')
  if not 1<=max(abs(t[i]-previous[i]) for i in [0,1])<=8:raise ValueError('Each ordinary walking leg must be1..8 tiles; no teleport route')
  if click is not None and (not isinstance(click,list)or len(click)!=2 or any(type(x)not in [int,float]or not math.isfinite(x)or x<0 for x in click)):raise ValueError('Finite positive logical clicks required')
  if type(l.get('expectTransition',False))!=bool:raise ValueError('Transition expectation must be boolean')
  previous=t
 return a

def validate_stage_route(stage):
 if stage.get('fixture')!='repeatable-v1' or stage.get('tcp')!='127.0.0.1:43649' or stage.get('websocket') not in ['127.0.0.1:43549','ws://127.0.0.1:43549/']:raise ValueError('Frozen repeatable stage TCP43649/WS43549 required')
 return 'ws://127.0.0.1:43549/'
def validate_live_route(client):
 # Bootstrap's route event is absent in benchmark mode; actual open sockets,
 # fixture policy, frozen stage and fresh server authority are independent proof.
 if client.get('route') not in [None,43649]:raise ValueError('Reported Java route conflicts with private fixture')
 if client.get('requestedFixture')!='repeatable-v1':raise ValueError('Actual repeatable fixture policy missing')
 sockets=client.get('transportConnections')
 if not isinstance(sockets,list) or not 1<=len(sockets)<=8:raise ValueError('Bounded actual transport socket evidence required')
 active=[x for x in sockets if x.get('readyState')==1 and x.get('closed')is False]
 if len(active)!=1 or active[0].get('url')!='ws://127.0.0.1:43549/':raise ValueError('Actual open private WebSocket43549 required')
 return True

def position(a):return [a.get('x'),a.get('y'),a.get('level')]
def summarize(rows):
 if not rows:raise ValueError('No samples')
 first,last=rows[0],rows[-1];b,c=first['client'],last['client'];epochs=sorted({x['client'].get('ready',{}).get('epoch')for x in rows if isinstance(x['client'].get('ready',{}).get('epoch'),int)})
 # Histories overlap; retain maxima only, never add repeated ring entries or label
 # held images/world-frame count per route second as steady-state FPS.
 intervals=[v for x in rows for v in x['client'].get('recentWorldIntervals',[])or[] if isinstance(v,(int,float))]
 return {'elapsedHostMs':last['hostMs']-first['hostMs'],'worldFramesDelta':c['frames']-b['frames'],'heldDelta':c['held']-b['held'],
 'uploadedChunksDelta':c['uploadedChunks']-b['uploadedChunks'],'uploadedBytesDelta':c['uploadedBytes']-b['uploadedBytes'],
 'epochs':epochs,'maxObservedRecentCompletedWorldIntervalMs':max(intervals,default=None),
 'maxObservedPresentationAgeMs':max((max(0,x['client'].get('clock',0)-x['client'].get('lastFrame',x['client'].get('clock',0)))for x in rows),default=0),
 'maxPollRoundTripMs':max(x['pollRoundTripMs']for x in rows),'sampleCount':len(rows),
 'loadReportDelta':(c.get('loadProfile')or{}).get('totalReports',0)-(b.get('loadProfile')or{}).get('totalReports',0),
 'loadReportPollGaps':sum(1 for previous,current in zip(rows,rows[1:])if (current['client'].get('loadProfile')or{}).get('totalReports',0)-(previous['client'].get('loadProfile')or{}).get('totalReports',0)>len(current['client'].get('loadProfileSamples')or[])),
 'scope':'Diagnostic route elapsed includes polling/input/settling. Ring histories overlap; no percentile/count deduplication, no held-frame FPS. frameWork spans cover completed world only; load-only optional diagnostics required for held CPU attribution.'}

class ExistingSession:
 def __init__(self,p):
  a=json.loads(Path(p).read_text());port=a['port'];sid=a['sessionId']
  if type(port)!=int or not 1<=port<=65535 or not re.fullmatch('[A-Za-z0-9-]{1,100}',sid):raise ValueError('Invalid existing isolated session receipt')
  self.base=f'http://127.0.0.1:{port}/session/{sid}'
 def call(self,path,body):
  req=urllib.request.Request(self.base+path,data=json.dumps(body).encode(),headers={'Content-Type':'application/json'})
  with urllib.request.urlopen(req,timeout=40)as r:v=json.load(r)['value']
  if isinstance(v,dict)and 'error'in v and 'message'in v:raise RuntimeError(str(v)[:1800])
  return v
 def snapshot(self):return self.call('/execute/sync',{'script':SNAPSHOT,'args':[]})
 def click(self,b,click):
  if not b or min(b['w'],b['h'],b['logicalW'],b['logicalH'])<=0:raise ValueError('Actual positive logical/DOM canvas dimensions required')
  if not 0<=click[0]<b['logicalW']or not 0<=click[1]<b['logicalH']:raise ValueError('Click outside actual logical viewport')
  x=round(b['x']+click[0]*b['w']/b['logicalW']);y=round(b['y']+click[1]*b['h']/b['logicalH'])
  self.call('/actions',{'actions':[{'type':'pointer','id':'travel-pointer','parameters':{'pointerType':'mouse'},'actions':[{'type':'pointerMove','origin':'viewport','x':x,'y':y,'duration':0},{'type':'pointerDown','button':0},{'type':'pointerUp','button':0}]}]})

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--session',type=Path);p.add_argument('--authority',type=Path);p.add_argument('--stage',type=Path);p.add_argument('--route',type=Path,required=True);p.add_argument('--out',type=Path);p.add_argument('--drive',action='store_true');p.add_argument('--require-load-profile',action='store_true');p.add_argument('--dry-run',action='store_true');p.add_argument('--poll-ms',type=int,default=250);p.add_argument('--timeout',type=int,default=90);a=p.parse_args();route=validate_route(json.loads(a.route.read_text()))
 if a.dry_run:print(json.dumps({'valid':True,'route':route,'noBrowserOrProcesses':True}));return
 if not all([a.session,a.authority,a.stage,a.out]):p.error('Existing session, private authority, frozen stage and new output directory required')
 if not 100<=a.poll_ms<=1000 or not 5<=a.timeout<=180:p.error('Bounded cadence/timeout required')
 if a.out.exists():p.error('Refuse overwriting prior evidence')
 # All generated evidence must remain under the declared category, irrespective
 # of the invocation cwd. No file in a fixture/server or stage is modified.
 repo=Path(__file__).resolve().parents[2];allowed=[(repo/'output/diagnostics').resolve(),(repo.parent/'Core-Framework/output/diagnostics').resolve()]
 if not any(a.out.resolve().is_relative_to(d)for d in allowed):p.error('Use workspace-layout.py path diagnostics browser-travel-loading, then a new run directory')
 validate_stage_route(json.loads(a.stage.read_text()));session=ExistingSession(a.session);a.out.mkdir(parents=True);start=time.monotonic();rows=[];result={'accepted':False,'scope':'Actual original pointer/manual walking with server-authoritative positions; diagnostic load timings, not steady-state FPS','drive':a.drive,'route':route,'publicChanged':False,'stageSHA256':hashlib.sha256(a.stage.read_bytes()).hexdigest(),'sessionSHA256':hashlib.sha256(a.session.read_bytes()).hexdigest(),'legs':[]}
 def sample():
  t=time.monotonic();c=session.snapshot();st=a.authority.stat();auth=json.loads(a.authority.read_text());age=max(0,time.time()-st.st_mtime)
  if age>4 or auth.get('private')is not True or auth.get('valid')is not True or auth.get('online')is not True or auth.get('account')!=route['account']:raise RuntimeError('Fresh active private browserdemo authority required')
  if c.get('error')or c.get('contextLost')or c.get('visible')!='visible'or c.get('fixture',{}).get('username')!=route['account']or c.get('worker',{}).get('enabled')is not True:raise RuntimeError('Current WorkerON private repeatable-v1 route/visibility/error gate failed')
  validate_live_route(c)
  if a.require_load_profile and not isinstance(c.get('loadProfile',{}),dict):raise RuntimeError('Load-only diagnostics missing; cannot attribute held work')
  if a.require_load_profile and not c.get('loadProfile'):raise RuntimeError('Load-only diagnostics missing; cannot attribute held work')
  if rows and(auth.get('stableTicks',0)<rows[-1]['authority'].get('stableTicks',0)or c['frames']<rows[-1]['client']['frames']or c['held']<rows[-1]['client']['held']):raise RuntimeError('Fixture/session counters reset during route')
  r={'hostMs':(time.monotonic()-start)*1000,'pollRoundTripMs':(time.monotonic()-t)*1000,'authorityAgeSeconds':age,'authority':{k:auth.get(k)for k in ['x','y','level','stableTicks','fixture','account','private','valid','online']},'client':c};rows.append(r)
  if len(rows)>50000:raise RuntimeError('Bounded sample count exceeded')
  with(a.out/'samples.jsonl').open('a')as f:f.write(json.dumps(r)+'\n')
  return r
 try:
  baseline=sample()
  if position(baseline['authority'])!=route['origin']or baseline['client']['ready']['phase']!='ready':raise RuntimeError('Set up origin and settle BEFORE measurement; teleport setup is excluded')
  for i,leg in enumerate(route['legs']):
   begin=sample();segment=[begin];target=leg['target'];previous=route['origin']if i==0 else route['legs'][i-1]['target']
   if a.drive:
    if leg.get('click')is None:raise ValueError('Calibrate every logical pointer click before --drive')
    session.click(begin['client']['bounds'],leg['click'])
   deadline=time.monotonic()+a.timeout;stable=0
   while time.monotonic()<deadline:
    time.sleep(a.poll_ms/1000);r=sample();segment.append(r);pos=position(r['authority'])
    if pos[2]!=target[2]or max(abs(pos[k]-previous[k])for k in [0,1])>12:raise RuntimeError('Unexpected signed-layer/jump; ordinary walk evidence invalid')
    fresh=r['client']['frames']>=begin['client']['frames']+2
    transitioned=r['client']['ready']['epoch']>begin['client']['ready']['epoch']
    ok=pos==target and r['client']['ready']['phase']=='ready'and fresh and(not leg.get('expectTransition')or transitioned)
    stable=stable+1 if ok else 0
    if stable>=2:break
   else:raise RuntimeError('Target/actual source epoch/fresh ready timeout')
   new_streams=[x for x in r['client'].get('streams',[])or[] if x.get('epoch',0)>begin['client']['ready']['epoch']]
   if leg.get('expectTransition')and(not new_streams or any(x.get('ordinary')is not True for x in new_streams)):raise RuntimeError('Expected ordinary-walking completed stream receipt missing/hard')
   result['legs'].append({'index':i,'target':target,'summary':summarize(segment),'before':begin,'after':r});(a.out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
  result['summary']=summarize(rows);result['accepted']=True
 except BaseException as error:
  result['error']=str(error)[:2000];raise
 finally:(a.out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
 print(json.dumps({'accepted':result['accepted'],'output':str(a.out),'summary':result.get('summary')},indent=2))
if __name__=='__main__':main()
