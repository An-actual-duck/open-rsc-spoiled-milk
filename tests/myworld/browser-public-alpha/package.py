#!/usr/bin/env python3
"""Offline admission/refusal/reproducibility tests; no cook or listener."""
import importlib.util,json,tempfile,zipfile,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
spec=importlib.util.spec_from_file_location('public_packager',ROOT/'scripts/browser-gameplay/package-public-alpha.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
checks=0
def reject(fn):
 global checks
 try:fn()
 except (ValueError,FileNotFoundError,KeyError):checks+=1
 else:raise AssertionError('Invalid input accepted')
def doc(path,value):path.write_text(json.dumps(value))
with tempfile.TemporaryDirectory(prefix='public-alpha-package-')as d:
 r=Path(d);m.ROOT=r;(r/'Client_Base/src').mkdir(parents=True);source=r/'Client_Base/src/Fixture.java';source.write_text('fixture source')
 jar=r/'client.jar'
 with zipfile.ZipFile(jar,'w')as z:z.writestr('Fixture.class',b'class bytes');z.writestr('own.txt',b'own asset')
 docs={'source-manifest.json':{'Client_Base/src/Fixture.java':m.sha(source)},'compiled-classes.json':{'Fixture.class':hashlib.sha256(b'class bytes').hexdigest()},'asset-manifest.json':{'own.txt':hashlib.sha256(b'own asset').hexdigest()}}
 for n,v in docs.items():doc(r/n,v)
 b={'singleProductionCommit':True,'cleanCheckoutAtBuild':True,'buildSucceeded':True,'classesTransplanted':False,'clientSourceCommit':'a'*40,'jarSha256':m.sha(jar),'sourceManifestSha256':m.sha(r/'source-manifest.json'),'compiledClassesManifestSha256':m.sha(r/'compiled-classes.json'),'assetManifestSha256':m.sha(r/'asset-manifest.json'),'compiledClasses':1,'packagedOwnAssets':1}
 receipt=r/'receipt.json';doc(receipt,b);assert m.admit_build(jar,receipt,'a'*40)==b;checks+=1
 for change in [{'singleProductionCommit':False},{'cleanCheckoutAtBuild':False},{'classesTransplanted':True},{'jarSha256':'0'*64},{'clientSourceCommit':'b'*40},{'compiledClasses':2}]:doc(receipt,{**b,**change});reject(lambda:m.admit_build(jar,receipt,'a'*40))
 doc(receipt,b);source.write_text('mutated source');reject(lambda:m.admit_build(jar,receipt,'a'*40));source.write_text('fixture source')
 doc(r/'compiled-classes.json',{'Fixture.class':'0'*64});doc(receipt,{**b,'compiledClassesManifestSha256':m.sha(r/'compiled-classes.json')});reject(lambda:m.admit_build(jar,receipt,'a'*40))
 for value in ['../escape','/absolute','a/../b','a//b','a?x','a\\b']:reject(lambda:m.safe_path(value))
 link=r/'link';link.symlink_to(source);reject(lambda:m.copy_checked(link,r/'copy'))
 reject(lambda:m.copy_checked(source,r/'copy',{'bytes':100,'sha256':'0'*64}))
 m.copy_checked(source,r/'copy',{'bytes':source.stat().st_size,'sha256':m.sha(source)});assert m.sha(source)==m.sha(r/'copy');checks+=1
 reject(lambda:m.copy_checked(source,r/'copy'))
 classes=r/'classes';classes.mkdir();(classes/'X.class').write_bytes(b'bytecode');m.executable_jar(classes,r/'one.jar');m.executable_jar(classes,r/'two.jar');assert m.sha(r/'one.jar')==m.sha(r/'two.jar');checks+=1
 (classes/'X.class').write_bytes(b'browserdemo');reject(lambda:m.executable_jar(classes,r/'secret.jar'))
 duplicate=r/'duplicate.json';duplicate.write_text('{"value":1,"value":2}');reject(lambda:m.document(duplicate))
print('PASS '+str(checks)+' full-build manifest/class/source refusals, safe copied inputs and deterministic bootstrap archives; CPU only')
