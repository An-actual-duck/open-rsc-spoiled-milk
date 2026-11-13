#!/usr/bin/env python3
import importlib.util,tempfile
from pathlib import Path
s=importlib.util.spec_from_file_location('route',Path(__file__).with_name('analyze-native-route.py'));m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
assert m.summary([20_000_000]*99+[100_000_000])['p99Ms']==20
assert m.summary([20_000_000]*99+[100_000_000])['maxMs']==100
with tempfile.TemporaryDirectory() as d:
 p=Path(d)/'events.csv';header='stage,sequence,anchorNanos,durationNanos\n'
 p.write_text(header+'x,3,60,20\nx,2,40,20\nx,4,80,20\n');assert len(m.events(p)['x'])==3
 for body in ['x,2,40,20\nx,2,40,20\n','x,2,40,20\nx,4,80,20\n','x,2,40,-1\n']:
  p.write_text(header+body)
  try:m.events(p)
  except ValueError:pass
  else:raise AssertionError('Invalid events accepted')
print('PASS exact sequence validation, equal-value retention, nearest-rank tails and lost/duplicate/negative refusal')
