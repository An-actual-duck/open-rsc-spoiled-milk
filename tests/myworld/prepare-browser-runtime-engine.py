#!/usr/bin/env python3
"""Freeze one explicit runtime-options variant of the approved v3 JNI probe.

Reads only a caller-selected, hash-guarded frozen stage. No compilation, browser,
GPU, account, server launch or production runtime change is performed.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
VARIANTS = {
    'java17-js': {'javaVersion': 17, 'engineRequested': 'default-js', 'wasmGC': False},
    'java8-wasmgc': {'javaVersion': 8, 'engineRequested': 'wasmgc-experimental', 'wasmGC': True},
}
# Exact manager-accepted native-contract-modes-v3-void inputs. This is an
# independent review-artifact dependency, not a dependency on a parked branch.
EXPECTED = {
    '/browser-native-call-dispatch.js': (1373, '75c297407af233acacaa8a00c1c94025ae62f60a7fab1a5eec08130193a7a9f5'),
    '/browser-native-runtime-probe.js': (5929, '7ec86da6d1efde21975408e9ba766e4cf38bbd734b35990f29dac05017a68f74'),
    '/index.html': (273, '23c1c2670dee16a6002defbb8b687b45cad189d79e8f38d850f5a051280151be'),
    '/probe.jar': (2075, '32ba181e42d95ab7c33fb5ce95d417dcd95849ca59d6f2ace5d61453edf7cab2'),
}
JAVA_SHA = 'b8fccb13a37a9267140fa272203e92da61c7c6b07968e030c06cfadc7fb3de79'
MODES = ['values', 'async-fatal', 'sync-fatal', 'thenable-fatal']

def sha(value):
    return hashlib.sha256(value).hexdigest()

def source_inputs(stage):
    stage = Path(stage).resolve()
    raw = (stage/'stage.json').read_bytes()
    metadata = json.loads(raw)
    if not metadata.get('preparedOnly') or metadata.get('javaVersion') != 8 or metadata.get('queryModes') != MODES:
        raise ValueError('Select the exact approved v3-void probe stage')
    if set(metadata.get('webFiles', {})) != set(EXPECTED):
        raise ValueError('Unexpected input web-file inventory')
    captured = {}
    for relative, (size, digest) in EXPECTED.items():
        path = stage/'web'/relative.lstrip('/')
        if path.is_symlink() or not path.is_file():
            raise ValueError('Probe input must be a regular file: '+relative)
        value = path.read_bytes()
        if len(value) != size or sha(value) != digest or metadata['webFiles'][relative] != {'bytes': size, 'sha256': digest}:
            raise ValueError('Approved probe input hash/manifest mismatch: '+relative)
        captured[relative] = value
    java = (stage/'source/NativeDispatchProbe.java').read_bytes()
    if sha(java) != JAVA_SHA or metadata.get('javaSourceSha256') != JAVA_SHA:
        raise ValueError('Approved Java probe source mismatch')
    if metadata.get('probeJarSha256') != EXPECTED['/probe.jar'][1] or metadata.get('helperSourceSha256') != EXPECTED['/browser-native-call-dispatch.js'][1]:
        raise ValueError('Approved Java/helper metadata mismatch')
    return metadata, raw, captured, java

def once(source, old, new):
    if source.count(old) != 1:
        raise ValueError('Frozen probe patch point changed: '+old[:80])
    return source.replace(old, new, 1)

def variant_script(original, variant):
    if variant not in VARIANTS:
        raise ValueError('Only the two explicitly reviewed runtime variants are allowed')
    config = VARIANTS[variant]
    source = original.decode('utf8')
    source = once(source, "const mode=new URLSearchParams(location.search).get('mode')||'values';", "const runtimeVariant=Object.freeze("+json.dumps(dict(name=variant, **config), separators=(',', ':'))+");\nconst query=new URLSearchParams(location.search);\nconst mode=query.get('mode')||'values';")
    source = once(source, "const describe=error=>", "proof.requestedRuntime=runtimeVariant;\nproof.activation={initResolved:false,probePassed:false,actualEngineObserved:null,actualJavaVersionObserved:null};\nproof.browserUserAgent=String(globalThis.navigator?.userAgent||'unknown');\nconst describe=error=>")
    source = once(source, "try{\n if(![", "try{\n if([...query.keys()].some(key=>key!=='mode')||query.getAll('mode').length>1)throw new Error('Runtime options are fixed by this stage; only a single mode query is allowed');\n if(![")
    options = {'version': config['javaVersion']}
    if config['wasmGC']:
        options['wasmGC'] = True
    source = once(source, "await boundedWait(cheerpjInit({version:8,natives}),'runtime initialization',45000);", "await boundedWait(cheerpjInit({..."+json.dumps(options, separators=(',', ':'))+",natives}),'runtime initialization',45000);\n proof.activation.initResolved=true;")
    source = once(source, ' proof.passed=true;', ' proof.passed=true;proof.activation.probePassed=true;')
    return source.encode('utf8')

def prepare(source_stage, output, variant, port):
    if not 1024 < port < 65536:
        raise ValueError('Use an explicit unprivileged loopback HTTP port')
    if variant not in VARIANTS:
        raise ValueError('Invalid runtime variant')
    metadata, raw, captured, java = source_inputs(source_stage)
    script = variant_script(captured['/browser-native-runtime-probe.js'], variant)
    output = Path(output).resolve()
    output.mkdir(parents=True, exist_ok=False)
    web = output/'web'; web.mkdir()
    snapshot = output/'source-inputs'; snapshot.mkdir()
    (snapshot/'stage.json').write_bytes(raw)
    (snapshot/'NativeDispatchProbe.java').write_bytes(java)
    for relative, value in captured.items():
        (snapshot/relative.lstrip('/')).write_bytes(value)
        (web/relative.lstrip('/')).write_bytes(script if relative == '/browser-native-runtime-probe.js' else value)
    options = {'version': VARIANTS[variant]['javaVersion']}
    if VARIANTS[variant]['wasmGC']:
        options['wasmGC'] = True
    receipt = dict(runtimeEngineProbe=1, preparedOnly=True, browserExecuted=False, processesStarted=False,
        preparedUtc=datetime.now(timezone.utc).isoformat(), toolingSourceCommit=subprocess.check_output(['git','rev-parse','HEAD'], cwd=ROOT, text=True).strip(),
        sourceStage=str(Path(source_stage).resolve()), sourceManifestSha256=sha(raw), sourceManifest=metadata,
        javaSourceSha256=JAVA_SHA, probeJarSha256=EXPECTED['/probe.jar'][1],
        variant=variant, javaVersion=VARIANTS[variant]['javaVersion'], engineRequested=VARIANTS[variant]['engineRequested'],
        runtimeOptions=options, actualEngineObserved=None, actualJavaVersionObserved=None,
        activationPolicy='init resolution and probe results observed separately; no actual engine getter is claimed',
        runtimeUrl='https://cjrtnc.leaningtech.com/5.0/loader.js', queryModes=MODES, protocolChecks=1903,
        webFiles={'/'+p.name:dict(bytes=p.stat().st_size, sha256=sha(p.read_bytes())) for p in sorted(web.iterdir())},
        urls={mode:'http://127.0.0.1:'+str(port)+'/?mode='+mode for mode in MODES},
        serveCommand=['python3',str(ROOT/'tests/myworld/serve-browser-runtime-engine.py'),str(output),'--port',str(port)],
        proofExpression='window.nativeRuntimeProof', scope='Prepared runtime/JNI compatibility only; no game, GPU, account or FPS claim')
    (output/'stage.json').write_text(json.dumps(receipt, indent=2)+'\n')
    return receipt

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-stage', required=True, type=Path)
    parser.add_argument('--variant', required=True, choices=VARIANTS)
    parser.add_argument('--run-id', required=True)
    parser.add_argument('--http-port', required=True, type=int)
    args = parser.parse_args()
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]{0,79}', args.run_id):
        parser.error('Invalid fresh run ID')
    category = Path(subprocess.check_output(['python3',str(ROOT/'scripts/workspace-layout.py'),'path','tests','browser-runtime-engine-matrix','--mkdir'], text=True).strip())
    print(json.dumps(prepare(args.source_stage, category/args.run_id, args.variant, args.http_port), indent=2))

if __name__ == '__main__':
    main()
