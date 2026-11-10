#!/usr/bin/env python3
"""Frozen actual cache byte/ID/hash/budget/LRU oracle plus BigInteger limb proof."""
from pathlib import Path
import argparse,hashlib,json,subprocess
ROOT=Path(__file__).resolve().parents[2]
BASE='5e77ca2f8a05d068c72ad208bdf17678bbf25f1a'
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--java-home',type=Path,default=Path('/home/justin/jdk8u482-b08'));a=p.parse_args();java=a.java_home/'bin'
 out=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-sprite-key-limbs','--mkdir'],text=True).strip())/a.java_home.name;out.mkdir(exist_ok=True)
 ref=out/'reference-src/orsc/BrowserWorldSpriteCache.java';ref.parent.mkdir(parents=True,exist_ok=True);ref.write_bytes(subprocess.check_output(['git','show',BASE+':PC_Client/src/orsc/BrowserWorldSpriteCache.java'],cwd=ROOT))
 jar=ROOT/'Client_Base/Open_RSC_Client.jar';pc=ROOT/'PC_Client/src/orsc';shared=[pc/n for n in ['OpenGLSpriteTextureBuilder.java','OpenGLPixelWorkspace.java','CompositeWorldSpriteTexture.java','DynamicTextureData.java','OpenGLSpriteAlpha.java']]+[ROOT/'tests/myworld/BrowserSpriteKeyLimbsFixture.java']
 outputs=[]
 for name,cache in [('reference',ref),('candidate',pc/'BrowserWorldSpriteCache.java')]:
  classes=out/name;classes.mkdir(exist_ok=True);subprocess.run([str(java/'javac'),'-source','8','-target','8','-cp',str(jar),'-d',str(classes),str(cache),*map(str,shared)],check=True)
  for policy in (['false']if name=='reference'else['false','true','default']):
   dest=out/(name+'-'+policy+'.bin');properties=[]if policy=='default'else['-Dspoiledmilk.browserSpriteKeyLimbs='+policy];subprocess.run([str(java/'java'),'-Xmx512m','-Djava.awt.headless=true',*properties,'-Dtest.expectNoProperty='+str(policy=='default').lower(),'-Dtest.expectLimbs='+str(name=='candidate'and policy!='false').lower(),'-cp',str(classes)+':'+str(jar),'orsc.BrowserSpriteKeyLimbsFixture',str(dest)],check=True);outputs.append(dest)
 data=outputs[0].read_bytes();assert all(p.read_bytes()==data for p in outputs[1:]),'Frozen cache bytes/metadata/hash/IDs/stats/LRU changed'
 report={'base':BASE,'javaHome':str(a.java_home),'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest(),'candidateOffOnDefaultExact':True,'gpuExecuted':False,'dependencyJarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'candidateSourceSha256':hashlib.sha256((pc/'BrowserWorldSpriteCache.java').read_bytes()).hexdigest(),'fixtureSha256':hashlib.sha256((ROOT/'tests/myworld/BrowserSpriteKeyLimbsFixture.java').read_bytes()).hexdigest()};(out/'result.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
if __name__=='__main__':main()
