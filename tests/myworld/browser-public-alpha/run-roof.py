#!/usr/bin/env python3
"""Real source, in-memory single-centre activation and actual UI reload parity; no services/cooks."""
import argparse,hashlib,importlib.util,json,pathlib,re,shutil,subprocess
ROOT=pathlib.Path(__file__).resolve().parents[3]
p=argparse.ArgumentParser();p.add_argument('--jar',type=pathlib.Path,required=True);p.add_argument('--receipt',type=pathlib.Path,required=True);p.add_argument('--name',required=True);a=p.parse_args()
if not re.fullmatch('[A-Za-z0-9_-]+',a.name):raise ValueError('Fresh simple name required')
def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def module(name,path):
 s=importlib.util.spec_from_file_location(name,path);m=importlib.util.module_from_spec(s);s.loader.exec_module(m);return m
receipt=json.loads(a.receipt.read_text());assert receipt['singleProductionCommit']and receipt['cleanCheckoutAtBuild']and receipt['buildSucceeded']and receipt['classesTransplanted']is False and receipt['jarSha256']==sha(a.jar)
category=pathlib.Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-public-alpha','--mkdir'],text=True).strip());out=category/a.name;out.mkdir()
mini=out/'mini-cache';(mini/'video').mkdir(parents=True);extract=module('roof_textures',ROOT/'tools/browser-gameplay/whole-zone/extract-textures.py');extract.extract(ROOT/'Client_Base/Cache/video/Custom_Sprites.osar',mini/'video/Custom_Sprites.osar')
pack=module('roof_packaging',ROOT/'scripts/browser-gameplay/package-public-alpha.py');head=subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip();facade=out/'minimal-root';(facade/'Client_Base/Cache').mkdir(parents=True)
for relative,source in pack.runtime_cache_sources(ROOT,head):target=facade/'Client_Base'/relative;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
profile='world-builder-configs/installed-client.json';descriptor=json.loads((ROOT/'Client_Base'/profile).read_text());relative=descriptor['packageRelativePath'];(facade/'Client_Base'/profile).parent.mkdir(parents=True);shutil.copyfile(ROOT/'Client_Base'/profile,facade/'Client_Base'/profile);(facade/'Client_Base'/relative).mkdir(parents=True);shutil.copyfile(ROOT/'Client_Base'/relative/'manifest.json',facade/'Client_Base'/relative/'manifest.json');assert len(list((facade/'Client_Base'/relative).rglob('*')))==1
selection=module('roof_selection',ROOT/'scripts/installed-map-selection.py').select(ROOT);source=selection['serverPackage'];classes=out/'classes';classes.mkdir()
fixtures=[pathlib.Path(__file__).with_name('RoofFallbackFixture.java')]+[ROOT/'tests/myworld/whole-ram'/n for n in ('WholeRamActivationFixture.java','WholeRamWorldState.java','WholeRamModelFieldFixture.java')]
commands=[];cases=[]
compile=['javac','-source','8','-target','8','-cp',str(a.jar.resolve()),'-d',str(classes),*map(str,fixtures)];commands.append(compile)
with(out/'compile.log').open('w')as log:subprocess.run(compile,stdout=log,stderr=subprocess.STDOUT,check=True)
for mode in ('true','false'):
 command=['nice','-n','10','/home/justin/jdk8u482-b08/bin/java','-Xmx768m','-Djava.awt.headless=true','-Dspoiledmilk.browserPublicAlpha='+mode,'-Dspoiledmilk.browserBenchmark=true','-Dspoiledmilk.browserWholeZone=true','-Dspoiledmilk.browserWholeRam=true','-cp',str(classes)+':'+str(a.jar.resolve()),'orsc.RoofFallbackFixture',str(facade),source,str(mini),str(out/('mode-'+mode))];commands.append(command)
 with(out/('mode-'+mode+'.log')).open('w')as log:r=subprocess.run(command,stdout=log,stderr=subprocess.STDOUT,cwd=facade,timeout=120)
 text=(out/('mode-'+mode+'.log')).read_text();assert r.returncode==0,text[-4000:];match=re.search(r'PASS (\d+) fresh one-centre',text);assert match
 cases.append(dict(publicMode=mode=='true',checks=int(match.group(1)),passed=True));print(mode,match.group(1),'PASS')
result=dict(passed=True,receipt=receipt,cases=cases,commands=commands,fixtureSha256={str(f.relative_to(ROOT)):sha(f)for f in fixtures},minimalClientMapFiles=1,rawSourceReadsBlockedAfterSnapshotCreation=True,scope='Fresh exact one-centre recipes constructed in memory from actual installed source snapshots; full cooked constructor bypassed only by fixture injection. Both roof variants, actual account setter/UI reload and CPU collision/elevation/minimap/model/picking/order parity; actual old captured halo rejection and recipe-hit counts. Signed-level scope roundtrips -1/10 and real L1 load; not full negative-plane transport, full445 cook constructor/readiness, browser/GPU/FPS proof. No old cooked artifact rebound; no services started.')
(out/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(out/'result.json')
