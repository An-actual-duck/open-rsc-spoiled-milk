#!/usr/bin/env python3
"""Actual structured URL + early dependency refusals; no stage/browser/server."""
from pathlib import Path
import sys,subprocess
from urllib.parse import urlsplit,parse_qsl
ROOT=Path(__file__).resolve().parents[3];sys.path.insert(0,str(ROOT/'scripts/browser-gameplay'))
from private_scene_urls import scene_urls,clone_template_urls,baseline_reuse_urls
base='http://127.0.0.1:8234/?mode=webgl&benchmark=1&wholezone=on&wholeram=on&staticscene=on&geometryformat=compact&baselinereuse=on&clonetemplates=on';urls=clone_template_urls(base);get=lambda u:dict(parse_qsl(urlsplit(u).query));a=get(urls['urlCloneTemplatesOn']);b=get(urls['urlCloneTemplatesOff']);assert a.pop('clonetemplates')=='on'and b.pop('clonetemplates')=='off'and a==b and a['baselinereuse']=='off'
assert clone_template_urls(base.replace('wholeram=on','wholeram=off'))['urlCloneTemplatesOn']is None
assert get(scene_urls(base,'bounded','off',512,'compact',True)['urlStaticSceneOff'])['clonetemplates']=='off'
assert get(baseline_reuse_urls(base)['urlBaselineReuseOn'])['clonetemplates']=='off'
script=ROOT/'scripts/browser-gameplay/prepare-whole-ram-client.py';common=['python3',str(script),'--name','invalid','--http-port','8234','--server-run','unused','--client-jar','unused','--client-receipt','unused','--bake-run','unused','--ram-run','unused','--clone-templates','on']
for args in [common+['--static-scene','off'],common+['--static-scene','on','--baseline-reuse','on']]:
 r=subprocess.run(args,text=True,capture_output=True);assert r.returncode!=0 and'Clone templates require'in r.stderr
print('PASS 6 actual clone-template URL/dependency checks; no stage or process startup')
