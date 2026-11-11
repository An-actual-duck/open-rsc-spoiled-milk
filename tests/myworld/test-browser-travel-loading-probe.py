#!/usr/bin/env python3
"""Bounded pure helper proof; no browser, fixture, network or process actions."""
import importlib.util,json,copy,unittest
from pathlib import Path
P=Path(__file__).with_name('browser-travel-loading-probe.py');s=importlib.util.spec_from_file_location('travel',P);m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
class Travel(unittest.TestCase):
 def setUp(self):self.route=json.loads(P.with_name('browser-travel-loading-route.json').read_text())
 def test_valid_adjacent_candidate(self):self.assertEqual(len(m.validate_route(self.route)['legs']),8)
 def invalid(self,fn):
  a=copy.deepcopy(self.route);fn(a)
  with self.assertRaises(ValueError):m.validate_route(a)
 def test_reject_teleport(self):self.invalid(lambda a:a['legs'][0].update(target=[350,550,0]))
 def test_reject_layer(self):self.invalid(lambda a:a['legs'][0].update(target=[111,624,-1]))
 def test_reject_repeat(self):self.invalid(lambda a:a['legs'][0].update(target=a['origin']))
 def test_reject_account(self):self.invalid(lambda a:a.update(account='owner'))
 def test_reject_nonfinite_click(self):self.invalid(lambda a:a['legs'][0].update(click=[float('nan'),1]))
 def test_reject_float_tile(self):self.invalid(lambda a:a['legs'][0].update(target=[111.,624,0]))
 def test_reject_unbounded(self):self.invalid(lambda a:a.update(legs=a['legs']*9))
 def test_reject_ambiguous_transition(self):self.invalid(lambda a:a['legs'][0].update(expectTransition=1))
 def test_hold_is_not_fps(self):
  def row(t,frames,held,uploaded,epoch,ints):return {'hostMs':t,'pollRoundTripMs':10,'client':{'frames':frames,'held':held,'uploadedChunks':uploaded,'uploadedBytes':uploaded*100,'ready':{'epoch':epoch},'recentWorldIntervals':ints}}
  rows=[row(0,50,0,10,1,[16,16]),row(5000,50,40,40,2,[16,16]),row(5500,52,40,42,2,[16,17])];a=m.summarize(rows)
  self.assertEqual(a['worldFramesDelta'],2);self.assertEqual(a['heldDelta'],40);self.assertEqual(a['elapsedHostMs'],5500);self.assertEqual(a['epochs'],[1,2]);self.assertEqual(a['maxObservedRecentCompletedWorldIntervalMs'],17);self.assertNotIn('fps',a)
 def test_no_ring_double_count(self):
  row={'hostMs':0,'pollRoundTripMs':1,'client':{'frames':4,'held':0,'uploadedChunks':2,'uploadedBytes':200,'ready':{'epoch':2},'recentWorldIntervals':[50,50]}};a=m.summarize([row,row]);self.assertEqual(a['maxObservedRecentCompletedWorldIntervalMs'],50);self.assertNotIn('intervalCount',a)
 def test_load_ring_loss_is_explicit(self):
  def row(n):return {'hostMs':n,'pollRoundTripMs':1,'client':{'frames':n,'held':0,'uploadedChunks':0,'uploadedBytes':0,'ready':{'epoch':1},'loadProfile':{'totalReports':n},'loadProfileSamples':[1]*32}}
  a=m.summarize([row(1),row(100)]);self.assertEqual(a['loadReportDelta'],99);self.assertEqual(a['loadReportPollGaps'],1)
 def live(self):return {'route':None,'requestedFixture':'repeatable-v1','transportConnections':[{'url':'ws://127.0.0.1:43549/','readyState':1,'closed':False}]}
 def test_benchmark_absent_route_uses_actual_socket(self):self.assertTrue(m.validate_live_route(self.live()))
 def test_stage_loopback_pair(self):self.assertEqual(m.validate_stage_route({'fixture':'repeatable-v1','tcp':'127.0.0.1:43649','websocket':'127.0.0.1:43549'}),'ws://127.0.0.1:43549/')
 def test_wrong_reported_route_still_rejected(self):
  a=self.live();a['route']=43639
  with self.assertRaises(ValueError):m.validate_live_route(a)
 def test_missing_socket_is_not_fake_proof(self):
  a=self.live();a['transportConnections']=[]
  with self.assertRaises(ValueError):m.validate_live_route(a)
 def test_wrong_open_socket(self):
  a=self.live();a['transportConnections'][0]['url']='ws://127.0.0.1:43539/'
  with self.assertRaises(ValueError):m.validate_live_route(a)
 def test_closed_socket(self):
  a=self.live();a['transportConnections'][0]['closed']=True
  with self.assertRaises(ValueError):m.validate_live_route(a)
 def test_socket_must_be_open(self):
  a=self.live();a['transportConnections'][0]['readyState']=0
  with self.assertRaises(ValueError):m.validate_live_route(a)
 def test_combat_fixture_cannot_masquerade_as_route(self):
  a=self.live();a['requestedFixture']='renderer-combat-v1'
  with self.assertRaises(ValueError):m.validate_live_route(a)
 def test_script_is_passive(self):
  self.assertNotIn('.push=',m.SNAPSHOT);self.assertNotIn('postMessage(',m.SNAPSHOT);self.assertNotIn('measurement=',m.SNAPSHOT)
if __name__=='__main__':unittest.main()
