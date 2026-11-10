#!/usr/bin/env python3
"""Prepare ONLY an immutable production-shader dispatch pixel corpus.

An explicit approved dependency JAR is required. This command never starts a
browser, GPU context, HTTP service, Java game or account; root runs the emitted
explicit hardware command separately in its exclusive window.
"""
from pathlib import Path
from datetime import datetime,timezone
import argparse,hashlib,importlib.util,json,re,shutil,subprocess
ROOT=Path(__file__).resolve().parents[2]
BASE='325bf42b60fe411705f9f14c0a3ef6debabe24a7'
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def prepare(run_id,jar):
 if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,79}',run_id):raise ValueError('Invalid plain run name')
 jar=jar.resolve(strict=True)
 spec=importlib.util.spec_from_file_location('dispatch_generator',ROOT/'scripts/browser-gameplay/native_dispatch.py');generator=importlib.util.module_from_spec(spec);spec.loader.exec_module(generator)
 base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-native-call-dispatch','--mkdir'],text=True).strip())
 run=base/'hardware'/run_id;run.mkdir(parents=True,exist_ok=False);web=run/'web';web.mkdir();source=run/'source';source.mkdir();classes=run/'classes';classes.mkdir()
 for file in (ROOT/'tools/browser-gameplay/web').glob('*.js'):shutil.copyfile(file,web/file.name)
 canonical=(web/'webgl.js').read_text();(web/'webgl-sync.js').write_text(generator.synchronous_module(canonical))
 original=web/'original';original.mkdir()
 for file in subprocess.check_output(['git','ls-tree','--name-only',BASE+':tools/browser-gameplay/web'],cwd=ROOT,text=True).splitlines():
  if file.endswith('.js'):(original/file).write_bytes(subprocess.check_output(['git','show',BASE+':tools/browser-gameplay/web/'+file],cwd=ROOT))
 shutil.copyfile(ROOT/'tests/myworld/browser-owned-scenario.mjs',web/'browser-owned-scenario.mjs')
 shutil.copyfile(ROOT/'tests/myworld/browser-native-call-hardware.html',web/'index.html');shutil.copyfile(ROOT/'tests/myworld/browser-native-call-hardware.js',web/'browser-native-call-hardware.js')
 paths=[ROOT/'PC_Client/src/orsc/OpenGLShaderProgram.java',ROOT/'PC_Client/src/orsc/OpenGLGeometryShadows.java',ROOT/'tests/myworld/BrowserColorShaderOracle.java',ROOT/'tests/myworld/BrowserOwnedSkyOracle.java']
 for file in paths:shutil.copyfile(file,source/file.name)
 with(run/'shader-oracle.log').open('w')as log:
  subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),*[str(source/path.name)for path in paths]],check=True,stdout=log,stderr=subprocess.STDOUT)
  result=subprocess.check_output(['java','-Djava.awt.headless=true','-cp',str(classes)+':'+str(jar),'orsc.BrowserColorShaderOracle',str(web/'shaders')],text=True).strip();log.write(result+'\n')
 with(run/'sky-oracle.log').open('w')as log:subprocess.run(['java','-Djava.awt.headless=true','-cp',str(classes)+':'+str(jar),'orsc.BrowserOwnedSkyOracle',str(web/'sky')],check=True,stdout=log,stderr=subprocess.STDOUT)
 length,digest=result.split(':');assert int(length)==len((web/'shaders/world.vert').read_text())and digest==sha(web/'shaders/world.vert')
 # Freeze the existing isolated driver, altering only this suite's explicit
 # path/proof inventory. The source and resulting driver hashes are recorded.
 driver_source=ROOT/'tests/myworld/run-browser-owned-gl-hardware.mjs';driver=driver_source.read_text()
 changes={"fileURLToPath(new URL('../../output/tests/browser-owned-gl-state/hardware/',import.meta.url))":json.dumps(str(base/'hardware')),
  'window.ownedStateProof':'window.nativeCallProof','proof.transitions!==42':"proof.nativeModes?.join(',')!=='async,async,sync'",
  'owned GL state':'native-call dispatch','old/off/on/live':'original/async/sync'}
 for old,new in changes.items():
  if old not in driver:raise ValueError('Hardware driver contract changed: '+old)
  driver=driver.replace(old,new)
 (run/'run-hardware.mjs').write_text(driver)
 files={'/'+str(path.relative_to(web)):{'sha256':sha(path),'bytes':path.stat().st_size}for path in sorted(web.rglob('*'))if path.is_file()}
 meta={'preparedOnly':True,'gpuExecuted':False,'preparedUtc':datetime.now(timezone.utc).isoformat(),'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'sourceDirty':bool(subprocess.check_output(['git','status','--porcelain'],cwd=ROOT,text=True).strip()),'baselineCommit':BASE,'moduleSha256':sha(web/'webgl.js'),'syncModuleSha256':sha(web/'webgl-sync.js'),'originalSha256':sha(original/'webgl.js'),'generatorSha256':sha(ROOT/'scripts/browser-gameplay/native_dispatch.py'),'productionVertexSha256':digest,'productionSourceSha256':{path.name:sha(path)for path in paths},'dependencyJar':str(jar),'dependencyJarSha256':sha(jar),'driverAuthoritySha256':sha(driver_source),'driverSha256':sha(run/'run-hardware.mjs'),'webFiles':files,'scope':'Prepared actual shader/native sky async-vs-sync dispatch pixel differential; GPU and CheerpJ unrun, no FPS claim','explicitRunCommand':['node',str(run/'run-hardware.mjs'),'--run-hardware',str(run)]}
 (run/'stage.json').write_text(json.dumps(meta,indent=2)+'\n');return run
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--run-id',default=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'));parser.add_argument('--dependency-jar',type=Path,required=True);args=parser.parse_args();print(json.dumps({'prepared':str(prepare(args.run_id,args.dependency_jar)),'gpuExecuted':False,'status':'PREPARED_ONLY'},indent=2))
