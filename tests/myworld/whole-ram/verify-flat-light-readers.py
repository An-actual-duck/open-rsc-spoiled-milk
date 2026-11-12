#!/usr/bin/env python3
"""Conservative source guard for the observable oracle's flat-light exception."""
import argparse,hashlib,json,pathlib,re,subprocess
p=argparse.ArgumentParser();p.add_argument('--source-root',type=pathlib.Path,required=True);p.add_argument('--commit',required=True);a=p.parse_args()
expected={'Client_Base/src/orsc/graphics/three/Scene.java':(4,'5e7a70b3f9e87235090788a0ce3b8bb803b7e0e45dc7f7d703b562bcb9b0a490'),'Client_Base/src/orsc/graphics/three/RSModel.java':(8,'2be51584442ade47738044ca17d7ed9b224ecf1a45c3ffcbaa0f80646903c141')}
whole={'Client_Base/src/orsc/graphics/three/Scene.java':'f24ff1759505c43f7d900a2d451a28be9f1d2b1ec172a36890a850ea1c4358f5','Client_Base/src/orsc/graphics/three/RSModel.java':'22cb8d8f4667b5ec7b1ad3b95c002ebe23cbb8fe086e1169c75acb576e3d7297'}
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
