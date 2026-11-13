#!/usr/bin/env python3
import importlib.util
from pathlib import Path
p=Path(__file__).with_name('browser-frame-sequence.py');spec=importlib.util.spec_from_file_location('seq',p);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
def steady(rows):return [dict(r,held=0,ready={'epoch':1}) for r in rows]
r=m.summarize(steady([{'sequence':2,'tail':[10,10]},{'sequence':4,'tail':[10,10,10,50]},{'sequence':4,'tail':[10,10,10,50]},{'sequence':6,'tail':[10,50,50,100]}]));assert r['count']==3 and r['p50Ms']==50 and r['maxMs']==100 and r['firstSequence']==4
for rows in [[{'sequence':0,'tail':[]},{'sequence':5,'tail':[1]}],[{'sequence':3,'tail':[1]},{'sequence':2,'tail':[1]}],[{'sequence':0,'tail':[]},{'sequence':1,'tail':[float('nan')]}]]:
 try:m.summarize(steady(rows))
 except ValueError:pass
 else:raise AssertionError('Malformed/lost events accepted')
print('PASS exact event-ID deduplication, repeated equal values, overlapping rings, reset/loss/nonfinite rejection; no held-frame or FPS inference')

for rows in [[{'sequence':0,'tail':[]},{'sequence':1,'tail':[20]}], [{'sequence':0,'tail':[],'held':0,'ready':{'epoch':1}},{'sequence':1,'tail':[200],'held':1,'ready':{'epoch':1}}], [{'sequence':0,'tail':[],'held':0,'ready':{'epoch':1}},{'sequence':1,'tail':[200],'held':0,'ready':{'epoch':2}}]]:
 try:m.summarize(rows)
 except ValueError:pass
 else:raise AssertionError('Missing or nonsteady context accepted')
print('PASS explicit context requirement and hold/epoch rejection without deleting crossing gaps')

r=m.summarize(steady([{'sequence':8,'tail':[20]}, {'sequence':11,'tail':[9000,20,20]}]));assert r['count']==2 and r['maxMs']==20 and r['firstSequence']==10
print('PASS first resumed interval excluded even when prior hold counters are unchanged')
