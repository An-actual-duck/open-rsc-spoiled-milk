#!/usr/bin/env python3
"""Prepare only a minimal Java nested-array JNI gate; no game or browser starts."""
import argparse, hashlib, json, pathlib, shutil, subprocess
ROOT=pathlib.Path(__file__).resolve().parents[2]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--name',required=True);p.add_argument('--http-port',type=int,default=8190);a=p.parse_args()
    if not a.name or any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_'for c in a.name):p.error('Use a simple run name')
    if not 1<=a.http_port<=65535:p.error('Invalid HTTP port')
    category=pathlib.Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-nested-array-probe','--mkdir'],text=True).strip());run=category/a.name
    if run.exists():p.error('Run already exists; preserve earlier evidence')
    run.mkdir();web=run/'web';web.mkdir();classes=run/'classes';classes.mkdir()
    java=ROOT/'tests/myworld/BrowserNestedArrayProbe.java';module=ROOT/'tests/myworld/browser-nested-array-probe.js'
    subprocess.run(['javac','-source','8','-target','8','-d',str(classes),str(java)],check=True)
    subprocess.run(['jar','cf',str(web/'browser-spike.jar'),'-C',str(classes),'.'],check=True)
    shutil.copy2(module,web/module.name)
    html='''<!doctype html><meta charset="utf-8"><title>Private nested array JNI gate</title><pre id="status">Loading nested-array proof</pre><script src="https://cjrtnc.leaningtech.com/5.0/loader.js"></script><script type="module">
import {createNestedArrayProbe} from './browser-nested-array-probe.js';
const probe=createNestedArrayProbe();window.nestedArrayProof={started:true,complete:false,passed:false,work:probe.stats};
try {await cheerpjInit({version:8,natives:{Java_orsc_browser_probe_BrowserNestedArrayProbe_inspect(lib,rows,stage){return probe.inspect(rows,stage);},Java_orsc_browser_probe_BrowserNestedArrayProbe_report(lib,passed,checks,detail){Object.assign(window.nestedArrayProof,{complete:true,passed,checks,detail});document.querySelector('#status').textContent=JSON.stringify(window.nestedArrayProof,null,2);}}});const exit=await cheerpjRunMain('orsc.browser.probe.BrowserNestedArrayProbe','/app/browser-spike.jar');window.nestedArrayProof.exit=exit;}catch(error){Object.assign(window.nestedArrayProof,{complete:true,passed:false,error:String(error)});document.querySelector('#status').textContent=JSON.stringify(window.nestedArrayProof,null,2);}
</script>''';(web/'index.html').write_text(html)
    receipt={'preparedOnly':True,'processesStarted':False,'scope':'Actual nested Java array JNI gate only; no game/account/GPU/server. No performance claim.','sourceCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'sourceFiles':{str(x.relative_to(ROOT)):sha(x)for x in [java,module,pathlib.Path(__file__)]},'probeJarSha256':sha(web/'browser-spike.jar'),'moduleSha256':sha(web/module.name),'htmlSha256':sha(web/'index.html'),'httpCommand':['python3',str(ROOT/'scripts/browser-gameplay/serve.py'),str(web),'--port',str(a.http_port)],'url':'http://127.0.0.1:'+str(a.http_port)+'/'}
    (run/'preparation.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(receipt,indent=2))
if __name__=='__main__':main()
