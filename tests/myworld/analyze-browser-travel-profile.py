#!/usr/bin/env python3
"""Read-only Firefox travel profile/source attribution. No browser/process control."""
import argparse,collections,hashlib,json,re
from pathlib import Path

def cell(table,row,key,default=None):
 i=table.get('schema',{}).get(key)
 return row[i]if i is not None and i<len(row)else default

def profiles(p):
 yield p
 for c in p.get('processes',[]):yield from profiles(c)

def markers(thread):
 table=thread.get('markers',{});out=[]
 for row in table.get('data',[]):
  data=cell(table,row,'data')or{}
  if isinstance(data,dict)and data.get('name')in ['rsc-travel-start','rsc-travel-end']:out.append({'name':data['name'],'type':data.get('type'),'time':cell(table,row,'startTime')})
 return out

def select(profile,receipt,route):
 if receipt.get('completed')is not True or receipt.get('publicChanged')is not False or route.get('accepted')is not True or route.get('drive')is not True:raise ValueError('Completed actual private pointer travel receipts required')
 if route['route']['account']!='browserdemo':raise ValueError('Private account required')
 if receipt['before']['error']or receipt['after']['error']or receipt['before']['ready']['phase']!='ready'or receipt['after']['ready']['phase']!='ready':raise ValueError('Fresh ready/error gates')
 if receipt['after']['ready']['epoch']<=receipt['before']['ready']['epoch']:raise ValueError('No actual source epoch transition')
 hits=[(p,t,markers(t))for p in profiles(profile)for t in p.get('threads',[])if markers(t)]
 if len(hits)!=1:raise ValueError('Exactly one content thread with paired travel markers required')
 proc,thread,marks=hits[0]
 if thread.get('name')!='GeckoMain'or thread.get('processType')!='tab'or len(marks)!=2 or any(m['type']!='UserTiming'for m in marks):raise ValueError('Actual content travel markers required')
 start=next(m['time']for m in marks if m['name']=='rsc-travel-start');end=next(m['time']for m in marks if m['name']=='rsc-travel-end')
 if not 100<=end-start<=180000:raise ValueError('Bounded travel marker interval required')
 units=proc['meta']['sampleUnits']
 if units.get('time')!='ms'or units.get('threadCPUDelta')!='ns':raise ValueError('Unknown timing units')
 for leg in route['legs']:
  if leg['before']['authority']['level']!=leg['after']['authority']['level']:raise ValueError('Ordinary same signed-layer walk required')
 return proc,thread,marks,start,end

def analyze_thread(thread,start,end):
 strings=thread['stringTable'];stack=thread['stackTable'];frame=thread['frameTable'];samples=thread['samples'];cache={None:()}
 def frames(i):
  if i in cache:return cache[i]
  row=stack['data'][i];f=frame['data'][cell(stack,row,'frame')];label=strings[cell(frame,f,'location')];js=bool(cell(frame,f,'relevantForJS',False))or cell(frame,f,'implementation')is not None or bool(re.search(r'^(?:https?://|[^ ]+ \(https?://)',label))
  cache[i]=frames(cell(stack,row,'prefix'))+((label,js),);return cache[i]
 leaf=collections.Counter();inc=collections.Counter();incJs=collections.Counter();deep=collections.Counter();cpuLeaf=collections.Counter();cpuDeep=collections.Counter();cpuInc=collections.Counter();cpuIncJs=collections.Counter();callers=collections.defaultdict(collections.Counter);n=0;cpu=0;empty=0
 for row in samples.get('data',[]):
  tm=cell(samples,row,'time')
  if tm is None or not start<=tm<=end:continue
  n+=1;delta=cell(samples,row,'threadCPUDelta',0)or 0
  if delta<0:raise ValueError('Negative CPU delta')
  cpu+=delta;fs=frames(cell(samples,row,'stack'));labels=[x[0]for x in fs]
  if not labels:empty+=1;continue
  leaf[labels[-1]]+=1;cpuLeaf[labels[-1]]+=delta
  for label in set(labels):inc[label]+=1;cpuInc[label]+=delta
  js=[x[0]for x in fs if x[1]]
  for label in set(js):incJs[label]+=1;cpuIncJs[label]+=delta
  if js:
   deep[js[-1]]+=1;cpuDeep[js[-1]]+=delta
   if len(js)>1:callers[js[-1]][js[-2]]+=1
 def rank(c,d=None,k=45):return [{'label':x,'samples':v,'sampleSharePercent':100*v/n if n else 0,'cpuNs':d[x]if d is not None else None,'cpuSharePercent':100*d[x]/cpu if d is not None and cpu else None}for x,v in c.most_common(k)]
 return {'name':thread['name'],'pid':thread.get('pid'),'tid':thread.get('tid'),'processType':thread.get('processType'),'selectedSamples':n,'stacklessSamples':empty,'threadCpuNs':cpu,'averageOneCorePercent':cpu/(end-start)/10000,'leaf':rank(leaf,cpuLeaf),'deepestJs':rank(deep,cpuDeep),'inclusive':rank(inc,cpuInc),'inclusiveJs':rank(incJs,cpuIncJs,80),'callers':{x:rank(c,None,8)for x,c in callers.items()if x in dict(deep.most_common(45))}}

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('input',type=Path);p.add_argument('--out',type=Path,required=True);p.add_argument('--stage-receipt',type=Path);p.add_argument('--signature-source-indices',default='');p.add_argument('--vertex-source-index',type=int);p.add_argument('--reference-copy-source-index',type=int);a=p.parse_args();src=a.input.resolve();out=a.out.resolve()
 if out.exists():p.error('Refuse prior evidence overwrite')
 profile=json.loads((src/'sources-profile.json').read_text());extra=json.loads((src/'additional-information.json').read_text());receipt=json.loads((src/'receipt.json').read_text());route=json.loads((src/'route/result.json').read_text());proc,mainThread,marks,start,end=select(profile,receipt,route);provenance=None
 if a.stage_receipt:
  digest=hashlib.sha256(a.stage_receipt.read_bytes()).hexdigest()
  if digest!=route['stageSHA256']:raise ValueError('Frozen stage/preparation receipt does not match actual route hash')
  data=json.loads(a.stage_receipt.read_text());provenance={'path':str(a.stage_receipt.resolve()),'sha256':digest,'data':data}
 out.mkdir(parents=True);(out/'sources').mkdir()
 rows=[json.loads(x)for x in(src/'route/samples.jsonl').read_text().splitlines()];owned={}
 for r in rows:
  for s in r['client'].get('loadProfileSamples')or[]:owned[s['owner'],s['attempt']]=s
 base=rows[0]['client']['loadProfile']['last']['attempt'];oldEpoch=receipt['before']['ready']['epoch'];reports=[s for (_,i),s in sorted(owned.items())if i>base];prior=[s for s in owned.values()if s['sourceEpoch']==oldEpoch];new=[s for s in reports if s['sourceEpoch']>oldEpoch];offset=receipt['before']['origin']-proc['meta']['startTime'];windows={'travel':[start,end]}
 bridge={}
 if prior and new:
  old=max(prior,key=lambda s:s['attempt']);first=min(new,key=lambda s:s['attempt']);lo=old['receivedTime']+offset;hi=first['receivedTime']-first['totalMs']+offset
  if start<=lo<hi<=end:windows['bridgeSilentInterval']=[lo,hi]
  bridge={'lastOldAttempt':old,'firstNewAttempt':first,'clockOffsetMainToProfileMs':offset,'reportsDeduped':len(reports),'reportedAttemptRange':[base+1,max(s['attempt']for s in reports)],'pathCounts':dict(collections.Counter(s['path']for s in reports)),'inclusiveTotalMs':sum(s['totalMs']for s in reports),'nestedPhaseTotalsMs':{k:sum(s[k]for s in reports)for k in ['geometryMs','vertexPackMs','materialPackMs','nativeChunkSubmitMs','activeKeysMs','textureMs','terminalMs']},'scope':'Bridge silent interval is last old source report return to first new attempt estimated start (receivedTime−inclusive Java totalMs). Same main performance clock converted via receipt origin; not exact core entry marker. Nested phase totals overlap and must not be added.'}
 attribution={}
 if a.signature_source_indices or a.vertex_source_index is not None or a.reference_copy_source_index is not None:
  sig=set(int(x)for x in a.signature_source_indices.split(',')if x);target=a.vertex_source_index;copyIndex=a.reference_copy_source_index;thread=mainThread;table=thread['samples'];st=thread['stackTable'];ft=thread['frameTable'];strings=thread['stringTable'];cache={None:()}
  def labels(i):
   if i in cache:return cache[i]
   row=st['data'][i];cache[i]=labels(cell(st,row,'prefix'))+(strings[cell(ft,ft['data'][cell(st,row,'frame')],'location')],);return cache[i]
  for name,(lo,hi)in windows.items():
   total=0;union=0;near=collections.Counter();referenceCopy=collections.Counter()
   for row in table['data']:
    tm=cell(table,row,'time')
    if tm is None or not lo<=tm<=hi:continue
    total+=1;fs=labels(cell(table,row,'stack'));js=[f for f in fs if re.search(r'\[(\d+)\]$',f)]
    ids=[int(re.search(r'\[(\d+)\]$',f).group(1))for f in js]
    if sig.intersection(ids):union+=1
    if target is not None and target in ids:
     i=len(ids)-1-ids[::-1].index(target);near[js[i-1]if i else'<root>']+=1
    if copyIndex is not None and copyIndex in ids:
     i=ids.index(copyIndex);referenceCopy[' => '.join(js[max(0,i-5):i+1])]+=1
   attribution[name]={'selectedSamples':total,'signatureSourceIndices':sorted(sig),'signatureOrMixAnyStackSamples':union,'signatureOrMixAnyStackSharePercent':100*union/total if total else 0,'vertexSourceIndex':target,'vertexNearestCallers':dict(near),'referenceCopySourceIndex':copyIndex,'referenceCopyCallerPaths':dict(referenceCopy),'scope':'Source index sets explicitly provided by analyst AFTER current captured body match; one union count per sample, no additive inclusive costs. Reference-copy index is separately explicit; no opaque index is reused across captures.'}
 selected=[t for t in proc['threads']if t.get('name')in ['GeckoMain','DOM Worker']];analyses={name:[analyze_thread(t,*span)for t in selected]for name,span in windows.items()};exports={}
 for group in analyses.values():
  for t in group:
   for ranked in [t['deepestJs'][:40],t['inclusiveJs'][:50],*[v for v in t['callers'].values()]]:
    for item in ranked:
     label=item['label'];m=re.search(r'\[(\d+)\]$',label)
     if not m or label in exports:continue
     i=int(m.group(1));table=proc['sources'];row=table['data'][i];sid=cell(table,row,'id');record=extra['jsSources'].get(sid)
     if record is None:exports[label]={'sourceIndex':i,'sourceId':sid,'unavailable':True};continue
     body=record.get('sourceText');assert isinstance(body,str);name=label.split(' ')[0];generated=bool(re.fullmatch(r'J_[a-fA-F0-9]+',name))
     if generated:assert re.search(r'function\s+'+re.escape(name)+r'\s*\(',body),label
     dest=out/'sources'/f'{i}.js';dest.write_text(body);exports[label]={'sourceIndex':i,'sourceId':sid,'filename':cell(table,row,'filename'),'generatedFunctionNameVerified':generated,'sha256':hashlib.sha256(body.encode()).hexdigest(),'bytes':len(body.encode()),'path':str(dest)}
 inputs=[src/'sources-profile.json',src/'additional-information.json',src/'receipt.json',src/'route/result.json',src/'route/samples.jsonl'];result={'accepted':True,'input':str(src),'sourceProvenance':provenance,'inputSHA256':{str(f.relative_to(src)):hashlib.sha256(f.read_bytes()).hexdigest()for f in inputs},'profileVersion':profile['meta']['version'],'sampleUnits':proc['meta']['sampleUnits'],'processPid':mainThread.get('pid'),'markers':marks,'windowsMs':windows,'analyses':analyses,'sources':exports,'sourceBoundAttribution':attribution,'bridge':bridge,'routeSummary':route['summary'],'limitations':['Instrumented ordinary walking diagnostic, not steady FPS or GPU time','CPU deltas attributed to sampled endpoint; first selected delta may straddle boundary','Inclusive/deepest/leaf and nested bridge timers overlap; never sum them','Generated function source is exact current captured body; Java method attribution still requires structural match','Bridge silent interval is bounded source-report gap, not an exact named Java core phase','Worker ACK execution and scanout are different; no physical input latency inferred']};(out/'analysis.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps({'output':str(out),'windowsMs':windows,'threads':{k:[{x:t[x]for x in ['name','tid','selectedSamples','threadCpuNs','averageOneCorePercent']}for t in v]for k,v in analyses.items()}},indent=2))
if __name__=='__main__':main()
