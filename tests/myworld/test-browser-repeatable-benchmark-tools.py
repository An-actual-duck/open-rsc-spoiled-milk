#!/usr/bin/env python3
"""CPU-only private benchmark tools checks. Never starts a browser, GPU or server."""
from pathlib import Path
import argparse,hashlib,importlib.util,json,sqlite3,subprocess
ROOT=Path(__file__).resolve().parents[2]
def run(command,log):
    result=subprocess.run(command,cwd=ROOT,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
    log.write(result.stdout);print(result.stdout,end='');result.check_returncode()
def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--java-home',type=Path);parser.add_argument('--client-jar',type=Path,default=ROOT/'Client_Base/Open_RSC_Client.jar');parser.add_argument('--require-candidates',action='store_true');parser.add_argument('--require-projection',action='store_true');parser.add_argument('--native-source',type=Path);parser.add_argument('--native-source-commit');args=parser.parse_args()
    if bool(args.native_source)!=bool(args.native_source_commit):parser.error('Use native review source and exact commit together')
    java=str(args.java_home/'bin/java')if args.java_home else 'java';javac=str(args.java_home/'bin/javac')if args.java_home else 'javac';jar=args.client_jar.resolve()
    category=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-repeatable-benchmark-tools','--mkdir'],text=True).strip());suite=category/('jdk-'+(args.java_home.name if args.java_home else 'default'));suite.mkdir(exist_ok=True);classes=suite/'classes';classes.mkdir(exist_ok=True)
    source=(ROOT/'tools/browser-gameplay/web/browser.js').read_text();begin=source.index('function alignWorldSurface(width,height){');depth=0;end=begin
    for n in range(source.index('{',begin),len(source)):
        if source[n]=='{':depth+=1
        if source[n]=='}':
            depth-=1
            if depth==0:end=n+1;break
    digest=hashlib.sha256(source[begin:end].encode()).hexdigest()
    assert digest=='01a412965f4ee56080b8cba692cec7a76e25bb4db2455e9c46a86a890613242e','Literal325 alignment function changed'
    observer=(ROOT/'tools/browser-gameplay/src/orsc/browser/BrowserBootstrap.java').read_text().split('Thread observer =',1)[1].split('static int[] snapshotBatchedModelWork',1)[0]
    assert observer.count('.getScene()')==1,'Observer must use one lifecycle snapshot rather than unchecked repeated Scene reads'
    assert 'snapshotBatchedModelWork(observedScene,' in observer
    assert 'surfaceAlignment'not in source and 'browserBenchmarkAlignment'not in source
    assert not(ROOT/'tools/browser-gameplay/web/browser-surface-alignment.js').exists()
    spec=importlib.util.spec_from_file_location('repeatable_prepare',ROOT/'scripts/browser-gameplay/prepare-repeatable.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    roster=module.roster();assert len(roster)==32 and len({(n['x'],n['y'])for n in roster})==32 and len({n['id']for n in roster})==8 and {n['direction']for n in roster}==set(range(8))
    config='server_port: 43594\nother_server_port: 111\n';assert module.replace(config,'server_port',43649)=='server_port: 43649\nother_server_port: 111\n'
    with sqlite3.connect('file:'+str(ROOT/'server/inc/sqlite/myworld_seed.db')+'?mode=ro',uri=True)as database:assert database.execute('SELECT COUNT(*) FROM players').fetchone()[0]==0
    bootstrap=sorted((ROOT/'tools/browser-gameplay/src/orsc/browser').glob('*.java'))
    fixtures=[ROOT/'tests/myworld/RepeatableCameraFixture.java',ROOT/'tests/myworld/BenchmarkCandidatePoliciesFixture.java',ROOT/'tests/myworld/BrowserBootstrapStartupFixture.java',ROOT/'tests/myworld/BrowserColorShaderOracle.java']
    with(suite/'cpu.log').open('w')as log:
        run([javac,'-source','8','-target','8','-cp',str(jar),'-d',str(classes)]+[str(p)for p in bootstrap+fixtures],log)
        cp=str(classes)+':'+str(jar)
        run([java,'-Djava.awt.headless=true','-cp',cp,'orsc.browser.RepeatableCameraFixture'],log)
        run([java,'-Djava.awt.headless=true','-cp',cp,'orsc.browser.BrowserBootstrapStartupFixture'],log)
        flags=['-D'+name+'=false'for name in ['spoiledmilk.browserGeometryTransferReuse','spoiledmilk.browserRegionPreparation','spoiledmilk.browserResidentDescriptorWriting','spoiledmilk.browserHaloHeightLookup','spoiledmilk.browserSceneryProjectionWork','spoiledmilk.browserPrimitiveChunkCoverage']]
        positive=['-D'+name+'=true'for name in ['spoiledmilk.browserGeometryTransferReuse','spoiledmilk.browserRegionPreparation','spoiledmilk.browserResidentDescriptorWriting','spoiledmilk.browserHaloHeightLookup','spoiledmilk.browserSceneryProjectionWork','spoiledmilk.browserPrimitiveChunkCoverage']]
        tail=(['--require-candidates']if args.require_candidates else [])+(['--require-projection']if args.require_projection else [])
        for selected in [flags,positive]:run([java,'-Djava.awt.headless=true']+selected+['-cp',cp,'orsc.browser.BenchmarkCandidatePoliciesFixture']+tail,log)
        servercp=':'.join(str(ROOT/'server'/p)for p in ['core-gameplay-overlay.jar','core.jar','plugins.jar','lib/*'])
        run([javac,'-source','8','-target','8','-cp',servercp,'-d',str(classes),str(ROOT/'tools/browser-gameplay/src/BrowserRepeatableServer.java')],log)
        for fixture in ['test-browser-repeatable-policy.mjs','test-browser-preparation-candidates.mjs','test-browser-stutter-diagnostics.mjs','test-browser-native-frontend-policy.mjs']:run(['node',str(ROOT/'tests/myworld'/fixture)],log)
        for flags in [[],['--repeatable'],['--benchmark-key-limbs'],['--physical'],['--physical-off'],['--without-native-module']]:run(['node','--experimental-vm-modules',str(ROOT/'tests/myworld/test-browser-webgl-frontend.mjs')]+flags,log)
        if args.native_source:
            native=['--native-source',str(args.native_source.resolve()),'--native-source-commit',args.native_source_commit]
            run(['python3',str(ROOT/'tests/myworld/test-browser-native-dispatch-stage.py')]+native,log)
            for selected in ['async','sync']:
                run(['node','--experimental-vm-modules',str(ROOT/'tests/myworld/test-browser-webgl-frontend.mjs'),'--native-'+selected,'--trace-output',str(suite/('frontend-'+selected+'.json'))]+native,log)
            run(['node','--experimental-vm-modules',str(ROOT/'tests/myworld/test-browser-webgl-frontend.mjs'),'--native-sync','--without-native-module']+native,log)
            assert json.loads((suite/'frontend-async.json').read_text())==json.loads((suite/'frontend-sync.json').read_text()),'Frontend CSS/native/event/disposal sequence differs'
            run(['node','--experimental-vm-modules',str(ROOT/'tests/myworld/test-browser-webgl-frontend.mjs'),'--native-sync','--native-thenable']+native,log)
        run([java,'-Djava.awt.headless=true','-cp',cp,'orsc.BrowserColorShaderOracle',str(suite/'shaders')],log)
        run(['node',str(ROOT/'tests/myworld/test-browser-gpu-cache.mjs'),'',str(suite/'shaders/world.vert')],log)
    (suite/'result.json').write_text(json.dumps(dict(passed=True,cpuOnly=True,browserStarted=False,serverStarted=False,jarSha256=hashlib.sha256(jar.read_bytes()).hexdigest(),alignmentFunctionSha256=digest,sourceProvenance='Selective salvage from291409; geometry helper fromfecc; no cached alignment'),indent=2)+'\n')
    print('PASS CPU-only repeatable benchmark tools; no server/browser/GPU/FPS claim')
if __name__=='__main__':main()
