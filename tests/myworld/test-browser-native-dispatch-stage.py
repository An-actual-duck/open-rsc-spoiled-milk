#!/usr/bin/env python3
"""CPU-only staging provenance checks using an explicit committed review source."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--native-source', required=True, type=Path)
    parser.add_argument('--native-source-commit', required=True)
    args = parser.parse_args()
    spec = importlib.util.spec_from_file_location('native_stage', ROOT/'scripts/browser-gameplay/native_dispatch_stage.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    category = Path(subprocess.check_output(['python3', str(ROOT/'scripts/workspace-layout.py'), 'path', 'tests', 'browser-native-dispatch-stage', '--mkdir'], text=True).strip())
    suite = category/('cpu-'+str(time.time_ns()))
    suite.mkdir()
    def new_run(name):
        run = suite/name
        web = run/'web'
        web.mkdir(parents=True)
        (web/'webgl.js').write_bytes((ROOT/'tools/browser-gameplay/web/webgl.js').read_bytes())
        return run, web
    run, web = new_run('baseline')
    baseline = module.stage_native_dispatch(ROOT, run, web)
    assert baseline['available'] is False and not (web/'webgl-sync.js').exists()
    before = subprocess.check_output(['git','status','--porcelain'], cwd=args.native_source)
    run, web = new_run('review')
    receipt = module.stage_native_dispatch(ROOT,run,web,args.native_source,args.native_source_commit)
    assert receipt['available'] and receipt['reviewDependency'] and receipt['runtimeEval'] is False
    assert receipt['sourceCommit'] == args.native_source_commit
    for relative in module.FILES:
        original = subprocess.check_output(['git','show',args.native_source_commit+':'+relative],cwd=args.native_source)
        assert receipt['inputs'][relative]['sha256'] == hashlib.sha256(original).hexdigest()
        assert (run/'native-dispatch-source'/Path(relative).name).read_bytes() == original
    canonical = (web/'webgl.js').read_bytes()
    generated = (web/'webgl-sync.js').read_bytes()
    assert generated.startswith(('// Generated private sync-native variant; canonical SHA256 '+hashlib.sha256(canonical).hexdigest()+'\n').encode())
    assert b"BROWSER_NATIVE_CALL_DISPATCH = 'sync'" in generated
    assert generated.count(b'    async Java_orsc_BrowserWebGLBridge_') == 1
    assert canonical.count(b'    async Java_orsc_BrowserWebGLBridge_') == 20
    assert hashlib.sha256(generated).hexdigest() == receipt['generatedSha256']
    assert (web/'browser-native-call-dispatch.js').read_bytes() == (run/'native-dispatch-source/browser-native-call-dispatch.js').read_bytes()
    after = subprocess.check_output(['git','status','--porcelain'], cwd=args.native_source)
    assert before == after, 'Read-only review worktree changed'
    for name,commit in [('short',args.native_source_commit[:8]),('missing','0'*40)]:
        invalid_run, invalid_web = new_run(name)
        try:
            module.stage_native_dispatch(ROOT,invalid_run,invalid_web,args.native_source,commit)
        except (ValueError,subprocess.CalledProcessError):
            pass
        else:
            raise AssertionError('Invalid review commit accepted')
        assert not (invalid_web/'webgl-sync.js').exists()
    try:
        module.stage_native_dispatch(ROOT,suite/'outside-owner',web,args.native_source,args.native_source_commit)
    except ValueError:
        pass
    else:
        raise AssertionError('Output outside stage accepted')
    # Transformation authority must still reject body drift, including Promise
    # changes with no await token. Import the captured, reviewed generator only.
    spec = importlib.util.spec_from_file_location('captured_generator',run/'native-dispatch-source/native_dispatch.py')
    generator = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(generator)
    drifted=canonical.decode().replace('    async Java_orsc_BrowserWebGLBridge_holdWorld(){', '    async Java_orsc_BrowserWebGLBridge_holdWorld(){return Promise.resolve();', 1)
    assert drifted != canonical.decode()
    try:
        generator.synchronous_module(drifted)
    except ValueError:
        pass
    else:
        raise AssertionError('Changed audited native accepted')
    (suite/'result.json').write_text(json.dumps(dict(passed=True,cpuOnly=True,readOnlyReview=True,receipt=receipt),indent=2)+'\n')
    print('PASS exact-commit native staging:20/19 inventory, hashes, read-only dependency, invalid commit/output/drift rejection; no GPU')

if __name__ == '__main__':
    main()
