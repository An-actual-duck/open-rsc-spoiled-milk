#!/usr/bin/env python3
"""CPU comparison against the exact pre-extraction production sky source."""
from pathlib import Path
import hashlib
import subprocess
ROOT=Path(__file__).resolve().parents[2]
BASELINE='958a278453e460d59dcfce6c102adcc42dcfc1d7'
SOURCE_SHA='d37f819178f1da7fd3d0136a461863043a773669f060d5b3ce3ba8fb35683fbd'

def main():
    out=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-sky','--mkdir'],text=True).strip())
    source=subprocess.check_output(['git','show',BASELINE+':PC_Client/src/orsc/OpenGLWorldChunkRenderer.java'],cwd=ROOT)
    assert hashlib.sha256(source).hexdigest()==SOURCE_SHA,'Pre-extraction reference changed'
    text=source.decode();start=text.index('\tprivate void drawWorldSkyClouds(');end=text.index('\tprivate static final class BelowTerrainFloor',start)
    constants=text[text.index('\tprivate static final int SKY_DOME_SEGMENTS'):text.index('\tprivate static final int DEFAULT_SPATIAL_BATCH_TILE_SIZE')]
    entry='''    void emit(float radius,RendererDayNightCycle.Presentation presentation) throws Exception {
        drawWorldSkyDome(radius,presentation);
        float skyBrightness=clampStatic((presentation.skyRed+presentation.skyGreen+presentation.skyBlue)/3.0f,0.0f,1.0f);
        float starVisibility=smoothSkyAmount((0.58f-skyBrightness)/0.40f);
        float cloudVisibility=smoothSkyAmount((skyBrightness-0.20f)/0.42f);
        drawWorldSkyMilkyWay(radius,starVisibility);drawWorldSkyStars(radius,starVisibility);drawWorldSkyClouds(radius,presentation,cloudVisibility);
    }
'''
    reference='package orsc;\nfinal class OriginalSkyReference {\n final SkyStreamRecorder gl=new SkyStreamRecorder();\n'+constants+entry+text[start:end]+'\nprivate static float clampStatic(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}\n}\n'
    path=out/'OriginalSkyReference.java';path.write_text(reference);classes=out/'cpu-classes';classes.mkdir(exist_ok=True)
    jar=ROOT/'Client_Base/Open_RSC_Client.jar'
    subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),str(path),str(ROOT/'tests/myworld/SkyStreamRecorder.java'),str(ROOT/'tests/myworld/BrowserSkyFixture.java')],check=True)
    subprocess.run(['java','-Djava.awt.headless=true','-cp',str(classes)+':'+str(jar),'orsc.BrowserSkyFixture'],cwd=ROOT,check=True)

if __name__=='__main__':main()
