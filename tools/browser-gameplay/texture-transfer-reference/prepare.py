#!/usr/bin/env python3
"""Freeze a loopback-only diagnostic stage. Does not start any process."""
import argparse,hashlib,json,socket,shutil,subprocess
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--writer-capture',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--port',type=int,default=8208);a=p.parse_args()
with socket.socket()as s:s.bind(('127.0.0.1',a.port))
if a.output.exists():raise SystemExit('Refuse existing stage')
raw=a.writer_capture.read_bytes();capture=json.loads(raw);assert capture['accepted']and capture['publicChanged']is False
layouts=[]
for l in capture['layouts']:
 rects=l['coarse'];assert 0<len(rects)<=128
 layouts.append(dict(frameSequence=l['frameSequence'],rectangles=rects,writerArea=l['writerArea'],coarseArea=l['coarseArea']))
assert 1<=len(layouts)<=32
source=Path(__file__).resolve().parent;manifest=dict(scope='Standalone owned synthetic arrays; captured writer rectangles only, no gameplay pixels',sourceCommit=subprocess.check_output(['git','rev-parse','HEAD'],cwd=source,text=True).strip(),sourceDirty=bool(subprocess.check_output(['git','status','--porcelain'],cwd=source,text=True).strip()),writerCaptureSHA256=hashlib.sha256(raw).hexdigest(),writerCapturePath=str(a.writer_capture.resolve()),width=960,height=540,scheme='captured64x32coarsening',layouts=layouts)
a.output.mkdir(parents=True)
for n in ['index.html','harness.mjs','transfer.mjs']:shutil.copyfile(source/n,a.output/n)
(a.output/'layouts.json').write_text(json.dumps(manifest,indent=2)+'\n')
receipt=dict(port=a.port,host='127.0.0.1',url=f'http://127.0.0.1:{a.port}/',preparedOnly=True,publicChanged=False,source=manifest,files={n:hashlib.sha256((a.output/n).read_bytes()).hexdigest()for n in ['index.html','harness.mjs','transfer.mjs','layouts.json']},startCommand=['python3','-m','http.server',str(a.port),'--bind','127.0.0.1','--directory',str(a.output.resolve())])
(a.output/'stage-manifest.json').write_text(json.dumps(receipt,indent=2)+'\n');print(a.output/'stage-manifest.json')
