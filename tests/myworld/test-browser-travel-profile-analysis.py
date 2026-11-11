#!/usr/bin/env python3
"""Synthetic schema/units/window/union guards only; no real browser claims."""
import copy,importlib.util,unittest
from pathlib import Path
p=Path(__file__).with_name('analyze-browser-travel-profile.py');s=importlib.util.spec_from_file_location('analysis',p);m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
class Analysis(unittest.TestCase):
 def setup(self):
  t={'name':'GeckoMain','processType':'tab','pid':2,'markers':{'schema':{'data':0,'startTime':1},'data':[[{'name':'rsc-travel-start','type':'UserTiming'},100],[{'name':'rsc-travel-end','type':'UserTiming'},500]]}}
  profile={'meta':{'sampleUnits':{'time':'ms','threadCPUDelta':'ns'}},'threads':[t]};r={'completed':True,'publicChanged':False,'before':{'error':None,'ready':{'phase':'ready','epoch':2},'pose':[1]},'after':{'error':None,'ready':{'phase':'ready','epoch':3},'pose':[2]}};route={'accepted':True,'drive':True,'route':{'account':'browserdemo'},'legs':[{'before':{'authority':{'level':0}},'after':{'authority':{'level':0}}}]};return profile,r,route
 def test_travel_moving_pose_and_short_window_is_valid(self):
  x=m.select(*self.setup());self.assertEqual(x[-2:],(100,500))
 def test_no_fixed_20second_assumption(self):
  p,r,s=self.setup();p['threads'][0]['markers']['data'][1][1]=200;self.assertEqual(m.select(p,r,s)[-1],200)
 def test_units_fail_closed(self):
  p,r,s=self.setup();p['meta']['sampleUnits']['threadCPUDelta']='ms'
  with self.assertRaises(ValueError):m.select(p,r,s)
 def test_actual_epoch_required(self):
  p,r,s=self.setup();r['after']['ready']['epoch']=2
  with self.assertRaises(ValueError):m.select(p,r,s)
 def test_no_failed_route(self):
  p,r,s=self.setup();s['accepted']=False
  with self.assertRaises(ValueError):m.select(p,r,s)
 def test_main_content_marker_required(self):
  p,r,s=self.setup();p['threads'][0]['processType']='default'
  with self.assertRaises(ValueError):m.select(p,r,s)
 def test_inclusive_dedup_and_cpu_ns(self):
  t={'name':'GeckoMain','pid':1,'tid':1,'processType':'tab','stringTable':['root','Hot (http://127.0.0.1/a.js:1)[7]','Leaf'],'stackTable':{'schema':{'prefix':0,'frame':1},'data':[[None,0],[0,1],[1,1],[2,2]]},'frameTable':{'schema':{'location':0,'relevantForJS':1},'data':[[0,False],[1,True],[2,False]]},'samples':{'schema':{'time':0,'stack':1,'threadCPUDelta':2},'data':[[1,3,1000000],[2,3,1000000]]}}
  a=m.analyze_thread(t,0,4);self.assertEqual(a['threadCpuNs'],2000000);self.assertEqual(a['averageOneCorePercent'],50);hot=next(x for x in a['inclusiveJs']if x['label'].startswith('Hot'));self.assertEqual(hot['samples'],2);self.assertEqual(a['deepestJs'][0]['samples'],2)
 def test_worker_native_url_is_not_javascript_source(self):
  t={'name':'DOM Worker','stringTable':['WorkerThreadPrimaryRunnable::Run http://127.0.0.1/worker.js'],'stackTable':{'schema':{'prefix':0,'frame':1},'data':[[None,0]]},'frameTable':{'schema':{'location':0},'data':[[0]]},'samples':{'schema':{'time':0,'stack':1,'threadCPUDelta':2},'data':[[1,0,1000]]}}
  self.assertEqual(m.analyze_thread(t,0,2)['deepestJs'],[])
if __name__=='__main__':unittest.main()
