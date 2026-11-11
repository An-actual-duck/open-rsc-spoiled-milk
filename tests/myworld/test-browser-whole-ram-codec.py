#!/usr/bin/env python3
"""Source-bound actual WRP1 decoder failures and successful partial roundtrip."""
import argparse,hashlib,json,subprocess,sys,time
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def sha(p):return hashlib.sha256(Path(p).read_bytes()).hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--client-jar',type=Path,required=True);p.add_argument('--build-receipt',type=Path,required=True);p.add_argument('--prepared',type=Path,required=True,help='Bounded three-centre pilot prepared.bin (<=16 MiB), not a full cook');a=p.parse_args();assert 1<=a.prepared.stat().st_size<=16*1024*1024,'Use the bounded three-centre pilot for malformed copies';receipt=json.loads(a.build_receipt.read_text());assert receipt['buildSucceeded']and receipt['singleProductionCommit']and receipt['jarSha256']==sha(a.client_jar)
 suite=Path(subprocess.check_output([sys.executable,str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-whole-ram-codec','--mkdir'],text=True).strip());out=suite/time.strftime('%Y%m%dT%H%M%SZ',time.gmtime());out.mkdir(exist_ok=False);source=ROOT/'tests/myworld/fixtures/BrowserWholeRamCodecFixture.java';commands=[['javac','-cp',str(a.client_jar),'-d',str(out),str(source)],['java','-Xmx512m','-XX:+UseSerialGC','-cp',str(out)+':'+str(a.client_jar),'BrowserWholeRamCodecFixture',str(a.prepared)]]
 result={'clientJarSha256':sha(a.client_jar),'clientSourceCommit':receipt['clientSourceCommit'],'buildReceiptSha256':sha(a.build_receipt),'fixtureSha256':sha(source),'preparedSha256':sha(a.prepared),'commands':commands,'gameBrowserGpuServerStarted':False,'passed':False};started=time.monotonic()
 try:
  with(out/'run.log').open('w')as log:
   for command in commands:subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,timeout=90,check=True)
  result['passed']=True
 finally:
  result['elapsedSeconds']=time.monotonic()-started;result['logSha256']=sha(out/'run.log');(out/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(out/'result.json')
 return 0
if __name__=='__main__':raise SystemExit(main())
