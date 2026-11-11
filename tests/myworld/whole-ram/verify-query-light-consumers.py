#!/usr/bin/env python3
"""Closed source proof gate for carrier-seeded CPU query TERRAIN light only."""
import argparse,hashlib,json,pathlib,re,subprocess
p=argparse.ArgumentParser();p.add_argument('--source-root',type=pathlib.Path,required=True);p.add_argument('--commit',required=True);a=p.parse_args();expected=json.loads(pathlib.Path(__file__).with_name('query-light-consumers.json').read_text())
def git(*args):return subprocess.check_output(['git','-C',str(a.source_root)]+list(args),text=True)
def sha(s):return hashlib.sha256(s.encode()).hexdigest()
for name,digest in expected['wholeSource'].items():
 if sha(git('show',a.commit+':'+name))!=digest:raise AssertionError('Query light ownership/consumer source changed; review exception: '+name)
world=git('show',a.commit+':Client_Base/src/orsc/graphics/three/World.java');start=world.index('\tprivate TerrainVertexInput[] collectTerrainVertexInputs(');end=world.index('\n\tprivate ',start+10)
if sha(world[start:end])!=expected['carrierSeedMethodSha256']:raise AssertionError('Original carrier light seeding changed')
calls={}
for name in git('ls-tree','-r','--name-only',a.commit,'Client_Base/src','PC_Client/src').splitlines():
 if not name.endswith('.java'):continue
 text=git('show',a.commit+':'+name);n=len(re.findall(r'\.getVertexLight\s*\(',text))
 if n:calls[name]=n
if calls!={'PC_Client/src/orsc/OpenGLWorldChunkRenderer.java':1,'Client_Base/src/orsc/mudclient.java':3}:raise AssertionError('New maintained query light consumer: '+json.dumps(calls))
print(json.dumps({'passed':True,'sourceCommit':git('rev-parse',a.commit).strip(),'guardedSources':len(expected['wholeSource'])+1,'getVertexLightCalls':calls,'scope':'Only GLOBAL L0 prepared WORLD query TERRAIN light words under nonzero aligned carrier basis may differ. Actual active whole-zone filter strips all WORLD plane0..2 before visual packing. Other reads are resident-object parity/fingerprint diagnostics. Terrain relief/roof/indoor geometry consumes coords/heights/kinds/roof bits, not light. Local wall/roof effective rendering stays exact; no normal-renderer light parity exception outside this private mode. Closed maintained source contract, not arbitrary reflection/plugins.'}))
