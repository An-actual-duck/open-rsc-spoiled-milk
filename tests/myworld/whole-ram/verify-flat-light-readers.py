#!/usr/bin/env python3
"""Conservative source guard for the observable oracle's flat-light exception."""
import argparse,hashlib,json,pathlib,re,subprocess
p=argparse.ArgumentParser();p.add_argument('--source-root',type=pathlib.Path,required=True);p.add_argument('--commit',required=True);a=p.parse_args()
expected={'Client_Base/src/orsc/graphics/three/Scene.java':(4,'5e7a70b3f9e87235090788a0ce3b8bb803b7e0e45dc7f7d703b562bcb9b0a490'),'Client_Base/src/orsc/graphics/three/RSModel.java':(8,'2be51584442ade47738044ca17d7ed9b224ecf1a45c3ffcbaa0f80646903c141')}
whole={'Client_Base/src/orsc/graphics/three/Scene.java':'b5e487fa633155fac7f4a99f29ca7c050b0137cdf77c4b8887b090ff831780dd','Client_Base/src/orsc/graphics/three/RSModel.java':'4087d583a9609ae96b44253366c5e9754d21f04136a18a49aafbc66a99bc99c9'}
def git(*cmd):return subprocess.check_output(['git','-C',str(a.source_root)]+list(cmd),text=True)
found={}
for name in git('ls-tree','-r','--name-only',a.commit,'Client_Base/src','PC_Client/src').splitlines():
 if not name.endswith('.java'):continue
 text=git('show',a.commit+':'+name);lines=[' '.join(x.split())for x in text.splitlines()if re.search(r'\bvertDiffuseLight\b',x)]
 if lines:
  found[name]=(len(lines),hashlib.sha256('\n'.join(lines).encode()).hexdigest())
  if name in whole and hashlib.sha256(text.encode()).hexdigest()!=whole[name]:raise AssertionError('Audited enclosing consumer/control-flow source changed; review flat-light exception: '+name)
if found!=expected:raise AssertionError('Vertex diffuse consumer/source roster changed; review sentinel-only exception before changing this guard: '+json.dumps(found))
print(json.dumps({'passed':True,'commit':git('rev-parse',a.commit).strip(),'guardedSourceLines':12,'roster':found,'scope':'Scene two render consumers and RSModel object-light consumer are Gouraud-sentinel gated; other references declaration/copy/lighting/codec only. Any maintained additional field occurrence or changed consumer line requires review. No arbitrary plugin/reflection access guarantee.'}))
