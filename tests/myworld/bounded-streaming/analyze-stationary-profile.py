#!/usr/bin/env python3
"""Read-only stationary CPU attribution with exact captured-source exports."""
import argparse,hashlib,importlib.util,json,re
from pathlib import Path
spec=importlib.util.spec_from_file_location('travel',Path(__file__).parents[1]/'analyze-browser-travel-profile.py');travel=importlib.util.module_from_spec(spec);spec.loader.exec_module(travel)
p=argparse.ArgumentParser(description=__doc__);p.add_argument('input',type=Path);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
assert not a.out.exists(),'Preserve previous evidence'
profile=json.loads((a.input/'firefox-profile.json').read_text());extra=json.loads((a.input/'additional-information.json').read_text());receipt=json.loads((a.input/'receipt.json').read_text())
for n,h in receipt['files'].items():assert sha(a.input/n)==h,n+' receipt mismatch'
hits=[]
for proc in travel.profiles(profile):
 for t in proc['threads']:
  marks=[]
  for row in t['markers']['data']:
   v=travel.cell(t['markers'],row,'data')or{}
   if v.get('name')in ('rsc-bounded-cpu-stationary-start','rsc-bounded-cpu-stationary-end'):marks.append({'name':v['name'],'type':v['type'],'time':travel.cell(t['markers'],row,'startTime')})
  if marks:hits.append((proc,t,marks))
assert len(hits)==1
proc,t,marks=hits[0];assert t['name']=='GeckoMain'and t.get('processType')=='tab'and len(marks)==2 and all(x['type']=='UserTiming'for x in marks)
lo=next(x['time']for x in marks if x['name'].endswith('start'));hi=next(x['time']for x in marks if x['name'].endswith('end'));assert 100<hi-lo<180000
assert proc['meta']['sampleUnits']['threadCPUDelta']=='ns'and proc['meta']['sampleUnits']['time']=='ms'
analyses=[travel.analyze_thread(x,lo,hi)for x in proc['threads']if x['name']in ('GeckoMain','DOM Worker')]
a.out.mkdir(parents=True);(a.out/'sources').mkdir();exports={}
for analysis in analyses:
 for item in analysis['deepestJs']+analysis['inclusiveJs']+[x for v in analysis['callers'].values()for x in v]:
  label=item['label'];m=re.search(r'\[(\d+)\]$',label)
  if not m or label in exports:continue
  i=int(m.group(1));row=proc['sources']['data'][i];sid=travel.cell(proc['sources'],row,'id');record=extra['jsSources'].get(sid)
  if record is None:exports[label]={'sourceIndex':i,'sourceId':sid,'unavailable':True};continue
  body=record['sourceText'];name=label.split(' ')[0];generated=bool(re.fullmatch(r'J_[a-fA-F0-9]+',name))
  if generated:assert re.search(r'function\s+'+re.escape(name)+r'\s*\(',body),label
  dest=a.out/'sources'/f'{i}.js';dest.write_text(body);exports[label]={'sourceIndex':i,'sourceId':sid,'filename':travel.cell(proc['sources'],row,'filename'),'sha256':hashlib.sha256(body.encode()).hexdigest(),'generatedNameVerified':generated,'path':str(dest)}
result={'passed':True,'windowMs':[lo,hi],'durationMs':hi-lo,'markers':marks,'inputSHA256':{n:sha(a.input/n)for n in ('firefox-profile.json','additional-information.json','profile-start.json','profile-end.json','receipt.json')},'sourceReceipt':receipt['sourceReceipt'],'analyses':analyses,'sources':exports,'limitations':['Stationary busy-NPC CPU sample diagnostic; no FPS/GPU/physical scanout acceptance.','CPU delta belongs to sampled endpoint and first delta can straddle marker.','Inclusive, deepest and leaf rankings overlap; never add them.','Source bodies are exact current capture. Java method mapping requires explicit structural match, not stale generated labels.']}
(a.out/'analysis.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({'output':str(a.out),'durationMs':hi-lo,'threads':[{k:x[k]for k in ('name','tid','selectedSamples','threadCpuNs','averageOneCorePercent')}for x in analyses]},indent=2))
