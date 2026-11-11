#!/usr/bin/env python3
"""Prepare isolated real-CheerpJ ABI/cost stage. Never launch HTTP/browser/process."""
import argparse,hashlib,json,shutil,socket,struct,subprocess,sys
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--source-root',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--port',type=int,default=8211);p.add_argument('--width',type=int,default=67);p.add_argument('--height',type=int,default=43);p.add_argument('--texts-per-clear',type=int,default=1);p.add_argument('--frame-batch',action='store_true');a=p.parse_args();here=Path(__file__).resolve().parent;out=a.output.resolve()
if a.frame_batch and not 1<=a.texts_per_clear<=32:raise SystemExit('Frame batch admits at most32x128 glyphs')
if out.exists():raise SystemExit('Refuse existing stage')
with socket.socket()as s:s.bind(('127.0.0.1',a.port))
# Reuse the reviewed source-bound exact-literal extractor; this compiles only copied helpers.
subprocess.run([sys.executable,str(here.parent/'overlay-command-reference/prepare.py'),'--source-root',str(a.source_root.resolve()),'--output',str(out)],check=True)
java=out/'LiteralOverlayReference.java';source=java.read_text();start=source.index('public static void main(String[]args)');java.write_text(source[:start]+(here/'ProbeMain.java.inc').read_text()+'\n}\n')
corpus=json.loads((out/'corpus.json').read_text());font=bytes(corpus['sources'][0]['data']);(out/'font.bin').write_bytes(struct.pack('>i',len(font))+font)
subprocess.run(['javac','-d',str(out/'classes'),*[str(p)for p in out.glob('*.java')]],check=True)
subprocess.run(['jar','cf',str(out/'probe.jar'),'-C',str(out/'classes'),'.','-C',str(out),'font.bin'],check=True)
shutil.copyfile(out/'probe.jar',out/'browser-spike.jar')
for n in ['index.html','probe.mjs','kernel.mjs']:shutil.copyfile(here/n,out/n)
receipt={'preparedOnly':True,'publicChanged':False,'scope':'Real CheerpJ TEXT ABI/cost probe; no GPU/game/server; root owns launch','sourceRoot':str(a.source_root.resolve()),'probeCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=here,text=True).strip(),'probeDirty':bool(subprocess.check_output(['git','status','--porcelain'],cwd=here,text=True).strip()),'literalProvenance':json.loads((out/'provenance.json').read_text()),'fontName':corpus['sources'][0]['name'],'fontSHA256':hashlib.sha256(font).hexdigest(),'surface':{'width':a.width,'height':a.height,'textsPerClear':a.texts_per_clear,'frameBatch':a.frame_batch,'modeCount':5 if a.frame_batch else 3},'host':'127.0.0.1','port':a.port,'url':f'http://127.0.0.1:{a.port}/','startCommand':['python3',str(here.parents[2]/'scripts/browser-gameplay/serve.py'),str(out),'--port',str(a.port)],'files':{n:hashlib.sha256((out/n).read_bytes()).hexdigest()for n in ['probe.jar','browser-spike.jar','index.html','probe.mjs','kernel.mjs']}}
(out/'stage-manifest.json').write_text(json.dumps(receipt,indent=2)+'\n');print(out/'stage-manifest.json')
