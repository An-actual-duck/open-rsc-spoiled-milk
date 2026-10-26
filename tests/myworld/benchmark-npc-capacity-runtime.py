#!/usr/bin/env python3
from pathlib import Path
import subprocess
import tempfile
import argparse
ROOT=Path(__file__).resolve().parents[2]
p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);p.add_argument('--baseline-registry',action='store_true');p.add_argument('--populations',nargs='+',type=int,default=[4000,8000,16384]);p.add_argument('--workloads',nargs='+',default=['idle','active']);args=p.parse_args()
args.output.mkdir(parents=True,exist_ok=True)
with tempfile.TemporaryDirectory(prefix='npc-runtime-') as directory:
 cp=str(ROOT/'server/core.jar')
 sources=[ROOT/'server/test/com/openrsc/server/combat/MutableGameClock.java',ROOT/'tests/myworld/NpcCapacityRuntimeBenchmark.java']
 subprocess.run(['javac','-cp',cp,'-d',directory,*map(str,sources)],check=True)
 overlay=''
 if args.baseline_registry:
  baseline=Path(directory)/'baseline';baseline.mkdir()
  source=baseline/'EntityList.java'
  source.write_text(subprocess.check_output(['git','show','96cdabf534a123b31db9c763d7a079d3ae812361:server/src/com/openrsc/server/util/EntityList.java'],cwd=ROOT,text=True))
  subprocess.run(['javac','-cp',cp,'-d',str(baseline),str(source)],check=True)
  overlay=str(baseline)+':'
 print('REGISTRY_IMPLEMENTATION '+('starting-commit' if args.baseline_registry else 'current'),flush=True)
 for n in args.populations:
  for workload in args.workloads:
   log=args.output/f'{n}-{workload}.log'
   with log.open('w') as stream:
    result=subprocess.run(['java','-Xms512m','-Xmx1024m','-cp',overlay+directory+':'+cp,'com.openrsc.server.combat.NpcCapacityRuntimeBenchmark',str(n),workload],cwd=ROOT/'server',stdout=stream,stderr=subprocess.STDOUT,timeout=120)
   lines=log.read_text().splitlines()
   for line in lines:
    if any(marker in line for marker in ['RUNTIME population=','VISIBILITY local_npc_references=','HEAP snapshot_used_bytes=','activeCombatInvariant=','PASS listener-free']): print(line,flush=True)
   if result.returncode:
    print('\n'.join(lines[-30:]));raise SystemExit(result.returncode)
