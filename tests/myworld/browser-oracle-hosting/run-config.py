#!/usr/bin/env python3
import argparse,json,pathlib,shutil,subprocess
p=argparse.ArgumentParser();p.add_argument('--root',type=pathlib.Path,required=True);p.add_argument('--jar',type=pathlib.Path,required=True);p.add_argument('--out',type=pathlib.Path,required=True);a=p.parse_args();root=a.root.resolve();jar=a.jar.resolve();assert not a.out.exists();a.out.mkdir(parents=True);out=a.out.resolve();classes=out/'classes';classes.mkdir()
selection=json.loads(subprocess.check_output(['python3','scripts/installed-map-selection.py',str(root)],cwd=root,text=True));cp=str(jar)+':'+str(root/'server/lib/*')
subprocess.run(['javac','-source','8','-target','8','-cp',cp,'-d',str(classes),str(root/'server/test/com/openrsc/server/net/PublicWebSocketConfigurationTest.java')],check=True)
for mode in ('on','off'):
 case=out/mode;case.mkdir()
 for name in ('connections.conf','myworld-host.conf'):shutil.copyfile(root/'server'/name,case/name)
 flags=['-Dspoiledmilk.publicWebSocketCert=/fixture/cert.pem','-Dspoiledmilk.publicWebSocketKey=/fixture/key.pem']
 if mode=='on':flags+=['-Dspoiledmilk.publicWebSocketOrigin=https://play.rscspoiledmilk.com']
 cmd=['java']+flags+['-cp',str(classes)+':'+cp,'com.openrsc.server.net.PublicWebSocketConfigurationTest',selection['serverPackage'],selection['manifestSha256']]
 r=subprocess.run(cmd,cwd=case,text=True,capture_output=True,timeout=90);(out/(mode+'.log')).write_text(r.stdout+r.stderr);assert r.returncode==0 and 'PASS actual hosted parser'in r.stdout,mode+'failed'
(out/'result.json').write_text(json.dumps(dict(passed=True,cases=2,sourceSelection=selection,scope='Actual hosted parser with explicit installed map path, preserving tracked defaults. No server object, socket or database.'),indent=2)+'\n');print('PASS actual hosted public ON/OFF configuration')
