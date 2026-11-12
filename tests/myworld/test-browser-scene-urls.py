#!/usr/bin/env python3
"""Paired private controls preserve unrelated exact-stage flags; no services."""
import importlib.util
import sys
from pathlib import Path
from urllib.parse import parse_qs,urlsplit
sys.dont_write_bytecode=True
root=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('scene_urls',root/'scripts/browser-gameplay/private_scene_urls.py')
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
checks=0
for residency in ['full','bounded']:
 for budget in [256,512]:
  for static in ['on','off']:
   result=module.scene_urls('http://127.0.0.1:8240/?wholezone=on&wholeram=on&benchmark=1&fixture=repeatable-v1',residency,static,budget)
   for key in ['url','urlFullGpu','urlBoundedGpu','urlBounded256','urlBounded512','urlStaticSceneOn','urlStaticSceneOff']:
    q=parse_qs(urlsplit(result[key]).query)
    assert q['wholezone']==['on']and q['wholeram']==['on']and q['benchmark']==['1']and q['fixture']==['repeatable-v1'];checks+=1
   q=lambda key:parse_qs(urlsplit(result[key]).query)
   assert q('urlFullGpu')['gpuresidency']==['full']and'gpubudget'not in q('urlFullGpu');checks+=1
   assert q('urlBounded256')['gpubudget']==['256']and q('urlBounded512')['gpubudget']==['512'];checks+=1
   assert q('urlStaticSceneOn')['staticscene']==['on']and q('urlStaticSceneOff')['staticscene']==['off'];checks+=1
   assert result['sceneControls']['ordinaryDefaultsChanged']is False;checks+=1
for args in [('bad','off',256),('bounded','bad',256),('bounded','on',1024)]:
 try:module.scene_urls('http://127.0.0.1/',*args)
 except ValueError:checks+=1
 else:raise AssertionError('Invalid private stage controls accepted')
print(f'PASS {checks} private scene URL control checks; no staging, Java, browser, GPU or service')
