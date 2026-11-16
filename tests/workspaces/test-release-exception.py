#!/usr/bin/env python3
"""Real disposable Git repositories: exact release scope and durable exclusions."""
from pathlib import Path
import importlib.util,json,subprocess,time
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('release_exception',ROOT/'scripts/check-release-exception.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
BASE=ROOT/'output/tests/release-exception'/str(time.time_ns());BASE.mkdir(parents=True)
def fixture(name):
 base=BASE/name;base.mkdir();repo=base/'repo';remote=base/'remote.git'
 def git(*args,where=repo):return subprocess.check_output(['git','-C',str(where),'-c','user.name=Release Fixture','-c','user.email=fixture@example.invalid',*args],stderr=subprocess.DEVNULL).decode().strip()
 subprocess.run(['git','init','--bare',str(remote)],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 subprocess.run(['git','init','-b','main',str(repo)],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
 (repo/'README').write_text('synthetic fixture only\n');git('add','.');git('commit','-m','fixture source');tip=git('rev-parse','HEAD');git('branch','feat/preserved');git('remote','add','spoiled-milk',str(remote));git('push','spoiled-milk','feat/preserved')
 path=repo/'config/release-exceptions/v0.3.22.json';path.parent.mkdir(parents=True)
 data={'schemaVersion':1,'releaseVersion':'v0.3.22','authorization':'synthetic approval','excludedBranches':[{'branch':'feat/preserved','commit':tip,'remote':'spoiled-milk','reason':'preserved fixture'}]}
 path.write_text(json.dumps(data));git('add','.');git('commit','-m','review exception')
 return repo,remote,path,data,git
checks=0
def rejected(name,mutate,version='v0.3.22'):
 global checks
 repo,remote,path,data,git=fixture(name);mutate(repo,remote,path,data,git)
 try:module.validate(repo,'config/release-exceptions/v0.3.22.json',version)
 except (ValueError,OSError,subprocess.CalledProcessError):checks+=1;return
 raise AssertionError(name+' incorrectly admitted')
r,remote,p,d,g=fixture('exact');assert module.validate(r,'config/release-exceptions/v0.3.22.json','v0.3.22')==['feat/preserved'];checks+=1
rejected('dirty-manifest',lambda r,b,p,d,g:p.write_text(p.read_text()+'\n'))
rejected('wrong-version',lambda *args:None,version='v0.3.23')
def move_local(r,b,p,d,g):
 g('switch','feat/preserved');(r/'new-source').write_text('new work');g('add','.');g('commit','-m','advance excluded work');g('switch','main')
rejected('local-tip-moved',move_local)
def move_remote(r,b,p,d,g):
 g('push','spoiled-milk','main');g('update-ref','refs/heads/feat/preserved',g('rev-parse','HEAD'),where=b)
rejected('remote-tip-moved',move_remote)
def bad_main(r,b,p,d,g):
 d['excludedBranches'][0]['branch']='main';p.write_text(json.dumps(d));g('add','.');g('commit','-m','bad exclusion')
rejected('never-exclude-main',bad_main)
def missing_reason(r,b,p,d,g):
 d['excludedBranches'][0]['reason']='';p.write_text(json.dumps(d));g('add','.');g('commit','-m','missing review reason')
rejected('missing-reason',missing_reason)
print('PASS',checks,'real-Git release exception admission/refusal cases; evidence:',BASE)
