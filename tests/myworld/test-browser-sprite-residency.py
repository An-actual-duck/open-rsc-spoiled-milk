#!/usr/bin/env python3
"""Actual Java bulk builder and JS residency/resource contracts, no GPU context."""
from pathlib import Path
import os,subprocess
root=Path(__file__).resolve().parents[2]
out=Path(subprocess.check_output(['python3',str(root/'scripts/workspace-layout.py'),'path','tests','browser-sprite-residency','--mkdir'],text=True).strip())/'classes';out.mkdir(exist_ok=True)
java=Path(os.environ.get('JAVA_HOME','/home/justin/jdk8u482-b08'))/'bin'
jar=root/'Client_Base/Open_RSC_Client.jar'
sources=[root/'PC_Client/src/orsc'/n for n in ['BrowserWorldSpriteBatch.java','BrowserWorldSpriteCache.java']]+[root/'Client_Base/src/com/openrsc/client/model/Sprite.java',root/'tests/myworld/BrowserWorldSpriteBatchFixture.java']
subprocess.run([str(java/'javac'),'-source','8','-target','8','-cp',str(jar),'-d',str(out),*map(str,sources)],check=True)
subprocess.run([str(java/'java'),'-cp',str(out)+os.pathsep+str(jar),'orsc.BrowserWorldSpriteBatchFixture'],check=True)
subprocess.run(['node',str(root/'tests/myworld/test-browser-sprite-atlas.mjs')],check=True)
subprocess.run(['node',str(root/'tests/myworld/test-browser-gpu-cache.mjs')],check=True)
