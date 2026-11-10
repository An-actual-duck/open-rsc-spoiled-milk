#!/usr/bin/env python3
"""CPU fog-demand safety and frozen pre-task lighting source oracle; no browser."""
from pathlib import Path
import argparse, hashlib, json, subprocess
ROOT=Path(__file__).resolve().parents[2]
BASE='9ee9edc16b24f4be4ed362fa6fd7c4091c44fa3b'
def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--java-home',type=Path)
    args=parser.parse_args()
    java=str(args.java_home/'bin/java') if args.java_home else 'java'
    javac=str(args.java_home/'bin/javac') if args.java_home else 'javac'
    suite=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-fog-bounded-preparation','--mkdir'],text=True).strip())
    run=suite/('jdk-'+(args.java_home.name if args.java_home else 'default'));run.mkdir(exist_ok=True)
    classes=run/'classes';classes.mkdir(exist_ok=True)
    old=subprocess.check_output(['git','show',BASE+':PC_Client/src/orsc/BrowserLightingData.java'],cwd=ROOT)
    frozen=run/'FrozenBrowserLightingData.java';frozen.write_bytes(old.replace(b'BrowserLightingData',b'FrozenBrowserLightingData'))
    source_names=['PC_Client/src/orsc/BrowserLightingData.java','PC_Client/src/orsc/BrowserFogDemand.java','PC_Client/src/orsc/BrowserCasterProvenance.java','PC_Client/src/orsc/BrowserWebGLBridge.java','PC_Client/src/orsc/BrowserWorldLoadState.java','tests/myworld/BrowserFogDemandFixture.java','tests/myworld/BrowserFogFrozenLightingFixture.java','tests/myworld/BrowserFogProvenanceFixture.java','tests/myworld/BrowserFogTelemetryFixture.java']
    jar=ROOT/'Client_Base/Open_RSC_Client.jar'
    subprocess.run([javac,'-source','8','-target','8','-cp',str(jar),'-d',str(classes),str(frozen)]+[str(ROOT/n) for n in source_names],check=True)
    logs=[]
    for fixture in ['BrowserFogDemandFixture','BrowserFogFrozenLightingFixture','BrowserFogProvenanceFixture','BrowserFogTelemetryFixture']:
        result=subprocess.run([java,'-Djava.awt.headless=true','-cp',str(classes)+':'+str(jar),'orsc.'+fixture],check=True,text=True,capture_output=True)
        logs.append(result.stdout+result.stderr);print(logs[-1],end='')
    (run/'result.txt').write_text('\n'.join(logs))
    (run/'provenance.json').write_text(json.dumps({'baseline':BASE,'baselineLightingSha256':hashlib.sha256(old).hexdigest(),'jarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'scope':'CPU only, no GPU or FPS claim'},indent=2)+'\n')
if __name__=='__main__':main()
