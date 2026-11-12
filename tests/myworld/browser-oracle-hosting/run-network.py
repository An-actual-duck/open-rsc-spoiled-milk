#!/usr/bin/env python3
"""CPU-only production direct-WSS/TLS renewal tests; certificates stay in ignored output."""
import argparse,hashlib,json,pathlib,subprocess
p=argparse.ArgumentParser();p.add_argument('--root',type=pathlib.Path,required=True);p.add_argument('--out',type=pathlib.Path,required=True);p.add_argument('--jar',type=pathlib.Path);a=p.parse_args();root=a.root.resolve();jar=(a.jar or root/'server/core.jar').resolve()
assert not a.out.exists(),'Preserve previous evidence';a.out.mkdir(parents=True);out=a.out.resolve();commands=[];passed=[]
def run(name,cmd):
 commands.append(cmd);r=subprocess.run(cmd,cwd=root,text=True,capture_output=True,timeout=90);(out/(name+'.log')).write_text(r.stdout+r.stderr);assert r.returncode==0,name+' failed';
 if name in ('on','off'):assert 'PASS checks='in r.stdout,name+' missing completion marker'
 passed.append(name)
try:
 for mode in ('on','off'):
  data=out/mode;data.mkdir()
  for label,host in [('one','play.rscspoiledmilk.com'),('two','play.rscspoiledmilk.com'),('wrong','wrong.example')]:
   run(mode+'-'+label,['openssl','req','-x509','-newkey','rsa:2048','-nodes','-days','2','-subj','/CN='+host,'-addext','subjectAltName=DNS:'+host,'-keyout',str(data/(label+'-key.pem')),'-out',str(data/(label+'-cert.pem'))])
  run(mode+'-ec',['openssl','req','-x509','-newkey','ec','-pkeyopt','ec_paramgen_curve:P-256','-nodes','-days','2','-subj','/CN=play.rscspoiledmilk.com','-addext','subjectAltName=DNS:play.rscspoiledmilk.com','-keyout',str(data/'ec-key.pem'),'-out',str(data/'ec-cert.pem')])
 classes=out/'classes';classes.mkdir();cp=str(jar)+':'+str(root/'server/lib/*')
 fixture=root/'server/test/com/openrsc/server/net/PublicWebSocketTest.java'
 run('compile',['javac','-source','8','-target','8','-cp',cp,'-d',str(classes),str(fixture)])
 for mode in ('on','off'):
  flags=['-Dspoiledmilk.publicWebSocketOrigin=https://play.rscspoiledmilk.com']if mode=='on'else[]
  run(mode,['java','-Djava.awt.headless=true']+flags+['-cp',str(classes)+':'+cp,'com.openrsc.server.net.PublicWebSocketTest',str(out/mode)])
finally:
 (out/'result.json').write_text(json.dumps(dict(passed=len(passed)==11,passedCases=passed,commands=commands,serverJarSha256=hashlib.sha256(jar.read_bytes()).hexdigest(),limits='Embedded production handlers and real TLS engines only; no sockets/listeners/database/browser/public-host changes. Ephemeral test private keys in this ignored run directory are not deployment credentials.'),indent=2)+'\n')
print(json.dumps(dict(passed=True,output=str(out))))
