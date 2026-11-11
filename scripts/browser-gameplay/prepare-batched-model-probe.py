#!/usr/bin/env python3
"""Prepare only an actual bounded-model JNI probe; no browser/game/server/GPU."""
import argparse,hashlib,json,pathlib,shutil,struct,subprocess
ROOT=pathlib.Path(__file__).resolve().parents[2]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--name',required=True);p.add_argument('--client-jar',required=True,type=pathlib.Path);p.add_argument('--client-source-commit',required=True);p.add_argument('--reference-run',required=True,type=pathlib.Path);p.add_argument('--http-port',type=int,default=8191);a=p.parse_args()
    if not a.name or any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_'for c in a.name):p.error('Simple run name required')
    if not 1<=a.http_port<=65535:p.error('Invalid port')
    if len(a.client_source_commit)!=40 or any(c not in '0123456789abcdef'for c in a.client_source_commit):p.error('Exact client source commit required')
    jar=a.client_jar.resolve();reference=a.reference_run.resolve();verified=json.loads((reference/'result.json').read_text())
    if not verified['passed'] or sha(reference/'reference-batch.bin')!=verified['references']['batch']:p.error('An intact passed frozen-reference suite is required')
    relative='tools/browser-gameplay/web/browser-batched-model-kernel.js';commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    if subprocess.check_output(['git','show',commit+':'+relative],cwd=ROOT)!=(ROOT/relative).read_bytes():p.error('Checkpoint native module before staging')
    category=pathlib.Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-batched-model-probe','--mkdir'],text=True).strip());run=category/a.name
    if run.exists():p.error('Run exists; preserve old evidence')
    run.mkdir();web=run/'web';web.mkdir();classes=run/'classes';classes.mkdir();shutil.copy2(jar,web/'client.jar');shutil.copy2(ROOT/relative,web/'browser-batched-model-kernel.js')
    subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),str(ROOT/'tests/myworld/NativeModelKernelReferenceFixture.java'),str(ROOT/'tests/myworld/BrowserBatchedModelKernelProbe.java')],check=True)
    data=(reference/'reference-batch.bin').read_bytes();offset=8;total=min(384,struct.unpack_from('>i',data,4)[0])
    if total<256:p.error('Reference needs at least256 uniform-camera models')
    for model in range(total):
        offset+=4+8*4+6*4
        for n in range(16):length=struct.unpack_from('>i',data,offset)[0];offset+=4+4*length
    (classes/'batch-reference.bin').write_bytes(data[:4]+struct.pack('>i',total)+data[8:offset]);subprocess.run(['jar','cf',str(web/'browser-spike.jar'),'-C',str(classes),'.'],check=True)
    html='''<!doctype html><meta charset="utf-8"><title>Private batched model JNI proof</title><pre id="status">Loading bounded batch proof</pre><script src="https://cjrtnc.leaningtech.com/5.0/loader.js"></script><script type="module">
import {createBatchedModelKernel} from './browser-batched-model-kernel.js';const kernel=createBatchedModelKernel();window.batchedModelProof={started:true,complete:false,passed:false,work:kernel.stats};
try{await cheerpjInit({version:8,natives:{Java_orsc_graphics_three_BrowserBatchedModelKernel_transformPacket:kernel.native,Java_orsc_graphics_three_BrowserBatchedModelKernelProbe_report(lib,passed,models,values,javaWork,detail){Object.assign(window.batchedModelProof,{complete:true,passed,models,values,javaWork:Array.from(javaWork),detail});document.querySelector('#status').textContent=JSON.stringify(window.batchedModelProof,null,2);}}});window.batchedModelProof.exit=await cheerpjRunMain('orsc.graphics.three.BrowserBatchedModelKernelProbe','/app/browser-spike.jar:/app/client.jar');}catch(error){Object.assign(window.batchedModelProof,{complete:true,passed:false,error:String(error)});document.querySelector('#status').textContent=JSON.stringify(window.batchedModelProof,null,2);}
</script>''';(web/'index.html').write_text(html)
    receipt={'preparedOnly':True,'processesStarted':False,'scope':'Actual Java bounded producer -> nested arrays -> JS batch -> Java array writeback, no game/GPU/FPS claim','clientSourceCommit':a.client_source_commit,'clientJarSha256':sha(jar),'frontendSourceCommit':commit,'moduleSha256':sha(web/'browser-batched-model-kernel.js'),'probeJarSha256':sha(web/'browser-spike.jar'),'referenceSha256':sha(reference/'reference-batch.bin'),'probeModels':total,'httpCommand':['python3',str(ROOT/'scripts/browser-gameplay/serve.py'),str(web),'--port',str(a.http_port)],'url':'http://127.0.0.1:'+str(a.http_port)+'/'}
    (run/'preparation.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(receipt,indent=2))
if __name__=='__main__':main()
