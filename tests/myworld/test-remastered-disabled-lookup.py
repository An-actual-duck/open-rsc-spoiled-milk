#!/usr/bin/env python3
"""Actual resolver differential, including isolated availability-enabled tests; production pause unchanged."""
from pathlib import Path
import os,subprocess
ROOT=Path(__file__).resolve().parents[2]
BASE='ee876dbe895d914a9df78088cec2ca35362ffc04'
suite=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','remastered-disabled-lookup','--mkdir'],text=True).strip())
jar=ROOT/'Client_Base/Open_RSC_Client.jar';java=Path(os.environ.get('JAVA_HOME','/home/justin/jdk8u482-b08'))/'bin'
resolver=ROOT/'Client_Base/src/orsc/remastered/RemasteredSpriteResolver.java';settings=ROOT/'Client_Base/src/orsc/remastered/RemasteredSpriteSettings.java'
old=subprocess.check_output(['git','show',BASE+':Client_Base/src/orsc/remastered/RemasteredSpriteResolver.java'],cwd=ROOT,text=True).replace('RemasteredSpriteResolver','FrozenRemasteredSpriteResolver')
for available in [False,True]:
    run=suite/('isolated-enabled'if available else'production-paused');src=run/'source/orsc/remastered';src.mkdir(parents=True,exist_ok=True);classes=run/'classes';classes.mkdir(exist_ok=True)
    frozen=src/'FrozenRemasteredSpriteResolver.java';frozen.write_text(old)
    sources=[resolver,frozen,ROOT/'tests/myworld/RemasteredDisabledLookupFixture.java']
    if available:
        # Test-only classpath copy deliberately exposes preserved enabled implementation.
        override=src/'RemasteredSpriteSettings.java';override.write_text(settings.read_text().replace('AVAILABLE = false','AVAILABLE = true'));sources.append(override)
    subprocess.run([str(java/'javac'),'-source','8','-target','8','-cp',str(jar),'-d',str(classes),*map(str,sources)],check=True)
    env=dict(os.environ);env.pop('SPOILED_MILK_REMASTERED_SPRITES',None)
    subprocess.run([str(java/'java'),'-Djava.awt.headless=true','-cp',str(classes)+os.pathsep+str(jar),'orsc.remastered.RemasteredDisabledLookupFixture',str(available).lower()],cwd=ROOT,env=env,check=True)
