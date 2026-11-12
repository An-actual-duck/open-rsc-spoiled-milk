#!/usr/bin/env python3
"""Actual same-artifact controls and dependency refusal; no stage/server/browser starts."""
import sys,subprocess
from pathlib import Path
from urllib.parse import urlsplit,parse_qsl
ROOT=Path(__file__).resolve().parents[3]
sys.path.insert(0,str(ROOT/'scripts/browser-gameplay'))
from private_scene_urls import scene_urls,baseline_reuse_urls
base='http://127.0.0.1:8232/?mode=webgl&benchmark=fixture&wholezone=on&wholeram=on&staticscene=on&geometryformat=compact&baselinereuse=on'
checks=0
urls=baseline_reuse_urls(base)
a=dict(parse_qsl(urlsplit(urls['urlBaselineReuseOn']).query));b=dict(parse_qsl(urlsplit(urls['urlBaselineReuseOff']).query))
assert a.pop('baselinereuse')=='on'and b.pop('baselinereuse')=='off'and a==b;checks+=1
scene=scene_urls(base,'bounded','on',512,'compact',True)
for key in ['urlBounded256','urlBounded512','urlGeometryLegacy','urlGeometryCompact','urlStaticSceneOn']:
 assert dict(parse_qsl(urlsplit(scene[key]).query))['baselinereuse']=='on';checks+=1
assert dict(parse_qsl(urlsplit(scene['urlStaticSceneOff']).query))['baselinereuse']=='off';checks+=1
assert baseline_reuse_urls(base.replace('wholeram=on','wholeram=off'))['urlBaselineReuseOn']is None;checks+=1
script=ROOT/'scripts/browser-gameplay/prepare-whole-ram-client.py'
help=subprocess.run(['python3',str(script),'--help'],capture_output=True,text=True,check=True).stdout
assert '--baseline-reuse' in help and '--geometry-format' in help and '--compact-run'in help;checks+=1
args=['python3',str(script),'--name','invalid','--http-port','8232','--server-run','unused','--client-jar','unused','--client-receipt','unused','--bake-run','unused','--ram-run','unused','--baseline-reuse','on','--static-scene','off']
r=subprocess.run(args,capture_output=True,text=True);assert r.returncode!=0 and 'Baseline reuse requires --static-scene on'in r.stderr;checks+=1
print('PASS',checks,'actual structured stage URL/dependency/compact coexistence checks')
