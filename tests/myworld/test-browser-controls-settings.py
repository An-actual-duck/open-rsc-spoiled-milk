#!/usr/bin/env python3
from pathlib import Path
import os
import subprocess
import tempfile
ROOT=Path(__file__).resolve().parents[2]
JAVA=Path(os.environ.get('JAVA_HOME','/home/justin/jdk8u482-b08'))/'bin'
JAR=Path(os.environ.get("SPOILED_MILK_TEST_CLIENT_JAR", ROOT/'Client_Base/Open_RSC_Client.jar'))
with tempfile.TemporaryDirectory(prefix='browser-controls-settings-') as temp:
    sources=[ROOT/'Client_Base/src/orsc/BrowserPresentationSettings.java',ROOT/'Client_Base/src/orsc/RendererSettingsPanel.java',ROOT/'Client_Base/src/orsc/RendererGeometrySettings.java',ROOT/'PC_Client/src/orsc/ORSCApplet.java',ROOT/'PC_Client/src/orsc/BrowserMiddleMouseInput.java',
             ROOT/'PC_Client/src/orsc/DesktopMiddleMouseOrbit.java',ROOT/'tests/myworld/BrowserControlsSettingsFixture.java']
    subprocess.run([str(JAVA/'javac'),'-source','8','-target','8','-cp',str(JAR),'-d',temp,*map(str,sources)],check=True)
    subprocess.run([str(JAVA/'java'),'-Djava.awt.headless=true','-cp',temp+os.pathsep+str(JAR),'orsc.BrowserControlsSettingsFixture'],check=True)
    subprocess.run([str(JAVA/'javac'),'-source','8','-target','8','-cp',temp+os.pathsep+str(JAR),'-d',temp,
                    *map(str,sorted((ROOT/'tools/browser-gameplay/src/orsc/browser').glob('*.java')))],check=True)
subprocess.run(['node',str(ROOT/'tests/myworld/browser-middle-mouse-fixture.mjs')],check=True)
