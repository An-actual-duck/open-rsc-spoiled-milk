#!/usr/bin/env python3
"""CPU contracts only. No server/browser/GPU launch or FPS claim."""
import argparse,hashlib,importlib.util,json,sqlite3,subprocess,tempfile,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def module(path,name):
    spec=importlib.util.spec_from_file_location(name,path);result=importlib.util.module_from_spec(spec);spec.loader.exec_module(result);return result
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--client-jar',required=True,type=Path);p.add_argument('--java-home',type=Path);p.add_argument('--server-artifacts',required=True,type=Path);args=p.parse_args()
    jar=args.client_jar.resolve();java=str(args.java_home/'bin/java')if args.java_home else 'java';javac=str(args.java_home/'bin/javac')if args.java_home else 'javac'
    category=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-combat-workload','--mkdir'],text=True).strip());run=category/('cpu-'+(args.java_home.name if args.java_home else 'default'));run.mkdir(exist_ok=True);classes=run/'classes';classes.mkdir(exist_ok=True)
    prepare=module(ROOT/'scripts/browser-gameplay/prepare-combat.py','combat_prepare')
    with tempfile.TemporaryDirectory(dir=run)as tmp:
        folder=Path(tmp);assets=folder/'assets';(assets/'animations/on-entity').mkdir(parents=True);(assets/'legacy-animations').mkdir()
        (assets/'animations/on-entity/effect.png').write_bytes(b'preserved-binary-source')
        archive=folder/'test.jar'
        with zipfile.ZipFile(archive,'w')as z:z.writestr('myworld-assets/animations/on-entity/effect.png',b'preserved-binary-source')
        audit=prepare.asset_audit(archive,assets);assert audit['files']==1 and audit['missing']==audit['different']==0
        (assets/'legacy-animations/missing.png').write_bytes(b'x');(assets/'animations/on-entity/effect.png').write_bytes(b'changed')
        audit=prepare.asset_audit(archive,assets);assert audit['files']==2 and audit['missing']==1 and audit['different']==1
    with sqlite3.connect('file:'+str(ROOT/'server/inc/sqlite/myworld_seed.db')+'?mode=ro',uri=True)as database:assert database.execute('SELECT COUNT(*) FROM players').fetchone()[0]==0
    server=(ROOT/'tools/renderer-benchmark/RendererBenchmarkServer.java').read_text()
    for evidence in ['"127.0.0.1".equals(config.SERVER_BIND_ADDRESS)','!= 43639','!= 43539','"renderer_benchmark_dev".equals(config.DB_NAME)','"RendererProof13"','MagicCombatEvent.start','Spells.ICE_CRYSTAL','Spells.THUNDER_STRIKE','Spells.BATTERING_RAM','new RangeEvent','new ThrowingEvent','p.startCombat(target)','index < 8','Summoning.summonTestBear','resourceUnitsSpent','primaryDamageByFamily','summonsPresent']:
        assert evidence in server,evidence
    source=(ROOT/'scripts/browser-gameplay/prepare-combat.py').read_text()
    assert 'subprocess.Popen'not in source and "'prepare'"in source and "phase='combat-real'"in source
    assert "'frozen-repeatable-baseline-2-artifacts-source-unverified'"not in source,'Source provenance must be an explicit caller input, not hardcoded guessed commit'
    with(run/'cpu.log').open('w')as log:
        def call(argv):
            result=subprocess.run(argv,cwd=ROOT,text=True,stdout=subprocess.PIPE,stderr=subprocess.STDOUT);print(result.stdout,end='');log.write(result.stdout);result.check_returncode()
        call([javac,'-source','8','-target','8','-cp',str(jar),'-d',str(classes)]+[str(f)for f in sorted((ROOT/'tools/browser-gameplay/src/orsc/browser').glob('*.java'))]+[str(ROOT/'tests/myworld/BrowserCombatWorkloadFixture.java'),str(ROOT/'tests/myworld/RepeatableCameraFixture.java')])
        for name in ['BrowserCombatWorkloadFixture','RepeatableCameraFixture']:call([java,'-Djava.awt.headless=true','-cp',str(classes)+':'+str(jar),'orsc.browser.'+name])
        artifacts=args.server_artifacts.resolve();cp=':'.join(str(artifacts/f)for f in ['lib/log4j-api-2.17.0.jar','lib/log4j-core-2.17.0.jar','core-gameplay-overlay.jar','core.jar','plugins.jar','lib/*'])
        call([javac,'-source','8','-target','8','-cp',cp,'-d',str(classes),str(ROOT/'tools/renderer-benchmark/RendererBenchmarkServer.java'),str(ROOT/'tests/myworld/RendererSummonSustainFixture.java')])
        call([java,'-Djava.awt.headless=true','-cp',str(classes)+':'+cp,'RendererSummonSustainFixture'])
        call(['node',str(ROOT/'tests/myworld/test-browser-repeatable-policy.mjs')])
        for flags in [[],['--repeatable'],['--combat']]:call(['node','--experimental-vm-modules',str(ROOT/'tests/myworld/test-browser-webgl-frontend.mjs')]+flags)
    (run/'result.json').write_text(json.dumps(dict(passed=True,cpuOnly=True,serverStarted=False,browserStarted=False,clientSha256=hashlib.sha256(jar.read_bytes()).hexdigest(),serverArtifactRoot=str(args.server_artifacts.resolve()),note='Observer/gates/route/packaging contracts only; no decoded/visible VFX or FPS proof'),indent=2)+'\n')
    print('PASS CPU-only browser combat fixture contracts; no server, browser, GPU or FPS proof')
if __name__=='__main__':main()
