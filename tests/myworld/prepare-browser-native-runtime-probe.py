#!/usr/bin/env python3
"""Compile/freeze the authentic JNI contract probe; never launch a browser."""
from pathlib import Path
from datetime import datetime,timezone
import argparse,hashlib,json,os,re,shutil,subprocess
ROOT=Path(__file__).resolve().parents[2]
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def prepare(run_id):
 if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,79}',run_id):raise ValueError('Invalid run name')
 base=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-native-call-dispatch','--mkdir'],text=True).strip());run=base/'runtime'/run_id;run.mkdir(parents=True,exist_ok=False);web=run/'web';web.mkdir();classes=run/'classes';classes.mkdir();source=run/'source';source.mkdir()
 java=Path(os.environ.get('JAVA_HOME','/home/justin/jdk8u482-b08'))/'bin'
 original=ROOT/'tests/myworld/NativeDispatchProbe.java';shutil.copyfile(original,source/original.name)
 with(run/'compile.log').open('w')as log:
  subprocess.run([str(java/'javac'),'-source','8','-target','8','-d',str(classes),str(source/original.name)],check=True,stdout=log,stderr=subprocess.STDOUT)
  subprocess.run([str(java/'jar'),'cf',str(web/'probe.jar'),'-C',str(classes),'.'],check=True,stdout=log,stderr=subprocess.STDOUT)
 shutil.copyfile(ROOT/'tests/myworld/browser-native-runtime-probe.html',web/'index.html');shutil.copyfile(ROOT/'tests/myworld/browser-native-runtime-probe.js',web/'browser-native-runtime-probe.js');shutil.copyfile(ROOT/'tools/browser-gameplay/web/browser-native-call-dispatch.js',web/'browser-native-call-dispatch.js')
 files={'/'+str(path.relative_to(web)):{'bytes':path.stat().st_size,'sha256':sha(path)}for path in sorted(web.rglob('*'))if path.is_file()}
 meta={'preparedOnly':True,'browserExecuted':False,'preparedUtc':datetime.now(timezone.utc).isoformat(),'sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'sourceDirty':bool(subprocess.check_output(['git','status','--porcelain'],cwd=ROOT,text=True).strip()),'javaSourceSha256':sha(original),'helperSourceSha256':sha(ROOT/'tools/browser-gameplay/web/browser-native-call-dispatch.js'),'probeJarSha256':sha(web/'probe.jar'),'runtimeUrl':'https://cjrtnc.leaningtech.com/5.0/loader.js','javaVersion':8,'webFiles':files,'scope':'Prepared one-JVM scalar/int[]/JavaThrowable/thenable contract; actual CheerpJ unrun; no game/account/GPU/FPS claim','proofExpression':'window.nativeRuntimeProof'}
 (run/'stage.json').write_text(json.dumps(meta,indent=2)+'\n');return run
if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--run-id',default=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ'));args=parser.parse_args();print(json.dumps({'prepared':str(prepare(args.run_id)),'browserExecuted':False,'status':'PREPARED_ONLY'},indent=2))
