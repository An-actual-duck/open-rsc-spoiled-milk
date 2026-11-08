#!/usr/bin/env python3
"""Disposable ordinary-login/AWT input proof on unique loopback ports; never public runtime."""
import argparse, hashlib, json, os, re, shutil, socket, sqlite3, subprocess, sys, time, zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
MARKER='MOVEMENT-INPUT-PROOF.json'

def sha(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def cp(server,classes):
    first=['log4j-api-2.17.0.jar','log4j-core-2.17.0.jar','log4j-iostreams-2.17.0.jar','xpp3-1.1.4c.jar','mysql-connector-j-9.4.0.jar','commons-lang-2.6.jar']
    return os.pathsep.join(map(str,[*(server/'lib'/n for n in first),server/'core-gameplay-overlay.jar',server/'core.jar',server/'plugins.jar',server/'lib/*',classes]))
def prepare(a):
    if not re.fullmatch(r'[A-Za-z0-9_-]+',a.name): raise ValueError('Simple new run name required')
    clientjar=a.client.resolve(strict=True);core=a.core.resolve(strict=True)
    with zipfile.ZipFile(clientjar) as archive:
        if b'spoiledmilk.openglHiddenDiagnostic' not in archive.read('orsc/OpenGLWindowController.class'):raise ValueError('Hidden diagnostic support required')
    base=Path(subprocess.check_output([sys.executable,str(ROOT/'scripts/workspace-layout.py'),'path','tests','idle-walk-native-input','--mkdir'],text=True).strip())
    run=base/a.name;run.mkdir(exist_ok=False);server=run/'server';server.mkdir();client=run/'client';client.mkdir();classes=run/'classes';classes.mkdir()
    selection=json.loads(subprocess.check_output([sys.executable,str(ROOT/'scripts/installed-map-selection.py'),str(ROOT)],text=True))
    for d in ['conf','database']:shutil.copytree(ROOT/'server'/d,server/d)
    for n in ['badwords.txt','goodwords.txt','globalrules.txt','alertwords.txt']:shutil.copy2(ROOT/'server'/n,server/n)
    package=server/'package';shutil.copytree(selection['serverPackage'],package)
    config=(ROOT/'server/myworld.conf').read_text()
    values={'db_name':'movement_walk_dev','server_name':'Private Movement Input Proof','server_bind_address':'127.0.0.1','server_port':'43649','ws_server_port':'43549','npc_capacity':'16384','client_version':'10059','want_feature_websockets':'true','want_discord_bot':'false','want_discord_general_logging':'false','want_discord_auction_updates':'false','want_discord_item_showcase':'false','want_discord_monitoring_updates':'false','want_auto_server_shutdown':'false','layered_native_terrain_package_path':str(package),'layered_native_terrain_manifest_sha256':selection['manifestSha256'],'layered_native_world_runtime_profile':'world-builder-installed'}
    for k,v in values.items():
        config,n=re.subn(r'(?m)^([ \t]*)'+k+r':.*$',lambda m:m[1]+k+': '+v,config)
        if not n:
            if k=='npc_capacity':config=config.replace('world:\n','world:\n\tnpc_capacity: '+v+'\n',1)
            else:config+='\n'+k+': '+v+'\n'
    (server/'myworld.conf').write_text(config)
    subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-keyout',str(server/'private-key.pem'),'-out',str(server/'private-cert.pem'),'-days','7','-subj','/CN=localhost'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
    (server/'private-key.pem').chmod(0o600)
    (server/'connections.conf').write_text('db_type: sqlite\nssl:\n\tssl_server_cert_path: '+str(server/'private-cert.pem')+'\n\tssl_server_key_path: '+str(server/'private-key.pem')+'\n')
    seed=ROOT/'server/inc/sqlite/myworld_seed.db'
    with sqlite3.connect('file:'+str(seed)+'?mode=ro',uri=True) as db:
        if db.execute('SELECT COUNT(*) FROM players').fetchone()[0]!=0 or db.execute('PRAGMA integrity_check').fetchone()[0]!='ok':raise ValueError('Intact account-free seed required')
    (server/'inc/sqlite').mkdir(parents=True);shutil.copy2(seed,server/'inc/sqlite/movement_walk_dev.db');(server/'inc/sqlite/movement_walk_dev.db').chmod(0o600)
    artifacts=[]
    for source in [*(ROOT/'server'/n for n in ['core.jar','plugins.jar','core-gameplay-overlay.jar']),*sorted((ROOT/'server/lib').glob('*.jar'))]:
        relative=source.relative_to(ROOT/'server');destination=server/relative;destination.parent.mkdir(exist_ok=True)
        shutil.copy2(core if relative.as_posix()=='core.jar' else source,destination);artifacts.append({'path':str(relative),'sha256':sha(destination)})
    shutil.copy2(clientjar,client/'client.jar');cache=client/'Cache';cache.mkdir()
    for n in ['audio','video']:shutil.copytree(ROOT/'Client_Base/Cache'/n,cache/n)
    (cache/'config.txt').write_bytes(subprocess.check_output(['git','show','HEAD:Client_Base/Cache/config.txt'],cwd=ROOT))
    for n,v in {'ip.txt':'127.0.0.1\n','port.txt':'43649\n','credentials.txt':'\n','discord_inuse.txt':'1\n'}.items():(cache/n).write_text(v)
    (cache/'uid.dat').write_bytes(bytes(8))
    profile=ROOT/'Client_Base/world-builder-configs/installed-client.json';relative=Path(json.loads(profile.read_text())['packageRelativePath'])
    if relative.is_absolute() or '..' in relative.parts:raise ValueError('Unsafe client package path')
    (client/'world-builder-configs').mkdir();shutil.copy2(profile,client/'world-builder-configs/installed-client.json');(client/relative).mkdir(parents=True);shutil.copy2(ROOT/'Client_Base'/relative/'manifest.json',client/relative/'manifest.json')
    sources=run/'sources';sources.mkdir()
    for source in [ROOT/'tools/movement-responsiveness/MovementInputProofServer.java',ROOT/'tools/movement-responsiveness/orsc/MovementInputProofClient.java']:shutil.copy2(source,sources/source.name)
    subprocess.run(['javac','-source','8','-target','8','-cp',cp(server,classes),'-d',str(classes),str(sources/'MovementInputProofServer.java')],check=True)
    subprocess.run(['javac','-source','8','-target','8','-cp',str(client/'client.jar'),'-d',str(classes),str(sources/'MovementInputProofClient.java')],check=True)
    marker={'schema':1,'private':True,'source':a.source,'mode':a.mode,'tcp':43649,'websocket':43549,'clientSha256':sha(client/'client.jar'),'seedAccounts':0,'seedSha256':sha(seed),'mapManifest':selection['manifestSha256'],'artifacts':artifacts,'sources':{p.name:sha(p) for p in sources.iterdir()},'classes':{str(p.relative_to(classes)):sha(p) for p in classes.rglob('*.class')},'prepareJavaVersion':subprocess.check_output(['java','-version'],stderr=subprocess.STDOUT,text=True),'javacExecutable':str(Path(shutil.which('javac')).resolve()),'javacVersion':subprocess.check_output(['javac','-version'],stderr=subprocess.STDOUT,text=True),'configSha256':sha(server/'myworld.conf')}
    (run/MARKER).write_text(json.dumps(marker,indent=2)+'\n');print(run)

def execute(a):
    run=a.run.resolve(strict=True);mark=json.loads((run/MARKER).read_text());server=run/'server';client=run/'client';classes=run/'classes'
    if not a.java.is_absolute():raise ValueError('--java must name an absolute executable')
    java=a.java.resolve(strict=True)
    if not java.is_file() or not os.access(java,os.X_OK):raise ValueError('Executable JVM required')
    version=subprocess.check_output([str(java),'-version'],stderr=subprocess.STDOUT,text=True)
    if mark.get('tcp')!=43649 or mark.get('websocket')!=43549 or not mark.get('private'):raise ValueError('Private marked fixture only')
    if (run/'processes.json').exists():raise ValueError('Fresh prepared run required; never restart stale processes')
    for artifact in mark['artifacts']:
        if sha(server/artifact['path'])!=artifact['sha256']:raise ValueError('Server artifact changed')
    if sha(client/'client.jar')!=mark['clientSha256']:raise ValueError('Client artifact changed')
    for port in [43649,43549]:
        with socket.socket() as s:s.bind(('127.0.0.1',port))
    command=[str(java),'-Xmx2g','-XX:+UseSerialGC','-cp',cp(server,classes),'MovementInputProofServer']
    proc=None;client_proc=None;receipt={'runnerSha256':sha(Path(__file__)),'serverCommand':command,'state':'before-launch','clientExit':None,'javaRequested':str(a.java),'javaExecutable':str(java),'javaVersion':version,'compileJavacExecutable':mark.get('javacExecutable'),'compileJavacVersion':mark.get('javacVersion')}
    def save():(run/'execution.json').write_text(json.dumps(receipt,indent=2)+'\n')
    save()
    try:
        with (run/'server.log').open('w') as log:
            proc=subprocess.Popen(command,cwd=server,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
        (run/'processes.json').write_text(json.dumps({'server':proc.pid})+'\n');receipt['serverPid']=proc.pid;save()
        deadline=time.monotonic()+90
        while time.monotonic()<deadline and not (run/'status.json').exists():
            if proc.poll() is not None:raise RuntimeError('Private server exited before readiness')
            time.sleep(.2)
        if not (run/'status.json').exists():raise RuntimeError('Private server readiness timeout; startup cause unclassified')
        diagnostic=run/'diagnostics';diagnostic.mkdir()
        properties={'spoiledmilk.openglHiddenDiagnostic':'true','spoiledmilk.openglHiddenWidth':'1920','spoiledmilk.openglHiddenHeight':'1080','spoiledmilk.rendererDiagnostics':'true','spoiledmilk.rendererTelemetry':'true','spoiledmilk.rendererDiagnosticSessionDir':str(diagnostic),'spoiledmilk.clientLog':str(run/'client-runtime.log'),'spoiledmilk.openglRendererProfile':'remaster','spoiledmilk.shadowQuality':'high','spoiledmilk.openglVsync':'false','spoiledmilk.openglFrameCapture':'true','spoiledmilk.openglFrameCaptureDir':str(run/'captures'),'movement.proof.samples':str(a.samples)}
        common=Path(subprocess.check_output(['git','rev-parse','--path-format=absolute','--git-common-dir'],cwd=ROOT,text=True).strip());lock=common/'ai-workspaces/renderer-gpu-benchmark.lock'
        argv=['flock','-w','10','-F',str(lock),str(java),'-Xms512m','-Xmx2g',*['-D'+k+'='+v for k,v in properties.items()],'-cp',str(classes)+os.pathsep+str(client/'client.jar'),'orsc.MovementInputProofClient']
        receipt.update(state='client-launch',clientCommand=argv);save()
        with (run/'client.log').open('w') as log:
            client_proc=subprocess.Popen(argv,cwd=client,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
            receipt['clientPid']=client_proc.pid;save()
            (run/'processes.json').write_text(json.dumps({'server':proc.pid,'client':client_proc.pid})+'\n')
            client_exit=client_proc.wait(timeout=240)
        receipt.update(state='client-finished',clientExit=client_exit)
        if client_exit or not (run/'CLIENT-DONE').exists():raise RuntimeError('Client proof failed; logs retained')
        settled=[json.loads(line) for line in (run/'settled-samples.jsonl').read_text().splitlines()]
        if len(settled)!=a.samples:raise RuntimeError('Incomplete settled-destination evidence')
        for row in settled:
            expected=(row['expectedWorldX'],row['expectedWorldY'])
            if (row['actualClientWorldX'],row['actualClientWorldY'])!=expected or (row['serverTickSnapshot']['x'],row['serverTickSnapshot']['y'])!=expected:
                raise RuntimeError('Client/server destination disagrees with ordinary clicked ground target')
        captures=[json.loads(line) for line in (diagnostic/'captures/capture-index.jsonl').read_text().splitlines()]
        completed=[row for row in captures if row.get('status')=='completed' and not row.get('failed')]
        if len(completed)<2 or not list((run/'captures').rglob('*.png')):raise RuntimeError('Missing completed native capture evidence')
        receipt['completedCaptureRecords']=len(completed)
    except subprocess.TimeoutExpired:
        receipt.update(state='client-timeout')
        snapshot={'monotonicNanos':time.monotonic_ns(),'clientPid':None if client_proc is None else client_proc.pid,'serverPid':None if proc is None else proc.pid}
        if (run/'status.json').exists():snapshot['statusBeforeStop']=json.loads((run/'status.json').read_text())
        (run/'timeout-before-stop.json').write_text(json.dumps(snapshot,indent=2)+'\n')
        raise
    finally:
        save()
        if client_proc is not None and client_proc.poll() is None:
            client_proc.terminate()
            try:client_proc.wait(timeout=10)
            except subprocess.TimeoutExpired:client_proc.kill();client_proc.wait()
            receipt['privateClientTerminationFallback']=True;save()
        if proc is not None and proc.poll() is None:
            (run/'STOP').write_text('Private disposable fixture shutdown requested\n')
            try:proc.wait(timeout=20)
            except subprocess.TimeoutExpired:
                receipt['privateServerTerminationFallback']=True;proc.terminate()
                try:proc.wait(timeout=10)
                except subprocess.TimeoutExpired:proc.kill();proc.wait()
            receipt['serverExit']=proc.returncode;save()
    print('Completed ordinary AWT/native proof:',run)

p=argparse.ArgumentParser(description=__doc__);sub=p.add_subparsers(dest='command',required=True)
a=sub.add_parser('prepare');a.add_argument('--name',required=True);a.add_argument('--client',type=Path,required=True);a.add_argument('--core',type=Path,required=True);a.add_argument('--source',required=True);a.add_argument('--mode',choices=['baseline','candidate'],required=True)
a=sub.add_parser('run');a.add_argument('--run',type=Path,required=True);a.add_argument('--samples',type=int,default=12);a.add_argument('--java',type=Path,required=True)
a=p.parse_args();prepare(a) if a.command=='prepare' else execute(a)
