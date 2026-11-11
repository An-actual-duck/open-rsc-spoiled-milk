#!/usr/bin/env python3
"""Current-source RAM baseline route CPU attribution; no process/browser control."""
import argparse,collections,hashlib,importlib.util,json,re
from pathlib import Path
s=importlib.util.spec_from_file_location('travel',Path(__file__).parents[1]/'analyze-browser-travel-profile.py');travel=importlib.util.module_from_spec(s);s.loader.exec_module(travel)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 a=argparse.ArgumentParser(description=__doc__);a.add_argument('input',type=Path);a.add_argument('--out',type=Path,required=True);o=a.parse_args();src=o.input;out=o.out
 if out.exists():raise ValueError('Preserve prior evidence')
 files=['firefox-profile.json','additional-information.json','profile-start.json','profile-end.json','route/result.json','route/samples.jsonl'];profile=json.loads((src/files[0]).read_text());extra=json.loads((src/files[1]).read_text());start=json.loads((src/files[2]).read_text());route=json.loads((src/files[4]).read_text());rows=[json.loads(x)for x in(src/files[5]).read_text().splitlines()]
 if not route.get('accepted')or not route.get('drive')or route.get('publicChanged')is not False or route['route']['account']!='browserdemo':raise ValueError('Actual accepted private walking required')
 hits=[]
 for p in travel.profiles(profile):
  for t in p['threads']:
   marks=[]
   for row in t['markers']['data']:
    data=travel.cell(t['markers'],row,'data')or{}
    if data.get('name')in['rsc-ram-baseline-route-start','rsc-ram-baseline-route-end']:marks.append({'name':data['name'],'type':data['type'],'time':travel.cell(t['markers'],row,'startTime')})
   if marks:hits.append((p,t,marks))
 if len(hits)!=1:raise ValueError('Exactly one content marker thread required')
 p,t,marks=hits[0]
 if t['name']!='GeckoMain'or t.get('processType')!='tab'or len(marks)!=2 or any(x['type']!='UserTiming'for x in marks):raise ValueError('Content paired UserTiming gate')
 lo=next(x['time']for x in marks if x['name'].endswith('start'));hi=next(x['time']for x in marks if x['name'].endswith('end'))
 if p['meta']['sampleUnits'].get('threadCPUDelta')!='ns'or p['meta']['sampleUnits'].get('time')!='ms'or not 100<hi-lo<180000:raise ValueError('Time units/window gate')
 offset=start['clock']['origin']-p['meta']['startTime'];owned={}
 for r in rows:
  for report in r['client'].get('loadProfileSamples')or[]:owned[report['owner'],report['attempt']]=report
 gaps=[];details=[]
 for leg in route['legs']:
  old=leg['before']['client']['ready']['epoch'];new=leg['after']['client']['ready']['epoch']
  if old==new:continue
  before=[x for x in owned.values()if x['sourceEpoch']==old];after=[x for x in owned.values()if x['sourceEpoch']==new]
  if not before or not after:raise ValueError('Missing transition reports')
  oldReport=max(before,key=lambda x:x['attempt']);newReport=min(after,key=lambda x:x['attempt']);left=oldReport['receivedTime']+offset;right=newReport['receivedTime']-newReport['totalMs']+offset
  if not lo<=left<right<=hi:raise ValueError('Unbounded source report silence')
  gaps.append([left,right]);details.append({'leg':leg['index'],'epochs':[old,new],'window':[left,right],'durationMs':right-left,'lastOld':oldReport,'firstNew':newReport})
 if len(gaps)!=6 or any(gaps[n][0]<gaps[n-1][1]for n in range(1,len(gaps))):raise ValueError('Six disjoint boundary silence windows required')
 out.mkdir(parents=True);(out/'sources').mkdir();groups={};threads=[x for x in p['threads']if x['name']in['GeckoMain','DOM Worker']]
 for label,ranges,invert in [('route',[[lo,hi]],False),('sourceReportSilences',gaps,False),('outsideReportSilences',gaps,True)]:
  selected=[];duration=(hi-lo-sum(b-a for a,b in gaps))if invert else sum(b-a for a,b in ranges)
  for thread in threads:
   copy=dict(thread);samples=dict(thread['samples']);samples['data']=[x for x in samples['data']if lo<=travel.cell(samples,x,'time')<=hi and(any(a<=travel.cell(samples,x,'time')<=b for a,b in ranges)!=invert)];copy['samples']=samples;analysis=travel.analyze_thread(copy,lo,hi);analysis['averageOneCorePercent']=analysis['threadCpuNs']/duration/10000;analysis['selectedDurationMs']=duration;selected.append(analysis)
  groups[label]=selected
 exports={}
 for group in groups.values():
  for analysis in group:
   for item in analysis['deepestJs'][:50]+analysis['inclusiveJs'][:70]+[x for row in analysis['callers'].values()for x in row]:
    label=item['label'];m=re.search(r'\[(\d+)\]$',label)
    if not m or label in exports:continue
    i=int(m.group(1));table=p['sources'];row=table['data'][i];sid=travel.cell(table,row,'id');record=extra['jsSources'].get(sid)
    if not record:exports[label]={'sourceIndex':i,'sourceId':sid,'unavailable':True};continue
    body=record['sourceText'];name=label.split(' ')[0];generated=bool(re.fullmatch('J_[0-9a-fA-F]+',name))
    if generated and not re.search(r'function\s+'+re.escape(name)+r'\s*\(',body):raise ValueError('Current generated body/name mismatch')
    target=out/'sources'/f'{i}.js';target.write_text(body);exports[label]={'sourceIndex':i,'sourceId':sid,'filename':travel.cell(table,row,'filename'),'sha256':hashlib.sha256(body.encode()).hexdigest(),'generatedNameVerified':generated,'path':str(target)}
 result={'accepted':True,'profileVersion':profile['meta']['version'],'sampleUnits':p['meta']['sampleUnits'],'pid':t['pid'],'markers':marks,'offsetMainClockToProfileMs':offset,'routeWindow':[lo,hi],'boundaryWindows':details,'analyses':groups,'sources':exports,'inputSHA256':{n:sha(src/n)for n in files},'limits':['Profiled walking CPU diagnostic, not FPS/GPU/scanout timing','Silence windows are source-report-return gaps, not exact named core entry markers','CPU deltas use sampled endpoints; first delta may straddle a window edge','Outside silence windows includes walking/bridge/transition tails; not pure stationary steady state','Inclusive/deepest/leaf and nested timers overlap; never sum them','Java attribution requires current exported body structural match; source indices never reused from older captures']};(out/'analysis.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({'output':str(out),'durationMs':hi-lo,'gapsMs':[x['durationMs']for x in details],'threads':{name:[{k:a[k]for k in ['name','threadCpuNs','averageOneCorePercent','selectedSamples']}for a in aa]for name,aa in groups.items()}},indent=2))
if __name__=='__main__':main()
