#!/usr/bin/env python3
import argparse,subprocess,json,hashlib
from pathlib import Path
from datetime import datetime,timezone
ROOT=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--client-jar',type=Path,required=True);a=p.parse_args()
s=(ROOT/'tools/renderer-benchmark/orsc/RendererBenchmarkClient.java').read_text()
start=s.index('try (NativeRouteProbe routeProbe=');loop=s.index('for (int second = 0; second < seconds; second++)',start);end=s.index('System.out.println(RendererPerformanceDiagnostics.handleCommand("::pf stop',loop)
assert start<loop<end and 'if(routeProbe!=null)routeProbe.close()' not in s[start:end]
base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','native-route-failure','--mkdir'],text=True).strip());out=base/datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ');out.mkdir();classes=out/'classes';classes.mkdir()
sources=[ROOT/'tools/renderer-benchmark/orsc/NativeRouteProbe.java',ROOT/'tools/renderer-benchmark/orsc/RendererBenchmarkClient.java',ROOT/'tests/myworld/NativeRouteFailureFixture.java']
subprocess.run(['javac','-source','8','-target','8','-cp',str(a.client_jar),'-d',str(classes),*map(str,sources)],check=True)
r=subprocess.run(['java','-Djava.awt.headless=true','-cp',str(classes)+':'+str(a.client_jar),'orsc.NativeRouteFailureFixture',str(out)],capture_output=True);(out/'stdout.log').write_bytes(r.stdout);(out/'stderr.log').write_bytes(r.stderr);r.check_returncode()
(out/'receipt.json').write_text(json.dumps({'passed':True,'gpuExecuted':False,'serverStarted':False,'sourceHashes':{str(x.relative_to(ROOT)):hashlib.sha256(x.read_bytes()).hexdigest() for x in sources},'jarSha256':hashlib.sha256(a.client_jar.read_bytes()).hexdigest()},indent=2)+'\n');print(r.stdout.decode(),end='');print(out/'receipt.json')
