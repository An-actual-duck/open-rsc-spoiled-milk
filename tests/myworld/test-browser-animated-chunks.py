#!/usr/bin/env python3
"""Actual browser bridge chunk-cache policy through a recording native boundary."""
from pathlib import Path
import subprocess,argparse,hashlib,json
root=Path(__file__).resolve().parents[2]
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--client-jar',type=Path,default=root/'Client_Base/Open_RSC_Client.jar',help='Explicit dependency artifact; defaults to the existing local client JAR')
args=parser.parse_args()
jar=args.client_jar.resolve()
if not jar.is_file(): parser.error('Client dependency JAR does not exist: '+str(jar))
suite=Path(subprocess.check_output(['python3',str(root/'scripts/workspace-layout.py'),'path','tests','browser-animated-chunks','--mkdir'],text=True).strip())
classes=suite/'classes';classes.mkdir(exist_ok=True)
(suite/'dependency.json').write_text(json.dumps({'clientJar':str(jar),'sha256':hashlib.sha256(jar.read_bytes()).hexdigest()},indent=2)+'\n')
subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),str(root/'PC_Client/src/orsc/BrowserFrameSinkRegistry.java'),str(root/'PC_Client/src/orsc/BrowserWorldLoadState.java'),str(root/'PC_Client/src/orsc/BrowserLoadProfile.java'),str(root/'PC_Client/src/orsc/BrowserWebGLBridge.java'),str(root/'tests/myworld/BrowserAnimatedChunkFixture.java')],check=True)
for policy in ['lru','active']:
 for reuse in [None,'true','false']:
  subprocess.run(['java','-Xmx512m','-Djava.awt.headless=true','-Dspoiledmilk.browserCachePolicy='+policy]+([] if reuse is None else ['-Dspoiledmilk.browserAnimatedBufferReuse='+reuse])+['-cp',str(classes)+':'+str(jar),'orsc.BrowserAnimatedChunkFixture'],check=True)
