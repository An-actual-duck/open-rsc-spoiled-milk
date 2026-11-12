#!/usr/bin/env python3
"""Early refusal controls only; no JAR execution, service, browser or GPU."""
import subprocess,sys,tempfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
script=ROOT/'tests/myworld/prepare-browser-compact-hardware.py'
def main():
 head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip();base=['python3',str(script)]
 cases=[(['--name','../escape','--source-commit',head,'--dependency-jar','/NO-JAR'],'Simple name'),(['--name','safe','--source-commit','HEAD','--dependency-jar','/NO-JAR'],'exact source'),(['--name','safe','--source-commit',head,'--dependency-jar','/NO-JAR'],'existing dependency'),(['--name','safe','--source-commit',head,'--dependency-jar','/NO-JAR','--port','80'],'Unprivileged')]
 for args,wanted in cases:
  r=subprocess.run(base+args,cwd=ROOT,text=True,capture_output=True,timeout=10);assert r.returncode!=0 and wanted in r.stderr,(args,r.stderr)
 print('PASS compact hardware preparation: 4 early refusal cases; no Java/browser/GPU/service started')
if __name__=='__main__':main()
