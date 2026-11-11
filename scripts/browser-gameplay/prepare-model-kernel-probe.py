#!/usr/bin/env python3
"""Prepare a browser JNI writeback probe; never starts a browser, server or GPU."""
import argparse, hashlib, json, pathlib, shutil, subprocess

ROOT=pathlib.Path(__file__).resolve().parents[2]
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--name',required=True);p.add_argument('--client-jar',type=pathlib.Path,required=True);p.add_argument('--client-source-commit',required=True);p.add_argument('--reference-run',type=pathlib.Path,required=True);p.add_argument('--http-port',type=int,default=8185);a=p.parse_args()
    if not a.name or any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_' for c in a.name):p.error('Use a simple run name')
    if len(a.client_source_commit)!=40 or any(c not in '0123456789abcdef'for c in a.client_source_commit):p.error('Exact client source commit required')
    jar=a.client_jar.resolve();reference=a.reference_run.resolve();r=json.loads((reference/'result.json').read_text())
    if not r['passed'] or sha(reference/'baseline.bin')!=r['referenceSha256']:p.error('Use an intact passed frozen-reference suite')
    frontend_commit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip()
    native_relative='tools/browser-gameplay/web/browser-native-model-kernel.js'
    if subprocess.check_output(['git','show',frontend_commit+':'+native_relative],cwd=ROOT)!=(ROOT/native_relative).read_bytes():p.error('Checkpoint the native module before preparing an immutable probe')
    category=pathlib.Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-native-model-probe','--mkdir'],text=True).strip());run=category/a.name
    if run.exists():p.error('Run exists; preserve old evidence')
    run.mkdir();web=run/'web';web.mkdir();classes=run/'classes';classes.mkdir();shutil.copy2(jar,web/'client.jar');shutil.copy2(ROOT/'tools/browser-gameplay/web/browser-native-model-kernel.js',web/'browser-native-model-kernel.js')
    subprocess.run(['javac','-source','8','-target','8','-cp',str(jar),'-d',str(classes),str(ROOT/'tests/myworld/NativeModelKernelReferenceFixture.java'),str(ROOT/'tests/myworld/NativeModelKernelBrowserProbe.java')],check=True)
    # First64 cases keep browser proof quick while exercising all axis combinations.
    import struct
    data=(reference/'baseline.bin').read_bytes();offset=8;count=min(64,r['cases'])
    for _ in range(count):
        offset+=4+8*4+6*4
        for _ in range(16):
            length=struct.unpack_from('>i',data,offset)[0];offset+=4+4*length
    (classes/'native-model-reference.bin').write_bytes(data[:4]+struct.pack('>i',count)+data[8:offset])
    subprocess.run(['jar','cf',str(web/'browser-spike.jar'),'-C',str(classes),'.'],check=True)
    html='''<!doctype html><meta charset="utf-8"><title>Private model kernel JNI proof</title><pre id="status">Loading local Java writeback proof</pre><script src="https://cjrtnc.leaningtech.com/5.0/loader.js"></script><script type="module">
import {createNativeModelKernel} from './browser-native-model-kernel.js';
const kernel=createNativeModelKernel();window.modelKernelProof={started:true,complete:false,passed:false,work:kernel.stats};
try {await cheerpjInit({version:8,natives:{Java_orsc_graphics_three_BrowserNativeModelKernel_transform:kernel.native,async Java_orsc_graphics_three_NativeModelKernelBrowserProbe_report(lib,passed,cases,values,detail){Object.assign(window.modelKernelProof,{complete:true,passed,cases,values,detail});document.querySelector('#status').textContent=JSON.stringify(window.modelKernelProof,null,2);}}});const exit=await cheerpjRunMain('orsc.graphics.three.NativeModelKernelBrowserProbe','/app/browser-spike.jar:/app/client.jar');window.modelKernelProof.exit=exit;}catch(error){Object.assign(window.modelKernelProof,{complete:true,passed:false,error:String(error)});document.querySelector('#status').textContent=JSON.stringify(window.modelKernelProof,null,2);}
</script>'''
    (web/'index.html').write_text(html)
    receipt={'preparedOnly':True,'processesStarted':False,'scope':'No server/account/GPU: actual Java RSModel native int[] writeback against frozen reference. No FPS claim.','clientSourceCommit':a.client_source_commit,'frontendSourceCommit':frontend_commit,'clientJarSha256':sha(jar),'referenceReceipt':str(reference/'result.json'),'referenceSha256':r['referenceSha256'],'probeCases':count,'probeJarSha256':sha(web/'browser-spike.jar'),'nativeModuleSha256':sha(web/'browser-native-model-kernel.js'),'httpCommand':['python3',str(ROOT/'scripts/browser-gameplay/serve.py'),str(web),'--port',str(a.http_port)],'url':'http://127.0.0.1:'+str(a.http_port)+'/'}
    (run/'preparation.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(receipt,indent=2))
if __name__=='__main__':main()
