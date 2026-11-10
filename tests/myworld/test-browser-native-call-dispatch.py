#!/usr/bin/env python3
"""Freeze the original async backend and derive the private sync candidate.

No browser/GPU or Java runtime is launched. Node executes exact backend command,
state, array-lifetime, failure and Promise-order fixtures against these sources.
"""
from pathlib import Path
import hashlib,importlib.util,json,re,subprocess
ROOT=Path(__file__).resolve().parents[2]
BASE='325bf42b60fe411705f9f14c0a3ef6debabe24a7'
spec=importlib.util.spec_from_file_location('native_dispatch',ROOT/'scripts/browser-gameplay/native_dispatch.py')
generator=importlib.util.module_from_spec(spec);spec.loader.exec_module(generator)
source=(ROOT/'tools/browser-gameplay/web/webgl.js').read_text()
old=subprocess.check_output(['git','show',BASE+':tools/browser-gameplay/web/webgl.js'],cwd=ROOT,text=True)
def bodies(s):return s.split('  const natives={',1)[1].split('\n  };',1)[0]
def original_wrapper(s):return s.split('    const call=natives[name];',1)[1].split('\n  function queuePhysicalOutput',1)[0]
# The separately owned sampler receipt is the only accepted body change. It
# runs after a successful frameWork completion in BOTH dispatch policies.
assert bodies(source).replace('stats.javaBridgeReportedWorldFrames=stats.javaWorldFrames;','')==bodies(old),'Canonical async native body drifted from frozen control'
assert original_wrapper(source)==original_wrapper(old),'Original async fatal wrapper changed'
generated=generator.synchronous_module(source)
assert generated.count('    async Java_orsc_BrowserWebGLBridge_')==1
assert '    async Java_orsc_BrowserWebGLBridge_initialize('in generated
restored=generated.split('\n',1)[1].replace("export const BROWSER_NATIVE_CALL_DISPATCH = 'sync';",generator.MARKER,1)
for name in generator.SYNC_NATIVES:restored=restored.replace('    '+name+'(','    async '+name+'(',1)
assert restored==source,'Generator changed something besides audited async declarations/marker'
bad_sources=[source.replace(generator.MARKER,''),source+'\n'+generator.MARKER,
 source.replace('async Java_orsc_BrowserWebGLBridge_worldProgress','async Java_orsc_BrowserWebGLBridge_unknown',1),
 source.replace('worldProgress(lib,metadata){','worldProgress(lib,metadata){await Promise.resolve();',1),
 source.replace('worldProgress(lib,metadata){','worldProgress(lib,metadata){return Promise.resolve();',1),
 source.replace('  const natives={','  const natives={\n    Java_orsc_BrowserWebGLBridge_unknown(){},',1),
 source.replace('    async Java_orsc_BrowserWebGLBridge_worldProgress','    Java_orsc_BrowserWebGLBridge_worldProgress',1)]
for bad in bad_sources:
 try:generator.synchronous_module(bad)
 except ValueError:pass
 else:raise AssertionError('Generator accepted drift/async native inventory')
suite=Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-native-call-dispatch','--mkdir'],text=True).strip())
paths=[]
for name,text in [('original',old),('async-control',source),('sync-candidate',generated)]:
 linked=re.sub(r"from '\./([^']+)'",lambda m:"from '"+(ROOT/'tools/browser-gameplay/web'/m.group(1)).as_uri()+"'",text)
 path=suite/(name+'.mjs');path.write_text(linked);paths.append(path)
receipt={'baseline':BASE,'canonicalSha256':hashlib.sha256(source.encode()).hexdigest(),'originalSha256':hashlib.sha256(old.encode()).hexdigest(),'generatedSha256':hashlib.sha256(generated.encode()).hexdigest(),'pureNatives':list(generator.SYNC_NATIVES),'alwaysAsync':generator.ASYNC_NATIVE,'literalAsyncBodiesAndFatalWrapperPreserved':True,'GPU':False,'CheerpJSmoke':False,'FPSClaim':False}
(suite/'source-receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
result=subprocess.run(['node',str(ROOT/'tests/myworld/browser-native-call-dispatch-fixture.mjs'),*map(str,paths),str(suite/'cpu-result.json')],text=True,capture_output=True)
(suite/'cpu-result.log').write_text(result.stdout+result.stderr);print(result.stdout,end='');print(result.stderr[:4000] if result.returncode else result.stderr,end='');result.check_returncode()
print('PASS strict19-method generated transform and literal frozen ASYNC body/wrapper authority; no GPU/CheerpJ/FPS claim')
